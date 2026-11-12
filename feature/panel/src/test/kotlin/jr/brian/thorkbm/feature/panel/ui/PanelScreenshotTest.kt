package jr.brian.thorkbm.feature.panel.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import jr.brian.thorkbm.core.designsystem.LocalizedContent
import jr.brian.thorkbm.core.designsystem.theme.ErgoTheme
import jr.brian.thorkbm.core.keyboard.KeyboardEvent
import jr.brian.thorkbm.core.keyboard.KeyboardReducer
import jr.brian.thorkbm.core.keyboard.KeyboardState
import jr.brian.thorkbm.core.keyboard.KeyboardTimer
import jr.brian.thorkbm.core.keyboard.Overlay
import jr.brian.thorkbm.core.model.Language
import jr.brian.thorkbm.core.model.Modifier
import jr.brian.thorkbm.core.model.ModifierState
import jr.brian.thorkbm.core.model.Page
import jr.brian.thorkbm.core.model.Settings
import jr.brian.thorkbm.feature.panel.PanelUiState
import jr.brian.thorkbm.feature.settings.SettingsTab
import jr.brian.thorkbm.feature.settings.SettingsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the panel at the Thor's lower-screen size (1240 × 1080 px at 2×); images live in src/test/screenshots. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w620dp-h540dp-xhdpi", sdk = [35])
class PanelScreenshotTest {
    @get:Rule val compose = createComposeRule()

    /** Compose content can be set once per test, so each capture swaps the state it shows. */
    private var shown by mutableStateOf<PanelUiState?>(null)

    private fun capture(name: String, state: PanelUiState) {
        if (shown == null) {
            compose.setContent {
                shown?.let { current ->
                    ErgoTheme {
                        LocalizedContent(current.keyboard.settings.effectiveUiLanguage) { PanelScreen(current) {} }
                    }
                }
            }
        }
        shown = state
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun keyboard(language: Language, page: Page = Page.ABC) = KeyboardState(settings = Settings(language = language), page = page)

    @Test
    fun letters() {
        for (language in Language.entries) capture("keyboard-${language.tag}", PanelUiState(keyboard = keyboard(language)))
    }

    @Test
    fun alternativePages() {
        capture("numbers", PanelUiState(keyboard = keyboard(Language.EN_US, Page.NUMBERS)))
        capture("symbols-pt", PanelUiState(keyboard = keyboard(Language.PT_PT, Page.SYMBOLS)))
        capture("functions", PanelUiState(keyboard = keyboard(Language.EN_US, Page.FN)))
    }

    @Test
    fun shiftedAndAccentPopup() {
        val shifted = keyboard(Language.PT_PT).copy(modifiers = KeyboardState.NO_MODIFIERS + (Modifier.SHIFT to ModifierState.ONE_SHOT))
        capture("shifted-pt", PanelUiState(keyboard = shifted))
        val key = keyboard(Language.PT_PT).layout.find("char:a")!!
        var state = KeyboardReducer.reduce(keyboard(Language.PT_PT), KeyboardEvent.Down(1, key.rect.centerX, key.rect.centerY, 0)).state
        val serial = state.presses.getValue(1).serial
        state = KeyboardReducer.reduce(state, KeyboardEvent.TimerFired(KeyboardTimer.LongPress(serial))).state
        capture("accent-popup", PanelUiState(keyboard = state))
    }

    @Test
    fun menuAndSettings() {
        capture("mode-menu", PanelUiState(keyboard = keyboard(Language.EN_US).copy(overlay = Overlay.MENU)))
        val settings = SettingsUiState(settings = Settings(), tab = SettingsTab.MOUSE)
        capture("settings-mouse", PanelUiState(keyboard = keyboard(Language.EN_US).copy(overlay = Overlay.SETTINGS), settings = settings))
        val screen = SettingsUiState(settings = Settings(language = Language.DE_DE), tab = SettingsTab.SCREEN, canBindKey = true)
        capture("settings-screen-de", PanelUiState(keyboard = keyboard(Language.DE_DE).copy(overlay = Overlay.SETTINGS), settings = screen))
    }

    @Test
    fun pinyinCandidates() {
        val zh = keyboard(Language.ZH_CN).let {
            KeyboardReducer.reduce(it, KeyboardEvent.ImeChanged("nihao", listOf("你好", "你", "尼", "泥", "拟", "逆", "腻"), 3)).state
        }
        capture("pinyin", PanelUiState(keyboard = zh))
    }
}
