"""Fresh/legacy/upgrade/error installs using fake services and temporary homes.
These tests NEVER call systemctl, open input devices or modify the real HOME.
"""
from __future__ import annotations
import base64
import contextlib
import io
import hashlib
import os
import shlex
import sys
from pathlib import Path
import shutil
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch
import install
from native_runtime import native_test_environment,ensure_native_startup

class FakeSystem:
    def __init__(self,p,legacy=True):
        self.p=p;self.calls=[];self.test_fail=False;self.start_fail=False;self.tested=False
        self.states={u:{'loaded':False,'active':False,'enabled':False,'runtime':False} for u in install.UNITS}
        if legacy:self.states[install.UNITS[2]].update(loaded=True,active=True,enabled=True)
    def ensure(self):self.calls.append(('ensure',))
    def require_bottom_plasma(self,bundle):self.calls.append(('plasma_preflight',))
    def native_test(self,bundle):
        self.calls.append(('test',));self.tested=True;install.verify_tree(bundle)
        if self.test_fail:raise install.InstallError('test failed')
    def statuses(self):return {u:dict(s) for u,s in self.states.items()}
    def stop(self,unit):self.calls.append(('stop',unit));self.states[unit]['active']=False
    def ctl(self,*args,check=True):
        self.calls.append(args)
        if args[0]=='daemon-reload':
            for k,u in (('unit',install.UNITS[0]),('ayn_unit',install.UNITS[1])):
                self.states[u]['loaded']=self.p[k].exists()
        elif args[0] in ('enable','disable'):
            self.states[args[-1]]['enabled']=args[0]=='enable'
        elif args[0]=='start':
            self.states[args[1]]['active']=True
        return SimpleNamespace(returncode=0,stdout='active\n',stderr='')
    def activation_diagnostics(self,p,since,reason):
        self.calls.append(('diagnostics',))
    def ready(self,p,app):
        self.calls.append(('ready',));
        if self.start_fail:raise install.InstallError('not ready')

