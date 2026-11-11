import unittest
from tests.helpers import controller,tap
from armada_dual_screen_input.ergo_layout import ACCENTS

class ControllerTests(unittest.TestCase):
    def setUp(self):self.c,self.clock,self.events,self.ext,self.changes=controller()
    def press(self,key,tid=1):
        k=self.c.layout.find(key);self.c.down(tid,*k.rect.center);return k
    def test_tap_letter_on_release_only(self):
        self.press('char:a');self.assertFalse(self.events);self.c.up(1)
        self.assertEqual(self.events[0]['text'],'a')
    def test_shift_once(self):
        tap(self.c,'mod:shift');tap(self.c,'char:a');tap(self.c,'char:b')
        self.assertEqual([e['text'] for e in self.events],['A','b'])
    def test_shift_double_caps(self):
        tap(self.c,'mod:shift');self.clock.advance(.1);tap(self.c,'mod:shift')
        tap(self.c,'char:a');tap(self.c,'char:b')
        self.assertEqual([e['text'] for e in self.events],['A','B'])
        tap(self.c,'mod:shift');tap(self.c,'char:c');self.assertEqual(self.events[-1]['text'],'c')
    def test_longpress_does_not_emit_base(self):
        self.press('char:a');self.clock.advance(.41)
        self.assertIsNotNone(self.c.popup);self.assertFalse(self.events)
    def test_accent_choose_one_literal(self):
        self.press('char:a');self.clock.advance(.41)
        k=self.c.popup.options[3];self.c.move(1,*k.rect.center);self.c.up(1)
        self.assertEqual(self.events,[{'kind':'literal','text':'ã'}])
    def test_accent_cancel_outside(self):
        self.press('char:a');self.clock.advance(.41);self.c.move(1,1200,1000);self.c.up(1)
        self.assertFalse(self.events)
    def test_accent_release_without_selection(self):
        self.press('char:a');self.clock.advance(.41);self.c.up(1);self.assertFalse(self.events)
    def test_upper_accent_popup(self):
        tap(self.c,'mod:shift');self.press('char:a');self.clock.advance(.41)
        self.assertEqual([k.value for k in self.c.popup.options],['Á','À','Â','Ã'])
        self.c.move(1,*self.c.popup.options[0].rect.center);self.c.up(1)
        self.assertEqual(self.events[-1]['text'],'Á');self.assertEqual(self.c.modifiers['shift'],0)
    def test_all_vowels_popup(self):
        for letter,expected in ACCENTS.items():
            with self.subTest(letter=letter):
                self.press('char:'+letter);self.clock.advance(.41)
                self.assertEqual(tuple(k.value for k in self.c.popup.options),expected)
                self.c.cancel_contact(1)
    def test_cancel_longpress_after_page_change(self):
        self.press('char:a');self.c.change_page('numbers');self.clock.advance(1)
        self.assertIsNone(self.c.popup);self.assertFalse(self.events)
    def test_second_finger_cancels_popup(self):
        self.press('char:a');self.clock.advance(.41);self.press('char:b',2);self.c.up(2)
        self.assertEqual([e.get('text') for e in self.events],['b'])
    def test_slide_off_cancels_letter(self):
        self.press('char:a');self.c.move(1,*self.c.layout.find('char:s').rect.center);self.c.up(1)
        self.assertFalse(self.events)
    def test_overlap_preserves_typing_order(self):
        self.press('char:a',1);self.press('char:b',2);self.c.up(2);self.c.up(1)
        self.assertEqual([e['text'] for e in self.events],['a','b'])
    def test_long_hold_consonant_not_repeat(self):
        self.press('char:b');self.clock.advance(3);self.assertFalse(self.events)
        self.c.up(1);self.assertEqual(len(self.events),1)
    def test_backspace_repeat(self):
        self.press('key:14');self.assertEqual(len(self.events),1)
        self.clock.advance(.34);self.assertEqual(len(self.events),1)
        self.clock.advance(.08);self.assertGreaterEqual(len(self.events),2)
        self.c.up(1);n=len(self.events);self.clock.advance(1);self.assertEqual(len(self.events),n)
    def test_repeat_off_key_stops(self):
        self.press('key:14');self.c.move(1,0,0);self.clock.advance(2)
        self.assertEqual(len(self.events),1)
    def test_repeat_disabled(self):
        self.c.settings['keyboard_repeat_enabled']=False
        self.press('key:14');self.clock.advance(2);self.assertEqual(len(self.events),1)
    def test_ctrl_survives_page_change(self):
        tap(self.c,'header:fn');tap(self.c,'mod:ctrl');tap(self.c,'page:abc');tap(self.c,'char:c')
        self.assertEqual(self.events[-1]['mods'],('ctrl',));self.assertEqual(self.c.modifiers['ctrl'],0)
    def test_fn_returns_previous_page(self):
        tap(self.c,'page:numbers');tap(self.c,'header:fn');tap(self.c,'header:fn')
        self.assertEqual(self.c.page,'numbers')
    def test_dead_returns_letters(self):
        self.c.change_page('symbols');tap(self.c,'dead:~')
        self.assertEqual(self.c.page,'abc');self.assertEqual(self.events[-1],{'kind':'dead','dead':'dead_tilde'})
    def test_mouse_hold_lifetime(self):
        self.press('mouse:left');self.assertEqual(self.ext[-1][0],'mouse_down')
        self.c.up(1);self.assertEqual(self.ext[-1][0],'mouse_up')
    def test_mouse_cancel_releases(self):
        self.press('mouse:right');self.c.cancel_contact(1);self.assertEqual(self.ext[-1][0],'mouse_up')
    def test_settings_language_changes(self):
        tap(self.c,'header:settings');tap(self.c,'setting:language')
        from armada_dual_screen_input.ergo_layout import language_picker
        k=next(k for k in language_picker('language','pt_PT') if k.value[1]=='en_US')
        self.c.down(1,*k.rect.center);self.c.up(1)
        self.assertEqual(self.c.settings['language'],'en_US');self.assertIn(('language','en_US'),self.changes)
    def test_settings_mouse_disabled(self):
        tap(self.c,'header:settings');self.ext.clear();tap(self.c,'mouse:left')
        self.assertFalse(self.ext)
    def test_settings_numeric_clamp(self):
        tap(self.c,'header:settings');tap(self.c,'settings:keyboard')
        for _ in range(20):tap(self.c,'setting:long_press_ms:minus')
        self.assertEqual(self.c.settings['long_press_ms'],250)
    def test_settings_failed_save_keeps_value(self):
        self.c.setting_changed=lambda *a:False;tap(self.c,'header:settings');tap(self.c,'setting:language')
        from armada_dual_screen_input.ergo_layout import language_picker
        k=next(k for k in language_picker('language','pt_PT') if k.value[1]=='en_US')
        self.c.down(1,*k.rect.center);self.c.up(1)
        self.assertEqual(self.c.settings['language'],'pt_PT')
    def test_menu_pad_mode(self):
        tap(self.c,'header:mode')
        from armada_dual_screen_input.ergo_layout import menu_keys
        key=next(k for k in menu_keys() if k.value=='pad');self.c.down(2,*key.rect.center);self.c.up(2)
        self.assertEqual(self.c.view,'pad');self.assertEqual(len(self.c.layout.keys),6)
    def test_no_physical_mod_down_when_latched(self):
        tap(self.c,'header:fn');tap(self.c,'mod:ctrl');self.assertFalse(self.events)
    def test_shift_caps_not_raw_navigation_modifier(self):
        tap(self.c,'header:fn');tap(self.c,'caps');tap(self.c,'key:105')
        self.assertNotIn('shift',self.events[-1]['mods'])
