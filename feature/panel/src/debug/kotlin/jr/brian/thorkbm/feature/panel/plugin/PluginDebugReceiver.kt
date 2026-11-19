package jr.brian.thorkbm.feature.panel.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Messenger
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Debug builds only: stands in for Command Panel so the plugin can be checked on a device without
 * it (tools/plugin/check-plugin.sh). It skips the caller check, which the unit tests cover, and logs
 * the replies Command Panel would get under the `ThorKBM-Plugin` tag.
 *
 * ```
 * adb shell am broadcast -n jr.brian.thorkbm.debug/jr.brian.thorkbm.feature.panel.plugin.PluginDebugReceiver \
 *     -a jr.brian.thorkbm.debug.PLUGIN_SHOW --ei display 4 [--ei version 1]
 * adb shell am broadcast -n jr.brian.thorkbm.debug/jr.brian.thorkbm.feature.panel.plugin.PluginDebugReceiver \
 *     -a jr.brian.thorkbm.debug.PLUGIN_HIDE
 * ```
 */
class PluginDebugReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Session {
        fun session(): PluginSession
    }

    override fun onReceive(context: Context, intent: Intent) {
        val session = EntryPointAccessors.fromApplication(context, Session::class.java).session()
        when (intent.action) {
            ACTION_SHOW -> session.show(intent.getIntExtra(EXTRA_DISPLAY, -1), intent.getIntExtra(EXTRA_VERSION, PanelPluginContract.VERSION), fakeCommandPanel)
            ACTION_HIDE -> session.hide()
        }
    }

    private companion object {
        const val ACTION_SHOW = "jr.brian.thorkbm.debug.PLUGIN_SHOW"
        const val ACTION_HIDE = "jr.brian.thorkbm.debug.PLUGIN_HIDE"
        const val EXTRA_DISPLAY = "display"
        const val EXTRA_VERSION = "version"

        val fakeCommandPanel = Messenger(
            Handler(Looper.getMainLooper()) { msg ->
                when (msg.what) {
                    PanelPluginContract.MSG_SHOWN -> Log.i(PluginSession.TAG, "fake CP got MSG_SHOWN")
                    PanelPluginContract.MSG_CLOSED -> Log.i(PluginSession.TAG, "fake CP got MSG_CLOSED reason=${msg.arg1}")
                }
                true
            },
        )
    }
}
