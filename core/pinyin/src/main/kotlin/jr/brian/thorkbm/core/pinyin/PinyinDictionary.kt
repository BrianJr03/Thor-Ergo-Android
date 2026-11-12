package jr.brian.thorkbm.core.pinyin

import kotlinx.serialization.json.Json

/**
 * Read-only offline dictionaries: toneless syllables (ü written as v) to characters in preferred
 * order, plus a small curated phrase list. Data: xpinyin / Unihan, see `pinyin-provenance.json`.
 */
class PinyinDictionary private constructor(
    loadSyllables: () -> Map<String, String>,
    loadPhrases: () -> Map<String, List<String>>,
) {
    constructor(syllables: Map<String, String>, phrases: Map<String, List<String>>) : this({ syllables }, { phrases })

    private val syllables by lazy(loadSyllables)
    private val phrases by lazy(loadPhrases)

    /** Parses the data now (call off the main thread) instead of on the first keystroke. */
    fun warmUp() {
        syllables.size
        phrases.size
    }

    fun hasSyllable(syllable: String): Boolean = syllable in syllables

    /** Characters for [syllable], split by code point: the data includes supplementary-plane characters. */
    fun characters(syllable: String): List<String> = syllables[syllable]?.codePointStrings().orEmpty()

    fun phrases(spelling: String): List<String> = phrases[spelling].orEmpty()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The bundled data, parsed (~170 KB) on first use or by [warmUp]. */
        fun bundled(): PinyinDictionary = PinyinDictionary(
            { json.decodeFromString(read("pinyin-index.json")) },
            { json.decodeFromString(read("pinyin-phrases.json")) },
        )

        private fun read(name: String) =
            requireNotNull(PinyinDictionary::class.java.getResource("/pinyin/$name")) { "Missing $name" }.readText()

        private fun String.codePointStrings(): List<String> {
            val result = ArrayList<String>(length)
            var i = 0
            while (i < length) {
                val next = offsetByCodePoints(i, 1)
                result += substring(i, next)
                i = next
            }
            return result
        }
    }
}
