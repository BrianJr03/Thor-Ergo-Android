package jr.brian.thorkbm.core.injection

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import jr.brian.thorkbm.core.model.KeyCode
import jr.brian.thorkbm.core.model.Modifier
import org.junit.Test

class AndroidKeysTest {
    @Test
    fun `every key code maps to a distinct android key`() {
        val mapped = KeyCode.entries.map(AndroidKeys::keyCode)
        assertThat(mapped.toSet()).hasSize(KeyCode.entries.size)
        assertThat(mapped).doesNotContain(KeyEvent.KEYCODE_UNKNOWN)
    }

    @Test
    fun `letters map alphabetically`() {
        assertThat(AndroidKeys.keyCode(KeyCode.A)).isEqualTo(KeyEvent.KEYCODE_A)
        assertThat(AndroidKeys.keyCode(KeyCode.Z)).isEqualTo(KeyEvent.KEYCODE_Z)
    }

    @Test
    fun `a chord presses modifiers in order and releases in reverse`() {
        val strokes = AndroidKeys.chordEvents(KeyCode.C, setOf(Modifier.SHIFT, Modifier.CTRL))
        val ctrlShift = AndroidKeys.metaState(listOf(Modifier.SHIFT, Modifier.CTRL))
        assertThat(strokes).containsExactly(
            KeyStroke(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, AndroidKeys.metaFlag(Modifier.SHIFT)),
            KeyStroke(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, ctrlShift),
            KeyStroke(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C, ctrlShift),
            KeyStroke(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_C, ctrlShift),
            KeyStroke(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, AndroidKeys.metaFlag(Modifier.SHIFT)),
            KeyStroke(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0),
        ).inOrder()
    }

    @Test
    fun `a plain key is one down and one up`() {
        assertThat(AndroidKeys.chordEvents(KeyCode.ENTER, emptySet())).containsExactly(
            KeyStroke(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0),
            KeyStroke(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0),
        ).inOrder()
    }
}
