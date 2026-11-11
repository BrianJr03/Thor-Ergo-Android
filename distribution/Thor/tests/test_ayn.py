"""No real input node, display property, service or power state is touched."""
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import tempfile
import unittest
from armada_dual_screen_input.ergo_ayn import Toggle,Companion,open_button,key_capabilities,F24,read_json,atomic_json

class Display:
    def __init__(self):self.id=['session',10];self.value=0;self.calls=[];self.fail=False
    def identity(self):return self.id[:]
    def read(self):return self.value
    def set(self,value,identity):
        if identity!=self.id or self.fail:raise RuntimeError('unavailable')
        self.value=value;self.calls.append(value)

class AYNTests(unittest.TestCase):
    def setUp(self):
        self.c=Display();self.saved=[];self.now=10
        self.t=Toggle(self.c,self.saved.append,clock=lambda:self.now)
    def click(self):self.t.press(0);self.now+=.4;return self.t.press(1)
    def test_one_click_off_one_on(self):
        self.assertTrue(self.click());self.assertEqual(self.c.value,1)
        self.assertTrue(self.click());self.assertEqual(self.c.value,0)
    def test_repeat_events_ignored(self):
        self.click()
        for _ in range(10):self.assertFalse(self.t.press(2))
        self.assertEqual(self.c.calls,[1])
    def test_press_needs_release(self):
        self.click();self.now+=1;self.assertFalse(self.t.press(1));self.assertEqual(self.c.calls,[1])
    def test_debounce(self):
        self.click();self.t.press(0);self.now+=.05;self.assertFalse(self.t.press(1))
    def test_external_lock_is_never_unlocked(self):
        self.c.value=1;self.assertFalse(self.click());self.assertEqual(self.c.calls,[])
    def test_disable_restores_owned_screen(self):
        self.click();self.t.restore();self.assertEqual(self.c.calls,[1,0]);self.assertIsNone(self.t.owned)
    def test_external_lock_not_owned_on_restore(self):
        self.c.value=1;self.t.restore();self.assertEqual(self.c.calls,[])
    def test_restart_can_restore_same_session(self):
        self.click();new=Toggle(self.c,owned=self.t.owned);new.restore();self.assertEqual(self.c.value,0)
    def test_restart_does_not_unlock_new_session(self):
        self.click();self.c.id=['different',20];self.t.restore();self.assertEqual(self.c.value,1)
    def test_failed_restore_retains_recovery_identity(self):
        self.click();self.c.fail=True
        with self.assertRaises(RuntimeError):self.t.restore()
        self.assertIsNotNone(self.t.owned)
    def test_current_identity_checked_before_setting(self):
        seen=[]
        def run(args,**kwargs):seen.append(args);return SimpleNamespace(stdout='')
        c=Companion(run)
        with patch.object(c,'identity',return_value=['new']):
            with self.assertRaises(RuntimeError):c.set(1,['old'])
        self.assertFalse(seen)
    def test_xprop_same_armada_property_only(self):
        c=Companion();self.assertTrue(callable(c.read))
        with patch.object(c,'identity',return_value=['ok']),patch.object(c,'read',return_value=1),patch.object(c,'_xprop') as x:
            c.set(1,['ok']);self.assertEqual(x.call_args.args,('-f','GAMESCOPE_COMPANION_LOCKED','32c','-set','GAMESCOPE_COMPANION_LOCKED','1'))
    def test_non_gamescope_fails_closed(self):
        c=Companion()
        with patch.object(c,'_xprop',return_value='no such atom'):
            with self.assertRaises(RuntimeError):c.read()
    def test_absent_companion_atom_is_awake(self):
        c=Companion()
        with patch.object(c,'_xprop',side_effect=['GAMESCOPE_FOCUSED_APP = 769\n','no such atom']):self.assertEqual(c.read(),0)
    def test_invalid_companion_property_rejected(self):
        c=Companion()
        with patch.object(c,'_xprop',side_effect=['GAMESCOPE_FOCUSED_APP = 769','GAMESCOPE_COMPANION_LOCKED = 99']):
            with self.assertRaises(RuntimeError):c.read()
    def test_normal_keyboard_is_never_opened(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'event1/device').mkdir(parents=True);(root/'event1/device/name').write_text('Standard USB keyboard')
            with patch('os.open') as opening:
                with self.assertRaises(RuntimeError):open_button(root,root)
                opening.assert_not_called()
    def test_f24_is_194(self):self.assertEqual(F24,194)
    def test_mixed_capabilities_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'event1/device').mkdir(parents=True);(root/'event1/device/name').write_text('gpio-keys-ayn')
            with patch('os.open',return_value=9),patch('os.close') as close,patch('os.fstat',return_value=SimpleNamespace(st_mode=0o20600)),patch('armada_dual_screen_input.ergo_ayn.device_name',return_value='gpio-keys-ayn'),patch('armada_dual_screen_input.ergo_ayn.key_capabilities',return_value={F24,30}),patch('fcntl.ioctl') as grab:
                with self.assertRaises(RuntimeError):open_button(root,root)
                grab.assert_not_called();close.assert_called_with(9)
    def test_permission_denial_is_non_privileged_failure(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'event1/device').mkdir(parents=True);(root/'event1/device/name').write_text('gpio-keys-ayn')
            with patch('os.open',side_effect=PermissionError):
                with self.assertRaises(RuntimeError):open_button(root,root)
    def test_state_symlink_not_read(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);atomic_json(root/'a',{'status':'off'});(root/'b').symlink_to(root/'a')
            self.assertEqual(read_json(root/'b'),{})
    def test_state_atomic_private_mode(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'a';atomic_json(p,{'status':'off'})
            self.assertEqual(p.stat().st_mode&0o777,0o600);self.assertEqual(read_json(p)['status'],'off')
