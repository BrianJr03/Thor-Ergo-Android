"""Opt-in AYN/F24 secondary-screen toggle, independent of the keyboard window.

Uses the same GAMESCOPE_COMPANION_LOCKED property as Armada's companion-lock
operation, not DPMS, brightness, suspension or stopping a compositor. Runs as
the desktop user; never invokes sudo or changes input-device permissions.
On current Armada, uses a reversible InputPlumber QuickAccess2-to-D-Bus route.
Only when InputPlumber is absent may a dedicated single-key GPIO be grabbed.
A normal keyboard/gamepad is never intercepted. The setting is off by default.
"""
from __future__ import annotations
import argparse
import fcntl
import json
import os
from pathlib import Path
import re
import select
import signal
import stat
import struct
import subprocess
import tempfile
import time
from .gamescope_target import GamescopeTarget

F24=194
EV_KEY=1
EVENT=struct.Struct('@llHHi')
EVIOCGRAB=0x40044590
STATE_NAMES={'off','starting','ready','screen_off','unavailable'}

def paths():
    home=Path.home()
    config=Path(os.environ.get('XDG_CONFIG_HOME',home/'.config'))/'thor-ergo'/'ergo.json'
    state=Path(os.environ.get('XDG_STATE_HOME',home/'.local/state'))/'thor-ergo'
    return config,state

def read_json(path):
    path=Path(path)
    try:
        if path.is_symlink() or path.stat().st_size>65536:return {}
        data=json.loads(path.read_text(encoding='utf-8'))
        return data if isinstance(data,dict) else {}
    except (OSError,ValueError):return {}

def atomic_json(path,data):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
    if path.is_symlink():raise OSError('Refusing symbolic state file')
    fd,tmp=tempfile.mkstemp(prefix='.ayn-',dir=path.parent)
    try:
        with os.fdopen(fd,'w',encoding='utf-8') as f:
            json.dump(data,f,ensure_ascii=True);f.write('\n')
        os.chmod(tmp,0o600);os.replace(tmp,path)
    finally:Path(tmp).unlink(missing_ok=True)

class Companion:
    """User-scoped, verified current Gamescope primary session."""
    def __init__(self,run=subprocess.run):self.run=run
    def identity(self):
        model=Path('/sys/firmware/devicetree/base/model').read_bytes().rstrip(b'\0').decode('utf-8')
        if model not in ('AYN Thor','AYN Thor Lite'):raise RuntimeError('unsupported_model')
        target=GamescopeTarget.resolve(Path(os.environ.get('XDG_RUNTIME_DIR',f'/run/user/{os.getuid()}')))
        x=Path('/tmp/.X11-unix/X0').stat()
        if not stat.S_ISSOCK(x.st_mode) or x.st_uid!=os.getuid():raise RuntimeError('unverified_xwayland')
        boot=Path('/proc/sys/kernel/random/boot_id').read_text().strip()
        return [boot,*target.identity,x.st_dev,x.st_ino,x.st_ctime_ns]
    def _xprop(self,*args):
        env=dict(os.environ);env['DISPLAY']=':0';env['LC_ALL']='C'
        env.pop('LD_PRELOAD',None)
        result=self.run(['/usr/bin/xprop','-display',':0','-root',*args],env=env,
                        capture_output=True,text=True,timeout=3,check=True)
        return result.stdout
    def read(self):
        # Also verify that :0 really exposes Gamescope root properties.
        focused=self._xprop('-notype','GAMESCOPE_FOCUSED_APP')
        if not re.search(r'GAMESCOPE_FOCUSED_APP\s*=\s*\d+',focused):
            raise RuntimeError('not_game_mode')
        result=self._xprop('-notype','GAMESCOPE_COMPANION_LOCKED')
        match=re.search(r'GAMESCOPE_COMPANION_LOCKED\s*=\s*([01])\s*$',result.strip())
        if match:return int(match.group(1))
        if 'not found' in result or 'no such atom' in result:return 0
        raise RuntimeError('unknown_companion_state')
    def set(self,value,identity):
        if self.identity()!=identity:raise RuntimeError('session_changed')
        self._xprop('-f','GAMESCOPE_COMPANION_LOCKED','32c','-set',
                    'GAMESCOPE_COMPANION_LOCKED',str(int(bool(value))))
        if self.identity()!=identity or self.read()!=value:raise RuntimeError('companion_not_confirmed')

