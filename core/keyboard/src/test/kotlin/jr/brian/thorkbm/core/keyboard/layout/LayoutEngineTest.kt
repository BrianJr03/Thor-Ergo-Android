package jr.brian.thorkbm.core.keyboard.layout

import com.google.common.truth.Truth.assertThat
import jr.brian.thorkbm.core.keyboard.profile.LanguageProfile
import jr.brian.thorkbm.core.model.KeyCode
import jr.brian.thorkbm.core.model.Language
import jr.brian.thorkbm.core.model.ModifierState
import jr.brian.thorkbm.core.model.Page
import jr.brian.thorkbm.core.model.PanelView
import org.junit.Test

class LayoutEngineTest {
    private fun build(page: Page = Page.ABC, view: PanelView = PanelView.COMBINED, language: Language = Language.PT_PT) =
        LayoutEngine.build(page, view, language)

    private fun KeyboardLayout.textAt(y: Float) =
        keys.filter { it.rect.y == y && it.action is KeyAction.Text }.joinToString("") { (it.action as KeyAction.Text).char }

    private fun allLayouts() = Language.entries.flatMap { lang ->
        Page.entries.flatMap { page -> PanelView.entries.map { view -> Triple(lang, page, view) } }
    }

    @Test
    fun `letter rows match the profile exactly`() {
        val layout = build()
        assertThat(layout.textAt(142f)).isEqualTo("qwertyuiop")
        assertThat(layout.textAt(330f)).isEqualTo("asdfghjklç")
        assertThat(layout.textAt(518f)).isEqualTo("zxcvbnm")
    }

    @Test
    fun `every language lays out its own letter rows`() {
        for (language in Language.entries) {
            val rows = LanguageProfile.of(language).letterRows
            val layout = build(language = language)
            val letters = layout.keys.filter { it.id.startsWith("char:") && it.rect.y < 716f }
                .joinToString("") { (it.action as KeyAction.Text).char }
            assertThat(letters).isEqualTo(rows.joinToString(""))
        }
    }

    @Test
    fun `enter, at and slash share the bottom row with a wide enter`() {
        val layout = build()
        val enter = layout.find("footer:enter")!!.rect
        val at = layout.find("footer:at")!!.rect
        val slash = layout.find("footer:slash")!!.rect
        assertThat(enter.x).isLessThan(at.x)
        assertThat(at.x).isLessThan(slash.x)
        assertThat(setOf(enter.y, at.y, slash.y)).hasSize(1)
        assertThat(enter.w).isGreaterThan(at.w * 1.6f)
    }

    @Test
    fun `arrows exist only on the alternative pages`() {
        val arrows = setOf(KeyCode.LEFT, KeyCode.UP, KeyCode.DOWN, KeyCode.RIGHT)
        for (page in Page.entries) {
            val count = build(page).keys.count { (it.action as? KeyAction.Key)?.code in arrows }
            assertThat(count).isEqualTo(if (page == Page.ABC) 0 else 4)
        }
    }

    @Test
    fun `no keys overlap each other or the pad in any layout`() {
        for ((language, page, view) in allLayouts()) {
            val layout = build(page, view, language)
            val rects = layout.keys.map { it.rect } + listOfNotNull(layout.pad)
            rects.forEachIndexed { i, a ->
                rects.drop(i + 1).forEach { b -> assertThat(a.overlaps(b)).isFalse() }
            }
        }
    }

    @Test
    fun `all keys stay inside the panel`() {
        for ((language, page, view) in allLayouts()) {
            for (key in build(page, view, language).keys) {
                val r = key.rect
                assertThat(minOf(r.x, r.y, r.w, r.h)).isAtLeast(0f)
                assertThat(r.right).isAtMost(LayoutEngine.WIDTH + 0.01f)
                assertThat(r.bottom).isAtMost(LayoutEngine.HEIGHT + 0.01f)
            }
        }
    }

    @Test
    fun `every key centre hits that key`() {
        for ((language, page, view) in allLayouts()) {
            val layout = build(page, view, language)
            for (key in layout.keys) assertThat(layout.hit(key.rect.centerX, key.rect.centerY)).isEqualTo(key)
        }
    }

    @Test
    fun `the pad is identical on every page and absent in keyboard view`() {
        assertThat(Page.entries.map { build(it).pad }.toSet()).containsExactly(LayoutEngine.PAD_COMBINED)
        assertThat(build(view = PanelView.KEYBOARD).pad).isNull()
        assertThat(build(view = PanelView.PAD).pad).isEqualTo(LayoutEngine.PAD_FULL)
        assertThat(build(view = PanelView.PAD).keys).hasSize(6)
    }

