package jr.brian.thorkbm.feature.panel.input

import com.google.common.truth.Truth.assertThat
import jr.brian.thorkbm.core.keyboard.layout.LayoutEngine
import jr.brian.thorkbm.core.model.MouseButton
import org.junit.Test

class TouchRouterTest {
    private val router = TouchRouter()
    private val pad = LayoutEngine.PAD_COMBINED

    @Test
    fun `ownership is decided at touch-down`() {
        assertThat(router.down(1, pad.centerX, pad.centerY, pad)).isEqualTo(TouchOwner.TRACKPAD)
        assertThat(router.down(2, 100f, 200f, pad)).isEqualTo(TouchOwner.KEYBOARD)
        assertThat(router.ownerOf(1)).isEqualTo(TouchOwner.TRACKPAD)
        assertThat(router.up(2)).isEqualTo(TouchOwner.KEYBOARD)
        assertThat(router.ownerOf(2)).isNull()
    }

    @Test
    fun `overlays own every touch`() {
        assertThat(router.down(1, pad.centerX, pad.centerY, null)).isEqualTo(TouchOwner.KEYBOARD)
    }

    @Test
    fun `mouse buttons are reference counted`() {
        val holds = MouseHolds()
        assertThat(holds.press(1, MouseButton.LEFT)).isTrue()
        assertThat(holds.press(2, MouseButton.LEFT)).isFalse()
        assertThat(holds.release(1)).isNull()
        assertThat(holds.release(2)).isEqualTo(MouseButton.LEFT)
        assertThat(holds.any).isFalse()
    }

    @Test
    fun `clearing returns the held buttons`() {
        val holds = MouseHolds()
        holds.press(1, MouseButton.LEFT)
        holds.press(2, MouseButton.RIGHT)
        assertThat(holds.clear()).containsExactly(MouseButton.LEFT, MouseButton.RIGHT)
    }
}
