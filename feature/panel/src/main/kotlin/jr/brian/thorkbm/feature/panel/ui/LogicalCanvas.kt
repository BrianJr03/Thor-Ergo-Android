package jr.brian.thorkbm.feature.panel.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import jr.brian.thorkbm.core.keyboard.layout.LayoutEngine
import jr.brian.thorkbm.core.keyboard.layout.Rect
import kotlin.math.roundToInt

/** Maps the logical 1240 × 1080 panel space onto the real window, stretching each axis like the original. */
@Immutable
internal data class LogicalScale(val sx: Float, val sy: Float) {
    fun toLogicalX(px: Float) = px / sx

    fun toLogicalY(px: Float) = px / sy

    fun textSize(units: Float, density: Density): TextUnit = with(density) { (units * minOf(sx, sy)).toSp() }

    companion object {
        fun of(widthPx: Int, heightPx: Int) = LogicalScale(widthPx / LayoutEngine.WIDTH, heightPx / LayoutEngine.HEIGHT)
    }
}

/** Places a child of a Box exactly over the logical [rect]. */
internal fun Modifier.place(rect: Rect, scale: LogicalScale): Modifier = this
    .offset { IntOffset((rect.x * scale.sx).roundToInt(), (rect.y * scale.sy).roundToInt()) }
    .layout { measurable, _ ->
        val w = (rect.w * scale.sx).roundToInt()
        val h = (rect.h * scale.sy).roundToInt()
        val placeable = measurable.measure(Constraints.fixed(w, h))
        layout(w, h) { placeable.place(0, 0) }
    }
