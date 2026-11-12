package jr.brian.thorkbm.core.injection.accessibility

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the Accessibility backend can do while the user has the service enabled. */
interface AccessibilityActions {
    /** Commits text through the accessibility input method into the focused editor. */
    fun commitText(text: String): Boolean

    fun sendKey(keyCode: Int, metaState: Int, down: Boolean): Boolean

    /** Dispatches a short tap gesture at (x, y) on [displayId]. */
    fun tap(displayId: Int, x: Float, y: Float, longPress: Boolean = false): Boolean
}

/**
 * Connects the system-bound accessibility service to the rest of the app,
 * and decides which hardware keys the service should swallow.
 */
@Singleton
class AccessibilityBridge @Inject constructor() {
    private val _actions = MutableStateFlow<AccessibilityActions?>(null)
    val actions: StateFlow<AccessibilityActions?> = _actions.asStateFlow()

    private val _toggleKeys = MutableSharedFlow<Unit>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _learnedKeys = MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Presses of the bound lower-screen toggle key. */
    val toggleKeys: SharedFlow<Unit> = _toggleKeys.asSharedFlow()

    /** The key captured after [startLearning]. */
    val learnedKeys: SharedFlow<Int> = _learnedKeys.asSharedFlow()

    @Volatile
    var boundKeyCode: Int? = null

    @Volatile
    private var learning: Boolean = false

    /** Captures the next hardware key press instead of passing it to apps. */
    fun startLearning() {
        learning = true
    }

    fun stopLearning() {
        learning = false
    }

    internal fun attach(actions: AccessibilityActions) {
        _actions.value = actions
    }

    internal fun detach(actions: AccessibilityActions) {
        _actions.compareAndSet(actions, null)
    }

    /** Called for every hardware key-down; returns true to consume it. */
    fun onHardwareKey(keyCode: Int): Boolean = when {
        learning -> {
            learning = false
            _learnedKeys.tryEmit(keyCode)
            true
        }
        keyCode == boundKeyCode -> {
            _toggleKeys.tryEmit(Unit)
            true
        }
        else -> false
    }

    /** Whether non-down events of this key should be swallowed too. */
    internal fun owns(keyCode: Int): Boolean = keyCode == boundKeyCode
}
