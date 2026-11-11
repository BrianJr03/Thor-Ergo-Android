"""AYN profile routing regressions. No real D-Bus, device events or input nodes.

The profile fixture is synthetic. All mutation tests use FakeBus and temporary
state directories; they cannot change the running system's controller profile.
"""
from __future__ import annotations
from collections import deque
from copy import deepcopy
import json
import os
from pathlib import Path
import stat
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from armada_dual_screen_input import ergo_inputplumber as ip
from armada_dual_screen_input import ergo_ayn as ayn

ACTION='thor_ergo_ayn_'+'a'*32
OWNER=':1.42'
DEVICE=ip.Device(OWNER,ip.ROOT+'/CompositeDevice0','test-fixture-device',
                 (ip.ROOT+'/devices/target/dbus0',))
BASE={'version':1,'kind':'DeviceProfile','name':'Fixture', 'mapping':[
 {'name':'Fixture left','source_event':{'gamepad':{'button':'LeftTop'}},
  'target_events':[{'gamepad':{'button':'LeftPaddle1'}}]},
 {'name':'Fixture AYN','source_event':deepcopy(ip.SOURCE),
  'target_events':[{'gamepad':{'button':'Screenshot'}}]},
 {'name':'Fixture keyboard','source_event':{'keyboard':'KeyF1'},
  'target_events':[{'keyboard':'KeyF2'}]},
]}

def record(original=None):
    original=deepcopy(original or BASE)
    return {'schema':1,'boot':ip._boot_id(),'owner':OWNER,'path':DEVICE.path,
            'persistent_id':DEVICE.persistent_id,'action':ACTION,'original':original,
            'applied':ip.make_route(original,ACTION),'original_path':''}

class FakeBus:
    def __init__(self,profile=None):
        self.owner=OWNER;self.device=DEVICE;self.current=deepcopy(profile or BASE)
        self.log=[];self.queue=None;self.gone=False;self.denied=False
        self.fail_once=None;self.original_path='';self.pending=None
    def current_owner(self):return self.owner
    def check_owner(self):
        if self.owner!=OWNER:raise ip.RouteError('ip_session_changed')
    def composite_paths(self):return [] if self.gone else [self.device.path]
    def discover(self,source_check=None):self.log.append('discover');return self.device
    def prop(self,path,name):
        return {'PersistentId':self.device.persistent_id,'ProfilePath':self.original_path,
                'DbusDevices':list(self.device.targets)}[name]
    def profile(self,device):return deepcopy(self.current)
    def subscribe(self,device,action,queue):
        self.log.append('subscribe');self.queue=queue;self.action=action;return [7]
    def unsubscribe(self,ids):self.log.append(('unsubscribe',tuple(ids)))
    def load(self,device,profile):
        ip.can_route(profile)
        self.log.append(('load',deepcopy(profile)))
        if self.denied:raise ip.RouteError('ip_permission_denied')
        self.current=deepcopy(profile)
        if self.fail_once:
            error=self.fail_once;self.fail_once=None;raise ip.RouteError(error)
    def restore_path(self,*args):return False
    def wait_profile(self,device,expected,previous=None):
        if self.current!=expected:raise ip.RouteError('ip_profile_not_confirmed')
    def iterate(self):pass

