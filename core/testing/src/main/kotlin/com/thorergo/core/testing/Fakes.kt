package com.thorergo.core.testing

import com.thorergo.core.data.SettingsRepository
import com.thorergo.core.display.DisplayInfo
import com.thorergo.core.display.DisplayRepository
import com.thorergo.core.display.DualDisplays
import com.thorergo.core.injection.InjectorStatus
import com.thorergo.core.injection.InputInjector
import com.thorergo.core.injection.PanelPower
import com.thorergo.core.injection.PanelPowerController
import com.thorergo.core.injection.PointerModeConfig
import com.thorergo.core.injection.ShizukuState
import com.thorergo.core.model.KeyCode
import com.thorergo.core.model.Modifier
import com.thorergo.core.model.MouseButton
import com.thorergo.core.model.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class FakeSettingsRepository(initial: Settings = Settings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<Settings> = state

    /** When set, the next updates fail and leave the stored value unchanged. */
    var failWrites = false

    val current: Settings get() = state.value

    override suspend fun update(transform: (Settings) -> Settings): Result<Settings> {
        if (failWrites) return Result.failure(IllegalStateException("disk full"))
        state.value = transform(state.value).normalized()
        return Result.success(state.value)
    }
}

/** Records every injection, in order, as a readable string. */
class FakeInputInjector(
    status: InjectorStatus = InjectorStatus(ShizukuState.Ready, accessibility = true),
) : InputInjector {
    val calls = mutableListOf<String>()
    var succeed = true
    private val _status = MutableStateFlow(status)
    override val status: StateFlow<InjectorStatus> = _status

    fun setStatus(status: InjectorStatus) {
        _status.value = status
    }

    private fun record(call: String): Boolean {
        calls += call
        return succeed
    }

    override suspend fun chord(code: KeyCode, mods: Set<Modifier>) =
        record((mods.sortedBy { it.ordinal }.map { it.name } + code.name).joinToString("+"))

    override suspend fun commitText(text: String) = record("text:$text")

    override suspend fun pointerMove(x: Float, y: Float) = record("move:${x.toInt()},${y.toInt()}")

    override suspend fun button(button: MouseButton, down: Boolean, x: Float, y: Float) =
        record("${button.name.lowercase()}:${if (down) "down" else "up"}")

    override suspend fun scroll(vertical: Int, horizontal: Int, x: Float, y: Float) = record("scroll:$vertical,$horizontal")

    override suspend fun releaseAll() {
        calls += "releaseAll"
    }

    override fun reconnect() {
        calls += "reconnect"
    }
}

class FakeDisplayRepository(
    initial: DualDisplays? = DualDisplays(DisplayInfo(0, "Main", 1080, 1920), DisplayInfo(1, "Lower", 1240, 1080)),
) : DisplayRepository {
    val state = MutableStateFlow(initial)
    override val displays: Flow<DualDisplays?> = state
}

class FakePointerModeConfig(override var pointerAsTouch: Boolean = false) : PointerModeConfig

class FakePanelPowerController : PanelPowerController {
    private val _power = MutableStateFlow(PanelPower.ON)
    override val power: StateFlow<PanelPower> = _power
    var hardwareAvailable = true

    override suspend fun toggle(nowMs: Long) = if (_power.value == PanelPower.ON) turnOff() else turnOn()

    override suspend fun turnOff() {
        _power.value = if (hardwareAvailable) PanelPower.OFF else PanelPower.BLACKOUT
    }

    override suspend fun turnOn() {
        _power.value = PanelPower.ON
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}
