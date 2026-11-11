"""Scoped, reversible AYN routing through InputPlumber's public D-Bus API.

The Thor F24 GPIO is owned by InputPlumber and mapped to QuickAccess2 before
profile translation. We replace ONLY that profile mapping with a private
D-Bus action, and listen ONLY for that action from that composite's D-Bus target.
No evdev capture, keyboard monitoring, intercept-mode changes, permission
changes, service restarts or controller-target replacement are performed.

References pinned to Armada 20261109 / InputPlumber 0.83.0 are in the release's
Documentation/AYN-INPUTPLUMBER.md. All imports are inert; Gio is loaded only when
an actual connection is requested. Profile parsing uses bundled pure PyYAML.
"""
from __future__ import annotations

from collections import deque
from copy import deepcopy
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import tempfile
import time
import uuid
import xml.etree.ElementTree as ET

from . import _vendor_yaml as yaml
from .ergo_inputmap import MappingError, installed_ayn_map

SERVICE = 'org.shadowblip.InputPlumber'
ROOT = '/org/shadowblip/InputPlumber'
COMPOSITE = 'org.shadowblip.Input.CompositeDevice'
TARGET = 'org.shadowblip.Input.DBusDevice'
QUICK = 'Gamepad:Button:QuickAccess2'
SOURCE = {'gamepad': {'button': 'QuickAccess2'}}
JOURNAL = 'ayn-inputplumber.json'
MAX_PROFILE = 65536
MAX_JOURNAL = 524288
ACTION_RE = re.compile(r'^thor_ergo_ayn_[0-9a-f]{32}$')
COMPOSITE_RE = re.compile(r'^/org/shadowblip/InputPlumber/CompositeDevice[0-9]+$')
TARGET_RE = re.compile(r'^/org/shadowblip/InputPlumber/devices/target/[A-Za-z0-9_]+$')
ALLOWED_MODELS = {'AYN Thor', 'AYN Thor Lite'}


class RouteError(RuntimeError):
    """Public, fixed error codes only; never leak profiles into diagnostic logs."""
    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


class ProfileLoader(yaml.SafeLoader):
    """Bounded depth, no aliases, no duplicate keys, no object constructors."""
    def __init__(self, stream):
        self.depth = 0
        super().__init__(stream)

    def compose_node(self, parent, index):
        if self.check_event(yaml.AliasEvent):
            raise RouteError('ip_profile_format')
        self.depth += 1
        try:
            if self.depth > 32:
                raise RouteError('ip_profile_format')
            return super().compose_node(parent, index)
        finally:
            self.depth -= 1

    def construct_mapping(self, node, deep=False):
        pairs = {}
        for key_node, value_node in node.value:
            key = self.construct_object(key_node, deep=deep)
            if not isinstance(key, str) or key in pairs:
                raise RouteError('ip_profile_format')
            pairs[key] = self.construct_object(value_node, deep=deep)
        return pairs


def fingerprint(profile: dict) -> str:
    data = json.dumps(profile, sort_keys=True, ensure_ascii=True, allow_nan=False,
                      separators=(',', ':')).encode('ascii')
    return hashlib.sha256(data).hexdigest()


def parse_profile(text: str) -> dict:
    if not isinstance(text, str) or len(text.encode('utf-8')) > MAX_PROFILE:
        raise RouteError('ip_profile_format')
    try:
        profile = yaml.load(text, Loader=ProfileLoader)
        if (not isinstance(profile, dict) or type(profile.get('version')) is not int
                or profile['version'] != 1 or profile.get('kind') != 'DeviceProfile'
                or not isinstance(profile.get('name'), str)
                or not isinstance(profile.get('mapping'), list)):
            raise RouteError('ip_profile_format')
        for mapping in profile['mapping']:
            if (not isinstance(mapping, dict) or not isinstance(mapping.get('name'), str)
                    or not isinstance(mapping.get('source_event'), dict)
                    or not isinstance(mapping.get('target_events'), list)
                    or any(not isinstance(item, dict) for item in mapping['target_events'])):
                raise RouteError('ip_profile_format')
        fingerprint(profile)  # Reject non-JSON values, recursive data, NaN, etc.
        return profile
    except RouteError:
        raise
    except Exception as exc:
        raise RouteError('ip_profile_format') from exc


