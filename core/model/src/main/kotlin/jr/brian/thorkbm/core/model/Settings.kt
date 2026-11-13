package jr.brian.thorkbm.core.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Every user preference. Defaults match Thor Ergo 1.0. */
@Serializable
data class Settings(
    val language: Language = Language.EN_US,
    /** `null` means the interface follows the keyboard [language]. */
    val uiLanguage: Language? = null,
    val view: PanelView = PanelView.COMBINED,
    val longPressMs: Int = 400,
    val repeatEnabled: Boolean = true,
    val repeatDelayMs: Int = 350,
    val repeatIntervalMs: Int = 60,
    val capsWindowMs: Int = 350,
    val haptics: Boolean = true,
    /** Slide across letters to type a word (English (US) only); off keeps "leaving a key cancels it". */
    val swipeTyping: Boolean = false,
    val sensitivity: Double = 1.5,
    val scrollSpeed: Int = 3,
    val scrollScale: Double = 1.0,
    val tapToClick: Boolean = true,
    val glide: Boolean = true,
    val friction: Int = 5,
    /** Inject clicks and drags as screen touches, for games that ignore mouse input. */
    val pointerAsTouch: Boolean = false,
    /** Fade the upper-screen pointer out after a few seconds without trackpad use. */
    val autoHidePointer: Boolean = false,
    /** Pure-black backgrounds and near-black keys for AMOLED screens. */
    val oledBlack: Boolean = false,
    /** Show the small "Open keyboard" button while the panel is hidden; off means reopen from the app. */
    val showRestoreButton: Boolean = true,
    /** Which corner of the lower screen the "Open keyboard" button sits in. */
    val restoreButtonCorner: ScreenCorner = ScreenCorner.BOTTOM_RIGHT,
    /** Set once the panel has been started from setup; the launcher icon then opens the keyboard directly. */
    val setupCompleted: Boolean = false,
    val screenToggleEnabled: Boolean = false,
    /** Android key code bound to the lower-screen toggle, or `null` for on-screen control only. */
    val screenToggleKeyCode: Int? = null,
) {
    val effectiveUiLanguage: Language get() = uiLanguage ?: language

    operator fun get(setting: NumericSetting): Double = setting.read(this)

    operator fun get(setting: ToggleSetting): Boolean = setting.read(this)

    fun with(setting: NumericSetting, value: Double): Settings = setting.write(this, setting.clamp(value))

    fun with(setting: ToggleSetting, value: Boolean): Settings = setting.write(this, value)

    /** Moves a numeric setting by whole [steps] of its increment, clamped to its range. */
    fun stepped(setting: NumericSetting, steps: Int): Settings = with(setting, get(setting) + steps * setting.step)

    fun toggled(setting: ToggleSetting): Settings = with(setting, !get(setting))

    /** Clamps every numeric value; use after reading untrusted storage. */
    fun normalized(): Settings = NumericSetting.entries.fold(this) { acc, s -> acc.with(s, s.read(acc)) }
}

enum class ScreenCorner {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

    val isTop: Boolean get() = this == TOP_LEFT || this == TOP_RIGHT

    val isLeft: Boolean get() = this == TOP_LEFT || this == BOTTOM_LEFT

    /** The next corner in [entries] order, wrapping around; the settings row cycles with it. */
    fun next(): ScreenCorner = entries[(ordinal + 1) % entries.size]
}

enum class SettingUnit { NONE, MILLIS, TIMES }

/** Range metadata shared by normalisation, the ± steppers and their labels. */
enum class NumericSetting(
    val min: Double,
    val max: Double,
    val step: Double,
    val unit: SettingUnit,
    internal val read: (Settings) -> Double,
    internal val write: (Settings, Double) -> Settings,
) {
    LONG_PRESS_MS(250.0, 900.0, 50.0, SettingUnit.MILLIS, { it.longPressMs.toDouble() }, { s, v -> s.copy(longPressMs = v.roundToInt()) }),
    REPEAT_DELAY_MS(150.0, 800.0, 25.0, SettingUnit.MILLIS, { it.repeatDelayMs.toDouble() }, { s, v -> s.copy(repeatDelayMs = v.roundToInt()) }),
    REPEAT_INTERVAL_MS(20.0, 200.0, 10.0, SettingUnit.MILLIS, { it.repeatIntervalMs.toDouble() }, { s, v -> s.copy(repeatIntervalMs = v.roundToInt()) }),
    CAPS_WINDOW_MS(150.0, 800.0, 25.0, SettingUnit.MILLIS, { it.capsWindowMs.toDouble() }, { s, v -> s.copy(capsWindowMs = v.roundToInt()) }),
    SENSITIVITY(0.2, 5.0, 0.1, SettingUnit.TIMES, { it.sensitivity }, { s, v -> s.copy(sensitivity = v) }),
    SCROLL_SPEED(1.0, 5.0, 1.0, SettingUnit.NONE, { it.scrollSpeed.toDouble() }, { s, v -> s.copy(scrollSpeed = v.roundToInt()) }),
    SCROLL_SCALE(0.01, 1.0, 0.05, SettingUnit.TIMES, { it.scrollScale }, { s, v -> s.copy(scrollScale = v) }),
    FRICTION(1.0, 10.0, 1.0, SettingUnit.NONE, { it.friction.toDouble() }, { s, v -> s.copy(friction = v.roundToInt()) }),
    ;

    val isInteger: Boolean get() = step >= 1.0

    /** Clamps to the range; integer settings round to whole numbers, others to four decimals. */
    fun clamp(value: Double): Double {
        if (!value.isFinite()) return read(Settings())
        val bounded = value.coerceIn(min, max)
        return if (isInteger) bounded.roundToLong().toDouble() else (bounded * 10_000).roundToLong() / 10_000.0
    }
}

enum class ToggleSetting(
    internal val read: (Settings) -> Boolean,
    internal val write: (Settings, Boolean) -> Settings,
) {
    REPEAT_ENABLED({ it.repeatEnabled }, { s, v -> s.copy(repeatEnabled = v) }),
    HAPTICS({ it.haptics }, { s, v -> s.copy(haptics = v) }),
    SWIPE_TYPING({ it.swipeTyping }, { s, v -> s.copy(swipeTyping = v) }),
    TAP_TO_CLICK({ it.tapToClick }, { s, v -> s.copy(tapToClick = v) }),
    GLIDE({ it.glide }, { s, v -> s.copy(glide = v) }),
    POINTER_AS_TOUCH({ it.pointerAsTouch }, { s, v -> s.copy(pointerAsTouch = v) }),
    AUTO_HIDE_POINTER({ it.autoHidePointer }, { s, v -> s.copy(autoHidePointer = v) }),
    SHOW_RESTORE_BUTTON({ it.showRestoreButton }, { s, v -> s.copy(showRestoreButton = v) }),
    SCREEN_TOGGLE({ it.screenToggleEnabled }, { s, v -> s.copy(screenToggleEnabled = v) }),
    OLED_BLACK({ it.oledBlack }, { s, v -> s.copy(oledBlack = v) }),
}
