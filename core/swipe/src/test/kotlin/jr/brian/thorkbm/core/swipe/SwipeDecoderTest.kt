package jr.brian.thorkbm.core.swipe

import com.google.common.truth.Truth.assertThat
import jr.brian.thorkbm.core.keyboard.layout.KeyAction
import jr.brian.thorkbm.core.keyboard.layout.LayoutEngine
import jr.brian.thorkbm.core.model.Language
import jr.brian.thorkbm.core.model.Page
import jr.brian.thorkbm.core.model.PanelView
import org.junit.Test
import kotlin.random.Random

class SwipeDecoderTest {
    private val keys: Map<Char, KeyBox> = LayoutEngine.build(Page.ABC, PanelView.COMBINED, Language.EN_US).keys
        .mapNotNull { key ->
            val char = (key.action as? KeyAction.Text)?.char?.singleOrNull()?.takeIf { it in 'a'..'z' } ?: return@mapNotNull null
            char to KeyBox(key.rect.centerX, key.rect.centerY, key.rect.w, key.rect.h)
        }.toMap()

    private val decoder = SwipeDecoder(bundled)

    /** A finger-like trace through [word]'s keys: interpolated, with seeded wobble around each centre. */
    private fun trace(word: String, wobble: Float = 25f, seed: Int = 1): List<SwipePoint> {
        val random = Random(seed)
        val centers = word.map { keys.getValue(it) }.map {
            SwipePoint(it.cx + random.nextFloat() * 2 * wobble - wobble, it.cy + random.nextFloat() * 2 * wobble - wobble)
        }
        return centers.zipWithNext { a, b ->
            (0 until 8).map { i -> SwipePoint(a.x + (b.x - a.x) * i / 8, a.y + (b.y - a.y) * i / 8) }
        }.flatten() + centers.last()
    }

    @Test
    fun `common words come out first`() {
        for (word in listOf("hello", "the", "keyboard", "swipe", "you", "thanks", "good", "world", "question", "typing")) {
            assertThat(decoder.decode(trace(word), keys).first()).isEqualTo(word)
        }
    }

    @Test
    fun `a wobbly trace still finds the word`() {
        for (seed in 1..5) {
            assertThat(decoder.decode(trace("people", wobble = 40f, seed = seed), keys).first()).isEqualTo("people")
        }
    }

    @Test
    fun `contractions are typed with their apostrophe`() {
        assertThat(decoder.decode(trace("dont"), keys).first()).isEqualTo("don't")
    }

    @Test
    fun `alternatives are distinct and limited`() {
        val words = decoder.decode(trace("hello"), keys, limit = 6)
        assertThat(words).hasSize(6)
        assertThat(words).containsNoDuplicates()
    }

    @Test
    fun `words must start near the start of the trace`() {
        // A trace from Q to P never offers a word that starts with an unrelated letter.
        val words = decoder.decode(trace("qp"), keys)
        assertThat(words.all { it.first() in "qwa" }).isTrue()
    }

    @Test
    fun `a trace that stays on one key decodes nothing useful`() {
        val k = keys.getValue('k')
        assertThat(decoder.decode(listOf(SwipePoint(k.cx, k.cy)), keys)).isEmpty()
    }

    @Test
    fun `resampling spaces points evenly along the path`() {
        val points = SwipeDecoder.resample(listOf(SwipePoint(0f, 0f), SwipePoint(310f, 0f)))
        assertThat(points).hasSize(SwipeDecoder.SAMPLES)
        assertThat(points.last().x).isWithin(0.01f).of(310f)
        assertThat(points[1].x).isWithin(0.01f).of(10f)
    }

    private companion object {
        val bundled = SwipeLexicon.bundled()
    }
}
