# Architecture

The complete runtime lives in `Thor/app/`. The independent user-level installer
is `Thor/install.py`. Host launchers transfer the prebuilt `Payload/Thor.zip`
over OpenSSH; no package download or root filesystem change is required.

## Application

`ergo_layout.py` defines geometry shared by drawing and touch hit testing.
`ergo_controller.py` handles contact ownership, modifiers, repeats, page selection
and accent popups. `ergo_render.py` draws using the upstream Pango/Noto text path.
`ergo_profiles.py`, catalogs in `locales/`, and the Pinyin data implement the
national layouts and language choices. Keyboard/input adapters retain upstream
Gamescope and Ratatoskr integration.

The app receives the lower Plasma session's available allocation; it does not
simulate a Plasma status bar or change the compositor's window geometry.

## Installation

Each install writes a separate complete release inside the user's persistent
home. Hash manifests reject missing, changed, extra and symbolic-link payload
files. Tests run before stopping an existing panel. The recorded service command
includes `tools/run.py --run`. Activation requires a first-frame receipt.
Transactions snapshot managed files, settings and user service state for Undo.

Host passwords belong to OpenSSH's prompt and are not stored. Runtime receipts
and private recovery state stay on the device and are not redistributed.

## AYN helper

The optional user service discovers the dedicated AYN source, validates its
mapping to QuickAccess2, and uses InputPlumber's D-Bus profile API to route only
that event. It temporarily owns a narrowly scoped mapping and maintains a
private recovery journal. It does not bypass device permissions, capture an
entire controller, change intercept mode or create target controllers.

The panel toggles through `GAMESCOPE_COMPANION_LOCKED` while Plasma stays alive.
Disabling/stopping the helper attempts restoration of its own mapping and lock.
Conflicting external profile/lock controllers are not supported; externally
changed profiles are respected rather than repeatedly overwritten.

See AYN-INPUTPLUMBER.md and the test scope for recovery limits.
