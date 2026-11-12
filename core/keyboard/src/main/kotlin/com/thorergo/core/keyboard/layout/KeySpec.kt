package com.thorergo.core.keyboard.layout

import com.thorergo.core.model.DeadKey
import com.thorergo.core.model.KeyCode
import com.thorergo.core.model.Modifier
import com.thorergo.core.model.MouseButton
import com.thorergo.core.model.Page

/** What a key does. The reducer interprets these; the UI only draws them. */
sealed interface KeyAction {
    /** Types [char]. [code] is its US physical position, used for shortcuts and AltGr. */
    data class Text(val char: String, val code: KeyCode?) : KeyAction

    data class Key(val code: KeyCode) : KeyAction

    data class ModifierKey(val modifier: Modifier) : KeyAction

    data object CapsLock : KeyAction

    data class GoToPage(val page: Page) : KeyAction

    data class Dead(val dead: DeadKey) : KeyAction

    data class Mouse(val button: MouseButton) : KeyAction

    data object Fn : KeyAction

    data object Mode : KeyAction

    data object Settings : KeyAction

    data object Hide : KeyAction

    data class Candidate(val slot: Int) : KeyAction

    data class CandidatePage(val delta: Int) : KeyAction

    data class Accent(val char: String) : KeyAction
}

/** Interface strings the UI resolves from resources; everything else is drawn literally. */
enum class LabelId {
    LEFT_CLICK, RIGHT_CLICK, MODE, HIDE, SPACE, ENTER, SYMBOLS,
    CAPS_LOCK, NUM_LOCK, SCROLL_LOCK, INSERT, DELETE, HOME, END, PAGE_UP, PAGE_DOWN,
}

sealed interface KeyLabel {
    data class Literal(val text: String) : KeyLabel

    data class Localized(val id: LabelId) : KeyLabel

    data object None : KeyLabel
}

enum class KeyIcon { MOUSE_LEFT, MOUSE_RIGHT, DROPDOWN, GEAR, SHIFT, BACKSPACE, ENTER, LEFT, UP, DOWN, RIGHT }

enum class KeyStyle { NORMAL, ACCENT, SMALL }

data class KeySpec(
    val id: String,
    val action: KeyAction,
    val label: KeyLabel,
    val rect: Rect = Rect(0f, 0f, 0f, 0f),
    val icon: KeyIcon? = null,
    val style: KeyStyle = KeyStyle.NORMAL,
)

data class KeyboardLayout(
    val keys: List<KeySpec>,
    /** The trackpad surface, or `null` when this view has none. */
    val pad: Rect?,
) {
    private val byId = keys.associateBy { it.id }

    /** Later keys sit on top, so they win a shared point. */
    fun hit(x: Float, y: Float): KeySpec? = keys.lastOrNull { it.rect.contains(x, y) }

    fun find(id: String): KeySpec? = byId[id]
}
