"""Read-only evidence for the InputPlumber 0.83 per-source capability gap.

That release's evdev drivers translate events using their V2 capability map,
but get_capabilities() still reports raw evdev capabilities. Never infer that
raw F24 should be remapped directly: verify the installed Thor source and its
V2 F24 -> QuickAccess2 mapping instead. This module never writes system files.
On-disk evidence does not prove that a physical event has been received.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import stat

from . import _vendor_yaml as yaml

MAX_FILE = 512 * 1024
MAX_FILES = 512
MAX_TOTAL = 8 * 1024 * 1024
QUICK_SOURCE = {'gamepad': {'button': 'QuickAccess2'}}
F24_EVENT = {'evdev': {'event_type': 'KEY', 'event_code': 'KEY_F24', 'value_type': 'button'}}
GPIO_NAMES = {'gpio-keys-ayn', 'AYN Key'}


class MappingError(RuntimeError):
    def __init__(self, code='ip_config_unverified', details=None):
        self.code = code
        self.details = dict(details or {})
        super().__init__(code)


class ConfigLoader(yaml.SafeLoader):
    def __init__(self, stream):
        self.depth = 0
        super().__init__(stream)

    def compose_node(self, parent, index):
        if self.check_event(yaml.AliasEvent):
            raise MappingError()
        self.depth += 1
        try:
            if self.depth > 32:
                raise MappingError()
            return super().compose_node(parent, index)
        finally:
            self.depth -= 1

    def construct_mapping(self, node, deep=False):
        out = {}
        for key_node, value_node in node.value:
            key = self.construct_object(key_node, deep=deep)
            if not isinstance(key, str) or key in out:
                raise MappingError()
            out[key] = self.construct_object(value_node, deep=deep)
        return out


def parse_config(text):
    try:
        if not isinstance(text, str) or len(text.encode('utf-8')) > MAX_FILE:
            raise MappingError()
        data = yaml.load(text, Loader=ConfigLoader)
        if not isinstance(data, dict):
            raise MappingError()
        json.dumps(data, allow_nan=False)  # Reject dates, recursive/non-JSON values.
        return data
    except MappingError:
        raise
    except Exception as error:
        raise MappingError() from error


def _trusted(info, directory=False, owner=0):
    kind = stat.S_ISDIR if directory else stat.S_ISREG
    return (kind(info.st_mode) and info.st_uid == owner
            and not (stat.S_IMODE(info.st_mode) & 0o022))


def read_configs(directories, owner=0):
    """Bounded root-owned YAML only; ordering matches InputPlumber's file sort.

    ``owner`` and directory overrides are for isolated temporary-file tests.
    Production calls use root ownership and fixed system locations only.
    """
    paths = []
    total = 0
    current_file = ''
    try:
        for priority, directory in enumerate(map(Path, directories)):
            if not directory.exists():
                if directory.is_symlink():
                    raise MappingError()
                continue
            if not _trusted(directory.lstat(), directory=True, owner=owner):
                raise MappingError()
            # Immediate parent must not redirect config resolution to user data.
            if not _trusted(directory.parent.lstat(), directory=True, owner=owner):
                raise MappingError()
            for path in directory.iterdir():
                if path.suffix != '.yaml':
                    continue
                paths.append((path.name, priority, path))
                if len(paths) > MAX_FILES:
                    raise MappingError()
        result = []
        for _, _, path in sorted(paths):
            current_file = path.name
            fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC | os.O_NONBLOCK)
            try:
                info = os.fstat(fd)
                if not _trusted(info, owner=owner) or info.st_size > MAX_FILE:
                    raise MappingError()
                with os.fdopen(fd, 'rb', closefd=False) as stream:
                    content = stream.read(MAX_FILE + 1)
                total += len(content)
                if len(content) > MAX_FILE or total > MAX_TOTAL:
                    raise MappingError()
                data = parse_config(content.decode('utf-8'))
                result.append((path.name, data, hashlib.sha256(content).hexdigest()))
            finally:
                os.close(fd)
        return result
    except MappingError as error:
        error.details.setdefault('stage', 'read_system_yaml')
        if current_file:
            error.details.setdefault('file', current_file[:160])
        raise
    except (OSError, UnicodeError, ValueError) as error:
        raise MappingError(details={'stage': 'read_system_yaml', 'file': current_file[:160],
                                    'error_type': type(error).__name__}) from error


def source_map_id(config, model):
    """Accept only the exact, unfiltered single-F24 Thor source configuration."""
    if (type(config.get('version')) is not int or config['version'] != 1
            or config.get('kind') != 'CompositeDevice' or config.get('name') != model
            or config.get('capability_map_id') is not None):
        raise MappingError()
    sources = config.get('source_devices')
    if not isinstance(sources, list) or len(sources) > 128:
        raise MappingError()
    candidates = []
    for source in sources:
        if not isinstance(source, dict):
            raise MappingError()
        evdev = source.get('evdev')
        if not isinstance(evdev, dict) or evdev.get('name') not in GPIO_NAMES:
            continue
        if (any(source.get(k) not in (None, False) for k in ('blocked', 'ignore', 'passthrough'))
                or source.get('events') is not None or source.get('udev') is not None):
            raise MappingError('ip_config_conflict')
        identifier = source.get('capability_map_id')
        if not isinstance(identifier, str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,80}', identifier):
            raise MappingError()
        candidates.append(identifier)
    if len(candidates) != 1:
        raise MappingError('ip_config_conflict')
    return candidates[0]


def proves_f24_to_quick(config, identifier):
    if (type(config.get('version')) is not int or config['version'] != 2
            or config.get('kind') != 'CapabilityMap' or config.get('id') != identifier):
        raise MappingError()
    mappings = config.get('mapping')
    if not isinstance(mappings, list) or len(mappings) > 512:
        raise MappingError()
    found = []
    for mapping in mappings:
        if not isinstance(mapping, dict) or not isinstance(mapping.get('source_events'), list):
            raise MappingError()
        sources = mapping['source_events']
        # Also detect an alternative/chord mapping of the same physical button.
        has_f24 = any(isinstance(s, dict) and isinstance(s.get('evdev'), dict)
                      and s['evdev'].get('event_code') == 'KEY_F24' for s in sources)
        target = mapping.get('target_event')
        if not has_f24 and target != QUICK_SOURCE:
            continue
        if (sources != [F24_EVENT] or target != QUICK_SOURCE
                or mapping.get('mapping_type') not in (None, {'evdev': 'multi_source'})):
            raise MappingError('ip_config_conflict')
        found.append(mapping)
    if len(found) != 1:
        raise MappingError('ip_config_conflict')
    return True


def installed_ayn_map(model, device_dirs=None, map_dirs=None, owner=0):
    """Prove the source translation without relying on D-Bus Capabilities.

    Uses system defaults only; custom source filters/maps are not overwritten.
    Conflicting same-name device configs are refused, rather than guessed.
    """
    if model not in ('AYN Thor', 'AYN Thor Lite'):
        raise MappingError()
    base = next((p for p in (Path('/usr/local/share/inputplumber'),
                            Path('/usr/share/inputplumber')) if p.is_dir()),
                Path('/usr/share/inputplumber'))
    if device_dirs is None:
        device_dirs = (Path('/etc/inputplumber/devices.d'), base / 'devices')
    if map_dirs is None:
        map_dirs = (base / 'capability_maps', Path('/etc/inputplumber/capability_maps.d'))
    configs = read_configs(device_dirs, owner)
    matching = [(name, data, sha) for name, data, sha in configs if data.get('name') == model]
    if not matching:
        raise MappingError(details={'stage': 'source_config', 'reason': 'no_matching_model'})
    identifiers = {source_map_id(data, model) for _, data, _ in matching}
    if len(identifiers) != 1:
        raise MappingError('ip_config_conflict')
    identifier = identifiers.pop()
    maps = [entry for entry in read_configs(map_dirs, owner) if entry[1].get('id') == identifier]
    if not maps:
        raise MappingError(details={'stage': 'capability_map', 'reason': 'id_not_found',
                                    'map_id': identifier})
    # The daemon's loader inserts in filename/dir order: later same-ID map wins.
    map_name, config, digest = maps[-1]
    try:
        proves_f24_to_quick(config, identifier)
    except MappingError as error:
        error.details.update(stage='capability_map', file=map_name[:160], map_id=identifier)
        raise
    return {'kind': 'installed_source_map', 'map_id': identifier,
            'map_file': map_name, 'map_sha256': digest,
            'physical_event': 'KEY_F24', 'translated_event': 'Gamepad:Button:QuickAccess2',
            'runtime_events_tested': False}