def can_route(profile: dict) -> None:
    # Loading a profile with target_devices, even [], recreates controller targets.
    # Refuse rather than alter/drop the user's controller configuration silently.
    if profile.get('target_devices') is not None:
        raise RouteError('ip_profile_targets')
    if len(profile['mapping']) > 512:
        raise RouteError('ip_profile_format')
    for mapping in profile['mapping']:
        source = mapping['source_event']
        gamepad = source.get('gamepad')
        if gamepad is not None and not isinstance(gamepad, dict):
            raise RouteError('ip_profile_format')
        if isinstance(gamepad, dict) and gamepad.get('button') == 'QuickAccess2' and source != SOURCE:
            raise RouteError('ip_profile_format')


def is_quick(mapping: dict) -> bool:
    return mapping.get('source_event') == SOURCE


def make_route(original: dict, action: str) -> dict:
    can_route(original)
    if not ACTION_RE.fullmatch(action):
        raise RouteError('ip_journal_invalid')
    profile = deepcopy(original)
    profile['name'] = 'Thor Ergo AYN ' + action[-32:]
    mappings = []
    position = None
    for mapping in profile['mapping']:
        if is_quick(mapping):
            if position is None:
                position = len(mappings)
        else:
            mappings.append(mapping)
    mappings.insert(len(mappings) if position is None else position, {
        'name': 'Thor Ergo lower-screen toggle',
        'source_event': deepcopy(SOURCE),
        'target_events': [{'dbus': action}],
    })
    profile['mapping'] = mappings
    return profile


def undo_route(current: dict, record: dict) -> dict | None:
    """Restore ours only; preserve a newer external profile or unrelated edits."""
    original, applied = record['original'], record['applied']
    if current == applied or current == original:
        # A timed-out load can have been applied before async D-Bus profile
        # metadata catches up. Reassert the original rather than drop recovery.
        return deepcopy(original)
    ours = [m for m in applied['mapping'] if is_quick(m)][0]
    if ours not in current['mapping']:
        return None  # Someone else selected a new AYN mapping. Do not overwrite it.
    can_route(current)
    restored = deepcopy(current)
    mappings = restored['mapping']
    index = mappings.index(ours)
    mappings = [m for m in mappings if m != ours]
    if not any(is_quick(m) for m in mappings):
        mappings[index:index] = deepcopy([m for m in original['mapping'] if is_quick(m)])
    restored['mapping'] = mappings
    if restored['name'] == applied['name']:
        restored['name'] = original['name']
    return restored


def _boot_id() -> str:
    return Path('/proc/sys/kernel/random/boot_id').read_text().strip()


def gpio_source(path: str, sysroot=Path('/sys/class/input')) -> bool:
    """Identify the physical source using metadata, without opening its node."""
    if not isinstance(path, str) or not re.fullmatch(
            r'/dev/(?:input|inputplumber/sources)/event[0-9]+', path):
        return False
    try:
        entry = Path(sysroot) / Path(path).name / 'device'
        if (entry / 'name').read_text().strip() not in ('gpio-keys-ayn', 'AYN Key'):
            return False
        words = (entry / 'capabilities/key').read_text().split()
        # Thor is AArch64; sysfs prints native 64-bit words most-significant first.
        if not words or len(words) > 12 or any(not re.fullmatch(r'[0-9a-fA-F]{1,16}', w) for w in words):
            return False
        bitmap = sum(int(word, 16) << (64 * index)
                     for index, word in enumerate(reversed(words)))
        return bitmap == 1 << 194
    except OSError:
        return False


@dataclass(frozen=True)
class Device:
    owner: str
    path: str
    persistent_id: str
    targets: tuple[str, ...]
    mapping_evidence: str = 'advertised_capability'


