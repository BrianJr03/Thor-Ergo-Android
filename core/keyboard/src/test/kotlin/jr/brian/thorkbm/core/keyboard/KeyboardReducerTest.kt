package jr.brian.thorkbm.core.keyboard

import com.google.common.truth.Truth.assertThat
import jr.brian.thorkbm.core.keyboard.layout.KeyAction
import jr.brian.thorkbm.core.keyboard.output.OutputEvent
import jr.brian.thorkbm.core.model.DeadKey
import jr.brian.thorkbm.core.model.KeyCode
import jr.brian.thorkbm.core.model.Language
import jr.brian.thorkbm.core.model.Modifier
import jr.brian.thorkbm.core.model.ModifierState
import jr.brian.thorkbm.core.model.MouseButton
import jr.brian.thorkbm.core.model.Page
import jr.brian.thorkbm.core.model.PanelView
import jr.brian.thorkbm.core.model.Settings
import org.junit.Test

class KeyboardReducerTest {
    private val k = KeyboardHarness(Settings(language = Language.PT_PT))

    private fun popupValues() = k.state.popup!!.options.map { (it.action as KeyAction.Accent).char }

    @Test
    fun `a letter is typed on release only`() {
        k.down("char:a")
        assertThat(k.outputs).isEmpty()
        k.up()
        assertThat(k.texts).containsExactly("a")
    }

    @Test
    fun `one-shot shift applies to the next letter only`() {
        k.tap("mod:SHIFT")
        k.tap("char:a")
        k.tap("char:b")
        assertThat(k.texts).containsExactly("A", "b").inOrder()
    }

    @Test
    fun `double-tapped shift locks until tapped again`() {
        k.tap("mod:SHIFT")
        k.advance(100)
        k.tap("mod:SHIFT")
        k.tap("char:a")
        k.tap("char:b")
        k.tap("mod:SHIFT")
        k.tap("char:c")
        assertThat(k.texts).containsExactly("A", "B", "c").inOrder()
    }

    @Test
    fun `a slow second shift tap turns it off instead of locking`() {
        k.tap("mod:SHIFT")
        k.advance(400)
        k.tap("mod:SHIFT")
        assertThat(k.state.shift).isEqualTo(ModifierState.OFF)
    }

    @Test
    fun `holding a letter opens accents without typing the base letter`() {
        k.down("char:a")
        k.advance(410)
        assertThat(k.state.popup).isNotNull()
        assertThat(k.outputs).isEmpty()
    }

    @Test
    fun `sliding to an accent and releasing types it`() {
        k.down("char:a")
        k.advance(410)
        val option = k.state.popup!!.options[3]
        k.move(option.rect.centerX, option.rect.centerY)
        k.up()
        assertThat(k.outputs).containsExactly(OutputEvent.Literal("ã"))
    }

    @Test
    fun `releasing the popup away from options types nothing`() {
        k.down("char:a")
        k.advance(410)
        k.move(1200f, 1000f)
        k.up()
        assertThat(k.outputs).isEmpty()
    }

    @Test
    fun `accent popup follows shift and consumes it`() {
        k.tap("mod:SHIFT")
        k.down("char:a")
        k.advance(410)
        assertThat(popupValues()).containsExactly("Á", "À", "Â", "Ã").inOrder()
        val option = k.state.popup!!.options[0]
        k.move(option.rect.centerX, option.rect.centerY)
        k.up()
        assertThat(k.texts.last()).isEqualTo("Á")
        assertThat(k.state.shift).isEqualTo(ModifierState.OFF)
    }

    @Test
    fun `every vowel offers its Portuguese accents`() {
        val expected = mapOf(
            "a" to listOf("á", "à", "â", "ã"), "e" to listOf("é", "ê"), "i" to listOf("í"),
            "o" to listOf("ó", "ô", "õ"), "u" to listOf("ú", "ü"),
        )
        for ((letter, accents) in expected) {
            k.down("char:$letter")
            k.advance(410)
            assertThat(popupValues()).isEqualTo(accents)
            k.send(KeyboardEvent.Cancel(1))
        }
    }

