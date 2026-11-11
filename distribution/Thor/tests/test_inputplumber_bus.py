"""Wire-contract and failure tests for Gio adapter. All connections are fakes."""
from __future__ import annotations
from copy import deepcopy
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock,patch
from armada_dual_screen_input import ergo_inputplumber as ip
from test_inputplumber import BASE, DEVICE, OWNER, ACTION, FakeBus

class Variant:
    def __init__(self,signature,values):self.signature=signature;self.values=values
    def unpack(self):return self.values

class WireTests(unittest.TestCase):
    def setUp(self):
        self.bus=object.__new__(ip.Bus);self.bus.owner=OWNER
        self.bus.GLib=SimpleNamespace(Variant=Variant,VariantType=SimpleNamespace(new=lambda s:s))
        self.bus.Gio=SimpleNamespace(DBusCallFlags=SimpleNamespace(NO_AUTO_START=2))
        self.bus.connection=SimpleNamespace(call_sync=Mock(return_value=Variant('(s)',('result',))))
    def test_rpc_bounded_no_autostart(self):
        self.assertEqual(self.bus.rpc(OWNER,'/path','iface','Method','(s)',('value',),'(s)'),('result',))
        args=self.bus.connection.call_sync.call_args.args
        self.assertEqual(args[:4],(OWNER,'/path','iface','Method'))
        self.assertEqual(args[4].signature,'(s)');self.assertEqual(args[4].values,('value',))
        self.assertEqual(args[5:],('(s)',2,1500,None))
    def test_properties_get_signature(self):
        self.bus.prop(DEVICE.path,'DbusDevices')
        args=self.bus.connection.call_sync.call_args.args
        self.assertEqual(args[:4],(OWNER,DEVICE.path,'org.freedesktop.DBus.Properties','Get'))
        self.assertEqual(args[4].values,(ip.COMPOSITE,'DbusDevices'));self.assertEqual(args[5],'(v)')
    def test_load_only_profile_method(self):
        self.bus.check_owner=Mock();self.bus.prop=lambda p,n:DEVICE.persistent_id
        self.bus.load(DEVICE,ip.make_route(BASE,ACTION))
        args=self.bus.connection.call_sync.call_args.args
        self.assertEqual(args[3],'LoadProfileFromYaml');self.assertEqual(args[4].signature,'(s)')
        self.assertEqual(ip.parse_profile(args[4].values[0]),ip.make_route(BASE,ACTION))
        self.assertEqual(args[5],'()')
    def test_load_refuses_new_identity(self):
        self.bus.check_owner=lambda:None;self.bus.prop=lambda p,n:'not-the-device'
        with self.assertRaisesRegex(ip.RouteError,'ip_session_changed'):self.bus.load(DEVICE,BASE)
        self.bus.connection.call_sync.assert_not_called()
    def test_load_refuses_targets(self):
        profile=deepcopy(BASE);profile['target_devices']=[]
        with self.assertRaisesRegex(ip.RouteError,'ip_profile_targets'):self.bus.load(DEVICE,profile)
        self.bus.connection.call_sync.assert_not_called()
    def test_permission_error_fixed_code(self):
        self.bus.connection.call_sync.side_effect=Exception('org.freedesktop.DBus.Error.AccessDenied: private info')
        with self.assertRaisesRegex(ip.RouteError,'^ip_permission_denied$'):self.bus.current_owner()
    def test_absent_daemon_fixed_code(self):
        self.bus.connection.call_sync.side_effect=Exception('org.freedesktop.DBus.Error.NameHasNoOwner')
        with self.assertRaisesRegex(ip.RouteError,'^ip_not_running$'):self.bus.current_owner()
    def test_api_missing_fixed_code(self):
        self.bus.connection.call_sync.side_effect=Exception('org.freedesktop.DBus.Error.UnknownMethod')
        with self.assertRaisesRegex(ip.RouteError,'^ip_api_unavailable$'):self.bus.current_owner()
    def test_no_raw_error_profile_exposed(self):
        self.bus.connection.call_sync.side_effect=Exception('secret profile contents')
        with self.assertRaisesRegex(ip.RouteError,'^ip_dbus_unavailable$'):self.bus.current_owner()
    def test_objectmanager_selects_composites_only(self):
        self.bus.rpc=Mock(return_value=({DEVICE.path:{ip.COMPOSITE:{}},ip.ROOT+'/other':{ip.COMPOSITE:{}}},))
        self.assertEqual(self.bus.composite_paths(),[DEVICE.path])
    def test_introspection_fallback_no_guessed_index(self):
        self.bus.rpc=Mock(side_effect=[ip.RouteError('ip_api_unavailable'),
                                     ('<node><node name="CompositeDevice3"/><node name="other"/></node>',)])
        self.assertEqual(self.bus.composite_paths(),[ip.ROOT+'/CompositeDevice3'])
    def test_introspection_does_not_accept_traversal(self):
        self.bus.rpc=Mock(side_effect=[ip.RouteError('ip_api_unavailable'),
                                     ('<node><node name="../CompositeDevice3"/></node>',)])
        self.assertEqual(self.bus.composite_paths(),[])
    def test_denied_enumeration_no_fallback(self):
        self.bus.rpc=Mock(side_effect=ip.RouteError('ip_permission_denied'))
        with self.assertRaises(ip.RouteError):self.bus.composite_paths()
        self.assertEqual(self.bus.rpc.call_count,1)
    def test_wait_refuses_external_profile(self):
        profile=deepcopy(BASE);profile['name']='External'
        self.bus.check_owner=lambda:None;self.bus.profile=lambda d:profile
        with self.assertRaisesRegex(ip.RouteError,'ip_profile_changed'):
            self.bus.wait_profile(DEVICE,ip.make_route(BASE,ACTION),BASE)
    def test_wait_accepts_delayed_metadata(self):
        target=ip.make_route(BASE,ACTION)
        self.bus.check_owner=lambda:None;self.bus.profile=Mock(side_effect=[BASE,target]);self.bus.iterate=Mock()
        with patch.object(ip.time,'sleep'):self.bus.wait_profile(DEVICE,target,BASE)
        self.bus.iterate.assert_called_once()
    def test_wait_bounded(self):
        self.bus.check_owner=lambda:None;self.bus.profile=lambda d:BASE
        with patch.object(ip.time,'monotonic',side_effect=[0,3]):
            with self.assertRaisesRegex(ip.RouteError,'ip_profile_not_confirmed'):
                self.bus.wait_profile(DEVICE,ip.make_route(BASE,ACTION),BASE)
    def test_restore_path_checks_contents(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)/'profile.yaml';p.write_text(ip.yaml.safe_dump(BASE))
            self.bus.check_owner=Mock();self.bus.prop=lambda p,n:DEVICE.persistent_id
            self.assertTrue(self.bus.restore_path(DEVICE,str(p),BASE))
            args=self.bus.connection.call_sync.call_args.args
            self.assertEqual(args[3],'LoadProfilePath');self.assertEqual(args[4].values,(str(p),))
    def test_restore_path_ignores_changed_file(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)/'profile.yaml';different=deepcopy(BASE);different['name']='Changed';p.write_text(json.dumps(different))
            self.assertFalse(self.bus.restore_path(DEVICE,str(p),BASE))
            self.bus.connection.call_sync.assert_not_called()
    def test_restore_path_ignores_symlink(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)/'profile.yaml';p.write_text(json.dumps(BASE));q=Path(td)/'link';q.symlink_to(p)
            self.assertFalse(self.bus.restore_path(DEVICE,str(q),BASE))
            self.bus.connection.call_sync.assert_not_called()
    def test_restore_path_relative_refused(self):self.assertFalse(self.bus.restore_path(DEVICE,'../anything',BASE))

