package com.thorergo.core.injection

import com.thorergo.core.display.DisplayRepository
import com.thorergo.core.injection.di.ApplicationScope
import com.thorergo.core.injection.di.InjectionDispatcher
import com.thorergo.core.injection.shizuku.ShizukuConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class PanelPower {
    ON,

    /** The lower panel is powered off through Shizuku; the upper screen is unaffected. */
    OFF,

    /** No privileged access: the panel shows a full black overlay (AMOLED pixels are off). */
    BLACKOUT,
}

/** Turns only the lower panel off and on, never the whole device. */
interface PanelPowerController {
    val power: StateFlow<PanelPower>

    /** Toggles with a debounce, so one physical press never flips twice. */
    suspend fun toggle(nowMs: Long)

    suspend fun turnOff()

    suspend fun turnOn()
}

@Singleton
internal class ShizukuPanelPowerController @Inject constructor(
    private val shizuku: ShizukuConnection,
    displays: DisplayRepository,
    @ApplicationScope scope: CoroutineScope,
    @InjectionDispatcher private val dispatcher: CoroutineDispatcher,
) : PanelPowerController {
    private val _power = MutableStateFlow(PanelPower.ON)
    override val power: StateFlow<PanelPower> = _power.asStateFlow()

    private val lowerDisplay: StateFlow<Int?> = displays.displays.map { it?.lower?.id }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val mutex = Mutex()
    private var lastToggle = Long.MIN_VALUE / 2

    override suspend fun toggle(nowMs: Long) {
        if (nowMs - lastToggle < DEBOUNCE_MS) return
        lastToggle = nowMs
        if (_power.value == PanelPower.ON) turnOff() else turnOn()
    }

    override suspend fun turnOff() = mutex.withLock {
        if (_power.value != PanelPower.ON) return@withLock
        _power.value = if (setHardwarePower(on = false)) PanelPower.OFF else PanelPower.BLACKOUT
    }

    override suspend fun turnOn() = mutex.withLock {
        if (_power.value == PanelPower.OFF) setHardwarePower(on = true)
        _power.value = PanelPower.ON
    }

    private suspend fun setHardwarePower(on: Boolean): Boolean = withContext(dispatcher) {
        val display = lowerDisplay.value ?: return@withContext false
        shizuku.service?.setDisplayPower(display, on) ?: false
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
