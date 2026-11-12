package jr.brian.thorkbm.core.swipe

/** A word as typed ([text], e.g. "don't") and the letter keys its swipe passes over ("dont"). */
data class LexiconWord(val text: String, val letters: String, val count: Long)

/**
 * Read-only English word list with spoken-frequency counts, most common first.
 * Data: FrequencyWords / OpenSubtitles 2018, see `swipe-provenance.json`.
 */
class SwipeLexicon private constructor(load: () -> List<Pair<String, Long>>) {
    constructor(words: List<Pair<String, Long>>) : this({ words })

    val words: List<LexiconWord> by lazy {
        load().mapNotNull { (text, count) ->
            val letters = text.lowercase().filter { it in 'a'..'z' }
            if (letters.isEmpty()) null else LexiconWord(text, letters, count)
        }
    }

    /** Parses the data now (call off the main thread) instead of on the first swipe. */
    fun warmUp() {
        words.size
    }

    companion object {
        /** The bundled list (~250 KB), parsed on first use or by [warmUp]. */
        fun bundled(): SwipeLexicon = SwipeLexicon {
            val text = requireNotNull(SwipeLexicon::class.java.getResource("/swipe/en_US.tsv")) { "Missing en_US.tsv" }.readText()
            text.lineSequence().filter { it.isNotEmpty() }.map { line ->
                val (word, count) = line.split('\t')
                word to count.toLong()
            }.toList()
        }
    }
}
