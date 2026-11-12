package com.thorergo.core.display

import kotlinx.coroutines.flow.Flow

data class DisplayInfo(val id: Int, val name: String, val width: Int, val height: Int)

/** The game display and the companion display that hosts the panel. */
data class DualDisplays(val upper: DisplayInfo, val lower: DisplayInfo)

interface DisplayRepository {
    /** Emits whenever displays are added, removed or resized; `null` while only one screen exists. */
    val displays: Flow<DualDisplays?>
}

/**
 * Picks the panel's display: the built-in default display shows the game, and the first other
 * display is the companion screen. This matches the AYN Thor, where the lower panel is display 1+.
 */
internal fun pairDisplays(all: List<DisplayInfo>, defaultId: Int): DualDisplays? {
    val upper = all.firstOrNull { it.id == defaultId } ?: return null
    val lower = all.filter { it.id != defaultId }.minByOrNull { it.id } ?: return null
    return DualDisplays(upper, lower)
}
