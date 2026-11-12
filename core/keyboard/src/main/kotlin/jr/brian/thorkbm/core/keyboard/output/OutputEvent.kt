package jr.brian.thorkbm.core.keyboard.output

import jr.brian.thorkbm.core.model.DeadKey
import jr.brian.thorkbm.core.model.KeyCode
import jr.brian.thorkbm.core.model.Modifier

/** What the keyboard asks to be typed; [OutputRouter] turns it into injections for the current language. */
sealed interface OutputEvent {
    /**
     * A text key. [text] is already case-resolved; [code] is its US position (for shortcuts and AltGr).
     * [shift] is a one-shot Shift, [caps] a locked one.
     */
    data class Text(
        val text: String,
        val code: KeyCode?,
        val mods: Set<Modifier>,
        val caps: Boolean,
        val shift: Boolean,
    ) : OutputEvent

    data class Key(val code: KeyCode, val mods: Set<Modifier>) : OutputEvent

    /** An accent chosen from the long-press popup. */
    data class Literal(val text: String) : OutputEvent

    data class Dead(val dead: DeadKey) : OutputEvent

    /** Pick Pinyin candidate [index]; ignored if [revision] is stale. */
    data class Choose(val index: Int, val revision: Int) : OutputEvent
}
