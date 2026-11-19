package jr.brian.thorkbm.feature.panel.plugin

import android.app.Service
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The panel as a Command Panel plugin: Command Panel binds this service and sends MSG_SHOW and
 * MSG_HIDE (see [PanelPluginContract]). The panel itself still runs in PanelService, which stays
 * private; this service only drives it through [PluginSession].
 */
@AndroidEntryPoint
class PanelPluginService : Service() {
    @Inject lateinit var session: PluginSession

    private val messenger by lazy {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        Messenger(Handler(Looper.getMainLooper(), PluginMessages(CommandPanelCaller(packageManager, anySigner = debuggable), session)))
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    /** Command Panel unbound or died: its panel is back, or it is gone and can't be told anything. */
    override fun onUnbind(intent: Intent): Boolean {
        session.hide()
        return false
    }
}

/** Checks every message's sender, then hands MSG_SHOW and MSG_HIDE to the session. */
internal class PluginMessages(
    private val caller: CallerCheck,
    private val session: PluginSession,
) : Handler.Callback {
    override fun handleMessage(msg: Message): Boolean {
        if (!caller.isCommandPanel(msg.sendingUid)) {
            Log.w(PluginSession.TAG, "Ignored message ${msg.what} from uid ${msg.sendingUid}, not Command Panel")
            return true
        }
        when (msg.what) {
            PanelPluginContract.MSG_SHOW -> session.show(msg.arg1, msg.arg2, msg.replyTo)
            PanelPluginContract.MSG_HIDE -> session.hide()
            else -> return false
        }
        return true
    }
}
