package jr.brian.thorkbm.core.model

/** What an in-process text composer (dead keys, Pinyin) asks to send to the target. */
sealed interface ComposerOutput {
    data class Commit(val text: String) : ComposerOutput

    /** A key the composer did not use, to be sent to the target as-is. */
    data class Forward(val code: KeyCode) : ComposerOutput
}
