package jr.brian.thorkbm.core.update

/** One block of release-note Markdown. Inline `**bold**`, `` `code` `` and links stay in [text]. */
sealed interface NoteBlock {
    data class Heading(val level: Int, val text: String) : NoteBlock

    /** [marker] is "•" for `-`, `*` and `+` items, or "3." for numbered ones. */
    data class Item(val depth: Int, val marker: String, val text: String) : NoteBlock

    data class Paragraph(val text: String) : NoteBlock

    data class Quote(val text: String) : NoteBlock

    data class Code(val text: String) : NoteBlock
}

/**
 * The small Markdown subset RELEASE_NOTES.md uses: headings, nested lists, quotes, fenced code and
 * paragraphs. Hard-wrapped lines are joined back into their item or paragraph.
 */
object ReleaseNotes {
    private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
    private val ITEM = Regex("""^(\s*)([-*+]|\d+[.)])\s+(.*)$""")

    fun parse(markdown: String): List<NoteBlock> {
        val blocks = mutableListOf<NoteBlock>()
        // The block wrapped lines continue; null after a blank line or a block that cannot continue.
        var open: NoteBlock? = null
        val lines = markdown.replace("\r\n", "\n").lines()
        var i = 0
        fun add(block: NoteBlock) {
            blocks += block
            open = block
        }
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            val heading = HEADING.matchEntire(trimmed)
            val item = ITEM.matchEntire(line)
            when {
                trimmed.startsWith("```") -> {
                    val code = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) code += lines[i++]
                    blocks += NoteBlock.Code(code.joinToString("\n"))
                    open = null
                }
                trimmed.isEmpty() -> open = null
                heading != null -> {
                    blocks += NoteBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
                    open = null
                }
                item != null -> {
                    val indent = item.groupValues[1].replace("\t", "    ").length
                    val marker = item.groupValues[2].let { if (it[0].isDigit()) it.dropLast(1) + "." else "•" }
                    add(NoteBlock.Item((indent / 2).coerceAtMost(3), marker, item.groupValues[3].trim()))
                }
                trimmed.startsWith(">") -> {
                    val text = trimmed.removePrefix(">").trim()
                    val quote = open as? NoteBlock.Quote
                    if (quote != null) replaceLast(blocks, quote.copy(text = "${quote.text} $text").also { open = it })
                    else add(NoteBlock.Quote(text))
                }
                else -> when (val block = open) {
                    is NoteBlock.Item -> replaceLast(blocks, block.copy(text = "${block.text} $trimmed").also { open = it })
                    is NoteBlock.Paragraph -> replaceLast(blocks, block.copy(text = "${block.text} $trimmed").also { open = it })
                    is NoteBlock.Quote -> replaceLast(blocks, block.copy(text = "${block.text} $trimmed").also { open = it })
                    else -> add(NoteBlock.Paragraph(trimmed))
                }
            }
            i++
        }
        return blocks
    }

    private fun replaceLast(blocks: MutableList<NoteBlock>, block: NoteBlock) {
        blocks[blocks.lastIndex] = block
    }
}
