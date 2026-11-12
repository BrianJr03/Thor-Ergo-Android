package jr.brian.thorkbm.feature.panel.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jr.brian.thorkbm.feature.panel.R
import jr.brian.thorkbm.feature.panel.input.CursorController
import kotlin.math.roundToInt

/** The pointer on the upper screen. Its window passes every touch through. */
@Composable
fun CursorOverlay(controller: CursorController) {
    val cursor by controller.cursor.collectAsStateWithLifecycle()
    val sizeDp = with(LocalDensity.current) { SPRITE_PX.toDp() }
    val alpha by animateFloatAsState(if (cursor.idle) 0f else 1f, tween(if (cursor.idle) FADE_OUT_MS else FADE_IN_MS), label = "pointer")
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(if (cursor.pressed) R.drawable.cursor_glove_pressed else R.drawable.cursor_glove_idle),
            contentDescription = null,
            modifier = Modifier
                .offset { IntOffset((cursor.x - HOTSPOT_X).roundToInt(), (cursor.y - HOTSPOT_Y).roundToInt()) }
                .size(sizeDp)
                .alpha(alpha),
        )
    }
}

/** The sprite is drawn 1:1 in pixels; the hotspot is the fingertip of the original artwork. */
private const val SPRITE_PX = 64
private const val HOTSPOT_X = 19
private const val HOTSPOT_Y = 11
private const val FADE_OUT_MS = 400
private const val FADE_IN_MS = 80
