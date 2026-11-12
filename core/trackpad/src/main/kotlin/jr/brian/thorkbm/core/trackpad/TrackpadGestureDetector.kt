package jr.brian.thorkbm.core.trackpad

import jr.brian.thorkbm.core.model.MouseButton
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

sealed interface TrackpadOutput {
    /** Relative pointer motion in pixels. */
    data class Move(val dx: Int, val dy: Int) : TrackpadOutput

    /** Wheel motion in high-resolution units (120 = one notch); positive [vertical] scrolls up. */
    data class Scroll(val horizontal: Int, val vertical: Int) : TrackpadOutput

    data class Click(val button: MouseButton) : TrackpadOutput

    data class StartGlide(val glide: Glide) : TrackpadOutput
}

/**
 * Turns raw multi-touch contacts on the pad into pointer motion, scrolling and clicks.
 * Ported from Ratatoskr's `TouchGestureProcessor`. Deterministic: times are passed in, and
 * glides are returned for the host to animate. Not thread-safe; call from one thread.
 */
class TrackpadGestureDetector(var config: TrackpadConfig = TrackpadConfig()) {
    private class Contact(var startX: Float, var startY: Float, var lastX: Float, var lastY: Float)

    private val contacts = LinkedHashMap<Long, Contact>()
    private var startTime = 0L
    private var lastMoveTime = 0L
    private var travelled = 0.0
    private var maxExcursion = 0.0
    private var maxFingers = 0
    private var pointerActive = false
    private var suppressTaps = false
    private var misuseLock = false
    private var restX = 0.0
    private var restY = 0.0
    private var velX = 0.0
    private var velY = 0.0
    private var velTime = 0L
    private var scrollRestX = 0.0
    private var scrollRestY = 0.0

    val activeContacts: Int get() = contacts.size

    fun down(id: Long, x: Float, y: Float, timeMs: Long): List<TrackpadOutput> {
        if (contacts.isEmpty()) suppressTaps = false
        contacts[id] = Contact(x, y, x, y)
        val count = contacts.size
        maxFingers = max(maxFingers, count)
        if (count >= MISUSE_FINGERS) {
            // A palm or a whole hand: ignore everything until every finger lifts.
            misuseLock = true
            velX = 0.0
            velY = 0.0
            return emptyList()
        }
        if (count == 1) {
            startTime = timeMs
            lastMoveTime = timeMs
            travelled = 0.0
            maxExcursion = 0.0
            restX = 0.0
            restY = 0.0
            velX = 0.0
            velY = 0.0
            pointerActive = false
        }
        return emptyList()
    }

    fun move(id: Long, x: Float, y: Float, timeMs: Long): List<TrackpadOutput> {
        val contact = contacts[id] ?: return emptyList()
        var dx = (x - contact.lastX).toDouble()
        var dy = (y - contact.lastY).toDouble()
        contact.lastX = x
        contact.lastY = y
        if (misuseLock) return emptyList()

        travelled += hypot(dx, dy)
        val excursion = hypot((x - contact.startX).toDouble(), (y - contact.startY).toDouble())
        maxExcursion = max(maxExcursion, excursion)
        val dtMs = timeMs - lastMoveTime
        lastMoveTime = timeMs

        return when (contacts.size) {
            1 -> pointer(contact, x, y, dx, dy, excursion, dtMs, timeMs)
            2 -> scroll(dx, dy)
            else -> emptyList()
        }
    }

    private fun pointer(contact: Contact, x: Float, y: Float, rawDx: Double, rawDy: Double, excursion: Double, dtMs: Long, timeMs: Long): List<TrackpadOutput> {
        if (maxFingers >= 2) {
            // The trailing finger of a scroll still belongs to that gesture: never move the pointer.
            restX = 0.0
            restY = 0.0
            velX = 0.0
            velY = 0.0
            velTime = 0
            return emptyList()
        }
        var dx = rawDx
        var dy = rawDy
        val reliable = dtMs in 1..MAX_VELOCITY_INTERVAL_MS
        val intervalS = max(1L, dtMs) / 1000.0
        val speed = if (reliable) hypot(dx, dy) / intervalS else 0.0
        if (!pointerActive) {
            // Landing jitter is not travel. Once past the slop, replay only the travel beyond it.
            if (excursion < TAP_MAX_DISTANCE_PX) return emptyList()
            pointerActive = true
            val excess = max(0.0, 1.0 - TAP_MAX_DISTANCE_PX / excursion)
            dx = (x - contact.startX) * excess
            dy = (y - contact.startY) * excess
        }
        val gain = config.sensitivity * (1.0 + min(MAX_ACCELERATION, speed / ACCELERATION_SPEED))
        val targetX = dx * gain + restX
        val targetY = dy * gain + restY
        val ix = targetX.toInt()
        val iy = targetY.toInt()
        restX = targetX - ix
        restY = targetY - iy
        // Measured on every step, sub-pixel ones too: a slowing finger must slow the glide.
        velX = if (reliable) dx * gain / intervalS else 0.0
        velY = if (reliable) dy * gain / intervalS else 0.0
        velTime = timeMs
        return if (ix != 0 || iy != 0) listOf(TrackpadOutput.Move(ix, iy)) else emptyList()
    }

