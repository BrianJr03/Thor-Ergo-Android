package jr.brian.thorkbm.core.keyboard.output

import com.google.common.truth.Truth.assertThat
import jr.brian.thorkbm.core.keyboard.profile.UsKeymap
import jr.brian.thorkbm.core.model.DeadKey
import jr.brian.thorkbm.core.model.KeyCode
import jr.brian.thorkbm.core.model.Language
import jr.brian.thorkbm.core.model.Modifier
import jr.brian.thorkbm.core.pinyin.PinyinComposer
import jr.brian.thorkbm.core.pinyin.PinyinDictionary
import org.junit.Test

class OutputRouterTest {
    private val router = OutputRouter(PinyinComposer(PinyinDictionary(mapOf("ni" to "你尼", "hao" to "好"), emptyMap())))
    private var state = ComposerState()

    private fun route(event: OutputEvent, language: Language): RouteResult =
        router.route(state, event, language).also { state = it.state }

    private fun text(text: String, code: KeyCode? = null, mods: Set<Modifier> = emptySet(), caps: Boolean = false, shift: Boolean = false) =
        OutputEvent.Text(text, code, mods, caps, shift)

    @Test
    fun `us text becomes physical keys with shift where needed`() {
        assertThat(route(text("@"), Language.EN_US).injections).containsExactly(Injection.Chord(KeyCode.DIGIT_2, setOf(Modifier.SHIFT)))
        assertThat(route(text("a", KeyCode.A), Language.EN_US).injections).containsExactly(Injection.Chord(KeyCode.A))
    }

    @Test
    fun `every printable ascii character is on the us map`() {
        val missing = (32..126).map { it.toChar().toString() }.filterNot { it in UsKeymap.characters }
        assertThat(missing).isEmpty()
    }

    @Test
    fun `a swiped word is typed key by key on us`() {
        assertThat(route(OutputEvent.Word(" Don't"), Language.EN_US).injections).containsExactly(
            Injection.Chord(KeyCode.SPACE),
            Injection.Chord(KeyCode.D, setOf(Modifier.SHIFT)),
            Injection.Chord(KeyCode.O),
            Injection.Chord(KeyCode.N),
            Injection.Chord(KeyCode.APOSTROPHE),
            Injection.Chord(KeyCode.T),
        ).inOrder()
    }

    @Test
    fun `a word with a character missing from the us map types nothing`() {
        val result = route(OutputEvent.Word("café"), Language.EN_US)
        assertThat(result.injections).isEmpty()
        assertThat(result.error).isEqualTo(OutputError.NOT_ON_US_KEYBOARD)
    }

    @Test
    fun `us never silently maps an accented letter`() {
        val result = route(text("á"), Language.EN_US)
        assertThat(result.injections).isEmpty()
        assertThat(result.error).isEqualTo(OutputError.NOT_ON_US_KEYBOARD)
    }

    @Test
    fun `other layouts commit unicode text`() {
        assertThat(route(text("ç", KeyCode.SEMICOLON), Language.PT_PT).injections).containsExactly(Injection.Commit("ç"))
    }

    @Test
    fun `control characters are rejected`() {
        assertThat(route(text("\n"), Language.PT_PT).error).isEqualTo(OutputError.INVALID_CHARACTER)
    }

    @Test
    fun `a shortcut uses the us position and ends any pending accent`() {
        route(OutputEvent.Dead(DeadKey.TILDE), Language.PT_PT)
        assertThat(state.pendingDead).isEqualTo(DeadKey.TILDE)
        val result = route(text("c", KeyCode.C, setOf(Modifier.CTRL)), Language.PT_PT)
        assertThat(result.injections).containsExactly(Injection.Chord(KeyCode.C, setOf(Modifier.CTRL)))
        assertThat(state.pendingDead).isNull()
    }

