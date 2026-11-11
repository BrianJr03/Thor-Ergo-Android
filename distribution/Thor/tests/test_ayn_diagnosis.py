"""Read-only diagnostic and bottom-Plasma prerequisite regression tests.
No live device, service, display or network is accessed by these tests.
"""
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import contextlib
import errno
import io
import json
import os
import subprocess
import tempfile
import unittest
import diagnose_ayn as d
import install

class DiagnosisTests(unittest.TestCase):
    def test_bitmap_64_f24(self):
        self.assertEqual(d.sysfs_key_codes('4 0 0 0',64),{194})
    def test_bitmap_32_f24(self):
        self.assertEqual(d.sysfs_key_codes('4 0 0 0 0 0 0',32),{194})
    def test_mixed_capabilities(self):
        self.assertEqual(d.sysfs_key_codes('4 0 0 40000000',64),{194,30})
    def test_bad_bitmap(self):
        for value in ('hello','1x','10000000000000000','1 '*20):
            with self.assertRaises(ValueError):d.sysfs_key_codes(value,64)
    def test_read_state_allowlist(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'state';p.write_text(json.dumps({'status':'unavailable','reason':'a','password':'not-for-report'}))
            r=d.read_state(p,('status','reason'))
            self.assertEqual(r,{'available':True,'status':'unavailable','reason':'a'})
            self.assertNotIn('password',str(r))
    def test_read_state_refuses_symlink(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp);(p/'a').write_text('{}');(p/'b').symlink_to(p/'a')
            self.assertFalse(d.read_state(p/'b',())['available'])
    def test_missing_state_reports_errno(self):
        with tempfile.TemporaryDirectory() as tmp:
            r=d.read_state(Path(tmp)/'not-present',())
            self.assertEqual(r['errno'],errno.ENOENT)
    def test_error_preserves_category(self):
        r=d.failure(PermissionError(errno.EACCES,'Permission denied','/dev/input/event7'))
        self.assertFalse(r['ok']);self.assertEqual(r['errno'],13);self.assertEqual(r['path'],'/dev/input/event7')
    def test_home_path_redacted(self):
        self.assertEqual(d.safe_text('/var/home/person/file /home/person/other'),'$HOME/file $HOME/other')
    def test_current_home_redacted(self):
        self.assertEqual(d.safe_text(str(Path.home())+'/path'),'$HOME/path')
    def test_query_error_contains_stderr_not_command(self):
        e=subprocess.CalledProcessError(1,['xprop','secret-argument'],stderr='display unavailable')
        r=d.failure(e)
        self.assertIn('display unavailable',r['stderr']);self.assertNotIn('secret-argument',str(r))
    def test_timeout_no_argv(self):
        r=d.failure(subprocess.TimeoutExpired(['hidden'],3))
        self.assertNotIn('hidden',str(r));self.assertFalse(r['ok'])
    def test_attempt_failure_does_not_stop_later_probe(self):
        r=d.attempt(lambda:(_ for _ in ()).throw(RuntimeError('identity_unavailable')))
        self.assertEqual(r['message'],'identity_unavailable')
        self.assertEqual(d.attempt(lambda:7),{'ok':True,'value':7})
    def test_environment_restored(self):
        with patch.dict(os.environ,{'HOME':'/home/test','XAUTHORITY':'/fake/a'}):
            before=dict(os.environ)
            with d.graphics_context({'HOME':'/home/helper','DISPLAY':':0'}):
                self.assertEqual(os.environ['HOME'],'/home/helper');self.assertNotIn('XAUTHORITY',os.environ)
            self.assertEqual(dict(os.environ),before)
    def test_environment_restored_on_error(self):
        before=dict(os.environ)
        with self.assertRaises(ValueError):
            with d.graphics_context({'DISPLAY':':8'}):raise ValueError('test')
        self.assertEqual(dict(os.environ),before)
    def test_no_environment_context_leaves_values(self):
        before=dict(os.environ)
        with d.graphics_context(None):self.assertEqual(dict(os.environ),before)
    def test_helper_environment_only_allowlist(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'123';p.mkdir()
            (p/'status').write_text('Uid:\t1\t'+str(os.getuid())+'\t1\t1\n')
            (p/'cmdline').write_bytes(b'python3\0/release/app/tools/ayn.py\0')
            (p/'environ').write_bytes(b'DISPLAY=:0\0PASSWORD=not-for-report\0SSH_AUTH_SOCK=secret\0')
            self.assertEqual(d.helper_environment(123,Path(tmp)),{'DISPLAY':':0'})
    def test_other_process_environment_not_read(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'123';p.mkdir()
            (p/'status').write_text('Uid:\t1\t'+str(os.getuid())+'\t1\t1\n')
            (p/'cmdline').write_bytes(b'other\0')
            self.assertIsNone(d.helper_environment(123,Path(tmp)))
    def test_other_user_helper_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'123';p.mkdir()
            (p/'status').write_text('Uid:\t1\t'+str(os.getuid()+1)+'\t1\t1\n')
            self.assertIsNone(d.helper_environment(123,Path(tmp)))
    def test_service_query_only_read_only_properties(self):
        calls=[]
        def run(args,**kwargs):
            calls.append(args);return SimpleNamespace(returncode=0,stdout='ActiveState=active\nMainPID=123\nExecStart=must-not-print\n',stderr='')
        r=d.service_state(d.UNITS[0],run)
        self.assertEqual(r['MainPID'],'123');self.assertNotIn('ExecStart',r)
        self.assertEqual(calls[0][:3],['/usr/bin/systemctl','--user','show'])
    def test_unlisted_service_never_queried(self):
        with self.assertRaises(ValueError):d.service_state('unexpected.service',lambda *a:None)
    def test_service_error_returned(self):
        def run(*a,**k):return SimpleNamespace(returncode=1,stdout='',stderr='no bus')
        self.assertFalse(d.service_state(d.UNITS[0],run)['ok'])
    def test_no_input_open_for_unrelated_keyboard(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'event1/device/capabilities';p.mkdir(parents=True)
            (p/'key').write_text('40000000');(p.parent/'name').write_text('ordinary keyboard')
            with patch.object(d.os,'open') as opened:
                r=d.input_candidates(Path(tmp),Path(tmp)/'dev');opened.assert_not_called()
            self.assertEqual(r['candidates'],[])
    def test_f24_candidate_name_mismatch_visible(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'event1/device/capabilities';p.mkdir(parents=True)
            (p/'key').write_text('4 0 0 0');(p.parent/'name').write_text('gpio-keys')
            r=d.input_candidates(Path(tmp),Path(tmp)/'dev')
            row=r['candidates'][0]
            self.assertTrue(row['has_F24']);self.assertFalse(row['dedicated_ayn_device'])
            self.assertEqual(row['open']['errno'],errno.ENOENT)
    def test_no_regular_file_open_as_input(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);p=root/'sys/event1/device/capabilities';p.mkdir(parents=True)
            (p/'key').write_text('4 0 0 0');(p.parent/'name').write_text('gpio-keys-ayn')
            (root/'dev').mkdir();(root/'dev/event1').write_bytes(b'')
            with patch.object(d.os,'open') as opened:
                r=d.input_candidates(root/'sys',root/'dev');opened.assert_not_called()
            self.assertEqual(r['candidates'][0]['open']['reason'],'not_character_device')
    def test_metadata_ioctls_only(self):
        calls=[]
        def ioctl(fd,code,data,mutate):
            calls.append(code)
            if code&255==6:data[:13]=b'gpio-keys-ayn\0'
            else:data[194//8]|=1<<(194%8)
        with patch.object(d.fcntl,'ioctl',side_effect=ioctl):r=d.ioctl_info(123)
        self.assertTrue(r['only_F24']);self.assertEqual([x&255 for x in calls],[6,0x21])
        self.assertNotIn(0x40044590,calls)
    def test_socket_checks_metadata_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'not-socket';p.write_text('')
            r=d.socket_status(p);self.assertTrue(r['present']);self.assertFalse(r['socket'])
    def test_program_source_has_no_capture_or_mutation_calls(self):
        import ast
        tree=ast.parse(Path(d.__file__).read_text())
        names={n.func.attr for n in ast.walk(tree) if isinstance(n,ast.Call) and isinstance(n.func,ast.Attribute)}
        self.assertTrue(names.isdisjoint({'press','set','restore','open_button','write_text','write_bytes','chmod','unlink','kill'}))
        for n in ast.walk(tree):
            if isinstance(n,ast.Call) and isinstance(n.func,ast.Attribute):
                self.assertFalse(isinstance(n.func.value,ast.Name) and n.func.value.id=='os' and n.func.attr=='read')

class PlasmaPreflightTests(unittest.TestCase):
    def test_success_is_readonly_child_process(self):
        s=install.System()
        with patch.object(install.subprocess,'run',return_value=SimpleNamespace(returncode=0)) as run,contextlib.redirect_stdout(io.StringIO()):
            s.require_bottom_plasma(Path('/bundle'))
        args=run.call_args.args[0]
        self.assertIn('-I',args);self.assertIn('game_only=True',args[4]);self.assertEqual(args[-1],'/bundle/app/src')
        self.assertNotIn('systemctl',str(args));self.assertNotIn('start(',str(args))
    def test_missing_plasma_clear_error(self):
        with patch.object(install.subprocess,'run',return_value=SimpleNamespace(returncode=3)):
            with self.assertRaisesRegex(install.InstallError,'BOTTOM screen'):
                install.System().require_bottom_plasma(Path('/bundle'))
    def test_timeout_fails_without_service_action(self):
        with patch.object(install.subprocess,'run',side_effect=subprocess.TimeoutExpired('probe',12)):
            with self.assertRaisesRegex(install.InstallError,'No running app was changed'):
                install.System().require_bottom_plasma(Path('/bundle'))
    def test_manager_check_before_copy_or_stop(self):
        from tests.test_standalone import StandaloneTests
        t=StandaloneTests();t.setUp()
        try:
            with patch.object(t.s,'require_bottom_plasma',side_effect=install.InstallError('missing Plasma')):
                with self.assertRaises(install.InstallError):t.m.install()
            self.assertFalse(any(c[0]=='stop' for c in t.s.calls))
            self.assertFalse(t.p['releases'].exists())
        finally:t.doCleanups()
