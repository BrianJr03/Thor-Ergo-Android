package com.thorergo.core.keyboard.profile

import com.thorergo.core.model.Language

/** A national mobile layout. Letters are stored lower-case; shifted forms come from [upper]. */
data class LanguageProfile(
    val letterRows: List<String>,
    /** Long-press variants per base letter, lower-case. */
    val accents: Map<String, List<String>>,
    /** Five extra characters on the symbols page (unused by en_US, which has its own page). */
    val extraSymbols: String,
    /** Second row of the numbers page. */
    val numbersRow: String,
) {
    fun accentsFor(char: String): List<String> = accents[char.lowercase()].orEmpty()

    companion object {
        private const val EURO_NUMBERS = "@#€%&-+=()"
        private const val DOLLAR_NUMBERS = "@#$%&-+=()"

        private val profiles: Map<Language, LanguageProfile> = mapOf(
            Language.PT_PT to LanguageProfile(
                listOf("qwertyuiop", "asdfghjklç", "zxcvbnm"),
                mapOf(
                    "a" to listOf("á", "à", "â", "ã"), "e" to listOf("é", "ê"), "i" to listOf("í"),
                    "o" to listOf("ó", "ô", "õ"), "u" to listOf("ú", "ü"),
                ),
                "$£¥ºª", EURO_NUMBERS,
            ),
            Language.EN_US to LanguageProfile(
                listOf("qwertyuiop", "asdfghjkl;", "zxcvbnm"), emptyMap(), "", DOLLAR_NUMBERS,
            ),
            Language.ES_ES to LanguageProfile(
                listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm"),
                mapOf(
                    "a" to listOf("á"), "e" to listOf("é"), "i" to listOf("í"), "o" to listOf("ó"),
                    "u" to listOf("ú", "ü"), "n" to listOf("ñ"),
                ),
                "¿¡€ºª", EURO_NUMBERS,
            ),
            Language.FR_FR to LanguageProfile(
                listOf("azertyuiop", "qsdfghjklm", "wxcvbnç"),
                mapOf(
                    "a" to listOf("à", "â", "æ"), "c" to listOf("ç"), "e" to listOf("é", "è", "ê", "ë"),
                    "i" to listOf("î", "ï"), "o" to listOf("ô", "œ"), "u" to listOf("ù", "û", "ü"),
                    "y" to listOf("ÿ"),
                ),
                "éèàùœ", EURO_NUMBERS,
            ),
            Language.DE_DE to LanguageProfile(
                listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnmß"),
                mapOf("a" to listOf("ä"), "o" to listOf("ö"), "u" to listOf("ü"), "s" to listOf("ß")),
                "äöüß€", EURO_NUMBERS,
            ),
            Language.IT_IT to LanguageProfile(
                listOf("qwertyuiop", "asdfghjklò", "zxcvbnm"),
                mapOf(
                    "a" to listOf("à"), "e" to listOf("è", "é"), "i" to listOf("ì"),
                    "o" to listOf("ò", "ó"), "u" to listOf("ù"),
                ),
                "àèìòù", EURO_NUMBERS,
            ),
            Language.ZH_CN to LanguageProfile(
                listOf("qwertyuiop", "asdfghjkl。", "zxcvbnm"), emptyMap(), "，。？！、", DOLLAR_NUMBERS,
            ),
        )

        fun of(language: Language): LanguageProfile = profiles.getValue(language)

        /** A single sharp-s key must not type two letters, so ß shifts to the capital ẞ. */
        fun upper(text: String): String = if (text == "ß") "ẞ" else text.uppercase()

        /** Ordinal indicators look alphabetic but have no case. */
        fun hasCase(char: String): Boolean =
            char.length == 1 && char[0].isLetter() && char != "º" && char != "ª"
    }
}
