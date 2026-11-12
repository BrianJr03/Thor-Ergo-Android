package jr.brian.thorkbm.feature.panel.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jr.brian.thorkbm.core.designsystem.LocalizedContent
import jr.brian.thorkbm.core.designsystem.R
import jr.brian.thorkbm.core.designsystem.component.ErgoButton
import jr.brian.thorkbm.core.designsystem.component.KeyTone
import jr.brian.thorkbm.core.designsystem.theme.ErgoTheme
import jr.brian.thorkbm.core.injection.PanelPower
import jr.brian.thorkbm.core.keyboard.Overlay
import jr.brian.thorkbm.core.keyboard.layout.LayoutEngine
import jr.brian.thorkbm.core.model.PanelView
import jr.brian.thorkbm.feature.panel.PanelEffect
import jr.brian.thorkbm.feature.panel.PanelIntent
import jr.brian.thorkbm.feature.panel.PanelTouch
import jr.brian.thorkbm.feature.panel.PanelUiState
import jr.brian.thorkbm.feature.panel.PanelViewModel
import jr.brian.thorkbm.feature.settings.SettingsContent
import kotlinx.coroutines.flow.Flow

@Composable
fun PanelRoute(viewModel: PanelViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HapticEffects(viewModel.effect, enabled = state.keyboard.settings.haptics)
    ErgoTheme {
        LocalizedContent(state.keyboard.settings.effectiveUiLanguage) {
            PanelScreen(state, viewModel::onIntent)
        }
    }
}

@Composable
private fun HapticEffects(effects: Flow<PanelEffect>, enabled: Boolean) {
    val view = LocalView.current
    LaunchedEffect(effects, enabled) {
        effects.collect { effect ->
            if (enabled && effect == PanelEffect.KeyFeedback) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
}

@Composable
fun PanelScreen(state: PanelUiState, onIntent: (PanelIntent) -> Unit) {
    when {
        state.power != PanelPower.ON -> Blackout { onIntent(PanelIntent.Touch(it)) }
        state.collapsed -> CollapsedHandle { onIntent(PanelIntent.Expand) }
        else -> ExpandedPanel(state, onIntent)
    }
}

@Composable
private fun ExpandedPanel(state: PanelUiState, onIntent: (PanelIntent) -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(ErgoTheme.colors.background),
    ) {
        val density = LocalDensity.current
        val scale = with(density) { remember(maxWidth, maxHeight) { LogicalScale.of(maxWidth.roundToPx(), maxHeight.roundToPx()) } }
        val keyboard = state.keyboard
        KeyboardSurface(keyboard, scale, onTouch = { onIntent(PanelIntent.Touch(it)) })
        StatusLine(state, scale)
        if (keyboard.overlay == Overlay.SETTINGS) {
            val top = with(density) { (HEADER_BOTTOM * scale.sy).toDp() }
            SettingsContent(
                state = state.settings,
                onIntent = { onIntent(PanelIntent.Settings(it)) },
                modifier = Modifier
                    .padding(top = top)
                    .background(ErgoTheme.colors.background)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ErgoButton(stringResource(R.string.back_to_keyboard), { onIntent(PanelIntent.CloseSettings) }, Modifier.weight(1f), tone = KeyTone.ACCENT)
                    ErgoButton(stringResource(R.string.release_inputs), { onIntent(PanelIntent.ReleaseAll) }, Modifier.weight(1f))
                }
            }
        }
        if (keyboard.overlay == Overlay.MENU) ModeMenu(keyboard.view, scale, onIntent)
    }
}

/** The thin band between the header and the keys: a pending accent, Pinyin spelling, or an error. */
@Composable
private fun StatusLine(state: PanelUiState, scale: LogicalScale) {
    val keyboard = state.keyboard
    val text = when {
        state.message != null -> stringResource(state.message.resource())
        keyboard.ime.preedit.isEmpty() || keyboard.overlay != Overlay.NONE -> return
        keyboard.settings.language.usesPinyin -> "${stringResource(R.string.pinyin_hint)}  ${keyboard.ime.preedit}"
        else -> stringResource(R.string.pending_accent, keyboard.ime.preedit)
    }
    val density = LocalDensity.current
    val top = with(density) { ((HEADER_BOTTOM - 34f) * scale.sy).toDp() }
    Text(
        text,
        color = if (state.message != null) ErgoTheme.colors.danger else ErgoTheme.colors.latched,
        fontSize = scale.textSize(24f, density),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = top, start = 16.dp, end = 16.dp),
    )
}

@Composable
private fun ModeMenu(current: PanelView, scale: LogicalScale, onIntent: (PanelIntent) -> Unit) {
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onIntent(PanelIntent.DismissMenu) },
    ) {
        Column(
            Modifier
                .padding(
                    start = with(density) { (MENU_X * scale.sx).toDp() },
                    top = with(density) { (MENU_Y * scale.sy).toDp() },
                )
                .width(with(density) { (MENU_WIDTH * scale.sx).toDp() })
                .background(ErgoTheme.colors.background)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val views = listOf(
                PanelView.COMBINED to R.string.menu_combined,
                PanelView.KEYBOARD to R.string.menu_keyboard,
                PanelView.PAD to R.string.menu_pad,
            )
            for ((view, label) in views) {
                ErgoButton(
                    stringResource(label),
                    { onIntent(PanelIntent.SelectView(view)) },
                    Modifier.fillMaxWidth(),
                    tone = if (view == current) KeyTone.ACCENT else KeyTone.NORMAL,
                )
            }
            ErgoButton(stringResource(R.string.screen_turn_off), { onIntent(PanelIntent.TurnScreenOff) }, Modifier.fillMaxWidth())
            ErgoButton(stringResource(R.string.menu_reconnect), { onIntent(PanelIntent.Reconnect) }, Modifier.fillMaxWidth())
            ErgoButton(stringResource(R.string.menu_release), { onIntent(PanelIntent.ReleaseAll) }, Modifier.fillMaxWidth())
        }
    }
}

/** The panel while it is off: pure black, and any touch wakes it. */
@Composable
private fun Blackout(onTouch: (PanelTouch) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.filter { it.changedToDownIgnoreConsumed() }
                            .forEach { onTouch(PanelTouch.Down(it.id.value, 0f, 0f, it.uptimeMillis)) }
                        event.changes.forEach { it.consume() }
                    }
                }
            },
    )
}

@Composable
private fun CollapsedHandle(onExpand: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ErgoButton(stringResource(R.string.open_keyboard), onExpand, Modifier.width(260.dp), tone = KeyTone.ACCENT)
    }
}

private const val HEADER_BOTTOM = 142f
private const val MENU_X = 610f
private const val MENU_Y = 122f
private const val MENU_WIDTH = LayoutEngine.WIDTH - MENU_X - 24f
