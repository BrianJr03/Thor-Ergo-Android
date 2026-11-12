package com.thorergo.core.keyboard

import com.thorergo.core.keyboard.layout.KeyAction
import com.thorergo.core.keyboard.layout.KeySpec
import com.thorergo.core.keyboard.layout.KeyText
import com.thorergo.core.keyboard.layout.LayoutEngine
import com.thorergo.core.keyboard.output.OutputEvent
import com.thorergo.core.keyboard.profile.LanguageProfile
import com.thorergo.core.model.Modifier
import com.thorergo.core.model.ModifierState
import com.thorergo.core.model.Page

/**
 * The keyboard state machine as a pure function. Timers, output and persistence are requested
 * through [KeyboardCommand]s, so every behaviour is testable without a clock or a device.
 */
object KeyboardReducer {
    private val SHORTCUT_MODIFIERS = setOf(Modifier.CTRL, Modifier.ALT, Modifier.SUPER, Modifier.ALTGR)

    fun reduce(state: KeyboardState, event: KeyboardEvent): Transition =
        Step(state).apply { handle(event) }.toTransition()

    private class Step(var state: KeyboardState) {
        private val commands = mutableListOf<KeyboardCommand>()

        fun toTransition() = Transition(state, commands)

        fun handle(event: KeyboardEvent) {
            when (event) {
                is KeyboardEvent.Down -> down(event)
                is KeyboardEvent.Move -> move(event)
                is KeyboardEvent.Up -> up(event)
                is KeyboardEvent.Cancel -> cancelContact(event.pointerId)
                is KeyboardEvent.TimerFired -> timerFired(event)
                is KeyboardEvent.ImeChanged -> {
                    val page = if (event.revision != state.ime.revision) 0 else state.ime.page
                    state = state.copy(ime = ImeState(event.preedit, event.candidates, event.revision, page))
                }
                is KeyboardEvent.SettingsChanged -> {
                    val languageChanged = event.settings.language != state.settings.language
                    state = state.copy(settings = event.settings)
                    if (languageChanged) {
                        state = state.copy(ime = ImeState())
                        resetContacts(clearModifiers = true)
                        emit(KeyboardCommand.ResetInput)
                    }
                }
                is KeyboardEvent.SelectView -> {
                    resetContacts(clearModifiers = true)
                    state = state.copy(overlay = Overlay.NONE, settings = state.settings.copy(view = event.view))
                    emit(KeyboardCommand.ResetInput)
                    emit(KeyboardCommand.PersistView(event.view))
                }
                KeyboardEvent.DismissMenu -> if (state.overlay == Overlay.MENU) state = state.copy(overlay = Overlay.NONE)
                KeyboardEvent.CloseSettings -> closeSettings()
                KeyboardEvent.ReleaseAll -> {
                    resetContacts(clearModifiers = true)
                    emit(KeyboardCommand.ResetInput)
                }
                KeyboardEvent.Reconnect -> {
                    if (state.overlay == Overlay.MENU) state = state.copy(overlay = Overlay.NONE)
                    emit(KeyboardCommand.Reconnect)
                }
            }
        }

        private fun emit(command: KeyboardCommand) {
            commands += command
        }

        private fun down(event: KeyboardEvent.Down) {
            if (event.pointerId in state.presses) return
            // A second finger must not type through an open accent menu.
            state.popup?.let { cancelContact(it.pointerId) }
            if (state.overlay == Overlay.MENU) {
                state = state.copy(overlay = Overlay.NONE)
                return
            }
            val key = state.activeKeys.lastOrNull { it.rect.contains(event.x, event.y) } ?: return
            val action = key.action
            if (action is KeyAction.Mouse) {
                if (state.overlay != Overlay.SETTINGS) {
                    state = state.copy(presses = state.presses + (event.pointerId to newPress(key)))
                    emit(KeyboardCommand.Mouse(action.button, event.pointerId, down = true))
                }
                return
            }
            if (state.settings.haptics) emit(KeyboardCommand.KeyFeedback)

            // Resolve older letters before a new key so overlapping thumbs keep typing order.
            for ((id, previous) in state.presses) {
                if (!previous.committed && !previous.cancelled && previous.inside && previous.key.action is KeyAction.Text) {
                    send(previous)
                    cancelTimers(previous)
                    updatePress(id) { it.copy(committed = true) }
                }
            }

            var press = newPress(key)
            when {
                action is KeyAction.Text &&
                    LanguageProfile.of(state.settings.language).accentsFor(action.char).isNotEmpty() &&
                    press.mods.none { it in SHORTCUT_MODIFIERS } ->
                    emit(KeyboardCommand.StartTimer(KeyboardTimer.LongPress(press.serial), state.settings.longPressMs.toLong()))

                action is KeyAction.Key && action.code.repeats -> {
                    send(press, consume = false)
                    press = press.copy(committed = true)
                    if (state.settings.repeatEnabled) {
                        emit(KeyboardCommand.StartTimer(KeyboardTimer.Repeat(press.serial), state.settings.repeatDelayMs.toLong()))
                    }
                }
            }
            state = state.copy(presses = state.presses + (event.pointerId to press))
        }

