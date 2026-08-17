package com.abhimankolte.aethertube.tv.ui.common.compose

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp

/**
 * Shared motion tokens and animation specifications across all Compose TV screens.
 */
object MotionTokens {
    /** Standard duration for focus transitions (scale, color, glow). */
    const val FOCUS_ANIM_MS = 180

    /** Debounce interval before updating the ambient backdrop texture. */
    const val BACKDROP_DEBOUNCE_MS = 220L

    /** Snappy Apple TV-style focus scale spring with light overshoot. */
    val FocusScaleSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}

/**
 * Shared focus indicator metrics and dimming tokens.
 */
object FocusTokens {
    val RingWidth = 4.dp
    val RingInnerWidth = 1.5.dp
    const val UnfocusedDim = 0.35f
}
