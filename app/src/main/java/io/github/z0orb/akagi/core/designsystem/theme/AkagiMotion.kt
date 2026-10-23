package io.github.z0orb.akagi.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/** Motion tokens (PLAN §7.18): short, with Material's emphasized easing, so screens arrive fast and settle softly. */
object AkagiMotion {
    private const val ENTER_MS = 250
    private const val EXIT_MS = 200
    private val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val emphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    fun <T> enter(): FiniteAnimationSpec<T> = tween(ENTER_MS, easing = emphasizedDecelerate)

    fun <T> exit(): FiniteAnimationSpec<T> = tween(EXIT_MS, easing = emphasizedAccelerate)
}
