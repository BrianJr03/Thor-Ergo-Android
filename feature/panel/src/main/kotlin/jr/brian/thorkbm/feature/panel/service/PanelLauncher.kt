package jr.brian.thorkbm.feature.panel.service

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Starts and stops the keyboard panel. */
interface PanelControl {
    val running: StateFlow<Boolean>

    fun start()

    fun stop()

    /** Brings a hidden panel back; does nothing if the panel is not running. */
    fun show()
}

@Singleton
class PanelLauncher @Inject constructor(@ApplicationContext private val context: Context) : PanelControl {
    private val _running = MutableStateFlow(false)
    override val running: StateFlow<Boolean> = _running.asStateFlow()

    override fun start() {
        context.startForegroundService(Intent(context, PanelService::class.java))
    }

    override fun stop() {
        context.startService(PanelService.stopIntent(context))
    }

    override fun show() {
        if (_running.value) context.startService(PanelService.showIntent(context))
    }

    internal fun setRunning(running: Boolean) {
        _running.value = running
    }
}
