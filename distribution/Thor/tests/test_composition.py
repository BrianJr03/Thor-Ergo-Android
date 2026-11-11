import unittest
from armada_dual_screen_input.ergo_composition import PortugueseComposition

class Transport:
    def __init__(self):self.target=1;self.text=[];self.actions=[]
    def focus(self):return self.target
    def matches(self,t):return t==self.target
    def commit(self,text,t):
        if t!=self.target:raise RuntimeError('focus changed')
        self.text.append(text)
    def action(self,action,t):
        if t!=self.target:raise RuntimeError('focus changed')
        self.actions.append(action)

class CompositionTests(unittest.TestCase):
    def setUp(self):self.t=Transport();self.c=PortugueseComposition(self.t)
    def char(self,s):return self.c.handle({'kind':'key','code':0,'text':s})
    def dead(self,s):return self.c.handle({'kind':'key','code':0,'dead':s})
    def test_all_accent_pairs(self):
        cases=[('dead_acute','aeiou','áéíóú'),('dead_grave','a','à'),('dead_circumflex','aeo','âêô'),('dead_tilde','ao','ãõ'),('dead_diaeresis','u','ü')]
        for dead,plain,accents in cases:
            for a,b in zip(plain,accents):
                self.dead(dead);self.char(a);self.assertEqual(self.t.text[-1],b)
                self.dead(dead);self.char(a.upper());self.assertEqual(self.t.text[-1],b.upper())
    def test_sentence(self):
        for s in 'Olá ação café avô ç Ç':self.c.handle({'kind':'ergo_literal','text':s})
        self.assertEqual(''.join(self.t.text),'Olá ação café avô ç Ç')
    def test_literal_clears_pending(self):
        self.dead('dead_tilde');self.c.handle({'kind':'ergo_literal','text':'á'})
        self.assertEqual(self.t.text,['á']);self.assertIsNone(self.c.dead)
    def test_literal_focus_change_discarded(self):
        self.dead('dead_tilde');self.t.target=2
        self.assertEqual(self.c.handle({'kind':'ergo_literal','text':'ã'})['status'],'focus_changed');self.assertFalse(self.t.text)
    def test_barrier_discards_dead_not_insert(self):
        self.dead('dead_tilde');self.c.handle({'kind':'ergo_barrier'});self.assertIsNone(self.c.dead);self.assertFalse(self.t.text)
    def test_barrier_focus_change(self):
        self.dead('dead_tilde');self.t.target=3
        self.assertEqual(self.c.handle({'kind':'ergo_barrier'})['status'],'focus_changed')
    def test_delete_is_action_three(self):
        self.c.handle({'kind':'key','code':111});self.assertEqual(self.t.actions,[3])
    def test_delete_pending_accent(self):
        self.dead('dead_acute');self.c.handle({'kind':'key','code':111});self.assertEqual(self.t.actions,[3]);self.assertIsNone(self.c.dead)
    def test_delete_focus_changed(self):
        self.dead('dead_tilde');self.t.target=2
        self.assertEqual(self.c.handle({'kind':'key','code':111})['status'],'focus_changed');self.assertFalse(self.t.actions)
    def test_backspace_cancels_only_pending(self):
        self.dead('dead_acute');self.c.handle({'kind':'key','code':14});self.assertFalse(self.t.actions)
    def test_ordinary_backspace(self):
        self.c.handle({'kind':'key','code':14});self.assertEqual(self.t.actions,[2])
    def test_space_emits_standalone_accent(self):
        self.dead('dead_tilde');self.char(' ');self.assertEqual(self.t.text,['~'])
    def test_literal_validations(self):
        for text in ('',None,12,'ab','\n','\x00'):
            with self.assertRaises(ValueError):self.c.handle({'kind':'ergo_literal','text':text})
    def test_up_forwarded(self):
        self.assertEqual(self.c.handle({'kind':'key','code':103})['forward_key'],103)
    def test_left_game_action(self):
        self.c.handle({'kind':'key','code':105});self.assertEqual(self.t.actions,[4])
    def test_reset_discards_accents(self):
        self.dead('dead_tilde');self.c.handle({'kind':'reset'});self.char('a');self.assertEqual(self.t.text,['a'])