        private fun newPress(key: KeySpec): Press {
            val serial = state.nextSerial
            state = state.copy(nextSerial = serial + 1)
            return Press(
                serial = serial,
                key = key,
                mods = state.activeModifiers(),
                upper = state.shift != ModifierState.OFF,
                caps = state.shift == ModifierState.LOCKED,
            )
        }

        private fun move(event: KeyboardEvent.Move) {
            val press = state.presses[event.pointerId] ?: return
            val popup = state.popup
            if (popup != null && popup.pointerId == event.pointerId) {
                val selected = popup.options.indexOfFirst { it.rect.contains(event.x, event.y) }.takeIf { it >= 0 }
                state = state.copy(popup = popup.copy(selected = selected))
                return
            }
            val inside = press.key.rect.contains(event.x, event.y)
            if (press.key.action is KeyAction.Mouse) return
            if (!inside) {
                // No sliding onto a different key: leaving the key cancels it.
                if (!press.cancelled) cancelTimers(press)
                updatePress(event.pointerId) { it.copy(inside = false, cancelled = true) }
            }
        }

        private fun up(event: KeyboardEvent.Up) {
            val press = state.presses[event.pointerId] ?: return
            state = state.copy(presses = state.presses - event.pointerId)
            cancelTimers(press)
            val action = press.key.action
            val popup = state.popup
            when {
                action is KeyAction.Mouse -> emit(KeyboardCommand.Mouse(action.button, event.pointerId, down = false))
                popup != null && popup.pointerId == event.pointerId -> {
                    state = state.copy(popup = null)
                    popup.selected?.let { index ->
                        val accent = popup.options[index].action as KeyAction.Accent
                        emit(KeyboardCommand.Emit(OutputEvent.Literal(accent.char)))
                        consumeModifiers(press.mods)
                    }
                }
                !press.cancelled && press.inside && !press.committed -> activate(press, event.timeMs)
                press.committed -> consumeModifiers(press.mods)
            }
        }

        private fun cancelContact(pointerId: Long) {
            val press = state.presses[pointerId]
            if (press != null) {
                state = state.copy(presses = state.presses - pointerId)
                cancelTimers(press)
                (press.key.action as? KeyAction.Mouse)?.let { emit(KeyboardCommand.Mouse(it.button, pointerId, down = false)) }
            }
            if (state.popup?.pointerId == pointerId) state = state.copy(popup = null)
        }

        private fun timerFired(event: KeyboardEvent.TimerFired) {
            when (val timer = event.timer) {
                is KeyboardTimer.LongPress -> {
                    val (pointerId, press) = pressWithSerial(timer.serial) ?: return
                    if (press.cancelled || !press.inside || press.committed) return
                    val action = press.key.action as? KeyAction.Text ?: return
                    val accents = LanguageProfile.of(state.settings.language).accentsFor(action.char)
                    val variants = if (press.upper) accents.map(LanguageProfile::upper) else accents
                    val (frame, options) = LayoutEngine.popup(press.key, variants)
                    state = state.copy(popup = AccentPopup(pointerId, frame, options))
                }
                is KeyboardTimer.Repeat -> {
                    val (_, press) = pressWithSerial(timer.serial) ?: return
                    if (press.cancelled || !press.inside) return
                    send(press, consume = false)
                    emit(KeyboardCommand.StartTimer(timer, state.settings.repeatIntervalMs.toLong()))
                }
            }
        }

        private fun pressWithSerial(serial: Long): Pair<Long, Press>? =
            state.presses.entries.firstOrNull { it.value.serial == serial }?.toPair()

