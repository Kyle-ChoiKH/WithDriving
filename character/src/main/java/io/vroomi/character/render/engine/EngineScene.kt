package io.vroomi.character.render.engine

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
import io.vroomi.character.CharacterTarget
import io.vroomi.character.SizeMode
import io.vroomi.character.engine.EngineMechanics
import io.vroomi.character.engine.EnginePowertrain
import io.vroomi.character.engine.EngineView
import io.vroomi.character.engine.Powertrain
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.calibre.DEFAULT_NOW_MS

/** Animation state of the Cutaway scene, hoisted per screen. The app reads the chip and the events. */
@Stable
class EngineSceneState internal constructor(internal val rt: EngineRuntime) {
    var chip by mutableStateOf<String?>(null)
        internal set
    val view: EngineView get() = rt.view
    val powertrain: Powertrain get() = rt.mech.powertrain
    /** Estimated engine rpm (0 for an EV), for the optional hum. */
    val estimatedRpm: Double get() = if (rt.mech.powertrain == Powertrain.ICE) rt.mech.engine.rpm else 0.0
    fun drainEvents(): List<EngineMechanics.Event> = rt.mech.drainEvents()
    /** Drops the cached section layers (another theme is about to be shown); they are rebuilt when drawn again. */
    fun releaseLayers() = rt.releaseLayers()
}

@Composable
fun rememberEngineSceneState(): EngineSceneState = remember { EngineSceneState(EngineRuntime()) }

/**
 * The Cutaway theme: a sectioned powertrain driven by the car, drawn procedurally at 60 fps.
 * [forced] overrides [choice] (a demo). [interactive] (parked only): tap toggles SIDE and FRONT views.
 */
@Composable
fun EngineScene(
    target: CharacterTarget,
    options: SceneOptions,
    choice: EnginePowertrain,
    modifier: Modifier = Modifier,
    state: EngineSceneState = rememberEngineSceneState(),
    forced: Powertrain? = null,
    interactive: Boolean = false,
    showFrameTime: Boolean = false,
    onFirstFrame: (() -> Unit)? = null,
) {
    val rt = state.rt
    val currentFirst by rememberUpdatedState(onFirstFrame)
    val drawn = remember { BooleanArray(2) }
    val tm = rememberTextMeasurer()
    val currentTarget by rememberUpdatedState(target)
    val currentOptions by rememberUpdatedState(options)
    val currentChoice by rememberUpdatedState(choice)
    val currentForced by rememberUpdatedState(forced)
    var frame by remember { mutableLongStateOf(0L) }
    val drawMs = remember { FloatArray(1) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
                rt.step(dt, currentTarget, currentOptions, currentChoice, currentForced, System.currentTimeMillis())
                val c = rt.mech.chip
                if (c != state.chip) state.chip = c
                frame = now
                if (drawn[0] && !drawn[1]) { drawn[1] = true; currentFirst?.invoke() }
            }
        }
    }

    val tap = if (interactive) Modifier.pointerInput(Unit) { detectTapGestures { rt.requestToggle() } } else Modifier
    Canvas(modifier.then(tap)) {
        @Suppress("UNUSED_VARIABLE") val f = frame
        val t0 = if (showFrameTime) System.nanoTime() else 0L
        rt.draw(this, tm, if (showFrameTime) drawMs[0] else null)
        if (showFrameTime) drawMs[0] = drawMs[0] * 0.9f + (System.nanoTime() - t0) / 1e6f * 0.1f
        drawn[0] = true
    }
}

/** A still frame after simulating [seconds] of [target] (no frame clock), for snapshots and previews. */
@Composable
fun EngineSceneStill(
    target: CharacterTarget,
    options: SceneOptions,
    choice: EnginePowertrain,
    modifier: Modifier = Modifier,
    view: EngineView = EngineView.SIDE,
    seconds: Float = 3f,
    forced: Powertrain? = null,
    nowMs: Long = DEFAULT_NOW_MS,
    callouts: Boolean = true,
) {
    val rt = remember(target, options, choice, view, seconds, forced, nowMs, callouts) {
        EngineRuntime().also { r ->
            r.calloutsEnabled = callouts
            r.setView(view)
            val frames = (seconds * 60).toInt()
            val start = nowMs - (seconds * 1000).toLong()
            for (i in 0 until frames) r.step(1f / 60f, target, options, choice, forced, start + i * 1000L / 60)
            r.setView(view)
            settle(r)
        }
    }
    val tm = rememberTextMeasurer()
    Canvas(modifier) { rt.draw(this, tm) }
}

/** Jump the layout springs to their targets (a still frame should not catch them mid-flight). */
internal fun settle(r: EngineRuntime) {
    r.cx.snap(r.cx.value); r.cy.snap(r.cy.value)
}

/** A miniature for the theme picker card (parked, idling, still or live). */
@Composable
fun EngineMiniature(choice: EnginePowertrain, modifier: Modifier = Modifier, nowMs: Long? = null) {
    val target = remember { CharacterTarget(sizeMode = SizeMode.FULL) }
    val options = remember { SceneOptions(hourOfDay = 14f) }
    val forced = if (choice == EnginePowertrain.EV) Powertrain.EV else Powertrain.ICE
    if (nowMs != null) {
        EngineSceneStill(target, options, choice, modifier, seconds = 1.2f, forced = forced, nowMs = nowMs, callouts = false)
    } else {
        val state = remember { EngineSceneState(EngineRuntime().also { it.calloutsEnabled = false }) }
        EngineScene(target, options, choice, modifier, state, forced = forced)
    }
}
