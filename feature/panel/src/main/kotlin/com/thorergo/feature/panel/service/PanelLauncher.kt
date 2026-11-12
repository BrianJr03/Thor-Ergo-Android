package com.thorergo.feature.panel.service

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Starts and stops the keyboard panel. */
interface PanelControl {
    val running: StateFlow<Boolean>

    fun start()

    fun stop()
}

@Singleton
class PanelLauncher @Inject constructor(@ApplicationContext private val context: Context) : PanelControl {
    private val _running = MutableStateFlow(false)
    override val running: StateFlow<Boolean> = _running.asStateFlow()

    override fun start() {
        context.startForegroundService(Intent(context, PanelService::class.java))
    }

    override fun stop() {
        context.startService(PanelService.stopIntent(context))
    }

    internal fun setRunning(running: Boolean) {
        _running.value = running
    }
}
