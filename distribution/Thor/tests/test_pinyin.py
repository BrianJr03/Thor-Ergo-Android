import unittest
from armada_dual_screen_input.ergo_pinyin import PinyinComposition,dictionaries
from tests.test_composition import Transport

class PinyinTests(unittest.TestCase):
    def setUp(self):self.t=Transport();self.c=PinyinComposition(self.t)
    def type(self,text):
        for char in text:self.c.handle({'kind':'key','code':0,'text':char})
        return self.c.state()
    def choose(self,index=0):return self.c.handle({'kind':'choose','index':index,'revision':self.c.revision})
    def key(self,code):return self.c.handle({'kind':'key','code':code})
    def test_dictionary_shipped(self):
        index,phrases=dictionaries();self.assertGreater(len(index),400);self.assertGreater(len(phrases),200)
    def test_nihao_phrase(self):
        s=self.type('nihao');self.assertEqual(s['candidates'][0],'你好');self.assertFalse(self.t.text)
        self.choose();self.assertEqual(self.t.text,['你好']);self.assertFalse(self.c.buffer)
    def test_zhongguo(self):
        self.type('zhongguo');self.choose();self.assertEqual(self.t.text,['中国'])
    def test_space_selects(self):
        self.type('nihao');self.type(' ');self.assertEqual(self.t.text,['你好'])
    def test_enter_sends_pinyin_not_return(self):
        self.type('nihao');self.key(28);self.assertEqual(self.t.text,['nihao']);self.assertFalse(self.t.actions)
        self.key(28);self.assertEqual(self.t.actions,[1])
    def test_backspace_edits_uncommitted_only(self):
        self.type('nihao');self.key(14);self.assertEqual(self.c.buffer,'niha');self.assertFalse(self.t.actions)
    def test_escape_cancels(self):
        self.type('nihao');self.key(1);self.assertFalse(self.c.buffer);self.assertFalse(self.t.text)
    def test_forward_escape_without_composition(self):self.assertEqual(self.key(1)['forward_key'],1)
    def test_delete_sends_delete(self):self.key(111);self.assertEqual(self.t.actions,[3])
    def test_focus_change_never_commits_to_new_target(self):
        self.type('nihao');self.t.target=2
        self.assertEqual(self.choose()['status'],'focus_changed');self.assertFalse(self.t.text)
    def test_stale_candidate_is_noop(self):
        self.type('ni');old=self.c.revision;self.type('hao')
        self.c.handle({'kind':'choose','index':0,'revision':old});self.assertFalse(self.t.text)
        self.assertEqual(self.c.buffer,'nihao')
    def test_bad_candidate_rejected(self):
        self.type('ni')
        for value in (-1,999999,None,'0',True):
            with self.assertRaises(ValueError):self.c.handle({'kind':'choose','index':value,'revision':self.c.revision})
    def test_buffer_bounded(self):self.type('q'*100);self.assertEqual(len(self.c.buffer),64)
    def test_uppercase_normalized(self):
        self.type('NIHAO');self.choose();self.assertEqual(self.t.text,['你好'])
    def test_v_for_u_umlaut(self):
        s=self.type('lv');self.assertIn('绿',s['candidates'])
    def test_first_syllable_consumed_preserving_rest(self):
        self.c=PinyinComposition(self.t,index={'ni':'你','hao':'好'},phrases={})
        self.type('nihao');self.choose();self.assertEqual(self.c.buffer,'hao')
        self.choose();self.assertEqual(self.t.text,['你','好'])
    def test_apostrophe_separates_syllables(self):
        self.c=PinyinComposition(self.t,index={'xi':'西','an':'安'},phrases={})
        self.type("xi'an");self.choose();self.assertEqual(self.c.buffer,'an')
        self.choose();self.assertEqual(self.t.text,['西','安'])
    def test_literal_punctuation(self):self.type('。');self.assertEqual(self.t.text,['。'])
    def test_numeric_does_not_implicitly_select(self):
        self.type('ni');self.type('1');self.assertEqual(self.t.text,['ni1'])
    def test_barrier_cancels_without_leaking_latin(self):
        self.type('ni');self.c.handle({'kind':'ergo_barrier'});self.assertFalse(self.t.text);self.assertFalse(self.c.buffer)
    def test_barrier_respects_focus(self):
        self.type('ni');self.t.target=3;self.assertEqual(self.c.handle({'kind':'ergo_barrier'})['status'],'focus_changed')
    def test_literal_ends_composition(self):
        self.type('ni');self.c.handle({'kind':'ergo_literal','text':'é'})
        self.assertEqual(self.t.text,['é']);self.assertFalse(self.c.buffer)
    def test_unknown_request(self):
        with self.assertRaises(ValueError):self.c.handle({'kind':'exec'})
    def test_control_character_rejected(self):
        with self.assertRaises(ValueError):self.type('\n')
    def test_reset_does_not_write(self):
        self.type('nihao');self.c.handle({'kind':'reset'});self.assertFalse(self.t.text)