        private fun activate(press: Press, timeMs: Long) {
            when (val action = press.key.action) {
                is KeyAction.Text, is KeyAction.Key -> send(press)
                is KeyAction.Dead -> {
                    emit(KeyboardCommand.Emit(OutputEvent.Dead(action.dead)))
                    changePage(Page.ABC)
                }
                is KeyAction.ModifierKey -> tapModifier(action.modifier, timeMs)
                KeyAction.CapsLock -> {
                    val locked = state.shift == ModifierState.LOCKED
                    setModifier(Modifier.SHIFT, if (locked) ModifierState.OFF else ModifierState.LOCKED)
                }
                is KeyAction.GoToPage -> changePage(action.page)
                KeyAction.Fn -> {
                    closeSettings()
                    if (state.page != Page.FN) {
                        state = state.copy(previousPage = state.page)
                        changePage(Page.FN)
                    } else {
                        changePage(state.previousPage)
                    }
                }
                KeyAction.Mode -> {
                    resetContacts(clearModifiers = false)
                    emit(KeyboardCommand.ReleasePointer)
                    state = state.copy(overlay = Overlay.MENU)
                }
                KeyAction.Settings -> if (state.overlay == Overlay.SETTINGS) {
                    closeSettings()
                } else {
                    resetContacts(clearModifiers = true)
                    emit(KeyboardCommand.ResetInput)
                    state = state.copy(overlay = Overlay.SETTINGS)
                }
                KeyAction.Hide -> {
                    resetContacts(clearModifiers = true)
                    emit(KeyboardCommand.ResetInput)
                    emit(KeyboardCommand.Hide)
                }
                is KeyAction.Candidate -> {
                    val ime = state.ime
                    val index = ime.page * LayoutEngine.CANDIDATES_PER_PAGE + action.slot
                    if (index in ime.candidates.indices) emit(KeyboardCommand.Emit(OutputEvent.Choose(index, ime.revision)))
                }
                is KeyAction.CandidatePage -> {
                    val ime = state.ime
                    state = state.copy(ime = ime.copy(page = (ime.page + action.delta).coerceIn(0, ime.pageCount - 1)))
                }
                is KeyAction.Mouse, is KeyAction.Accent -> Unit
            }
        }

        private fun tapModifier(modifier: Modifier, timeMs: Long) {
            val current = state.modifier(modifier)
            val last = state.lastModifierTap[modifier]
            val doubleTap = last != null && timeMs - last < state.settings.capsWindowMs
            val next = when (current) {
                ModifierState.OFF -> ModifierState.ONE_SHOT
                ModifierState.ONE_SHOT -> if (doubleTap) ModifierState.LOCKED else ModifierState.OFF
                ModifierState.LOCKED -> ModifierState.OFF
            }
            setModifier(modifier, next)
            state = state.copy(lastModifierTap = state.lastModifierTap + (modifier to timeMs))
        }

        private fun send(press: Press, consume: Boolean = true) {
            when (val action = press.key.action) {
                is KeyAction.Text -> {
                    val shift = when {
                        press.caps -> ModifierState.LOCKED
                        press.upper -> ModifierState.ONE_SHOT
                        else -> ModifierState.OFF
                    }
                    val text = KeyText.of(action, state.settings.language, shift)
                    emit(KeyboardCommand.Emit(OutputEvent.Text(text, action.code, press.mods, press.caps, press.upper && !press.caps)))
                }
                is KeyAction.Key -> {
                    // Caps Lock is not a held Shift: it must not turn arrows into selections.
                    val mods = if (press.caps) press.mods - Modifier.SHIFT else press.mods
                    emit(KeyboardCommand.Emit(OutputEvent.Key(action.code, mods)))
                }
                else -> Unit
            }
            if (consume) consumeModifiers(press.mods)
        }

        private fun consumeModifiers(mods: Set<Modifier>) {
            for (modifier in mods) {
                if (state.modifier(modifier) == ModifierState.ONE_SHOT) setModifier(modifier, ModifierState.OFF)
            }
        }

        private fun setModifier(modifier: Modifier, value: ModifierState) {
            state = state.copy(modifiers = state.modifiers + (modifier to value))
        }

        private fun changePage(page: Page) {
            // Keep one-shot modifiers so Ctrl → ABC → C still works.
            resetContacts(clearModifiers = false)
            emit(KeyboardCommand.ReleasePointer)
            state = state.copy(page = page, overlay = Overlay.NONE)
        }

        private fun closeSettings() {
            if (state.overlay == Overlay.SETTINGS) state = state.copy(overlay = Overlay.NONE)
        }

        private fun resetContacts(clearModifiers: Boolean) {
            state.presses.values.forEach(::cancelTimers)
            state = state.copy(
                presses = emptyMap(),
                popup = null,
                overlay = if (state.overlay == Overlay.MENU) Overlay.NONE else state.overlay,
            )
            if (clearModifiers) state = state.copy(modifiers = KeyboardState.NO_MODIFIERS, lastModifierTap = emptyMap())
        }

        private fun cancelTimers(press: Press) {
            val action = press.key.action
            if (action is KeyAction.Text) emit(KeyboardCommand.CancelTimer(KeyboardTimer.LongPress(press.serial)))
            if (action is KeyAction.Key && action.code.repeats) emit(KeyboardCommand.CancelTimer(KeyboardTimer.Repeat(press.serial)))
        }

        private inline fun updatePress(pointerId: Long, transform: (Press) -> Press) {
            val press = state.presses[pointerId] ?: return
            state = state.copy(presses = state.presses + (pointerId to transform(press)))
        }
    }
}
