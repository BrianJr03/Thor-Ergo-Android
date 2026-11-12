package com.thorergo.core.injection

import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import com.thorergo.core.display.DisplayRepository
import com.thorergo.core.injection.accessibility.AccessibilityBridge
import com.thorergo.core.injection.di.ApplicationScope
import com.thorergo.core.injection.di.InjectionDispatcher
import com.thorergo.core.injection.shizuku.ShizukuConnection
import com.thorergo.core.model.KeyCode
import com.thorergo.core.model.Modifier
import com.thorergo.core.model.MouseButton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chooses a backend per capability: Shizuku reaches any app (keys, mouse, touches); the
 * Accessibility service commits Unicode text and is the fallback for keys and taps.
 */
@Singleton
internal class CompositeInjector @Inject constructor(
    private val shizuku: ShizukuConnection,
    private val accessibility: AccessibilityBridge,
    displays: DisplayRepository,
    @ApplicationScope scope: CoroutineScope,
    @InjectionDispatcher private val dispatcher: CoroutineDispatcher,
) : InputInjector, PointerModeConfig {
    override val status: StateFlow<InjectorStatus> =
        combine(shizuku.state, accessibility.actions.map { it != null }) { s, a -> InjectorStatus(s, a) }
            .stateIn(scope, SharingStarted.Eagerly, InjectorStatus())

    private val upperDisplay: StateFlow<Int> = displays.displays.map { it?.upper?.id ?: Display.DEFAULT_DISPLAY }
        .stateIn(scope, SharingStarted.Eagerly, Display.DEFAULT_DISPLAY)

    @Volatile
    override var pointerAsTouch: Boolean = false

    private var buttonState = 0
    private var lastX = 0f
    private var lastY = 0f

    init {
        shizuku.start()
    }

    override suspend fun chord(code: KeyCode, mods: Set<Modifier>): Boolean = withContext(dispatcher) {
        val strokes = AndroidKeys.chordEvents(code, mods)
        val split = strokes.indexOfFirst { it.action == KeyEvent.ACTION_UP }
        val send: (KeyStroke) -> Boolean = shizuku.service?.let { service -> { s: KeyStroke -> service.injectKey(s.action, s.keyCode, s.metaState) } }
            ?: accessibility.actions.value?.let { actions -> { s: KeyStroke -> actions.sendKey(s.keyCode, s.metaState, s.action == KeyEvent.ACTION_DOWN) } }
            ?: return@withContext false
        val pressed = strokes.take(split).all(send)
        delay(KEY_HOLD_MS)
        val released = strokes.drop(split).map(send).all { it }
        pressed && released
    }

    override suspend fun commitText(text: String): Boolean = withContext(dispatcher) {
        accessibility.actions.value?.commitText(text) ?: false
    }

    override suspend fun pointerMove(x: Float, y: Float): Boolean = withContext(dispatcher) {
        lastX = x
        lastY = y
        val service = shizuku.service ?: return@withContext false
        when {
            pointerAsTouch && buttonState != 0 -> service.injectTouch(upperDisplay.value, MotionEvent.ACTION_MOVE, x, y)
            pointerAsTouch -> true
            buttonState != 0 -> service.injectMouse(upperDisplay.value, MotionEvent.ACTION_MOVE, x, y, buttonState, 0, 0f, 0f)
            else -> service.injectMouse(upperDisplay.value, MotionEvent.ACTION_HOVER_MOVE, x, y, 0, 0, 0f, 0f)
        }
    }

    override suspend fun button(button: MouseButton, down: Boolean, x: Float, y: Float): Boolean = withContext(dispatcher) {
        val flag = buttonFlag(button)
        val before = buttonState
        buttonState = if (down) before or flag else before and flag.inv()
        if (before == buttonState) return@withContext true
        val service = shizuku.service
        val display = upperDisplay.value
        when {
            service == null -> {
                // Accessibility can only tap: left is a tap, right a long press, on release.
                !down && accessibility.actions.value?.tap(display, x, y, longPress = button == MouseButton.RIGHT) == true
            }
            pointerAsTouch -> when {
                button != MouseButton.LEFT -> true
                down -> service.injectTouch(display, MotionEvent.ACTION_DOWN, x, y)
                else -> service.injectTouch(display, MotionEvent.ACTION_UP, x, y)
            }
            down -> {
                val first = before == 0
                (!first || service.injectMouse(display, MotionEvent.ACTION_DOWN, x, y, buttonState, 0, 0f, 0f)) &&
                    service.injectMouse(display, MotionEvent.ACTION_BUTTON_PRESS, x, y, buttonState, flag, 0f, 0f)
            }
            else -> {
                service.injectMouse(display, MotionEvent.ACTION_BUTTON_RELEASE, x, y, buttonState, flag, 0f, 0f) &&
                    (buttonState != 0 || service.injectMouse(display, MotionEvent.ACTION_UP, x, y, 0, 0, 0f, 0f))
            }
        }
    }

    override suspend fun scroll(vertical: Int, horizontal: Int, x: Float, y: Float): Boolean = withContext(dispatcher) {
        val service = shizuku.service ?: return@withContext false
        service.injectMouse(upperDisplay.value, MotionEvent.ACTION_SCROLL, x, y, buttonState, 0, vertical / NOTCH, horizontal / NOTCH)
    }

    override suspend fun releaseAll() {
        for (button in MouseButton.entries) {
            if (buttonState and buttonFlag(button) != 0) button(button, down = false, lastX, lastY)
        }
    }

    override fun reconnect() {
        shizuku.rebind()
    }

    private fun buttonFlag(button: MouseButton) = when (button) {
        MouseButton.LEFT -> MotionEvent.BUTTON_PRIMARY
        MouseButton.RIGHT -> MotionEvent.BUTTON_SECONDARY
        MouseButton.MIDDLE -> MotionEvent.BUTTON_TERTIARY
    }

    private companion object {
        const val KEY_HOLD_MS = 20L
        const val NOTCH = 120f
    }
}

/** Whether clicks are sent as screen touches rather than mouse buttons. */
interface PointerModeConfig {
    var pointerAsTouch: Boolean
}