    @Test
    fun `fn page has all twelve large function keys`() {
        val keys = build(Page.FN).keys
        for (n in 1..12) assertThat(keys.count { it.label == KeyLabel.Literal("F$n") }).isEqualTo(1)
        keys.filter { (it.label as? KeyLabel.Literal)?.text?.matches(Regex("F\\d+")) == true }
            .forEach { assertThat(it.rect.w).isGreaterThan(180f) }
    }

    @Test
    fun `fn page has no unlabelled placeholders`() {
        for (key in build(Page.FN).keys) assertThat(key.label != KeyLabel.None || key.icon != null).isTrue()
    }

    @Test
    fun `numbers and symbols cover the full character set`() {
        val values = listOf(Page.NUMBERS, Page.SYMBOLS).flatMap { build(it).keys }
            .mapNotNull { (it.action as? KeyAction.Text)?.char }
        for (c in "@#€%&-+=()'\":;/?!\\|_*<>[]{}$£¥ºª«»…–—×÷") assertThat(values).contains(c.toString())
    }

    @Test
    fun `us symbols are all typeable and carry no dead keys`() {
        val keys = listOf(Page.NUMBERS, Page.SYMBOLS).flatMap { build(it, language = Language.EN_US).keys }
        assertThat(keys.none { it.action is KeyAction.Dead }).isTrue()
        keys.mapNotNull { it.action as? KeyAction.Text }.forEach { assertThat(it.code).isNotNull() }
    }

    @Test
    fun `chinese reserves a candidate strip above the letters`() {
        val layout = build(language = Language.ZH_CN)
        val strip = layout.keys.filter { it.id.startsWith("ime:") }
        assertThat(strip).hasSize(8)
        val firstLetter = layout.find("char:q")!!.rect
        assertThat(firstLetter.y).isEqualTo(220f)
        strip.forEach { assertThat(it.rect.bottom).isAtMost(firstLetter.y) }
    }

    @Test
    fun `accent popups stay on screen and contain their options`() {
        val profile = LanguageProfile.of(Language.FR_FR)
        for (language in Language.entries) {
            val layout = build(language = language)
            for ((letter, accents) in LanguageProfile.of(language).accents) {
                val key = layout.find("char:$letter") ?: continue
                val (frame, options) = LayoutEngine.popup(key, accents)
                assertThat(frame.x).isAtLeast(0f)
                assertThat(frame.y).isAtLeast(118f)
                assertThat(frame.right).isAtMost(LayoutEngine.WIDTH)
                assertThat(frame.bottom).isAtMost(LayoutEngine.HEIGHT)
                options.forEach { assertThat(frame.contains(it.rect.centerX, it.rect.centerY)).isTrue() }
            }
        }
        assertThat(profile.accentsFor("e")).hasSize(4)
    }

    @Test
    fun `letter legends match the typed letter in every shift state`() {
        for (language in Language.entries) {
            for (key in build(language = language).keys) {
                val action = key.action as? KeyAction.Text ?: continue
                if (!LanguageProfile.hasCase(action.char)) continue
                assertThat(KeyText.of(action, language, ModifierState.OFF)).isEqualTo(action.char)
                for (shift in listOf(ModifierState.ONE_SHOT, ModifierState.LOCKED)) {
                    assertThat(KeyText.of(action, language, shift)).isEqualTo(LanguageProfile.upper(action.char))
                }
            }
        }
    }

    @Test
    fun `german sharp s shifts to a single capital`() {
        assertThat(KeyText.of(KeyAction.Text("ß", null), Language.DE_DE, ModifierState.ONE_SHOT)).isEqualTo("ẞ")
    }

    @Test
    fun `ordinal indicators never change case`() {
        for (shift in ModifierState.entries) {
            assertThat(KeyText.of(KeyAction.Text("º", null), Language.PT_PT, shift)).isEqualTo("º")
            assertThat(KeyText.of(KeyAction.Text("ª", null), Language.ES_ES, shift)).isEqualTo("ª")
        }
    }

    @Test
    fun `us punctuation shifts only with one-shot shift`() {
        val one = KeyAction.Text("1", KeyCode.DIGIT_1)
        assertThat(KeyText.of(one, Language.EN_US, ModifierState.ONE_SHOT)).isEqualTo("!")
        assertThat(KeyText.of(one, Language.EN_US, ModifierState.LOCKED)).isEqualTo("1")
        assertThat(KeyText.of(one, Language.PT_PT, ModifierState.ONE_SHOT)).isEqualTo("1")
    }

    @Test
    fun `shortcut positions follow us letters in every language`() {
        val de = build(language = Language.DE_DE)
        assertThat((de.find("char:z")!!.action as KeyAction.Text).code).isEqualTo(KeyCode.Z)
        val fr = build(language = Language.FR_FR)
        assertThat((fr.find("char:m")!!.action as KeyAction.Text).code).isEqualTo(KeyCode.M)
        assertThat((de.find("char:ü")!!.action as KeyAction.Text).code).isNull()
    }
}
