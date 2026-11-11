"""Regression for physical capabilities != translated event in InputPlumber 0.83.

All system files and D-Bus values below are synthetic, in temporary directories.
No user's diagnostics, profile, identity or address is included.
"""
from copy import deepcopy
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from armada_dual_screen_input import ergo_inputmap as im
from armada_dual_screen_input import ergo_inputplumber as ip
from test_inputplumber import DEVICE, OWNER, BASE, ACTION, FakeBus

DEVICE_CONFIG = {
    'version': 1, 'kind': 'CompositeDevice', 'name': 'AYN Thor',
    'source_devices': [
        {'group': 'gamepad', 'capability_map_id': 'ayn2',
         'evdev': {'name': 'AYN Odin2 Gamepad'}},
        {'group': 'gamepad', 'capability_map_id': 'ayn2',
         'evdev': {'name': 'gpio-keys-ayn', 'phys_path': 'gpio-keys/input0', 'handler': 'event*'}},
    ],
    'target_devices': ['xbox-elite'],  # Device config, NOT runtime profile.
}
MAP = {'version': 2, 'kind': 'CapabilityMap', 'name': 'Fixture AYN Type 2',
       'id': 'ayn2', 'mapping': [
           {'name': 'AYN Key', 'source_events': [deepcopy(im.F24_EVENT)],
            'target_event': deepcopy(im.QUICK_SOURCE)}]}
PROOF = {'kind': 'installed_source_map', 'map_id': 'ayn2', 'map_file': 'fixture.yaml',
         'physical_event': 'KEY_F24', 'translated_event': ip.QUICK,
         'runtime_events_tested': False}


