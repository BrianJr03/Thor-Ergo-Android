# Upstream provenance

Thor KBM is a native Android fork of **Thor Ergo**, the ArmadaOS version, which
is an independent fork of **Armada Dual Screen Input** by
**sa1utyeggs and contributors**, based on version **0.1.7**.

- Upstream: https://github.com/sa1utyeggs/armada-dual-screen-input
- Source baseline: https://github.com/sa1utyeggs/armada-dual-screen-input/tree/v0.1.7
- Thor Ergo (ArmadaOS version): https://github.com/seekingalphacrawler/Thor-Ergo
- Thor KBM (this repository): https://github.com/BrianJr03/Thor-Ergo-Android

The original Git ancestry is retained. Thor Ergo 1.0, the ArmadaOS/Linux edition,
lives in the history up to the commit "Publish Thor Ergo 1.0".

Thor KBM is a reimplementation for Android in Kotlin. Behaviour was ported
from the Thor Ergo 1.0 sources. The keyboard geometry, controller, output routing, dead
keys and Pinyin come from Thor Ergo and Armada Dual Screen Input. The trackpad
gesture engine comes from Ratatoskr. No Python code is shipped.

Original copyright and third-party notices are retained; see CREDITS.md,
THIRD_PARTY.md and `licenses/`. The redesigned interface, national layouts and
the Android port do not transfer authorship of upstream work to the layout
designer.

**Graphic design and keyboard layout: seekingalphacrawler.**
