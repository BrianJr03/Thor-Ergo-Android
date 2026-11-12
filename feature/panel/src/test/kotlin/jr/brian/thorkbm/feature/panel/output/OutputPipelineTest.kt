package jr.brian.thorkbm.feature.panel.output

import com.google.common.truth.Truth.assertThat
import jr.brian.thorkbm.core.keyboard.output.ComposerState
import jr.brian.thorkbm.core.keyboard.output.OutputEvent
import jr.brian.thorkbm.core.keyboard.output.OutputRouter
import jr.brian.thorkbm.core.model.DeadKey
import jr.brian.thorkbm.core.model.KeyCode
import jr.brian.thorkbm.core.model.Language
import jr.brian.thorkbm.core.model.Modifier
import jr.brian.thorkbm.core.pinyin.PinyinComposer
import jr.brian.thorkbm.core.pinyin.PinyinDictionary
import jr.brian.thorkbm.core.testing.FakeInputInjector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OutputPipelineTest {
    private val injector = FakeInputInjector()
    private val messages = mutableListOf<PanelMessage>()
    private val composers = mutableListOf<ComposerState>()
    private var language = Language.PT_PT

    private fun TestScope.pipeline() = OutputPipeline(
        router = OutputRouter(PinyinComposer(PinyinDictionary(emptyMap(), emptyMap()))),
        injector = injector,
        language = { language },
        clock = { currentTime },
        onComposerChanged = { composers += it },
        onMessage = { messages += it },
    ).also { it.start(backgroundScope) }

    private fun text(t: String, code: KeyCode? = null, mods: Set<Modifier> = emptySet()) = OutputEvent.Text(t, code, mods, caps = false, shift = false)

    @Test
    fun `events are injected in order`() = runTest {
        val pipeline = pipeline()
        pipeline.submit(text("o"))
        pipeline.submit(text("l"))
        pipeline.submit(OutputEvent.Literal("á"))
        runCurrent()
        assertThat(injector.calls).containsExactly("text:o", "text:l", "text:á").inOrder()
    }

    @Test
    fun `a shortcut waits for earlier text`() = runTest {
        val pipeline = pipeline()
        pipeline.submit(text("a"))
        pipeline.submit(text("c", KeyCode.C, setOf(Modifier.CTRL)))
        runCurrent()
        assertThat(injector.calls).containsExactly("text:a", "CTRL+C").inOrder()
    }

    @Test
    fun `us layout types physical keys`() = runTest {
        language = Language.EN_US
        val pipeline = pipeline()
        pipeline.submit(text("@"))
        runCurrent()
        assertThat(injector.calls).containsExactly("SHIFT+DIGIT_2")
    }

    @Test
    fun `routing errors are reported without stopping later input`() = runTest {
        language = Language.EN_US
        val pipeline = pipeline()
        pipeline.submit(text("á"))
        pipeline.submit(text("b", KeyCode.B))
        runCurrent()
        assertThat(messages).containsExactly(PanelMessage.NOT_ON_US_KEYBOARD)
        assertThat(injector.calls).containsExactly("B")
    }

    @Test
    fun `an injection failure drops pending input`() = runTest {
        val pipeline = pipeline()
        injector.succeed = false
        pipeline.submit(text("a"))
        pipeline.submit(text("b"))
        runCurrent()
        assertThat(injector.calls).containsExactly("text:a")
        assertThat(messages).containsExactly(PanelMessage.TEXT_UNAVAILABLE)
    }

    @Test
    fun `a full queue cancels pending input`() = runTest {
        val pipeline = pipeline()
        repeat(OutputPipeline.CAPACITY + 1) { pipeline.submit(text("a")) }
        assertThat(messages).containsExactly(PanelMessage.QUEUE_FULL)
        runCurrent()
        assertThat(injector.calls).isEmpty()
    }

    @Test
    fun `reset discards queued input and composition`() = runTest {
        val pipeline = pipeline()
        pipeline.submit(OutputEvent.Dead(DeadKey.TILDE))
        runCurrent()
        assertThat(composers.last().pendingDead).isEqualTo(DeadKey.TILDE)
        pipeline.submit(text("a"))
        pipeline.reset()
        runCurrent()
        assertThat(injector.calls).isEmpty()
        assertThat(composers.last().pendingDead).isNull()
    }

    @Test
    fun `dead key composition reaches the target as one character`() = runTest {
        val pipeline = pipeline()
        pipeline.submit(OutputEvent.Dead(DeadKey.TILDE))
        pipeline.submit(text("a", KeyCode.A))
        runCurrent()
        assertThat(injector.calls).containsExactly("text:ã")
    }
}