class ParsingTests(unittest.TestCase):
    def test_yaml_and_json(self):
        text=ip.yaml.safe_dump(BASE)
        self.assertEqual(ip.parse_profile(text),BASE)
        self.assertEqual(ip.parse_profile(json.dumps(BASE)),BASE)
    def test_duplicate_keys(self):
        with self.assertRaises(ip.RouteError):ip.parse_profile(ip.yaml.safe_dump(BASE)+'name: other\n')
    def test_no_aliases(self):
        with self.assertRaises(ip.RouteError):ip.parse_profile('version: 1\nx: &a [1]\ny: *a\n')
    def test_no_unsafe_python_tags(self):
        with self.assertRaises(ip.RouteError):ip.parse_profile('!!python/object/apply:os.system ["echo never"]')
    def test_depth(self):
        with self.assertRaises(ip.RouteError):ip.parse_profile('['*40+'1'+']'*40)
    def test_no_nonstr_keys(self):
        with self.assertRaises(ip.RouteError):ip.parse_profile(ip.yaml.safe_dump(BASE)+'2: value\n')
    def test_size_limit(self):
        with self.assertRaises(ip.RouteError):ip.parse_profile(' '* (ip.MAX_PROFILE+1))
    def test_nan(self):
        bad=deepcopy(BASE);bad['description']=float('nan')
        with self.assertRaises(ip.RouteError):ip.parse_profile(ip.yaml.safe_dump(bad))
    def test_schema_missing(self):
        for key in ('version','kind','name','mapping'):
            with self.subTest(key=key):
                bad=deepcopy(BASE);del bad[key]
                with self.assertRaises(ip.RouteError):ip.parse_profile(json.dumps(bad))
    def test_version_type(self):
        for v in (True,'1',2,None):
            bad=deepcopy(BASE);bad['version']=v
            with self.subTest(version=v),self.assertRaises(ip.RouteError):ip.parse_profile(json.dumps(bad))
    def test_malformed_mapping(self):
        for v in ('bad', {'name':'x','source_event':[],'target_events':[]},
                  {'name':'x','source_event':{},'target_events':['bad']}):
            bad=deepcopy(BASE);bad['mapping'].append(v)
            with self.subTest(value=v),self.assertRaises(ip.RouteError):ip.parse_profile(json.dumps(bad))
    def test_bad_gamepad(self):
        for v in (0,[],True,'button'):
            bad=deepcopy(BASE);bad['mapping'][0]['source_event']['gamepad']=v
            with self.subTest(v=v),self.assertRaises(ip.RouteError):ip.can_route(bad)
    def test_refuse_controller_recreation(self):
        for v in ([],['xbox-elite'],['keyboard']):
            bad=deepcopy(BASE);bad['target_devices']=v
            with self.subTest(v=v),self.assertRaisesRegex(ip.RouteError,'ip_profile_targets'):ip.can_route(bad)
    def test_ambiguous_ayn_source(self):
        bad=deepcopy(BASE);bad['mapping'][1]['source_event']['keyboard']='KeyF24'
        with self.assertRaises(ip.RouteError):ip.can_route(bad)
    def test_many_mappings(self):
        bad=deepcopy(BASE);bad['mapping']=bad['mapping']*200
        with self.assertRaises(ip.RouteError):ip.can_route(bad)
    def test_invalid_private_action(self):
        for a in ('ui_accept',ACTION+'x','thor_ergo_ayn_bad',''):
            with self.subTest(a=a),self.assertRaises(ip.RouteError):ip.make_route(BASE,a)

class MappingTests(unittest.TestCase):
    def test_only_ayn_mapping_changes(self):
        out=ip.make_route(BASE,ACTION)
        self.assertEqual(out['mapping'][0],BASE['mapping'][0]);self.assertEqual(out['mapping'][2],BASE['mapping'][2])
        self.assertEqual(out['mapping'][1]['source_event'],ip.SOURCE)
        self.assertEqual(out['mapping'][1]['target_events'],[{'dbus':ACTION}])
    def test_input_not_mutated(self):
        orig=deepcopy(BASE);out=ip.make_route(orig,ACTION);out['mapping'][0]['name']='changed'
        self.assertEqual(orig,BASE)
    def test_no_existing_ayn_mapping(self):
        orig=deepcopy(BASE);orig['mapping']=[m for m in orig['mapping'] if not ip.is_quick(m)]
        out=ip.make_route(orig,ACTION);self.assertEqual(len(out['mapping']),len(orig['mapping'])+1)
        self.assertEqual(ip.undo_route(out,record(orig)),orig)
    def test_multiple_ayn_rules_all_restored(self):
        orig=deepcopy(BASE);orig['mapping'].append(deepcopy(orig['mapping'][1]))
        out=ip.make_route(orig,ACTION);self.assertEqual(sum(map(ip.is_quick,out['mapping'])),1)
        self.assertEqual(ip.undo_route(out,record(orig)),orig)
    def test_exact_undo(self):
        rec=record();self.assertEqual(ip.undo_route(rec['applied'],rec),BASE)
    def test_undo_pending_original(self):
        self.assertEqual(ip.undo_route(BASE,record()),BASE)
    def test_external_profile_not_overwritten(self):
        external=deepcopy(BASE);external['name']='Externally selected'
        self.assertIsNone(ip.undo_route(external,record()))
    def test_external_unrelated_mapping_preserved(self):
        rec=record();current=deepcopy(rec['applied']);current['mapping'][0]['name']='Edited elsewhere'
        restored=ip.undo_route(current,rec)
        self.assertEqual(restored['mapping'][0]['name'],'Edited elsewhere')
        self.assertEqual(restored['mapping'][1],BASE['mapping'][1]);self.assertEqual(restored['name'],'Fixture')
    def test_external_new_name_preserved(self):
        rec=record();current=deepcopy(rec['applied']);current['name']='New user title'
        self.assertEqual(ip.undo_route(current,rec)['name'],'New user title')
    def test_external_new_ayn_remains(self):
        rec=record();current=deepcopy(rec['applied'])
        other=deepcopy(BASE['mapping'][1]);other['target_events']=[{'keyboard':'KeyF12'}]
        current['mapping'].append(other)
        result=ip.undo_route(current,rec)
        self.assertEqual([m for m in result['mapping'] if ip.is_quick(m)],[other])
    def test_new_target_devices_not_recreated(self):
        rec=record();current=deepcopy(rec['applied']);current['target_devices']=[]
        with self.assertRaisesRegex(ip.RouteError,'ip_profile_targets'):ip.undo_route(current,rec)