class FixtureFiles(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.root = Path(self.temp.name)
        self.devices = self.root/'devices'; self.maps = self.root/'capability_maps'
        self.override = self.root/'overrides'
        for d in (self.devices, self.maps, self.override): d.mkdir(mode=0o755)
        self.device_file = self.devices/'50-ayn_thor.yaml'
        self.map_file = self.maps/'ayn_type2.yaml'
        self.write(self.device_file, DEVICE_CONFIG); self.write(self.map_file, MAP)
    def tearDown(self): self.temp.cleanup()
    def write(self, path, value):
        path.write_text(json.dumps(value), encoding='utf-8'); path.chmod(0o644)
    def check(self):
        return im.installed_ayn_map('AYN Thor', (self.devices,), (self.maps, self.override), os.getuid())
    def test_verified_complete_chain(self):
        result = self.check()
        self.assertEqual(result['translated_event'], ip.QUICK)
        self.assertEqual(result['physical_event'], 'KEY_F24')
        self.assertEqual(result['map_id'], 'ayn2')
        self.assertFalse(result['runtime_events_tested'])
    def test_no_files_changed(self):
        before = {p:p.read_bytes() for p in self.root.rglob('*.yaml')}
        self.check(); self.assertEqual(before, {p:p.read_bytes() for p in before})
    def test_missing_source_file(self):
        self.device_file.unlink()
        with self.assertRaises(im.MappingError): self.check()
    def test_missing_map(self):
        self.map_file.unlink()
        with self.assertRaises(im.MappingError): self.check()
    def test_missing_system_directories(self):
        with self.assertRaises(im.MappingError):
            im.installed_ayn_map('AYN Thor', (self.root/'missing',), (), os.getuid())
    def test_wrong_live_device_name(self):
        with self.assertRaises(im.MappingError): im.installed_ayn_map('Other', (), (), os.getuid())
    def test_source_without_map_id_rejected(self):
        data=deepcopy(DEVICE_CONFIG); del data['source_devices'][1]['capability_map_id']; self.write(self.device_file,data)
        with self.assertRaises(im.MappingError): self.check()
    def test_global_extra_translation_rejected(self):
        data=deepcopy(DEVICE_CONFIG); data['capability_map_id']='another'; self.write(self.device_file,data)
        with self.assertRaises(im.MappingError): self.check()
    def test_source_flags_rejected(self):
        for flag in ('blocked','ignore','passthrough'):
            with self.subTest(flag=flag):
                data=deepcopy(DEVICE_CONFIG);data['source_devices'][1][flag]=True;self.write(self.device_file,data)
                with self.assertRaises(im.MappingError):self.check()
    def test_source_filter_rejected(self):
        data=deepcopy(DEVICE_CONFIG); data['source_devices'][1]['events']={'exclude':['Keyboard:KeyF24']};self.write(self.device_file,data)
        with self.assertRaises(im.MappingError):self.check()
    def test_duplicate_gpio_source_rejected(self):
        data=deepcopy(DEVICE_CONFIG);data['source_devices'].append(deepcopy(data['source_devices'][1]));self.write(self.device_file,data)
        with self.assertRaises(im.MappingError):self.check()
    def test_same_name_conflicting_device_rejected(self):
        data=deepcopy(DEVICE_CONFIG);data['source_devices'][1]['capability_map_id']='other'
        self.write(self.devices/'10-custom.yaml',data)
        with self.assertRaises(im.MappingError):self.check()
    def test_same_name_same_map_accepted(self):
        self.write(self.devices/'10-custom.yaml',DEVICE_CONFIG);self.check()
    def test_other_device_config_does_not_match(self):
        data=deepcopy(DEVICE_CONFIG);data['name']='Unrelated'
        self.write(self.devices/'other.yaml',data);self.check()
    def test_map_override_precedence_same_name(self):
        data=deepcopy(MAP);data['mapping'][0]['target_event']={'keyboard':'KeyF24'}
        self.write(self.override/'ayn_type2.yaml',data)
        with self.assertRaises(im.MappingError):self.check()
    def test_map_override_precedence_sorted_filename(self):
        data=deepcopy(MAP);data['mapping'][0]['target_event']={'keyboard':'KeyF24'}
        self.write(self.override/'zz-custom.yaml',data)
        with self.assertRaises(im.MappingError):self.check()
    def test_earlier_map_does_not_override_later(self):
        data=deepcopy(MAP);data['mapping'][0]['target_event']={'keyboard':'KeyF24'}
        self.write(self.override/'00-custom.yaml',data);self.check()
    def test_symlink_file_rejected(self):
        other=self.root/'other.yaml';self.map_file.rename(other);self.map_file.symlink_to(other)
        with self.assertRaises(im.MappingError):self.check()
    def test_symlink_directory_rejected(self):
        other=self.root/'other';self.maps.rename(other);self.maps.symlink_to(other,target_is_directory=True)
        with self.assertRaises(im.MappingError):self.check()
    def test_world_writable_file_rejected(self):
        self.map_file.chmod(0o666)
        with self.assertRaises(im.MappingError):self.check()
    def test_group_writable_directory_rejected(self):
        self.maps.chmod(0o775)
        with self.assertRaises(im.MappingError):self.check()
    def test_wrong_owner_rejected(self):
        with self.assertRaises(im.MappingError):
            im.installed_ayn_map('AYN Thor',(self.devices,),(self.maps,),os.getuid()+1000)
    def test_oversize_file_rejected(self):
        self.map_file.write_text(' '*(im.MAX_FILE+1))
        with self.assertRaises(im.MappingError):self.check()
    def test_file_count_bounded(self):
        with patch.object(im,'MAX_FILES',0),self.assertRaises(im.MappingError):self.check()
    def test_aggregate_size_bounded(self):
        with patch.object(im,'MAX_TOTAL',1),self.assertRaises(im.MappingError):self.check()
    def test_trusted_yaml_file_parsed(self):
        self.map_file.write_text(ip.yaml.safe_dump(MAP));self.check()
    def test_unsafe_yaml_rejected(self):
        self.map_file.write_text('!!python/object/apply:os.system ["never"]')
        with self.assertRaises(im.MappingError):self.check()
    def test_duplicate_yaml_keys_rejected(self):
        self.map_file.write_text(ip.yaml.safe_dump(MAP)+'id: other\n')
        with self.assertRaises(im.MappingError):self.check()
    def test_alias_yaml_rejected(self):
        with self.assertRaises(im.MappingError):im.parse_config('x: &a [1]\ny: *a')
    def test_deep_yaml_rejected(self):
        with self.assertRaises(im.MappingError):im.parse_config('x: '+ '['*40+'1'+']'*40)
    def test_no_profile_or_identifier_in_proof(self):
        proof=self.check()
        self.assertNotIn('name',proof);self.assertNotIn('profile',proof);self.assertNotIn(str(self.root),json.dumps(proof))


class MapSemanticsTests(unittest.TestCase):
    def test_exact_map_passes(self):self.assertTrue(im.proves_f24_to_quick(MAP,'ayn2'))
    def test_multi_source_explicit_passes(self):
        data=deepcopy(MAP);data['mapping'][0]['mapping_type']={'evdev':'multi_source'}
        self.assertTrue(im.proves_f24_to_quick(data,'ayn2'))
    def test_wrong_header(self):
        for key,value in (('version',1),('version',True),('kind','DeviceProfile'),('id','other')):
            with self.subTest(key=key,value=value):
                data=deepcopy(MAP);data[key]=value
                with self.assertRaises(im.MappingError):im.proves_f24_to_quick(data,'ayn2')
    def test_different_target_rejected(self):
        data=deepcopy(MAP);data['mapping'][0]['target_event']={'gamepad':{'button':'QuickAccess'}}
        with self.assertRaises(im.MappingError):im.proves_f24_to_quick(data,'ayn2')
    def test_extra_source_chord_rejected(self):
        data=deepcopy(MAP);data['mapping'][0]['source_events'].append({'evdev':{'event_code':'KEY_A'}})
        with self.assertRaises(im.MappingError):im.proves_f24_to_quick(data,'ayn2')
    def test_delayed_chord_rejected(self):
        data=deepcopy(MAP);data['mapping'][0]['mapping_type']={'evdev':'delayed_chord'}
        with self.assertRaises(im.MappingError):im.proves_f24_to_quick(data,'ayn2')
    def test_other_source_to_same_quick_rejected(self):
        data=deepcopy(MAP);m=deepcopy(data['mapping'][0]);m['source_events'][0]['evdev']['event_code']='KEY_F23';data['mapping'].append(m)
        with self.assertRaises(im.MappingError):im.proves_f24_to_quick(data,'ayn2')
    def test_duplicate_f24_rule_rejected(self):
        data=deepcopy(MAP);data['mapping'].append(deepcopy(data['mapping'][0]))
        with self.assertRaises(im.MappingError):im.proves_f24_to_quick(data,'ayn2')
    def test_other_unrelated_mapping_preserved(self):
        data=deepcopy(MAP);data['mapping'].append({'name':'Other','source_events':[{'evdev':{'event_code':'BTN_SOUTH'}}],'target_event':{'gamepad':{'button':'South'}}})
        original=deepcopy(data);im.proves_f24_to_quick(data,'ayn2');self.assertEqual(original,data)


class AdvertisedCapabilitiesRegression(unittest.TestCase):
    def setUp(self):
        self.bus=object.__new__(ip.Bus);self.bus.owner=OWNER
        self.bus.composite_paths=lambda:[DEVICE.path];self.bus.check_owner=lambda:None
        self.props={'Name':'AYN Thor','SourceDevicePaths':['/dev/input/event9'],
                    'Capabilities':['Sync','Keyboard:KeyF24'],
                    'DbusDevices':list(DEVICE.targets),'PersistentId':DEVICE.persistent_id}
        self.bus.prop=lambda p,n:self.props[n]
        self.bus.profile=lambda device:deepcopy(BASE)
    def discover(self,proof=PROOF):
        return self.bus.discover(lambda path:True,lambda name:deepcopy(proof))
    def test_raw_f24_plus_proven_v2_map_passes(self):
        device=self.discover();self.assertEqual(device.mapping_evidence,'installed_source_map')
        row=self.bus.discovery_observations[0]
        self.assertFalse(row['quickaccess2_advertised']);self.assertTrue(row['raw_f24_advertised'])
    def test_quick_advertised_uses_original_path(self):
        self.props['Capabilities']=[ip.QUICK]
        with patch.object(ip,'installed_ayn_map',side_effect=AssertionError('unnecessary read')):
            self.assertEqual(self.bus.discover(lambda p:True),DEVICE)
    def test_no_blind_f24_acceptance(self):
        with patch.object(ip,'installed_ayn_map',side_effect=im.MappingError()):
            with self.assertRaisesRegex(ip.RouteError,'ip_config_unverified'):
                self.bus.discover(lambda p:True)
    def test_raw_f24_not_used_as_route_source(self):
        self.discover();route=ip.make_route(BASE,ACTION)
        self.assertEqual(route['mapping'][1]['source_event'],ip.SOURCE)
        self.assertNotIn({'keyboard':'KeyF24'},[m['source_event'] for m in route['mapping']])
    def test_no_raw_no_quick_refused(self):
        self.props['Capabilities']=['Keyboard:KeyF1']
        with self.assertRaisesRegex(ip.RouteError,'ip_ayn_mapping_unavailable'):self.discover()
    def test_unverified_physical_gpio_refused(self):
        with self.assertRaisesRegex(ip.RouteError,'ip_thor_not_found'):
            self.bus.discover(lambda p:False,lambda name:deepcopy(PROOF))
    def test_wrong_proof_not_accepted(self):
        proof=deepcopy(PROOF);proof['translated_event']='Keyboard:KeyF24'
        with self.assertRaisesRegex(ip.RouteError,'ip_config_unverified'):self.discover(proof)
    def test_invalid_capabilities_separate_reason(self):
        for caps in (None,'Keyboard:KeyF24',[True]):
            with self.subTest(caps=caps):
                self.props['Capabilities']=caps
                with self.assertRaisesRegex(ip.RouteError,'ip_capabilities_invalid'):self.discover()
    def test_missing_target_still_refused(self):
        self.props['DbusDevices']=[]
        with self.assertRaisesRegex(ip.RouteError,'ip_signal_target_unavailable'):self.discover()
    def test_failure_diagnostic_retains_flags(self):
        with patch.object(ip,'installed_ayn_map',side_effect=im.MappingError('ip_config_conflict')):
            result=ip.probe(self.bus,lambda p:True)
        self.assertFalse(result['available']);self.assertEqual(result['reason'],'ip_config_conflict')
        self.assertTrue(result['observations'][0]['raw_f24_advertised'])
        self.assertFalse(result['observations'][0]['quickaccess2_advertised'])
    def test_success_diagnostic_reports_proof(self):
        with patch.object(ip,'installed_ayn_map',return_value=deepcopy(PROOF)):
            result=ip.probe(self.bus,lambda p:True)
        self.assertTrue(result['available']);self.assertEqual(result['mapping_evidence'],'installed_source_map')
        self.assertFalse(result['physical_events_tested'])
        self.assertEqual(result['explicit_ayn_rule_count'],1)
        self.assertNotIn(BASE['name'],json.dumps(result))
    def test_daemon_failure_diagnostic_is_useful(self):
        with patch.object(ip,'Bus',side_effect=ip.RouteError('ip_not_running')):
            result=ip.probe()
        self.assertFalse(result['available']);self.assertEqual(result['reason'],'ip_not_running')
    def test_profile_failure_keeps_discovery(self):
        self.bus.profile=lambda device: (_ for _ in ()).throw(ip.RouteError('ip_profile_targets'))
        with patch.object(ip,'installed_ayn_map',return_value=deepcopy(PROOF)):
            result=ip.probe(self.bus,lambda p:True)
        self.assertEqual(result['reason'],'ip_profile_targets');self.assertTrue(result['observations'])
    def test_route_and_restore_with_real_discovery_logic(self):
        with tempfile.TemporaryDirectory() as folder:
            fake=FakeBus()
            fake.discover=lambda source_check=None:self.discover()
            lease=ip.Lease.open(Path(folder),fake)
            self.assertEqual(lease.device.mapping_evidence,'installed_source_map')
            self.assertEqual(fake.current['mapping'][1]['source_event'],ip.SOURCE)
            lease.close();self.assertEqual(fake.current,BASE)

if __name__=='__main__':unittest.main()
