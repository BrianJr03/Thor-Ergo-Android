package com.thorergo.core.injection

import android.view.KeyEvent
import com.thorergo.core.model.KeyCode
import com.thorergo.core.model.Modifier

/** The one mapping from platform-neutral keys to Android key codes and meta state. */
internal object AndroidKeys {
    fun keyCode(code: KeyCode): Int = when (code) {
        KeyCode.ESCAPE -> KeyEvent.KEYCODE_ESCAPE
        KeyCode.DIGIT_1 -> KeyEvent.KEYCODE_1
        KeyCode.DIGIT_2 -> KeyEvent.KEYCODE_2
        KeyCode.DIGIT_3 -> KeyEvent.KEYCODE_3
        KeyCode.DIGIT_4 -> KeyEvent.KEYCODE_4
        KeyCode.DIGIT_5 -> KeyEvent.KEYCODE_5
        KeyCode.DIGIT_6 -> KeyEvent.KEYCODE_6
        KeyCode.DIGIT_7 -> KeyEvent.KEYCODE_7
        KeyCode.DIGIT_8 -> KeyEvent.KEYCODE_8
        KeyCode.DIGIT_9 -> KeyEvent.KEYCODE_9
        KeyCode.DIGIT_0 -> KeyEvent.KEYCODE_0
        KeyCode.MINUS -> KeyEvent.KEYCODE_MINUS
        KeyCode.EQUALS -> KeyEvent.KEYCODE_EQUALS
        KeyCode.BACKSPACE -> KeyEvent.KEYCODE_DEL
        KeyCode.TAB -> KeyEvent.KEYCODE_TAB
        KeyCode.LEFT_BRACKET -> KeyEvent.KEYCODE_LEFT_BRACKET
        KeyCode.RIGHT_BRACKET -> KeyEvent.KEYCODE_RIGHT_BRACKET
        KeyCode.ENTER -> KeyEvent.KEYCODE_ENTER
        KeyCode.CTRL_LEFT -> KeyEvent.KEYCODE_CTRL_LEFT
        KeyCode.SEMICOLON -> KeyEvent.KEYCODE_SEMICOLON
        KeyCode.APOSTROPHE -> KeyEvent.KEYCODE_APOSTROPHE
        KeyCode.GRAVE -> KeyEvent.KEYCODE_GRAVE
        KeyCode.SHIFT_LEFT -> KeyEvent.KEYCODE_SHIFT_LEFT
        KeyCode.BACKSLASH -> KeyEvent.KEYCODE_BACKSLASH
        KeyCode.COMMA -> KeyEvent.KEYCODE_COMMA
        KeyCode.PERIOD -> KeyEvent.KEYCODE_PERIOD
        KeyCode.SLASH -> KeyEvent.KEYCODE_SLASH
        KeyCode.ALT_LEFT -> KeyEvent.KEYCODE_ALT_LEFT
        KeyCode.SPACE -> KeyEvent.KEYCODE_SPACE
        KeyCode.CAPS_LOCK -> KeyEvent.KEYCODE_CAPS_LOCK
        KeyCode.F1 -> KeyEvent.KEYCODE_F1
        KeyCode.F2 -> KeyEvent.KEYCODE_F2
        KeyCode.F3 -> KeyEvent.KEYCODE_F3
        KeyCode.F4 -> KeyEvent.KEYCODE_F4
        KeyCode.F5 -> KeyEvent.KEYCODE_F5
        KeyCode.F6 -> KeyEvent.KEYCODE_F6
        KeyCode.F7 -> KeyEvent.KEYCODE_F7
        KeyCode.F8 -> KeyEvent.KEYCODE_F8
        KeyCode.F9 -> KeyEvent.KEYCODE_F9
        KeyCode.F10 -> KeyEvent.KEYCODE_F10
        KeyCode.F11 -> KeyEvent.KEYCODE_F11
        KeyCode.F12 -> KeyEvent.KEYCODE_F12
        KeyCode.NUM_LOCK -> KeyEvent.KEYCODE_NUM_LOCK
        KeyCode.SCROLL_LOCK -> KeyEvent.KEYCODE_SCROLL_LOCK
        KeyCode.ALT_RIGHT -> KeyEvent.KEYCODE_ALT_RIGHT
        KeyCode.HOME -> KeyEvent.KEYCODE_MOVE_HOME
        KeyCode.UP -> KeyEvent.KEYCODE_DPAD_UP
        KeyCode.PAGE_UP -> KeyEvent.KEYCODE_PAGE_UP
        KeyCode.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        KeyCode.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        KeyCode.END -> KeyEvent.KEYCODE_MOVE_END
        KeyCode.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        KeyCode.PAGE_DOWN -> KeyEvent.KEYCODE_PAGE_DOWN
        KeyCode.INSERT -> KeyEvent.KEYCODE_INSERT
        KeyCode.DELETE -> KeyEvent.KEYCODE_FORWARD_DEL
        KeyCode.META_LEFT -> KeyEvent.KEYCODE_META_LEFT
        else -> letterKeyCode(code)
    }

    private fun letterKeyCode(code: KeyCode): Int {
        val letter = code.name.single()
        return KeyEvent.KEYCODE_A + (letter - 'A')
    }

    fun metaFlag(modifier: Modifier): Int = when (modifier) {
        Modifier.SHIFT -> KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        Modifier.CTRL -> KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        Modifier.ALT -> KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        Modifier.SUPER -> KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        Modifier.ALTGR -> KeyEvent.META_ALT_ON or KeyEvent.META_ALT_RIGHT_ON
    }

    fun metaState(mods: Collection<Modifier>): Int = mods.fold(0) { acc, m -> acc or metaFlag(m) }

    /**
     * The key events for a chord: modifiers down in order with accumulating meta state,
     * the key down and up, then modifiers up in reverse.
     */
    fun chordEvents(code: KeyCode, mods: Set<Modifier>): List<KeyStroke> {
        val ordered = mods.sortedBy { it.ordinal }
        val strokes = mutableListOf<KeyStroke>()
        var meta = 0
        for (mod in ordered) {
            meta = meta or metaFlag(mod)
            strokes += KeyStroke(KeyEvent.ACTION_DOWN, keyCode(KeyCode.of(mod)), meta)
        }
        strokes += KeyStroke(KeyEvent.ACTION_DOWN, keyCode(code), meta)
        strokes += KeyStroke(KeyEvent.ACTION_UP, keyCode(code), meta)
        for (mod in ordered.reversed()) {
            meta = meta and metaFlag(mod).inv()
            strokes += KeyStroke(KeyEvent.ACTION_UP, keyCode(KeyCode.of(mod)), meta)
        }
        return strokes
    }
}

internal data class KeyStroke(val action: Int, val keyCode: Int, val metaState: Int)
