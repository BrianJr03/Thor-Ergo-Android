package com.thorergo.core.keyboard.profile

import com.thorergo.core.model.DeadKey
import com.thorergo.core.model.KeyCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What a pt_PT key yields under a given Shift/Caps/AltGr combination. */
sealed interface KeymapValue {
    data class Text(val text: String) : KeymapValue

    data class Dead(val dead: DeadKey) : KeymapValue
}

/** The standard XKB `pt` table (not Brazilian ABNT2), used for AltGr symbols. */
class PtAltGrKeymap internal constructor(private val levels: Map<KeyCode, List<KeymapValue?>>) {
    fun lookup(code: KeyCode, shift: Boolean, caps: Boolean, altGr: Boolean): KeymapValue? {
        val index = (if (shift) 1 else 0) or (if (caps) 2 else 0) or (if (altGr) 4 else 0)
        return levels[code]?.getOrNull(index)
    }

    @Serializable
    private data class Entry(val text: String? = null, val dead: String? = null)

    companion object {
        private const val RESOURCE = "/keymaps/pt_PT.json"

        val instance: PtAltGrKeymap by lazy {
            val json = requireNotNull(PtAltGrKeymap::class.java.getResource(RESOURCE)) { "Missing $RESOURCE" }.readText()
            parse(json)
        }

        internal fun parse(json: String): PtAltGrKeymap {
            val raw = Json.decodeFromString<Map<String, List<Entry>>>(json)
            val levels = raw.mapNotNull { (code, entries) ->
                val key = KeyCode.fromEvdev(code.toInt()) ?: return@mapNotNull null
                key to entries.map { entry ->
                    when {
                        entry.dead != null -> DeadKey.fromXkb(entry.dead)?.let(KeymapValue::Dead)
                        entry.text != null -> KeymapValue.Text(entry.text)
                        else -> null
                    }
                }
            }.toMap()
            return PtAltGrKeymap(levels)
        }
    }
}
