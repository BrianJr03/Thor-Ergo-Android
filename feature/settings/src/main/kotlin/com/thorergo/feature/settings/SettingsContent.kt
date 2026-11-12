package com.thorergo.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thorergo.core.designsystem.R
import com.thorergo.core.designsystem.component.ErgoButton
import com.thorergo.core.designsystem.component.ErgoTabs
import com.thorergo.core.designsystem.component.KeyTone
import com.thorergo.core.designsystem.component.StepperRow
import com.thorergo.core.designsystem.component.ValueRow
import com.thorergo.core.designsystem.theme.ErgoTheme
import com.thorergo.core.model.Language
import com.thorergo.core.model.NumericSetting
import com.thorergo.core.model.Settings
import com.thorergo.core.model.ToggleSetting

/**
 * The settings tabs. Hosts add their own actions in [footer]: the panel offers
 * "Back to keyboard" and "Release all inputs".
 */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, color = ErgoTheme.colors.text)
        ErgoTabs(SettingsTab.entries, state.tab, { it.label() }, { onIntent(SettingsIntent.SelectTab(it)) })
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val picker = state.picker
            if (picker != null) {
                LanguagePickerList(picker, state.settings, onIntent)
            } else {
                when (state.tab) {
                    SettingsTab.LANGUAGES -> LanguagesTab(state.settings, onIntent)
                    SettingsTab.KEYBOARD -> KeyboardTab(state.settings, onIntent)
                    SettingsTab.MOUSE -> MouseTab(state.settings, onIntent)
                    SettingsTab.SCREEN -> ScreenTab(state, onIntent)
                    SettingsTab.ABOUT -> AboutTab()
                }
            }
            state.note?.let {
                Hint(stringResource(if (it == SettingsNote.SAVED) R.string.saved else R.string.save_failed))
            }
        }
        footer()
    }
}

@Composable
private fun LanguagesTab(settings: Settings, onIntent: (SettingsIntent) -> Unit) {
    ValueRow(stringResource(R.string.setting_keyboard_language), settings.language.displayName, { onIntent(SettingsIntent.OpenPicker(LanguagePicker.KEYBOARD)) })
    ValueRow(
        stringResource(R.string.setting_ui_language),
        settings.uiLanguage?.displayName ?: stringResource(R.string.follow_keyboard),
        { onIntent(SettingsIntent.OpenPicker(LanguagePicker.INTERFACE)) },
    )
    Hint(stringResource(R.string.languages_hint_immediate))
    Hint(stringResource(R.string.languages_hint_menus))
    Hint(stringResource(R.string.languages_hint_app_only))
    when {
        settings.language.usesPhysicalKeys -> Hint(stringResource(R.string.languages_hint_us))
        settings.language.usesPinyin -> {
            Hint(stringResource(R.string.languages_hint_pinyin))
            Hint(stringResource(R.string.languages_hint_no_learning))
        }
    }
}

