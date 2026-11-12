# Thor KBM

The first release of Thor KBM: a native Android fork of Thor Ergo, the ArmadaOS
version, for the AYN Thor's dual screens. The ArmadaOS version remains in the
Git history and on its own release page.

## What carries over from the ArmadaOS version

- The same keyboard geometry (1240 × 1080 logical layout).
- The same seven national layouts and accent popups.
- Shift and Caps Lock latching, overlapping-thumb typing order, and key repeat.
- Dead keys and the pt_PT AltGr table.
- Offline Pinyin with the same dictionary.
- The trackpad engine: slop, acceleration, taps, scroll, glide and four-finger
  lockout.
- Every setting, with its range and default.

## What is new or different

- Input goes through **Shizuku** (full fidelity) or the **Accessibility
  service** (text into focused fields), instead of Gamescope and uinput.
- A glove pointer on the upper screen.
- **Lower screen off** replaces the InputPlumber AYN-button helper. It uses a
  real panel power-off through Shizuku and falls back to a black overlay
  without it. You can bind any hardware key the Accessibility service can see.
- New settings:
  - vibrate on key press;
  - clicks as screen touches, for games that ignore the mouse.
- No SSH installers or host scripts: install the APK.

## Known limitations

- Not yet tested on an AYN Thor. See SUPPORT.md.
- Without Shizuku, keys and taps only reach focused text fields, and there is
  no pointer movement or scrolling.
- Whether the physical AYN key reaches Android as a bindable key depends on the
  firmware.
