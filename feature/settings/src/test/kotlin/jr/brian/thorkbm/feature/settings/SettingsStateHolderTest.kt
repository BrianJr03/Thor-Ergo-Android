package jr.brian.thorkbm.feature.settings

import com.google.common.truth.Truth.assertThat
import jr.brian.thorkbm.core.injection.InjectorStatus
import jr.brian.thorkbm.core.injection.PanelPower
import jr.brian.thorkbm.core.injection.ShizukuState
import jr.brian.thorkbm.core.injection.accessibility.AccessibilityBridge
import jr.brian.thorkbm.core.model.Language
import jr.brian.thorkbm.core.model.NumericSetting
import jr.brian.thorkbm.core.model.Settings
import jr.brian.thorkbm.core.model.ToggleSetting
import jr.brian.thorkbm.core.testing.FakeInputInjector
import jr.brian.thorkbm.core.testing.FakePanelPowerController
import jr.brian.thorkbm.core.testing.FakeSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStateHolderTest {
    private val repository = FakeSettingsRepository(Settings(language = Language.PT_PT))
    private val bridge = AccessibilityBridge()
    private val power = FakePanelPowerController()
    private val injector = FakeInputInjector(InjectorStatus(ShizukuState.NotRunning, accessibility = true))

    private fun TestScope.holder() = SettingsStateHolder(backgroundScope, repository, bridge, power, injector)

    @Test
    fun `steppers persist and clamp`() = runTest(UnconfinedTestDispatcher()) {
        val holder = holder()
        repeat(20) { holder.onIntent(SettingsIntent.Step(NumericSetting.LONG_PRESS_MS, -1)) }
        assertThat(repository.current.longPressMs).isEqualTo(250)
        assertThat(holder.state.value.note).isEqualTo(SettingsNote.SAVED)
    }

    @Test
    fun `a failed save keeps the old value and says so`() = runTest(UnconfinedTestDispatcher()) {
        val holder = holder()
        repository.failWrites = true
        holder.onIntent(SettingsIntent.OpenPicker(LanguagePicker.KEYBOARD))
        holder.onIntent(SettingsIntent.ChooseLanguage(Language.EN_US))
        assertThat(repository.current.language).isEqualTo(Language.PT_PT)
        assertThat(holder.state.value.note).isEqualTo(SettingsNote.SAVE_FAILED)
    }

    @Test
    fun `the interface picker can follow the keyboard again`() = runTest(UnconfinedTestDispatcher()) {
        val holder = holder()
        holder.onIntent(SettingsIntent.OpenPicker(LanguagePicker.INTERFACE))
        holder.onIntent(SettingsIntent.ChooseLanguage(Language.DE_DE))
        assertThat(repository.current.uiLanguage).isEqualTo(Language.DE_DE)
        holder.onIntent(SettingsIntent.OpenPicker(LanguagePicker.INTERFACE))
        holder.onIntent(SettingsIntent.ChooseLanguage(null))
        assertThat(repository.current.uiLanguage).isNull()
        assertThat(holder.state.value.picker).isNull()
    }

    @Test
    fun `toggles flip the stored value`() = runTest(UnconfinedTestDispatcher()) {
        holder().onIntent(SettingsIntent.Toggle(ToggleSetting.GLIDE))
        assertThat(repository.current.glide).isFalse()
    }

    @Test
    fun `learning a key binds the next hardware key and enables the toggle`() = runTest(UnconfinedTestDispatcher()) {
        val holder = holder()
        holder.onIntent(SettingsIntent.LearnKey)
        assertThat(holder.state.value.learningKey).isTrue()
        assertThat(bridge.onHardwareKey(131)).isTrue()
        runCurrent()
        assertThat(repository.current.screenToggleKeyCode).isEqualTo(131)
        assertThat(repository.current.screenToggleEnabled).isTrue()
        assertThat(holder.state.value.learningKey).isFalse()
        assertThat(bridge.onHardwareKey(132)).isFalse()
    }

    @Test
    fun `capabilities follow the injector status`() = runTest(UnconfinedTestDispatcher()) {
        val holder = holder()
        assertThat(holder.state.value.canBindKey).isTrue()
        assertThat(holder.state.value.canPowerOff).isFalse()
        injector.setStatus(InjectorStatus(ShizukuState.Ready, accessibility = false))
        assertThat(holder.state.value.canPowerOff).isTrue()
        assertThat(holder.state.value.canBindKey).isFalse()
    }

    @Test
    fun `turning the screen off goes to the power controller`() = runTest(UnconfinedTestDispatcher()) {
        holder().onIntent(SettingsIntent.TurnScreenOff)
        assertThat(power.power.value).isEqualTo(PanelPower.OFF)
    }

    @Test
    fun `values format in the interface locale`() {
        assertThat(formatSetting(NumericSetting.SENSITIVITY, 1.5, Locale.forLanguageTag("pt-PT"))).isEqualTo("1,50×")
        assertThat(formatSetting(NumericSetting.SENSITIVITY, 1.5, Locale.US)).isEqualTo("1.50×")
        assertThat(formatSetting(NumericSetting.LONG_PRESS_MS, 400.0, Locale.US)).isEqualTo("400 ms")
        assertThat(formatSetting(NumericSetting.FRICTION, 5.0, Locale.US)).isEqualTo("5")
    }
}
