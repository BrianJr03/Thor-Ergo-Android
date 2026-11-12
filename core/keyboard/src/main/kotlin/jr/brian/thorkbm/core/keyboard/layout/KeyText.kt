package jr.brian.thorkbm.core.keyboard.layout

import jr.brian.thorkbm.core.keyboard.profile.LanguageProfile
import jr.brian.thorkbm.core.keyboard.profile.UsKeymap
import jr.brian.thorkbm.core.model.Language
import jr.brian.thorkbm.core.model.ModifierState

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
