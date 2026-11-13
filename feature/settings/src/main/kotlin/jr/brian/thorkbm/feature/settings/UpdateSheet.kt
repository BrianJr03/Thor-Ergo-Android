package jr.brian.thorkbm.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import jr.brian.thorkbm.core.designsystem.R
import jr.brian.thorkbm.core.designsystem.component.ErgoButton
import jr.brian.thorkbm.core.designsystem.component.KeyTone
import jr.brian.thorkbm.core.designsystem.theme.ErgoTheme
import jr.brian.thorkbm.core.update.NoteBlock
import jr.brian.thorkbm.core.update.Release
import jr.brian.thorkbm.core.update.ReleaseNotes
import jr.brian.thorkbm.core.update.UpdateAction
import jr.brian.thorkbm.core.update.UpdateState
import jr.brian.thorkbm.core.update.UpdateStep
import java.util.Locale

/**
 * The update sheet: release notes, then the install permission, download progress and the
 * installer. Covers its host completely and takes every touch, so the keyboard below never types.
 * Draws nothing while [UpdateState.step] is null.
 */
@Composable
fun UpdateSheet(update: UpdateState, onAction: (UpdateAction) -> Unit, modifier: Modifier = Modifier) {
    val step = update.step ?: return
    val release = update.release ?: return
    Box(
        modifier
            .fillMaxSize()
            .background(ErgoTheme.colors.background)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (step) {
                UpdateStep.Notes -> NotesStep(update.installedVersion, release, onAction)
                UpdateStep.NeedsPermission -> MessageStep(
                    title = stringResource(R.string.update_permission_title),
                    body = stringResource(R.string.update_permission_body),
                ) {
                    ErgoButton(stringResource(R.string.update_cancel), { onAction(UpdateAction.DISMISS) }, Modifier.weight(1f))
                    ErgoButton(stringResource(R.string.update_open_settings), { onAction(UpdateAction.ALLOW_INSTALLS) }, Modifier.weight(1f))
                    ErgoButton(stringResource(R.string.update_retry), { onAction(UpdateAction.DOWNLOAD) }, Modifier.weight(1f), tone = KeyTone.ACCENT)
                }
                is UpdateStep.Downloading -> DownloadingStep(release, step)
                UpdateStep.Ready -> MessageStep(
                    title = stringResource(R.string.update_ready_title),
                    body = stringResource(R.string.update_ready_body),
                ) {
                    ErgoButton(stringResource(R.string.update_later), { onAction(UpdateAction.DISMISS) }, Modifier.weight(1f))
                    ErgoButton(stringResource(R.string.update_install), { onAction(UpdateAction.INSTALL) }, Modifier.weight(1f), tone = KeyTone.ACCENT)
                }
                is UpdateStep.Failed -> MessageStep(
                    title = stringResource(R.string.update_failed_title),
                    body = listOfNotNull(stringResource(R.string.update_failed_body), step.message).joinToString("\n\n"),
                ) {
                    ErgoButton(stringResource(R.string.update_cancel), { onAction(UpdateAction.DISMISS) }, Modifier.weight(1f))
                    ErgoButton(stringResource(R.string.update_open_release), { onAction(UpdateAction.OPEN_RELEASE_PAGE) }, Modifier.weight(1f))
                    ErgoButton(stringResource(R.string.update_retry), { onAction(UpdateAction.DOWNLOAD) }, Modifier.weight(1f), tone = KeyTone.ACCENT)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.NotesStep(installed: String, release: Release, onAction: (UpdateAction) -> Unit) {
    Header(stringResource(R.string.update_available_title), stringResource(R.string.update_versions, installed, release.version))
    val blocks = remember(release.notes) { ReleaseNotes.parse(release.notes) }
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (blocks.isEmpty()) Body(stringResource(R.string.update_no_notes)) else blocks.forEach { NoteBlockText(it) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ErgoButton(stringResource(R.string.update_later), { onAction(UpdateAction.DISMISS) }, Modifier.weight(1f))
        val apk = release.apk
        if (apk != null) {
            val size = if (apk.size > 0) " (${formatBytes(apk.size)})" else ""
            ErgoButton(stringResource(R.string.update_download) + size, { onAction(UpdateAction.DOWNLOAD) }, Modifier.weight(1f), tone = KeyTone.ACCENT)
        } else {
            ErgoButton(stringResource(R.string.update_open_release), { onAction(UpdateAction.OPEN_RELEASE_PAGE) }, Modifier.weight(1f), tone = KeyTone.ACCENT)
        }
    }
}

/** No buttons: the download cannot be left half done, and the installer opens by itself at the end. */
@Composable
private fun DownloadingStep(release: Release, step: UpdateStep.Downloading) {
    Header(stringResource(R.string.update_downloading_title), stringResource(R.string.about_version, release.version))
    val colors = ErgoTheme.colors
    if (step.total > 0) {
        LinearProgressIndicator(
            progress = { (step.downloaded.toFloat() / step.total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = colors.accentBorder,
            trackColor = colors.keyBottom,
        )
        Body(stringResource(R.string.update_progress, formatBytes(step.downloaded), formatBytes(step.total)))
    } else {
        LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp), color = colors.accentBorder, trackColor = colors.keyBottom)
        Body(formatBytes(step.downloaded))
    }
}

@Composable
private fun ColumnScope.MessageStep(title: String, body: String, buttons: @Composable RowScope.() -> Unit) {
    Header(title, null)
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) { Body(body) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), content = buttons)
}

@Composable
private fun Header(title: String, subtitle: String?) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, color = ErgoTheme.colors.text)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ErgoTheme.colors.textMuted)
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = ErgoTheme.colors.text)
}

