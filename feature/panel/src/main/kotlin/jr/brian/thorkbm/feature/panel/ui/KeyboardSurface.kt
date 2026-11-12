package jr.brian.thorkbm.feature.panel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jr.brian.thorkbm.core.designsystem.component.KeyCap
import jr.brian.thorkbm.core.designsystem.component.KeyTone
import jr.brian.thorkbm.core.designsystem.theme.ErgoTheme
import jr.brian.thorkbm.core.keyboard.AccentPopup
import jr.brian.thorkbm.core.keyboard.KeyboardState
import jr.brian.thorkbm.core.keyboard.layout.KeyIcon
import jr.brian.thorkbm.core.keyboard.layout.KeyLabel
import jr.brian.thorkbm.core.keyboard.layout.KeySpec
import jr.brian.thorkbm.core.keyboard.layout.Rect
import jr.brian.thorkbm.feature.panel.PanelTouch

/**
 * Draws the keys, pad and accent popup at their logical rectangles and feeds raw multi-touch to
 * [onTouch]. Hit-testing happens in the reducer against the same rectangles, never in Compose.
 */
@Composable
internal fun KeyboardSurface(state: KeyboardState, scale: LogicalScale, onTouch: (PanelTouch) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(scale) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            val id = change.id.value
                            val x = scale.toLogicalX(change.position.x)
                            val y = scale.toLogicalY(change.position.y)
                            when {
                                change.changedToDownIgnoreConsumed() -> onTouch(PanelTouch.Down(id, x, y, change.uptimeMillis))
                                change.changedToUpIgnoreConsumed() -> onTouch(PanelTouch.Up(id, change.uptimeMillis))
                                change.positionChanged() -> onTouch(PanelTouch.Move(id, x, y, change.uptimeMillis))
                            }
                            change.consume()
                        }
                    }
                }
            },
    ) {
        state.pad?.let { Pad(it, scale) }
        val pressed = state.pressedKeyIds
        for (key in state.activeKeys) {
            Key(key, state, pressed = key.id in pressed, scale = scale)
        }
        state.popup?.let { AccentPopupView(it, scale) }
    }
}

@Composable
private fun Key(key: KeySpec, state: KeyboardState, pressed: Boolean, scale: LogicalScale) {
    val density = LocalDensity.current
    val text = legend(key, state)
    val image = icon(key, state)
    KeyCap(Modifier.place(key.rect, scale), pressed = pressed, tone = tone(key, state), corner = KEY_CORNER) {
        val iconSize = with(density) { (minOf(key.rect.h * 0.42f, 64f) * minOf(scale.sx, scale.sy)).toDp() }
        val glyph: @Composable () -> Unit = {
            if (image != null) Icon(image, contentDescription = text.ifEmpty { null }, tint = ErgoTheme.colors.text, modifier = Modifier.size(iconSize))
        }
        // A dropdown arrow trails its label ("Mode ▾"); every other icon leads.
        val trailing = key.icon == KeyIcon.DROPDOWN
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!trailing) glyph()
            if (text.isNotEmpty()) {
                Text(
                    text,
                    color = ErgoTheme.colors.text,
                    fontSize = scale.textSize(legendSize(key), density),
                    lineHeight = scale.textSize(legendSize(key) * 1.1f, density),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    softWrap = true,
                )
            }
            if (trailing) glyph()
        }
    }
}

@Composable
private fun Pad(rect: Rect, scale: LogicalScale) {
    val colors = ErgoTheme.colors
    val shape = RoundedCornerShape(KEY_CORNER * 1.6f)
    Box(
        Modifier
            .place(rect, scale)
            .clip(shape)
            .background(colors.pad)
            .border(2.dp, colors.padBorder, shape),
    )
}

@Composable
private fun AccentPopupView(popup: AccentPopup, scale: LogicalScale) {
    val density = LocalDensity.current
    val colors = ErgoTheme.colors
    val shape = RoundedCornerShape(KEY_CORNER * 1.4f)
    Box(
        Modifier
            .place(popup.frame, scale)
            .clip(shape)
            .background(colors.background)
            .border(2.dp, colors.accentBorder, shape),
    )
    popup.options.forEachIndexed { index, option ->
        KeyCap(Modifier.place(option.rect, scale), tone = if (index == popup.selected) KeyTone.ACCENT else KeyTone.NORMAL, corner = KEY_CORNER) {
            Text(
                (option.label as? KeyLabel.Literal)?.text.orEmpty(),
                color = colors.text,
                fontSize = scale.textSize(60f, density),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private val KEY_CORNER = 12.dp
