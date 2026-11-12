package jr.brian.thorkbm.feature.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jr.brian.thorkbm.core.data.SettingsRepository
import jr.brian.thorkbm.core.display.DisplayRepository
import jr.brian.thorkbm.core.injection.InputInjector
import jr.brian.thorkbm.core.injection.PanelPower
import jr.brian.thorkbm.core.injection.PanelPowerController
import jr.brian.thorkbm.core.injection.PointerModeConfig
import jr.brian.thorkbm.core.injection.accessibility.AccessibilityBridge
import jr.brian.thorkbm.core.keyboard.KeyboardCommand
import jr.brian.thorkbm.core.keyboard.KeyboardEvent
import jr.brian.thorkbm.core.keyboard.KeyboardReducer
import jr.brian.thorkbm.core.keyboard.KeyboardState
import jr.brian.thorkbm.core.keyboard.KeyboardTimer
import jr.brian.thorkbm.core.keyboard.Overlay
import jr.brian.thorkbm.core.keyboard.layout.KeyAction
import jr.brian.thorkbm.core.keyboard.layout.Point
import jr.brian.thorkbm.core.keyboard.output.ComposerState
import jr.brian.thorkbm.core.keyboard.output.OutputRouter
import jr.brian.thorkbm.core.model.Language
import jr.brian.thorkbm.core.model.MouseButton
import jr.brian.thorkbm.core.model.Settings
import jr.brian.thorkbm.core.pinyin.PinyinDictionary
import jr.brian.thorkbm.core.swipe.KeyBox
import jr.brian.thorkbm.core.swipe.SwipeDecoder
import jr.brian.thorkbm.core.swipe.SwipePoint
import jr.brian.thorkbm.core.trackpad.Glide
import jr.brian.thorkbm.core.trackpad.TrackpadConfig
import jr.brian.thorkbm.core.trackpad.TrackpadGestureDetector
import jr.brian.thorkbm.core.trackpad.TrackpadOutput
import jr.brian.thorkbm.feature.panel.di.MonotonicClock
import jr.brian.thorkbm.feature.panel.input.CursorController
import jr.brian.thorkbm.feature.panel.input.MouseHolds
import jr.brian.thorkbm.feature.panel.input.TouchOwner
import jr.brian.thorkbm.feature.panel.input.TouchRouter
import jr.brian.thorkbm.feature.panel.output.OutputPipeline
import jr.brian.thorkbm.feature.panel.output.PanelMessage
import jr.brian.thorkbm.feature.settings.SettingsStateHolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

/**
 * The lower-screen panel. Touches go to the keyboard reducer or the trackpad (decided at touch-down);
 * the reducer's commands are executed here: timers, output, mouse buttons and resets.
 * All state is confined to the main thread; injection happens on the injector's own thread.
 */
class PanelViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val injector: InputInjector,
    private val pointerMode: PointerModeConfig,
    private val power: PanelPowerController,
    private val bridge: AccessibilityBridge,
    private val cursor: CursorController,
    private val clock: MonotonicClock,
    private val dictionary: PinyinDictionary,
    private val swipeDecoder: SwipeDecoder,
    displays: DisplayRepository,
    router: OutputRouter,
    settingsFactory: SettingsStateHolder.Factory,
    @Named(BACKGROUND) private val background: CoroutineDispatcher,
) : ViewModel() {
    private val settingsHolder = settingsFactory.create(viewModelScope)

    private val _state = MutableStateFlow(PanelUiState())
    val state: StateFlow<PanelUiState> = _state.asStateFlow()

    private val effects = Channel<PanelEffect>(Channel.BUFFERED)
    val effect: Flow<PanelEffect> = effects.receiveAsFlow()

    private var keyboard = KeyboardState()
    private val touches = TouchRouter()
    private val holds = MouseHolds()
    private val trackpad = TrackpadGestureDetector()
    private val timers = HashMap<KeyboardTimer, Job>()
    private var glide: Job? = null
    private var idlePointer: Job? = null

    /** Pointer actions run strictly in order: a click must never overtake the move before it. */
    private val pointerQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    private val output = OutputPipeline(
        router = router,
        injector = injector,
        language = { keyboard.settings.language },
        clock = clock::now,
        onComposerChanged = ::onComposerChanged,
        onMessage = { message -> _state.update { it.copy(message = message) } },
    )

    init {
        output.start(viewModelScope)
        viewModelScope.launch { for (action in pointerQueue) action() }
        viewModelScope.launch { repository.settings.collect(::applySettings) }
        viewModelScope.launch { settingsHolder.state.collect { s -> _state.update { it.copy(settings = s) } } }
        viewModelScope.launch { injector.status.collect { s -> _state.update { it.copy(status = s) } } }
        viewModelScope.launch {
            displays.displays.collect { pair -> pair?.upper?.let { cursor.setBounds(it.width, it.height) } }
        }
        viewModelScope.launch {
            power.power.collect { value ->
                if (value != PanelPower.ON) resetInput()
                _state.update { it.copy(power = value) }
            }
        }
        viewModelScope.launch {
            bridge.toggleKeys.collect { if (keyboard.settings.screenToggleEnabled) power.toggle(clock.now()) }
        }
    }

    fun onIntent(intent: PanelIntent) {
        when (intent) {
            is PanelIntent.Touch -> touch(intent.touch)
            is PanelIntent.SelectView -> reduce(KeyboardEvent.SelectView(intent.view))
            PanelIntent.DismissMenu -> reduce(KeyboardEvent.DismissMenu)
            PanelIntent.CloseSettings -> reduce(KeyboardEvent.CloseSettings)
            PanelIntent.ReleaseAll -> reduce(KeyboardEvent.ReleaseAll)
            PanelIntent.Reconnect -> reduce(KeyboardEvent.Reconnect)
            PanelIntent.TurnScreenOff -> {
                reduce(KeyboardEvent.DismissMenu)
                viewModelScope.launch { power.turnOff() }
            }
            PanelIntent.Wake -> viewModelScope.launch { power.turnOn() }
            PanelIntent.Expand -> _state.update { it.copy(collapsed = false) }
            PanelIntent.DismissMessage -> _state.update { it.copy(message = null) }
            is PanelIntent.Settings -> settingsHolder.onIntent(intent.intent)
        }
    }

    private fun touch(touch: PanelTouch) {
        if (_state.value.power != PanelPower.ON) {
            // Any touch on a dark panel only wakes it; that gesture types nothing.
            if (touch is PanelTouch.Down) onIntent(PanelIntent.Wake)
            return
        }
        when (touch) {
            is PanelTouch.Down -> {
                val padActive = keyboard.popup == null && keyboard.overlay == Overlay.NONE
                when (touches.down(touch.pointerId, touch.x, touch.y, if (padActive) keyboard.pad else null)) {
                    TouchOwner.TRACKPAD -> {
                        stopGlide()
                        pointerActivity()
                        handle(trackpad.down(touch.pointerId, touch.x, touch.y, touch.timeMs))
                        if (holds.any) trackpad.suppressTapsForCurrentGroup()
                    }
                    TouchOwner.KEYBOARD -> reduce(KeyboardEvent.Down(touch.pointerId, touch.x, touch.y, touch.timeMs))
                }
            }
            is PanelTouch.Move -> when (touches.ownerOf(touch.pointerId)) {
                TouchOwner.TRACKPAD -> handle(trackpad.move(touch.pointerId, touch.x, touch.y, touch.timeMs))
                TouchOwner.KEYBOARD -> reduce(KeyboardEvent.Move(touch.pointerId, touch.x, touch.y))
                null -> Unit
            }
            is PanelTouch.Up -> when (touches.up(touch.pointerId)) {
                TouchOwner.TRACKPAD -> {
                    if (holds.any) trackpad.suppressTapsForCurrentGroup()
                    handle(trackpad.up(touch.pointerId, touch.timeMs))
                }
                TouchOwner.KEYBOARD -> reduce(KeyboardEvent.Up(touch.pointerId, touch.timeMs))
                null -> Unit
            }
            is PanelTouch.Cancel -> when (touches.up(touch.pointerId)) {
                TouchOwner.TRACKPAD -> handle(trackpad.up(touch.pointerId, Long.MAX_VALUE))
                TouchOwner.KEYBOARD -> reduce(KeyboardEvent.Cancel(touch.pointerId))
                null -> Unit
            }
        }
    }

    private fun reduce(event: KeyboardEvent) {
        val transition = KeyboardReducer.reduce(keyboard, event)
        keyboard = transition.state
        _state.update { it.copy(keyboard = keyboard) }
        transition.commands.forEach(::execute)
    }

    private fun execute(command: KeyboardCommand) {
        when (command) {
            is KeyboardCommand.Emit -> output.submit(command.event)
            // Decoded right away on this thread so the word keeps its place among the keys typed around it.
            is KeyboardCommand.DecodeSwipe -> reduce(KeyboardEvent.SwipeDecoded(command.serial, decodeSwipe(command.points)))
            is KeyboardCommand.StartTimer -> {
                timers.remove(command.timer)?.cancel()
                timers[command.timer] = viewModelScope.launch {
                    delay(command.delayMs)
                    timers.remove(command.timer)
                    reduce(KeyboardEvent.TimerFired(command.timer))
                }
            }
            is KeyboardCommand.CancelTimer -> timers.remove(command.timer)?.cancel()
            is KeyboardCommand.Mouse -> if (command.down) {
                pointerActivity()
                trackpad.suppressTapsForCurrentGroup()
                if (holds.press(command.pointerId, command.button)) pointer { button(command.button, true) }
            } else {
                holds.release(command.pointerId)?.let { button -> pointer { button(button, false) } }
            }
            is KeyboardCommand.PersistView -> viewModelScope.launch { repository.update { it.copy(view = command.view) } }
            KeyboardCommand.ReleasePointer -> releasePointer()
            KeyboardCommand.ResetInput -> resetInput()
            KeyboardCommand.Reconnect -> {
                injector.reconnect()
                output.reset()
                _state.update { it.copy(message = null) }
            }
            KeyboardCommand.Hide -> _state.update { it.copy(collapsed = true) }
            KeyboardCommand.KeyFeedback -> effects.trySend(PanelEffect.KeyFeedback)
        }
    }

    private fun handle(outputs: List<TrackpadOutput>) {
        if (outputs.isNotEmpty()) pointerActivity()
        for (out in outputs) {
            when (out) {
                is TrackpadOutput.Move -> moveCursor(out.dx, out.dy)
                is TrackpadOutput.Scroll -> pointer {
                    val c = cursor.cursor.value
                    injector.scroll(out.vertical, out.horizontal, c.x, c.y)
                }
                is TrackpadOutput.Click -> {
                    reduce(KeyboardEvent.PointerClicked)
                    pointer {
                        button(out.button, true)
                        delay(CLICK_MS)
                        button(out.button, false)
                    }
                }
                is TrackpadOutput.StartGlide -> startGlide(out.glide)
            }
        }
    }

    private fun moveCursor(dx: Int, dy: Int) {
        val c = cursor.moveBy(dx, dy)
        pointer { injector.pointerMove(c.x, c.y) }
    }

    private suspend fun button(button: MouseButton, down: Boolean) {
        val c = cursor.cursor.value
        if (button == MouseButton.LEFT) cursor.setPressed(down)
        injector.button(button, down, c.x, c.y)
    }

    private fun startGlide(motion: Glide) {
        stopGlide()
        glide = viewModelScope.launch {
            while (true) {
                val (dx, dy) = motion.step() ?: break
                if (dx != 0 || dy != 0) {
                    pointerActivity()
                    moveCursor(dx, dy)
                }
                delay(Glide.FRAME_MS)
            }
        }
    }

    private fun stopGlide() {
        glide?.cancel()
        glide = null
    }

    /** Shows the pointer and, if auto-hide is on, fades it out after [POINTER_IDLE_MS] without trackpad use. */
    private fun pointerActivity() {
        idlePointer?.cancel()
        cursor.setIdle(false)
        if (!keyboard.settings.autoHidePointer) return
        idlePointer = viewModelScope.launch {
            delay(POINTER_IDLE_MS)
            cursor.setIdle(true)
        }
    }

    private fun pointer(action: suspend () -> Unit) {
        pointerQueue.trySend(action)
    }

    private fun releasePointer() {
        stopGlide()
        trackpad.reset()
        touches.dropTrackpad()
        for (button in holds.clear()) pointer { button(button, false) }
    }

    private fun resetInput() {
        releasePointer()
        output.reset()
        viewModelScope.launch { injector.releaseAll() }
        _state.update { it.copy(message = null) }
    }

    private fun onComposerChanged(composer: ComposerState) {
        reduce(KeyboardEvent.ImeChanged(composer.preedit, composer.pinyin.candidates, composer.pinyin.revision))
    }

    private fun decodeSwipe(points: List<Point>): List<String> {
        val letters = keyboard.layout.keys.mapNotNull { key ->
            val char = (key.action as? KeyAction.Text)?.char?.singleOrNull()?.takeIf { it in 'a'..'z' } ?: return@mapNotNull null
            char to KeyBox(key.rect.centerX, key.rect.centerY, key.rect.w, key.rect.h)
        }.toMap()
        return swipeDecoder.decode(points.map { SwipePoint(it.x, it.y) }, letters)
    }

    private fun applySettings(settings: Settings) {
        if (settings.language == Language.ZH_CN && keyboard.settings.language != Language.ZH_CN) {
            viewModelScope.launch { withContext(background) { dictionary.warmUp() } }
        }
        if (settings.swipeTyping && !keyboard.settings.swipeTyping) {
            viewModelScope.launch { withContext(background) { swipeDecoder.warmUp() } }
        }
        trackpad.config = TrackpadConfig.from(settings)
        pointerMode.pointerAsTouch = settings.pointerAsTouch
        bridge.boundKeyCode = settings.screenToggleKeyCode.takeIf { settings.screenToggleEnabled }
        val autoHideChanged = settings.autoHidePointer != keyboard.settings.autoHidePointer
        reduce(KeyboardEvent.SettingsChanged(settings))
        _state.update { it.copy(settingsLoaded = true) }
        if (autoHideChanged) pointerActivity()
    }

    override fun onCleared() {
        stopGlide()
        idlePointer?.cancel()
        timers.values.forEach(Job::cancel)
        bridge.boundKeyCode = null
    }

    companion object {
        const val BACKGROUND = "panel-background"
        private const val CLICK_MS = 20L
        const val POINTER_IDLE_MS = 3_000L
    }
}
