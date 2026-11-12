package jr.brian.thorkbm.core.keyboard.output

import com.google.common.truth.Truth.assertThat
import jr.brian.thorkbm.core.model.ComposerOutput
import jr.brian.thorkbm.core.model.DeadKey
import jr.brian.thorkbm.core.model.KeyCode
import org.junit.Test

class DeadKeyComposerTest {
    private fun compose(dead: DeadKey, text: String): DeadKeyResult {
        val pending = DeadKeyComposer.handle(null, DeadKeyInput.Dead(dead)).pending
        return DeadKeyComposer.handle(pending, DeadKeyInput.Text(text))
    }

    @Test
    fun `every accent pair composes in both cases`() {
        val cases = listOf(
            Triple(DeadKey.ACUTE, "aeiou", "áéíóú"), Triple(DeadKey.GRAVE, "a", "à"),
            Triple(DeadKey.CIRCUMFLEX, "aeo", "âêô"), Triple(DeadKey.TILDE, "ao", "ãõ"),
            Triple(DeadKey.DIAERESIS, "u", "ü"),
        )
        for ((dead, plain, accented) in cases) {
            plain.zip(accented).forEach { (a, b) ->
                assertThat(compose(dead, a.toString()).outputs).containsExactly(ComposerOutput.Commit(b.toString()))
                assertThat(compose(dead, a.uppercase()).outputs).containsExactly(ComposerOutput.Commit(b.uppercase()))
            }
        }
    }

    @Test
    fun `a letter with no precomposed form keeps the spacing accent`() {
        assertThat(compose(DeadKey.TILDE, "x").outputs).containsExactly(ComposerOutput.Commit("~x"))
    }

    @Test
    fun `space types the accent on its own`() {
        assertThat(compose(DeadKey.TILDE, " ").outputs).containsExactly(ComposerOutput.Commit("~"))
    }

    @Test
    fun `the same dead key twice types it, a different one replaces it`() {
        val twice = DeadKeyComposer.handle(DeadKey.ACUTE, DeadKeyInput.Dead(DeadKey.ACUTE))
        assertThat(twice).isEqualTo(DeadKeyResult(null, listOf(ComposerOutput.Commit("´"))))
        val other = DeadKeyComposer.handle(DeadKey.ACUTE, DeadKeyInput.Dead(DeadKey.TILDE))
        assertThat(other).isEqualTo(DeadKeyResult(DeadKey.TILDE, listOf(ComposerOutput.Commit("´"))))
    }

    @Test
    fun `a literal clears the pending accent`() {
        val result = DeadKeyComposer.handle(DeadKey.TILDE, DeadKeyInput.Literal("á"))
        assertThat(result).isEqualTo(DeadKeyResult(null, listOf(ComposerOutput.Commit("á"))))
    }

    @Test
    fun `backspace and escape cancel only a pending accent`() {
        for (code in listOf(KeyCode.BACKSPACE, KeyCode.ESCAPE)) {
            assertThat(DeadKeyComposer.handle(DeadKey.ACUTE, DeadKeyInput.Key(code))).isEqualTo(DeadKeyResult(null))
            assertThat(DeadKeyComposer.handle(null, DeadKeyInput.Key(code)).outputs).containsExactly(ComposerOutput.Forward(code))
        }
    }

    @Test
    fun `editing keys drop the pending accent and pass through`() {
        val result = DeadKeyComposer.handle(DeadKey.ACUTE, DeadKeyInput.Key(KeyCode.DELETE))
        assertThat(result).isEqualTo(DeadKeyResult(null, listOf(ComposerOutput.Forward(KeyCode.DELETE))))
    }
}
