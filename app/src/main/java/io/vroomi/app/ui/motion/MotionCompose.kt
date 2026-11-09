package io.vroomi.app.ui.motion

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch

/**
 * The motion preferences of the moment. [reduced]: the system animator scale is 0 or 차분 모드 is on (every
 * passage becomes a crossfade, press feedback becomes opacity only). [moving]: only opacity moves.
 * [slow]: the debug "느린 애니메이션 ×5" factor.
 */
@Immutable
data class MotionPrefs(
    val reduced: Boolean = false,
    val moving: Boolean = false,
    val slow: Float = 1f,
    val parked: Boolean = true,
    /** Snapshots and previews: everything starts settled (no entrance runs). */
    val still: Boolean = false,
) {
    /** A spring token as a Compose spec (slowed for debugging: time scales with 1 / omega). */
    fun <T> spring(token: SpringToken, visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = token.dampingRatio, stiffness = token.stiffness / (slow * slow), visibilityThreshold = visibilityThreshold)

    /** An opacity tween on the Moving curve. */
    fun <T> fade(ms: Int, delayMs: Int = 0): FiniteAnimationSpec<T> =
        tween((ms * slow).toInt(), (delayMs * slow).toInt()) { Motion.MovingCurve.ease(it) }

    /** Position and size: a spring when allowed, otherwise an instant change (reduced motion, moving). */
    fun <T> move(token: SpringToken): AnimationSpec<T> = if (reduced || moving) tween(0) else spring(token)

    fun ms(v: Int): Long = (v * slow).toLong()
}

val LocalMotion = staticCompositionLocalOf { MotionPrefs() }

/** The motion preferences, settled in previews and snapshots (inspection mode): nothing waits for an entrance. */
@Composable
fun currentMotion(): MotionPrefs {
    val p = LocalMotion.current
    return if (LocalInspectionMode.current && !p.still) p.copy(still = true) else p
}

/**
 * Press feedback for every tappable surface while parked: scale 0.97 (large cards 0.985) and a 6 % overlay, on
 * the Press spring. Dragging off cancels cleanly. Under reduced motion it is opacity only. While moving there is
 * none at all (the driving rule: nothing scales or pulses; the toggle's new state is the feedback).
 * It never consumes the pointer: the clickable below still gets the tap.
 */
fun Modifier.pressFeedback(shape: Shape, large: Boolean = false, enabled: Boolean = true): Modifier = composed {
    val prefs = currentMotion()
    if (prefs.moving) return@composed this
    val press = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val scaleOk = !prefs.reduced
    val depth = if (large) 0.015f else 0.03f
    this
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                scope.launch { press.animateTo(1f, prefs.spring(Motion.Press)) }
                waitForUpOrCancellation(PointerEventPass.Initial)
                scope.launch { press.animateTo(0f, prefs.spring(Motion.Press)) }
            }
        }
        .graphicsLayer {
            val p = press.value
            if (scaleOk) { scaleX = 1f - depth * p; scaleY = 1f - depth * p }
            if (!scaleOk) alpha = 1f - 0.12f * p
        }
        .drawWithContent {
            drawContent()
            val p = press.value
            if (p > 0.01f) drawOutline(shape.createOutline(size, layoutDirection, this), Color.White, alpha = 0.06f * p)
        }
}

/** Haptics, parked only: a light tick on selection, a confirm on commit. Harmless without a vibrator. */
class Haptics(private val view: android.view.View, private val parked: () -> Boolean) {
    fun select() { if (parked()) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    fun confirm() {
        if (!parked()) return
        val c = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
        view.performHapticFeedback(c)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val prefs = currentMotion()
    return remember(view, prefs.parked) { Haptics(view) { prefs.parked } }
}
