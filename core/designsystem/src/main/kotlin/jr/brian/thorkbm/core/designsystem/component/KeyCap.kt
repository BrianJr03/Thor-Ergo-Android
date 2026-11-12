package jr.brian.thorkbm.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jr.brian.thorkbm.core.designsystem.theme.ErgoTheme

enum class KeyTone { NORMAL, ACCENT, LATCHED }

/** The rounded, softly lit key surface shared by the keyboard, header and settings buttons. */
@Composable
fun KeyCap(
    modifier: Modifier = Modifier,
    pressed: Boolean = false,
    tone: KeyTone = KeyTone.NORMAL,
    corner: Dp = 12.dp,
    /** Off while swipe typing, so keys under a sliding finger stay put and line up with its trail. */
    animatePress: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = ErgoTheme.colors
    val shape = RoundedCornerShape(corner)
    val brush = when {
        pressed -> colors.keyPressed
        tone == KeyTone.ACCENT || tone == KeyTone.LATCHED -> colors.accent
        else -> colors.key
    }
    val border = if (tone == KeyTone.NORMAL) colors.keyBorder else colors.accentBorder
    // Press-down: the key shrinks slightly and sinks a few dp, then eases back on release.
    val sink = pressed && animatePress
    val pressScale by animateFloatAsState(if (sink) 0.92f else 1f, tween(PRESS_MILLIS), label = "pressScale")
    val pressOffset by animateDpAsState(if (sink) 3.dp else 0.dp, tween(PRESS_MILLIS), label = "pressOffset")
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                translationY = pressOffset.toPx()
            }
            .clip(shape)
            .background(brush)
            .border(1.dp, border, shape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

private const val PRESS_MILLIS = 100