class Bus:
    """Synchronous bounded requests and narrowly filtered Gio signal subscriptions."""
    def __init__(self):
        try:
            import gi
            gi.require_version('Gio', '2.0')
            from gi.repository import Gio, GLib
            self.Gio, self.GLib = Gio, GLib
            self.context = GLib.MainContext.default()
            self.connection = Gio.bus_get_sync(Gio.BusType.SYSTEM, None)
        except Exception as exc:
            raise RouteError('ip_dbus_unavailable') from exc
        self.owner = self.current_owner()

    def rpc(self, destination, path, interface, method, signature=None, values=(), reply=None):
        try:
            parameters = self.GLib.Variant(signature, values) if signature else None
            expected = self.GLib.VariantType.new(reply) if reply else None
            result = self.connection.call_sync(
                destination, path, interface, method, parameters, expected,
                self.Gio.DBusCallFlags.NO_AUTO_START, 1500, None)
            return result.unpack()
        except Exception as exc:
            text = str(exc)
            if any(word in text for word in ('AccessDenied', 'NotAuthorized', 'not authorized', 'PermissionDenied', 'Permission denied')):
                code = 'ip_permission_denied'
            elif any(word in text for word in ('ServiceUnknown', 'NameHasNoOwner')):
                code = 'ip_not_running'
            elif any(word in text for word in ('UnknownMethod', 'UnknownInterface', 'UnknownProperty')):
                code = 'ip_api_unavailable'
            else:
                code = 'ip_dbus_unavailable'
            raise RouteError(code) from exc

    def current_owner(self):
        return self.rpc('org.freedesktop.DBus', '/org/freedesktop/DBus',
                        'org.freedesktop.DBus', 'GetNameOwner', '(s)', (SERVICE,), '(s)')[0]

    def check_owner(self):
        if self.current_owner() != self.owner:
            raise RouteError('ip_session_changed')

    def prop(self, path, name):
        return self.rpc(self.owner, path, 'org.freedesktop.DBus.Properties',
                        'Get', '(ss)', (COMPOSITE, name), '(v)')[0]

    def composite_paths(self):
        try:
            objects = self.rpc(self.owner, ROOT, 'org.freedesktop.DBus.ObjectManager',
                               'GetManagedObjects', reply='(a{oa{sa{sv}}})')[0]
            return sorted(path for path, interfaces in objects.items()
                          if COMPOSITE_RE.fullmatch(path) and COMPOSITE in interfaces)
        except RouteError as exc:
            if exc.code not in ('ip_api_unavailable', 'ip_dbus_unavailable'):
                raise
            # Some releases place ObjectManager elsewhere; enumerate only direct
            # CompositeDevice children, never guess a numbered device.
            xml = self.rpc(self.owner, ROOT, 'org.freedesktop.DBus.Introspectable',
                           'Introspect', reply='(s)')[0]
            if len(xml) > 262144:
                raise RouteError('ip_api_unavailable')
            try:
                tree = ET.fromstring(xml)
                return sorted(ROOT + '/' + node.get('name', '') for node in tree.findall('node')
                              if COMPOSITE_RE.fullmatch(ROOT + '/' + node.get('name', '')))
            except ET.ParseError as error:
                raise RouteError('ip_api_unavailable') from error

    def discover(self, source_check=gpio_source, mapping_check=None):
        """Find the physical Thor, not an arbitrary virtual keyboard.

        InputPlumber 0.83 evdev drivers report raw KEY_F24 capabilities while
        translating the actual event to QuickAccess2 with a per-source V2 map.
        A missing advertised QuickAccess2 is therefore NOT a missing profile
        rule. Verify the installed source map for that compatibility case.
        """
        matches = []
        observations = []
        self.discovery_observations = observations
        paths = self.composite_paths()
        if len(paths) > 128:
            raise RouteError('ip_device_ambiguous')
        for path in paths:
            name = self.prop(path, 'Name')
            if name not in ALLOWED_MODELS:
                continue
            row = {'composite': path, 'model': name}
            observations.append(row)
            try:
                sources = self.prop(path, 'SourceDevicePaths')
                verified = (isinstance(sources, (list, tuple))
                            and any(source_check(s) for s in sources))
                row['dedicated_gpio_source_verified'] = bool(verified)
                if not verified:
                    continue
                capabilities = self.prop(path, 'Capabilities')
                if (not isinstance(capabilities, (list, tuple)) or len(capabilities) > 4096
                        or any(not isinstance(c, str) for c in capabilities)):
                    raise RouteError('ip_capabilities_invalid')
                advertised = QUICK in capabilities
                raw_f24 = 'Keyboard:KeyF24' in capabilities
                row.update(capability_count=len(capabilities),
                           quickaccess2_advertised=advertised,
                           raw_f24_advertised=raw_f24)
                evidence = 'advertised_capability'
                if not advertised:
                    # Raw F24 is corroborating evidence, not the event to route.
                    if not raw_f24:
                        raise RouteError('ip_ayn_mapping_unavailable')
                    try:
                        proof = (mapping_check or installed_ayn_map)(name)
                    except MappingError as error:
                        row['mapping_check_details'] = error.details
                        raise RouteError(error.code) from error
                    if (not isinstance(proof, dict)
                            or proof.get('kind') != 'installed_source_map'
                            or proof.get('physical_event') != 'KEY_F24'
                            or proof.get('translated_event') != QUICK):
                        raise RouteError('ip_config_unverified')
                    row['source_map'] = proof
                    evidence = 'installed_source_map'
                row['mapping_evidence'] = evidence
                targets = self.prop(path, 'DbusDevices')
                if (not isinstance(targets, (list, tuple)) or not targets or len(targets) > 16
                        or any(not isinstance(p, str) or not TARGET_RE.fullmatch(p) for p in targets)):
                    raise RouteError('ip_signal_target_unavailable')
                row['dbus_target_count'] = len(targets)
                persistent = self.prop(path, 'PersistentId')
                if not isinstance(persistent, str) or not persistent or len(persistent) > 512:
                    raise RouteError('ip_identity_unavailable')
                row['persistent_identity_present'] = True
                matches.append(Device(self.owner, path, persistent, tuple(sorted(set(targets))), evidence))
            except RouteError as error:
                row['reason'] = error.code
                raise
        self.check_owner()
        if len(matches) != 1:
            raise RouteError('ip_device_ambiguous' if matches else 'ip_thor_not_found')
        return matches[0]

    def profile(self, device):
        text = self.rpc(self.owner, device.path, COMPOSITE, 'GetProfileYaml', reply='(s)')[0]
        return parse_profile(text)

    def load(self, device, profile):
        can_route(profile)
        self.check_owner()
        if self.prop(device.path, 'PersistentId') != device.persistent_id:
            raise RouteError('ip_session_changed')
        # JSON is a YAML subset explicitly supported by InputPlumber's API.
        text = json.dumps(profile, ensure_ascii=True, allow_nan=False)
        if len(text.encode('utf-8')) > MAX_PROFILE:
            raise RouteError('ip_profile_format')
        self.rpc(self.owner, device.path, COMPOSITE, 'LoadProfileFromYaml', '(s)', (text,), '()')

    def restore_path(self, device, path, original):
        """Restore original ProfilePath metadata only if the file still matches."""
        if not isinstance(path, str) or not path.startswith('/'):
            return False
        try:
            file = Path(path)
            info = file.stat()
            if (file.is_symlink() or not stat.S_ISREG(info.st_mode)
                    or info.st_uid not in (0, os.getuid()) or info.st_size > MAX_PROFILE):
                return False
            if parse_profile(file.read_text(encoding='utf-8')) != original:
                return False
        except (OSError, ValueError, RouteError):
            return False
        self.check_owner()
        if self.prop(device.path, 'PersistentId') != device.persistent_id:
            raise RouteError('ip_session_changed')
        self.rpc(self.owner, device.path, COMPOSITE, 'LoadProfilePath', '(s)', (path,), '()')
        return True

    def wait_profile(self, device, expected, previous=None):
        deadline = time.monotonic() + 2.5
        while True:
            self.check_owner()
            current = self.profile(device)
            if current == expected:
                return
            if previous is not None and current != previous:
                raise RouteError('ip_profile_changed')
            if time.monotonic() >= deadline:
                raise RouteError('ip_profile_not_confirmed')
            self.iterate()
            time.sleep(.04)

    def subscribe(self, device, action, queue):
        subscriptions = []
        def callback(connection, sender, path, interface, member, params, user_data=None):
            if (sender != device.owner or path not in device.targets
                    or interface != TARGET or member != 'InputEvent'):
                return
            try:
                if params.get_type_string() != '(sd)':
                    return
                event, value = params.unpack()
                if event == action and type(value) in (int, float) and value in (0.0, 1.0):
                    # No other keyboard/gamepad events are queued or recorded.
                    if len(queue) >= 64:
                        queue.clear(); queue.append(None)
                    else:
                        queue.append(int(value))
            except (TypeError, ValueError):
                return
        try:
            for path in device.targets:
                subscriptions.append(self.connection.signal_subscribe(
                    device.owner, TARGET, 'InputEvent', path, action,
                    self.Gio.DBusSignalFlags.NONE, callback, None))
            # Round trip after AddMatch ensures subscriptions precede profile load.
            self.check_owner()
            return subscriptions
        except Exception:
            self.unsubscribe(subscriptions)
            raise

    def unsubscribe(self, subscriptions):
        for item in subscriptions:
            self.connection.signal_unsubscribe(item)

    def iterate(self):
        for _ in range(128):
            if not self.context.pending():
                break
            self.context.iteration(False)


