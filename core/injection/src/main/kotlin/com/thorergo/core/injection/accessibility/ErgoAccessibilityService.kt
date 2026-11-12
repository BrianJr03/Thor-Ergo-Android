package com.thorergo.core.injection.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fallback injection without Shizuku (text into focused editors, keys, taps), plus the
 * hardware-key filter for the lower-screen toggle. It reads no window content.
 */
@AndroidEntryPoint
class ErgoAccessibilityService : AccessibilityService(), AccessibilityActions {
    @Inject lateinit var bridge: AccessibilityBridge

    override fun onServiceConnected() {
        super.onServiceConnected()
        bridge.attach(this)
    }

    override fun onDestroy() {
        bridge.detach(this)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return bridge.owns(event.keyCode)
        return bridge.onHardwareKey(event.keyCode)
    }

    override fun commitText(text: String): Boolean {
        val connection = inputMethod?.currentInputConnection ?: return false
        connection.commitText(text, 1, null)
        return true
    }

    override fun sendKey(keyCode: Int, metaState: Int, down: Boolean): Boolean {
        val connection = inputMethod?.currentInputConnection ?: return false
        val now = SystemClock.uptimeMillis()
        connection.sendKeyEvent(KeyEvent(now, now, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, keyCode, 0, metaState))
        return true
    }

    override fun tap(displayId: Int, x: Float, y: Float, longPress: Boolean): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, if (longPress) LONG_PRESS_MS else TAP_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).setDisplayId(displayId).build()
        return dispatchGesture(gesture, null, null)
    }

    private companion object {
        const val TAP_MS = 40L
        const val LONG_PRESS_MS = 600L
    }
}
