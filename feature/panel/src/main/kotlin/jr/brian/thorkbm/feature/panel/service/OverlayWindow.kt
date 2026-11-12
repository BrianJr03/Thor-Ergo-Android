package jr.brian.thorkbm.feature.panel.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

internal enum class PanelWindowMode { FULL, HANDLE, GONE }

/** The owners a ComposeView needs when it lives in a window instead of an Activity. */
internal class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    fun create() {
        savedState.performRestore(Bundle())
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

/**
 * A Compose window on a specific display. [touchable] = false lets every touch pass through
 * (the cursor); the panel is touchable but never focusable, so the game keeps input focus.
 */
internal class OverlayWindow(
    context: Context,
    display: Display,
    private val owner: OverlayOwner,
    private val touchable: Boolean,
    content: @Composable () -> Unit,
) {
    private val windowContext = context.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    private val windowManager = windowContext.getSystemService(WindowManager::class.java)

    private val view = ComposeView(windowContext).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent(content)
    }

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            (if (touchable) WindowManager.LayoutParams.FLAG_SPLIT_TOUCH else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        // Fill the whole display: overlays otherwise stop short of the system bars.
        fitInsetsTypes = 0
        isFitInsetsIgnoringVisibility = true
        // Android blocks touches through untrusted overlays above this opacity.
        if (!touchable) alpha = PASS_THROUGH_ALPHA
        title = if (touchable) "ThorKbmPanel" else "ThorKbmCursor"
    }

    /**
     * Whether the view is in the window manager. Tracked here because
     * [android.view.View.isAttachedToWindow] only turns true on the next frame.
     */
    private var added = false

    fun show() = add()

    /** Full display, a handle-sized strip at the bottom, or no window at all. */
    fun setMode(mode: PanelWindowMode, handleHeightPx: Int) {
        if (mode == PanelWindowMode.GONE) return remove()
        val height = if (mode == PanelWindowMode.HANDLE) handleHeightPx else WindowManager.LayoutParams.MATCH_PARENT
        val resized = params.height != height
        params.height = height
        if (!added) add() else if (resized) windowManager.updateViewLayout(view, params)
    }

    fun remove() {
        if (!added) return
        added = false
        windowManager.removeViewImmediate(view)
    }

    private fun add() {
        if (added) return
        windowManager.addView(view, params)
        added = true
    }

    private companion object {
        const val PASS_THROUGH_ALPHA = 0.8f
    }
}
