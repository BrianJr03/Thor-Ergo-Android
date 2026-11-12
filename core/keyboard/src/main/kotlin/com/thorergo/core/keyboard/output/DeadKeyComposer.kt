package com.thorergo.core.keyboard.output

import com.thorergo.core.model.ComposerOutput
import com.thorergo.core.model.DeadKey
import com.thorergo.core.model.KeyCode
import java.text.Normalizer

sealed interface DeadKeyInput {
    data class Text(val text: String) : DeadKeyInput

    /** An accent chosen from the popup replaces, rather than doubles, a pending accent. */
    data class Literal(val text: String) : DeadKeyInput

    data class Dead(val dead: DeadKey) : DeadKeyInput

    data class Key(val code: KeyCode) : DeadKeyInput
}

data class DeadKeyResult(val pending: DeadKey?, val outputs: List<ComposerOutput> = emptyList())

/** Accent composition for the European layouts; [pending] is the dead key waiting for a letter. */
object DeadKeyComposer {
    fun handle(pending: DeadKey?, input: DeadKeyInput): DeadKeyResult = when (input) {
        is DeadKeyInput.Literal -> DeadKeyResult(null, listOf(ComposerOutput.Commit(input.text)))
        is DeadKeyInput.Dead -> when (pending) {
            // The same dead key twice types the accent itself.
            input.dead -> DeadKeyResult(null, listOf(ComposerOutput.Commit(input.dead.spacing)))
            null -> DeadKeyResult(input.dead)
            else -> DeadKeyResult(input.dead, listOf(ComposerOutput.Commit(pending.spacing)))
        }
        is DeadKeyInput.Text -> DeadKeyResult(null, listOf(ComposerOutput.Commit(compose(pending, input.text))))
        is DeadKeyInput.Key ->
            if (pending != null && (input.code == KeyCode.BACKSPACE || input.code == KeyCode.ESCAPE)) {
                // Backspace or Escape cancels only the uncommitted accent.
                DeadKeyResult(null)
            } else {
                DeadKeyResult(null, listOf(ComposerOutput.Forward(input.code)))
            }
    }

    private fun compose(pending: DeadKey?, text: String): String {
        if (pending == null) return text
        if (text == " ") return pending.spacing
        val composed = Normalizer.normalize(text + pending.combining, Normalizer.Form.NFC)
        return if (composed.codePointCount(0, composed.length) == 1) composed else pending.spacing + text
    }
}
