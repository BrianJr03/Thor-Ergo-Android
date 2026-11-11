#!/usr/bin/env python3
"""Read-only AYN diagnosis. No events read, EVIOCGRAB, screen writes or sudo.

Run from the complete distribution, including via its Diagnostics menu. This
reports the display guards, raw metadata and InputPlumber profile compatibility.
It does not enable the feature or alter the currently installed application.
Only a small allowlist of graphical environment fields is used, never printed.
"""
from __future__ import annotations

from contextlib import contextmanager
import errno
import fcntl
import json
import os
from pathlib import Path
import re
import stat
import struct
import subprocess
import sys

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
F24 = 194
ALLOWED_NAMES = {'gpio-keys-ayn', 'AYN Key'}
ENV_KEYS = {'XDG_RUNTIME_DIR', 'XAUTHORITY', 'DISPLAY', 'HOME', 'XDG_CONFIG_HOME', 'XDG_STATE_HOME', 'XDG_DATA_HOME'}
READ_PROPERTIES = ('GAMESCOPE_FOCUSED_APP', 'GAMESCOPE_COMPANION_LOCKED')
UNITS = ('thor-ergo-ayn.service', 'thor-ergo.service',
         'armada-bottom-screen.service', 'armada-bottom-gamescope.service',
         'gamescope-session-plus@steam.service')


def safe_text(value: object, limit: int = 900) -> str:
    text = str(value)
    homes = {str(Path.home()), os.environ.get('HOME', '')}
    for home in sorted(homes, key=len, reverse=True):
        if len(home) > 1:
            text = text.replace(home, '$HOME')
    text = re.sub(r'/var/home/[^/\s]+|/home/[^/\s]+', '$HOME', text)
    text = ''.join(c for c in text if c in '\n\t' or c.isprintable())
    return text[:limit]


def failure(exc: Exception) -> dict:
    result = {'ok': False, 'type': type(exc).__name__}
    if isinstance(exc, OSError):
        result['errno'] = exc.errno
        result['message'] = safe_text(exc.strerror or str(exc))
        if exc.filename:
            result['path'] = safe_text(exc.filename)
    elif isinstance(exc, subprocess.CalledProcessError):
        result['returncode'] = exc.returncode
        result['stderr'] = safe_text(exc.stderr or '')
        result['stdout'] = safe_text(exc.stdout or '')
    elif isinstance(exc, subprocess.TimeoutExpired):
        result['message'] = 'Query timed out; nothing was changed.'
    else:
        result['message'] = safe_text(exc)
    return result


def attempt(function, summarize=lambda value: value) -> dict:
    try:
        return {'ok': True, 'value': summarize(function())}
    except Exception as exc:
        return failure(exc)


def read_text(path: Path, limit: int = 8192) -> str:
    with path.open('rb') as stream:
        data = stream.read(limit)
    return data.rstrip(b'\0\r\n').decode('utf-8', 'replace')


def read_state(path: Path, keys: tuple[str, ...]) -> dict:
    try:
        if path.is_symlink() or path.stat().st_size > 65536:
            return {'available': False, 'reason': 'not_a_regular_small_file'}
        value = json.loads(read_text(path, 65536))
        if not isinstance(value, dict):
            return {'available': False, 'reason': 'not_a_mapping'}
        return {'available': True, **{k: value[k] for k in keys
                                     if k in value and isinstance(value[k], (str, int, bool, type(None)))}}
    except (OSError, ValueError) as exc:
        return failure(exc)


def service_state(unit: str, run=subprocess.run) -> dict:
    if unit not in UNITS:
        raise ValueError('Unlisted unit')
    env = dict(os.environ)
    env['XDG_RUNTIME_DIR'] = f'/run/user/{os.getuid()}'
    env['DBUS_SESSION_BUS_ADDRESS'] = 'unix:path=' + env['XDG_RUNTIME_DIR'] + '/bus'
    env['LC_ALL'] = 'C'
    try:
        result = run(['/usr/bin/systemctl', '--user', 'show', unit,
                      '--property=LoadState,ActiveState,SubState,MainPID,ExecMainStatus,Result'],
                     capture_output=True, text=True, timeout=3, check=False, env=env)
        if result.returncode:
            return {'ok': False, 'returncode': result.returncode,
                    'message': safe_text(result.stderr)}
        allowed = {'LoadState', 'ActiveState', 'SubState', 'MainPID', 'ExecMainStatus', 'Result'}
        fields = {}
        for line in result.stdout.splitlines():
            name, sep, value = line.partition('=')
            if sep and name in allowed:
                fields[name] = safe_text(value, 120)
        return {'ok': True, **fields}
    except (OSError, subprocess.SubprocessError) as exc:
        return failure(exc)


