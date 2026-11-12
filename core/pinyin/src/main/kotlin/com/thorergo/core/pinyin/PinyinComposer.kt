package com.thorergo.core.pinyin

import com.thorergo.core.model.ComposerOutput
import com.thorergo.core.model.KeyCode

/** A candidate and how many buffer characters choosing it consumes. */
data class PinyinCandidate(val text: String, val consume: Int)

data class PinyinState(
    val buffer: String = "",
    val options: List<PinyinCandidate> = emptyList(),
    val revision: Int = 0,
) {
    val candidates: List<String> get() = options.map { it.text }
}

sealed interface PinyinInput {
    data class Text(val text: String) : PinyinInput

    /** Typed exactly as given (an accent popup choice); cancels pending conversion. */
    data class Literal(val text: String) : PinyinInput

    data class Key(val code: KeyCode) : PinyinInput

    data class Choose(val index: Int, val revision: Int) : PinyinInput

    data object Reset : PinyinInput
}

data class PinyinResult(val state: PinyinState, val outputs: List<ComposerOutput> = emptyList())

/**
 * Deterministic syllable and curated-phrase conversion. Not predictive and never learns from typing.
 * Prefers full-buffer words, then one syllable, then the longest valid syllable prefix; an apostrophe
 * separates syllables explicitly (xi'an).
 */
class PinyinComposer(private val dictionary: PinyinDictionary) {
    fun handle(state: PinyinState, input: PinyinInput): PinyinResult = when (input) {
        PinyinInput.Reset -> PinyinResult(reset(state))
        is PinyinInput.Choose ->
            // A late tap must never select a different character.
            if (input.revision != state.revision || input.index !in state.options.indices) {
                PinyinResult(state)
            } else {
                choose(state, input.index)
            }
        is PinyinInput.Literal -> PinyinResult(reset(state), listOf(ComposerOutput.Commit(input.text)))
        is PinyinInput.Text -> text(state, input.text)
        is PinyinInput.Key -> key(state, input.code)
    }

    private fun text(state: PinyinState, text: String): PinyinResult {
        val c = text.singleOrNull()
        val buffer = state.buffer
        return when {
            c != null && (c in 'a'..'z' || c in 'A'..'Z' || (c == '\'' && buffer.isNotEmpty())) ->
                if (buffer.length < LIMIT) PinyinResult(refresh(state.copy(buffer = buffer + text, revision = state.revision + 1))) else PinyinResult(state)
            text == " " && buffer.isNotEmpty() ->
                if (state.options.isNotEmpty()) choose(state, 0) else PinyinResult(reset(state), listOf(ComposerOutput.Commit(buffer)))
            // A symbol never silently picks a Han candidate: keep the Latin spelling before it.
            else -> PinyinResult(reset(state), listOf(ComposerOutput.Commit(buffer + text)))
        }
    }

    private fun key(state: PinyinState, code: KeyCode): PinyinResult {
        if (state.buffer.isNotEmpty()) {
            when (code) {
                KeyCode.BACKSPACE -> return PinyinResult(refresh(state.copy(buffer = state.buffer.dropLast(1), revision = state.revision + 1)))
                KeyCode.ESCAPE -> return PinyinResult(reset(state))
                // Enter accepts the literal Latin spelling; another Enter sends Return.
                KeyCode.ENTER -> return PinyinResult(reset(state), listOf(ComposerOutput.Commit(state.buffer)))
                else -> Unit
            }
        }
        return PinyinResult(reset(state), listOf(ComposerOutput.Forward(code)))
    }

    private fun choose(state: PinyinState, index: Int): PinyinResult {
        val option = state.options[index]
        val rest = state.buffer.drop(option.consume).trimStart('\'')
        return PinyinResult(refresh(state.copy(buffer = rest, revision = state.revision + 1)), listOf(ComposerOutput.Commit(option.text)))
    }

    private fun reset(state: PinyinState) = PinyinState(revision = state.revision + 1)

    private fun refresh(state: PinyinState): PinyinState {
        val raw = state.buffer.lowercase()
        val normalized = raw.replace("'", "")
        val results = LinkedHashMap<String, Int>()
        fun add(text: String, consume: Int) {
            if (text.isNotEmpty()) results.putIfAbsent(text, consume)
        }
        dictionary.phrases(normalized).forEach { add(it, raw.length) }
        if ('\'' !in raw) dictionary.characters(normalized).forEach { add(it, raw.length) }
        if (results.isEmpty() && raw.isNotEmpty()) {
            val first = raw.substringBefore('\'')
            // A full syllable is a safer prefix than a phrase ending mid-syllable.
            val length = (minOf(MAX_SYLLABLE, first.length) downTo 1).firstOrNull { dictionary.hasSyllable(first.take(it)) }
            if (length != null) {
                val prefix = first.take(length)
                val consume = length + if (raw.getOrNull(length) == '\'') 1 else 0
                dictionary.phrases(prefix).forEach { add(it, consume) }
                dictionary.characters(prefix).forEach { add(it, consume) }
            }
        }
        return state.copy(options = results.map { (text, consume) -> PinyinCandidate(text, consume) })
    }

    companion object {
        const val LIMIT = 64
        private const val MAX_SYLLABLE = 6
    }
}
