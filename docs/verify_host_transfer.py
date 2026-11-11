"""POSIX installer regression tests: real scripts/ZIP extraction, fake SSH only.

No network connections, services or input devices. Runs both entry scripts on
Linux sh, dash and bash; macOS utilities/Gatekeeper are NOT tested by this suite.
An inert installer is substituted into a TEMPORARY copy of the shipped payload.
"""
from pathlib import Path
import hashlib, io, json, os, pty, select, shutil, signal, stat, subprocess, sys, tempfile, time, unittest, zipfile
ROOT=Path(__file__).resolve().parents[1]
PYTHON=sys.executable
FAKE='''#!{python}
import os,sys,subprocess,json,time,signal
from pathlib import Path
args=sys.argv[1:]
Path(os.environ['HOST_ARGS']).write_text(json.dumps(args))
assert '-T' in args
assert 'StrictHostKeyChecking=ask' in args
assert 'BatchMode=no' in args
assert 'ForwardAgent=no' in args
assert 'ForwardX11=no' in args
assert 'ClearAllForwardings=yes' in args
assert args[-2]==os.environ.get('EXPECT_DEST','armada@203.0.113.10')
if os.environ.get('FAKE_AUTH'):
    with open('/dev/tty','w') as tty_out, open('/dev/tty','r') as tty_in:
        tty_out.write('FAKE SSH password: ');tty_out.flush()
        answer=tty_in.readline().rstrip('\\n')
        if answer!='test-only':raise SystemExit(255)
if os.environ.get('FAKE_SSH_CODE'):raise SystemExit(int(os.environ['FAKE_SSH_CODE']))
if os.environ.get('FAKE_HANG'):
    def stop(*_):
        Path(os.environ['HOST_STOP']).write_text('terminated')
        raise SystemExit(143)
    signal.signal(signal.SIGTERM,stop)
    Path(os.environ['HOST_STARTED']).write_text(str(os.getpid()))
    while True:time.sleep(.05)
raise SystemExit(subprocess.call(args[-1],shell=True))
'''.format(python=PYTHON)
REMOTE='''from pathlib import Path
import sys,os,json
root=Path(__file__).resolve().parent
assert (root/'app/ERGO_VERSION').read_text().strip()=='1.0'
assert (root/'app/data/pinyin-index.json').is_file()
assert (root/'app/runtime/usr/lib64/libgtk-layer-shell.so.0').is_file()
assert (root/'selftest.py').is_file()
Path(os.environ['HOST_RESULT']).write_text(json.dumps({'action':sys.argv[1], 'files':len(list(root.rglob('*')))}))
raise SystemExit(int(os.environ.get('REMOTE_CODE','0')))
'''

def rehash_manifest(root):
    manifest=root/'Installers/payload.sha256'
    manifest.write_text(''.join(hashlib.sha256((root/p).read_bytes()).hexdigest()+'  '+p+'\n' for p in ('Payload/Thor.zip','Thor/transfer.py')),encoding='ascii')

class HostTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory(prefix='thor-host-tests-')
        cls.base=Path(cls.temp.name)
        cls.dist=cls.base/"folder with spaces ' & [layout] é"
        shutil.copytree(ROOT,cls.dist)
        # Replace ONLY a temporary payload's remote installer; never the real one.
        payload=cls.dist/'Payload/Thor.zip'
        buf=io.BytesIO()
        with zipfile.ZipFile(payload) as old,zipfile.ZipFile(buf,'w',zipfile.ZIP_DEFLATED) as new:
            for item in old.infolist():
                new.writestr(item,REMOTE.encode() if item.filename=='install.py' else old.read(item))
        payload.write_bytes(buf.getvalue())
        rehash_manifest(cls.dist)
        cls.bin=cls.base/'fake-bin';cls.bin.mkdir()
        (cls.bin/'python3').symlink_to(PYTHON)
        (cls.bin/'ssh').write_text(FAKE);(cls.bin/'ssh').chmod(0o755)
    @classmethod
    def tearDownClass(cls):cls.temp.cleanup()
    def setUp(self):
        for n in ('args.json','result.json','started','stopped'):
            (self.base/n).unlink(missing_ok=True)
        self.env=dict(os.environ,PATH=str(self.bin)+':'+os.environ['PATH'],HOST_ARGS=str(self.base/'args.json'),
             HOST_RESULT=str(self.base/'result.json'),HOST_STARTED=str(self.base/'started'),HOST_STOP=str(self.base/'stopped'),TMPDIR=str(self.base))
    def launch(self,entries='1\n203.0.113.10\n\n\n',launcher='Install-Linux.sh',shell='/bin/sh',env=None,dist=None):
        return subprocess.run([shell,str((dist or self.dist)/launcher)],input=entries,capture_output=True,text=True,
                              env=env or self.env,timeout=20,cwd='/')
    def check_run(self,launcher,shell,choice,action):
        p=self.launch(f'{choice}\n203.0.113.10\n\n\n',launcher,shell)
        self.assertEqual(p.returncode,0,(p.stdout,p.stderr))
        self.assertEqual(json.loads((self.base/'result.json').read_text())['action'],action)
        args=json.loads((self.base/'args.json').read_text())
        self.assertEqual(args[args.index('-p')+1],'22')
        self.assertIn(' zip ',args[-1]);self.assertFalse(list(self.base.glob('thor-ergo-public-*')))
    def test_dedicated_ayn_diagnostic_windows_source(self):
        text=(ROOT/'Diagnose-AYN-Windows.cmd').read_text()
        self.assertIn('-AynDiagnostic',text)
        self.assertIn('install-windows.ps1',text)
    def test_dedicated_ayn_diagnostic_linux(self):
        p=self.launch('203.0.113.10\n\n\n',launcher='Diagnose-AYN-Linux.sh')
        self.assertEqual(p.returncode,0,(p.stdout,p.stderr))
        self.assertEqual(json.loads((self.base/'result.json').read_text())['action'],'diagnostics')
        self.assertNotIn('Operation [1]',p.stdout)
        self.assertIn('BOTTOM screen: Plasma Mobile',p.stdout)
    def test_dedicated_ayn_diagnostic_mac(self):
        p=self.launch('203.0.113.10\n\n\n',launcher='Diagnose-AYN-macOS.command')
        self.assertEqual(p.returncode,0,(p.stdout,p.stderr))
        self.assertEqual(json.loads((self.base/'result.json').read_text())['action'],'diagnostics')
        self.assertNotIn('Operation [1]',p.stdout)
    def test_both_screen_requirements_before_connection(self):
        p=self.launch()
        self.assertEqual(p.returncode,0,p.stderr)
        self.assertIn('TOP screen: Steam Game Mode started and unlocked.',p.stdout)
        self.assertIn('BOTTOM screen: Plasma Mobile must be enabled in Armada Control on the top screen.',p.stdout)

    def test_auth_hint_and_no_secrets_in_argv(self):
        p=self.launch();self.assertEqual(p.returncode,0,p.stderr)
        self.assertIn('SSH password (default: "armada")',p.stdout)
        args=json.loads((self.base/'args.json').read_text())
        self.assertNotIn('-pw',args);self.assertNotIn('sshpass',' '.join(args))
    def test_custom_username_port_ipv6(self):
        env=dict(self.env,EXPECT_DEST='tester@2001:db8::1')
        p=self.launch('1\n[2001:db8::1]\ntester\n02222\n',env=env)
        self.assertEqual(p.returncode,0,(p.stdout,p.stderr))
        args=json.loads((self.base/'args.json').read_text());self.assertEqual(args[args.index('-p')+1],'2222')
    def test_ipv6_scope_id(self):
        env=dict(self.env,EXPECT_DEST='armada@fe80::1%en0')
        p=self.launch('1\nfe80::1%en0\n\n\n',env=env)
        self.assertEqual(p.returncode,0,(p.stdout,p.stderr))
    def test_remote_error_preserved(self):
        p=self.launch(env=dict(self.env,REMOTE_CODE='7'));self.assertEqual(p.returncode,7,(p.stdout,p.stderr))
        self.assertFalse(list(self.base.glob('thor-ergo-public-*')))
    def test_connection_or_auth_failure_preserved(self):
        p=self.launch(env=dict(self.env,FAKE_SSH_CODE='255'));self.assertEqual(p.returncode,255,(p.stdout,p.stderr))
        self.assertFalse((self.base/'result.json').exists())
    def test_eof_fails_without_connecting(self):
        p=self.launch('');self.assertNotEqual(p.returncode,0);self.assertFalse((self.base/'args.json').exists())
    def test_invalid_operation_does_not_connect(self):
        p=self.launch('bad\n');self.assertNotEqual(p.returncode,0);self.assertFalse((self.base/'args.json').exists())
    def test_modified_payload_rejected_before_ssh(self):
        with tempfile.TemporaryDirectory(dir=self.base) as d:
            dist=Path(d)/'modified';shutil.copytree(self.dist,dist)
            p=dist/'Payload/Thor.zip';p.write_bytes(p.read_bytes()+b'modified')
            result=self.launch(dist=dist)
            self.assertNotEqual(result.returncode,0);self.assertIn('checksum mismatch',result.stderr)
            self.assertFalse((self.base/'args.json').exists())
    def test_modified_receiver_rejected_before_ssh(self):
        with tempfile.TemporaryDirectory(dir=self.base) as d:
            dist=Path(d)/'modified';shutil.copytree(self.dist,dist)
            p=dist/'Thor/transfer.py';p.write_bytes(p.read_bytes()+b'\n# changed\n')
            result=self.launch(dist=dist)
            self.assertNotEqual(result.returncode,0);self.assertIn('checksum mismatch',result.stderr)
            self.assertFalse((self.base/'args.json').exists())
    def test_missing_payload_explains_extract_complete_zip(self):
        with tempfile.TemporaryDirectory(dir=self.base) as d:
            dist=Path(d)/'missing';shutil.copytree(self.dist,dist);(dist/'Payload/Thor.zip').unlink()
            result=self.launch(dist=dist)
            self.assertNotEqual(result.returncode,0);self.assertIn('complete folder',result.stderr)
            self.assertFalse((self.base/'args.json').exists())
    def test_missing_ssh_fails_before_questions(self):
        with tempfile.TemporaryDirectory(dir=self.base) as d:
            bin=Path(d)
            # Wrapper path resolution needs dirname before checking tools.
            (bin/'dirname').symlink_to(shutil.which('dirname'))
            result=self.launch(env=dict(self.env,PATH=str(bin)))
            self.assertNotEqual(result.returncode,0);self.assertIn('Missing standard host tool: ssh',result.stderr)
    def test_shasum_fallback_without_sha256sum(self):
        with tempfile.TemporaryDirectory(dir=self.base) as d:
            bin=Path(d)
            for tool in ('dirname','awk','base64','tr','wc','shasum','python3'):
                (bin/tool).symlink_to(PYTHON if tool=='python3' else shutil.which(tool))
            (bin/'ssh').write_text(FAKE);(bin/'ssh').chmod(0o755)
            result=self.launch(env=dict(self.env,PATH=str(bin)))
            self.assertEqual(result.returncode,0,(result.stdout,result.stderr))
    def check_interrupt(self,sig):
        env=dict(self.env,FAKE_HANG='1')
        p=subprocess.Popen(['/bin/sh',str(self.dist/'Install-Linux.sh')],stdin=subprocess.PIPE,
                           stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env,text=True,start_new_session=True)
        p.stdin.write('1\n203.0.113.10\n\n\n');p.stdin.flush()
        deadline=time.monotonic()+10
        while not (self.base/'started').exists() and time.monotonic()<deadline:time.sleep(.02)
        self.assertTrue((self.base/'started').exists())
        os.kill(p.pid,sig)
        out,err=p.communicate(timeout=10)
        self.assertEqual(p.returncode,128+sig,(out,err))
        self.assertTrue((self.base/'stopped').exists(),(out,err))
        self.assertFalse((self.base/'result.json').exists())
    def test_sigterm_stops_ssh(self):self.check_interrupt(signal.SIGTERM)
    def test_sigint_stops_ssh(self):self.check_interrupt(signal.SIGINT)
    def test_authentication_reads_local_tty_not_zip(self):
        # Use a fresh child interpreter, not forkpty from a possibly threaded
        # test process. Only that child acquires its private controlling terminal.
        env=dict(self.env,FAKE_AUTH='1')
        master,slave=pty.openpty()
        child_code=("import fcntl,os,sys,termios; "
                    "fcntl.ioctl(0,termios.TIOCSCTTY,0); "
                    "os.execve('/bin/sh',['/bin/sh',sys.argv[1]],os.environ)")
        try:
            proc=subprocess.Popen([PYTHON,'-B','-c',child_code,str(self.dist/'Install-macOS.command')],
                stdin=slave,stdout=slave,stderr=slave,start_new_session=True,env=env)
        finally:os.close(slave)
        output=bytearray();prompts=[(b'Operation [1]: ',b'1\n'),(b'Thor local IP address: ',b'203.0.113.10\n'),
          (b'SSH user [armada]: ',b'\n'),(b'SSH port [22]: ',b'\n'),(b'FAKE SSH password: ',b'test-only\n'),
          (b'Press Enter to close: ',b'\n')]
        index=0;deadline=time.monotonic()+20;status=None
        try:
            while time.monotonic()<deadline:
                ready,_,_=select.select([master],[],[],.1)
                if ready:
                    try:block=os.read(master,65536)
                    except OSError:break
                    if not block:break
                    output.extend(block)
                if index<len(prompts) and prompts[index][0] in output:
                    os.write(master,prompts[index][1]);index+=1
                if proc.poll() is not None:break
            if proc.poll() is None:
                try:proc.wait(timeout=1)
                except subprocess.TimeoutExpired:
                    proc.terminate()
                    try:proc.wait(timeout=2)
                    except subprocess.TimeoutExpired:proc.kill();proc.wait(timeout=2)
            status=proc.returncode
        finally:
            if proc.poll() is None:proc.kill();proc.wait(timeout=2)
            os.close(master)
        self.assertEqual(status,0,output.decode(errors='replace'))
        self.assertEqual(index,len(prompts),output.decode(errors='replace'))
        self.assertTrue((self.base/'result.json').exists())