def helper_environment(pid: int, proc_root=Path('/proc')) -> dict[str, str] | None:
    """Use a running same-user helper's graphics context; never log its values."""
    if not isinstance(pid, int) or pid <= 1:
        return None
    root = Path(proc_root) / str(pid)
    try:
        status = read_text(root / 'status')
        uid = re.search(r'^Uid:\s+\d+\s+(\d+)', status, re.M)
        if not uid or int(uid[1]) != os.getuid():
            return None
        with (root / 'cmdline').open('rb') as f:
            argv = f.read(16384).split(b'\0')
        if not any(a.endswith(b'/tools/ayn.py') for a in argv):
            return None
        with (root / 'environ').open('rb') as f:
            data = f.read(1024 * 1024)
        selected = {}
        for entry in data.split(b'\0'):
            name, sep, value = entry.partition(b'=')
            key = name.decode('ascii', 'ignore')
            if sep and key in ENV_KEYS:
                selected[key] = value.decode('utf-8', 'surrogateescape')
        return selected
    except (OSError, ValueError):
        return None


@contextmanager
def graphics_context(selected: dict[str, str] | None):
    previous = {key: os.environ.get(key) for key in ENV_KEYS}
    try:
        if selected is not None:
            for key in ENV_KEYS:
                if key in selected:
                    os.environ[key] = selected[key]
                else:
                    os.environ.pop(key, None)
        yield
    finally:
        for key, value in previous.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value


def socket_status(path: Path) -> dict:
    try:
        info = path.stat()
        return {'present': True, 'socket': stat.S_ISSOCK(info.st_mode),
                'owned_by_current_user': info.st_uid == os.getuid(),
                'mode': oct(stat.S_IMODE(info.st_mode)), 'symlink': path.is_symlink()}
    except (OSError, ValueError) as exc:
        return failure(exc)


def sysfs_key_codes(text: str, word_bits: int | None = None) -> set[int]:
    """Linux bitmap_print_to_pagebuf writes most significant native word first."""
    bits = word_bits or struct.calcsize('L') * 8
    if bits not in (32, 64):
        raise ValueError('Unsupported word size')
    words = text.strip().split()
    if len(words) > 768 // bits:
        raise ValueError('Unexpected key bitmap length')
    codes = set()
    for index, word in enumerate(reversed(words)):
        if not re.fullmatch(r'[0-9a-fA-F]+', word) or len(word) > bits // 4:
            raise ValueError('Invalid key bitmap')
        value = int(word, 16)
        codes.update(index * bits + b for b in range(bits) if value & (1 << b))
    return codes


