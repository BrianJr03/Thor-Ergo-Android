package jr.brian.thorkbm.feature.panel.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import jr.brian.thorkbm.core.designsystem.R
import jr.brian.thorkbm.core.designsystem.component.KeyTone
import jr.brian.thorkbm.core.designsystem.icon.ErgoIcons
import jr.brian.thorkbm.core.keyboard.KeyboardState
import jr.brian.thorkbm.core.keyboard.Overlay
import jr.brian.thorkbm.core.keyboard.layout.KeyAction
import jr.brian.thorkbm.core.keyboard.layout.KeyIcon
import jr.brian.thorkbm.core.keyboard.layout.KeyLabel
import jr.brian.thorkbm.core.keyboard.layout.KeySpec
import jr.brian.thorkbm.core.keyboard.layout.KeyStyle
import jr.brian.thorkbm.core.keyboard.layout.KeyText
import jr.brian.thorkbm.core.keyboard.layout.LabelId
import jr.brian.thorkbm.core.model.Modifier
import jr.brian.thorkbm.core.model.ModifierState
import jr.brian.thorkbm.core.model.Page
import jr.brian.thorkbm.feature.panel.output.PanelMessage

/** The legend drawn on [key] for the current state; text keys use the same function that types them. */
@Composable
internal fun legend(key: KeySpec, state: KeyboardState): String {
    val action = key.action
    if (action is KeyAction.Candidate) return state.stripCandidateAt(action.slot).orEmpty()
    return when (val label = key.label) {
        is KeyLabel.Localized -> stringResource(label.id.resource())
        is KeyLabel.Literal ->
            if (action is KeyAction.Text) KeyText.of(action, state.settings.language, state.shift) else label.text
        KeyLabel.None -> ""
    }
}

internal fun icon(key: KeySpec, state: KeyboardState): ImageVector? = when (key.icon) {
    KeyIcon.SHIFT -> if (state.shift == ModifierState.LOCKED) ErgoIcons.ShiftLocked else ErgoIcons.Shift
    KeyIcon.BACKSPACE -> ErgoIcons.Backspace
    KeyIcon.ENTER -> ErgoIcons.Enter
    KeyIcon.LEFT -> ErgoIcons.Left
    KeyIcon.UP -> ErgoIcons.Up
    KeyIcon.DOWN -> ErgoIcons.Down
    KeyIcon.RIGHT -> ErgoIcons.Right
    KeyIcon.DROPDOWN -> ErgoIcons.Dropdown
    KeyIcon.GEAR -> ErgoIcons.Gear
    KeyIcon.MOUSE_LEFT -> ErgoIcons.MouseLeft
    KeyIcon.MOUSE_RIGHT -> ErgoIcons.MouseRight
    null -> null
}

/** Accent for Enter; latched modifiers, the open Fn page and open settings light up. */
internal fun tone(key: KeySpec, state: KeyboardState): KeyTone {
    if (key.style == KeyStyle.ACCENT) return KeyTone.ACCENT
    val lit = when (val action = key.action) {
        is KeyAction.ModifierKey -> state.modifier(action.modifier) != ModifierState.OFF
        KeyAction.CapsLock -> state.modifier(Modifier.SHIFT) == ModifierState.LOCKED
        KeyAction.Fn -> state.page == Page.FN && state.overlay == Overlay.NONE
        KeyAction.Settings -> state.overlay == Overlay.SETTINGS
        KeyAction.Mode -> state.overlay == Overlay.MENU
        else -> false
    }
    return if (lit) KeyTone.LATCHED else KeyTone.NORMAL
}

/** Legend size in logical units, relative to the key's role. */
internal fun legendSize(key: KeySpec): Float = when {
    key.action is KeyAction.Text && key.label is KeyLabel.Literal -> 64f
    key.action is KeyAction.Candidate -> 34f
    key.style == KeyStyle.SMALL -> 30f
    key.action == KeyAction.Fn -> 44f
    key.label is KeyLabel.Localized && key.action !is KeyAction.Text -> 30f
    else -> 36f
}

private fun LabelId.resource(): Int = when (this) {
    LabelId.LEFT_CLICK -> R.string.header_left_click
    LabelId.RIGHT_CLICK -> R.string.header_right_click
    LabelId.MODE -> R.string.header_mode
    LabelId.HIDE -> R.string.header_hide
    LabelId.SPACE -> R.string.key_space
    LabelId.ENTER -> R.string.key_enter
    LabelId.SYMBOLS -> R.string.key_symbols
    LabelId.CAPS_LOCK -> R.string.key_caps_lock
    LabelId.NUM_LOCK -> R.string.key_num_lock
    LabelId.SCROLL_LOCK -> R.string.key_scroll_lock
    LabelId.INSERT -> R.string.key_insert
    LabelId.DELETE -> R.string.key_delete
    LabelId.HOME -> R.string.key_home
    LabelId.END -> R.string.key_end
    LabelId.PAGE_UP -> R.string.key_page_up
    LabelId.PAGE_DOWN -> R.string.key_page_down
}

internal fun PanelMessage.resource(): Int = when (this) {
    PanelMessage.QUEUE_FULL -> R.string.error_queue_full
    PanelMessage.EXPIRED -> R.string.error_expired
    PanelMessage.INJECTION_FAILED -> R.string.error_injection_failed
    PanelMessage.TEXT_UNAVAILABLE -> R.string.error_text_unavailable
    PanelMessage.NOT_ON_US_KEYBOARD -> R.string.error_not_on_us
    PanelMessage.NO_ALTGR_SYMBOL -> R.string.error_no_altgr
    PanelMessage.ALTGR_USE_SYMBOLS_PAGE -> R.string.error_altgr_symbols
    PanelMessage.NO_PHYSICAL_SHORTCUT -> R.string.error_no_shortcut
    PanelMessage.INVALID_CHARACTER -> R.string.error_invalid_character
}