@Composable
private fun NoteBlockText(block: NoteBlock) {
    val colors = ErgoTheme.colors
    val typography = MaterialTheme.typography
    when (block) {
        is NoteBlock.Heading -> Text(
            inline(block.text),
            style = if (block.level <= 2) typography.titleMedium else typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.text,
            modifier = Modifier.padding(top = 8.dp),
        )
        is NoteBlock.Item -> Row(Modifier.padding(start = (block.depth * 20).dp)) {
            Text(block.marker, style = typography.bodyLarge, color = colors.latched, modifier = Modifier.width(24.dp))
            Text(inline(block.text), style = typography.bodyLarge, color = colors.text, modifier = Modifier.weight(1f))
        }
        is NoteBlock.Paragraph -> Text(inline(block.text), style = typography.bodyLarge, color = colors.text)
        is NoteBlock.Quote -> Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(20.dp).background(colors.accentBorder))
            Text(
                inline(block.text),
                style = typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = colors.textMuted,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        is NoteBlock.Code -> Text(
            block.text,
            style = typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = colors.text,
            modifier = Modifier.fillMaxWidth().background(colors.keyBottom).padding(12.dp),
        )
    }
}

/** `**bold**`, `` `code` `` and `[text](url)`, shown as their text; links are not followable from the panel. */
@Composable
private fun inline(text: String): AnnotatedString {
    val colors = ErgoTheme.colors
    return remember(text, colors) {
        buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                val bold = if (text.startsWith("**", i)) text.indexOf("**", i + 2) else -1
                val code = if (text[i] == '`') text.indexOf('`', i + 1) else -1
                val link = if (text[i] == '[') LINK.matchAt(text, i) else null
                when {
                    bold > 0 -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, bold)) }
                        i = bold + 2
                    }
                    code > 0 -> {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.keyBottom)) { append(text.substring(i + 1, code)) }
                        i = code + 1
                    }
                    link != null -> {
                        withStyle(SpanStyle(color = colors.latched)) { append(link.groupValues[1]) }
                        i += link.value.length
                    }
                    else -> append(text[i++])
                }
            }
        }
    }
}

private val LINK = Regex("""\[([^\]]+)]\(([^)\s]+)\)""")

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
}
