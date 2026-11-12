package jr.brian.thorkbm.core.injection.shizuku

import android.annotation.SuppressLint
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method
import kotlin.system.exitProcess

/**
 * Shizuku user service. It runs in a separate process as the shell user, which holds
 * INJECT_EVENTS and may call the hidden display-power APIs (the same ones scrcpy uses).
 * Everything here is reflection; failures return false instead of crashing the process.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi", "BlockedPrivateApi")
class PrivilegedInputService : IPrivilegedInput.Stub() {
    init {
        HiddenApiBypass.addHiddenApiExemptions("")
    }

    private val inputManager: Any by lazy {
        Class.forName("android.hardware.input.InputManager").getDeclaredMethod("getInstance").invoke(null)!!
    }
    private val injectMethod: Method by lazy {
        inputManager.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
    }
    private val setDisplayId: Method by lazy {
        MotionEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
    }
    private val setActionButton: Method by lazy {
        MotionEvent::class.java.getMethod("setActionButton", Int::class.javaPrimitiveType)
    }
    private var mouseDownTime = 0L
    private var touchDownTime = 0L

    override fun destroy() {
        exitProcess(0)
    }

    override fun injectKey(action: Int, keyCode: Int, metaState: Int): Boolean = attempt("key") {
        val now = SystemClock.uptimeMillis()
        val event = KeyEvent(now, now, action, keyCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD)
        inject(event)
    }

    override fun injectMouse(
        displayId: Int,
        action: Int,
        x: Float,
        y: Float,
        buttonState: Int,
        actionButton: Int,
        vScroll: Float,
        hScroll: Float,
    ): Boolean = attempt("mouse") {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) mouseDownTime = now
        val downTime = if (action == MotionEvent.ACTION_HOVER_MOVE || action == MotionEvent.ACTION_SCROLL) now else mouseDownTime
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = if (buttonState != 0) 1f else 0f
            size = 1f
            setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll)
            setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll)
        }
        val event = MotionEvent.obtain(
            downTime, now, action, 1, arrayOf(properties), arrayOf(coords),
            0, buttonState, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0,
        )
        try {
            setDisplayId.invoke(event, displayId)
            if (actionButton != 0) setActionButton.invoke(event, actionButton)
            inject(event)
        } finally {
            event.recycle()
        }
    }

    override fun injectTouch(displayId: Int, action: Int, x: Float, y: Float): Boolean = attempt("touch") {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) touchDownTime = now
        val event = MotionEvent.obtain(touchDownTime, now, action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        try {
            setDisplayId.invoke(event, displayId)
            inject(event)
        } finally {
            event.recycle()
        }
    }

    override fun setDisplayPower(displayId: Int, on: Boolean): Boolean = attempt("power") {
        val token = DisplayTokens.forDisplay(displayId) ?: return@attempt false
        DisplayTokens.setPowerMode(token, if (on) POWER_MODE_NORMAL else POWER_MODE_OFF)
    }

    private fun inject(event: InputEvent): Boolean = injectMethod.invoke(inputManager, event, INJECT_ASYNC) as Boolean

    private inline fun attempt(what: String, block: () -> Boolean): Boolean = try {
        block()
    } catch (e: Throwable) {
        Log.w(TAG, "Injecting $what failed", e)
        false
    }

    /** Physical display tokens: SurfaceControl through Android 13, DisplayControl (services.jar) after. */
    private object DisplayTokens {
        private val displayControl: Class<*>? by lazy {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return@lazy null
            runCatching {
                val factory = Class.forName("com.android.internal.os.ClassLoaderFactory")
                val create = factory.getDeclaredMethod(
                    "createClassLoader", String::class.java, String::class.java, String::class.java,
                    ClassLoader::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, String::class.java,
                )
                val loader = create.invoke(null, "/system/framework/services.jar", null, null, ClassLoader.getSystemClassLoader(), 0, true, null) as ClassLoader
                val cls = loader.loadClass("com.android.server.display.DisplayControl")
                val loadLibrary = Runtime::class.java.getDeclaredMethod("loadLibrary0", Class::class.java, String::class.java)
                loadLibrary.isAccessible = true
                loadLibrary.invoke(Runtime.getRuntime(), cls, "android_servers")
                cls
            }.getOrNull()
        }

        private val surfaceControl = Class.forName("android.view.SurfaceControl")

        private val tokenSource: Class<*> get() = displayControl ?: surfaceControl

        fun forDisplay(displayId: Int): IBinder? {
            val physicalId = physicalId(displayId) ?: return null
            return tokenSource.getMethod("getPhysicalDisplayToken", Long::class.javaPrimitiveType).invoke(null, physicalId) as IBinder?
        }

        fun setPowerMode(token: IBinder, mode: Int): Boolean {
            surfaceControl.getMethod("setDisplayPowerMode", IBinder::class.java, Int::class.javaPrimitiveType).invoke(null, token, mode)
            return true
        }

        /** Maps a logical display id to its physical id through DisplayInfo.address. */
        private fun physicalId(displayId: Int): Long? {
            val global = Class.forName("android.hardware.display.DisplayManagerGlobal").getMethod("getInstance").invoke(null)
            val info = global.javaClass.getMethod("getDisplayInfo", Int::class.javaPrimitiveType).invoke(global, displayId) ?: return null
            val address = info.javaClass.getField("address").get(info) ?: return null
            return address.javaClass.getMethod("getPhysicalDisplayId").invoke(address) as Long
        }
    }

    private companion object {
        const val TAG = "ThorKbmPrivileged"
        const val INJECT_ASYNC = 0
        const val POWER_MODE_OFF = 0
        const val POWER_MODE_NORMAL = 2
    }
}
