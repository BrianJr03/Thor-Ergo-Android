package jr.brian.thorkbm.feature.setup

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import jr.brian.thorkbm.core.injection.InjectorStatus
import jr.brian.thorkbm.core.injection.ShizukuState
import jr.brian.thorkbm.core.injection.shizuku.ShizukuAccess
import jr.brian.thorkbm.core.testing.FakeDisplayRepository
import jr.brian.thorkbm.core.testing.FakeInputInjector
import jr.brian.thorkbm.core.testing.FakeSettingsRepository
import jr.brian.thorkbm.core.testing.MainDispatcherRule
import jr.brian.thorkbm.feature.panel.service.PanelControl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class FakePermissions(var overlay: Boolean = false, var notifications: Boolean = false) : SystemPermissions {
        override fun canDrawOverlays() = overlay

        override fun canPostNotifications() = notifications
    }

    private class FakeShizuku(initial: ShizukuState) : ShizukuAccess {
        override val state = MutableStateFlow(initial)
        var permissionRequests = 0

        override fun requestPermission() {
            permissionRequests++
        }

        override fun refresh() = Unit
    }

    private class FakePanel : PanelControl {
        override val running = MutableStateFlow(false)

        override fun start() {
            running.value = true
        }

        override fun stop() {
            running.value = false
        }

        var shown = 0

        override fun show() {
            shown++
        }
    }

    private val permissions = FakePermissions()
    private val shizuku = FakeShizuku(ShizukuState.PermissionRequired)
    private val panel = FakePanel()
    private val injector = FakeInputInjector(InjectorStatus(ShizukuState.PermissionRequired, accessibility = false))
    private val displays = FakeDisplayRepository()

    /** The state is shared while subscribed, as the screen would be. */
    private fun TestScope.viewModel() = SetupViewModel(permissions, shizuku, panel, injector, displays, FakeSettingsRepository()).also { vm ->
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
    }

    @Test
    fun `the panel cannot start without overlay permission and a backend`() = runTest(main.dispatcher) {
        val vm = viewModel()
        assertThat(vm.state.value.canStart).isFalse()
        assertThat(vm.state.value.needsBackend).isTrue()
        vm.onIntent(SetupIntent.TogglePanel)
        assertThat(panel.running.value).isFalse()
    }

    @Test
    fun `granting permissions is picked up on refresh`() = runTest(main.dispatcher) {
        val vm = viewModel()
        permissions.overlay = true
        injector.setStatus(InjectorStatus(ShizukuState.Ready, accessibility = false))
        vm.onIntent(SetupIntent.Refresh)
        runCurrent()
        assertThat(vm.state.value.canStart).isTrue()
        vm.onIntent(SetupIntent.TogglePanel)
        runCurrent()
        assertThat(vm.state.value.panelRunning).isTrue()
    }

    @Test
    fun `accessibility alone starts in limited mode`() = runTest(main.dispatcher) {
        permissions.overlay = true
        injector.setStatus(InjectorStatus(ShizukuState.NotRunning, accessibility = true))
        val vm = viewModel()
        assertThat(vm.state.value.canStart).isTrue()
        assertThat(vm.state.value.limited).isTrue()
    }

    @Test
    fun `no second display blocks starting`() = runTest(main.dispatcher) {
        permissions.overlay = true
        injector.setStatus(InjectorStatus(ShizukuState.Ready, accessibility = true))
        displays.state.value = null
        val vm = viewModel()
        assertThat(vm.state.value.canStart).isFalse()
        assertThat(vm.state.value.hasSecondDisplay).isFalse()
    }

    @Test
    fun `opening the app brings a hidden panel back`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onIntent(SetupIntent.Resumed)
        assertThat(panel.shown).isEqualTo(1)
        vm.onIntent(SetupIntent.Refresh)
        assertThat(panel.shown).isEqualTo(1)
    }

    @Test
    fun `shizuku setup asks for whatever is missing`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onIntent(SetupIntent.SetUpShizuku)
        assertThat(shizuku.permissionRequests).isEqualTo(1)
        shizuku.state.value = ShizukuState.NotInstalled
        vm.effect.test {
            vm.onIntent(SetupIntent.SetUpShizuku)
            assertThat(awaitItem()).isEqualTo(SetupEffect.OpenShizukuDownload)
            vm.onIntent(SetupIntent.GrantOverlay)
            assertThat(awaitItem()).isEqualTo(SetupEffect.OpenOverlaySettings)
        }
    }
}
