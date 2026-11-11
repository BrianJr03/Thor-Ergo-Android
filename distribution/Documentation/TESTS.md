# Verification and test scope

## Reported physical test

The maintainer reports successful installation and use on **Windows 11 → AYN
Thor Pro, 12 GB RAM, ArmadaOS 20261109.dfed5d6**. Steam Game Mode runs on the
upper display and Plasma Mobile on the lower display. This includes the
keyboard, trackpad and optional AYN panel toggle.

The macOS and Linux host installers are included but have not been tested by
the maintainer on macOS/Linux computers. Other device models, OS builds,
all language/game combinations, repeated suspend/resume cycles and OS upgrades
have not been comprehensively validated. Translations have not received a full
native-speaker review.

## Automated checks

`python3 -B Thor/selftest.py` exercises keyboard geometry, hit testing, text
composition, cases, language catalogs, Pinyin, simulated input/output, service
launch arguments, installation/undo and bounded InputPlumber profile handling.
No input device is opened and no real controller profile or screen is changed.

`python3 -B Documentation/verify_host_transfer.py` executes host shell scripts
using fake SSH on Linux. It checks byte transfer, archive extraction, hashing,
quoted paths, invalid inputs, cancellation and returned status. It does not
test macOS utilities, Gatekeeper or a real remote device.

`python3 -B Documentation/verify_windows_source.py` is static source checking,
not execution in Windows PowerShell. The Windows installer additionally runs
its own binary-transfer preflight on the installation computer before SSH.

The installer runs native imports and offscreen drawing **on the Thor** before
stopping another panel, then requires a live first-frame receipt. These checks
are not a substitute for physical use in each application.

## Reproduce

The local isolated suite requires Python 3 and Pycairo; the app's native runtime
also needs the normal ArmadaOS GTK/PyGObject/Pango environment. The packaged
native library is aarch64. Do not try to run the Thor GUI on an x86 host.

Publication-only edits change version text, attribution and documentation, not
the keyboard geometry, touch processing, profile routing or input algorithms.
