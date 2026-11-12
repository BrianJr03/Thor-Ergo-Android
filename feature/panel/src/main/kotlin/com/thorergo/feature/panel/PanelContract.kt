package com.thorergo.feature.panel

import com.thorergo.core.injection.InjectorStatus
import com.thorergo.core.injection.PanelPower
import com.thorergo.core.keyboard.KeyboardState
import com.thorergo.core.model.PanelView
import com.thorergo.feature.panel.output.PanelMessage
import com.thorergo.feature.settings.SettingsIntent
import com.thorergo.feature.settings.SettingsUiState

data class PanelUiState(
    val keyboard: KeyboardState = KeyboardState(),
    val settings: SettingsUiState = SettingsUiState(),
    val power: PanelPower = PanelPower.ON,
    val collapsed: Boolean = false,
    val message: PanelMessage? = null,
    val status: InjectorStatus = InjectorStatus(),
)

/** Raw multi-touch in logical panel units (1240 × 1080). */
sealed interface PanelTouch {
    val pointerId: Long

    data class Down(override val pointerId: Long, val x: Float, val y: Float, val timeMs: Long) : PanelTouch

    data class Move(override val pointerId: Long, val x: Float, val y: Float, val timeMs: Long) : PanelTouch

    data class Up(override val pointerId: Long, val timeMs: Long) : PanelTouch

    data class Cancel(override val pointerId: Long) : PanelTouch
}

sealed interface PanelIntent {
    data class Touch(val touch: PanelTouch) : PanelIntent

    data class SelectView(val view: PanelView) : PanelIntent

    data object DismissMenu : PanelIntent

    data object CloseSettings : PanelIntent

    data object ReleaseAll : PanelIntent

    data object Reconnect : PanelIntent

    data object TurnScreenOff : PanelIntent

    data object Wake : PanelIntent

    data object Expand : PanelIntent

    data object DismissMessage : PanelIntent

    data class Settings(val intent: SettingsIntent) : PanelIntent
}

sealed interface PanelEffect {
    data object KeyFeedback : PanelEffect
}