class StandaloneTests(unittest.TestCase):
    def setUp(self):
        self.enterContext(contextlib.redirect_stdout(io.StringIO()))
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name);self.home=self.root/'home with spaces';self.home.mkdir()
        self.p=install.locations(self.home,{})
        self.src=self.root/'bundle';(self.src/'app/tools').mkdir(parents=True)
        (self.src/'app/src').mkdir()
        (self.src/'install.py').write_text('pass\n')
        (self.src/'selftest.py').write_text('pass\n')
        (self.src/'app/tools/run.py').write_text('pass\n')
        (self.src/'app/tools/ayn.py').write_text('pass\n')
        self.manifest()
        self.s=FakeSystem(self.p)
        self.m=install.Manager(self.src,self.p,self.s)
    def manifest(self):
        items=sorted(p for p in self.src.rglob('*') if p.is_file() and p.name!='SHA256SUMS')
        (self.src/'SHA256SUMS').write_text(''.join(install.digest(p)+'  '+p.relative_to(self.src).as_posix()+'\n' for p in items))
    def test_fresh_install_without_original(self):
        self.s=FakeSystem(self.p,legacy=False);self.m.system=self.s;self.m.install()
        state=install.read_json(self.p['installation']);self.assertEqual(state['version'],install.VERSION)
        self.assertTrue(self.s.states[install.UNITS[0]]['active']);self.assertTrue(self.s.states[install.UNITS[0]]['enabled'])
    def test_tests_before_stop(self):
        self.m.install();events=[c[0] for c in self.s.calls]
        self.assertLess(events.index('test'),events.index('stop'))
    def test_previous_panel_stopped_and_disabled_only_after_tests(self):
        self.m.install();self.assertFalse(self.s.states[install.UNITS[2]]['active']);self.assertFalse(self.s.states[install.UNITS[2]]['enabled'])
    def test_test_failure_does_not_stop_original(self):
        self.s.test_fail=True
        with self.assertRaises(install.InstallError):self.m.install()
        self.assertFalse(any(c[0]=='stop' for c in self.s.calls));self.assertTrue(self.s.states[install.UNITS[2]]['active'])
    def test_fail_start_restores_original(self):
        self.s.start_fail=True
        with self.assertRaises(install.InstallError):self.m.install()
        self.assertTrue(self.s.states[install.UNITS[2]]['active']);self.assertTrue(self.s.states[install.UNITS[2]]['enabled'])
        self.assertFalse(self.p['unit'].exists());self.assertFalse(self.p['installation'].exists())
    def test_legacy_preferences_are_imported_but_source_unchanged(self):
        install.atomic(self.p['old_settings'],{'language':'pt_PT','ui_language':'pt_PT','sensitivity':2.,'extra':'not imported'})
        before=self.p['old_settings'].read_bytes();self.m.install()
        settings=install.read_json(self.p['settings']);self.assertEqual(settings['language'],'pt_PT');self.assertEqual(settings['sensitivity'],2.)
        self.assertNotIn('extra',settings);self.assertEqual(self.p['old_settings'].read_bytes(),before)
    def test_ayn_default_disabled_even_imported_true(self):
        install.atomic(self.p['old_settings'],{'ayn_toggle_enabled':True})
        self.m.install();self.assertFalse(install.read_json(self.p['settings'])['ayn_toggle_enabled'])
    def test_autostart_choice_preserved(self):
        self.s.states[install.UNITS[2]]['enabled']=False;self.m.install()
        self.assertFalse(self.s.states[install.UNITS[0]]['enabled']);self.assertTrue(self.s.states[install.UNITS[0]]['active'])
    def test_undo_restores_old_panel_and_settings(self):
        before=self.s.statuses();self.m.install();self.m.undo()
        self.assertFalse(self.p['installation'].exists());self.assertFalse(self.p['settings'].exists())
        self.assertEqual(self.s.states[install.UNITS[2]],before[install.UNITS[2]])
    def test_update_is_a_new_full_copy(self):
        self.m.install();old=install.read_json(self.p['installation']);self.m.install();new=install.read_json(self.p['installation'])
        self.assertNotEqual(old['release'],new['release'])
        self.assertTrue((Path(old['release'])/'app/tools/run.py').exists())
        self.assertTrue((Path(new['release'])/'app/tools/run.py').exists())
    def test_upgrade_undo_restores_immediate_previous_public_version(self):
        self.m.install();old=self.p['installation'].read_bytes();self.m.install();self.m.undo()
        self.assertEqual(self.p['installation'].read_bytes(),old);self.assertTrue(self.s.states[install.UNITS[0]]['active'])
    def test_changed_service_is_not_overwritten(self):
        self.m.install();self.p['unit'].write_text('changed')
        with self.assertRaises(install.InstallError):self.m.check()
    def test_foreign_launcher_is_not_adopted(self):
        install.atomic(self.p['bin'],'something else')
        with self.assertRaises(install.InstallError):self.m.check()
    def test_service_dropins_block_unverified_override(self):
        d=self.p['unit'].with_name(self.p['unit'].name+'.d');d.mkdir(parents=True);(d/'override.conf').write_text('override')
        with self.assertRaises(install.InstallError):self.m.check()
    def test_recovery_record_blocks_new_install(self):
        install.atomic(self.p['root']/'recovery.json',{'transaction':'pending'})
        with self.assertRaises(install.InstallError):self.m.check()
    def test_manifest_tamper_detected(self):
        (self.src/'app/tools/run.py').write_text('changed')
        with self.assertRaises(install.InstallError):self.m.install()
        self.assertFalse(any(c[0]=='stop' for c in self.s.calls))
    def test_extra_executable_rejected(self):
        (self.src/'surprise.py').write_text('pass')
        with self.assertRaises(install.InstallError):install.verify_tree(self.src)
    def test_missing_file_rejected(self):
        (self.src/'install.py').unlink()
        with self.assertRaises(install.InstallError):install.verify_tree(self.src)
    def test_manifest_symlink_rejected(self):
        (self.src/'outside').symlink_to('/etc/passwd')
        with self.assertRaises(install.InstallError):install.verify_tree(self.src)
    def test_unsafe_manifest_path_rejected(self):
        (self.src/'SHA256SUMS').write_text('0'*64+'  ../file\n')
        with self.assertRaises(install.InstallError):install.verify_tree(self.src)
    def test_invalid_python_rejected_before_stop(self):
        (self.src/'app/tools/run.py').write_text('not valid @@@ syntax');self.manifest()
        with self.assertRaises(SyntaxError):self.m.install()
        self.assertFalse(any(c[0]=='stop' for c in self.s.calls))
    def test_snapshot_exact_bytes_and_modes(self):
        install.atomic(self.p['settings'],b'{"language":"pt_PT"}\n',0o640)
        files=install.snapshot_files(self.p);install.atomic(self.p['settings'],'other');install.restore_files(self.p,files)
        self.assertEqual(self.p['settings'].read_bytes(),b'{"language":"pt_PT"}\n')
        self.assertEqual(self.p['settings'].stat().st_mode&0o777,0o640)
    def test_quote_spaces_percent_and_quote(self):
        self.assertEqual(install.quoted('/home/a b/20%/x'), '"/home/a b/20%%/x"')
        self.assertEqual(install.quoted('a"b'),'"a\\"b"')
    def test_relative_xdg_rejected(self):
        with self.assertRaises(install.InstallError):install.locations(self.home,{'XDG_DATA_HOME':'relative'})
    def test_managed_symlink_rejected(self):
        self.p['settings'].parent.mkdir(parents=True);self.p['settings'].symlink_to(self.root/'elsewhere')
        with self.assertRaises(install.InstallError):self.m.check()


    def panel_arguments(self):
        line=next(line for line in self.p['unit'].read_text().splitlines() if line.startswith('ExecStart='))
        return shlex.split(line.split('=',1)[1])

    def test_generated_service_has_required_run_switch(self):
        self.m.install()
        args=self.panel_arguments()
        self.assertEqual(args[:3],['/usr/bin/python3','-u','-B'])
        self.assertTrue(args[3].endswith('/app/tools/run.py'))
        self.assertEqual(args[4:],['--run'])

    def test_generated_service_dispatches_with_actual_supervisor_parser(self):
        # Real generated unit + real argparse code. Only the infinite GUI loop
        # is replaced; neither GTK nor input devices can be opened by this test.
        from armada_dual_screen_input import supervisor
        self.m.install();args=self.panel_arguments()
        with patch.object(sys,'argv',args[3:]), \
             patch.dict(os.environ,supervisor.portable_environment(dict(os.environ)),clear=True), \
             patch.object(supervisor,'run',return_value=0) as run, \
             patch.object(supervisor,'probe',side_effect=AssertionError('not run mode')), \
             patch.object(supervisor.os,'execve',side_effect=AssertionError('unexpected reexec')):
            self.assertEqual(supervisor.main(),0)
        run.assert_called_once_with(False)

    def test_previous_missing_run_switch_reproduces_parser_failure(self):
        from armada_dual_screen_input import supervisor
        self.m.install();args=self.panel_arguments()
        with patch.object(sys,'argv',args[3:4]), \
             patch.dict(os.environ,supervisor.portable_environment(dict(os.environ)),clear=True), \
             patch.object(supervisor,'run',side_effect=AssertionError('must not start')), \
             contextlib.redirect_stderr(io.StringIO()) as error:
            with self.assertRaises(SystemExit) as caught:supervisor.main()
        self.assertEqual(caught.exception.code,2)
        self.assertIn('required',error.getvalue())

    def test_user_runtime_from_systemd_specifier(self):
        self.m.install()
        self.assertIn('Environment=XDG_RUNTIME_DIR=%t',self.p['unit'].read_text())

    def test_prior_failed_state_reset_before_start(self):
        self.m.install();events=[c[0] for c in self.s.calls]
        self.assertLess(events.index('reset-failed'),events.index('start'))
        self.assertLess(events.index('test'),events.index('reset-failed'))

    def test_start_failure_details_captured_before_rollback(self):
        self.s.start_fail=True
        with self.assertRaises(install.InstallError):self.m.install()
        events=[c[0] for c in self.s.calls]
        at=events.index('diagnostics')
        self.assertGreater(at,events.index('ready'))
        self.assertEqual(events[at+1],'stop')
        self.assertTrue(self.s.states[install.UNITS[2]]['active'])

    def test_diagnostic_failure_never_prevents_rollback(self):
        self.s.start_fail=True
        with patch.object(self.s,'activation_diagnostics',side_effect=OSError('journal unavailable')):
            with self.assertRaises(install.InstallError):self.m.install()
        self.assertTrue(self.s.states[install.UNITS[2]]['active'])
        self.assertFalse(self.p['unit'].exists())

    def test_no_failure_report_on_success(self):
        self.m.install()
        self.assertFalse(any(c[0]=='diagnostics' for c in self.s.calls))


