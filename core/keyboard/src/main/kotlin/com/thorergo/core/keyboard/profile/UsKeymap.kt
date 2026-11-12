package com.thorergo.core.keyboard.profile

import com.thorergo.core.model.KeyCode

/** A physical US key press that produces one printable ASCII character. */
data class UsStroke(val code: KeyCode, val shift: Boolean)

/** The standard US character map: all 95 printable ASCII characters. The target must use a US layout. */
object UsKeymap {
    private val map: Map<String, UsStroke> = buildMap {
        for (code in KeyCode.entries) {
            val name = code.name
            if (name.length == 1 && name[0] in 'A'..'Z') {
                put(name.lowercase(), UsStroke(code, false))
                put(name, UsStroke(code, true))
            }
        }
        val digits = listOf(
            KeyCode.DIGIT_1, KeyCode.DIGIT_2, KeyCode.DIGIT_3, KeyCode.DIGIT_4, KeyCode.DIGIT_5,
            KeyCode.DIGIT_6, KeyCode.DIGIT_7, KeyCode.DIGIT_8, KeyCode.DIGIT_9, KeyCode.DIGIT_0,
            KeyCode.MINUS, KeyCode.EQUALS,
        )
        "1234567890-=".zip("!@#$%^&*()_+").forEachIndexed { i, (low, high) ->
            put(low.toString(), UsStroke(digits[i], false))
            put(high.toString(), UsStroke(digits[i], true))
        }
        listOf(
            Triple('`', '~', KeyCode.GRAVE), Triple('[', '{', KeyCode.LEFT_BRACKET),
            Triple(']', '}', KeyCode.RIGHT_BRACKET), Triple('\\', '|', KeyCode.BACKSLASH),
            Triple(';', ':', KeyCode.SEMICOLON), Triple('\'', '"', KeyCode.APOSTROPHE),
            Triple(',', '<', KeyCode.COMMA), Triple('.', '>', KeyCode.PERIOD),
            Triple('/', '?', KeyCode.SLASH),
        ).forEach { (low, high, code) ->
            put(low.toString(), UsStroke(code, false))
            put(high.toString(), UsStroke(code, true))
        }
        put(" ", UsStroke(KeyCode.SPACE, false))
    }

    /** Momentary-Shift punctuation; Caps Lock never applies this. */
    private val shifted: Map<String, String> =
        "`1234567890-=[]\\;',./".zip("~!@#$%^&*()_+{}|:\"<>?").associate { (a, b) -> a.toString() to b.toString() }

    operator fun get(char: String): UsStroke? = map[char]

    val characters: Set<String> get() = map.keys

    fun shiftedPunctuation(char: String): String = shifted[char] ?: char
}
