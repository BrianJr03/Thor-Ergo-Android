package jr.brian.thorkbm.core.keyboard.layout

import kotlin.math.max
import kotlin.math.min

/** A point in logical panel units. */
data class Point(val x: Float, val y: Float)

/** A rectangle in logical panel units (see [LayoutEngine.WIDTH] × [LayoutEngine.HEIGHT]). */
data class Rect(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h
    val centerX: Float get() = x + w / 2
    val centerY: Float get() = y + h / 2

    /** Half-open, so adjacent keys never both claim a touch on their shared edge. */
    fun contains(px: Float, py: Float): Boolean = px >= x && px < right && py >= y && py < bottom

    fun overlaps(other: Rect): Boolean =
        max(x, other.x) < min(right, other.right) - EPSILON &&
            max(y, other.y) < min(bottom, other.bottom) - EPSILON

    private companion object {
        const val EPSILON = 1e-3f
    }
}
