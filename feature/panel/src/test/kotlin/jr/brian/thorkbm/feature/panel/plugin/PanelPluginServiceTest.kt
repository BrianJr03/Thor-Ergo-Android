package jr.brian.thorkbm.feature.panel.plugin

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import jr.brian.thorkbm.core.testing.FakePanelPowerController
import jr.brian.thorkbm.feature.panel.plugin.PanelPluginContract.CLOSED_BY_USER
import jr.brian.thorkbm.feature.panel.plugin.PanelPluginContract.CLOSED_ERROR
import jr.brian.thorkbm.feature.panel.plugin.PanelPluginContract.CLOSED_OPENED_ELSEWHERE
import jr.brian.thorkbm.feature.panel.plugin.PanelPluginContract.MSG_CLOSED
import jr.brian.thorkbm.feature.panel.plugin.PanelPluginContract.MSG_HIDE
import jr.brian.thorkbm.feature.panel.plugin.PanelPluginContract.MSG_SHOW
import jr.brian.thorkbm.feature.panel.plugin.PanelPluginContract.MSG_SHOWN
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** The plugin service's message handling: the caller check, the session and what Command Panel hears back. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PanelPluginServiceTest {
    private class FakeHost : PluginHost {
        var shows = 0
        var removes = 0
        var refuse = false

        override fun showForPlugin() {
            if (refuse) throw IllegalStateException("startForegroundService() not allowed")
            shows++
        }

        override fun remove() {
            removes++
        }
    }

    private val host = FakeHost()
    private val power = FakePanelPowerController()
    private val session = PluginSession(host, power)
    private val messages = PluginMessages({ it == COMMAND_PANEL_UID }, session)

    /** What Command Panel received, as (what, arg1). */
    private val received = mutableListOf<Pair<Int, Int>>()
    private val commandPanel = Messenger(Handler(Looper.getMainLooper()) { received += it.what to it.arg1; true })

    private fun send(what: Int, uid: Int = COMMAND_PANEL_UID, display: Int = LOWER, version: Int = 1, handler: PluginMessages = messages) {
        val msg = Message.obtain(null, what, display, version).apply {
            replyTo = commandPanel
            sendingUid = uid
        }
        handler.handleMessage(msg)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun drawn() {
        session.shown(session.pendingShow.value!!)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `show starts the panel and answers shown once it is drawn`() {
        send(MSG_SHOW)
        assertThat(host.shows).isEqualTo(1)
        assertThat(session.active.value).isTrue()
        assertThat(power.ownedElsewhere.value).isTrue()
        assertThat(session.pendingShow.value?.displayId).isEqualTo(LOWER)
        assertThat(received).isEmpty()
        drawn()
        assertThat(received).containsExactly(MSG_SHOWN to 0)
        assertThat(session.pendingShow.value).isNull()
    }

    @Test
    fun `hide removes the panel without a reply`() {
        send(MSG_SHOW)
        drawn()
        received.clear()
        send(MSG_HIDE)
        assertThat(host.removes).isEqualTo(1)
        assertThat(session.active.value).isFalse()
        assertThat(power.ownedElsewhere.value).isFalse()
        assertThat(received).isEmpty()
    }

    @Test
    fun `a stranger's messages are ignored`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        shadowOf(context.packageManager).setPackagesForUid(STRANGER_UID, "com.example.stranger")
        val strict = PluginMessages(CommandPanelCaller(context.packageManager, anySigner = false), session)
        send(MSG_SHOW, uid = STRANGER_UID, handler = strict)
        assertThat(host.shows).isEqualTo(0)
        assertThat(session.active.value).isFalse()
        assertThat(received).isEmpty()
    }

    @Test
    fun `Command Panel's package with another signing key is ignored, unless signers are relaxed`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pm = shadowOf(context.packageManager)
        pm.installPackage(
            PackageInfo().apply {
                packageName = "jr.brian.commandpannel.debug"
                applicationInfo = ApplicationInfo().apply { packageName = "jr.brian.commandpannel.debug" }
            },
        )
        pm.setPackagesForUid(DEBUG_CP_UID, "jr.brian.commandpannel.debug")
        send(MSG_SHOW, uid = DEBUG_CP_UID, handler = PluginMessages(CommandPanelCaller(context.packageManager, anySigner = false), session))
        assertThat(host.shows).isEqualTo(0)
        send(MSG_SHOW, uid = DEBUG_CP_UID, handler = PluginMessages(CommandPanelCaller(context.packageManager, anySigner = true), session))
        assertThat(host.shows).isEqualTo(1)
    }

    @Test
    fun `a version it doesn't speak closes with an error`() {
        send(MSG_SHOW, version = PanelPluginContract.VERSION + 1)
        assertThat(received).containsExactly(MSG_CLOSED to CLOSED_ERROR)
        assertThat(host.shows).isEqualTo(0)
        assertThat(session.active.value).isFalse()
    }

    @Test
    fun `a refused start closes with an error`() {
        host.refuse = true
        send(MSG_SHOW)
        assertThat(received).containsExactly(MSG_CLOSED to CLOSED_ERROR)
        assertThat(session.pendingShow.value).isNull()
    }

    @Test
    fun `the Hide key closes, and the panel goes when Command Panel's hide comes`() {
        send(MSG_SHOW)
        drawn()
        received.clear()
        session.closeByUser()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(received).containsExactly(MSG_CLOSED to CLOSED_BY_USER)
        assertThat(host.removes).isEqualTo(0)
        send(MSG_HIDE)
        assertThat(host.removes).isEqualTo(1)
        assertThat(received).hasSize(1)
    }

    @Test
    fun `the panel goes anyway if Command Panel never sends hide`() {
        send(MSG_SHOW)
        session.closeByUser()
        shadowOf(Looper.getMainLooper()).idleFor(PluginSession.CLOSE_FALLBACK_MS, TimeUnit.MILLISECONDS)
        assertThat(host.removes).isEqualTo(1)
        assertThat(session.active.value).isFalse()
    }

    @Test
    fun `opened elsewhere leaves the session and keeps the panel through the later hide`() {
        send(MSG_SHOW)
        drawn()
        received.clear()
        session.openedElsewhere()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(received).containsExactly(MSG_CLOSED to CLOSED_OPENED_ELSEWHERE)
        assertThat(session.active.value).isFalse()
        send(MSG_HIDE)
        assertThat(host.removes).isEqualTo(0)
    }

    @Test
    fun `the panel stopping on its own closes the session`() {
        send(MSG_SHOW)
        drawn()
        received.clear()
        session.panelGone()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(received).containsExactly(MSG_CLOSED to CLOSED_BY_USER)
        assertThat(session.active.value).isFalse()
    }

    @Test
    fun `Command Panel dying mid-session acts like hide, with no reply`() {
        send(MSG_SHOW)
        drawn()
        received.clear()
        session.commandPanelDied()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(host.removes).isEqualTo(1)
        assertThat(session.active.value).isFalse()
        assertThat(power.ownedElsewhere.value).isFalse()
        assertThat(received).isEmpty()
    }

    @Test
    fun `hide then unbind removes the panel once`() {
        send(MSG_SHOW)
        drawn()
        send(MSG_HIDE)
        // onUnbind right after MSG_HIDE, as Command Panel does.
        session.hide()
        assertThat(host.removes).isEqualTo(1)
    }

    @Test
    fun `a late drawn frame after Command Panel gave up sends nothing`() {
        send(MSG_SHOW)
        val request = session.pendingShow.value!!
        send(MSG_HIDE)
        session.shown(request)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(received).isEmpty()
        assertThat(host.removes).isEqualTo(1)
    }

    @Test
    fun `outside a session nothing is sent`() {
        session.openedElsewhere()
        session.panelGone()
        session.closeByUser()
        send(MSG_HIDE)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(received).isEmpty()
        assertThat(host.removes).isEqualTo(0)
    }

    private companion object {
        const val COMMAND_PANEL_UID = 10_123
        const val DEBUG_CP_UID = 10_124
        const val STRANGER_UID = 10_456
        const val LOWER = 4
    }
}
