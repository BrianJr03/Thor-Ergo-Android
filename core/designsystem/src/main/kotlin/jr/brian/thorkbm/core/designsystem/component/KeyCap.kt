package jr.brian.thorkbm.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
            .border(1.dp, border, shape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
