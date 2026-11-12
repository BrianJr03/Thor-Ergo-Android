package com.thorergo.feature.settings

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.thorergo.core.designsystem.R
import com.thorergo.core.model.NumericSetting
import com.thorergo.core.model.SettingUnit
import com.thorergo.core.model.ToggleSetting
import java.util.Locale

@Composable
internal fun NumericSetting.label(): String = stringResource(
    when (this) {
        NumericSetting.LONG_PRESS_MS -> R.string.setting_long_press
        NumericSetting.REPEAT_DELAY_MS -> R.string.setting_repeat_delay
        NumericSetting.REPEAT_INTERVAL_MS -> R.string.setting_repeat_interval
        NumericSetting.CAPS_WINDOW_MS -> R.string.setting_caps_window
        NumericSetting.SENSITIVITY -> R.string.setting_pointer_speed
        NumericSetting.SCROLL_SPEED -> R.string.setting_scroll_speed
        NumericSetting.SCROLL_SCALE -> R.string.setting_scroll_scale
        NumericSetting.FRICTION -> R.string.setting_friction
    },
)

@Composable
internal fun ToggleSetting.label(): String = stringResource(
    when (this) {
        ToggleSetting.REPEAT_ENABLED -> R.string.setting_repeat_enabled
        ToggleSetting.HAPTICS -> R.string.setting_haptics
        ToggleSetting.TAP_TO_CLICK -> R.string.setting_tap_to_click
        ToggleSetting.GLIDE -> R.string.setting_glide
        ToggleSetting.POINTER_AS_TOUCH -> R.string.setting_pointer_as_touch
        ToggleSetting.SCREEN_TOGGLE -> R.string.setting_screen_toggle
    },
)

@Composable
internal fun SettingsTab.label(): String = stringResource(
    when (this) {
        SettingsTab.LANGUAGES -> R.string.tab_languages
        SettingsTab.KEYBOARD -> R.string.tab_keyboard
        SettingsTab.MOUSE -> R.string.tab_mouse
        SettingsTab.SCREEN -> R.string.tab_screen
        SettingsTab.ABOUT -> R.string.tab_about
    },
)

@Composable
internal fun onOff(value: Boolean): String = stringResource(if (value) R.string.setting_on else R.string.setting_off)

/** Integers as-is, fractions with two decimals in the interface locale (1,50× in Portuguese). */
internal fun formatSetting(setting: NumericSetting, value: Double, locale: Locale): String {
    val number = if (setting.isInteger) value.toLong().toString() else String.format(locale, "%.2f", value)
    return when (setting.unit) {
        SettingUnit.MILLIS -> "$number ms"
        SettingUnit.TIMES -> "$number×"
        SettingUnit.NONE -> number
    }
}

@Composable
internal fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** "F24" rather than "KEYCODE_F24". */
internal fun keyName(code: Int): String = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
