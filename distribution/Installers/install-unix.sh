#!/bin/sh
# macOS/Linux host installer, POSIX shell. Passwords belong to OpenSSH only.
# A prebuilt ZIP is sent verbatim: no host tar/zip, Finder metadata or temp files.
set -eu
umask 077
ssh_pid=
finish() {
    code=$?
    trap - 0 HUP INT TERM
    if [ -n "$ssh_pid" ]; then
        kill -TERM "$ssh_pid" 2>/dev/null || :
        wait "$ssh_pid" 2>/dev/null || :
    fi
    if [ "$code" -ne 0 ]; then
        printf '\nOperation did not complete (code %s). See the error above.\n' "$code" >&2
    fi
    case "$code" in 129|130|143) ;; *)
        if [ -t 0 ]; then printf 'Press Enter to close: '; IFS= read -r ignored || :; fi;;
    esac
    exit "$code"
}
trap finish 0
trap 'printf "\nConnection interrupted. No automatic retry.\n" >&2; exit 129' HUP
trap 'printf "\nCancelled. No automatic retry.\n" >&2; exit 130' INT
trap 'printf "\nCancelled. No automatic retry.\n" >&2; exit 143' TERM
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
read_field() {
    printf '%s' "$1"
    IFS= read -r answer || fail 'Input ended. Run this installer in an interactive terminal.'
}
SELF_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
ROOT=$(CDPATH= cd -- "$SELF_DIR/.." && pwd -P)
PAYLOAD="$ROOT/Payload/Thor.zip"
BOOTSTRAP="$ROOT/Thor/transfer.py"
MANIFEST="$ROOT/Installers/payload.sha256"
printf '\nThor Ergo 1.0 - independent AYN Thor / ArmadaOS distribution\n'
for tool in ssh awk base64 tr wc; do
    command -v "$tool" >/dev/null 2>&1 || fail "Missing standard host tool: $tool"
done
if command -v sha256sum >/dev/null 2>&1; then
    hash_file() { sha256sum < "$1" | awk '{print $1}'; }
elif command -v shasum >/dev/null 2>&1; then
    hash_file() { shasum -a 256 < "$1" | awk '{print $1}'; }
else fail 'A SHA-256 utility is required (sha256sum or shasum).'; fi
[ -r "$MANIFEST" ] && [ -r "$PAYLOAD" ] && [ -r "$BOOTSTRAP" ] && [ -r "$SELF_DIR/validate-ip.awk" ] ||
    fail 'The complete folder must be extracted first. Do not run this file inside the ZIP or move it alone.'
# Fixed, allowlisted release paths. These values are never sourced as shell code.
expected_hash() {
    awk -v path="$1" '$2==path {print $1; n++} END {if(n!=1) exit 1}' "$MANIFEST"
}
verify_file() {
    expected=$(expected_hash "$2") || fail "Invalid release checksum entry: $2"
    case "$expected" in ''|*[!0-9a-f]*) fail "Invalid SHA-256: $2";; esac
    [ "${#expected}" -eq 64 ] || fail "Invalid SHA-256 length: $2"
    actual=$(hash_file "$1") || fail "Cannot hash file: $2"
    [ "$actual" = "$expected" ] || fail "Package checksum mismatch: $2. Extract a fresh complete ZIP."
}
verify_file "$PAYLOAD" 'Payload/Thor.zip'
sha=$actual
verify_file "$BOOTSTRAP" 'Thor/transfer.py'
size=$(wc -c < "$PAYLOAD" | tr -d '[:space:]')
case "$size" in ''|*[!0-9]*) fail 'Invalid payload length.';; esac
[ "${#size}" -le 8 ] && [ "$size" -gt 0 ] && [ "$size" -le 67108864 ] || fail 'Payload exceeds transfer limit.'
printf 'Complete payload and SSH receiver: checksums OK.\n'
case "${1:-}" in
    --ayn-diagnostic)
        action=diagnostics
        printf 'AYN read-only diagnosis selected. No app or display changes.\n'
        ;;
    '')
        printf '1 Install / update   2 Undo   3 Diagnostics   4 Wake lower screen\n'
        read_field 'Operation [1]: '
        case "$answer" in ''|1) action=install;;2) action=undo;;3) action=diagnostics;;4) action=wake;;*) fail 'Invalid operation.';;esac
        ;;
    *) fail 'Unsupported launcher argument.' ;;
esac
read_field 'Thor local IP address: '
address=$answer
printf '%s\n' "$address" | LC_ALL=C awk -f "$SELF_DIR/validate-ip.awk" || fail 'Enter a valid numeric IPv4 or IPv6 address, not a hostname.'
case "$address" in \[*\]) address=${address#\[};address=${address%\]};; esac
read_field 'SSH user [armada]: '
user=${answer:-armada}
case "$user" in [-.]*|*[!a-zA-Z0-9_.-]*) fail 'Invalid SSH username.';; esac
read_field 'SSH port [22]: '
port=${answer:-22}
case "$port" in ''|*[!0-9]*) fail 'Invalid SSH port.';; esac
[ "${#port}" -le 5 ] || fail 'SSH port must be between 1 and 65535.'
port=$(printf '%s\n' "$port" | awk '{printf "%d",$0+0}')
[ "$port" -ge 1 ] && [ "$port" -le 65535 ] || fail 'SSH port must be between 1 and 65535.'
boot=$(base64 < "$BOOTSTRAP" | tr -d '\r\n')
case "$boot" in ''|*[!a-zA-Z0-9+/=]*) fail 'Could not encode the receiver.';; esac
remote="python3 -B -c 'exec(__import__(\"base64\").b64decode(\"$boot\"))' zip $sha $size $action"
printf '\nKeep the Thor awake, with games closed.\n'
printf 'TOP screen: Steam Game Mode started and unlocked.\n'
printf 'BOTTOM screen: Plasma Mobile must be enabled in Armada Control on the top screen.\n'
printf 'Do not switch the whole Thor to Desktop Mode. Diagnostics does not change the app.\n'
printf 'SSH password (default: "armada"): enter it in the OpenSSH prompt.\n'
printf 'The password is not saved. Verify any new SSH host fingerprint.\n'
printf 'Changed host keys are NOT automatically accepted or removed.\n\n'
# No remote pseudo-terminal: a terminal would corrupt binary ZIP bytes.
# OpenSSH still reads authentication from the LOCAL controlling terminal.
# Background + wait lets the shell handle signals and stop this exact SSH child.
ssh -T -o StrictHostKeyChecking=ask -o BatchMode=no -o ConnectTimeout=12 \
    -o ServerAliveInterval=15 -o ServerAliveCountMax=3 \
    -o ForwardAgent=no -o ForwardX11=no -o ClearAllForwardings=yes \
    -p "$port" "$user@$address" "$remote" < "$PAYLOAD" &
ssh_pid=$!
result=0
wait "$ssh_pid" || result=$?
ssh_pid=
if [ "$result" -eq 0 ]; then printf '\nOperation completed.\n'; fi
exit "$result"
