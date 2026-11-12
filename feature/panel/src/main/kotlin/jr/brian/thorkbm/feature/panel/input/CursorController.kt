package jr.brian.thorkbm.feature.panel.input

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class Cursor(val x: Float, val y: Float, val pressed: Boolean = false)

/** The pointer position on the upper display, clamped to its bounds. Drawn by the cursor overlay. */
@Singleton
class CursorController @Inject constructor() {
    private val _cursor = MutableStateFlow(Cursor(0f, 0f))
    val cursor: StateFlow<Cursor> = _cursor.asStateFlow()

    private var width = 1f
    private var height = 1f

    /** Called when the upper display appears or changes size; recentres the pointer the first time. */
    fun setBounds(width: Int, height: Int) {
        val first = this.width <= 1f
        this.width = width.toFloat().coerceAtLeast(1f)
        this.height = height.toFloat().coerceAtLeast(1f)
        _cursor.update { if (first) it.copy(x = this.width / 2, y = this.height / 2) else clamp(it) }
    }

    fun moveBy(dx: Int, dy: Int): Cursor {
        _cursor.update { clamp(it.copy(x = it.x + dx, y = it.y + dy)) }
        return _cursor.value
    }

    fun setPressed(pressed: Boolean) = _cursor.update { it.copy(pressed = pressed) }

    private fun clamp(c: Cursor) = c.copy(x = c.x.coerceIn(0f, width - 1), y = c.y.coerceIn(0f, height - 1))
}