class Journal:
    def __init__(self, state: Path):
        self.path = Path(state) / JOURNAL

    def read(self):
        if not self.path.exists():
            return None
        try:
            info = self.path.lstat()
            if (not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid()
                    or info.st_size > MAX_JOURNAL):
                raise RouteError('ip_journal_invalid')
            record = json.loads(self.path.read_text(encoding='utf-8'))
            if not record:
                return None
            if (record.get('schema') != 1 or not ACTION_RE.fullmatch(record.get('action', ''))
                    or not COMPOSITE_RE.fullmatch(record.get('path', ''))
                    or not re.fullmatch(r':[0-9]+\.[0-9]+', record.get('owner', ''))
                    or not isinstance(record.get('boot'), str)
                    or not isinstance(record.get('persistent_id'), str)):
                raise RouteError('ip_journal_invalid')
            original = parse_profile(json.dumps(record['original']))
            if make_route(original, record['action']) != record['applied']:
                raise RouteError('ip_journal_invalid')
            return record
        except RouteError:
            raise
        except Exception as exc:
            raise RouteError('ip_journal_invalid') from exc

    def save(self, record):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        if self.path.is_symlink():
            raise RouteError('ip_journal_invalid')
        data = json.dumps(record, ensure_ascii=True, allow_nan=False) + '\n'
        if len(data.encode('ascii')) > MAX_JOURNAL:
            raise RouteError('ip_journal_invalid')
        fd, name = tempfile.mkstemp(prefix='.ayn-profile-', dir=self.path.parent)
        try:
            with os.fdopen(fd, 'w', encoding='ascii') as stream:
                stream.write(data); stream.flush(); os.fsync(stream.fileno())
            os.chmod(name, 0o600)
            os.replace(name, self.path)
        finally:
            Path(name).unlink(missing_ok=True)


