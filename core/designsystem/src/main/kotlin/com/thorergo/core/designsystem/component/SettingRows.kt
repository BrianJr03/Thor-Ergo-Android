package com.thorergo.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thorergo.core.designsystem.theme.ErgoTheme

/** A full-width pill button in the key style. */
@Composable
fun ErgoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: KeyTone = KeyTone.NORMAL,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    KeyCap(
        modifier = modifier
            .height(56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        tone = tone,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = ErgoTheme.colors.text)
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) ErgoTheme.colors.text else ErgoTheme.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
private fun SettingRow(label: String, modifier: Modifier = Modifier, control: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = ErgoTheme.colors.text, modifier = Modifier.weight(1f))
        control()
    }
}

/** − value + for a ranged setting. */
@Composable
fun StepperRow(
    label: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingRow(label, modifier) {
        ErgoButton("−", onDecrease, Modifier.width(72.dp), enabled = canDecrease)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = ErgoTheme.colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(120.dp),
        )
        ErgoButton("+", onIncrease, Modifier.width(72.dp), enabled = canIncrease)
    }
}

/** A setting whose value is a single button: a toggle or a picker. */
@Composable
fun ValueRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: KeyTone = KeyTone.NORMAL,
) {
    SettingRow(label, modifier) {
        ErgoButton(value, onClick, Modifier.width(264.dp), tone = tone, enabled = enabled)
    }
}

/** Equal-width tabs in the key style. */
@Composable
fun <T> ErgoTabs(tabs: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (tab in tabs) {
            KeyCap(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(role = Role.Tab) { onSelect(tab) },
                tone = if (tab == selected) KeyTone.ACCENT else KeyTone.NORMAL,
            ) {
                // Long translations ("Unterer Bildschirm") wrap instead of being cut off.
                Text(
                    label(tab),
                    style = MaterialTheme.typography.labelLarge.copy(lineHeight = 18.sp),
                    color = ErgoTheme.colors.text,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}
