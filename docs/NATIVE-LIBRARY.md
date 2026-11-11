# Replaceable native library and corresponding source

Thor Ergo loads gtk-layer-shell 0.10.0 dynamically, using the unmodified aarch64
library and typelib delivered in Armada Dual Screen Input 0.1.7. Its LGPL-3.0-or-
later, GPL-3.0 and MIT notices are retained beside the library. The corresponding
package source is identified beside the object code in
`Thor/app/packaging/dependencies/gtk-layer-shell-aarch64.json`:

https://dl.fedoraproject.org/pub/fedora/linux/releases/44/Everything/source/tree/Packages/g/gtk-layer-shell-0.10.0-1.fc44.src.rpm

The upstream 0.10.0 source release is also available at:

https://github.com/wmww/gtk-layer-shell/archive/refs/tags/v0.10.0.tar.gz

The new Python application and installer sources are all included in this ZIP.
The C source of this third-party shared library is provided by reference to the
source locations above, not included in the ZIP. No claim is made that these
remote archives were downloaded or rebuilt during this release's tests.

The application imposes no signature check or restriction against replacing
this library with an ABI-compatible modified version. To use a modified build,
replace the two `libgtk-layer-shell.so.0*` files and matching typelib under
`Thor/app/runtime/usr/`. Keep the two library copies consistent. Recreate
`Thor/SHA256SUMS` for all payload files (except that manifest itself) before
installing the modified source distribution. The manifest detects accidental
corruption, not a prohibition on modifying this software. Keep all applicable
third-party notices when distributing your own modified build.

The native prerequisite checker only checks the same public ABI needed by the
upstream application. It does not compare a running modified library with an
approved binary. System GTK/Pango/Cairo and fonts remain supplied by ArmadaOS.
