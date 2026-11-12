package com.thorergo.core.injection

import com.thorergo.core.model.KeyCode
import com.thorergo.core.model.Modifier
import com.thorergo.core.model.MouseButton
import kotlinx.coroutines.flow.StateFlow

/** What the device can do right now, given which backends are connected. */
data class InjectorStatus(
    val shizuku: ShizukuState = ShizukuState.NotRunning,
    val accessibility: Boolean = false,
) {
    val canSendKeys: Boolean get() = shizuku == ShizukuState.Ready || accessibility
    val canCommitText: Boolean get() = accessibility || shizuku == ShizukuState.Ready
    val canMovePointer: Boolean get() = shizuku == ShizukuState.Ready || accessibility
    val canPowerOffPanel: Boolean get() = shizuku == ShizukuState.Ready

    /** Shizuku reaches every app, including games; Accessibility only reaches focused editors. */
    val fullFidelity: Boolean get() = shizuku == ShizukuState.Ready
}

sealed interface ShizukuState {
    data object NotInstalled : ShizukuState

    data object NotRunning : ShizukuState

    data object PermissionRequired : ShizukuState

    data object Connecting : ShizukuState

    data object Ready : ShizukuState
}

/** Sends input to the app on the upper display. Calls are serialised by the caller. */
interface InputInjector {
    val status: StateFlow<InjectorStatus>

    /** Presses [mods] then [code], waits briefly, and releases in reverse order. */
    suspend fun chord(code: KeyCode, mods: Set<Modifier> = emptySet()): Boolean

    /** Commits Unicode text into the focused editor. */
    suspend fun commitText(text: String): Boolean

    /** Moves the pointer to an absolute position on the upper display. */
    suspend fun pointerMove(x: Float, y: Float): Boolean

    suspend fun button(button: MouseButton, down: Boolean, x: Float, y: Float): Boolean

    /** Wheel motion in high-resolution units (120 = one notch); positive [vertical] scrolls up. */
    suspend fun scroll(vertical: Int, horizontal: Int, x: Float, y: Float): Boolean

    /** Releases every key and button this injector is holding. */
    suspend fun releaseAll()

    /** Rebinds the backends after a failure. */
    fun reconnect()
}