@Composable
private fun LanguagePickerList(picker: LanguagePicker, settings: Settings, onIntent: (SettingsIntent) -> Unit) {
    val selected = if (picker == LanguagePicker.KEYBOARD) settings.language else settings.uiLanguage
    val choices: List<Language?> = (if (picker == LanguagePicker.INTERFACE) listOf(null) else emptyList<Language?>()) + Language.entries
    for (language in choices) {
        ErgoButton(
            text = language?.displayName ?: stringResource(R.string.follow_keyboard),
            onClick = { onIntent(SettingsIntent.ChooseLanguage(language)) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            tone = if (language == selected) KeyTone.ACCENT else KeyTone.NORMAL,
        )
    }
}

@Composable
private fun KeyboardTab(settings: Settings, onIntent: (SettingsIntent) -> Unit) {
    Stepper(NumericSetting.LONG_PRESS_MS, settings, onIntent)
    Toggle(ToggleSetting.REPEAT_ENABLED, settings, onIntent)
    Stepper(NumericSetting.REPEAT_DELAY_MS, settings, onIntent)
    Stepper(NumericSetting.REPEAT_INTERVAL_MS, settings, onIntent)
    Stepper(NumericSetting.CAPS_WINDOW_MS, settings, onIntent)
    Toggle(ToggleSetting.HAPTICS, settings, onIntent)
    Hint(stringResource(R.string.keyboard_hint_accents))
    Hint(stringResource(R.string.keyboard_hint_shift))
    Hint(stringResource(R.string.keyboard_hint_arrows))
}

@Composable
private fun MouseTab(settings: Settings, onIntent: (SettingsIntent) -> Unit) {
    Stepper(NumericSetting.SENSITIVITY, settings, onIntent)
    Stepper(NumericSetting.SCROLL_SPEED, settings, onIntent)
    Stepper(NumericSetting.SCROLL_SCALE, settings, onIntent)
    Toggle(ToggleSetting.TAP_TO_CLICK, settings, onIntent)
    Toggle(ToggleSetting.GLIDE, settings, onIntent)
    Stepper(NumericSetting.FRICTION, settings, onIntent)
    Toggle(ToggleSetting.POINTER_AS_TOUCH, settings, onIntent)
}

@Composable
private fun ScreenTab(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
    val settings = state.settings
    ErgoButton(stringResource(R.string.screen_turn_off), { onIntent(SettingsIntent.TurnScreenOff) }, Modifier.fillMaxWidth(), tone = KeyTone.ACCENT)
    ValueRow(
        ToggleSetting.SCREEN_TOGGLE.label(),
        onOff(settings.screenToggleEnabled),
        { onIntent(SettingsIntent.Toggle(ToggleSetting.SCREEN_TOGGLE)) },
        enabled = state.canBindKey,
    )
    val key = settings.screenToggleKeyCode
    ValueRow(
        stringResource(R.string.setting_screen_key),
        when {
            state.learningKey -> stringResource(R.string.screen_key_learn)
            key != null -> keyName(key)
            else -> stringResource(R.string.screen_key_none)
        },
        { onIntent(if (key != null && !state.learningKey) SettingsIntent.ClearKey else SettingsIntent.LearnKey) },
        enabled = state.canBindKey,
        tone = if (state.learningKey) KeyTone.ACCENT else KeyTone.NORMAL,
    )
    Hint(stringResource(R.string.screen_hint_keeps_running))
    Hint(stringResource(R.string.screen_hint_wake))
    if (!state.canPowerOff) Hint(stringResource(R.string.screen_hint_shizuku))
    if (!state.canBindKey) Hint(stringResource(R.string.screen_hint_accessibility))
}

@Composable
private fun AboutTab() {
    for (line in listOf(
        R.string.about_design, R.string.about_base, R.string.about_input,
        R.string.about_pinyin, R.string.about_android, R.string.about_independent,
    )) {
        Text(stringResource(line), style = MaterialTheme.typography.bodyLarge, color = ErgoTheme.colors.text, modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
private fun Stepper(setting: NumericSetting, settings: Settings, onIntent: (SettingsIntent) -> Unit) {
    val value = settings[setting]
    StepperRow(
        label = setting.label(),
        value = formatSetting(setting, value, currentLocale()),
        canDecrease = value > setting.min,
        canIncrease = value < setting.max,
        onDecrease = { onIntent(SettingsIntent.Step(setting, -1)) },
        onIncrease = { onIntent(SettingsIntent.Step(setting, 1)) },
    )
}

@Composable
private fun Toggle(setting: ToggleSetting, settings: Settings, onIntent: (SettingsIntent) -> Unit) {
    ValueRow(setting.label(), onOff(settings[setting]), { onIntent(SettingsIntent.Toggle(setting)) })
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = ErgoTheme.colors.textMuted, modifier = Modifier.padding(vertical = 2.dp))
}