    @Test
    fun `a pending long press dies with a page change`() {
        k.down("char:a")
        k.tap("page:NUMBERS", pointer = 2)
        k.advance(1000)
        assertThat(k.state.popup).isNull()
    }

    @Test
    fun `a second finger cancels the open popup`() {
        k.down("char:a")
        k.advance(410)
        k.down("char:b", pointer = 2)
        k.up(2)
        assertThat(k.texts).containsExactly("b")
        assertThat(k.state.popup).isNull()
    }

    @Test
    fun `sliding off a key cancels it`() {
        k.down("char:a")
        val (x, y) = k.center("char:s")
        k.move(x, y)
        k.up()
        assertThat(k.outputs).isEmpty()
    }

    @Test
    fun `overlapping presses keep typing order`() {
        k.down("char:a", 1)
        k.down("char:b", 2)
        k.up(2)
        k.up(1)
        assertThat(k.texts).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `a held consonant does not repeat`() {
        k.down("char:b")
        k.advance(3000)
        assertThat(k.outputs).isEmpty()
        k.up()
        assertThat(k.outputs).hasSize(1)
    }

    @Test
    fun `backspace fires on press then repeats after the delay`() {
        k.down("key:BACKSPACE")
        assertThat(k.outputs).hasSize(1)
        k.advance(340)
        assertThat(k.outputs).hasSize(1)
        k.advance(80)
        assertThat(k.outputs.size).isAtLeast(2)
        k.up()
        val count = k.outputs.size
        k.advance(1000)
        assertThat(k.outputs).hasSize(count)
    }

    @Test
    fun `repeat interval is rescheduled after each repeat`() {
        k.down("key:BACKSPACE")
        k.advance(350 + 60 * 3)
        assertThat(k.outputs).hasSize(1 + 4)
    }

    @Test
    fun `sliding off backspace stops repeating`() {
        k.down("key:BACKSPACE")
        k.move(0f, 0f)
        k.advance(2000)
        assertThat(k.outputs).hasSize(1)
    }

    @Test
    fun `repeat can be disabled`() {
        val off = KeyboardHarness(Settings(repeatEnabled = false))
        off.down("key:BACKSPACE")
        off.advance(2000)
        assertThat(off.outputs).hasSize(1)
    }

    @Test
    fun `ctrl survives a page change and is consumed by the next key`() {
        k.tap("header:fn")
        k.tap("mod:CTRL")
        k.tap("page:ABC")
        k.tap("char:c")
        assertThat((k.outputs.last() as OutputEvent.Text).mods).containsExactly(Modifier.CTRL)
        assertThat(k.state.modifier(Modifier.CTRL)).isEqualTo(ModifierState.OFF)
    }

    @Test
    fun `latching a modifier sends nothing`() {
        k.tap("header:fn")
        k.tap("mod:CTRL")
        assertThat(k.outputs).isEmpty()
    }

    @Test
    fun `fn toggles back to the previous page`() {
        k.tap("page:NUMBERS")
        k.tap("header:fn")
        assertThat(k.state.page).isEqualTo(Page.FN)
        k.tap("header:fn")
        assertThat(k.state.page).isEqualTo(Page.NUMBERS)
    }

    @Test
    fun `a dead key returns to the letters page`() {
        k.tap("page:NUMBERS")
        k.tap("page:SYMBOLS")
        k.tap("dead:~")
        assertThat(k.state.page).isEqualTo(Page.ABC)
        assertThat(k.outputs.last()).isEqualTo(OutputEvent.Dead(DeadKey.TILDE))
    }

    @Test
    fun `header mouse buttons hold for the lifetime of the touch`() {
        k.down("mouse:left")
        assertThat(k.commands.last()).isEqualTo(KeyboardCommand.Mouse(MouseButton.LEFT, 1, down = true))
        k.up()
        assertThat(k.commands.last()).isEqualTo(KeyboardCommand.Mouse(MouseButton.LEFT, 1, down = false))
    }

    @Test
    fun `cancelling a mouse touch releases the button`() {
        k.down("mouse:right")
        k.send(KeyboardEvent.Cancel(1))
        assertThat(k.commands.last()).isEqualTo(KeyboardCommand.Mouse(MouseButton.RIGHT, 1, down = false))
    }

    @Test
    fun `mouse buttons are ignored while settings are open`() {
        k.tap("header:settings")
        k.commands.clear()
        k.tap("mouse:left")
        assertThat(k.commands.filterIsInstance<KeyboardCommand.Mouse>()).isEmpty()
    }

    @Test
    fun `settings hide the keyboard and only the header accepts touches`() {
        k.tap("header:settings")
        assertThat(k.state.overlay).isEqualTo(Overlay.SETTINGS)
        assertThat(k.state.activeKeys).hasSize(6)
        assertThat(k.state.pad).isNull()
        k.tap("header:settings")
        assertThat(k.state.overlay).isEqualTo(Overlay.NONE)
    }

    @Test
    fun `choosing the pad view leaves only the header and persists`() {
        k.tap("header:mode")
        assertThat(k.state.overlay).isEqualTo(Overlay.MENU)
        k.send(KeyboardEvent.SelectView(PanelView.PAD))
        assertThat(k.state.view).isEqualTo(PanelView.PAD)
        assertThat(k.state.layout.keys).hasSize(6)
        assertThat(k.commands).contains(KeyboardCommand.PersistView(PanelView.PAD))
    }

    @Test
    fun `caps lock does not add shift to navigation keys`() {
        k.tap("header:fn")
        k.tap("caps")
        k.tap("key:LEFT")
        val event = k.outputs.last() as OutputEvent.Key
        assertThat(event.code).isEqualTo(KeyCode.LEFT)
        assertThat(event.mods).doesNotContain(Modifier.SHIFT)
    }

    @Test
    fun `one-shot shift stays applied for the whole arrow repeat`() {
        k.tap("header:fn")
        k.tap("mod:SHIFT")
        k.down("key:LEFT")
        k.advance(500)
        val mods = k.outputs.map { (it as OutputEvent.Key).mods }
        assertThat(mods.size).isAtLeast(2)
        assertThat(mods.all { Modifier.SHIFT in it }).isTrue()
        k.up()
        assertThat(k.state.shift).isEqualTo(ModifierState.OFF)
    }

    @Test
    fun `candidate taps choose from the current page and stale pages clamp`() {
        val zh = KeyboardHarness(Settings(language = Language.ZH_CN))
        val candidates = (1..13).map { "字$it" }
        zh.send(KeyboardEvent.ImeChanged("ni", candidates, revision = 4))
        zh.tap("ime:next")
        zh.tap("ime:next")
        zh.tap("ime:next")
        assertThat(zh.state.ime.page).isEqualTo(2)
        zh.tap("ime:choice:0")
        assertThat(zh.outputs.last()).isEqualTo(OutputEvent.Choose(12, 4))
        zh.tap("ime:choice:1")
        assertThat(zh.outputs).hasSize(1)
        zh.send(KeyboardEvent.ImeChanged("nih", candidates, revision = 5))
        assertThat(zh.state.ime.page).isEqualTo(0)
    }

    @Test
    fun `changing language resets composition and input`() {
        k.send(KeyboardEvent.ImeChanged("~", emptyList(), 1))
        k.send(KeyboardEvent.SettingsChanged(k.state.settings.copy(language = Language.EN_US)))
        assertThat(k.state.ime).isEqualTo(ImeState())
        assertThat(k.commands).contains(KeyboardCommand.ResetInput)
    }

    @Test
    fun `hide releases everything and asks the host to collapse`() {
        k.tap("mod:SHIFT")
        k.tap("header:hide")
        assertThat(k.state.shift).isEqualTo(ModifierState.OFF)
        assertThat(k.commands).containsAtLeast(KeyboardCommand.ResetInput, KeyboardCommand.Hide).inOrder()
    }

    @Test
    fun `touch timers are cancelled when the press ends`() {
        k.down("char:a")
        k.up()
        assertThat(k.activeTimers).isEmpty()
    }
}
