package jr.brian.thorkbm.core.keyboard.layout

import jr.brian.thorkbm.core.keyboard.profile.LanguageProfile
import jr.brian.thorkbm.core.keyboard.profile.UsKeymap
import jr.brian.thorkbm.core.model.DeadKey
import jr.brian.thorkbm.core.model.KeyCode
import jr.brian.thorkbm.core.model.Language
import jr.brian.thorkbm.core.model.Modifier
import jr.brian.thorkbm.core.model.MouseButton
import jr.brian.thorkbm.core.model.Page
import jr.brian.thorkbm.core.model.PanelView
import java.util.concurrent.ConcurrentHashMap

/**
 * Deterministic keyboard geometry in a logical [WIDTH] × [HEIGHT] space (the Thor's lower panel).
 * Drawing and touch hit-testing always use these same rectangles.
 */
object LayoutEngine {
    const val WIDTH = 1240f
    const val HEIGHT = 1080f
    const val CANDIDATES_PER_PAGE = 6

    private const val MARGIN = 12f
    private const val FULL_WIDTH = WIDTH - 2 * MARGIN
    private const val COMBINED_LEFT_WIDTH = 740f
    private const val KEY_GAP = 10f

    val PAD_COMBINED = Rect(764f, 716f, 464f, 340f)
    val PAD_FULL = Rect(12f, 142f, 1216f, 914f)

    private val cache = ConcurrentHashMap<Triple<Page, PanelView, Language>, KeyboardLayout>()

    fun build(page: Page, view: PanelView, language: Language): KeyboardLayout =
        cache.getOrPut(Triple(page, view, language)) { compute(page, view, language) }

    /** The keys present on every view, including while settings are open. */
    fun header(): List<KeySpec> = row(
        listOf(
            KeySpec("mouse:left", KeyAction.Mouse(MouseButton.LEFT), KeyLabel.Localized(LabelId.LEFT_CLICK), icon = KeyIcon.MOUSE_LEFT, style = KeyStyle.SMALL),
            KeySpec("mouse:right", KeyAction.Mouse(MouseButton.RIGHT), KeyLabel.Localized(LabelId.RIGHT_CLICK), icon = KeyIcon.MOUSE_RIGHT, style = KeyStyle.SMALL),
            KeySpec("header:fn", KeyAction.Fn, KeyLabel.Literal("Fn")),
            KeySpec("header:mode", KeyAction.Mode, KeyLabel.Localized(LabelId.MODE), icon = KeyIcon.DROPDOWN, style = KeyStyle.SMALL),
            KeySpec("header:settings", KeyAction.Settings, KeyLabel.None, icon = KeyIcon.GEAR),
            KeySpec("header:hide", KeyAction.Hide, KeyLabel.Localized(LabelId.HIDE), style = KeyStyle.SMALL),
        ),
        y = 16f, h = 90f, weights = listOf(234f, 222f, 190f, 156f, 136f, 218f),
    )

    /**
     * Places the accent popup above [key] (or below it when there is no room) and lays out its options.
     * Returns the outer frame and one key per variant.
     */
    fun popup(key: KeySpec, variants: List<String>): Pair<Rect, List<KeySpec>> {
        val count = variants.size
        val w = minOf(WIDTH - 24, count * 112f + 24 + (count - 1) * KEY_GAP)
        val h = 126f
        val x = maxOf(MARGIN, minOf(key.rect.centerX - w / 2, WIDTH - w - MARGIN))
        var y = key.rect.y - h - MARGIN
        if (y < 118f) y = key.rect.bottom + MARGIN
        y = minOf(HEIGHT - h - MARGIN, y)
        val options = row(
            variants.map { KeySpec("accent:$it", KeyAction.Accent(it), KeyLabel.Literal(it)) },
            y = y + 12, h = h - 24, x = x + 12, w = w - 24, gap = KEY_GAP,
        )
        return Rect(x, y, w, h) to options
    }

    private fun compute(page: Page, view: PanelView, language: Language): KeyboardLayout {
        val keys = header().toMutableList()
        if (view == PanelView.PAD) return KeyboardLayout(keys, PAD_FULL)

        val profile = LanguageProfile.of(language)
        val body = when (page) {
            Page.ABC -> abcRows(profile)
            Page.NUMBERS -> numberRows(profile)
            Page.SYMBOLS -> symbolRows(language, profile)
            Page.FN -> fnRows()
        }
        if (language == Language.ZH_CN && page == Page.ABC) {
            keys += body.map { it.copy(rect = squeezeForCandidates(it.rect)) }
            keys += candidateStrip()
        } else {
            keys += body
        }
        keys += footer(page, if (view == PanelView.COMBINED) COMBINED_LEFT_WIDTH else FULL_WIDTH)

        val finalKeys = if (language == Language.EN_US) keys.map(::withUsCode) else keys
        return KeyboardLayout(finalKeys, if (view == PanelView.COMBINED) PAD_COMBINED else null)
    }

