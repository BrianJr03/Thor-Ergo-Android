package jr.brian.thorkbm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Page { ABC, NUMBERS, SYMBOLS, FN }

@Serializable
enum class PanelView {
    @SerialName("combined") COMBINED,
    @SerialName("keyboard") KEYBOARD,
    @SerialName("pad") PAD,
}

/** Declaration order is the order modifiers are pressed in a chord. */
enum class Modifier { SHIFT, CTRL, ALT, SUPER, ALTGR }

/** A latched modifier: one tap arms it for the next key, a quick second tap locks it. */
enum class ModifierState { OFF, ONE_SHOT, LOCKED }

enum class MouseButton { LEFT, RIGHT, MIDDLE }

/** XKB dead keys: the spacing form typed on its own and the combining mark used for composition. */
enum class DeadKey(val xkbName: String, val spacing: String, val combining: Char) {
    ACUTE("dead_acute", "´", '́'),
    GRAVE("dead_grave", "`", '̀'),
    TILDE("dead_tilde", "~", '̃'),
    CIRCUMFLEX("dead_circumflex", "^", '̂'),
    DIAERESIS("dead_diaeresis", "¨", '̈'),
    CEDILLA("dead_cedilla", "¸", '̧'),
    OGONEK("dead_ogonek", "˛", '̨'),
    ABOVE_RING("dead_abovering", "˚", '̊'),
    MACRON("dead_macron", "¯", '̄'),
    BREVE("dead_breve", "˘", '̆'),
    DOUBLE_ACUTE("dead_doubleacute", "˝", '̋'),
    CARON("dead_caron", "ˇ", '̌'),
    ABOVE_DOT("dead_abovedot", "˙", '̇'),
    BELOW_DOT("dead_belowdot", "̣", '̣'),
    HOOK("dead_hook", "̉", '̉'),
    HORN("dead_horn", "̛", '̛'),
    ;

    companion object {
        /** The five dead keys on the symbols page, in display order. */
        val SYMBOLS_ROW: List<DeadKey> = listOf(ACUTE, GRAVE, CIRCUMFLEX, TILDE, DIAERESIS)

        fun fromXkb(name: String): DeadKey? = entries.firstOrNull { it.xkbName == name }
    }
}
