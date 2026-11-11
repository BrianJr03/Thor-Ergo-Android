# Thor Ergo 1.0

Multilingual touchscreen keyboard and trackpad for the lower screen of an
**AYN Thor running ArmadaOS**. An independent fork of
[Armada Dual Screen Input](https://github.com/sa1utyeggs/armada-dual-screen-input).

## Download

Choose **Thor-Ergo-1.0.zip** in Assets and extract the entire folder. This is the
complete installation package; no prior app or patch is required. SHA256SUMS.txt
contains the download checksum. GitHub's Source code archives are repository
snapshots, not the named installation package.

## Features

Large touch keys and a fixed touchpad; separate numbers, symbols and Fn pages;
Shift/Caps-aware labels and accent popups; seven national layouts and translated
menus; basic offline Chinese Pinyin; optional AYN-button lower-panel toggle;
SSH installers, diagnostics and reversible user-level installation.

## Tested setup

- Installation from **Windows 11**.
- **AYN Thor Pro with 12 GB RAM**.
- **ArmadaOS 20261109.dfed5d6** (20261109 release).
- Steam Game Mode on the upper screen and **Plasma Mobile enabled on the lower
  screen through Armada Control**.

The macOS/Linux host installers are included but **have not been tested by the
maintainer on those operating systems**. No compatibility promise is made for
other devices, future OS versions or every language/game combination.

## Installation

Save your work, close games and keep the Thor awake on the same network with SSH
active. Do not switch the whole device to Desktop Mode. On Windows, open
Install-Windows.cmd without administrator privileges, choose Install / update,
and enter the Thor's local IP, SSH user and port. OpenSSH asks for the current
password (Armada default: "armada"); the installer does not store it.

The README explains macOS/Linux and direct-on-Thor installation, the AYN option,
Undo and Diagnostics. [View the menus](https://github.com/seekingalphacrawler/Thor-Ergo/blob/main/docs/INTERFACE.md).

## Credits and maintenance

**Graphic design and keyboard layout: seekingalphacrawler.** Original authors,
licenses and notices are preserved, including sa1utyeggs / Armada Dual Screen
Input, Ven / Ratatoskr and their contributors. See CREDITS.md and the in-app
About & credits menu. This is not an official release of those projects.

A completed personal project shared as-is, with **no active maintenance or support
commitment**. Community forks are welcome.
