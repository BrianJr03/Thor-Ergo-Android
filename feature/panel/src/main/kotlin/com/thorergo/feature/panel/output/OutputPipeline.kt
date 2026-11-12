package com.thorergo.feature.panel.output

import com.thorergo.core.injection.InputInjector
import com.thorergo.core.keyboard.output.ComposerState
import com.thorergo.core.keyboard.output.Injection
import com.thorergo.core.keyboard.output.OutputError
import com.thorergo.core.keyboard.output.OutputEvent
import com.thorergo.core.keyboard.output.OutputRouter
import com.thorergo.core.model.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

enum class PanelMessage {
    QUEUE_FULL,
    EXPIRED,
    INJECTION_FAILED,
    TEXT_UNAVAILABLE,
    NOT_ON_US_KEYBOARD,
    NO_ALTGR_SYMBOL,
    ALTGR_USE_SYMBOLS_PAGE,
    NO_PHYSICAL_SHORTCUT,
    INVALID_CHARACTER,
    ;

    companion object {
        fun of(error: OutputError): PanelMessage = valueOf(error.name)
    }
}

/**
 * First-in, first-out output with one item in flight. Input is never sent late: an item still
 * queued [EXPIRY_MS] after it was typed fails instead, and a failure drops everything pending.
 * Must be used from one thread (the ViewModel's main dispatcher).
 */
class OutputPipeline(
    private val router: OutputRouter,
    private val injector: InputInjector,
    private val language: () -> Language,
    private val clock: () -> Long,
    private val onComposerChanged: (ComposerState) -> Unit,
    private val onMessage: (PanelMessage) -> Unit,
) {
    private class Pending(val event: OutputEvent, val queuedAt: Long, val epoch: Int)

    private val queue = Channel<Pending>(CAPACITY)
    private var epoch = 0
    private var composer = ComposerState()

    fun start(scope: CoroutineScope) {
        scope.launch {
            for (item in queue) process(item)
        }
    }

    fun submit(event: OutputEvent) {
        if (queue.trySend(Pending(event, clock(), epoch)).isFailure) fail(PanelMessage.QUEUE_FULL)
    }

    /** Drops pending output and any composition in progress. Late items from before the reset are ignored. */
    fun reset() {
        epoch++
        while (queue.tryReceive().isSuccess) Unit
        updateComposer(router.reset(composer))
    }

    private suspend fun process(item: Pending) {
        if (item.epoch != epoch) return
        if (clock() - item.queuedAt > EXPIRY_MS) return fail(PanelMessage.EXPIRED)
        val result = router.route(composer, item.event, language())
        updateComposer(result.state)
        result.error?.let { onMessage(PanelMessage.of(it)) }
        for (injection in result.injections) {
            val ok = when (injection) {
                is Injection.Chord -> injector.chord(injection.code, injection.mods)
                is Injection.Commit -> injector.commitText(injection.text)
            }
            if (item.epoch != epoch) return
            if (!ok) {
                return fail(if (injection is Injection.Commit) PanelMessage.TEXT_UNAVAILABLE else PanelMessage.INJECTION_FAILED)
            }
        }
    }

    private fun updateComposer(state: ComposerState) {
        if (state != composer) {
            composer = state
            onComposerChanged(state)
        }
    }

    private fun fail(message: PanelMessage) {
        reset()
        onMessage(message)
    }

    companion object {
        const val CAPACITY = 48
        const val EXPIRY_MS = 750L
    }
}
