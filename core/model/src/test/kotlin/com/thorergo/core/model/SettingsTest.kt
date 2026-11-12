package com.thorergo.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsTest {
    @Test
    fun `steps clamp to the range`() {
        var settings = Settings()
        repeat(20) { settings = settings.stepped(NumericSetting.LONG_PRESS_MS, -1) }
        assertThat(settings.longPressMs).isEqualTo(250)
        repeat(200) { settings = settings.stepped(NumericSetting.SENSITIVITY, 1) }
        assertThat(settings.sensitivity).isEqualTo(5.0)
    }

    @Test
    fun `fractional steps round to four decimals`() {
        val settings = Settings(sensitivity = 1.5).stepped(NumericSetting.SENSITIVITY, 1)
        assertThat(settings.sensitivity).isEqualTo(1.6)
        assertThat(Settings(scrollScale = 0.06).stepped(NumericSetting.SCROLL_SCALE, -1).scrollScale).isEqualTo(0.01)
    }

    @Test
    fun `normalisation repairs out-of-range and non-finite values`() {
        val repaired = Settings(longPressMs = -1, friction = 100_000, sensitivity = Double.NaN, scrollScale = 0.0).normalized()
        assertThat(repaired.longPressMs).isEqualTo(250)
        assertThat(repaired.friction).isEqualTo(10)
        assertThat(repaired.sensitivity).isEqualTo(1.5)
        assertThat(repaired.scrollScale).isEqualTo(0.01)
    }

    @Test
    fun `defaults are already normal`() {
        assertThat(Settings().normalized()).isEqualTo(Settings())
    }

    @Test
    fun `the interface follows the keyboard unless chosen`() {
        assertThat(Settings(language = Language.FR_FR).effectiveUiLanguage).isEqualTo(Language.FR_FR)
        assertThat(Settings(language = Language.FR_FR, uiLanguage = Language.DE_DE).effectiveUiLanguage).isEqualTo(Language.DE_DE)
    }

    @Test
    fun `toggles flip`() {
        assertThat(Settings().toggled(ToggleSetting.GLIDE).glide).isFalse()
        assertThat(Settings().toggled(ToggleSetting.SCREEN_TOGGLE).screenToggleEnabled).isTrue()
    }
}
