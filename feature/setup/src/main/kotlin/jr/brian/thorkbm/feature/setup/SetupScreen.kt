package jr.brian.thorkbm.feature.setup

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jr.brian.thorkbm.core.designsystem.R
import jr.brian.thorkbm.core.designsystem.component.ErgoButton
import jr.brian.thorkbm.core.designsystem.component.KeyCap
import jr.brian.thorkbm.core.designsystem.component.KeyTone
import jr.brian.thorkbm.core.designsystem.theme.ErgoTheme
import jr.brian.thorkbm.core.injection.ShizukuState
import jr.brian.thorkbm.core.injection.shizuku.ShizukuConnection

@Composable
fun SetupRoute(viewModel: SetupViewModel, onOpenSettings: () -> Unit, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onIntent(SetupIntent.Refresh)
    }
    LifecycleResumeEffect(Unit) {
        viewModel.onIntent(SetupIntent.Resumed)
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                SetupEffect.OpenOverlaySettings ->
                    context.open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()))
                SetupEffect.RequestNotificationPermission -> notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                SetupEffect.OpenAccessibilitySettings -> context.open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                SetupEffect.OpenShizukuDownload -> context.open(Intent(Intent.ACTION_VIEW, SHIZUKU_URL.toUri()))
                SetupEffect.OpenShizukuApp ->
                    context.packageManager.getLaunchIntentForPackage(ShizukuConnection.SHIZUKU_PACKAGE)?.let(context::open)
                SetupEffect.NavigateToSettings -> onOpenSettings()
                SetupEffect.Close -> onClose()
            }
        }
    }
    SetupScreen(state, viewModel::onIntent)
}

@Composable
fun SetupScreen(state: SetupUiState, onIntent: (SetupIntent) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleLarge, color = ErgoTheme.colors.text)
        Text(stringResource(R.string.setup_subtitle), style = MaterialTheme.typography.bodyLarge, color = ErgoTheme.colors.textMuted)

        Step(R.string.step_overlay_title, R.string.step_overlay_body, done = state.overlayGranted) {
            ErgoButton(stringResource(R.string.action_grant), { onIntent(SetupIntent.GrantOverlay) }, Modifier.width(220.dp))
        }
        Step(R.string.step_shizuku_title, R.string.step_shizuku_body, done = state.shizuku == ShizukuState.Ready) {
            val label = when (state.shizuku) {
                ShizukuState.NotInstalled -> R.string.shizuku_not_installed
                ShizukuState.NotRunning -> R.string.shizuku_not_running
                ShizukuState.PermissionRequired -> R.string.action_grant
                ShizukuState.Connecting, ShizukuState.Ready -> R.string.shizuku_connecting
            }
            ErgoButton(stringResource(label), { onIntent(SetupIntent.SetUpShizuku) }, Modifier.width(220.dp), enabled = state.shizuku != ShizukuState.Connecting)
        }
        Step(R.string.step_accessibility_title, R.string.step_accessibility_body, done = state.accessibilityEnabled) {
            ErgoButton(stringResource(R.string.action_grant), { onIntent(SetupIntent.EnableAccessibility) }, Modifier.width(220.dp))
        }
        Step(R.string.step_notifications_title, R.string.step_notifications_body, done = state.notificationsGranted) {
            ErgoButton(stringResource(R.string.action_grant), { onIntent(SetupIntent.GrantNotifications) }, Modifier.width(220.dp))
        }

        when {
            !state.hasSecondDisplay -> Notice(stringResource(R.string.setup_no_second_display), warning = true)
            state.needsBackend -> Notice(stringResource(R.string.setup_need_backend), warning = true)
            state.limited -> Notice(stringResource(R.string.setup_limited), warning = false)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ErgoButton(
                stringResource(if (state.panelRunning) R.string.stop_panel else R.string.start_panel),
                { onIntent(SetupIntent.TogglePanel) },
                Modifier.weight(1f),
                tone = KeyTone.ACCENT,
                enabled = state.panelRunning || state.canStart,
            )
            ErgoButton(stringResource(R.string.settings_title), { onIntent(SetupIntent.OpenSettings) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Step(title: Int, body: Int, done: Boolean, action: @Composable () -> Unit) {
    KeyCap(Modifier.fillMaxWidth(), tone = if (done) KeyTone.LATCHED else KeyTone.NORMAL) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = ErgoTheme.colors.text)
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = ErgoTheme.colors.textMuted)
            }
            if (done) {
                Text(stringResource(R.string.status_done), style = MaterialTheme.typography.labelLarge, color = ErgoTheme.colors.text, modifier = Modifier.padding(start = 16.dp))
            } else {
                action()
            }
        }
    }
}

@Composable
private fun Notice(text: String, warning: Boolean) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = if (warning) ErgoTheme.colors.danger else ErgoTheme.colors.latched)
}

private fun Context.open(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Log.w("ThorKBM", "No activity for $intent", e)
    }
}

private const val SHIZUKU_URL = "https://shizuku.rikka.app/download/"
