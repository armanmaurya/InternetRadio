package com.armanmaurya.internetradio.ui.mobile.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * Material "shared axis X" navigation motion (the forward/back transition used across Android).
 *
 * Both screens move a short, fixed distance along the X axis while a *fade-through* swaps them:
 * the outgoing screen fades out during the first 35% of the motion and the incoming screen fades
 * in over the rest, so the two are never visible half-transparent on top of each other.
 */
object MaterialSharedAxis {
    private const val DURATION_MS = 300

    /** Outgoing screen is fully faded out at 35% of the duration. */
    private const val FADE_OUT_MS = (DURATION_MS * 0.35f).toInt()
    private const val FADE_IN_MS = DURATION_MS - FADE_OUT_MS

    /** Material 3 "emphasized" easing for the movement: quick start, long gentle settle. */
    private val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /**
     * Transitions for [slideDistancePx] (Material uses 30dp; convert with the current density).
     * `forward = true` is a push (new screen comes from the end side), `false` is a pop.
     */
    fun enter(slideDistancePx: Int, forward: Boolean): EnterTransition =
        slideInHorizontally(tween(DURATION_MS, easing = EmphasizedEasing)) {
            if (forward) slideDistancePx else -slideDistancePx
        } + fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = LinearOutSlowInEasing))

    fun exit(slideDistancePx: Int, forward: Boolean): ExitTransition =
        slideOutHorizontally(tween(DURATION_MS, easing = EmphasizedEasing)) {
            if (forward) -slideDistancePx else slideDistancePx
        } + fadeOut(tween(FADE_OUT_MS, easing = FastOutLinearInEasing))
}