class TemporaryCase(unittest.TestCase):
    def setUp(self):self.temp=tempfile.TemporaryDirectory();self.state=Path(self.temp.name)
    def tearDown(self):self.temp.cleanup()

class JournalTests(TemporaryCase):
    def test_roundtrip(self):
        j=ip.Journal(self.state);j.save(record());self.assertEqual(j.read(),record())
    def test_permissions(self):
        j=ip.Journal(self.state);j.save(record());self.assertEqual(stat.S_IMODE(j.path.stat().st_mode),0o600)
    def test_missing_empty(self):
        j=ip.Journal(self.state);self.assertIsNone(j.read());j.save({});self.assertIsNone(j.read())
    def test_tampered_mapping(self):
        j=ip.Journal(self.state);r=record();r['applied']['mapping'][0]['name']='tampered';j.save(r)
        with self.assertRaises(ip.RouteError):j.read()
    def test_invalid_json(self):
        j=ip.Journal(self.state);j.path.write_text('[')
        with self.assertRaises(ip.RouteError):j.read()
    def test_symlink_read_rejected(self):
        other=self.state/'other';other.write_text(json.dumps(record()))
        j=ip.Journal(self.state);j.path.symlink_to(other)
        with self.assertRaises(ip.RouteError):j.read()
    def test_symlink_write_rejected(self):
        other=self.state/'other';other.write_text('untouched')
        j=ip.Journal(self.state);j.path.symlink_to(other)
        with self.assertRaises(ip.RouteError):j.save(record())
        self.assertEqual(other.read_text(),'untouched')
    def test_journal_size(self):
        j=ip.Journal(self.state);j.path.write_text(' '*(ip.MAX_JOURNAL+1))
        with self.assertRaises(ip.RouteError):j.read()
    def test_wrong_owner_field(self):
        j=ip.Journal(self.state);r=record();r['owner']='org.other';j.save(r)
        with self.assertRaises(ip.RouteError):j.read()
    def test_no_tempfiles_after_save(self):
        j=ip.Journal(self.state);j.save(record());self.assertEqual(list(self.state.iterdir()),[j.path])