class RaceTests(unittest.TestCase):
    def test_profile_changes_during_subscribe_no_write(self):
        with tempfile.TemporaryDirectory() as td:
            bus=FakeBus();original=bus.subscribe
            def subscribe(*args):
                result=original(*args);bus.current['name']='External';return result
            bus.subscribe=subscribe
            with self.assertRaisesRegex(ip.RouteError,'ip_profile_changed'):ip.Lease.open(Path(td),bus)
            self.assertFalse(any(isinstance(x,tuple) and x[0]=='load' for x in bus.log))
            self.assertFalse((Path(td)/ip.JOURNAL).exists())
    def test_partial_subscription_failure_cleans_up(self):
        bus=object.__new__(ip.Bus);bus.Gio=SimpleNamespace(DBusSignalFlags=SimpleNamespace(NONE=0))
        bus.connection=SimpleNamespace(signal_subscribe=Mock(side_effect=[1,RuntimeError('failure')]),signal_unsubscribe=Mock())
        device=ip.Device(OWNER,DEVICE.path,DEVICE.persistent_id,(DEVICE.targets[0],DEVICE.targets[0]+'1'))
        with self.assertRaises(RuntimeError):bus.subscribe(device,ACTION,[])
        bus.connection.signal_unsubscribe.assert_called_once_with(1)

if __name__=='__main__':unittest.main()
