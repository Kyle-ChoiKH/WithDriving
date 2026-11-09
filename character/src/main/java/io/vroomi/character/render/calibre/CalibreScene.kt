package io.vroomi.character.render.calibre

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import io.vroomi.character.CharacterTarget
import io.vroomi.character.SizeMode
import io.vroomi.character.calibre.CalibreFinish
import io.vroomi.character.calibre.CalibreMechanics
import io.vroomi.character.calibre.CalibreView
import io.vroomi.character.render.SceneOptions
import java.time.ZoneId

/**
 * Animation state of the Calibre scene, hoisted per screen like VroomiSceneState. The app reads the
 * status chip and the power reserve from it (to show the chip and to persist the reserve).
 */
@Stable
class CalibreSceneState internal constructor(internal val rt: CalibreRuntime) {
    /** Status chip chosen by the mechanics (Korean, at most 12 characters). */
    var chip by mutableStateOf<String?>(null)
        internal set
    val reserveHours: Float get() = rt.mech.reserveH
    val view: CalibreView get() = rt.view
    /** Real time keeps draining the barrel while the app is closed (never below 6 h). */
    fun restoreReserve(savedH: Float, savedAtMs: Long, nowMs: Long) = rt.mech.restoreReserve(savedH, savedAtMs, nowMs)
    /** Mechanics events since the last call (optional mechanical sound). */
    fun drainEvents(): List<CalibreMechanics.Event> = rt.mech.drainEvents()
    /** Drops the cached static layers (another theme is about to be shown); they are rebuilt when drawn again. */
    fun releaseLayers() = rt.releaseLayers()
}

@Composable
fun rememberCalibreSceneState(): CalibreSceneState = remember { CalibreSceneState(CalibreRuntime()) }

/**
 * The Calibre theme: an automatic chronograph movement driven by the car, drawn procedurally on a
 * Compose Canvas at 60 fps. [interactive] (parked only): tap the watch to flip caseback and dial.
 */
@Composable
fun CalibreScene(
    target: CharacterTarget,
    options: SceneOptions,
    finish: CalibreFinish,
    modifier: Modifier = Modifier,
    state: CalibreSceneState = rememberCalibreSceneState(),
    interactive: Boolean = false,
    showFrameTime: Boolean = false,
    onFirstFrame: (() -> Unit)? = null,
) {
    val rt = state.rt
    val currentFirst by rememberUpdatedState(onFirstFrame)
    val drawn = remember { BooleanArray(2) }
    val tm = rememberTextMeasurer()
    val density = LocalDensity.current
    val text = remember(density) { CalibreText(tm, density) }
    val currentTarget by rememberUpdatedState(target)
    val currentOptions by rememberUpdatedState(options)
    val currentFinish by rememberUpdatedState(finish)
    var frame by remember { mutableLongStateOf(0L) }
    val drawMs = remember { FloatArray(1) } // plain holder: written while drawing
    val zone = remember { ZoneId.systemDefault() }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
                rt.step(dt, currentTarget, currentOptions, currentFinish, System.currentTimeMillis())
                val c = rt.mech.chip
                if (c != state.chip) state.chip = c
                frame = now
                if (drawn[0] && !drawn[1]) { drawn[1] = true; currentFirst?.invoke() }
            }
        }
    }

    val tap = if (interactive) Modifier.pointerInput(Unit) { detectTapGestures { rt.requestFlip() } } else Modifier
    Canvas(modifier.then(tap)) {
        @Suppress("UNUSED_VARIABLE") val f = frame
        val t0 = if (showFrameTime) System.nanoTime() else 0L
        rt.draw(this, tm, text, System.currentTimeMillis(), zone, if (showFrameTime) drawMs[0] else null)
        if (showFrameTime) drawMs[0] = drawMs[0] * 0.9f + (System.nanoTime() - t0) / 1e6f * 0.1f
        drawn[0] = true
    }
}

/**
 * A still frame after simulating [seconds] of [target] (no frame clock), for snapshots and previews.
 * [nowMs] and [zone] fix the clock so the hands are reproducible.
 */
@Composable
fun CalibreSceneStill(
    target: CharacterTarget,
    options: SceneOptions,
    finish: CalibreFinish,
    modifier: Modifier = Modifier,
    view: CalibreView = CalibreView.CASEBACK,
    seconds: Float = 3f,
    nowMs: Long = DEFAULT_NOW_MS,
    zone: ZoneId = ZoneId.of("Asia/Seoul"),
    reserveH: Float = 41f,
) {
    val rt = remember(target, options, finish, view, seconds, nowMs, reserveH) {
        CalibreRuntime(reserveH).also { r ->
            r.setView(view)
            val frames = (seconds * 60).toInt()
            val start = nowMs - (seconds * 1000).toLong()
            for (i in 0 until frames) r.step(1f / 60f, target, options, finish, start + i * 1000L / 60)
            r.setView(view)
        }
    }
    val tm = rememberTextMeasurer()
    val density = LocalDensity.current
    val text = remember(density) { CalibreText(tm, density) }
    Canvas(modifier) { rt.draw(this, tm, text, nowMs, zone) }
}

/** A live miniature for the picker cards (parked; slow rotor sway, real time). */
@Composable
fun CalibreMiniature(finish: CalibreFinish, modifier: Modifier = Modifier, view: CalibreView = CalibreView.CASEBACK, nowMs: Long? = null) {
    val target = remember { CharacterTarget(sizeMode = SizeMode.FULL) }
    val options = remember { SceneOptions(hourOfDay = 14f) }
    if (nowMs != null) {
        CalibreSceneStill(target, options, finish, modifier, view, seconds = 1f, nowMs = nowMs)
    } else {
        val state = remember(view) { CalibreSceneState(CalibreRuntime().also { it.setView(view) }) }
        CalibreScene(target, options, finish, modifier, state)
    }
}

/** 2026-11-01 10:09:36 KST: the classic "ten past ten" pose for screenshots. */
const val DEFAULT_NOW_MS = 1_793_495_376_000L
