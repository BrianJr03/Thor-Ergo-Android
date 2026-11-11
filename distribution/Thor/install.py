#!/usr/bin/env python3
"""Standalone, transactional Thor Ergo installer. No sudo or package downloads.

Installation copies a COMPLETE bundled app to a new release directory. Native
imports/rendering are tested before any running panel is stopped. Configuration
and service state are snapshotted locally for rollback. Secrets are never asked
for here; SSH authentication belongs to the host's OpenSSH client.
"""
from __future__ import annotations
import argparse
import ast
import base64
from contextlib import contextmanager
import fcntl
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import platform
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import uuid
sys.dont_write_bytecode=True
from native_runtime import native_test_environment

VERSION='1.0'
HERE=Path(__file__).resolve().parent
UNITS=('thor-ergo.service','thor-ergo-ayn.service','thor-panel.service')

class InstallError(RuntimeError):pass

def digest(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def read_json(path):
    p=Path(path)
    if p.is_symlink():raise InstallError('A managed file is a symbolic link.')
    data=json.loads(p.read_text(encoding='utf-8'))
    if not isinstance(data,dict):raise InstallError('Invalid state file.')
    return data

def atomic(path,data,mode=0o600):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
    if path.is_symlink():raise InstallError('Refusing to replace a symbolic link.')
    if isinstance(data,dict):data=json.dumps(data,ensure_ascii=False,indent=2)+'\n'
    if isinstance(data,str):data=data.encode('utf-8')
    fd,tmp=tempfile.mkstemp(prefix='.thor-ergo-',dir=path.parent)
    try:
        with os.fdopen(fd,'wb') as f:f.write(data);f.flush();os.fsync(f.fileno())
        os.chmod(tmp,mode);os.replace(tmp,path)
    finally:Path(tmp).unlink(missing_ok=True)

def verify_tree(root):
    """Verify all payload files, reject added Python, symlinks and path escapes."""
    root=Path(root).resolve(strict=True)
    manifest=root/'SHA256SUMS'
    if manifest.is_symlink() or not manifest.is_file():raise InstallError('Missing payload manifest.')
    expected={}
    for line in manifest.read_text(encoding='utf-8').splitlines():
        if not line:continue
        try:sha,name=line.split('  ',1)
        except ValueError:raise InstallError('Malformed manifest.')
        pure=PurePosixPath(name)
        if (len(sha)!=64 or any(c not in '0123456789abcdef' for c in sha)
                or pure.is_absolute() or '..' in pure.parts or '\\' in name
                or name in expected or name=='SHA256SUMS'):
            raise InstallError('Unsafe or duplicate manifest entry.')
        expected[name]=sha
    actual=set()
    for p in root.rglob('*'):
        if p.is_symlink():raise InstallError('Symbolic links are not allowed in the bundle.')
        if not p.is_file():continue
        name=p.relative_to(root).as_posix()
        if name=='SHA256SUMS':continue
        # Bytecode is never bundled; do not silently accept extra executable files.
        actual.add(name)
        if name not in expected or digest(p)!=expected[name]:
            raise InstallError('Payload integrity failed: '+name)
        if p.suffix=='.py':ast.parse(p.read_text(encoding='utf-8'),filename=name)
    if actual!=set(expected):raise InstallError('Payload files are missing.')
    return len(actual)

def locations(home=None,env=None):
    env=os.environ if env is None else env;home=Path.home() if home is None else Path(home)
    data=Path(env.get('XDG_DATA_HOME',home/'.local/share'))
    config=Path(env.get('XDG_CONFIG_HOME',home/'.config'))
    state=Path(env.get('XDG_STATE_HOME',home/'.local/state'))
    if any(not p.is_absolute() or any(c in str(p) for c in '\n\r\0') for p in (home,data,config,state)):
        raise InstallError('Invalid home/XDG path.')
    root=data/'thor-ergo'
    return {'home':home,'root':root,'releases':root/'releases','transactions':root/'transactions',
            'installation':root/'installation.json','config':config/'thor-ergo/config.json',
            'settings':config/'thor-ergo/ergo.json','old_settings':config/'thor-panel/ergo.json',
            'unit':config/'systemd/user/thor-ergo.service',
            'ayn_unit':config/'systemd/user/thor-ergo-ayn.service',
            'bin':home/'.local/bin/thor-ergo','desktop':data/'applications/thor-ergo.desktop',
            'ready':state/'thor-ergo/ready.json','ayn_state':state/'thor-ergo',
            'state':state/'thor-ergo'}

MANAGED=('installation','config','settings','unit','ayn_unit','bin','desktop')

def snapshot_files(p):
    result={}
    for key in MANAGED:
        path=p[key]
        if path.is_symlink():raise InstallError('A managed path is a symbolic link: '+key)
        if path.exists():
            if not path.is_file() or path.stat().st_size>4*1024*1024:raise InstallError('Invalid managed file: '+key)
            result[key]={'data':base64.b64encode(path.read_bytes()).decode('ascii'),
                         'mode':stat.S_IMODE(path.stat().st_mode)}
        else:result[key]=None
    return result

def restore_files(p,files):
    if set(files)!=set(MANAGED):raise InstallError('Invalid rollback snapshot.')
    for key in MANAGED:
        item=files[key]
        if item is None:
            if p[key].is_symlink():raise InstallError('Managed path changed to a symbolic link.')
            p[key].unlink(missing_ok=True)
        else:atomic(p[key],base64.b64decode(item['data'],validate=True),int(item['mode'])&0o777)

def quoted(value):
    """systemd ExecStart argument escaping, including literal percent signs."""
    return '"'+str(value).replace('\\','\\\\').replace('"','\\"').replace('%','%%')+'"'

class System:
    def ctl(self,*args,check=True):
        try:r=subprocess.run(['/usr/bin/systemctl','--user',*args],capture_output=True,text=True,timeout=25)
        except (OSError,subprocess.TimeoutExpired) as e:raise InstallError('User service manager unavailable.') from e
        if check and r.returncode:raise InstallError('systemctl operation failed: '+' '.join(args))
        return r
    def ensure(self):
        if sys.platform!='linux' or os.geteuid()==0:raise InstallError('Run on the Thor as its normal desktop user, without sudo.')
        if platform.machine() not in ('aarch64','arm64'):raise InstallError('The target must be ARM64.')
        model=Path('/sys/firmware/devicetree/base/model').read_bytes().rstrip(b'\0').decode('utf-8')
        if not model.startswith('AYN Thor'):raise InstallError('This distribution is for AYN Thor devices.')
        release=Path('/etc/os-release').read_text()
        if not any(l.strip() in ('ID=armada','ID="armada"') for l in release.splitlines()):
            raise InstallError('The target is not identified as ArmadaOS.')
        runtime=Path('/run/user')/str(os.getuid())
        if not runtime.is_dir() or runtime.stat().st_uid!=os.getuid() or not (runtime/'bus').exists():
            raise InstallError('Log in to Steam Game Mode before installing.')
        os.environ['XDG_RUNTIME_DIR']=str(runtime)
        os.environ['DBUS_SESSION_BUS_ADDRESS']='unix:path='+str(runtime/'bus')
        if not os.access('/dev/uinput',os.R_OK|os.W_OK):
            raise InstallError('No user access to /dev/uinput. The installer does not change permissions.')
        self.ctl('show-environment')
    def require_bottom_plasma(self,bundle):
        """Read-only preflight BEFORE copying/stopping the existing app.

        Steam belongs on the upper display. This app needs Armada's bottom
        Plasma session, normally Plasma Mobile, not a second Steam UI.
        """
        code=("import sys;sys.path.insert(0,sys.argv[1]);"
              "from armada_dual_screen_input.session_detect import detect_target_session;"
              "t=detect_target_session(game_only=True);"
              "sys.exit(0 if t is not None and t.mode=='game' "
              "and t.wayland_display=='armada-plasma-bottom' else 3)")
        try:
            result=subprocess.run([sys.executable,'-I','-B','-c',code,str(bundle/'app/src')],
                capture_output=True,text=True,timeout=12,check=False)
        except (OSError,subprocess.TimeoutExpired) as error:
            raise InstallError('Could not verify the bottom Plasma session. No running app was changed.') from error
        if result.returncode:
            raise InstallError('The bottom Plasma session is not available. Keep Steam Game Mode started '
                'and unlocked on the TOP screen; enable Plasma Mobile on the BOTTOM screen in '
                'Armada Control (on the top screen), then retry. Do NOT switch the whole Thor '
                'to Desktop Mode. No running app was changed.')
        print('[Thor] Bottom Plasma session detected. Steam remains on the upper screen.',flush=True)

    def native_test(self,bundle):
        env=native_test_environment(bundle/'app')
        r=subprocess.run([sys.executable,'-B',str(bundle/'selftest.py'),'--native'],env=env,timeout=180)
        if r.returncode:raise InstallError('Native tests failed. The running app has not been stopped or changed.')
    def statuses(self):
        result={}
        for unit in UNITS:
            load=self.ctl('show',unit,'-p','LoadState','--value',check=False).stdout.strip()
            active=self.ctl('is-active',unit,check=False).stdout.strip()=='active'
            enable=self.ctl('is-enabled',unit,check=False).stdout.strip()
            if enable in ('masked','masked-runtime'):raise InstallError('A relevant service is masked; no changes made: '+unit)
            result[unit]={'loaded':load=='loaded','active':active,
                          'enabled':enable in ('enabled','enabled-runtime'),'runtime':enable=='enabled-runtime'}
        return result
    def stop(self,unit):self.ctl('stop',unit,check=False)
    def ready(self,p,app):
        deadline=time.monotonic()+35
        while time.monotonic()<deadline:
            try:
                ready=read_json(p['ready'])
                if (ready.get('version')==VERSION and ready.get('rendered') is True
                    and Path(ready.get('app','')).resolve()==app.resolve()
                    and ready.get('width',0)>=240 and ready.get('height',0)>=200
                    and self.ctl('is-active',UNITS[0],check=False).returncode==0):
                    os.kill(int(ready['pid']),0)
                    time.sleep(1)
                    os.kill(int(ready['pid']),0)
                    if self.ctl('is-active',UNITS[0],check=False).returncode==0:return
            except (OSError,ValueError,TypeError,KeyError):pass
            time.sleep(.5)
        raise InstallError('The UI did not confirm a live first frame. Automatic rollback requested.')


    def activation_diagnostics(self,p,since,reason):
        """Bounded technical startup report, captured before restoring old units.

        Does not read credentials, SSH variables, input events or global logs.
        Home paths are redacted; the local report is private (mode 0600).
        """
        sections=['Thor Ergo '+VERSION+' activation failure',str(reason)]
        for unit in UNITS[:2]:
            try:
                result=self.ctl('show',unit,
                    '--property=LoadState,ActiveState,SubState,Result,ExecMainCode,ExecMainStatus,NRestarts',
                    check=False)
                sections.append(unit+'\n'+(result.stdout or result.stderr or '(no status)').strip())
            except Exception as error:
                sections.append(unit+': status unavailable ('+type(error).__name__+')')
        try:
            result=subprocess.run(['/usr/bin/journalctl','--user','--no-pager','-o','cat',
                '-u',UNITS[0],'-u',UNITS[1],'--since','@'+str(int(since)),
                '-n','80'],capture_output=True,text=True,errors='replace',timeout=8,
                env={**os.environ,'SYSTEMD_COLORS':'0','SYSTEMD_PAGER':''})
            sections.append('Recent startup log\n'+(result.stdout or result.stderr or '(no journal entries)').strip())
        except (OSError,subprocess.TimeoutExpired) as error:
            sections.append('Startup journal unavailable ('+type(error).__name__+')')
        report='\n\n'.join(sections)
        for home in sorted({str(p['home']),str(p['home'].resolve()),str(Path.home())},key=len,reverse=True):
            if len(home)>1:report=report.replace(home,'$HOME')
        report=''.join(c for c in report if c in '\n\t' or (c.isprintable() and c!='\x1b'))
        report=report[:24000]+'\n'
        print('[Thor] Startup details (before rollback):\n'+report,flush=True)
        try:
            atomic(p['state']/'last-activation-error.txt',report,0o600)
            print('[Thor] Startup report saved in the Thor Ergo state directory.',flush=True)
        except (OSError,InstallError):
            print('[Thor] Could not save the report; the details above are still available.',flush=True)


class Manager:
    def __init__(self,source=HERE,p=None,system=None):
        self.source=Path(source);self.p=p or locations();self.system=system or System()
    @contextmanager
    def lock(self):
        root=self.p['root']
        if root.is_symlink():raise InstallError('Install root is a symbolic link.')
        root.mkdir(parents=True,exist_ok=True)
        fd=os.open(root/'.installer.lock',os.O_CREAT|os.O_RDWR|os.O_NOFOLLOW,0o600)
        try:
            try:fcntl.flock(fd,fcntl.LOCK_EX|fcntl.LOCK_NB)
            except OSError:raise InstallError('Another installer is running.')
            yield
        finally:os.close(fd)
    def check(self):
        self.system.ensure()
        n=verify_tree(self.source)
        self.system.require_bottom_plasma(self.source)
        p=self.p
        for name in MANAGED:
            if p[name].is_symlink():raise InstallError('Managed path is a symbolic link: '+name)
        if (p['root']/'recovery.json').exists():
            raise InstallError('A previous transaction was interrupted. Run undo before reinstalling.')
        for name in ('unit','ayn_unit'):
            dropins=p[name].with_name(p[name].name+'.d')
            if dropins.exists() and any(dropins.iterdir()):
                raise InstallError('Custom service overrides need review before upgrading: '+name)
        state=read_json(p['installation']) if p['installation'].exists() else None
        if state:
            if state.get('product')!='thor-ergo-standalone' or state.get('phase')!='installed':
                raise InstallError('Existing independent installation has an unrecognized state.')
            for key in ('unit','ayn_unit','bin','desktop'):
                if not p[key].exists() or digest(p[key])!=state.get('managed_hashes',{}).get(key):
                    raise InstallError('Managed launcher or service was changed; inspect it before upgrading: '+key)
        elif any(p[k].exists() for k in ('unit','ayn_unit','bin','desktop')):
            raise InstallError('An unrecognized Thor Ergo launcher/service already exists. Nothing replaced.')
        print(f'[Thor] Verified complete bundle: {n} files. No earlier app is required.',flush=True)
        return state
    def import_settings(self):
        p=self.p
        sys.path.insert(0,str(self.source/'app/src'))
        from armada_dual_screen_input.ergo_layout import normalize_settings
        source={}
        for path in (p['old_settings'],p['settings']):
            if path.is_file() and not path.is_symlink():
                try:source=read_json(path)
                except (OSError,ValueError,InstallError):pass
        values=normalize_settings(source)
        # Explicit opt-in in every public installation; never unexpectedly grab.
        values['ayn_toggle_enabled']=False
        return values
    def rollback(self,snapshot):
        p=self.p;s=self.system
        if set(snapshot['services'])!=set(UNITS):raise InstallError('Invalid service snapshot.')
        for unit in UNITS[:2]:
            s.stop(unit);s.ctl('disable',unit,check=False)
        restore_files(p,snapshot['files'])
        s.ctl('daemon-reload')
        for unit,info in snapshot['services'].items():
            if not info['loaded']:continue
            if info['enabled']:
                args=['enable']+(['--runtime'] if info.get('runtime') else [])+[unit]
                s.ctl(*args)
            else:s.ctl('disable',unit,check=False)
        for unit,info in snapshot['services'].items():
            if info['active']:s.ctl('start',unit)
        print('[Thor] Previous files, preferences and service state restored.',flush=True)
    def install(self):
        p=self.p;s=self.system
        with self.lock():
            prior=self.check();services=s.statuses();settings=self.import_settings()
            if prior is None and any(services[u]['loaded'] for u in UNITS[:2]):
                raise InstallError('An unrecognized Thor Ergo service is already loaded.')
            p['releases'].mkdir(parents=True,exist_ok=True)
            target=p['releases']/(VERSION+'-'+uuid.uuid4().hex[:10])
            print('[Thor] Copying the complete independent application...',flush=True)
            shutil.copytree(self.source,target,symlinks=False)
            verify_tree(target)
            print('[Thor] Testing logic, native imports and offscreen drawing; no input is sent.',flush=True)
            try:s.native_test(target)
            except Exception:
                shutil.rmtree(target,ignore_errors=True);raise
            tx=p['transactions']/(time.strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:8]+'.json')
            snapshot={'schema':1,'product':'thor-ergo-standalone','files':snapshot_files(p),
                      'services':services,'new_release':str(target),'phase':'prepared'}
            atomic(tx,snapshot)
            app=target/'app'
            # Persist recovery BEFORE stopping any service, including for power loss.
            atomic(p['root']/'recovery.json',{'transaction':str(tx)})
            # From this point forward every error triggers snapshot restoration.
            activation_since=None
            try:
                for unit in UNITS:s.stop(unit)
                if services[UNITS[2]]['loaded']:s.ctl('disable',UNITS[2])
                atomic(p['settings'],settings)
                # Original engine preferences in a private configuration file.
                input_config={k:settings[k] for k in ('sensitivity','scroll_speed','tap_to_click','glide','friction','scroll_scale')}
                input_config['keyboard_language']=settings['language']
                atomic(p['config'],input_config)
                common='[Unit]\nDescription={desc}\nAfter=graphical-session.target\n\n[Service]\nType=simple\nEnvironment=XDG_RUNTIME_DIR=%t\nEnvironment=PYTHONDONTWRITEBYTECODE=1\nEnvironment=PYTHONUNBUFFERED=1\n{extra}ExecStart={exec}\nRestart=on-failure\nRestartSec=3\nTimeoutStopSec=8\nKillMode=control-group\n\n[Install]\nWantedBy=default.target\n'
                atomic(p['unit'],common.format(desc='Thor Ergo keyboard and touchpad',extra='',
                    exec='/usr/bin/python3 -u -B '+quoted(app/'tools/run.py')+' --run'),0o644)
                helper=app/'tools/ayn.py'
                atomic(p['ayn_unit'],common.format(desc='Thor Ergo optional AYN button via InputPlumber',
                    extra='ExecStopPost=/usr/bin/python3 -B '+quoted(helper)+' --restore\n',
                    exec='/usr/bin/python3 -u -B '+quoted(helper)).replace('TimeoutStopSec=8','TimeoutStopSec=20'),0o644)
                import shlex
                atomic(p['bin'],'#!/bin/sh\nexec /usr/bin/python3 -B '+shlex.quote(str(target/'install.py'))+' "$@"\n',0o755)
                desktop='[Desktop Entry]\nType=Application\nName=Thor Ergo\nComment=Independent multilingual keyboard and touchpad\nExec=/usr/bin/python3 -B '+quoted(target/'install.py').replace('%%','%')+' start\nTerminal=false\nCategories=Utility;Accessibility;\n'
                atomic(p['desktop'],desktop,0o644)
                s.ctl('daemon-reload')
                if prior:autostart=services[UNITS[0]]['enabled']
                elif services[UNITS[2]]['loaded']:autostart=services[UNITS[2]]['enabled']
                else:autostart=True
                for unit in UNITS[:2]:
                    s.ctl('enable' if autostart else 'disable',unit)
                p['ready'].unlink(missing_ok=True)
                # Failed starts from an earlier attempt must not poison this one.
                s.ctl('reset-failed',*UNITS[:2],check=False)
                activation_since=time.time()
                s.ctl('start',UNITS[1]);s.ctl('start',UNITS[0])
                s.ready(p,app)
                state={'schema':1,'product':'thor-ergo-standalone','phase':'installed','version':VERSION,
                       'release':str(target),'transaction':str(tx),'autostart':autostart,
                       'managed_hashes':{k:digest(p[k]) for k in ('unit','ayn_unit','bin','desktop')}}
                atomic(p['installation'],state)
                snapshot['phase']='committed';atomic(tx,snapshot)
                (p['root']/'recovery.json').unlink(missing_ok=True)
            except BaseException as failure:
                # Capture the new service's error BEFORE rollback reloads/removes
                # its unit. A diagnostic failure must never prevent rollback.
                if activation_since is not None:
                    try:s.activation_diagnostics(p,activation_since,str(failure))
                    except Exception:
                        print('[Thor] Could not collect activation details; continuing rollback.',flush=True)
                try:
                    self.rollback(snapshot)
                    (p['root']/'recovery.json').unlink(missing_ok=True)
                except Exception as recovery:
                    print('[Thor] Automatic recovery was incomplete. Use undo; the snapshot is preserved.',flush=True)
                    atomic(p['root']/'recovery.json',{'transaction':str(tx)})
                raise
            print('[Thor] Thor Ergo '+VERSION+' installed. The interface confirmed a live first frame.',flush=True)
            print('[Thor] AYN toggle is OFF by default. Enable it in Settings > AYN button.',flush=True)
    def undo(self):
        p=self.p
        self.system.ensure()
        with self.lock():
            recovery=p['root']/'recovery.json'
            state=read_json(recovery if recovery.exists() else p['installation'])
            tx=Path(state['transaction']).resolve(strict=True)
            if tx.parent!=p['transactions'].resolve():raise InstallError('Invalid rollback transaction location.')
            snapshot=read_json(tx)
            if snapshot.get('product')!='thor-ergo-standalone':raise InstallError('Invalid rollback owner.')
            self.rollback(snapshot);recovery.unlink(missing_ok=True)
    def status(self):
        p=self.p
        if p['installation'].exists():
            state=read_json(p['installation']);print('Thor Ergo:',state.get('version'),'/',state.get('phase'))
        else:print('No independent Thor Ergo installation is registered.')
        for unit in UNITS[:2]:
            r=self.system.ctl('status',unit,'--no-pager','--lines=10',check=False)
            print(r.stdout or r.stderr)
        file=p['ayn_state']/'ayn-status.json'
        if file.exists():print('AYN:',read_json(file).get('status'),read_json(file).get('reason',''))
        report=p['state']/'last-activation-error.txt'
        if report.is_file() and not report.is_symlink():
            with report.open(encoding='utf-8',errors='replace') as stream:
                print('Last recorded activation failure (may be from an earlier attempt):\n'+stream.read(24000))

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',nargs='?',default='status',choices=('check','install','undo','uninstall','status','diagnostics','start','stop','wake'))
    args=parser.parse_args();m=Manager()
    try:
        if args.action=='install':m.install()
        elif args.action in ('undo','uninstall'):m.undo()
        elif args.action=='check':m.check()
        elif args.action=='status':m.status()
        elif args.action=='diagnostics':
            from diagnose_ayn import main as diagnose
            return diagnose()
        elif args.action in ('start','stop'):
            m.system.ctl(args.action,UNITS[1]);m.system.ctl(args.action,UNITS[0])
        elif args.action=='wake':
            sys.path.insert(0,str(HERE/'app/src'))
            from armada_dual_screen_input.ergo_ayn import restore_owned
            restore_owned(m.p['ayn_state']);print('Restored any companion lock owned by Thor Ergo.')
    except (InstallError,OSError,ValueError,KeyError,SyntaxError,subprocess.TimeoutExpired) as e:
        print('ERROR:',str(e),file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print('Canceled.',file=sys.stderr);return 130
    return 0

if __name__=='__main__':raise SystemExit(main())