    private fun abcRows(profile: LanguageProfile): List<KeySpec> {
        val (top, middle, bottom) = profile.letterRows
        return row(top.textKeys(), 142f, 172f, gap = KEY_GAP) +
            row(middle.textKeys(), 330f, 172f, gap = KEY_GAP) +
            row(
                listOf(modifierKey(Modifier.SHIFT, icon = KeyIcon.SHIFT)) + bottom.textKeys() + backspace(),
                518f, 172f, weights = edgeWeights(bottom.length), gap = KEY_GAP,
            )
    }

    private fun numberRows(profile: LanguageProfile): List<KeySpec> =
        row("1234567890".textKeys(), 142f, 172f, gap = KEY_GAP) +
            row(profile.numbersRow.textKeys(), 330f, 172f, gap = KEY_GAP) +
            row(
                listOf(pageKey(Page.SYMBOLS, KeyLabel.Localized(LabelId.SYMBOLS))) + "'\":;/?!".textKeys() + backspace(),
                518f, 172f, weights = edgeWeights(7), gap = KEY_GAP,
            )

    private fun symbolRows(language: Language, profile: LanguageProfile): List<KeySpec> {
        val rows = if (language == Language.EN_US) {
            // Every visible character is reachable through the US map; no dead keys or currencies that would fail.
            row("[]{}<>".textKeys(), 142f, 172f, gap = KEY_GAP) +
                row("\\|_`~*^".textKeys(), 330f, 172f, gap = KEY_GAP)
        } else {
            val accents = if (language.supportsDeadKeys) {
                DeadKey.SYMBOLS_ROW.map { KeySpec("dead:${it.spacing}", KeyAction.Dead(it), KeyLabel.Literal(it.spacing)) }
            } else {
                DeadKey.SYMBOLS_ROW.map { textKey(it.spacing) }
            }
            row("\\|_*<>[]{}".textKeys(), 142f, 172f, gap = KEY_GAP) +
                row(accents + profile.extraSymbols.textKeys(), 330f, 172f, gap = KEY_GAP)
        }
        val third = if (language == Language.EN_US) "'\":;?!$" else "«»…–—×÷"
        return rows + row(
            listOf(pageKey(Page.NUMBERS, KeyLabel.Literal("123"))) + third.textKeys() + backspace(),
            518f, 172f, weights = edgeWeights(7), gap = KEY_GAP,
        )
    }

    private fun fnRows(): List<KeySpec> {
        val f = KeyCode.FUNCTION_KEYS
        return row(f.take(6).mapIndexed { i, c -> rawKey(c, KeyLabel.Literal("F${i + 1}")) }, 142f, 124f) +
            row(f.drop(6).mapIndexed { i, c -> rawKey(c, KeyLabel.Literal("F${i + 7}")) }, 282f, 124f) +
            row(
                listOf(
                    rawKey(KeyCode.ESCAPE, KeyLabel.Literal("Esc")),
                    rawKey(KeyCode.TAB, KeyLabel.Literal("Tab")),
                    modifierKey(Modifier.SHIFT, KeyLabel.Literal("Shift")),
                    modifierKey(Modifier.CTRL, KeyLabel.Literal("Ctrl")),
                    modifierKey(Modifier.ALT, KeyLabel.Literal("Alt")),
                    modifierKey(Modifier.SUPER, KeyLabel.Literal("Super")),
                    modifierKey(Modifier.ALTGR, KeyLabel.Literal("AltGr")),
                    rawKey(KeyCode.INSERT, KeyLabel.Localized(LabelId.INSERT)),
                    rawKey(KeyCode.DELETE, KeyLabel.Localized(LabelId.DELETE)),
                ),
                422f, 124f,
            ) +
            row(
                listOf(
                    KeySpec("caps", KeyAction.CapsLock, KeyLabel.Localized(LabelId.CAPS_LOCK)),
                    rawKey(KeyCode.NUM_LOCK, KeyLabel.Localized(LabelId.NUM_LOCK)),
                    rawKey(KeyCode.SCROLL_LOCK, KeyLabel.Localized(LabelId.SCROLL_LOCK)),
                    rawKey(KeyCode.HOME, KeyLabel.Localized(LabelId.HOME)),
                    rawKey(KeyCode.END, KeyLabel.Localized(LabelId.END)),
                    rawKey(KeyCode.PAGE_UP, KeyLabel.Localized(LabelId.PAGE_UP)),
                    rawKey(KeyCode.PAGE_DOWN, KeyLabel.Localized(LabelId.PAGE_DOWN)),
                ),
                562f, 128f,
            )
    }

