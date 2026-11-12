package jr.brian.thorkbm.core.display

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PairDisplaysTest {
    private val main = DisplayInfo(0, "Built-in", 1080, 1920)
    private val lower = DisplayInfo(2, "Companion", 1240, 1080)

    @Test
    fun `pairs the default display with the companion`() {
        assertThat(pairDisplays(listOf(lower, main), 0)).isEqualTo(DualDisplays(main, lower))
    }

    @Test
    fun `a single screen has no pair`() {
        assertThat(pairDisplays(listOf(main), 0)).isNull()
    }

    @Test
    fun `the lowest secondary id wins`() {
        val cast = DisplayInfo(7, "Cast", 1920, 1080)
        assertThat(pairDisplays(listOf(main, cast, lower), 0)?.lower).isEqualTo(lower)
    }
}
