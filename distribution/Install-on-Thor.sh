#!/bin/sh
# Run on the Thor itself, not on a macOS/Linux host. SSH sessions are supported.
set -eu
DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
case "${1:-install}" in install|check)
    printf '%s\n' 'TOP: Steam Game Mode started and unlocked. BOTTOM: Plasma Mobile enabled via Armada Control.'
    printf '%s\n' 'Do not switch the whole Thor to Desktop Mode.'
;; esac
exec python3 -B "$DIR/Thor/install.py" "${1:-install}"