class LeaseTests(TemporaryCase):
    def test_enable_disable_restores(self):
        bus=FakeBus();lease=ip.Lease.open(self.state,bus)
        self.assertEqual(bus.current,lease.record['applied']);self.assertEqual(bus.log[:2],['discover','subscribe'])
        self.assertIsNotNone(ip.Journal(self.state).read());lease.close()
        self.assertEqual(bus.current,BASE);self.assertIsNone(ip.Journal(self.state).read())
        self.assertIn(('unsubscribe',(7,)),bus.log)
    def test_journal_before_write(self):
        bus=FakeBus();original=bus.load
        def load(device,profile):
            self.assertIsNotNone(ip.Journal(self.state).read());original(device,profile)
        bus.load=load;lease=ip.Lease.open(self.state,bus);lease.close()
    def test_denied_does_not_leave_a_route(self):
        bus=FakeBus();bus.denied=True
        with self.assertRaisesRegex(ip.RouteError,'ip_permission_denied'):ip.Lease.open(self.state,bus)
        self.assertEqual(bus.current,BASE);self.assertIsNone(ip.Journal(self.state).read())
        self.assertIn(('unsubscribe',(7,)),bus.log)
    def test_rpc_timeout_after_applied_is_undone(self):
        bus=FakeBus();bus.fail_once='ip_dbus_unavailable'
        with self.assertRaises(ip.RouteError):ip.Lease.open(self.state,bus)
        self.assertEqual(bus.current,BASE);self.assertIsNone(ip.Journal(self.state).read())
    def test_refused_profile_not_loaded(self):
        bus=FakeBus();bus.current['target_devices']=[]
        with self.assertRaisesRegex(ip.RouteError,'ip_profile_targets'):ip.Lease.open(self.state,bus)
        self.assertNotIn('subscribe',bus.log);self.assertFalse((self.state/ip.JOURNAL).exists())
    def test_poll_only_private_values(self):
        bus=FakeBus();lease=ip.Lease.open(self.state,bus);bus.queue.extend((1,0));self.assertEqual(lease.poll(0),[1,0]);lease.close()
    def test_overflow_does_not_replay(self):
        bus=FakeBus();lease=ip.Lease.open(self.state,bus);bus.queue.extend((None,1,0))
        with self.assertRaisesRegex(ip.RouteError,'ip_event_overflow'):lease.poll(0)
        self.assertFalse(bus.queue);lease.close()
    def test_health(self):
        bus=FakeBus();lease=ip.Lease.open(self.state,bus);lease.check();lease.close()
    def test_external_profile_not_fought(self):
        bus=FakeBus();lease=ip.Lease.open(self.state,bus)
        external=deepcopy(BASE);external['name']='New external';bus.current=external
        with self.assertRaisesRegex(ip.RouteError,'ip_profile_changed'):lease.check()
        lease.close();self.assertEqual(bus.current,external)
    def test_target_change(self):
        bus=FakeBus();lease=ip.Lease.open(self.state,bus)
        bus.device=ip.Device(OWNER,DEVICE.path,DEVICE.persistent_id,(DEVICE.targets[0]+'1',))
        with self.assertRaisesRegex(ip.RouteError,'ip_signal_target_changed'):lease.check()
        lease.close()
    def test_double_close(self):
        bus=FakeBus();lease=ip.Lease.open(self.state,bus);lease.close();n=len(bus.log);lease.close();self.assertEqual(len(bus.log),n)
    def test_failed_restore_keeps_backup(self):
        bus=FakeBus();lease=ip.Lease.open(self.state,bus);bus.denied=True
        with self.assertRaises(ip.RouteError):lease.close()
        self.assertIsNotNone(ip.Journal(self.state).read());self.assertIn(('unsubscribe',(7,)),bus.log)
        bus.denied=False;self.assertEqual(ip.recover(self.state,bus),'restored');self.assertEqual(bus.current,BASE)
    def test_recovery_after_crash(self):
        bus=FakeBus();r=record();bus.current=r['applied'];ip.Journal(self.state).save(r)
        self.assertEqual(ip.recover(self.state,bus),'restored');self.assertEqual(bus.current,BASE)
    def test_recovery_async_metadata_lag(self):
        bus=FakeBus();ip.Journal(self.state).save(record())
        self.assertEqual(ip.recover(self.state,bus),'restored')
        self.assertTrue(any(isinstance(x,tuple) and x[0]=='load' for x in bus.log))
    def test_recovery_new_boot_no_write(self):
        bus=FakeBus();r=record();r['boot']='previous-boot';ip.Journal(self.state).save(r)
        self.assertEqual(ip.recover(self.state,bus),'expired');self.assertFalse(bus.log)
    def test_recovery_new_daemon_no_write(self):
        bus=FakeBus();bus.owner=':1.99';ip.Journal(self.state).save(record())
        self.assertEqual(ip.recover(self.state,bus),'expired');self.assertFalse(bus.log)
    def test_recovery_missing_device_no_write(self):
        bus=FakeBus();bus.gone=True;ip.Journal(self.state).save(record())
        self.assertEqual(ip.recover(self.state,bus),'expired');self.assertFalse(bus.log)
    def test_recovery_reused_path_no_write(self):
        bus=FakeBus();bus.device=ip.Device(OWNER,DEVICE.path,'different',DEVICE.targets);ip.Journal(self.state).save(record())
        self.assertEqual(ip.recover(self.state,bus),'expired');self.assertFalse(bus.log)
    def test_probe_read_only(self):
        bus=FakeBus();out=ip.probe(bus)
        self.assertTrue(out['profile_compatible']);self.assertFalse(out['profile_change_permission_tested'])
        self.assertEqual(bus.log,['discover']);self.assertNotIn('profile',out)
    def test_recovery_empty_never_connects(self):
        with patch.object(ip,'Bus',side_effect=AssertionError('must not connect')):
            self.assertEqual(ip.recover(self.state),'none')

