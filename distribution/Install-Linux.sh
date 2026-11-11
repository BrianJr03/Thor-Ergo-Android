#!/bin/sh
set -eu
DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
exec /bin/sh "$DIR/Installers/install-unix.sh"
