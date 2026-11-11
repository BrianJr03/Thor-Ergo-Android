# Third-party components in Thor Ergo 1.0

The upstream notices are preserved verbatim in `THIRD_PARTY.upstream.md`.
This independent distribution modifies their runtime integration as follows.

* **Armada Dual Screen Input 0.1.7**: sa1utyeggs and contributors, MIT.
  All application source used by this build is included in `src/`.
  Original license: `LICENSE`. Version and source: https://github.com/sa1utyeggs/armada-dual-screen-input/tree/v0.1.7
* **Ratatoskr**: Ven (venatrix-ritz) and Thor Input Contributors, MIT.
  The exact upstream snapshot, its license, credits and local-change record are
  in `vendor/ratatoskr/`. Earlier inspiration, including Project Barry and the
  Decky community, remains attributed there. This is not a Decky plugin.
* **gtk-layer-shell 0.10.0-1.fc44 aarch64**: the private, replaceable shared
  library and GI typelib from the official supplied release are included,
  unmodified, in `runtime/`. Original LGPL/MIT notices are in
  `runtime/usr/share/licenses/gtk-layer-shell/`; matching package provenance
  and SHA-256 are in `packaging/dependencies/gtk-layer-shell-aarch64.json`.
  Project source: https://github.com/wmww/gtk-layer-shell
  Corresponding source RPM (same package/version identified by upstream): https://dl.fedoraproject.org/pub/fedora/linux/releases/44/Everything/source/tree/Packages/g/gtk-layer-shell-0.10.0-1.fc44.src.rpm
  Source release archive: https://github.com/wmww/gtk-layer-shell/archive/refs/tags/v0.10.0.tar.gz
  The native C dependency sources are referenced here; they are not claimed to
  be included as C source in this archive. The included Python application
  source and its independent build/test scripts are complete.
* **xpinyin 3.1 / Unihan 14.0.0**: Qing Lee and the Unicode data contributors.
  The unmodified source data used for the reverse index and the LPPL notice
  are in `licenses/xpinyin/`. The renamed index is an explicitly modified
  derivative, distributed under LPPL-1.3c-or-later. `data/pinyin-provenance.json`
  identifies its origin and modifications. `tools/build_pinyin_index.py`
  reproduces it from included data without a network connection.
  Original full xpinyin sources: https://github.com/CTeX-org/ctex-kit/tree/master/xpinyin
  Package history: https://ctan.org/pkg/xpinyin
  Additional Unicode permission notice: `licenses/xpinyin/Unicode-License.txt`.
* The small curated phrase and candidate-priority lists were entered for this
  edition; they are not a commercial dictionary, measured frequency corpus,
  user vocabulary, or text collected from a device.
* This edition uses a small, pure-Python Pinyin composer. It **does not load or
  require libpinyin**, despite the optional libpinyin support described in the
  unchanged upstream documentation. No libpinyin binary or dictionary is shipped.
* GTK, PyGObject, Pango, Cairo, libei/Wayland and system fonts are prerequisites
  provided by ArmadaOS, not installers for those system components. **No fonts
  are included or redistributed.** No driver experiment is included.
* Armada/AYN/Valve marks and names retain their ownership. Retained upstream
  assets preserve their notices. The visible keyboard controls are drawn in
  code, and this release is not endorsed by any upstream project or vendor.

**Graphic design and keyboard layout: seekingalphacrawler.** This credit does not reattribute
other authors' source code, data, libraries, assets or original theme work.

* **PyYAML 6.0.3**: Ingy döt Net, Kirill Simonov and contributors, MIT.
  The unmodified pure-Python implementation (no C extension) is included as
  `src/armada_dual_screen_input/_vendor_yaml/`. The application uses its SafeLoader
  with additional alias/depth/duplicate-key bounds for InputPlumber profiles.
  Original notice and packaging details: `licenses/pyyaml/`.
  Project/source: https://github.com/yaml/pyyaml/tree/6.0.3
* **InputPlumber**: ShadowBlip and contributors. This application integrates via
  its existing system D-Bus API; no InputPlumber daemon or Rust source is bundled
  or represented as authored by this distribution. Pinned API references are in
  `Documentation/AYN-INPUTPLUMBER.md` at the distribution root.
