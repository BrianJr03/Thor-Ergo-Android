"""Activation receipt/diagnostic regressions; no real services, input or network.

Two subprocess tests execute the real launch script with argument parsing only.
Neither starts the supervisor loop, a graphical window, nor any input device.
"""
import contextlib
import io
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch

import install
from native_runtime import native_test_environment

APP=Path(os.environ['THOR_ERGO_TEST_APP'])

class ActualLauncherTests(unittest.TestCase):
    def launch(self,*args):
        return subprocess.run([sys.executable,'-B',str(APP/'tools/run.py'),*args],
            env=native_test_environment(APP),capture_output=True,text=True,timeout=10)
    def test_actual_wrapper_missing_action_exits_two(self):
        result=self.launch()
        self.assertEqual(result.returncode,2,result.stdout+result.stderr)
        self.assertIn('--run',result.stderr)
        self.assertIn('required',result.stderr)
    def test_actual_wrapper_accepts_run_help(self):
        result=self.launch('--run','--help')
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertIn('--run',result.stdout)

class ReadinessTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.home=Path(self.temp.name)/'home';self.home.mkdir()
        self.p=install.locations(self.home,{})
        self.app=self.home/'app';self.app.mkdir()
        self.system=install.System()
        self.receipt={'version':install.VERSION,'rendered':True,'app':str(self.app),
            'width':1240,'height':1024,'pid':12345}
        self.ctl=self.enterContext(patch.object(self.system,'ctl',return_value=SimpleNamespace(returncode=0)))
        self.kill=self.enterContext(patch('install.os.kill'))
        self.enterContext(patch('install.time.sleep'))
        self.enterContext(patch('install.time.monotonic',side_effect=[0,0,36]))
    def store(self):install.atomic(self.p['ready'],self.receipt)
    def test_valid_live_frame_is_accepted(self):
        self.store();self.system.ready(self.p,self.app)
        self.assertEqual(self.kill.call_count,2)
    def test_offscreen_tests_without_receipt_are_not_success(self):
        with self.assertRaises(install.InstallError):self.system.ready(self.p,self.app)
        self.kill.assert_not_called()
    def test_other_version_not_accepted(self):
        self.receipt['version']='0.0.0';self.store()
        with self.assertRaises(install.InstallError):self.system.ready(self.p,self.app)
    def test_another_app_path_not_accepted(self):
        self.receipt['app']=str(self.home/'old');self.store()
        with self.assertRaises(install.InstallError):self.system.ready(self.p,self.app)
    def test_false_render_not_accepted(self):
        self.receipt['rendered']=False;self.store()
        with self.assertRaises(install.InstallError):self.system.ready(self.p,self.app)
    def test_tiny_frame_not_accepted(self):
        self.receipt['width']=1;self.store()
        with self.assertRaises(install.InstallError):self.system.ready(self.p,self.app)
    def test_inactive_service_not_accepted(self):
        self.ctl.return_value.returncode=3;self.store()
        with self.assertRaises(install.InstallError):self.system.ready(self.p,self.app)
    def test_dead_process_not_accepted(self):
        self.kill.side_effect=ProcessLookupError();self.store()
        with self.assertRaises(install.InstallError):self.system.ready(self.p,self.app)

class ActivationDiagnosticTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        home=Path(self.temp.name)/'private home';home.mkdir()
        self.p=install.locations(home,{})
        self.system=install.System()
        self.stdout=self.enterContext(contextlib.redirect_stdout(io.StringIO()))
        self.ctl=self.enterContext(patch.object(self.system,'ctl',return_value=SimpleNamespace(
            returncode=0,stdout='ActiveState=failed\nExecMainStatus=2\n',stderr='')))
        self.journal=self.enterContext(patch('install.subprocess.run',return_value=SimpleNamespace(
            returncode=0,stdout=str(home)+'/app/tools/run.py: error: --run is required\n',stderr='')))
    def report(self):return self.p['state']/'last-activation-error.txt'
    def test_actual_startup_error_is_reported(self):
        self.system.activation_diagnostics(self.p,1234.5,'No frame')
        self.assertIn('--run is required',self.stdout.getvalue())
        self.assertIn('ExecMainStatus=2',self.report().read_text())
    def test_private_mode_and_home_redaction(self):
        self.system.activation_diagnostics(self.p,1234.5,'No frame')
        self.assertEqual(self.report().stat().st_mode&0o777,0o600)
        self.assertNotIn(str(self.p['home']),self.report().read_text())
        self.assertIn('$HOME',self.report().read_text())
    def test_only_app_units_and_current_attempt_requested(self):
        self.system.activation_diagnostics(self.p,1234.5,'No frame')
        args=self.journal.call_args.args[0]
        self.assertIn('@1234',args)
        self.assertEqual([args[i+1] for i,a in enumerate(args[:-1]) if a=='-u'],list(install.UNITS[:2]))
        self.assertNotIn('-b',args)
        for call in self.ctl.call_args_list:
            self.assertNotIn('show-environment',call.args)
    def test_journal_timeout_does_not_raise(self):
        self.journal.side_effect=subprocess.TimeoutExpired('journalctl',8)
        self.system.activation_diagnostics(self.p,1234.5,'No frame')
        self.assertIn('unavailable',self.report().read_text())
    def test_failed_report_write_does_not_raise(self):
        with patch('install.atomic',side_effect=OSError('read only')):
            self.system.activation_diagnostics(self.p,1234.5,'No frame')
        self.assertIn('Could not save',self.stdout.getvalue())
    def test_report_symlink_is_not_followed(self):
        outside=self.p['home']/'outside';outside.write_text('untouched')
        self.report().parent.mkdir(parents=True)
        self.report().symlink_to(outside)
        self.system.activation_diagnostics(self.p,1234.5,'No frame')
        self.assertEqual(outside.read_text(),'untouched')
    def test_report_is_bounded(self):
        self.journal.return_value.stdout='x'*50000
        self.system.activation_diagnostics(self.p,1234.5,'No frame')
        self.assertLessEqual(len(self.report().read_text()),24001)
    def test_control_characters_removed(self):
        self.journal.return_value.stdout='error\x1b\x00\x07 bad\n'
        self.system.activation_diagnostics(self.p,1234.5,'No frame')
        text=self.report().read_text()
        self.assertNotIn('\x1b',text);self.assertNotIn('\x00',text);self.assertNotIn('\x07',text)

if __name__=='__main__':unittest.main()
