package jr.brian.thorkbm.core.trackpad

import kotlin.math.hypot

/**
 * Inertial pointer glide after a flick. Each [step] advances one [FRAME_MS] frame:
 * velocity decays by a friction-dependent factor and the integer part of the travel is emitted.
 */
class Glide(vx: Double, vy: Double, friction: Int) {
    private val decay = decayFor(friction)
    private var vx = vx
    private var vy = vy
    private var restX = 0.0
    private var restY = 0.0

    /** Returns the pixel delta for this frame, or `null` once the glide has stopped. */
    fun step(): Pair<Int, Int>? {
        vx *= decay
        vy *= decay
        if (hypot(vx, vy) < STOP_SPEED) return null
        restX += vx * FRAME_S
        restY += vy * FRAME_S
        val dx = restX.toInt()
        val dy = restY.toInt()
        restX -= dx
        restY -= dy
        return dx to dy
    }

    companion object {
        const val FRAME_MS = 16L
        private const val FRAME_S = FRAME_MS / 1000.0
        private const val STOP_SPEED = 12.0

        /** Glides start only from a flick faster than this (px/s). */
        const val START_SPEED = 140.0

        fun decayFor(friction: Int) = 0.97 - ((friction.coerceIn(1, 10) - 1) / 9.0) * 0.23
    }
}
