# Third-party components in ThorKBM

* **Armada Dual Screen Input 0.1.7**: sa1utyeggs and contributors, MIT.
  Behaviour reimplemented in Kotlin. Its copyright is in `LICENSE`.
  The upstream third-party record is kept verbatim in
  `licenses/THIRD_PARTY.upstream.md`.
* **Ratatoskr**: Ven (venatrix-ritz) and Thor Input Contributors, MIT. The
  trackpad engine is ported to `core/trackpad`. The license and credits are in
  `licenses/ratatoskr/`.
* **xpinyin 3.1 / Unihan 14.0.0**: Qing Lee and the Unicode data contributors.
  * `core/pinyin/src/main/resources/pinyin/pinyin-index.json` is an explicitly
    modified derivative, distributed under LPPL-1.3c-or-later.
  * The unmodified source data and the LPPL notice are in `licenses/xpinyin/`.
  * `tools/pinyin/build_pinyin_index.py` reproduces the index byte-for-byte
    from them, offline.
  * `pinyin-provenance.json` records its origin and modifications.
  * Original sources: https://ctan.org/pkg/xpinyin
  * Unicode permission notice: `licenses/xpinyin/Unicode-License.txt`.
* The curated phrase list and candidate preferences were hand-entered for Thor
  Ergo. They are not a commercial dictionary or collected text.
* **Shizuku API** (RikkaApps) and **HiddenApiBypass** (LSPosed): Apache-2.0,
  resolved from Maven Central.
* **AndroidX, Jetpack Compose, Dagger/Hilt, kotlinx.coroutines and
  kotlinx.serialization**: Apache-2.0, resolved from Google Maven and Maven
  Central.

No fonts are bundled. The app uses the system font.