class Toggle:
    """Tracks only locks owned by us; never clears a pre-existing OS lock."""
    def __init__(self,companion,save=lambda data:None,owned=None,clock=time.monotonic):
        self.companion=companion;self.save=save;self.owned=owned or None
        self.clock=clock;self.down=False;self.last=-1e9
    def restore(self):
        owned=self.owned
        if not owned:return
        identity=self.companion.identity()
        if identity==owned and self.companion.read()==1:self.companion.set(0,identity)
        self.owned=None;self.save({})
    def press(self,value):
        if value==0:self.down=False;return False
        if value!=1 or self.down:return False
        self.down=True
        if self.clock()-self.last<.30:return False
        self.last=self.clock()
        identity=self.companion.identity();current=self.companion.read()
        if self.owned and self.owned!=identity:
            self.owned=None;self.save({})
        if current==1:
            if self.owned!=identity:return False
            self.companion.set(0,identity);self.owned=None;self.save({})
        else:
            # Persist intent first, so service recovery can undo a killed writer.
            self.owned=identity;self.save({'identity':identity})
            try:self.companion.set(1,identity)
            except Exception:
                try:self.restore()
                except Exception:pass
                raise
        return True


def key_capabilities(fd):
    data=bytearray(96)
    fcntl.ioctl(fd,0x80004521|(len(data)<<16),data,True)
    return {code for code in range(768) if data[code//8]&(1<<(code%8))}

def device_name(fd):
    data=bytearray(256)
    fcntl.ioctl(fd,0x80004506|(len(data)<<16),data,True)
    return bytes(data).split(b'\0',1)[0].decode('utf-8','replace')

def open_button(sysroot=Path('/sys/class/input'),devroot=Path('/dev/input')):
    for event in sorted(Path(sysroot).glob('event*')):
        try:
            name=(event/'device/name').read_text().strip()
            if name not in ('gpio-keys-ayn','AYN Key'):continue
            fd=os.open(Path(devroot)/event.name,os.O_RDONLY|os.O_NONBLOCK|os.O_CLOEXEC|os.O_NOFOLLOW)
            try:
                if not stat.S_ISCHR(os.fstat(fd).st_mode):raise OSError('not_character_device')
                if device_name(fd) not in ('gpio-keys-ayn','AYN Key') or key_capabilities(fd)!={F24}:
                    raise OSError('not_dedicated_button')
                fcntl.ioctl(fd,EVIOCGRAB,1)
                # Ignore a key that was already held when the option was enabled.
                data=bytearray(96);fcntl.ioctl(fd,0x80004518|(96<<16),data,True)
                held=bool(data[F24//8]&(1<<(F24%8)))
                return fd,held
            except Exception:os.close(fd);raise
        except OSError:continue
    raise RuntimeError('dedicated_button_unavailable')

def close_button(fd):
    if fd is not None:
        try:fcntl.ioctl(fd,EVIOCGRAB,0)
        except OSError:pass
        os.close(fd)

def restore_owned(state,companion=None):
    ownership=read_json(Path(state)/'ayn-owned.json').get('identity')
    if not isinstance(ownership,list):return
    toggle=Toggle(companion or Companion(),lambda data:atomic_json(Path(state)/'ayn-owned.json',data),ownership)
    toggle.restore()


REASON_TEXT = {
    'ip_dbus_unavailable': 'InputPlumber: ligação D-Bus indisponível. Consulta o diagnóstico.',
    'ip_not_running': 'InputPlumber não está em execução.',
    'ip_permission_denied': 'InputPlumber: acesso não autorizado. Não alteres permissões; consulta o diagnóstico.',
    'ip_api_unavailable': 'Esta versão do InputPlumber não expõe a interface necessária.',
    'ip_thor_not_found': 'InputPlumber: não foi encontrado um Thor com o botão AYN identificado.',
    'ip_device_ambiguous': 'InputPlumber: foram encontrados vários dispositivos compatíveis.',
    'ip_ayn_mapping_unavailable': 'InputPlumber: não foi possível confirmar a origem do botão AYN.',
    'ip_capabilities_invalid': 'InputPlumber: lista de capacidades inválida. Consulta o diagnóstico.',
    'ip_config_unverified': 'InputPlumber: mapa de origem do AYN não confirmado. Consulta o diagnóstico.',
    'ip_config_conflict': 'InputPlumber: configuração AYN personalizada ou ambígua; não foi alterada.',
    'ip_signal_target_unavailable': 'InputPlumber: o destino de eventos do botão não está disponível.',
    'ip_signal_target_changed': 'InputPlumber: o destino do botão mudou; a ligação será repetida.',
    'ip_identity_unavailable': 'InputPlumber: não foi possível confirmar a identidade do dispositivo.',
    'ip_session_changed': 'A sessão do InputPlumber mudou; a ligação será repetida.',
    'ip_profile_format': 'O perfil atual do comando não pode ser adaptado com segurança.',
    'ip_profile_targets': 'O perfil atual recria controladores; não foi alterado.',
    'ip_profile_changed': 'O perfil do comando mudou. Desativa e volta a ativar esta opção.',
    'ip_profile_not_confirmed': 'InputPlumber não confirmou a alteração do botão.',
    'ip_journal_invalid': 'Os dados de recuperação do botão são inválidos. Consulta o diagnóstico.',
    'ip_restore_failed': 'Não foi possível repor o perfil anterior. Consulta o diagnóstico.',
    'ip_event_overflow': 'A ligação do botão foi reiniciada após excesso de eventos.',
    'gamescope_unavailable': 'A sessão gráfica não está disponível. Verifica Steam e Plasma Mobile.',
    'screen_restore_failed': 'Não foi possível voltar a acender o ecrã. Usa Wake lower screen.',
    'dedicated_button_unavailable': 'O botão físico não está acessível e o InputPlumber não está disponível.',
    'toggle_failed': 'O comando do ecrã falhou. Consulta o diagnóstico.',
}

def reason_text(code):
    return REASON_TEXT.get(code, 'Botão AYN indisponível neste ambiente. Consulta o diagnóstico.')

class RawButton:
    """Compatibility only for a system WITHOUT InputPlumber."""
    backend = 'dedicated_gpio'
    def __init__(self):
        self.fd, self.held = open_button()
        self.dropped = False
    def check(self):
        if self.fd is None:
            raise RuntimeError('input_lost')
    def poll(self, timeout=.1):
        ready, _, _ = select.select([self.fd], [], [], timeout)
        if not ready:
            return []
        data = os.read(self.fd, EVENT.size * 32)
        if not data or len(data) % EVENT.size:
            raise OSError('input_lost')
        result = []
        for offset in range(0, len(data), EVENT.size):
            _, _, typ, code, value = EVENT.unpack_from(data, offset)
            if typ == 0 and code == 3:  # SYN_DROPPED: reopen, never replay events.
                raise OSError('input_lost')
            if typ == EV_KEY and code == F24:
                result.append(value)
        return result
    def close(self):
        if self.fd is not None:
            close_button(self.fd)
            self.fd = None


def open_source(state):
    from .ergo_inputplumber import Lease, RouteError
    try:
        return Lease.open(state)
    except RouteError as error:
        if error.code != 'ip_not_running':
            raise
        # Do not fight InputPlumber or bypass its permissions when it is present.
        return RawButton()


def run():
    from .ergo_inputplumber import RouteError, recover as recover_profile
    config, state = paths()
    state.mkdir(parents=True, exist_ok=True)
    lock = os.open(state/'ayn.lock', os.O_CREAT|os.O_RDWR|os.O_CLOEXEC|os.O_NOFOLLOW, 0o600)
    try:
        fcntl.flock(lock, fcntl.LOCK_EX|fcntl.LOCK_NB)
    except OSError:
        os.close(lock)
        return 0
    running = True
    source = None
    toggle = None
    next_check = 0.
    saved_status = None
    blocked = ''
    presses = toggles = 0

    def stop(*_):
        nonlocal running
        running = False
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)

    def report(status, reason=''):
        nonlocal saved_status
        record = {'status': status, 'reason': reason, 'pid': os.getpid(),
                  'backend': getattr(source, 'backend', ''),
                  'mapping_evidence': getattr(getattr(source, 'device', None), 'mapping_evidence', ''),
                  'button_presses': presses, 'screen_toggles': toggles}
        if record != saved_status:
            atomic_json(state/'ayn-status.json', record)
            saved_status = record

    def release():
        nonlocal source, toggle
        error = None
        # Wake a screen locked by us before relinquishing its button route.
        try:
            if toggle:
                toggle.restore()
            else:
                restore_owned(state)
        except Exception:
            error = 'screen_restore_failed'
        toggle = None
        try:
            if source:
                source.close()
            else:
                recover_profile(state)
        except Exception:
            error = 'ip_restore_failed'
        source = None
        return error

    def code(error, default):
        return error.code if isinstance(error, RouteError) else default

    try:
        release()  # Also recover a previous interrupted profile transaction.
        while running:
            now = time.monotonic()
            if now >= next_check:
                next_check = now + 1.
                enabled = read_json(config).get('ayn_toggle_enabled') is True
                if not enabled:
                    blocked = ''
                    problem = release()
                    report('unavailable' if problem else 'off', problem or '')
                elif blocked:
                    report('unavailable', blocked)
                elif source is None:
                    report('starting')
                    phase = 'gamescope_unavailable'
                    try:
                        c = Companion()
                        c.identity(); c.read()
                        phase = 'dedicated_button_unavailable'
                        source = open_source(state)
                        toggle = Toggle(c, lambda d: atomic_json(state/'ayn-owned.json', d))
                        toggle.down = bool(getattr(source, 'held', False))
                        report('ready')
                    except Exception as error:
                        reason = code(error, phase)
                        recovery = release()
                        if reason in ('ip_profile_changed', 'ip_profile_targets', 'ip_journal_invalid'):
                            blocked = reason
                        report('unavailable', recovery or reason)
                        next_check = now + 3.
                elif toggle:
                    try:
                        source.check()
                        identity = toggle.companion.identity()
                        current = toggle.companion.read()
                        if toggle.owned and (identity != toggle.owned or current == 0):
                            toggle.owned = None; toggle.save({})
                        report('screen_off' if toggle.owned and current else 'ready')
                    except Exception as error:
                        reason = code(error, 'gamescope_unavailable')
                        recovery = release()
                        # Respect external profile selection instead of repeatedly
                        # loading ours over it. User can explicitly toggle off/on.
                        if reason == 'ip_profile_changed':
                            blocked = reason
                        report('unavailable', recovery or reason)
                        next_check = now + 3.
            if source is None:
                time.sleep(.1)
                continue
            try:
                for value in source.poll(.1):
                    if value == 1:
                        presses += 1
                    if toggle.press(value):
                        toggles += 1
                    report('screen_off' if toggle.owned else 'ready')
            except Exception as error:
                reason = code(error, 'toggle_failed')
                recovery = release()
                report('unavailable', recovery or reason)
                next_check = time.monotonic() + 3.
    finally:
        problem = release()
        report('unavailable' if problem else 'off', problem or '')
        os.close(lock)
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--restore', action='store_true')
    args = parser.parse_args()
    if args.restore:
        from .ergo_inputplumber import recover
        errors = 0
        # Attempt both recoveries even if the display session disappeared.
        try:
            restore_owned(paths()[1])
        except Exception:
            errors += 1
        try:
            recover(paths()[1])
        except Exception:
            errors += 1
        return 1 if errors else 0
    return run()


if __name__ == '__main__':
    raise SystemExit(main())