class RuntimeTests(unittest.TestCase):
    def setUp(self):
        self.enterContext(contextlib.redirect_stdout(io.StringIO()))
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.app=Path(self.tmp.name)/'app';(self.app/'src').mkdir(parents=True)
        self.lib=self.app/'runtime/usr/lib64';(self.lib/'girepository-1.0').mkdir(parents=True)
        (self.lib/'libgtk-layer-shell.so.0').write_bytes(b'fixture')
        (self.lib/'girepository-1.0/GtkLayerShell-0.1.typelib').write_bytes(b'fixture')
    def test_private_library_paths(self):
        env=native_test_environment(self.app,{})
        self.assertEqual(env['LD_LIBRARY_PATH'],str(self.lib));self.assertEqual(env['GI_TYPELIB_PATH'],str(self.lib/'girepository-1.0'))
    def test_no_global_environment_mutation(self):
        source={'LD_PRELOAD':'something','PYTHONPATH':'/old'};before=source.copy()
        env=native_test_environment(self.app,source);self.assertEqual(source,before);self.assertNotIn('LD_PRELOAD',env)
        self.assertEqual(env['PYTHONPATH'],str(self.app/'src'))
    def test_missing_typelib_fails_early(self):
        (self.lib/'girepository-1.0/GtkLayerShell-0.1.typelib').unlink()
        with self.assertRaises(ValueError):native_test_environment(self.app,{})
    def test_correct_environment_before_python_launch(self):
        with patch('install.subprocess.run') as run:
            run.return_value.returncode=0
            install.System().native_test(self.app.parent)
            self.assertEqual(run.call_args.kwargs['env']['LD_LIBRARY_PATH'],str(self.lib))
            self.assertIn('--native',run.call_args.args[0])
    def test_python_does_not_write_bytecode(self):self.assertEqual(native_test_environment(self.app,{})['PYTHONDONTWRITEBYTECODE'],'1')
