package jr.brian.thorkbm.feature.panel.input

import jr.brian.thorkbm.core.keyboard.layout.Rect
import jr.brian.thorkbm.core.model.MouseButton

/** Which engine owns a finger. Decided at touch-down and never changed, so a key never becomes a pad touch. */
enum class TouchOwner { KEYBOARD, TRACKPAD }

class TouchRouter {
    private val owners = HashMap<Long, TouchOwner>()

    /** [pad] is `null` whenever an overlay (menu, popup, settings) owns every touch. */
    fun down(pointerId: Long, x: Float, y: Float, pad: Rect?): TouchOwner {
        val owner = if (pad != null && pad.contains(x, y)) TouchOwner.TRACKPAD else TouchOwner.KEYBOARD
        owners[pointerId] = owner
        return owner
    }

    fun ownerOf(pointerId: Long): TouchOwner? = owners[pointerId]

    fun up(pointerId: Long): TouchOwner? = owners.remove(pointerId)

    /** Forgets trackpad fingers, e.g. after a page change; keyboard fingers are reset by the reducer. */
    fun dropTrackpad() {
        owners.values.removeAll { it == TouchOwner.TRACKPAD }
    }

    fun clear() = owners.clear()
}

/**
 * Header mouse buttons are reference-counted per finger: the first hold presses,
 * the last release lets go. Returns whether the physical button changed.
 */
class MouseHolds {
    private val holds = HashMap<Long, MouseButton>()

    val any: Boolean get() = holds.isNotEmpty()

    fun press(pointerId: Long, button: MouseButton): Boolean {
        val first = button !in holds.values
        holds[pointerId] = button
        return first
    }

    fun release(pointerId: Long): MouseButton? {
        val button = holds.remove(pointerId) ?: return null
        return button.takeIf { it !in holds.values }
    }

    /** Releases everything; returns the buttons that were down. */
    fun clear(): Set<MouseButton> = holds.values.toSet().also { holds.clear() }
}
