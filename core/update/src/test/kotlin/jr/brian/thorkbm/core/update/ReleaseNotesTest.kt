package jr.brian.thorkbm.core.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReleaseNotesTest {
    @Test
    fun `wrapped list items are joined back together`() {
        val notes = """
            ## 2.1

            - **OLED black theme:** pure-black backgrounds and near-black keys for AMOLED
              screens. Settings → Screen → OLED black theme.
            - Keys now press down when touched.
              - Except while swipe typing is on.
            1. First
        """.trimIndent()
        assertThat(ReleaseNotes.parse(notes)).containsExactly(
            NoteBlock.Heading(2, "2.1"),
            NoteBlock.Item(0, "•", "**OLED black theme:** pure-black backgrounds and near-black keys for AMOLED screens. Settings → Screen → OLED black theme."),
            NoteBlock.Item(0, "•", "Keys now press down when touched."),
            NoteBlock.Item(1, "•", "Except while swipe typing is on."),
            NoteBlock.Item(0, "1.", "First"),
        ).inOrder()
    }

    @Test
    fun `paragraphs, quotes and code blocks`() {
        val notes = "First line\nsecond line.\n\n> Quoted\n> more\n\n```\n- not a list\n```\nAfter"
        assertThat(ReleaseNotes.parse(notes)).containsExactly(
            NoteBlock.Paragraph("First line second line."),
            NoteBlock.Quote("Quoted more"),
            NoteBlock.Code("- not a list"),
            NoteBlock.Paragraph("After"),
        ).inOrder()
    }
}
