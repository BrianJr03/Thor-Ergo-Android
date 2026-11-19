package jr.brian.thorkbm.feature.panel.plugin

import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import jr.brian.thorkbm.core.injection.PanelPowerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the panel needs to know about a Command Panel session. */
interface PluginMode {
    /** True from MSG_SHOW until the session ends, including the short wait for MSG_HIDE after the panel closes itself. */
    val active: StateFlow<Boolean>

    /** The user closed the panel (its Hide key): Command Panel takes the lower screen back. */
    fun closeByUser()
}

/** Starts and removes the panel for a session; [PanelLauncher][jr.brian.thorkbm.feature.panel.service.PanelLauncher] in the app. */
interface PluginHost {
    /** Starts the panel, or takes over a running one. Throws if Android refuses to start it. */
    fun showForPlugin()

    /** Removes the panel right away. */
    fun remove()
}

/** One MSG_SHOW still waiting for its MSG_SHOWN. */
data class ShowRequest(val displayId: Int, val serial: Int)

/**
 * The plugin side of the Command Panel contract (see [PanelPluginContract]). Main thread only: the
 * plugin service, the panel service and the panel all call it from there.
 *
 * Command Panel owns the lower screen's power for the whole session, so ThorKBM's own power-off is
 * locked while one is up.
 */
@Singleton
class PluginSession @Inject constructor(
    private val host: PluginHost,
    private val power: PanelPowerController,
) : PluginMode {
    private enum class Phase {
        IDLE,
        ACTIVE,

        /** The panel ended the session and waits for MSG_HIDE, so the lower screen never goes blank. */
        CLOSING,
    }

    private var phase = Phase.IDLE
    private var reply: Messenger? = null
    private var serial = 0
    private val handler = Handler(Looper.getMainLooper())
    private val removeLate = Runnable { if (phase == Phase.CLOSING) end(removeUi = true) }

    /** Command Panel crashed or was killed: nobody will send MSG_HIDE, so act as if it had. */
    private val commandPanelDeath = IBinder.DeathRecipient { handler.post(::commandPanelDied) }

    private val _active = MutableStateFlow(false)
    override val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _pendingShow = MutableStateFlow<ShowRequest?>(null)

    /** The MSG_SHOW the panel still has to answer once it is drawn on [ShowRequest.displayId]. */
    val pendingShow: StateFlow<ShowRequest?> = _pendingShow.asStateFlow()

    fun show(displayId: Int, version: Int, replyTo: Messenger?) {
        if (replyTo == null) {
            Log.w(TAG, "MSG_SHOW without replyTo, ignored")
            return
        }
        if (version !in 1..PanelPluginContract.VERSION) {
            Log.w(TAG, "MSG_SHOW for contract version $version, which this build doesn't speak")
            return send(replyTo, PanelPluginContract.MSG_CLOSED, PanelPluginContract.CLOSED_ERROR)
        }
        handler.removeCallbacks(removeLate)
        unlinkReply()
        phase = Phase.ACTIVE
        reply = replyTo
        try {
            replyTo.binder.linkToDeath(commandPanelDeath, 0)
        } catch (_: RemoteException) {
            // Already dead: no session to run.
            end(removeUi = false)
            return
        }
        _active.value = true
        power.setOwnedElsewhere(true)
        _pendingShow.value = ShowRequest(displayId, ++serial)
        try {
            host.showForPlugin()
        } catch (e: RuntimeException) {
            // ForegroundServiceStartNotAllowedException and other refusals to start the panel.
            Log.e(TAG, "Could not start the panel for Command Panel", e)
            fail()
        }
    }

    /** The panel has drawn a frame on the requested display. */
    fun shown(request: ShowRequest) {
        if (phase != Phase.ACTIVE || _pendingShow.value != request) return
        _pendingShow.value = null
        send(reply, PanelPluginContract.MSG_SHOWN)
    }

    /** The panel can't show (no overlay permission, wrong display, window error). */
    fun fail() = close(PanelPluginContract.CLOSED_ERROR)

    override fun closeByUser() = close(PanelPluginContract.CLOSED_BY_USER)

    /** The panel was opened another way (the app, the notification, setup): carry on as a normal app. */
    fun openedElsewhere() {
        when (phase) {
            Phase.IDLE -> Unit
            Phase.ACTIVE -> {
                send(reply, PanelPluginContract.MSG_CLOSED, PanelPluginContract.CLOSED_OPENED_ELSEWHERE)
                end(removeUi = false)
            }
            // Already closed toward Command Panel; only keep the panel instead of removing it.
            Phase.CLOSING -> end(removeUi = false)
        }
    }

    /** MSG_HIDE, or Command Panel unbound: its panel is back, so ours goes at once. Safe to repeat. */
    fun hide() {
        if (phase != Phase.IDLE) end(removeUi = true)
    }

    /** Command Panel's process died: the same as MSG_HIDE, with no one left to reply to. */
    internal fun commandPanelDied() = hide()

    /** The panel service stopped (its Stop action, Settings, or Android). */
    fun panelGone() {
        if (phase == Phase.ACTIVE) send(reply, PanelPluginContract.MSG_CLOSED, PanelPluginContract.CLOSED_BY_USER)
        if (phase != Phase.IDLE) end(removeUi = false)
    }

    private fun close(reason: Int) {
        if (phase != Phase.ACTIVE) return
        send(reply, PanelPluginContract.MSG_CLOSED, reason)
        phase = Phase.CLOSING
        _pendingShow.value = null
        // Command Panel answers with MSG_HIDE once its panel is back; don't wait forever if it doesn't.
        handler.postDelayed(removeLate, CLOSE_FALLBACK_MS)
    }

    private fun end(removeUi: Boolean) {
        handler.removeCallbacks(removeLate)
        phase = Phase.IDLE
        unlinkReply()
        reply = null
        _pendingShow.value = null
        _active.value = false
        power.setOwnedElsewhere(false)
        if (removeUi) host.remove()
    }

    private fun unlinkReply() {
        reply?.binder?.unlinkToDeath(commandPanelDeath, 0)
    }

    private fun send(to: Messenger?, what: Int, arg1: Int = 0) {
        try {
            to?.send(Message.obtain(null, what, arg1, 0))
        } catch (e: RemoteException) {
            Log.w(TAG, "Command Panel is gone; message $what not delivered", e)
        }
    }

    internal companion object {
        const val TAG = "ThorKBM-Plugin"
        const val CLOSE_FALLBACK_MS = 1_000L
    }
}
