package jr.brian.thorkbm.feature.settings

import jr.brian.thorkbm.core.data.SettingsRepository
import jr.brian.thorkbm.core.injection.InputInjector
import jr.brian.thorkbm.core.injection.PanelPowerController
import jr.brian.thorkbm.core.injection.accessibility.AccessibilityBridge
import jr.brian.thorkbm.core.model.Settings
import jr.brian.thorkbm.core.update.AppUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Settings logic shared by the panel's in-place settings and the app's settings screen.
 * Owned by a ViewModel, which passes its scope.
 */
class SettingsStateHolder @AssistedInject constructor(
    @Assisted private val scope: CoroutineScope,
    private val repository: SettingsRepository,
    private val bridge: AccessibilityBridge,
    private val power: PanelPowerController,
    private val actions: AppActions,
    private val updater: AppUpdater,
    injector: InputInjector,
) {
    private data class Local(
        val tab: SettingsTab = SettingsTab.LANGUAGES,
        val picker: LanguagePicker? = null,
        val note: SettingsNote? = null,
        val learningKey: Boolean = false,
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<SettingsUiState> = combine(repository.settings, local, injector.status, updater.state) { settings, ui, status, update ->
        SettingsUiState(
            settings = settings,
            tab = ui.tab,
            picker = ui.picker,
            note = ui.note,
            learningKey = ui.learningKey,
            canBindKey = status.accessibility,
            canPowerOff = status.canPowerOffPanel,
            update = update,
        )
    }.stateIn(scope, SharingStarted.Eagerly, SettingsUiState())

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SelectTab -> local.update { it.copy(tab = intent.tab, picker = null, note = null) }
            is SettingsIntent.Step -> save { it.stepped(intent.setting, intent.direction) }
            is SettingsIntent.Toggle -> save { it.toggled(intent.setting) }
            SettingsIntent.CycleRestoreCorner -> save { it.copy(restoreButtonCorner = it.restoreButtonCorner.next()) }
            is SettingsIntent.OpenPicker -> local.update { it.copy(picker = intent.picker) }
            SettingsIntent.DismissPicker -> local.update { it.copy(picker = null) }
            is SettingsIntent.ChooseLanguage -> {
                val picker = local.value.picker ?: return
                local.update { it.copy(picker = null) }
                save { settings ->
                    when (picker) {
                        LanguagePicker.KEYBOARD -> intent.language?.let { settings.copy(language = it) } ?: settings
                        LanguagePicker.INTERFACE -> settings.copy(uiLanguage = intent.language)
                    }
                }
            }
            SettingsIntent.LearnKey -> learnKey()
            SettingsIntent.ClearKey -> save { it.copy(screenToggleKeyCode = null) }
            SettingsIntent.TurnScreenOff -> scope.launch { power.turnOff() }
            SettingsIntent.OpenSetup -> actions.openSetup()
            SettingsIntent.StopPanel -> actions.stopPanel()
            is SettingsIntent.Update -> updater.perform(intent.action)
        }
    }

    private fun learnKey() {
        if (local.value.learningKey) return
        local.update { it.copy(learningKey = true) }
        bridge.startLearning()
        scope.launch {
            try {
                val code = bridge.learnedKeys.first()
                save { it.copy(screenToggleKeyCode = code, screenToggleEnabled = true) }
            } finally {
                bridge.stopLearning()
                local.update { it.copy(learningKey = false) }
            }
        }
    }

    /** A failed write keeps the previous value and says so, as the original panel did. */
    private fun save(transform: (Settings) -> Settings) {
        scope.launch {
            val result = repository.update(transform)
            local.update { it.copy(note = if (result.isSuccess) SettingsNote.SAVED else SettingsNote.SAVE_FAILED) }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(scope: CoroutineScope): SettingsStateHolder
    }
}
