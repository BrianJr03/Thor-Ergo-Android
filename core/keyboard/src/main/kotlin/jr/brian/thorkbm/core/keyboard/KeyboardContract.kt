package jr.brian.thorkbm.core.keyboard

import jr.brian.thorkbm.core.keyboard.layout.KeySpec
import jr.brian.thorkbm.core.keyboard.layout.KeyboardLayout
import jr.brian.thorkbm.core.keyboard.layout.LayoutEngine
import jr.brian.thorkbm.core.keyboard.layout.Rect
import jr.brian.thorkbm.core.keyboard.output.OutputEvent
import jr.brian.thorkbm.core.model.Modifier
import jr.brian.thorkbm.core.model.ModifierState
import jr.brian.thorkbm.core.model.MouseButton
import jr.brian.thorkbm.core.model.Page
import jr.brian.thorkbm.core.model.PanelView
import jr.brian.thorkbm.core.model.Settings

/** One finger on a key. Ownership is fixed at touch-down; sliding off cancels instead of retargeting. */
data class Press(
    val serial: Long,
    val key: KeySpec,
    /** Modifiers latched when the finger landed; they travel with this key. */
    val mods: Set<Modifier>,
    val upper: Boolean,
    val caps: Boolean,
    val inside: Boolean = true,
    val committed: Boolean = false,
    val cancelled: Boolean = false,
)

data class AccentPopup(
    val pointerId: Long,
    val frame: Rect,
    val options: List<KeySpec>,
    val selected: Int? = null,
)

data class ImeState(
    val preedit: String = "",
    val candidates: List<String> = emptyList(),
    val revision: Int = 0,
    val page: Int = 0,
) {
    val pageCount: Int get() = maxOf(1, (candidates.size + LayoutEngine.CANDIDATES_PER_PAGE - 1) / LayoutEngine.CANDIDATES_PER_PAGE)

    fun candidateAt(slot: Int): String? = candidates.getOrNull(page * LayoutEngine.CANDIDATES_PER_PAGE + slot)
}

enum class Overlay { NONE, MENU, SETTINGS }

data class KeyboardState(
    val settings: Settings = Settings(),
    val page: Page = Page.ABC,
    val previousPage: Page = Page.ABC,
    val overlay: Overlay = Overlay.NONE,
    val modifiers: Map<Modifier, ModifierState> = NO_MODIFIERS,
    val lastModifierTap: Map<Modifier, Long> = emptyMap(),
    val presses: Map<Long, Press> = emptyMap(),
    val popup: AccentPopup? = null,
    val ime: ImeState = ImeState(),
    val nextSerial: Long = 0,
) {
    val view: PanelView get() = settings.view

    val layout: KeyboardLayout get() = LayoutEngine.build(page, view, settings.language)

    /** Keys that currently accept touches: only the header while settings cover the keyboard. */
    val activeKeys: List<KeySpec> get() = if (overlay == Overlay.SETTINGS) LayoutEngine.header() else layout.keys

    val pad: Rect? get() = if (overlay == Overlay.NONE) layout.pad else null

    val shift: ModifierState get() = modifier(Modifier.SHIFT)

    val pressedKeyIds: Set<String>
        get() = presses.values.filter { it.inside && !it.cancelled }.mapTo(mutableSetOf()) { it.key.id }

    fun modifier(modifier: Modifier): ModifierState = modifiers[modifier] ?: ModifierState.OFF

    fun activeModifiers(): Set<Modifier> = Modifier.entries.filterTo(linkedSetOf()) { modifier(it) != ModifierState.OFF }

    companion object {
        val NO_MODIFIERS: Map<Modifier, ModifierState> = Modifier.entries.associateWith { ModifierState.OFF }
    }
}

sealed interface KeyboardTimer {
    data class LongPress(val serial: Long) : KeyboardTimer

    data class Repeat(val serial: Long) : KeyboardTimer
}

/** Inputs to [KeyboardReducer]. Coordinates are logical panel units; times are monotonic milliseconds. */
sealed interface KeyboardEvent {
    data class Down(val pointerId: Long, val x: Float, val y: Float, val timeMs: Long) : KeyboardEvent

    data class Move(val pointerId: Long, val x: Float, val y: Float) : KeyboardEvent

    data class Up(val pointerId: Long, val timeMs: Long) : KeyboardEvent

    data class Cancel(val pointerId: Long) : KeyboardEvent

    data class TimerFired(val timer: KeyboardTimer) : KeyboardEvent

    data class ImeChanged(val preedit: String, val candidates: List<String>, val revision: Int) : KeyboardEvent

    data class SettingsChanged(val settings: Settings) : KeyboardEvent

    data class SelectView(val view: PanelView) : KeyboardEvent

    data object DismissMenu : KeyboardEvent

    data object CloseSettings : KeyboardEvent

    data object ReleaseAll : KeyboardEvent

    data object Reconnect : KeyboardEvent
}

/** Side effects requested by the reducer; the host executes them in order. */
sealed interface KeyboardCommand {
    data class Emit(val event: OutputEvent) : KeyboardCommand

    data class StartTimer(val timer: KeyboardTimer, val delayMs: Long) : KeyboardCommand

    data class CancelTimer(val timer: KeyboardTimer) : KeyboardCommand

    data class Mouse(val button: MouseButton, val pointerId: Long, val down: Boolean) : KeyboardCommand

    data class PersistView(val view: PanelView) : KeyboardCommand

    /** Drop trackpad contacts and held mouse buttons (page or menu change). */
    data object ReleasePointer : KeyboardCommand

    /** Release everything: pointer, held keys and pending output. */
    data object ResetInput : KeyboardCommand

    /** Re-establish the injection backends. */
    data object Reconnect : KeyboardCommand

    data object Hide : KeyboardCommand

    /** A key was touched; the host may play haptic feedback. */
    data object KeyFeedback : KeyboardCommand
}

data class Transition(val state: KeyboardState, val commands: List<KeyboardCommand> = emptyList())
