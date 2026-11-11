import json,tempfile,unittest
from pathlib import Path
from types import SimpleNamespace as NS
from unittest.mock import Mock,patch
from armada_dual_screen_input.ergo_panel import Host,atomic_json,read_object
from tests.helpers import Clock

class Language:
    def __init__(self):
        self.ready=True;self.closed=False;self.language='pt_PT';self.worker=NS(submit=lambda r:True)
        self._generation=1;self.preedit='';self.candidates=[];self.revision=0;self.visible=False;self.seen=[]
    def _response(self,g,r):self.seen.append((g,r))
    def set_language(self,l):self.language=l
    def stop(self):self.worker=None;self.ready=False;self._generation+=1
    def sync(self,v):self.visible=v

class HostTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.path=Path(self.tmp.name);self.clock=Clock();self.keys=[];self.buttons=[]
        self.app=NS(input_cfg={'keyboard_language':'pt_PT'},languages=Language(),
            bridge=NS(key=lambda *x:self.keys.append(x),mouse_button=lambda *x:self.buttons.append(x),release_all=Mock()),
            keyboard_cfg={},scroll_output=NS(scale=1.,rest_x=0,rest_y=0),
            gesture=NS(set_settings=Mock(),glide_stop=NS(set=Mock()),reset_all=Mock(),suppress_taps_for_current_group=Mock(),
                       touch_down=Mock(),touch_move=Mock(),touch_up=Mock()),
            drawing_area=NS(queue_draw=Mock()),_cleaned=False,collapsed=False,
            keyboard_guard=NS(update=Mock()),window=NS(get_mapped=lambda:True),
            _touch_owner={},session_mode='game',_set_collapsed=Mock())
        self.upstream=NS(CONFIG_PATH=str(self.path/'config.json'),BTN_LEFT=272,BTN_RIGHT=273)
        self.glib=NS(timeout_add=self.clock.schedule,source_remove=self.clock.cancel)
        self.h=Host(self.app,self.upstream,self.glib,NS(label=None))
    def test_config_separate(self):
        original='{"keyboard_language":"pt_PT","other":7}'
        Path(self.upstream.CONFIG_PATH).write_text(original)
        self.assertTrue(self.h.setting_changed('long_press_ms',450))
        self.assertEqual(Path(self.upstream.CONFIG_PATH).read_text(),original)
        self.assertEqual(read_object(self.path/'ergo.json')['long_press_ms'],450)
    def test_mouse_reference_count(self):
        self.h.mouse('left',1,True);self.h.mouse('left',2,True);self.h.mouse('left',1,False)
        self.assertEqual(self.buttons,[(272,True)])
        self.h.mouse('left',2,False);self.assertEqual(self.buttons[-1],(272,False))
    def test_mouse_right_is_different_code(self):
        self.h.mouse('right',3,True);self.h.mouse('right',3,False)
        self.assertEqual(self.buttons,[(273,True),(273,False)])
    def test_pad_owner_captured_not_reassigned(self):
        x,y=self.h.controller.layout.pad.center;self.h.down(1,x,y,10)
        self.h.move(1,40,40,10.1);self.h.up(1,10.2)
        self.app.gesture.touch_down.assert_called_once();self.app.gesture.touch_move.assert_called_once();self.app.gesture.touch_up.assert_called_once()
        self.assertFalse(self.h.controller.contacts)
    def test_key_never_turns_into_pad(self):
        x,y=self.h.controller.layout.find('char:a').rect.center;self.h.down(1,x,y,10)
        self.h.move(1,*self.h.controller.layout.pad.center,10.1);self.h.up(1,10.2)
        self.app.gesture.touch_down.assert_not_called();self.app.gesture.touch_move.assert_not_called()
        self.assertFalse(self.keys)
    def test_mouse_drag_suppresses_tap(self):
        self.h.mouse('left',1,True);self.h.down(2,*self.h.controller.layout.pad.center,10)
        self.assertGreaterEqual(self.app.gesture.suppress_taps_for_current_group.call_count,2)
    def test_reset_releases_mouse(self):
        self.h.mouse('left',1,True);self.h.reset();self.assertEqual(self.buttons[-1],(272,False))
        self.assertFalse(self.h.mouse_holds);self.assertFalse(self.app._touch_owner)
    def test_settings_disable_keyboard_guard(self):
        self.h.controller.settings_tab='keyboard';self.h.sync()
        self.app.keyboard_guard.update.assert_called_with(False);self.assertFalse(self.app.languages.visible)
    def test_pad_only_disable_keyboard_guard(self):
        self.h.controller.view='pad';self.h.sync();self.app.keyboard_guard.update.assert_called_with(False)
    def test_keyboard_guard_on(self):
        self.h.sync();self.app.keyboard_guard.update.assert_called_with(True)
    def test_closed_host_does_not_redraw(self):
        self.h.close();self.app.drawing_area.queue_draw.reset_mock();self.h.changed();self.app.drawing_area.queue_draw.assert_not_called()
    def test_forward_reply_stripped_before_old_handler(self):
        self.h.output.send({'kind':'key','code':103,'mods':()});token=self.h.output.active[1]
        self.app.languages._response(1,{'status':'ready','forward_key':103,'ergo_token':token})
        self.assertNotIn('forward_key',self.app.languages.seen[-1][1]);self.assertEqual(self.keys,[(103,True)])
    def test_stale_generation_ignored(self):
        self.app.languages._response(0,{'status':'ready','forward_key':103,'ergo_token':1})
        self.assertFalse(self.app.languages.seen);self.assertFalse(self.keys)
    def test_current_untagged_reply_unchanged(self):
        r={'status':'ready','forward_key':103};self.app.languages._response(1,r)
        self.assertEqual(self.app.languages.seen[-1][1],r)
    def test_atomic_rejects_symlink(self):
        t=self.path/'real';t.write_text('untouched');p=self.path/'link';p.symlink_to(t)
        with self.assertRaises(OSError):atomic_json(p,{'a':1})
        self.assertEqual(t.read_text(),'untouched')
    def test_invalid_json_does_not_silently_overwrite(self):
        p=self.path/'ergo.json';p.write_text('BAD')
        self.assertFalse(self.h.setting_changed('long_press_ms',500));self.assertEqual(p.read_text(),'BAD')
    def test_settings_apply_gesture(self):
        self.h.apply_settings();self.app.gesture.set_settings.assert_called_once()