class IdentityTests(TemporaryCase):
    def make_gpio(self,name='gpio-keys-ayn',bitmap='4 0 0 0'):
        root=self.state/'event9/device';root.mkdir(parents=True);(root/'name').write_text(name)
        (root/'capabilities').mkdir();(root/'capabilities/key').write_text(bitmap)
    def test_raw_mode000_identifies_without_open(self):
        self.make_gpio()
        with patch.object(os,'open',side_effect=AssertionError('no input opening')):
            self.assertTrue(ip.gpio_source('/dev/input/event9',self.state))
    def test_hidden_source_path(self):
        self.make_gpio();self.assertTrue(ip.gpio_source('/dev/inputplumber/sources/event9',self.state))
    def test_reject_virtual_full_keyboard(self):
        self.make_gpio('InputPlumber Keyboard');self.assertFalse(ip.gpio_source('/dev/input/event9',self.state))
    def test_reject_other_keys(self):
        self.make_gpio(bitmap='4 0 0 2');self.assertFalse(ip.gpio_source('/dev/input/event9',self.state))
    def test_reject_other_paths(self):
        self.make_gpio()
        for p in ('/dev/input/event9/../event9','/tmp/event9','event9','/dev/input/eventX'):
            with self.subTest(p=p):self.assertFalse(ip.gpio_source(p,self.state))
    def test_discovery_verified_gpio(self):
        bus=object.__new__(ip.Bus);bus.owner=OWNER;bus.composite_paths=lambda:[DEVICE.path]
        props={'Name':'AYN Thor','SourceDevicePaths':['/dev/input/event9'],
               'Capabilities':[ip.QUICK], 'DbusDevices':list(DEVICE.targets),'PersistentId':DEVICE.persistent_id}
        bus.prop=lambda p,n:props[n];bus.check_owner=lambda:None
        self.assertEqual(bus.discover(lambda p:p=='/dev/input/event9'),DEVICE)
    def test_discovery_refuses_unverified_source(self):
        bus=object.__new__(ip.Bus);bus.owner=OWNER;bus.composite_paths=lambda:[DEVICE.path]
        bus.prop=lambda p,n:{'Name':'AYN Thor','SourceDevicePaths':['/dev/input/event9']}[n];bus.check_owner=lambda:None
        with self.assertRaisesRegex(ip.RouteError,'ip_thor_not_found'):bus.discover(lambda p:False)

