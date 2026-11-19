#!/usr/bin/env bash
# Checks ThorKBM as a Command Panel plugin on a device, without Command Panel.
#
# Needs a DEBUG build (jr.brian.thorkbm.debug), set up (overlay permission granted). Its
# PluginDebugReceiver stands in for Command Panel and logs the replies it gets under ThorKBM-Plugin.
# The caller check is skipped on this path; the unit tests cover it.
#
#   tools/plugin/check-plugin.sh [path/to/app-debug.apk]
#   DISPLAY_ID=4 SERIAL=<adb serial> tools/plugin/check-plugin.sh
#
# Covers: the plugin service resolves; MSG_SHOW puts the panel on DISPLAY_ID and answers MSG_SHOWN
# within 3 s; focus stays on display 0; an unknown version gets MSG_CLOSED(error); MSG_HIDE removes
# the panel with no reply; the Hide key sends MSG_CLOSED(by user) and the panel goes; opening the
# app during a session sends MSG_CLOSED(opened elsewhere) and the panel stays.
set -uo pipefail

PKG=jr.brian.thorkbm.debug
RECEIVER=$PKG/jr.brian.thorkbm.feature.panel.plugin.PluginDebugReceiver
DISPLAY_ID=${DISPLAY_ID:-4}
ADB=(adb ${SERIAL:+-s "$SERIAL"})
failures=0

pass() { echo "PASS  $*"; }
fail() { echo "FAIL  $*"; failures=$((failures + 1)); }

fake_cp() { "${ADB[@]}" shell am broadcast -n "$RECEIVER" "$@" >/dev/null; }
plugin_log() { "${ADB[@]}" logcat -d -s ThorKBM-Plugin:I; }

# Waits up to $2 seconds for a line matching $1 in the plugin log.
wait_log() {
    local deadline=$((SECONDS + $2))
    while ((SECONDS < deadline)); do
        plugin_log | grep -q "$1" && return 0
        sleep 0.2
    done
    return 1
}

# Display id of the ThorKbmPanel window, empty if there is none.
panel_display() {
    "${ADB[@]}" shell dumpsys window windows | awk '
        /Window\{[^}]* ThorKbmPanel\}:/ { inside = 1; next }
        inside && /Window #/ { exit }
        inside && match($0, /mDisplayId=[0-9]+/) { print substr($0, RSTART + 11, RLENGTH - 11); exit }'
}

focused_display() {
    "${ADB[@]}" shell dumpsys window | grep -m1 -o 'mTopFocusedDisplayId=[0-9-]*' | cut -d= -f2
}

check_panel_on_display() {
    local shown
    shown=$(panel_display)
    [[ $shown == "$DISPLAY_ID" ]] && pass "panel window on display $DISPLAY_ID" || fail "panel window on display '${shown:-none}', expected $DISPLAY_ID"
    local focus
    focus=$(focused_display)
    [[ $focus == 0 ]] && pass "focus stays on display 0" || fail "focused display is '$focus'"
}

start_session() {
    "${ADB[@]}" logcat -c
    fake_cp -a jr.brian.thorkbm.debug.PLUGIN_SHOW --ei display "$DISPLAY_ID"
    if wait_log "MSG_SHOWN" 3; then pass "MSG_SHOWN within 3 s"; else fail "no MSG_SHOWN within 3 s"; plugin_log | tail -5; fi
}

"${ADB[@]}" get-state >/dev/null 2>&1 || { echo "No device"; exit 2; }
if [[ $# -ge 1 ]]; then "${ADB[@]}" install -r "$1" >/dev/null && pass "installed $1" || { fail "install $1"; exit 1; }; fi

echo "== discovery"
services=$("${ADB[@]}" shell cmd package query-services -a jr.brian.commandpanel.plugin.action.PANEL_PLUGIN --components)
grep -q "$PKG/jr.brian.thorkbm.feature.panel.plugin.PanelPluginService" <<<"$services" \
    && pass "plugin service resolves" || { fail "plugin service not found: $services"; }

echo "== show, then hide"
start_session
check_panel_on_display
"${ADB[@]}" logcat -c
fake_cp -a jr.brian.thorkbm.debug.PLUGIN_HIDE
sleep 1
[[ -z $(panel_display) ]] && pass "MSG_HIDE removes the panel" || fail "panel still up after MSG_HIDE"
plugin_log | grep -q "MSG_CLOSED" && fail "MSG_HIDE got a reply" || pass "no reply to MSG_HIDE"

echo "== unknown version"
"${ADB[@]}" logcat -c
fake_cp -a jr.brian.thorkbm.debug.PLUGIN_SHOW --ei display "$DISPLAY_ID" --ei version 99
wait_log "MSG_CLOSED reason=2" 2 && pass "version 99 closes with an error" || fail "no MSG_CLOSED(error) for version 99"

echo "== Hide key"
start_session
# The Hide key sits at the top right of the 1240 x 1080 layout, around (1119, 61).
# Logical size (after rotation); `wm size` gives the panel's physical, portrait size.
frame=$("${ADB[@]}" shell dumpsys display | grep -o "displayId=$DISPLAY_ID, [^}]*logicalFrame=Rect([^)]*)" | head -1)
w=$(sed -E 's/.*logicalFrame=Rect\(0, 0 - ([0-9]+), ([0-9]+)\).*/\1/' <<<"$frame")
h=$(sed -E 's/.*logicalFrame=Rect\(0, 0 - ([0-9]+), ([0-9]+)\).*/\2/' <<<"$frame")
size="${w}x${h}"
"${ADB[@]}" logcat -c
"${ADB[@]}" shell input -d "$DISPLAY_ID" tap $((w * 1119 / 1240)) $((h * 61 / 1080))
wait_log "MSG_CLOSED reason=0" 2 && pass "Hide sends MSG_CLOSED(by user)" || fail "no MSG_CLOSED(by user) after Hide (tapped $size display)"
# No real Command Panel answers with MSG_HIDE, so the panel goes after the 1 s fallback.
sleep 2
[[ -z $(panel_display) ]] && pass "panel gone after Hide" || fail "panel still up after Hide"

echo "== opened elsewhere"
start_session
"${ADB[@]}" logcat -c
"${ADB[@]}" shell am start -n "$PKG/jr.brian.thorkbm.MainActivity" >/dev/null
wait_log "MSG_CLOSED reason=1" 3 && pass "opening the app sends MSG_CLOSED(opened elsewhere)" || fail "no MSG_CLOSED(opened elsewhere)"
fake_cp -a jr.brian.thorkbm.debug.PLUGIN_HIDE
sleep 1
[[ -n $(panel_display) ]] && pass "panel stays up as a normal app (late MSG_HIDE ignored)" || fail "panel gone after opened elsewhere"
echo "The panel is left running on its own; stop it from its notification."

echo
((failures == 0)) && echo "All checks passed." || echo "$failures check(s) failed."
exit $((failures > 0))
