package com.thorergo.feature.panel.service

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
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            (if (touchable) WindowManager.LayoutParams.FLAG_SPLIT_TOUCH else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        // Android blocks touches through untrusted overlays above this opacity.
        if (!touchable) alpha = PASS_THROUGH_ALPHA
        title = if (touchable) "ThorErgoPanel" else "ThorErgoCursor"
    }

    fun show() {
        windowManager.addView(view, params)
    }

    /** Shrinks the panel to a handle at the bottom, or restores it to the full display. */
    fun setCollapsed(collapsed: Boolean, collapsedHeightPx: Int) {
        val height = if (collapsed) collapsedHeightPx else WindowManager.LayoutParams.MATCH_PARENT
        if (params.height == height) return
        params.height = height
        if (view.isAttachedToWindow) windowManager.updateViewLayout(view, params)
    }

    fun remove() {
        if (view.isAttachedToWindow) windowManager.removeViewImmediate(view)
    }

    private companion object {
        const val PASS_THROUGH_ALPHA = 0.8f
    }
}