class SignalsTests(unittest.TestCase):
    def setUp(self):
        self.bus=object.__new__(ip.Bus);self.calls=[];self.queue=deque()
        self.bus.Gio=SimpleNamespace(DBusSignalFlags=SimpleNamespace(NONE=0))
        def subscribe(*args):self.calls.append(args);return len(self.calls)
        self.bus.connection=SimpleNamespace(signal_subscribe=subscribe,signal_unsubscribe=Mock())
        self.bus.check_owner=lambda:None
        self.ids=self.bus.subscribe(DEVICE,ACTION,self.queue)
    def emit(self,value=1.0,action=ACTION,owner=OWNER,path=DEVICE.targets[0],interface=ip.TARGET,member='InputEvent',sig='(sd)'):
        params=SimpleNamespace(get_type_string=lambda:sig,unpack=lambda:(action,value))
        self.calls[0][6](None,owner,path,interface,member,params,None)
    def test_subscription_is_narrow(self):
        self.assertEqual(self.calls[0][:5],(OWNER,ip.TARGET,'InputEvent',DEVICE.targets[0],ACTION))
    def test_press_release(self):
        self.emit(1.0);self.emit(0.0);self.assertEqual(list(self.queue),[1,0])
    def test_other_actions_not_captured(self):
        for a in ('ui_accept','ui_quick2','ui_screenshot','a',ACTION+'x'):self.emit(action=a)
        self.assertFalse(self.queue)
    def test_wrong_owner_ignored(self):self.emit(owner=':1.99');self.assertFalse(self.queue)
    def test_wrong_path_ignored(self):self.emit(path=DEVICE.targets[0]+'1');self.assertFalse(self.queue)
    def test_wrong_interface_ignored(self):self.emit(interface='other');self.assertFalse(self.queue)
    def test_wrong_member_ignored(self):self.emit(member='KeyboardEvent');self.assertFalse(self.queue)
    def test_wrong_signature_ignored(self):self.emit(sig='(si)');self.assertFalse(self.queue)
    def test_nonbinary_values_ignored(self):
        for v in (2.0,0.5,-1.0,float('nan'),'1',True):self.emit(value=v)
        self.assertFalse(self.queue)
    def test_bounded_queue_overflow(self):
        for _ in range(65):self.emit()
        self.assertEqual(list(self.queue),[None])
    def test_unsubscribe(self):self.bus.unsubscribe(self.ids);self.bus.connection.signal_unsubscribe.assert_called_once_with(1)

class HelperTests(unittest.TestCase):
    def test_raw_fallback_only_no_daemon(self):
        raw=object()
        with patch.object(ip.Lease,'open',side_effect=ip.RouteError('ip_not_running')),patch.object(ayn,'RawButton',return_value=raw):
            self.assertIs(ayn.open_source(Path('/unused')),raw)
    def test_no_permission_bypass(self):
        with patch.object(ip.Lease,'open',side_effect=ip.RouteError('ip_permission_denied')),patch.object(ayn,'RawButton') as raw:
            with self.assertRaises(ip.RouteError):ayn.open_source(Path('/unused'))
            raw.assert_not_called()
    def test_restore_attempts_both(self):
        with patch('sys.argv',['ayn.py','--restore']),patch.object(ayn,'restore_owned',side_effect=RuntimeError),patch.object(ip,'recover') as rec:
            self.assertEqual(ayn.main(),1);rec.assert_called_once()
    def test_private_route_drives_toggle(self):
        comp=SimpleNamespace(identity=lambda:['session'],read=Mock(return_value=0),set=Mock())
        clock=Mock(return_value=1.0);toggle=ayn.Toggle(comp,lambda d:None,clock=clock)
        self.assertTrue(toggle.press(1));self.assertFalse(toggle.press(1));toggle.press(0)
        comp.read.return_value=1;clock.return_value=2.0;self.assertTrue(toggle.press(1))
        self.assertEqual([c.args[0] for c in comp.set.call_args_list],[1,0])
    def test_all_new_messages_localized(self):
        from armada_dual_screen_input.ergo_i18n import CATALOGS
        for lang,catalog in CATALOGS.items():
            for text in ayn.REASON_TEXT.values():
                with self.subTest(lang=lang,text=text):self.assertIn(text,catalog);self.assertTrue(catalog[text])
    def test_no_global_profile_or_controller_commands(self):
        source=Path(ip.__file__).read_text()
        for forbidden in ("'SetTargetDevices'","'SetInterceptMode'","'SendEvent'","'Stop'",'EVIOCGRAB','chmod 666','sudo '):
            self.assertNotIn(forbidden,source)

if __name__=='__main__':unittest.main()
