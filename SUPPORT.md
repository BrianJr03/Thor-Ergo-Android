# Support and testing scope

## What has been verified

- The pure logic modules have ported versions of the ArmadaOS version's tests:
  - keyboard layout and reducer;
  - output routing;
  - dead keys;
  - Pinyin;
  - trackpad;
  - settings.
- The ViewModels are tested against fakes.
- Screenshot tests render every layout at 1240 × 1080.
- Lint is clean apart from library-update notices.
- The debug APK builds.

## What has not

- Nothing has been run on an AYN Thor yet. These points need a real device:
  - That touching the non-focusable lower-screen overlay leaves input focus
    with the game on the upper screen.
  - That Shizuku-injected mouse and key events reach specific games. If a game
    ignores the mouse, try **Clicks as screen touches**.
  - That the lower panel is a separate physical display, so it can be powered
    off on its own.
  - Whether the AYN key (or another button) reaches Android as a key the
    Accessibility service can bind.

Please report device results, including Android build, Shizuku version and the
game, as GitHub issues.

## Maintenance

This is a personal project shared as-is, with no support commitment or update
schedule. Community forks are welcome.

## Attribution

**Graphic design and keyboard layout: seekingalphacrawler.** This is not an
official AYN, ArmadaOS, Armada Dual Screen Input or Ratatoskr release. Please do
not send support requests for this edition to the upstream developers.