    private fun footer(page: Page, width: Float): List<KeySpec> {
        val pageSwitch = if (page == Page.ABC) {
            pageKey(Page.NUMBERS, KeyLabel.Literal("123"))
        } else {
            pageKey(Page.ABC, KeyLabel.Literal("ABC"))
        }
        val topRow = row(
            listOf(
                pageSwitch,
                textKey(",", id = "footer:comma"),
                KeySpec("footer:space", KeyAction.Text(" ", KeyCode.SPACE), KeyLabel.Localized(LabelId.SPACE)),
                textKey(".", id = "footer:period"),
            ),
            716f, 154f, w = width, weights = listOf(1.1f, 0.85f, 3.1f, 0.85f),
        )
        val enter = KeySpec("footer:enter", KeyAction.Key(KeyCode.ENTER), KeyLabel.Localized(LabelId.ENTER), icon = KeyIcon.ENTER, style = KeyStyle.ACCENT)
        val bottomRow = if (page == Page.ABC) {
            row(listOf(enter, textKey("@", id = "footer:at"), textKey("/", id = "footer:slash")), 890f, 166f, w = width, weights = listOf(2f, 1.1f, 1.1f))
        } else {
            val arrows = listOf(KeyCode.LEFT to KeyIcon.LEFT, KeyCode.UP to KeyIcon.UP, KeyCode.DOWN to KeyIcon.DOWN, KeyCode.RIGHT to KeyIcon.RIGHT)
                .map { (code, icon) -> rawKey(code, KeyLabel.None, icon) }
            row(listOf(enter) + arrows, 890f, 166f, w = width, weights = listOf(2f, 0.53f, 0.53f, 0.53f, 0.53f))
        }
        return topRow + bottomRow
    }

    private fun candidateStrip(): List<KeySpec> = row(
        listOf(KeySpec("ime:previous", KeyAction.CandidatePage(-1), KeyLabel.Literal("‹"))) +
            (0 until CANDIDATES_PER_PAGE).map { KeySpec("ime:choice:$it", KeyAction.Candidate(it), KeyLabel.None) } +
            KeySpec("ime:next", KeyAction.CandidatePage(1), KeyLabel.Literal("›")),
        154f, 54f, weights = listOf(0.5f) + List(CANDIDATES_PER_PAGE) { 1f } + 0.5f, gap = 8f,
    )

    /** Letter rows move down to make room for the Pinyin candidate strip. */
    private fun squeezeForCandidates(rect: Rect): Rect {
        val scale = 470f / 548f
        return rect.copy(y = 220f + (rect.y - 142f) * scale, h = rect.h * scale)
    }

    private fun withUsCode(key: KeySpec): KeySpec {
        val action = key.action as? KeyAction.Text ?: return key
        val stroke = UsKeymap[action.char] ?: return key
        return key.copy(action = action.copy(code = stroke.code))
    }

    private fun edgeWeights(middle: Int) = listOf(1.3f) + List(middle) { 1f } + 1.8f

    private fun String.textKeys(): List<KeySpec> = map { textKey(it.toString()) }

    private fun textKey(char: String, id: String = "char:$char"): KeySpec =
        KeySpec(id, KeyAction.Text(char, positionCode(char)), KeyLabel.Literal(char))

    private fun rawKey(code: KeyCode, label: KeyLabel, icon: KeyIcon? = null): KeySpec =
        KeySpec("key:${code.name}", KeyAction.Key(code), label, icon = icon)

    private fun backspace() = rawKey(KeyCode.BACKSPACE, KeyLabel.None, KeyIcon.BACKSPACE)

    private fun modifierKey(modifier: Modifier, label: KeyLabel = KeyLabel.None, icon: KeyIcon? = null) =
        KeySpec("mod:${modifier.name}", KeyAction.ModifierKey(modifier), label, icon = icon)

    private fun pageKey(page: Page, label: KeyLabel) = KeySpec("page:${page.name}", KeyAction.GoToPage(page), label)

    /** US physical position for a character, used by shortcuts in every language. */
    private fun positionCode(char: String): KeyCode? = when (char) {
        "ç" -> KeyCode.SEMICOLON
        else -> {
            val stroke = UsKeymap[char]
            stroke?.code?.takeIf { char[0].isLetterOrDigit() || char in POSITIONAL_PUNCTUATION }
        }
    }

    private val POSITIONAL_PUNCTUATION = setOf(",", ".", " ", ";")

    private fun row(
        keys: List<KeySpec>,
        y: Float,
        h: Float,
        x: Float = MARGIN,
        w: Float = FULL_WIDTH,
        weights: List<Float> = List(keys.size) { 1f },
        gap: Float = 12f,
    ): List<KeySpec> {
        require(keys.isNotEmpty() && keys.size == weights.size && weights.all { it > 0 }) { "Invalid row" }
        val unit = (w - gap * (keys.size - 1)) / weights.sum()
        var cursor = x
        return keys.zip(weights) { key, weight ->
            val width = unit * weight
            key.copy(rect = Rect(cursor, y, width, h)).also { cursor += width + gap }
        }
    }
}