def recover(state, bus=None) -> str:
    """Undo our temporary mapping, also after a crash. Never overwrite other AYN mappings."""
    journal = Journal(state)
    record = journal.read()
    if not record:
        return 'none'
    if record['boot'] != _boot_id():
        journal.save({}); return 'expired'
    api = bus or Bus()
    if api.current_owner() != record['owner']:
        journal.save({}); return 'expired'
    api.owner = record['owner']
    # Path may disappear on device removal; owner+path+persistent ID prevent reuse.
    if record['path'] not in api.composite_paths():
        journal.save({}); return 'expired'
    if api.prop(record['path'], 'PersistentId') != record['persistent_id']:
        journal.save({}); return 'expired'
    device = Device(api.owner, record['path'], record['persistent_id'], ())
    current = api.profile(device)
    restored = undo_route(current, record)
    if restored is None:
        journal.save({}); return 'external'
    # Fail rather than knowingly race a new profile selection.
    if api.profile(device) != current:
        raise RouteError('ip_profile_changed')
    restored_path = False
    if current == record['applied'] and restored == record['original']:
        restored_path = api.restore_path(device, record.get('original_path', ''), restored)
    if not restored_path:
        api.load(device, restored)
    api.wait_profile(device, restored, current)
    journal.save({})
    return 'restored'


