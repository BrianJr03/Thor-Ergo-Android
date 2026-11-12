package jr.brian.thorkbm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import jr.brian.thorkbm.core.data.SettingsRepository
import jr.brian.thorkbm.core.designsystem.LocalizedContent
import jr.brian.thorkbm.core.designsystem.R
import jr.brian.thorkbm.core.designsystem.component.ErgoButton
import jr.brian.thorkbm.core.designsystem.component.KeyTone
import jr.brian.thorkbm.core.designsystem.theme.ErgoTheme
import jr.brian.thorkbm.feature.panel.service.PanelControl
import jr.brian.thorkbm.feature.settings.SettingsContent
import jr.brian.thorkbm.feature.settings.SettingsViewModel
import jr.brian.thorkbm.feature.setup.SetupRoute
import jr.brian.thorkbm.feature.setup.SetupViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The launcher entry point. Once setup is done it only opens the keyboard and closes, so nothing
 * is left on the upper screen; setup and settings appear on first run or when asked for.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var panel: PanelControl

    private var screen by mutableStateOf(Screen.SETUP)
    private var showingUi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        if (intent.wantsSetup() || savedInstanceState != null) return showUi()
        var deciding = true
        splash.setKeepOnScreenCondition { deciding }
        lifecycleScope.launch {
            if (settings.settings.first().setupCompleted && Settings.canDrawOverlays(this@MainActivity)) {
                openKeyboard()
            } else {
                showUi()
            }
            deciding = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.wantsSetup()) {
            screen = Screen.SETUP
            showUi()
        } else if (!showingUi) {
            openKeyboard()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The bars come back after dialogs and system settings; hide them again.
        if (hasFocus) hideSystemBars()
    }

    private fun openKeyboard() {
        if (panel.running.value) panel.show() else panel.start()
        finish()
    }

    private fun showUi() {
        if (showingUi) return
        showingUi = true
        enableEdgeToEdge()
        hideSystemBars()
        setContent { ThorKbmApp(screen, onScreen = { screen = it }, onClose = ::finish) }
    }

    /** Immersive full screen: the status and navigation bars appear only briefly on a swipe. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    companion object {
        private const val EXTRA_SHOW_SETUP = "jr.brian.thorkbm.extra.SHOW_SETUP"

        private fun Intent.wantsSetup() = getBooleanExtra(EXTRA_SHOW_SETUP, false)

        /** Opens the setup checklist even when setup is already complete. */
        fun setupIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_SHOW_SETUP, true)
    }
}

internal enum class Screen { SETUP, SETTINGS }

@Composable
private fun ThorKbmApp(screen: Screen, onScreen: (Screen) -> Unit, onClose: () -> Unit) {
    val setup: SetupViewModel = hiltViewModel()
    val setupState by setup.state.collectAsStateWithLifecycle()
    ErgoTheme {
        LocalizedContent(setupState.uiLanguage) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(ErgoTheme.colors.background)
                    .safeDrawingPadding(),
            ) {
                when (screen) {
                    Screen.SETUP -> SetupRoute(setup, onOpenSettings = { onScreen(Screen.SETTINGS) }, onClose = onClose)
                    Screen.SETTINGS -> {
                        BackHandler { onScreen(Screen.SETUP) }
                        SettingsScreen(onBack = { onScreen(Screen.SETUP) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(state, viewModel::onIntent, Modifier.padding(24.dp)) {
        ErgoButton(stringResource(R.string.setup_title), onBack, Modifier.fillMaxWidth(), tone = KeyTone.ACCENT)
    }
}
