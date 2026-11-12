package com.thorergo.core.trackpad

import com.thorergo.core.model.Settings

data class TrackpadConfig(
    val sensitivity: Double = 1.5,
    /** 1 (precise) to 5 (fast). */
    val scrollSpeed: Int = 3,
    /** Fine scroll reduction, 0 < scale ≤ 1. */
    val scrollScale: Double = 1.0,
    val tapToClick: Boolean = true,
    val glide: Boolean = true,
    /** 1 (slickest) to 10 (most friction). */
    val friction: Int = 5,
    val twoFingerRightClick: Boolean = true,
    val threeFingerMiddleClick: Boolean = false,
) {
    companion object {
        fun from(settings: Settings) = TrackpadConfig(
            sensitivity = settings.sensitivity,
            scrollSpeed = settings.scrollSpeed,
            scrollScale = settings.scrollScale,
            tapToClick = settings.tapToClick,
            glide = settings.glide,
            friction = settings.friction,
        )
    }
}