class Lease:
    backend = 'inputplumber'
    def __init__(self, api, device, state):
        self.api, self.device, self.state = api, device, Path(state)
        self.queue = deque()
        self.subscriptions = []
        self.record = None
        self.closed = False

    @classmethod
    def open(cls, state, bus=None, source_check=gpio_source):
        api = bus or Bus()
        recover(state, api)
        device = api.discover(source_check)
        lease = cls(api, device, state)
        original = api.profile(device)
        can_route(original)
        action = 'thor_ergo_ayn_' + uuid.uuid4().hex
        applied = make_route(original, action)
        record = {'schema': 1, 'boot': _boot_id(), 'owner': device.owner,
                  'path': device.path, 'persistent_id': device.persistent_id,
                  'action': action, 'original': original, 'applied': applied,
                  'original_path': api.prop(device.path, 'ProfilePath')}
        lease.subscriptions = api.subscribe(device, action, lease.queue)
        try:
            api.check_owner()
            if api.profile(device) != original:
                raise RouteError('ip_profile_changed')
            Journal(state).save(record)  # Recovery is durable BEFORE modification.
            lease.record = record
            api.load(device, applied)
            api.wait_profile(device, applied, original)
            lease.queue.clear()  # Discard setup/clear-state events, not user clicks.
            return lease
        except Exception as error:
            # AccessDenied is returned before a mutating method runs. When the
            # original profile is still present there is nothing to roll back.
            if isinstance(error, RouteError) and error.code == 'ip_permission_denied':
                try:
                    if api.profile(device) == original:
                        Journal(state).save({})
                        lease.record = None
                except Exception:
                    pass
            # A timeout may mean that the daemon applied the route. Restore even
            # when its async property update has not caught up; retain a recovery
            # journal if that fails. Never overwrite an external new profile.
            try:
                lease.close()
            except Exception:
                pass
            raise

    def check(self):
        self.api.check_owner()
        if self.api.prop(self.device.path, 'PersistentId') != self.device.persistent_id:
            raise RouteError('ip_session_changed')
        if self.api.profile(self.device) != self.record['applied']:
            raise RouteError('ip_profile_changed')
        targets = tuple(sorted(set(self.api.prop(self.device.path, 'DbusDevices'))))
        if targets != self.device.targets:
            raise RouteError('ip_signal_target_changed')

    def poll(self, timeout=.1):
        self.api.iterate()
        if not self.queue:
            time.sleep(timeout)
            self.api.iterate()
        values = list(self.queue); self.queue.clear()
        if None in values:
            raise RouteError('ip_event_overflow')
        return values

    def close(self):
        if self.closed:
            return
        try:
            if self.record:
                recover(self.state, self.api)
        finally:
            self.api.unsubscribe(self.subscriptions)
            self.subscriptions = []
            self.queue.clear()
            self.closed = True


def probe(bus=None, source_check=gpio_source) -> dict:
    """Read-only metadata, also on failure; no profile contents or subscriptions.

    The previous probe lost all intermediate findings when discovery raised.
    Keep the advertised-capability flags and source-map evidence so that an
    unsuccessful physical-device test is actionable without another blind fix.
    """
    api = None
    try:
        api = bus or Bus()
        device = api.discover(source_check)
        profile = api.profile(device)
        can_route(profile)
        return {'available': True, 'backend': 'inputplumber',
                'composite': device.path, 'dedicated_gpio_source_verified': True,
                'quickaccess2_supported': True, 'dbus_target_count': len(device.targets),
                'mapping_evidence': device.mapping_evidence,
                'observations': getattr(api, 'discovery_observations', []),
                'profile_compatible': True,
                'profile_rule_count': len(profile['mapping']),
                'explicit_ayn_rule_count': sum(is_quick(m) for m in profile['mapping']),
                'profile_change_permission_tested': False,
                'physical_events_tested': False,
                'note': 'Read-only. Loading a route and physical button events are not tested.'}
    except RouteError as error:
        return {'available': False, 'backend': 'inputplumber', 'reason': error.code,
                'observations': getattr(api, 'discovery_observations', []),
                'profile_change_permission_tested': False,
                'physical_events_tested': False,
                'note': 'No mapping was loaded by this diagnostic.'}
