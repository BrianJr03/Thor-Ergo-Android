package jr.brian.thorkbm.feature.panel.plugin

/**
 * The Panel plugin contract, version 1: how Command Panel hands the bottom screen to another app and takes it back.
 * The full spec is `docs/PANEL_PLUGINS.md` in the Command Panel repo.
 *
 * Constants only, with no Android or Command Panel imports, so a plugin can copy this file as is (keeping or changing
 * the `package` line). Never renumber or rename a value: published plugins depend on them. A new message or argument
 * means a new [VERSION].
 */
object PanelPluginContract {

    /** The contract version this file describes. */
    const val VERSION = 1

    /** Intent action of the plugin's bound service (its `<intent-filter>`). Namespaced, not an app id. */
    const val ACTION_PANEL_PLUGIN = "jr.brian.commandpanel.plugin.action.PANEL_PLUGIN"

    /** `<meta-data>` on the plugin's service: the highest contract version it speaks, as an integer. */
    const val META_VERSION = "jr.brian.commandpanel.plugin.VERSION"

    // ---- Command Panel to plugin (Message.what) ----

    /**
     * Show the plugin's UI on the bottom screen. `arg1` is the display id to show on, `arg2` the contract version this
     * session uses, and `replyTo` the Messenger for [MSG_SHOWN] / [MSG_CLOSED].
     */
    const val MSG_SHOW = 1

    /** Command Panel's panel is already back on screen: remove the plugin's UI from the bottom screen now. No reply. */
    const val MSG_HIDE = 2

    // ---- plugin to Command Panel (Message.what) ----

    /** The plugin's UI is drawn on the requested display. Sent once per [MSG_SHOW]. */
    const val MSG_SHOWN = 101

    /** The plugin ended the session itself. `arg1` is one of the `CLOSED_*` reasons. Command Panel restores its panel. */
    const val MSG_CLOSED = 102

    // ---- MSG_CLOSED reasons (arg1) ----

    /** The user closed the plugin (for example its own Hide key). */
    const val CLOSED_BY_USER = 0

    /** The plugin's UI was opened outside Command Panel (its launcher icon, a notification) and left the session. */
    const val CLOSED_OPENED_ELSEWHERE = 1

    /** The plugin can't show: it doesn't speak the session's version, or its UI failed to start. */
    const val CLOSED_ERROR = 2

    // ---- timings ----

    /** Command Panel restores its panel if no [MSG_SHOWN] arrives this long after [MSG_SHOW]. */
    const val SHOWN_TIMEOUT_MS = 3_000L

    // ---- Command Panel's identity, for the plugin's `sendingUid` check ----

    /** Command Panel's release and debug packages. */
    val COMMAND_PANEL_PACKAGES = listOf("jr.brian.commandpannel", "jr.brian.commandpannel.debug")

    /** SHA-256 of Command Panel's release signing certificate (lowercase hex, no separators). Debug builds differ. */
    const val COMMAND_PANEL_CERT_SHA256 = "8ebd8e6ff20a29019a857fe25a98532649d66bf3fd778a64150db8085d988927"
}
