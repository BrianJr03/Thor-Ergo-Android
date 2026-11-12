package jr.brian.thorkbm.core.model

/**
 * Platform-neutral physical key positions on a US keyboard.
 *
 * [evdev] is the Linux input code, kept because the national keymaps (e.g. pt_PT AltGr)
 * are defined by physical position. The Android key code mapping lives in the injection layer.
 */
enum class KeyCode(val evdev: Int) {
    ESCAPE(1),
    DIGIT_1(2), DIGIT_2(3), DIGIT_3(4), DIGIT_4(5), DIGIT_5(6),
    DIGIT_6(7), DIGIT_7(8), DIGIT_8(9), DIGIT_9(10), DIGIT_0(11),
    MINUS(12), EQUALS(13), BACKSPACE(14), TAB(15),
    Q(16), W(17), E(18), R(19), T(20), Y(21), U(22), I(23), O(24), P(25),
    LEFT_BRACKET(26), RIGHT_BRACKET(27), ENTER(28), CTRL_LEFT(29),
    A(30), S(31), D(32), F(33), G(34), H(35), J(36), K(37), L(38),
    SEMICOLON(39), APOSTROPHE(40), GRAVE(41), SHIFT_LEFT(42), BACKSLASH(43),
    Z(44), X(45), C(46), V(47), B(48), N(49), M(50),
    COMMA(51), PERIOD(52), SLASH(53), ALT_LEFT(56), SPACE(57), CAPS_LOCK(58),
    F1(59), F2(60), F3(61), F4(62), F5(63), F6(64), F7(65), F8(66), F9(67), F10(68),
    NUM_LOCK(69), SCROLL_LOCK(70), F11(87), F12(88),
    ALT_RIGHT(100), HOME(102), UP(103), PAGE_UP(104), LEFT(105), RIGHT(106),
    END(107), DOWN(108), PAGE_DOWN(109), INSERT(110), DELETE(111), META_LEFT(125),
    ;

    /** Backspace, Delete and the arrows fire on press and auto-repeat while held. */
    val repeats: Boolean get() = this in REPEATING

    companion object {
        private val byEvdev = entries.associateBy { it.evdev }
        private val REPEATING = setOf(BACKSPACE, DELETE, LEFT, UP, DOWN, RIGHT)

        fun fromEvdev(code: Int): KeyCode? = byEvdev[code]

        val FUNCTION_KEYS: List<KeyCode> = listOf(F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12)

        fun of(modifier: Modifier): KeyCode = when (modifier) {
            Modifier.SHIFT -> SHIFT_LEFT
            Modifier.CTRL -> CTRL_LEFT
            Modifier.ALT -> ALT_LEFT
            Modifier.SUPER -> META_LEFT
            Modifier.ALTGR -> ALT_RIGHT
        }
    }
}
