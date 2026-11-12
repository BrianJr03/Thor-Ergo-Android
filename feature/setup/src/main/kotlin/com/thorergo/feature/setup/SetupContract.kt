package com.thorergo.feature.setup

import com.thorergo.core.injection.ShizukuState
import com.thorergo.core.model.Language

data class SetupUiState(
    val overlayGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val shizuku: ShizukuState = ShizukuState.NotRunning,
    val hasSecondDisplay: Boolean = true,
    val panelRunning: Boolean = false,
    val uiLanguage: Language = Language.EN_US,
) {
    private val canSendInput: Boolean get() = shizuku == ShizukuState.Ready || accessibilityEnabled

    val canStart: Boolean get() = overlayGranted && canSendInput && hasSecondDisplay

    /** Accessibility alone works, but only reaches focused text fields. */
    val limited: Boolean get() = accessibilityEnabled && shizuku != ShizukuState.Ready

    val needsBackend: Boolean get() = !canSendInput
}

sealed interface SetupIntent {
    /** Permissions can change in system settings while the app is paused. */
    data object Refresh : SetupIntent

    /** The user opened the app: re-check permissions and bring a hidden panel back. */
    data object Resumed : SetupIntent

    data object GrantOverlay : SetupIntent

    data object GrantNotifications : SetupIntent

    data object EnableAccessibility : SetupIntent

    data object SetUpShizuku : SetupIntent

    data object TogglePanel : SetupIntent

    data object OpenSettings : SetupIntent
}

sealed interface SetupEffect {
    data object OpenOverlaySettings : SetupEffect

    data object RequestNotificationPermission : SetupEffect

    data object OpenAccessibilitySettings : SetupEffect

    data object OpenShizukuDownload : SetupEffect

    data object OpenShizukuApp : SetupEffect

    data object NavigateToSettings : SetupEffect
}
