package jr.brian.thorkbm.feature.settings

import jr.brian.thorkbm.core.model.Language
import jr.brian.thorkbm.core.model.NumericSetting
import jr.brian.thorkbm.core.model.Settings
import jr.brian.thorkbm.core.model.ToggleSetting
import jr.brian.thorkbm.core.update.UpdateAction
import jr.brian.thorkbm.core.update.UpdateState

enum class SettingsTab { LANGUAGES, KEYBOARD, MOUSE, SCREEN, SETUP, ABOUT }

enum class LanguagePicker { KEYBOARD, INTERFACE }

enum class SettingsNote { SAVED, SAVE_FAILED }

data class SettingsUiState(
    val settings: Settings = Settings(),
    val tab: SettingsTab = SettingsTab.LANGUAGES,
    val picker: LanguagePicker? = null,
    val note: SettingsNote? = null,
    val learningKey: Boolean = false,
    /** A hardware key can be bound only through the accessibility service. */
    val canBindKey: Boolean = false,
    /** Real panel power-off needs Shizuku; without it the panel blacks out. */
    val canPowerOff: Boolean = false,
    val update: UpdateState = UpdateState(),
)

sealed interface SettingsIntent {
    data class SelectTab(val tab: SettingsTab) : SettingsIntent

    data class Step(val setting: NumericSetting, val direction: Int) : SettingsIntent

    data class Toggle(val setting: ToggleSetting) : SettingsIntent

    /** Moves the "Open keyboard" button to the next corner. */
    data object CycleRestoreCorner : SettingsIntent

    data class OpenPicker(val picker: LanguagePicker) : SettingsIntent

    /** `null` for the interface picker means "follow keyboard". */
    data class ChooseLanguage(val language: Language?) : SettingsIntent

    data object DismissPicker : SettingsIntent

    data object LearnKey : SettingsIntent

    data object ClearKey : SettingsIntent

    data object TurnScreenOff : SettingsIntent

    /** Opens the permissions checklist in the app. */
    data object OpenSetup : SettingsIntent

    data object StopPanel : SettingsIntent

    /** Checking for, downloading and installing a new release. */
    data class Update(val action: UpdateAction) : SettingsIntent
}

/** App-level actions settings can trigger; implemented by the app module. */
interface AppActions {
    fun openSetup()

    fun stopPanel()
}
