package jr.brian.thorkbm.feature.panel

import com.google.common.truth.Truth.assertThat
import jr.brian.thorkbm.core.injection.PanelPower
import jr.brian.thorkbm.core.injection.accessibility.AccessibilityBridge
import jr.brian.thorkbm.core.keyboard.layout.LayoutEngine
import jr.brian.thorkbm.core.keyboard.output.OutputRouter
import jr.brian.thorkbm.core.model.Language
import jr.brian.thorkbm.core.model.PanelView
import jr.brian.thorkbm.core.model.Settings
import jr.brian.thorkbm.core.pinyin.PinyinComposer
import jr.brian.thorkbm.core.pinyin.PinyinDictionary
import jr.brian.thorkbm.core.testing.FakeDisplayRepository
import jr.brian.thorkbm.core.testing.FakeInputInjector
import jr.brian.thorkbm.core.testing.FakePanelPowerController
import jr.brian.thorkbm.core.testing.FakePointerModeConfig
import jr.brian.thorkbm.core.testing.FakeSettingsRepository
import jr.brian.thorkbm.core.testing.MainDispatcherRule
import jr.brian.thorkbm.feature.panel.di.MonotonicClock
import jr.brian.thorkbm.feature.panel.input.CursorController
import jr.brian.thorkbm.feature.settings.SettingsStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PanelViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeSettingsRepository(Settings(language = Language.PT_PT, haptics = false))
    private val injector = FakeInputInjector()
    private val power = FakePanelPowerController()
    private val bridge = AccessibilityBridge()
    private val cursor = CursorController()
    private val pointerMode = FakePointerModeConfig()
    private var nextPointer = 0L

    private fun TestScope.viewModel() = PanelViewModel(
        repository = repository,
        injector = injector,
        pointerMode = pointerMode,
        power = power,
        bridge = bridge,
        cursor = cursor,
        clock = MonotonicClock { currentTime },
        dictionary = PinyinDictionary(emptyMap(), emptyMap()),
        displays = FakeDisplayRepository(),
        router = OutputRouter(PinyinComposer(PinyinDictionary(emptyMap(), emptyMap()))),
        settingsFactory = object : SettingsStateHolder.Factory {
            override fun create(scope: CoroutineScope) = SettingsStateHolder(scope, repository, bridge, power, injector)
        },
        background = main.dispatcher,
    ).also {
        runCurrent()
        // Loading the stored layout resets input once; tests start after that.
        injector.calls.clear()
    }

    private fun PanelViewModel.tapKey(id: String) {
        val key = state.value.keyboard.activeKeys.first { it.id == id }
        val pointer = nextPointer++
        onIntent(PanelIntent.Touch(PanelTouch.Down(pointer, key.rect.centerX, key.rect.centerY, 0)))
        onIntent(PanelIntent.Touch(PanelTouch.Up(pointer, 10)))
    }

    private val pad = LayoutEngine.PAD_COMBINED

    @Test
    fun `typing a portuguese letter commits it as text`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.tapKey("char:ç")
        runCurrent()
        assertThat(injector.calls).containsExactly("text:ç")
    }

    @Test
    fun `dragging on the pad moves the cursor and the pointer`() = runTest(main.dispatcher) {
        val vm = viewModel()
        val start = cursor.cursor.value
        vm.onIntent(PanelIntent.Touch(PanelTouch.Down(1, pad.x + 50, pad.y + 50, 0)))
        vm.onIntent(PanelIntent.Touch(PanelTouch.Move(1, pad.x + 150, pad.y + 50, 40)))
        runCurrent()
        assertThat(cursor.cursor.value.x).isGreaterThan(start.x)
        assertThat(injector.calls.single()).startsWith("move:")
    }

    @Test
    fun `tapping the pad clicks`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onIntent(PanelIntent.Touch(PanelTouch.Down(1, pad.centerX, pad.centerY, 0)))
        vm.onIntent(PanelIntent.Touch(PanelTouch.Up(1, 80)))
        advanceTimeBy(50)
        assertThat(injector.calls).containsExactly("left:down", "left:up").inOrder()
    }

    @Test
    fun `holding the header button drags instead of clicking`() = runTest(main.dispatcher) {
        val vm = viewModel()
        val left = vm.state.value.keyboard.activeKeys.first { it.id == "mouse:left" }
        vm.onIntent(PanelIntent.Touch(PanelTouch.Down(1, left.rect.centerX, left.rect.centerY, 0)))
        vm.onIntent(PanelIntent.Touch(PanelTouch.Down(2, pad.centerX, pad.centerY, 0)))
        vm.onIntent(PanelIntent.Touch(PanelTouch.Up(2, 80)))
        vm.onIntent(PanelIntent.Touch(PanelTouch.Up(1, 100)))
        advanceTimeBy(50)
        assertThat(injector.calls).containsExactly("left:down", "left:up").inOrder()
    }

    @Test
    fun `hide collapses the panel and releases input`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.tapKey("header:hide")
        runCurrent()
        assertThat(vm.state.value.collapsed).isTrue()
        assertThat(injector.calls).contains("releaseAll")
        vm.onIntent(PanelIntent.Expand)
        assertThat(vm.state.value.collapsed).isFalse()
    }

    @Test
    fun `a touch on the dark panel only wakes it`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onIntent(PanelIntent.TurnScreenOff)
        runCurrent()
        assertThat(vm.state.value.power).isEqualTo(PanelPower.OFF)
        injector.calls.clear()
        vm.tapKey("char:a")
        runCurrent()
        assertThat(vm.state.value.power).isEqualTo(PanelPower.ON)
        assertThat(injector.calls.filter { it.startsWith("text:") }).isEmpty()
    }

    @Test
    fun `the bound hardware key toggles the lower screen when enabled`() = runTest(main.dispatcher) {
        repository.update { it.copy(screenToggleEnabled = true, screenToggleKeyCode = 131) }
        viewModel()
        assertThat(bridge.boundKeyCode).isEqualTo(131)
        bridge.onHardwareKey(131)
        runCurrent()
        assertThat(power.power.value).isEqualTo(PanelPower.OFF)
    }

    @Test
    fun `choosing a view persists it`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onIntent(PanelIntent.SelectView(PanelView.PAD))
        runCurrent()
        assertThat(repository.current.view).isEqualTo(PanelView.PAD)
        assertThat(vm.state.value.keyboard.layout.pad).isEqualTo(LayoutEngine.PAD_FULL)
    }

    @Test
    fun `touch mode follows the setting`() = runTest(main.dispatcher) {
        viewModel()
        repository.update { it.copy(pointerAsTouch = true) }
        runCurrent()
        assertThat(pointerMode.pointerAsTouch).isTrue()
    }
}
