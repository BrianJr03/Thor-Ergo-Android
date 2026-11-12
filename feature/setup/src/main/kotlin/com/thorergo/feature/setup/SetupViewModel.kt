package com.thorergo.feature.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thorergo.core.data.SettingsRepository
import com.thorergo.core.display.DisplayRepository
import com.thorergo.core.injection.InputInjector
import com.thorergo.core.injection.ShizukuState
import com.thorergo.core.injection.shizuku.ShizukuAccess
import com.thorergo.feature.panel.service.PanelControl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val permissions: SystemPermissions,
    private val shizuku: ShizukuAccess,
    private val panel: PanelControl,
    injector: InputInjector,
    displays: DisplayRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private data class Granted(val overlay: Boolean, val notifications: Boolean)

    private val granted = MutableStateFlow(readPermissions())

    private val effects = Channel<SetupEffect>(Channel.BUFFERED)
    val effect: Flow<SetupEffect> = effects.receiveAsFlow()

    val state: StateFlow<SetupUiState> = combine(
        granted,
        injector.status,
        displays.displays.map { it != null },
        panel.running,
        settings.settings.map { it.effectiveUiLanguage },
    ) { granted, status, dual, running, language ->
        SetupUiState(
            overlayGranted = granted.overlay,
            notificationsGranted = granted.notifications,
            accessibilityEnabled = status.accessibility,
            shizuku = status.shizuku,
            hasSecondDisplay = dual,
            panelRunning = running,
            uiLanguage = language,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SetupUiState())

    fun onIntent(intent: SetupIntent) {
        when (intent) {
            SetupIntent.Refresh -> {
                granted.value = readPermissions()
                shizuku.refresh()
            }
            SetupIntent.GrantOverlay -> effects.trySend(SetupEffect.OpenOverlaySettings)
            SetupIntent.GrantNotifications -> effects.trySend(SetupEffect.RequestNotificationPermission)
            SetupIntent.EnableAccessibility -> effects.trySend(SetupEffect.OpenAccessibilitySettings)
            SetupIntent.SetUpShizuku -> when (shizuku.state.value) {
                ShizukuState.NotInstalled -> effects.trySend(SetupEffect.OpenShizukuDownload)
                ShizukuState.NotRunning -> effects.trySend(SetupEffect.OpenShizukuApp)
                ShizukuState.PermissionRequired -> shizuku.requestPermission()
                ShizukuState.Connecting, ShizukuState.Ready -> Unit
            }
            SetupIntent.TogglePanel -> if (state.value.panelRunning) panel.stop() else if (state.value.canStart) panel.start()
            SetupIntent.OpenSettings -> effects.trySend(SetupEffect.NavigateToSettings)
        }
    }

    private fun readPermissions() = Granted(permissions.canDrawOverlays(), permissions.canPostNotifications())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
