package jr.brian.thorkbm.core.keyboard.output

import jr.brian.thorkbm.core.keyboard.profile.KeymapValue
import jr.brian.thorkbm.core.keyboard.profile.PtAltGrKeymap
import jr.brian.thorkbm.core.keyboard.profile.UsKeymap
import jr.brian.thorkbm.core.model.ComposerOutput
import jr.brian.thorkbm.core.model.DeadKey
import jr.brian.thorkbm.core.model.KeyCode
import jr.brian.thorkbm.core.model.Language
import jr.brian.thorkbm.core.model.Modifier
import jr.brian.thorkbm.core.pinyin.PinyinComposer
import jr.brian.thorkbm.core.pinyin.PinyinInput
import jr.brian.thorkbm.core.pinyin.PinyinState

/** A concrete action for the input injector. */
sealed interface Injection {
    /** Press [mods] then [code], release in reverse order. */
    data class Chord(val code: KeyCode, val mods: Set<Modifier> = emptySet()) : Injection

    /** Commit Unicode text into the focused editor. */
    data class Commit(val text: String) : Injection
}

enum class OutputError {
    NOT_ON_US_KEYBOARD,
    NO_ALTGR_SYMBOL,
    ALTGR_USE_SYMBOLS_PAGE,
    NO_PHYSICAL_SHORTCUT,
    INVALID_CHARACTER,
}

/** Composition in progress for the current language. */
data class ComposerState(
    val pendingDead: DeadKey? = null,
    val pinyin: PinyinState = PinyinState(),
) {
    val preedit: String get() = pendingDead?.spacing ?: pinyin.buffer
}

data class RouteResult(
    val state: ComposerState,
    val injections: List<Injection> = emptyList(),
    val error: OutputError? = null,
)

/**
 * Turns keyboard [OutputEvent]s into [Injection]s for a language:
 * US types physical keys; other layouts commit Unicode through a composer;
 * Ctrl/Alt/Super shortcuts always use US physical positions and never gain Shift from Caps Lock.
 */
