package jr.brian.thorkbm.feature.panel.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import jr.brian.thorkbm.core.designsystem.R as DesignR
import jr.brian.thorkbm.core.display.DisplayRepository
import jr.brian.thorkbm.core.display.DualDisplays
import jr.brian.thorkbm.core.injection.InputInjector
import jr.brian.thorkbm.core.injection.PanelPowerController
import jr.brian.thorkbm.core.injection.di.ApplicationScope
import jr.brian.thorkbm.core.model.PanelView
import jr.brian.thorkbm.feature.panel.PanelIntent
import jr.brian.thorkbm.feature.panel.PanelUiState
import jr.brian.thorkbm.feature.panel.PanelViewModel
import jr.brian.thorkbm.feature.panel.R
import jr.brian.thorkbm.feature.panel.input.CursorController
import jr.brian.thorkbm.feature.panel.ui.CursorOverlay
import jr.brian.thorkbm.feature.panel.ui.PanelRoute
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

/**
 * Hosts the keyboard panel on the lower display and the pointer on the upper display as
 * non-focusable overlay windows, so the game on the upper screen keeps input focus.
 */
@AndroidEntryPoint
class PanelService : LifecycleService() {
    @Inject lateinit var displays: DisplayRepository
    @Inject lateinit var viewModels: Provider<PanelViewModel>
    @Inject lateinit var cursor: CursorController
    @Inject lateinit var power: PanelPowerController
    @Inject lateinit var injector: InputInjector
    @Inject lateinit var launcher: PanelLauncher
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    private val owner = OverlayOwner()
    private lateinit var viewModel: PanelViewModel
    private var panel: OverlayWindow? = null
    private var pointer: OverlayWindow? = null
    private var attached: DualDisplays? = null

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        owner.create()
        viewModel = ViewModelProvider(owner, viewModelFactory { initializer { viewModels.get() } })[PanelViewModel::class.java]
        launcher.setRunning(true)

        lifecycleScope.launch { displays.displays.collect(::attach) }
        lifecycleScope.launch {
            viewModel.state.map { it.windowMode() to it.keyboard.settings.restoreButtonCorner }.distinctUntilChanged().collect { (mode, corner) ->
                panel?.setMode(mode, handleSizePx(), corner)
            }
        }
        lifecycleScope.launch {
            viewModel.state.map { it.showsPointer() }.distinctUntilChanged().collect { visible ->
                if (visible) pointer?.show() else pointer?.remove()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_SHOW -> viewModel.onIntent(PanelIntent.Expand)
        }
        return START_NOT_STICKY
    }

    private fun attach(pair: DualDisplays?) {
        if (pair == attached) return
        detach()
        attached = pair
        if (pair == null || !Settings.canDrawOverlays(this)) return
        val manager = getSystemService(DisplayManager::class.java)
        val lower = manager.getDisplay(pair.lower.id) ?: return
        val upper = manager.getDisplay(pair.upper.id) ?: return
        try {
            pointer = OverlayWindow(this, upper, owner, touchable = false) { CursorOverlay(cursor) }
                .also { if (viewModel.state.value.showsPointer()) it.show() }
            panel = OverlayWindow(this, lower, owner, touchable = true) { PanelRoute(viewModel) }
                .also { it.setMode(viewModel.state.value.windowMode(), handleSizePx(), viewModel.state.value.keyboard.settings.restoreButtonCorner) }
        } catch (e: RuntimeException) {
            Log.e("ThorKBM", "Could not show the panel windows", e)
            detach()
        }
    }

    private fun handleSizePx() = (HANDLE_WINDOW_DP * resources.displayMetrics.density).toInt()

    private fun detach() {
        panel?.remove()
        pointer?.remove()
        panel = null
        pointer = null
    }

    override fun onDestroy() {
        detach()
        owner.destroy()
        launcher.setRunning(false)
        // Never leave the lower panel dark or a button held once the panel is gone.
        appScope.launch {
            power.turnOn()
            injector.releaseAll()
        }
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(DesignR.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val stop = PendingIntent.getService(this, 0, stopIntent(this), PendingIntent.FLAG_IMMUTABLE)
        val show = PendingIntent.getService(this, 1, showIntent(this), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_panel_notification)
            .setContentTitle(getString(DesignR.string.notification_title))
            .setContentText(getString(DesignR.string.notification_text))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(show)
            .addAction(0, getString(DesignR.string.open_keyboard), show)
            .addAction(0, getString(DesignR.string.action_stop), stop)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "panel"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "jr.brian.thorkbm.action.STOP_PANEL"
        private const val ACTION_SHOW = "jr.brian.thorkbm.action.SHOW_PANEL"
        /** The collapsed button plus a small margin from the screen edges; see CollapsedHandle. */
        private const val HANDLE_WINDOW_DP = 64

        internal fun stopIntent(context: Context) = Intent(context, PanelService::class.java).setAction(ACTION_STOP)

        internal fun showIntent(context: Context) = Intent(context, PanelService::class.java).setAction(ACTION_SHOW)

        /** The pointer is only shown while a trackpad is on screen: not when hidden, not in keyboard-only view. */
        private fun PanelUiState.showsPointer() = !collapsed && keyboard.view != PanelView.KEYBOARD

        /** Hidden with the restore button turned off means no window at all on the lower screen. */
        private fun PanelUiState.windowMode() = when {
            !collapsed -> PanelWindowMode.FULL
            keyboard.settings.showRestoreButton -> PanelWindowMode.HANDLE
            else -> PanelWindowMode.GONE
        }
    }
}