def make_op(launcher,shell,number,action):
    def test(self):self.check_run(launcher,shell,number,action)
    return test
for shell in ('/bin/sh','/bin/dash','/bin/bash'):
    if Path(shell).exists():
        for launcher in ('Install-Linux.sh','Install-macOS.command'):
            for number,action in (('1','install'),('2','undo'),('3','diagnostics'),('4','wake')):
                setattr(HostTests,'test_'+Path(shell).name+'_'+launcher.split('.')[0]+'_'+action,make_op(launcher,shell,number,action))

def make_invalid(entries):
    def test(self):
        p=self.launch(entries);self.assertNotEqual(p.returncode,0,(p.stdout,p.stderr))
        self.assertFalse((self.base/'args.json').exists())
    return test
invalids=['1\n'+a+'\n\n\n' for a in ('','999.1.2.3','127.1','1.2.3','1.2.3.4.5','01.2.3.4','example.com','-oBad',
         ':::', '1:2:3:4:5:6:7', '1:2:3:4:5:6:7:8:9', '2001:db8::1::2','203.0.113.10;echo bad')]
invalids+=['1\n203.0.113.10\n'+u+'\n22\n' for u in ('-oBad','evil;user','space name')]
invalids+=['1\n203.0.113.10\n\n'+p+'\n' for p in ('0','65536','999999999999999999999','22;echo bad','-1')]
for i,entries in enumerate(invalids):setattr(HostTests,f'test_invalid_parameter_{i:02}',make_invalid(entries))
if __name__=='__main__':unittest.main(verbosity=1)