class OutputRouter(
    private val pinyin: PinyinComposer,
    private val altGr: PtAltGrKeymap = PtAltGrKeymap.instance,
) {
    fun reset(state: ComposerState): ComposerState =
        ComposerState(pinyin = pinyin.handle(state.pinyin, PinyinInput.Reset).state)

    fun route(state: ComposerState, event: OutputEvent, language: Language): RouteResult = when (event) {
        is OutputEvent.Text -> text(state, event, language)
        is OutputEvent.Literal -> when {
            !event.text.isPrintable() -> RouteResult(state, error = OutputError.INVALID_CHARACTER)
            language.usesPhysicalKeys -> usText(state, event.text)
            else -> compose(state, language, Compose.Literal(event.text))
        }
        is OutputEvent.Dead ->
            if (language.supportsDeadKeys) compose(state, language, Compose.Dead(event.dead)) else RouteResult(state, error = OutputError.NOT_ON_US_KEYBOARD)
        is OutputEvent.Choose ->
            if (language.usesPinyin) compose(state, language, Compose.Choose(event.index, event.revision)) else RouteResult(state)
        is OutputEvent.Key -> when {
            language.usesPhysicalKeys -> RouteResult(state, listOf(Injection.Chord(event.code, event.mods)))
            event.mods.isEmpty() -> compose(state, language, Compose.Key(event.code))
            // A shortcut ends any composition first so earlier text is never reordered after it.
            else -> RouteResult(reset(state), listOf(Injection.Chord(event.code, event.mods)))
        }
    }

    private fun text(state: ComposerState, event: OutputEvent.Text, language: Language): RouteResult {
        var text = event.text
        val shortcut = event.mods.any { it == Modifier.CTRL || it == Modifier.ALT || it == Modifier.SUPER }
        if (Modifier.ALTGR in event.mods && !shortcut) {
            if (language != Language.PT_PT) {
                return if (language.usesPhysicalKeys) usText(state, text) else RouteResult(state, error = OutputError.ALTGR_USE_SYMBOLS_PAGE)
            }
            val code = event.code ?: return RouteResult(state, error = OutputError.NO_ALTGR_SYMBOL)
            when (val value = altGr.lookup(code, shift = event.shift, caps = event.caps, altGr = true)) {
                is KeymapValue.Dead -> return compose(state, language, Compose.Dead(value.dead))
                is KeymapValue.Text -> text = value.text
                null -> return RouteResult(state, error = OutputError.NO_ALTGR_SYMBOL)
            }
        }
        if (shortcut) return shortcut(state, event, text, language)
        if (!text.isPrintable()) return RouteResult(state, error = OutputError.INVALID_CHARACTER)
        return if (language.usesPhysicalKeys) usText(state, text) else compose(state, language, Compose.Text(text))
    }

    private fun shortcut(state: ComposerState, event: OutputEvent.Text, text: String, language: Language): RouteResult {
        var mods = if (event.caps) event.mods - Modifier.SHIFT else event.mods
        var code = event.code
        val stroke = UsKeymap[text]
        // Explicit US symbols such as @ need their Shift even inside a shortcut.
        if ((language.usesPhysicalKeys && stroke != null && !text[0].isLetter()) || code == null) {
            stroke ?: return RouteResult(state, error = OutputError.NO_PHYSICAL_SHORTCUT)
            code = stroke.code
            if (stroke.shift) mods = mods + Modifier.SHIFT
        }
        val next = if (language.usesPhysicalKeys) state else reset(state)
        return RouteResult(next, listOf(Injection.Chord(code, mods.sortedBy { it.ordinal }.toSet())))
    }

    private fun usText(state: ComposerState, text: String): RouteResult {
        val stroke = UsKeymap[text] ?: return RouteResult(state, error = OutputError.NOT_ON_US_KEYBOARD)
        return RouteResult(state, listOf(Injection.Chord(stroke.code, if (stroke.shift) setOf(Modifier.SHIFT) else emptySet())))
    }

    private sealed interface Compose {
        data class Text(val text: String) : Compose

        data class Literal(val text: String) : Compose

        data class Dead(val dead: DeadKey) : Compose

        data class Key(val code: KeyCode) : Compose

        data class Choose(val index: Int, val revision: Int) : Compose
    }

    private fun compose(state: ComposerState, language: Language, input: Compose): RouteResult {
        val (next, outputs) = if (language.usesPinyin) {
            val pinyinInput = when (input) {
                is Compose.Text -> PinyinInput.Text(input.text)
                is Compose.Literal -> PinyinInput.Literal(input.text)
                is Compose.Key -> PinyinInput.Key(input.code)
                is Compose.Choose -> PinyinInput.Choose(input.index, input.revision)
                is Compose.Dead -> return RouteResult(state, error = OutputError.NOT_ON_US_KEYBOARD)
            }
            val result = pinyin.handle(state.pinyin, pinyinInput)
            state.copy(pinyin = result.state) to result.outputs
        } else {
            val deadInput = when (input) {
                is Compose.Text -> DeadKeyInput.Text(input.text)
                is Compose.Literal -> DeadKeyInput.Literal(input.text)
                is Compose.Key -> DeadKeyInput.Key(input.code)
                is Compose.Dead -> DeadKeyInput.Dead(input.dead)
                is Compose.Choose -> return RouteResult(state)
            }
            val result = DeadKeyComposer.handle(state.pendingDead, deadInput)
            state.copy(pendingDead = result.pending) to result.outputs
        }
        return RouteResult(next, outputs.map(::toInjection))
    }

    private fun toInjection(output: ComposerOutput): Injection = when (output) {
        is ComposerOutput.Commit -> Injection.Commit(output.text)
        is ComposerOutput.Forward -> Injection.Chord(output.code)
    }

    private fun String.isPrintable(): Boolean =
        codePointCount(0, length) == 1 && !Character.isISOControl(codePointAt(0)) && !Character.isWhitespace(codePointAt(0)) || this == " "
}
