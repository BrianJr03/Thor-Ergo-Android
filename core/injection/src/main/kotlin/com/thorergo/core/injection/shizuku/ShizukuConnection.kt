package com.thorergo.core.injection.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.thorergo.core.injection.BuildConfig
import com.thorergo.core.injection.ShizukuState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

/** What the setup screen needs from Shizuku. */
interface ShizukuAccess {
    val state: StateFlow<ShizukuState>

    /** Asks Shizuku for permission; the result arrives through [state]. */
    fun requestPermission()

    fun refresh()
}

/** Tracks Shizuku and keeps [IPrivilegedInput] bound whenever it is running and permitted. */
@Singleton
class ShizukuConnection @Inject constructor(
    @ApplicationContext private val context: Context,
) : ShizukuAccess {
    private val _state = MutableStateFlow<ShizukuState>(ShizukuState.NotRunning)
    override val state: StateFlow<ShizukuState> = _state.asStateFlow()

    @Volatile
    var service: IPrivilegedInput? = null
        private set

    private val serviceArgs = Shizuku.UserServiceArgs(ComponentName(context.packageName, PrivilegedInputService::class.java.name))
        .daemon(false)
        .processNameSuffix("privileged")
        .debuggable(BuildConfig.DEBUG)
        .version(SERVICE_VERSION)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) return
            service = IPrivilegedInput.Stub.asInterface(binder)
            _state.value = ShizukuState.Ready
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            refresh()
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        service = null
        refresh()
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    private var started = false

    fun start() {
        if (started) return
        started = true
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    override fun requestPermission() {
        if (Shizuku.pingBinder() && !Shizuku.shouldShowRequestPermissionRationale()) Shizuku.requestPermission(REQUEST_CODE)
    }

    override fun refresh() {
        _state.value = when {
            !Shizuku.pingBinder() -> if (isInstalled()) ShizukuState.NotRunning else ShizukuState.NotInstalled
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuState.PermissionRequired
            service != null -> ShizukuState.Ready
            else -> {
                bind()
                ShizukuState.Connecting
            }
        }
    }

    fun rebind() {
        runCatching { Shizuku.unbindUserService(serviceArgs, connection, true) }
        service = null
        refresh()
    }

    private fun bind() {
        try {
            Shizuku.bindUserService(serviceArgs, connection)
        } catch (e: RuntimeException) {
            Log.w("ThorErgo", "Could not bind the Shizuku service", e)
        }
    }

    private fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val REQUEST_CODE = 0x7e60
        private const val SERVICE_VERSION = 1
    }
}