    @Test
    fun `caps lock never adds shift to a shortcut`() {
        val result = route(text("C", KeyCode.C, setOf(Modifier.SHIFT, Modifier.CTRL), caps = true), Language.PT_PT)
        assertThat(result.injections).containsExactly(Injection.Chord(KeyCode.C, setOf(Modifier.CTRL)))
    }

    @Test
    fun `a us symbol shortcut keeps its own shift`() {
        val result = route(text("@", KeyCode.DIGIT_2, setOf(Modifier.CTRL)), Language.EN_US)
        assertThat(result.injections).containsExactly(Injection.Chord(KeyCode.DIGIT_2, setOf(Modifier.SHIFT, Modifier.CTRL)))
    }

    @Test
    fun `a national letter with no us position resolves through the us map or fails`() {
        assertThat(route(text("ü", null, setOf(Modifier.CTRL)), Language.DE_DE).error).isEqualTo(OutputError.NO_PHYSICAL_SHORTCUT)
    }

    @Test
    fun `altgr e types the euro sign in portuguese`() {
        assertThat(route(text("e", KeyCode.E, setOf(Modifier.ALTGR)), Language.PT_PT).injections).containsExactly(Injection.Commit("€"))
    }

    @Test
    fun `altgr on a dead-key position starts an accent`() {
        val result = route(text("ç", KeyCode.SEMICOLON, setOf(Modifier.ALTGR)), Language.PT_PT)
        assertThat(result.injections).isEmpty()
        assertThat(state.pendingDead).isEqualTo(DeadKey.ACUTE)
    }

    @Test
    fun `altgr in other layouts points to the symbols page`() {
        assertThat(route(text("e", KeyCode.E, setOf(Modifier.ALTGR)), Language.ES_ES).error).isEqualTo(OutputError.ALTGR_USE_SYMBOLS_PAGE)
    }

    @Test
    fun `dead keys are unavailable in us and pinyin`() {
        assertThat(route(OutputEvent.Dead(DeadKey.TILDE), Language.EN_US).error).isNotNull()
        assertThat(route(OutputEvent.Dead(DeadKey.TILDE), Language.ZH_CN).error).isNotNull()
    }

    @Test
    fun `dead key then letter commits the composed character`() {
        route(OutputEvent.Dead(DeadKey.TILDE), Language.PT_PT)
        assertThat(route(text("a", KeyCode.A), Language.PT_PT).injections).containsExactly(Injection.Commit("ã"))
    }

    @Test
    fun `plain editing keys pass through composition`() {
        assertThat(route(OutputEvent.Key(KeyCode.ENTER, emptySet()), Language.PT_PT).injections).containsExactly(Injection.Chord(KeyCode.ENTER))
    }

    @Test
    fun `a modified key is a physical chord`() {
        assertThat(route(OutputEvent.Key(KeyCode.RIGHT, setOf(Modifier.SHIFT)), Language.FR_FR).injections)
            .containsExactly(Injection.Chord(KeyCode.RIGHT, setOf(Modifier.SHIFT)))
    }

    @Test
    fun `popup literals commit as typed`() {
        assertThat(route(OutputEvent.Literal("ã"), Language.PT_PT).injections).containsExactly(Injection.Commit("ã"))
    }

    @Test
    fun `pinyin composes, then chooses a candidate`() {
        route(text("n", KeyCode.N), Language.ZH_CN)
        val typed = route(text("i", KeyCode.I), Language.ZH_CN)
        assertThat(typed.injections).isEmpty()
        assertThat(state.pinyin.candidates).containsExactly("你", "尼").inOrder()
        val chosen = route(OutputEvent.Choose(1, state.pinyin.revision), Language.ZH_CN)
        assertThat(chosen.injections).containsExactly(Injection.Commit("尼"))
        assertThat(state.preedit).isEmpty()
    }

    @Test
    fun `reset clears composition`() {
        route(OutputEvent.Dead(DeadKey.ACUTE), Language.PT_PT)
        assertThat(router.reset(state).pendingDead).isNull()
    }
}
