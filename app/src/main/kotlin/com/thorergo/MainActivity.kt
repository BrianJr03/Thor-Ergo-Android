package com.thorergo

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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thorergo.core.designsystem.LocalizedContent
import com.thorergo.core.designsystem.R
import com.thorergo.core.designsystem.component.ErgoButton
import com.thorergo.core.designsystem.component.KeyTone
import com.thorergo.core.designsystem.theme.ErgoTheme
import com.thorergo.feature.settings.SettingsContent
import com.thorergo.feature.settings.SettingsViewModel
import com.thorergo.feature.setup.SetupRoute
import com.thorergo.feature.setup.SetupViewModel
import dagger.hilt.android.AndroidEntryPoint

/** Setup and settings. The keyboard itself runs in PanelService, not here. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { ThorErgoApp() }
    }
}

private enum class Screen { SETUP, SETTINGS }

@Composable
private fun ThorErgoApp() {
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
