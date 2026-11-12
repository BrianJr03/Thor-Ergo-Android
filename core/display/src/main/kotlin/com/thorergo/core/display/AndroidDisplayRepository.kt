package com.thorergo.core.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

internal class AndroidDisplayRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : DisplayRepository {
    private val manager = context.getSystemService(DisplayManager::class.java)

    override val displays: Flow<DualDisplays?> = callbackFlow {
        fun publish() {
            trySend(pairDisplays(manager.displays.map(::info), Display.DEFAULT_DISPLAY))
        }
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = publish()
            override fun onDisplayRemoved(displayId: Int) = publish()
            override fun onDisplayChanged(displayId: Int) = publish()
        }
        manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        publish()
        awaitClose { manager.unregisterDisplayListener(listener) }
    }.distinctUntilChanged()

    /** Current logical size, which follows rotation (unlike the physical mode). */
    private fun info(display: Display): DisplayInfo {
        val bounds = context.createDisplayContext(display).getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        return DisplayInfo(display.displayId, display.name, bounds.width(), bounds.height())
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DisplayModule {
    @Binds
    abstract fun displayRepository(impl: AndroidDisplayRepository): DisplayRepository
}
