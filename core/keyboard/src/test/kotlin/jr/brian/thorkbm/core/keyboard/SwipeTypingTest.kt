package jr.brian.thorkbm.core.keyboard

import com.google.common.truth.Truth.assertThat
import jr.brian.thorkbm.core.keyboard.output.OutputEvent
import jr.brian.thorkbm.core.model.KeyCode
import jr.brian.thorkbm.core.model.Language
import jr.brian.thorkbm.core.model.ModifierState
import jr.brian.thorkbm.core.model.Settings
import org.junit.Test

class SwipeTypingTest {
    private val k = KeyboardHarness(Settings(language = Language.EN_US, swipeTyping = true))

    /** Slides one finger through [letters]' keys and lifts it. */
    private fun swipe(letters: String, pointer: Long = 1) {
        k.down("char:${letters.first()}", pointer)
        for (c in letters.drop(1)) {
            val (x, y) = k.center("char:$c")
            k.move(x, y, pointer)
        }
        k.up(pointer)
    }

    private val decodeRequests get() = k.commands.filterIsInstance<KeyboardCommand.DecodeSwipe>()

    /** Plays the host: answers the latest decode request with [words]. */
    private fun decoded(vararg words: String) {
        k.send(KeyboardEvent.SwipeDecoded(decodeRequests.last().serial, words.toList()))
    }

    @Test
    fun `with swipe off, sliding off a letter still cancels it`() {
        val off = KeyboardHarness(Settings(language = Language.EN_US))
        off.down("char:h")
        val (x, y) = off.center("char:j")
        off.move(x, y)
        off.up()
        assertThat(off.outputs).isEmpty()
        assertThat(off.commands.filterIsInstance<KeyboardCommand.DecodeSwipe>()).isEmpty()
    }

    @Test
    fun `swipe needs the english layout`() {
        val pt = KeyboardHarness(Settings(language = Language.PT_PT, swipeTyping = true))
        assertThat(pt.state.swipeActive).isFalse()
        assertThat(pt.state.layout.keys.none { it.id.startsWith("ime:") }).isTrue()
    }

    @Test
    fun `a slide across letters asks the host to decode and types the best word`() {
        swipe("helo")
        assertThat(decodeRequests).hasSize(1)
        assertThat(k.state.swipe!!.lifted).isTrue()
        decoded("hello", "help", "hell")
        assertThat(k.outputs).containsExactly(OutputEvent.Word("hello"))
        assertThat(k.state.swipe).isNull()
        assertThat(k.state.stripCandidateAt(1)).isEqualTo("help")
    }

    @Test
    fun `the trace is shown while sliding and the start key is not typed`() {
        k.down("char:h")
        val (x, y) = k.center("char:e")
        k.move(x, y)
        assertThat(k.state.swipe!!.keyIds).containsExactly("char:h", "char:e").inOrder()
        assertThat(k.state.pressedKeyIds).isEmpty()
        k.up()
        assertThat(k.outputs).isEmpty()
    }

    @Test
    fun `consecutive swiped words get a space between them`() {
        swipe("helo")
        decoded("hello")
        swipe("wrld")
        decoded("world")
        assertThat(k.texts).containsExactly("hello", " world").inOrder()
    }

    @Test
    fun `typing a key between swipes drops the auto space`() {
        swipe("helo")
        decoded("hello")
        k.tap("footer:space")
        swipe("wrld")
        decoded("world")
        assertThat(k.texts).containsExactly("hello", " ", "world").inOrder()
    }

    @Test
    fun `picking a suggestion deletes the swiped word and types it instead`() {
        swipe("helo")
        decoded("hello", "help")
        k.tap("ime:choice:1")
        val backspaces = k.outputs.count { it == OutputEvent.Key(KeyCode.BACKSPACE, emptySet()) }
        assertThat(backspaces).isEqualTo(5)
        assertThat(k.outputs.last()).isEqualTo(OutputEvent.Word("help"))
        // A second pick replaces the new word, not the old one.
        k.tap("ime:choice:0")
        assertThat(k.outputs.count { it == OutputEvent.Key(KeyCode.BACKSPACE, emptySet()) }).isEqualTo(9)
        assertThat(k.outputs.last()).isEqualTo(OutputEvent.Word("hello"))
    }

    @Test
    fun `suggestions are cleared by other typing so a pick never deletes the wrong text`() {
        swipe("helo")
        decoded("hello", "help")
        k.tap("char:s")
        k.tap("ime:choice:1")
        assertThat(k.outputs.filterIsInstance<OutputEvent.Key>()).isEmpty()
        assertThat(k.state.suggestions).isNull()
    }

    @Test
    fun `a trackpad click clears suggestions`() {
        swipe("helo")
        decoded("hello", "help")
        k.send(KeyboardEvent.PointerClicked)
        assertThat(k.state.suggestions).isNull()
    }

    @Test
    fun `one-shot shift capitalises the swiped word and its replacements`() {
        k.tap("mod:SHIFT")
        swipe("helo")
        decoded("hello", "help")
        assertThat(k.texts).containsExactly("Hello")
        assertThat(k.state.shift).isEqualTo(ModifierState.OFF)
        k.tap("ime:choice:1")
        assertThat(k.outputs.last()).isEqualTo(OutputEvent.Word("Help"))
    }

    @Test
    fun `tapping letters still types them one by one`() {
        k.tap("char:h")
        k.tap("char:i")
        assertThat(k.texts).containsExactly("h", "i").inOrder()
        assertThat(decodeRequests).isEmpty()
    }

    @Test
    fun `a second finger aborts the swipe`() {
        k.down("char:h")
        val (x, y) = k.center("char:e")
        k.move(x, y)
        k.tap("char:a", pointer = 2)
        k.up()
        assertThat(decodeRequests).isEmpty()
        assertThat(k.texts).containsExactly("a")
    }

    @Test
    fun `a stale decode result is ignored`() {
        swipe("helo")
        k.send(KeyboardEvent.SwipeDecoded(decodeRequests.last().serial + 7, listOf("hello")))
        assertThat(k.outputs).isEmpty()
    }

    @Test
    fun `turning swipe off clears its state and the strip`() {
        swipe("helo")
        decoded("hello", "help")
        k.send(KeyboardEvent.SettingsChanged(k.state.settings.copy(swipeTyping = false)))
        assertThat(k.state.suggestions).isNull()
        assertThat(k.state.layout.keys.none { it.id.startsWith("ime:") }).isTrue()
    }
}
