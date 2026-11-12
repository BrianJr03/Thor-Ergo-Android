package jr.brian.thorkbm.core.pinyin

import com.google.common.truth.Truth.assertThat
import jr.brian.thorkbm.core.model.ComposerOutput
import jr.brian.thorkbm.core.model.KeyCode
import org.junit.Test

class PinyinComposerTest {
    private var composer = PinyinComposer(bundled)
    private var state = PinyinState()
    private val committed = mutableListOf<String>()
    private val forwarded = mutableListOf<KeyCode>()

    private fun apply(input: PinyinInput) {
        val result = composer.handle(state, input)
        state = result.state
        result.outputs.forEach {
            when (it) {
                is ComposerOutput.Commit -> committed += it.text
                is ComposerOutput.Forward -> forwarded += it.code
            }
        }
    }

    private fun type(text: String) = text.forEach { apply(PinyinInput.Text(it.toString())) }

    private fun choose(index: Int = 0) = apply(PinyinInput.Choose(index, state.revision))

    private fun key(code: KeyCode) = apply(PinyinInput.Key(code))

    private fun withDictionary(index: Map<String, String>) {
        composer = PinyinComposer(PinyinDictionary(index, emptyMap()))
    }

    @Test
    fun `the bundled dictionary loads`() {
        assertThat(bundled.characters("a")).isNotEmpty()
        assertThat(bundled.phrases("nihao")).containsExactly("你好")
    }

    @Test
    fun `supplementary-plane characters are whole candidates`() {
        assertThat(bundled.characters("a").all { it.codePointCount(0, it.length) == 1 }).isTrue()
    }

    @Test
    fun `a phrase is offered first and chosen whole`() {
        type("nihao")
        assertThat(state.candidates.first()).isEqualTo("你好")
        assertThat(committed).isEmpty()
        choose()
        assertThat(committed).containsExactly("你好")
        assertThat(state.buffer).isEmpty()
    }

    @Test
    fun zhongguo() {
        type("zhongguo")
        choose()
        assertThat(committed).containsExactly("中国")
    }

    @Test
    fun `space selects the first candidate`() {
        type("nihao ")
        assertThat(committed).containsExactly("你好")
    }

    @Test
    fun `enter sends the latin spelling, a second enter sends return`() {
        type("nihao")
        key(KeyCode.ENTER)
        assertThat(committed).containsExactly("nihao")
        assertThat(forwarded).isEmpty()
        key(KeyCode.ENTER)
        assertThat(forwarded).containsExactly(KeyCode.ENTER)
    }

    @Test
    fun `backspace edits only the uncommitted buffer`() {
        type("nihao")
        key(KeyCode.BACKSPACE)
        assertThat(state.buffer).isEqualTo("niha")
        assertThat(forwarded).isEmpty()
    }

    @Test
    fun `escape cancels composition, otherwise it passes through`() {
        type("nihao")
        key(KeyCode.ESCAPE)
        assertThat(state.buffer).isEmpty()
        assertThat(committed).isEmpty()
        key(KeyCode.ESCAPE)
        assertThat(forwarded).containsExactly(KeyCode.ESCAPE)
    }

    @Test
    fun `a stale candidate tap does nothing`() {
        type("ni")
        val old = state.revision
        type("hao")
        apply(PinyinInput.Choose(0, old))
        assertThat(committed).isEmpty()
        assertThat(state.buffer).isEqualTo("nihao")
    }

    @Test
    fun `out of range candidates are ignored`() {
        type("ni")
        apply(PinyinInput.Choose(999_999, state.revision))
        apply(PinyinInput.Choose(-1, state.revision))
        assertThat(committed).isEmpty()
    }

    @Test
    fun `the buffer is bounded`() {
        type("q".repeat(100))
        assertThat(state.buffer).hasLength(PinyinComposer.LIMIT)
    }

    @Test
    fun `upper case input is normalised`() {
        type("NIHAO")
        choose()
        assertThat(committed).containsExactly("你好")
    }

    @Test
    fun `v stands for u-umlaut`() {
        type("lv")
        assertThat(state.candidates).contains("绿")
    }

    @Test
    fun `choosing a first syllable keeps the rest`() {
        withDictionary(mapOf("ni" to "你", "hao" to "好"))
        type("nihao")
        choose()
        assertThat(state.buffer).isEqualTo("hao")
        choose()
        assertThat(committed).containsExactly("你", "好").inOrder()
    }

    @Test
    fun `an apostrophe separates syllables`() {
        withDictionary(mapOf("xi" to "西", "an" to "安"))
        type("xi'an")
        choose()
        assertThat(state.buffer).isEqualTo("an")
        choose()
        assertThat(committed).containsExactly("西", "安").inOrder()
    }

    @Test
    fun `punctuation is typed literally`() {
        type("。")
        assertThat(committed).containsExactly("。")
    }

    @Test
    fun `digits never pick a candidate`() {
        type("ni1")
        assertThat(committed).containsExactly("ni1")
    }

    @Test
    fun `a literal ends composition without leaking latin`() {
        type("ni")
        apply(PinyinInput.Literal("é"))
        assertThat(committed).containsExactly("é")
        assertThat(state.buffer).isEmpty()
    }

    @Test
    fun `reset discards without writing`() {
        type("nihao")
        apply(PinyinInput.Reset)
        assertThat(committed).isEmpty()
        assertThat(state.buffer).isEmpty()
    }

    private companion object {
        val bundled: PinyinDictionary by lazy { PinyinDictionary.bundled() }
    }
}