def ioctl_info(fd: int) -> dict:
    """Only EVIOCGNAME and EVIOCGBIT; no EVIOCGKEY, grab or event reads."""
    name = bytearray(256)
    fcntl.ioctl(fd, 0x80004506 | (len(name) << 16), name, True)
    display_name = bytes(name).split(b'\0', 1)[0].decode('utf-8', 'replace')
    data = bytearray(96)
    fcntl.ioctl(fd, 0x80004521 | (len(data) << 16), data, True)
    codes = {i for i in range(768) if data[i // 8] & (1 << (i % 8))}
    return {'name': safe_text(display_name, 120), 'dedicated_ayn_device': display_name in ALLOWED_NAMES,
            'has_F24': F24 in codes, 'only_F24': codes == {F24},
            'key_count': len(codes), 'other_key_codes': sorted(codes - {F24})[:24]}


def input_candidates(sysroot=Path('/sys/class/input'), devroot=Path('/dev/input')) -> dict:
    candidates = []
    scanned = 0
    for entry in sorted(Path(sysroot).glob('event*')):
        if not re.fullmatch(r'event\d+', entry.name):
            continue
        scanned += 1
        try:
            name = read_text(entry / 'device/name', 512).strip()
            bitmap = read_text(entry / 'device/capabilities/key', 1024).strip()
            codes = sysfs_key_codes(bitmap)
        except (OSError, ValueError):
            continue
        if name not in ALLOWED_NAMES and F24 not in codes and 'ayn' not in name.lower():
            continue
        row = {'event': entry.name, 'name': safe_text(name, 120),
               'dedicated_ayn_device': name in ALLOWED_NAMES,
               'has_F24': F24 in codes, 'only_F24': codes == {F24},
               'key_count': len(codes), 'other_key_codes': sorted(codes - {F24})[:24]}
        node = Path(devroot) / entry.name
        try:
            info = node.lstat()
            row['character_device'] = stat.S_ISCHR(info.st_mode)
            row['mode'] = oct(stat.S_IMODE(info.st_mode))
            row['readable_by_user'] = os.access(node, os.R_OK)
            row['writable_by_user'] = os.access(node, os.W_OK)
            if not stat.S_ISCHR(info.st_mode):
                row['open'] = {'ok': False, 'reason': 'not_character_device'}
            else:
                fd = os.open(node, os.O_RDONLY | os.O_NONBLOCK | os.O_CLOEXEC | os.O_NOFOLLOW)
                try:
                    if not stat.S_ISCHR(os.fstat(fd).st_mode):
                        raise OSError(errno.ENODEV, 'Not a character input device')
                    row['open'] = {'ok': True, **ioctl_info(fd)}
                finally:
                    os.close(fd)
        except (OSError, ValueError) as exc:
            row['open'] = failure(exc)
        candidates.append(row)
    return {'nodes_scanned': scanned, 'candidates': candidates,
            'note': 'Metadata only. No events read and no exclusive grab attempted.'}


def collect() -> dict:
    source = HERE / 'app/src'
    sys.path.insert(0, str(source))
    from armada_dual_screen_input.ergo_ayn import Companion
    from armada_dual_screen_input.gamescope_target import GamescopeTarget
    from armada_dual_screen_input.session_detect import detect_target_session

    report = {'diagnostic': 'Thor Ergo 1.0 AYN / Plasma / InputPlumber - read only',
              'safety': 'No screen change, input grab, event recording, service change, sudo or installation.',
              'device_model': attempt(lambda: read_text(Path('/sys/firmware/devicetree/base/model'))),
              'armada_version': attempt(lambda: read_text(Path('/usr/lib/armada/version')))}
    report['services'] = {unit: service_state(unit) for unit in UNITS}
    try:
        pid = int(report['services'][UNITS[0]].get('MainPID', '0'))
    except (TypeError, ValueError):
        pid = 0
    helper_env = helper_environment(pid)
    report['graphics_context'] = 'running AYN helper' if helper_env is not None else 'SSH process (helper context unavailable)'
    with graphics_context(helper_env):
        runtime = Path(os.environ.get('XDG_RUNTIME_DIR', f'/run/user/{os.getuid()}'))
        home = Path.home()
        config = Path(os.environ.get('XDG_CONFIG_HOME', home / '.config'))
        state = Path(os.environ.get('XDG_STATE_HOME', home / '.local/state'))
        report['helper_status'] = read_state(state / 'thor-ergo/ayn-status.json', ('status', 'reason', 'pid', 'backend', 'button_presses', 'screen_toggles', 'mapping_evidence'))
        report['enabled_setting'] = read_state(config / 'thor-ergo/ergo.json', ('ayn_toggle_enabled',))
        data = Path(os.environ.get('XDG_DATA_HOME', home / '.local/share'))
        report['installed_release'] = read_state(data / 'thor-ergo/installation.json', ('version', 'phase', 'product'))
        report['sockets'] = {name: socket_status(runtime / name) for name in (
            'gamescope-primary', 'gamescope-secondary', 'armada-plasma-bottom')}
        report['x11_socket_0'] = socket_status(Path('/tmp/.X11-unix/X0'))
        report['xauthority_variable_present'] = bool(os.environ.get('XAUTHORITY'))
        if os.environ.get('XAUTHORITY'):
            report['xauthority_file_readable'] = os.access(os.environ['XAUTHORITY'], os.R_OK)
        report['lower_plasma'] = attempt(
            lambda: detect_target_session(game_only=True),
            lambda target: {'detected': target is not None,
                            'mode': target.mode if target else None,
                            'wayland_display': target.wayland_display if target else None})
        report['primary_input_target'] = attempt(
            lambda: GamescopeTarget.resolve(runtime), lambda value: 'primary Wayland + EIS verified')
        companion = Companion()
        report['ayn_guard_identity'] = attempt(companion.identity, lambda value: 'all original identity checks passed')
        report['ayn_guard_display_read'] = attempt(companion.read)
        report['root_property_queries'] = {
            key: attempt(lambda key=key: companion._xprop('-notype', key), safe_text)
            for key in READ_PROPERTIES}
        report['inputs'] = input_candidates()
        from armada_dual_screen_input.ergo_inputplumber import probe
        report['inputplumber'] = attempt(probe)
        report['inputplumber_note'] = ('A raw gpio-keys-ayn node with mode 000 is expected when owned by '
                                       'InputPlumber. Version 1.0 uses its D-Bus API, not that node. Raw F24 capabilities may coexist with a translated QuickAccess2 event.')
    report['limitations'] = ('Exclusive ownership / another process holding the button is NOT tested: '
                             'that would require grabbing it. Profile write permission, physical button events and panel off/on are NOT tested.')
    return report


def main() -> int:
    if sys.platform != 'linux' or os.geteuid() == 0:
        print('Run this probe on the Thor as the normal user, without sudo.', file=sys.stderr)
        return 1
    print('\n========== BEGIN THOR AYN DIAGNOSTIC ==========\n', flush=True)
    try:
        report = collect()
        print(safe_text(json.dumps(report, ensure_ascii=False, indent=2), 30000), flush=True)
    except Exception as exc:
        print(json.dumps(failure(exc), ensure_ascii=False, indent=2), flush=True)
        print('\n========== END THOR AYN DIAGNOSTIC ==========\n', flush=True)
        return 1
    print('\n========== END THOR AYN DIAGNOSTIC ==========\n', flush=True)
    print('Diagnosis complete. No app, settings, services or display state were changed.', flush=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
