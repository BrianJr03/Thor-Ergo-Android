package com.thorergo.core.keyboard.layout

import com.thorergo.core.keyboard.profile.LanguageProfile
import com.thorergo.core.keyboard.profile.UsKeymap
import com.thorergo.core.model.Language
import com.thorergo.core.model.ModifierState

/**
 * The single source of truth for both the character a text key types and the legend it shows.
 *
 * Letters follow Shift and Caps Lock. In US only, a one-shot Shift also maps punctuation
 * (1 → !); Caps Lock never changes punctuation.
 */
object KeyText {
    fun of(action: KeyAction.Text, language: Language, shift: ModifierState): String {
        val char = action.char
        if (LanguageProfile.hasCase(char)) {
            return if (shift != ModifierState.OFF) LanguageProfile.upper(char) else char.lowercase()
        }
        if (language == Language.EN_US && shift == ModifierState.ONE_SHOT) return UsKeymap.shiftedPunctuation(char)
        return char
    }
}
