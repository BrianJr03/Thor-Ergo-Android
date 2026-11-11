import unittest
from armada_dual_screen_input.ergo_output import SerialOutput,US_MAP
from tests.helpers import Clock

class OutputTests(unittest.TestCase):
    def setUp(self):
        self.clock=Clock();self.requests=[];self.keys=[];self.errors=[];self.aborts=[]
        self.lang='pt_PT';self.ready=True;self.accept=True
        self.o=SerialOutput(language=lambda:self.lang,available=lambda:self.ready,
            submit=lambda r:(self.requests.append(r) or self.accept),key=lambda *x:self.keys.append(x),
            schedule=self.clock.schedule,cancel=self.clock.cancel,error=self.errors.append,
            abort=lambda:self.aborts.append(True),clock=self.clock.time)
    def send(self,text='a',**kw):self.o.send(dict(kind='text',text=text,code=30,mods=(),**kw))
    def reply(self,**kw):
        self.o.reply(dict(status='ready',ergo_token=self.requests[-1]['ergo_token'],**kw))
    def test_text_uses_worker_not_raw(self):
        self.send();self.assertEqual(self.requests[0]['text'],'a');self.assertEqual(self.keys,[])
    def test_one_outstanding_request(self):
        self.send('a');self.send('b');self.assertEqual(len(self.requests),1)
        self.reply();self.assertEqual(self.requests[-1]['text'],'b');self.reply();self.assertIsNone(self.o.active)
    def test_late_reply_ignored(self):
        self.send();old=self.requests[-1]['ergo_token'];self.o.reset();self.send('b')
        self.o.reply({'status':'ready','ergo_token':old});self.assertEqual(self.o.active[0],'worker')
    def test_worker_timeout_clears_queue(self):
        self.send();self.send('b');self.clock.advance(1.6)
        self.assertTrue(self.errors);self.assertFalse(self.o.queue);self.assertTrue(self.aborts)
    def test_unavailable_fails_closed(self):
        self.ready=False;self.send();self.assertFalse(self.keys);self.assertFalse(self.requests);self.assertTrue(self.errors)
    def test_submit_failure(self):
        self.accept=False;self.send();self.assertTrue(self.errors);self.assertIsNone(self.o.active)
    def test_shortcut_waits_for_barrier(self):
        self.o.send({'kind':'text','text':'c','code':46,'mods':('ctrl',)})
        self.assertEqual(self.requests[-1]['kind'],'ergo_barrier');self.assertFalse(self.keys)
        self.reply();self.assertEqual(self.keys,[(29,True),(46,True)])
        self.clock.advance(.021);self.assertEqual(self.keys[-2:],[(46,False),(29,False)])
    def test_reset_releases_modifiers_reverse_order(self):
        self.lang='en_US';self.o.send({'kind':'text','text':'c','code':46,'mods':('ctrl','shift')})
        self.o.reset();self.assertEqual(self.keys[-3:],[(46,False),(42,False),(29,False)])
        size=len(self.keys);self.clock.advance(1);self.assertEqual(len(self.keys),size)
    def test_focus_change_cancels_waiting(self):
        self.send();self.send('b');self.o.reply({'status':'focus_changed','ergo_token':self.requests[-1]['ergo_token']})
        self.assertTrue(self.errors);self.assertEqual(len(self.requests),1)
    def test_queue_expiry_not_replayed(self):
        self.send();self.send('b');self.clock.advance(.8);self.reply()
        self.assertTrue(self.errors);self.assertEqual(len(self.requests),1)
    def test_queue_limit_cancels(self):
        self.send()
        for _ in range(49):self.send()
        self.assertTrue(self.errors);self.assertFalse(self.o.queue)
    def test_forward_up_physical_once(self):
        self.o.send({'kind':'key','code':103,'mods':()});self.reply(forward_key=103)
        self.clock.advance(.021);self.assertEqual(self.keys,[(103,True),(103,False)])
    def test_enter_gamescope_action(self):
        self.o.send({'kind':'key','code':28,'mods':()});self.assertEqual(self.requests[-1]['code'],28)
        self.reply();self.assertFalse(self.keys)
    def test_delete_gamescope_route(self):
        self.o.send({'kind':'key','code':111,'mods':()});self.assertEqual(self.requests[-1]['code'],111)
    def test_shift_arrow_uses_physical_chord(self):
        self.o.send({'kind':'key','code':106,'mods':('shift',)});self.reply();self.clock.advance(.021)
        self.assertEqual(self.keys,[(42,True),(106,True),(106,False),(42,False)])
    def test_ctrl_c_caps_does_not_add_shift(self):
        self.o.send({'kind':'text','text':'C','code':46,'mods':('shift','ctrl'),'caps':True})
        self.reply();self.assertEqual(self.keys,[(29,True),(46,True)])
    def test_altgr_e_is_euro(self):
        self.o.send({'kind':'text','text':'e','code':18,'mods':('altgr',)})
        self.assertEqual(self.requests[-1]['text'],'€')
    def test_literal_uses_special_request(self):
        self.o.send({'kind':'literal','text':'ã'});self.assertEqual(self.requests[-1]['kind'],'ergo_literal')
    def test_dead_request(self):
        self.o.send({'kind':'dead','dead':'dead_tilde'});self.assertEqual(self.requests[-1]['dead'],'dead_tilde')
    def test_english_ascii(self):
        self.lang='en_US';self.send('@');self.clock.advance(.021)
        self.assertEqual(self.keys,[(42,True),(3,True),(3,False),(42,False)]);self.assertFalse(self.requests)
    def test_english_accent_not_silently_mapped(self):
        self.lang='en_US';self.send('á');self.assertFalse(self.keys);self.assertTrue(self.errors)
    def test_all_ascii_printable_available(self):
        self.assertEqual(set(chr(i) for i in range(32,127))-set(US_MAP),set())
    def test_control_character_rejected(self):
        self.send('\n');self.assertTrue(self.errors);self.assertFalse(self.requests)
    def test_oversize_literal_rejected(self):
        self.o.send({'kind':'literal','text':'abc'});self.assertTrue(self.errors)
    def test_bad_modifier_rejected(self):
        self.o.send({'kind':'key','code':30,'mods':('bad',)});self.assertTrue(self.errors)
    def test_bad_code_rejected(self):
        self.lang='en_US';self.o.send({'kind':'key','code':999,'mods':()});self.assertTrue(self.errors)
    def test_closed_does_not_send(self):
        self.o.close();self.send();self.assertFalse(self.requests)
