package jr.brian.thorkbm.core.data

import androidx.datastore.core.CorruptionException
import com.google.common.truth.Truth.assertThat
import jr.brian.thorkbm.core.model.Language
import jr.brian.thorkbm.core.model.PanelView
import jr.brian.thorkbm.core.model.Settings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream

class SettingsSerializerTest {
    private suspend fun read(json: String) = SettingsSerializer.readFrom(json.byteInputStream())

    @Test
    fun `round trips every field`() = runTest {
        val settings = Settings(language = Language.DE_DE, uiLanguage = Language.FR_FR, view = PanelView.PAD, friction = 2, swipeTyping = true, screenToggleKeyCode = 131)
        val out = ByteArrayOutputStream()
        SettingsSerializer.writeTo(settings, out)
        assertThat(read(out.toString())).isEqualTo(settings)
    }

    @Test
    fun `missing and unknown keys fall back to defaults`() = runTest {
        assertThat(read("""{"language":"pt_PT","legacy":42}""")).isEqualTo(Settings(language = Language.PT_PT))
    }

    @Test
    fun `out of range values are clamped`() = runTest {
        assertThat(read("""{"longPressMs":-1,"friction":100000}""")).isEqualTo(Settings(longPressMs = 250, friction = 10))
    }

    @Test
    fun `unknown enum values fall back to defaults`() = runTest {
        assertThat(read("""{"language":"pt_BR","view":"bad"}""")).isEqualTo(Settings())
    }

    @Test
    fun `a string cannot enable a boolean`() = runTest {
        assertThat(read("""{"screenToggleEnabled":"true","glide":0}""")).isEqualTo(Settings())
    }

    @Test
    fun `garbage is reported as corruption`() {
        assertThrows(CorruptionException::class.java) { runBlocking { read("{not json") } }
    }
}
