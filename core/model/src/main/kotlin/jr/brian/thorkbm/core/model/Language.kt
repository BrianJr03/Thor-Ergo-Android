package jr.brian.thorkbm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Keyboard layouts, which double as the selectable interface languages. */
@Serializable
enum class Language(val tag: String, val displayName: String) {
    @SerialName("pt_PT") PT_PT("pt-PT", "Português (Portugal)"),
    @SerialName("en_US") EN_US("en-US", "English (US)"),
    @SerialName("es_ES") ES_ES("es-ES", "Español (España)"),
    @SerialName("fr_FR") FR_FR("fr-FR", "Français (France)"),
    @SerialName("de_DE") DE_DE("de-DE", "Deutsch (Deutschland)"),
    @SerialName("it_IT") IT_IT("it-IT", "Italiano (Italia)"),
    @SerialName("zh_CN") ZH_CN("zh-CN", "简体中文 (拼音)"),
    ;

    /** US types through physical key events; every other layout commits Unicode text. */
    val usesPhysicalKeys: Boolean get() = this == EN_US

    val usesPinyin: Boolean get() = this == ZH_CN

    /** Layouts whose symbols page carries composing dead keys. */
    val supportsDeadKeys: Boolean get() = this != EN_US && this != ZH_CN
}
