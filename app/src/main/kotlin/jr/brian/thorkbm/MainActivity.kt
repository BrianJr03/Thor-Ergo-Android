package jr.brian.thorkbm

import android.os.Bundle
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jr.brian.thorkbm.core.designsystem.LocalizedContent
import jr.brian.thorkbm.core.designsystem.R
import jr.brian.thorkbm.core.designsystem.component.ErgoButton
import jr.brian.thorkbm.core.designsystem.component.KeyTone
import jr.brian.thorkbm.core.designsystem.theme.ErgoTheme
import jr.brian.thorkbm.feature.settings.SettingsContent
import jr.brian.thorkbm.feature.settings.SettingsViewModel
import jr.brian.thorkbm.feature.setup.SetupRoute
import jr.brian.thorkbm.feature.setup.SetupViewModel
import dagger.hilt.android.AndroidEntryPoint

/** Setup and settings. The keyboard itself runs in PanelService, not here. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        hideSystemBars()
        setContent { ThorKbmApp() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The bars come back after dialogs and system settings; hide them again.
        if (hasFocus) hideSystemBars()
    }

    /** Immersive full screen: the status and navigation bars appear only briefly on a swipe. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private enum class Screen { SETUP, SETTINGS }

@Composable
private fun ThorKbmApp() {
    val setup: SetupViewModel = hiltViewModel()
    val setupState by setup.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.SETUP) }
    ErgoTheme {
        LocalizedContent(setupState.uiLanguage) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(ErgoTheme.colors.background)
                    .safeDrawingPadding(),
            ) {
                when (screen) {
                    Screen.SETUP -> SetupRoute(setup, onOpenSettings = { screen = Screen.SETTINGS })
                    Screen.SETTINGS -> {
                        BackHandler { screen = Screen.SETUP }
                        SettingsScreen(onBack = { screen = Screen.SETUP })
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
