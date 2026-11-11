# AYN button and InputPlumber

The optional feature toggles only the lower panel, while the lower Plasma
session and the keyboard helper remain running. It is Off by default after
installation. Enable it with games closed and all keys/buttons released.

## Discovery and routing

The helper verifies the dedicated AYN physical source using sysfs metadata.
InputPlumber can announce either QuickAccess2 or the underlying physical F24.
When only F24 is advertised, the helper checks the installed capability map
before accepting that it translates to QuickAccess2. Missing, conflicting,
filtered, unsafe or ambiguous source/map data are rejected.

The current profile is saved privately. Only QuickAccess2 is replaced with a
private D-Bus event, leaving unrelated mappings intact. Profiles containing
`target_devices` are rejected instead of risking controller recreation.
The helper does not call SetTargetDevices, SetInterceptMode or SendEvent.
It never reads the permission-blocked raw source when InputPlumber owns it.

## Screen control

A valid press uses the compositor's `GAMESCOPE_COMPANION_LOCKED` property.
It does not set brightness, draw a black overlay, stop Plasma or suspend the
whole device. The helper is independent of the keyboard's window visibility.

## Recovery and limitations

Turning the option off, stopping the helper or installing the application
attempts to restore the previous mapping and wake a lock created by this app.
A journal in `~/.local/state/thor-ergo/ayn-inputplumber.json` permits recovery
following interruption. It contains a controller-profile snapshot and must
not be shared without review. It is not part of the public distribution.

InputPlumber clears input states when loading profiles. Disable the option
before changing controller profiles or assigning AYN elsewhere. An external
profile change stops the route instead of being repeatedly overwritten.
The compositor cannot identify all simultaneous writers of its lock property;
avoid another application controlling the same lower-panel lock.

**Ready** means the route is installed, not that every future physical press
or system update is guaranteed. Use Diagnostics for per-cause messages, and
Wake lower screen for a lock this application owns. No sudo, chmod, udev rule
or additional Plasma shortcut is required.

## Source references

- ArmadaOS: https://github.com/armada-os/armada
- InputPlumber: https://github.com/ShadowBlip/InputPlumber
- Bundled SafeLoader: PyYAML 6.0.3, with its original MIT license retained.

The tests use synthetic profiles and simulated D-Bus replies; no personal
controller-profile or diagnostic dump is included in the source distribution.
