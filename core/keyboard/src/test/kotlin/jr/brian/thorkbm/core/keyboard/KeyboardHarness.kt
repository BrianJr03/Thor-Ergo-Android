package jr.brian.thorkbm.core.keyboard

import jr.brian.thorkbm.core.keyboard.output.OutputEvent
import jr.brian.thorkbm.core.model.Settings

/** Drives [KeyboardReducer] with a virtual clock, executing timer commands like the real host. */
class KeyboardHarness(settings: Settings = Settings()) {
    var state = KeyboardState(settings = settings)
        private set
    var now = 0L
        private set
    val outputs = mutableListOf<OutputEvent>()
    val commands = mutableListOf<KeyboardCommand>()
    private val timers = mutableMapOf<KeyboardTimer, Long>()

    fun send(event: KeyboardEvent) {
        val transition = KeyboardReducer.reduce(state, event)
        state = transition.state
        for (command in transition.commands) {
            commands += command
            when (command) {
                is KeyboardCommand.Emit -> outputs += command.event
                is KeyboardCommand.StartTimer -> timers[command.timer] = now + command.delayMs
                is KeyboardCommand.CancelTimer -> timers.remove(command.timer)
                else -> Unit
            }
        }
    }

    fun advance(ms: Long) {
        val end = now + ms
        while (true) {
            val (timer, due) = timers.entries.filter { it.value <= end }.minByOrNull { it.value }?.toPair() ?: break
            timers.remove(timer)
            now = due
            send(KeyboardEvent.TimerFired(timer))
        }
        now = end
    }

    fun center(keyId: String): Pair<Float, Float> {
        val key = requireNotNull(state.activeKeys.firstOrNull { it.id == keyId }) { "No key $keyId on ${state.page}" }
        return key.rect.centerX to key.rect.centerY
    }

    fun down(keyId: String, pointer: Long = 1) {
        val (x, y) = center(keyId)
        send(KeyboardEvent.Down(pointer, x, y, now))
    }

    fun move(x: Float, y: Float, pointer: Long = 1) = send(KeyboardEvent.Move(pointer, x, y))

    fun up(pointer: Long = 1) = send(KeyboardEvent.Up(pointer, now))

    fun tap(keyId: String, pointer: Long = 1) {
        down(keyId, pointer)
        up(pointer)
    }

    val texts: List<String>
        get() = outputs.mapNotNull { (it as? OutputEvent.Text)?.text ?: (it as? OutputEvent.Literal)?.text ?: (it as? OutputEvent.Word)?.text }

    val activeTimers: Set<KeyboardTimer> get() = timers.keys
}
