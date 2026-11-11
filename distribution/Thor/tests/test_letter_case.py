"""Render real Cairo frames while checking the exact immutable keys being drawn."""
import unittest
from unittest.mock import patch
import cairo
from tests.helpers import controller, tap
from armada_dual_screen_input import ergo_render as render

LETTERS = 'abcdefghijklmnopqrstuvwxyzç'


def drawn(c):
    keys = {}
    original = render.draw_key
    def capture(cr, key, label, **kw):
        keys[key.ident] = key
        return original(cr, key, label, **kw)
    surface = cairo.ImageSurface(cairo.FORMAT_ARGB32, 1240, 1080)
    with patch.object(render, 'draw_key', side_effect=capture):
        render.draw(cairo.Context(surface), c)
    surface.finish()
    return keys


class LetterCaseTests(unittest.TestCase):
    def assert_letters(self, c, upper):
        keys = drawn(c)
        for char in LETTERS:
            if 'char:' + char in keys:
                self.assertEqual(keys['char:' + char].label, char.upper() if upper else char)

    def test_initial_all_letters_and_cedilla_lowercase(self):
        c, *_ = controller()
        self.assert_letters(c, False)
        self.assertEqual(len([k for k in drawn(c) if k.startswith('char:')]), 27)

    def test_all_three_shift_states(self):
        c, *_ = controller()
        for value in (0, 1, 2):
            with self.subTest(state=value):
                c.modifiers['shift'] = value
                self.assert_letters(c, bool(value))

    def test_shift_once_upper_then_returns_to_lower(self):
        c, _, events, *_ = controller()
        tap(c, 'mod:shift')
        self.assert_letters(c, True)
        tap(c, 'char:a')
        self.assertEqual(events[-1]['text'], 'A')
        self.assert_letters(c, False)
        tap(c, 'char:a')
        self.assertEqual(events[-1]['text'], 'a')

    def test_double_tap_locks_uppercase(self):
        c, _, events, *_ = controller()
        tap(c, 'mod:shift'); tap(c, 'mod:shift')
        self.assertEqual(c.modifiers['shift'], 2)
        tap(c, 'char:a'); tap(c, 'char:ç')
        self.assertEqual([e['text'] for e in events], ['A', 'Ç'])
        self.assert_letters(c, True)

    def test_unlock_caps_returns_lowercase(self):
        c, *_ = controller()
        tap(c, 'mod:shift'); tap(c, 'mod:shift')
        tap(c, 'mod:shift')
        self.assert_letters(c, False)

    def test_caps_button_in_fn_tracks_case_after_return(self):
        c, *_ = controller()
        tap(c, 'header:fn'); tap(c, 'caps'); tap(c, 'header:fn')
        self.assert_letters(c, True)
        tap(c, 'header:fn'); tap(c, 'caps'); tap(c, 'header:fn')
        self.assert_letters(c, False)

    def test_labels_match_emitted_letter_for_all_states_and_letters(self):
        for state in (0, 1, 2):
            for letter in LETTERS:
                with self.subTest(state=state, letter=letter):
                    c, _, events, *_ = controller()
                    c.modifiers['shift'] = state
                    label = drawn(c)['char:' + letter].label
                    tap(c, 'char:' + letter)
                    self.assertEqual(events[-1]['text'], label)

    def test_layout_key_objects_are_not_mutated(self):
        c, *_ = controller()
        before = c.layout
        keys = tuple(c.layout.keys)
        drawn(c)
        self.assertIs(c.layout, before)
        self.assertEqual(c.layout.keys, keys)
        self.assertEqual(c.layout.find('char:a').label, 'A')
        self.assertEqual(c.layout.find('char:a').value, 'a')

    def test_geometry_actions_codes_unchanged_for_every_key(self):
        c, *_ = controller()
        for view in ('combined', 'keyboard'):
            for page in ('abc', 'numbers', 'symbols', 'fn'):
                c.view = view; c.page = page; c.rebuild()
                actual = drawn(c)
                for key in c.layout.keys:
                    other = actual[key.ident]
                    self.assertEqual((key.rect, key.action, key.value, key.code, key.icon, key.accent),
                                     (other.rect, other.action, other.value, other.code, other.icon, other.accent))

    def test_nonletter_labels_in_all_pages_unchanged(self):
        c, *_ = controller()
        for state in (0, 1, 2):
            c.modifiers['shift'] = state
            for page in ('abc', 'numbers', 'symbols', 'fn'):
                c.page = page; c.rebuild(); actual = drawn(c)
                for key in c.layout.keys:
                    if not (key.action == 'text' and key.value in LETTERS and len(key.value) == 1):
                        self.assertEqual(actual[key.ident].label, key.label)

    def test_ordinals_do_not_become_uppercase(self):
        c, *_ = controller(); c.page = 'symbols'; c.rebuild()
        c.modifiers['shift'] = 2
        keys = drawn(c)
        self.assertEqual(keys['char:º'].label, 'º')
        self.assertEqual(keys['char:ª'].label, 'ª')

    def test_english_letters_follow_same_case(self):
        c, *_ = controller({'language': 'en_US'})
        for state in (0, 1, 2):
            c.modifiers['shift'] = state
            self.assert_letters(c, bool(state))
        self.assertEqual(drawn(c)['char:;'].label, ';')

    def test_ctrl_alt_super_do_not_force_uppercase_legends(self):
        c, *_ = controller()
        for mod in ('ctrl', 'alt', 'super', 'altgr'):
            c.modifiers[mod] = 1
        self.assert_letters(c, False)

    def test_draw_does_not_emit_input_consume_shift_or_change_preferences(self):
        c, _, events, external, changes = controller()
        c.modifiers['shift'] = 1
        before = c.settings.copy()
        drawn(c); drawn(c)
        self.assertEqual(c.modifiers['shift'], 1)
        self.assertEqual(c.settings, before)
        self.assertFalse(events); self.assertFalse(external); self.assertFalse(changes)

    def test_long_press_variants_keep_correct_case(self):
        for state in (0, 1, 2):
            c, clock, *_ = controller()
            c.modifiers['shift'] = state
            key = c.layout.find('char:a')
            c.down(1, *key.rect.center); clock.advance(.41)
            actual = drawn(c)
            chars = ['á', 'à', 'â', 'ã']
            expected = [s.upper() if state else s for s in chars]
            self.assertEqual([actual['accent:' + s].label for s in expected], expected)

    def test_reset_returns_lowercase(self):
        c, *_ = controller(); c.modifiers['shift'] = 2
        c.reset_contacts()
        self.assert_letters(c, False)
