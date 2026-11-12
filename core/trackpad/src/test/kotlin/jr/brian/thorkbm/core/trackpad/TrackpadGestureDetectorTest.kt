package jr.brian.thorkbm.core.trackpad

import com.google.common.truth.Truth.assertThat
import jr.brian.thorkbm.core.model.MouseButton
import org.junit.Test

class TrackpadGestureDetectorTest {
    private val pad = TrackpadGestureDetector(TrackpadConfig(sensitivity = 1.0))
    private val outputs = mutableListOf<TrackpadOutput>()
    private var now = 0L

    private fun down(id: Long, x: Float, y: Float) { outputs += pad.down(id, x, y, now) }

    private fun move(id: Long, x: Float, y: Float, afterMs: Long = 10) {
        now += afterMs
        outputs += pad.move(id, x, y, now)
    }

    private fun up(id: Long, afterMs: Long = 0) {
        now += afterMs
        outputs += pad.up(id, now)
    }

    private val moves get() = outputs.filterIsInstance<TrackpadOutput.Move>()
    private val clicks get() = outputs.filterIsInstance<TrackpadOutput.Click>().map { it.button }
    private val scrolls get() = outputs.filterIsInstance<TrackpadOutput.Scroll>()

    @Test
    fun `a quick still touch is a left click`() {
        down(1, 100f, 100f)
        up(1, afterMs = 100)
        assertThat(clicks).containsExactly(MouseButton.LEFT)
    }

    @Test
    fun `tap to click can be disabled`() {
        pad.config = pad.config.copy(tapToClick = false)
        down(1, 100f, 100f)
        up(1, afterMs = 100)
        assertThat(clicks).isEmpty()
    }

    @Test
    fun `a long still touch is not a click`() {
        down(1, 100f, 100f)
        up(1, afterMs = 400)
        assertThat(clicks).isEmpty()
    }

    @Test
    fun `motion inside the landing slop does not move the pointer`() {
        down(1, 100f, 100f)
        move(1, 105f, 104f)
        move(1, 100f, 108f)
        assertThat(moves).isEmpty()
        up(1, afterMs = 50)
        assertThat(clicks).containsExactly(MouseButton.LEFT)
    }

    @Test
    fun `only travel beyond the slop is replayed`() {
        down(1, 0f, 0f)
        // 20 px at 10 ms → 2000 px/s → gain 1 × (1 + 2000/3000).
        move(1, 20f, 0f)
        val expected = (20 * (1 - 12.0 / 20) * (1 + 2000.0 / 3000)).toInt()
        assertThat(moves).containsExactly(TrackpadOutput.Move(expected, 0))
    }

    @Test
    fun `acceleration is capped`() {
        down(1, 0f, 0f)
        move(1, 20f, 0f)
        outputs.clear()
        move(1, 120f, 0f, afterMs = 1) // 100 km/s-scale flick
        assertThat(moves.single().dx).isEqualTo((100 * 2.6).toInt())
    }

    @Test
    fun `sub-pixel motion accumulates`() {
        pad.config = pad.config.copy(sensitivity = 0.2)
        down(1, 0f, 0f)
        move(1, 13f, 0f, afterMs = 400)
        var x = 13f
        repeat(10) {
            x += 1f
            move(1, x, 0f, afterMs = 400)
        }
        assertThat(moves.sumOf { it.dx }).isAtLeast(2)
    }

    @Test
    fun `two still fingers are a right click`() {
        down(1, 100f, 100f)
        down(2, 200f, 100f)
        up(2, afterMs = 80)
        up(1, afterMs = 10)
        assertThat(clicks).containsExactly(MouseButton.RIGHT)
    }

    @Test
    fun `two-finger drag scrolls with the speed divisor`() {
        pad.config = pad.config.copy(scrollSpeed = 3) // divisor 10
        down(1, 100f, 300f)
        down(2, 200f, 300f)
        move(1, 100f, 290f)
        assertThat(scrolls).containsExactly(TrackpadOutput.Scroll(0, 120))
    }

    @Test
    fun `scroll speed table`() {
        val divisors = (1..5).map { speed -> maxOf(4.0, 22.0 - speed * 4) }
        assertThat(divisors).containsExactly(18.0, 14.0, 10.0, 6.0, 4.0).inOrder()
    }

    @Test
    fun `fine scroll scale keeps fractional remainders`() {
        pad.config = pad.config.copy(scrollSpeed = 3, scrollScale = 0.05)
        down(1, 100f, 300f)
        down(2, 200f, 300f)
        var y = 300f
        repeat(10) {
            y -= 1f
            move(1, 100f, y) // 12 units × 0.05 = 0.6 each
        }
        assertThat(scrolls.sumOf { it.vertical }).isEqualTo(6)
    }

    @Test
    fun `the trailing finger after a scroll never moves the pointer or clicks`() {
        down(1, 100f, 300f)
        down(2, 200f, 300f)
        move(1, 100f, 250f)
        up(2)
        move(1, 300f, 250f)
        up(1)
        assertThat(moves).isEmpty()
        assertThat(clicks).isEmpty()
    }

    @Test
    fun `four fingers lock out all output until every finger lifts`() {
        (1L..4L).forEach { down(it, it * 50f, 100f) }
        move(1, 400f, 400f)
        (1L..4L).forEach { up(it) }
        assertThat(outputs).isEmpty()
        down(5, 10f, 10f)
        up(5, afterMs = 50)
        assertThat(clicks).containsExactly(MouseButton.LEFT)
    }

    @Test
    fun `a flick starts a glide that decays to a stop`() {
        down(1, 0f, 0f)
        move(1, 20f, 0f)
        move(1, 60f, 0f)
        up(1, afterMs = 5)
        val glide = outputs.filterIsInstance<TrackpadOutput.StartGlide>().single().glide
        var frames = 0
        var total = 0
        while (true) {
            val step = glide.step() ?: break
            total += step.first
            frames++
        }
        assertThat(frames).isGreaterThan(5)
        assertThat(total).isGreaterThan(0)
    }

    @Test
    fun `no glide when the finger stopped before lifting`() {
        down(1, 0f, 0f)
        move(1, 60f, 0f)
        up(1, afterMs = 150)
        assertThat(outputs.filterIsInstance<TrackpadOutput.StartGlide>()).isEmpty()
    }

    @Test
    fun `glide can be disabled`() {
        pad.config = pad.config.copy(glide = false)
        down(1, 0f, 0f)
        move(1, 60f, 0f)
        up(1, afterMs = 5)
        assertThat(outputs.filterIsInstance<TrackpadOutput.StartGlide>()).isEmpty()
    }

    @Test
    fun `glide decay follows friction`() {
        assertThat(Glide.decayFor(1)).isWithin(1e-9).of(0.97)
        assertThat(Glide.decayFor(10)).isWithin(1e-9).of(0.74)
        assertThat(Glide.decayFor(5)).isWithin(1e-3).of(0.868)
    }

    @Test
    fun `a held header button suppresses taps but not motion`() {
        down(1, 100f, 100f)
        pad.suppressTapsForCurrentGroup()
        up(1, afterMs = 50)
        assertThat(clicks).isEmpty()
        down(2, 100f, 100f)
        pad.suppressTapsForCurrentGroup()
        move(2, 140f, 100f)
        assertThat(moves).isNotEmpty()
    }

    @Test
    fun `suppression ends with the group`() {
        down(1, 100f, 100f)
        pad.suppressTapsForCurrentGroup()
        up(1, afterMs = 50)
        down(2, 100f, 100f)
        up(2, afterMs = 50)
        assertThat(clicks).containsExactly(MouseButton.LEFT)
    }
}
