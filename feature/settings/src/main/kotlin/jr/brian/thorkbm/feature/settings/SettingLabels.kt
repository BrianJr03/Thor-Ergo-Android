package jr.brian.thorkbm.feature.settings

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import jr.brian.thorkbm.core.designsystem.R
import jr.brian.thorkbm.core.model.NumericSetting
import jr.brian.thorkbm.core.model.ScreenCorner
import jr.brian.thorkbm.core.model.SettingUnit
import jr.brian.thorkbm.core.model.ToggleSetting
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
        ToggleSetting.SWIPE_TYPING -> R.string.setting_swipe_typing
        ToggleSetting.TAP_TO_CLICK -> R.string.setting_tap_to_click
        ToggleSetting.GLIDE -> R.string.setting_glide
        ToggleSetting.POINTER_AS_TOUCH -> R.string.setting_pointer_as_touch
        ToggleSetting.AUTO_HIDE_POINTER -> R.string.setting_auto_hide_pointer
        ToggleSetting.SHOW_RESTORE_BUTTON -> R.string.setting_show_restore_button
        ToggleSetting.SCREEN_TOGGLE -> R.string.setting_screen_toggle
        ToggleSetting.OLED_BLACK -> R.string.setting_oled_black
        ToggleSetting.AUTO_CHECK_UPDATES -> R.string.setting_auto_check_updates
    },
)

@Composable
internal fun ScreenCorner.label(): String = stringResource(
    when (this) {
        ScreenCorner.TOP_LEFT -> R.string.corner_top_left
        ScreenCorner.TOP_RIGHT -> R.string.corner_top_right
        ScreenCorner.BOTTOM_LEFT -> R.string.corner_bottom_left
        ScreenCorner.BOTTOM_RIGHT -> R.string.corner_bottom_right
    },
)

@Composable
internal fun SettingsTab.label(): String = stringResource(
    when (this) {
        SettingsTab.LANGUAGES -> R.string.tab_languages
        SettingsTab.KEYBOARD -> R.string.tab_keyboard
        SettingsTab.MOUSE -> R.string.tab_mouse
        SettingsTab.SCREEN -> R.string.tab_screen
        SettingsTab.SETUP -> R.string.tab_setup
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