    private fun scroll(dx: Double, dy: Double): List<TrackpadOutput> {
        val divisor = max(4.0, 22.0 - config.scrollSpeed * 4.0)
        val vertical = (-dy * (HI_RES_NOTCH / divisor)).toInt()
        val horizontal = (dx * (HI_RES_NOTCH / divisor)).toInt()
        if (vertical == 0 && horizontal == 0) return emptyList()
        // Fine scroll scale keeps its fractional remainder so slow scrolling still advances.
        scrollRestX += horizontal * config.scrollScale
        scrollRestY += vertical * config.scrollScale
        val h = scrollRestX.toInt()
        val v = scrollRestY.toInt()
        scrollRestX -= h
        scrollRestY -= v
        return if (h != 0 || v != 0) listOf(TrackpadOutput.Scroll(h, v)) else emptyList()
    }

    fun up(id: Long, timeMs: Long): List<TrackpadOutput> {
        contacts.remove(id) ?: return emptyList()
        if (misuseLock) {
            if (contacts.isEmpty()) {
                misuseLock = false
                maxFingers = 0
                travelled = 0.0
                velX = 0.0
                velY = 0.0
                pointerActive = false
            }
            return emptyList()
        }
        if (contacts.size == 1) {
            // Two fingers became one. Keep the gesture multi-finger until the last lift.
            val remaining = contacts.values.first()
            remaining.startX = remaining.lastX
            remaining.startY = remaining.lastY
            maxExcursion = 0.0
            restX = 0.0
            restY = 0.0
            velX = 0.0
            velY = 0.0
            velTime = 0
            lastMoveTime = timeMs
        }
        if (contacts.isNotEmpty()) return emptyList()

        val fingers = maxFingers
        maxFingers = 0
        val duration = timeMs - startTime
        if (timeMs - velTime > GLIDE_MAX_IDLE_MS) {
            // The finger had stopped before lifting; its last speed is stale.
            velX = 0.0
            velY = 0.0
        }
        val tapDistance = if (fingers == 1) maxExcursion else travelled / max(1, fingers)
        val isTap = !suppressTaps && !pointerActive && tapDistance < TAP_MAX_DISTANCE_PX && duration < TAP_MAX_TIME_MS
        return when {
            isTap -> when {
                fingers == 1 && config.tapToClick -> listOf(TrackpadOutput.Click(MouseButton.LEFT))
                fingers == 2 && config.twoFingerRightClick -> listOf(TrackpadOutput.Click(MouseButton.RIGHT))
                fingers == 3 && config.threeFingerMiddleClick -> listOf(TrackpadOutput.Click(MouseButton.MIDDLE))
                else -> emptyList()
            }
            config.glide && fingers == 1 && hypot(velX, velY) > Glide.START_SPEED ->
                listOf(TrackpadOutput.StartGlide(Glide(velX, velY, config.friction)))
            else -> emptyList()
        }
    }

    /** While a header mouse button is held the current group may move and scroll, but never click. */
    fun suppressTapsForCurrentGroup() {
        if (contacts.isNotEmpty()) suppressTaps = true
    }

    fun reset() {
        contacts.clear()
        maxFingers = 0
        travelled = 0.0
        maxExcursion = 0.0
        pointerActive = false
        suppressTaps = false
        misuseLock = false
        restX = 0.0
        restY = 0.0
        velX = 0.0
        velY = 0.0
        scrollRestX = 0.0
        scrollRestY = 0.0
    }

    companion object {
        const val TAP_MAX_TIME_MS = 350L
        const val TAP_MAX_DISTANCE_PX = 12.0
        const val HI_RES_NOTCH = 120.0
        const val GLIDE_MAX_IDLE_MS = 100L
        const val MISUSE_FINGERS = 4
        private const val MAX_VELOCITY_INTERVAL_MS = 500L
        private const val ACCELERATION_SPEED = 3000.0
        private const val MAX_ACCELERATION = 1.6
    }
}
