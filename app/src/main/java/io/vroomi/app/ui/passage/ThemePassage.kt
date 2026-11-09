package io.vroomi.app.ui.passage

import io.vroomi.app.ui.motion.Motion
import io.vroomi.character.calibre.SceneTheme
import kotlin.math.abs

/** How the incoming theme arrives. */
enum class PassageStyle {
    /** In place (arrows, a return, a demo): the incoming theme's signature entrance over the receding old one. */
    ENTRANCE,
    /** A gallery card grows into the stage: the card's still is the reveal; the live scene fades in under it. */
    GROW,
    /** A committed swipe: the neighbour's still already fills the stage; the live scene replaces it. */
    SWIPE,
    /** Moving, reduced motion or a quiet hand-over: opacity only. */
    CROSSFADE,
}

enum class PassagePhase { IDLE, CAPTURING, RELEASING, PREWARMING, REVEALING, SETTLING }

class PassageRequest(
    val target: SceneTheme,
    val style: PassageStyle = PassageStyle.ENTRANCE,
    /** 0..1: scales both the entrance's distance and its duration (0.5 halves both). */
    val intensity: Float = 1f,
    /** The outgoing image is already on screen (a swipe froze it): nothing to capture. */
    val snapshotHeld: Boolean = false,
)

/**
 * What the stage draws this frame. Draw order, bottom to top: backdrop; the incoming theme's still at
 * [heroAlpha]; the live scene unmasked at [baseAlpha]; the outgoing snapshot ([snapshotAlpha], [snapshotScale],
 * [snapshotDim], [snapshotBlur]); the live scene through its entrance mask at progress [reveal] when [maskOn]
 * (at [maskAlpha]).
 */
class PassageOutput {
    var phase = PassagePhase.IDLE
    /** The theme on screen before the passage (null at launch). */
    var from: SceneTheme? = null
    var to: SceneTheme? = null
    /** The one live theme scene that is mounted (null while releasing). */
    var live: SceneTheme? = null
    var style = PassageStyle.ENTRANCE
    var intensity = 1f
    /** The UI should capture the stage now; [captureComposite]: capture what is on screen (an interrupt). */
    var capture = false
    var captureComposite = false
    /** The capture did not arrive: the UI shows the outgoing theme's hero still instead. */
    var snapshotMissing = false
    var heroAlpha = 0f
    var baseAlpha = 1f
    var snapshotAlpha = 0f
    var snapshotScale = 1f
    var snapshotDim = 0f
    var snapshotBlur = 0f
    var maskOn = false
    var maskAlpha = 1f
    var reveal = 1f
    /** The entrance's linear time fraction (opacity ramps use it; masks and transforms use [reveal]). */
    var revealTime = 1f
    /**
     * The live scene's own entrance accent ([io.vroomi.character.render.SceneOptions.intro]): the entrance's
     * linear time while the target theme is live and revealing, 1 otherwise (an interrupted or finished
     * passage drops it at once).
     */
    var intro = 1f
    /** Mix of the chrome colours (0 = the old theme, 1 = the new one). */
    var tintMix = 1f
    /** Entrance duration in ms (for the debug overlay). */
    var durationMs = 0f
    /** Fade out the old theme's sound now / start the new theme's sound now (one frame each). */
    var soundOut = false
    var soundIn = false
    /** Release the outgoing theme's cached layers now (one frame). */
    var release = false

    val snapshotVisible: Boolean get() = snapshotAlpha > 0.001f
    val running: Boolean get() = phase != PassagePhase.IDLE
}

/**
 * The theme passage state machine: Idle -> Capturing -> Releasing -> Prewarming -> Revealing -> Settling -> Idle.
 * Pure Kotlin (no Compose): the UI feeds it time, the capture and first-frame signals and the platform state,
 * and draws [out]. Exactly one live scene is mounted at any moment: the outgoing one is unmounted (and its
 * caches released) a frame before the incoming one is mounted, and a snapshot covers the gap. At most one
 * passage runs and at most one waits.
 */
class ThemePassage(
    initial: SceneTheme?,
    /** Mean relative luminance of a theme (slows crossfades and dissolves between far-apart themes). */
    private val luminance: (SceneTheme) -> Float = { 0.2f },
) {
    val out = PassageOutput()
    var reducedMotion = false

    private var moving = false
    private var safety = false

    private var req: PassageRequest? = null
    private var style = PassageStyle.ENTRANCE
    private var phase = PassagePhase.IDLE
    private var tPhase = 0f
    private var tTotal = 0f
    private var recedeT = 0f
    private var receding = false
    private var firstFrame = false
    private var captured = false
    private var composite = false
    private var finishing = false
    private var finishT = 0f
    private var finishFrom = 0f
    private var finishMask = 0f
    private var pendingMount = 0
    private var releaseTicks = 0
    private var revealMs = 0f
    private var sinceInterrupt = Float.MAX_VALUE
    private var waiting: PassageRequest? = null
    private var shown: SceneTheme? = initial

    val phaseNow: PassagePhase get() = phase
    val isIdle: Boolean get() = phase == PassagePhase.IDLE
    val pending: PassageRequest? get() = waiting
    /** The theme on screen when idle, or the theme the running passage is heading to. */
    val target: SceneTheme? get() = req?.target ?: shown
    val safetyOn: Boolean get() = safety
    val movingOn: Boolean get() = moving

    init {
        out.live = initial
        publishIdle()
    }

    /** Queue a passage: it starts now when idle, otherwise it waits (a newer request replaces the waiting one). */
    fun request(r: PassageRequest) {
        if (safety) { waiting = r; return }
        if (phase == PassagePhase.IDLE) start(r) else waiting = r
    }

    /**
     * The user asked for another theme while one is arriving: continue from what is on screen now. During the
     * reveal the half-revealed screen becomes the new outgoing snapshot; before it, the target simply changes.
     * Interrupts closer together than [MIN_INTERRUPT_GAP_MS] wait instead (no more than 3 swings a second).
     */
    fun interrupt(r: PassageRequest) {
        if (safety || phase == PassagePhase.IDLE) { request(r); return }
        if (sinceInterrupt < MIN_INTERRUPT_GAP_MS || finishing) { waiting = r; return }
        sinceInterrupt = 0f
        waiting = null
        when (phase) {
            PassagePhase.REVEALING, PassagePhase.SETTLING -> {
                // The screen now is a mix of two themes: capture it and keep every output frozen until it arrives.
                shown = req?.target
                begin(r, compositeCapture = true)
            }
            else -> {
                // Nothing of the incoming theme is visible yet: retarget, keep the snapshot and its recede.
                req = r
                style = effectiveStyle(r)
                out.to = r.target
                out.style = style
                out.intensity = r.intensity.coerceIn(0.2f, 1f)
                revealMs = revealDuration(r)
                out.durationMs = revealMs
                firstFrame = false
                if (phase == PassagePhase.PREWARMING && out.live != r.target) {
                    phase = PassagePhase.RELEASING; tPhase = 0f; releaseTicks = 0
                    out.live = null; out.release = true
                }
            }
        }
    }

    /** Drops the waiting request (the shown theme is already the one wanted). */
    fun cancelPending() { waiting = null }

    /** The UI captured the snapshot. */
    fun captured() { if (phase == PassagePhase.CAPTURING) captured = true }

    /** The incoming live scene drew its first complete frame. */
    fun firstFrameReady(theme: SceneTheme) { if (theme == req?.target && out.live == theme) firstFrame = true }

    fun safety(on: Boolean) {
        if (on == safety) return
        safety = on
        if (on) finish() else waiting?.let { w -> waiting = null; request(w) }
    }

    fun moving(on: Boolean) {
        if (on == moving) return
        moving = on
        if (on) finish()
    }

    /** Jump to the end with a [Motion.SAFETY_FINISH_MS] opacity fade (a safety warning, driving off). */
    private fun finish() {
        if (phase == PassagePhase.IDLE || finishing) return
        val r = req ?: return
        finishing = true
        finishT = 0f
        if (phase == PassagePhase.CAPTURING) {
            // No snapshot yet: the outgoing theme's hero still stands in and fades.
            out.snapshotMissing = !captured
            out.capture = false; out.captureComposite = false
            finishFrom = 1f
        } else finishFrom = out.snapshotAlpha
        finishMask = if (out.maskOn) out.maskAlpha else 0f
        if (out.live != r.target) { out.live = null; out.release = true; pendingMount = 2 }
        waiting = null
        publishFinishing()
    }

    private fun effectiveStyle(r: PassageRequest): PassageStyle = when {
        moving || reducedMotion -> PassageStyle.CROSSFADE
        else -> r.style
    }

    private fun start(r: PassageRequest) = begin(r, compositeCapture = false)

    private fun begin(r: PassageRequest, compositeCapture: Boolean) {
        val sameTheme = r.target == out.live && !compositeCapture
        // A card growing out of the live scene itself needs no passage: the scene simply grows.
        if (sameTheme && effectiveStyle(r) == PassageStyle.GROW && phase == PassagePhase.IDLE) return
        req = r
        style = effectiveStyle(r)
        if (!compositeCapture) publishTarget()
        tTotal = 0f; tPhase = 0f
        firstFrame = sameTheme
        captured = false
        composite = compositeCapture
        finishing = false
        pendingMount = 0
        releaseTicks = 0
        out.soundOut = !sameTheme
        out.soundIn = false
        out.release = false
        out.snapshotMissing = false
        out.maskAlpha = 1f
        if (!compositeCapture) { recedeT = 0f; receding = false }
        revealMs = revealDuration(r)
        when {
            sameTheme && r.snapshotHeld -> {
                // A return to the theme that is already live (a cancelled preview): just reveal it again.
                phase = PassagePhase.PREWARMING
                out.capture = false
                publish()
            }
            sameTheme -> {
                phase = PassagePhase.CAPTURING
                out.capture = true
                out.captureComposite = false
            }
            r.snapshotHeld || style == PassageStyle.GROW || style == PassageStyle.SWIPE -> {
                // The outgoing image is a still already on screen (or not needed): skip capturing.
                phase = PassagePhase.RELEASING
                out.capture = false
                out.live = null; out.release = true
                publish()
            }
            else -> {
                phase = PassagePhase.CAPTURING
                out.capture = true
                out.captureComposite = compositeCapture
            }
        }
        out.phase = phase
    }

    /** The new passage's target, style and duration become visible (deferred while a composite is captured). */
    private fun publishTarget() {
        val r = req ?: return
        out.from = shown
        out.to = r.target
        out.style = style
        out.intensity = r.intensity.coerceIn(0.2f, 1f)
        out.durationMs = revealMs
    }

    private fun revealDuration(r: PassageRequest): Float {
        val from = shown
        val gap = if (from != null) abs(luminance(from) - luminance(r.target)) else 0f
        val big = gap >= Motion.BIG_LUMA_STEP
        val i = r.intensity.coerceIn(0.5f, 1f)
        return when (effectiveStyle(r)) {
            PassageStyle.CROSSFADE -> when {
                big -> Motion.CALM_CROSSFADE_MS.toFloat()
                moving -> Motion.MOVING_MS.toFloat()
                reducedMotion -> Motion.REDUCED_MS.toFloat()
                else -> Motion.MOVING_MS.toFloat() * i
            }
            PassageStyle.ENTRANCE -> {
                val brighter = from != null && luminance(r.target) > luminance(from)
                val base = when {
                    r.target == SceneTheme.PIXEL && big -> Entrances.PIXEL_CALM_MS
                    // Into a much brighter world (a high-key studio, a drafting sheet): long enough that the rise never hurries.
                    big && brighter -> maxOf(Entrances.durationMs(r.target), Entrances.BRIGHT_ENTRANCE_MS)
                    else -> Entrances.durationMs(r.target)
                }
                // A quiet passage between far-apart themes keeps its full length (the flash rule wins).
                if (big) base else base * i
            }
            PassageStyle.GROW -> GROW_FADE_MS
            PassageStyle.SWIPE -> Motion.SWIPE_LIVE_MS.toFloat()
        }
    }

    /** Advance by [dtMs] (already divided by the debug slow factor and the system animator scale). */
    fun tick(dtMs: Float) {
        out.soundOut = false; out.soundIn = false; out.release = false
        sinceInterrupt += dtMs
        if (phase == PassagePhase.IDLE) return
        tTotal += dtMs; tPhase += dtMs
        if (receding) recedeT += dtMs
        if (finishing) {
            finishT += dtMs
            // A full frame with nothing live, then mount the target.
            if (pendingMount > 0) { pendingMount--; if (pendingMount == 0) out.live = req?.target }
            if (finishT >= Motion.SAFETY_FINISH_MS) settleNow() else publishFinishing()
            return
        }
        when (phase) {
            PassagePhase.CAPTURING -> {
                if (captured || tPhase >= CAPTURE_TIMEOUT_MS) {
                    if (composite) { revealMs = revealDuration(req!!); publishTarget() }
                    out.snapshotMissing = !captured
                    out.capture = false
                    out.captureComposite = false
                    if (composite) recedeT = 0f
                    receding = style == PassageStyle.ENTRANCE
                    if (req?.target == out.live) {
                        enter(PassagePhase.PREWARMING)
                    } else {
                        enter(PassagePhase.RELEASING)
                        releaseTicks = 0
                        out.live = null; out.release = true
                    }
                }
            }
            PassagePhase.RELEASING -> {
                // A full frame with nothing live (the old caches are released), then mount the incoming scene, hidden.
                if (releaseTicks++ >= 1) {
                    enter(PassagePhase.PREWARMING)
                    out.live = req?.target
                }
                receding = style == PassageStyle.ENTRANCE
            }
            PassagePhase.PREWARMING -> {
                val growReady = style != PassageStyle.GROW || tTotal >= GROW_FADE_START_MS
                if ((firstFrame && growReady) || tPhase >= PREWARM_MAX_MS) enter(PassagePhase.REVEALING)
            }
            PassagePhase.REVEALING -> if (tPhase >= revealMs) { enter(PassagePhase.SETTLING); out.soundIn = true }
            PassagePhase.SETTLING -> if (tPhase >= SETTLE_MS) { settleNow(); return }
            PassagePhase.IDLE -> Unit
        }
        if (phase != PassagePhase.CAPTURING) publish() else out.phase = phase
    }

    private fun enter(p: PassagePhase) { phase = p; tPhase = 0f }

    private fun settleNow() {
        val r = req
        if (r != null) { shown = r.target; if (out.live == null) out.live = r.target }
        if (finishing) out.soundIn = true
        phase = PassagePhase.IDLE
        finishing = false
        req = null
        publishIdle()
        waiting?.let { w -> waiting = null; request(w) }
    }

    private fun publishIdle() {
        out.phase = PassagePhase.IDLE
        out.from = shown; out.to = shown
        out.capture = false; out.captureComposite = false; out.snapshotMissing = false
        out.heroAlpha = 0f; out.baseAlpha = 1f
        out.snapshotAlpha = 0f; out.snapshotScale = 1f; out.snapshotDim = 0f; out.snapshotBlur = 0f
        out.maskOn = false; out.maskAlpha = 1f; out.reveal = 1f; out.revealTime = 1f; out.tintMix = 1f
        out.intro = 1f
    }

    private fun publishFinishing() {
        val k = (finishT / Motion.SAFETY_FINISH_MS).coerceIn(0f, 1f)
        out.phase = phase
        out.baseAlpha = 1f
        out.heroAlpha = 0f
        out.snapshotAlpha = finishFrom * (1f - k)
        out.maskAlpha = finishMask * (1f - k)
        out.maskOn = finishMask > 0f
        out.tintMix = out.tintMix + (1f - out.tintMix) * k
        out.intro = 1f
    }

    private fun publish() {
        out.phase = phase
        // While capturing, the outgoing scene stays exactly as it is on screen (an interrupted accent included).
        if (phase != PassagePhase.CAPTURING) out.intro = 1f
        val i = out.intensity
        val recede = if (receding) Motion.Smooth.progress(recedeT.toDouble()) else 0f
        val r = req ?: return
        when (style) {
            PassageStyle.ENTRANCE -> {
                val p = when (phase) {
                    PassagePhase.REVEALING -> Entrances.eased(r.target, tPhase / revealMs, revealMs)
                    PassagePhase.SETTLING -> 1f
                    else -> 0f
                }
                out.revealTime = when (phase) {
                    PassagePhase.REVEALING -> (tPhase / revealMs).coerceIn(0f, 1f)
                    PassagePhase.SETTLING -> 1f
                    else -> 0f
                }
                if (phase != PassagePhase.CAPTURING && out.live == r.target) out.intro = 1f - (1f - out.revealTime) * i
                out.heroAlpha = 0f
                out.baseAlpha = if (phase == PassagePhase.SETTLING) 1f else 0f
                out.snapshotAlpha = if (phase == PassagePhase.SETTLING) 0f else 1f
                out.snapshotScale = 1f - RECEDE_SCALE * i * recede
                out.snapshotDim = RECEDE_DIM * i * recede
                out.snapshotBlur = 0f
                out.maskOn = phase == PassagePhase.REVEALING
                out.maskAlpha = 1f
                out.reveal = p
                out.tintMix = p
            }
            PassageStyle.CROSSFADE -> {
                val curve = if (revealMs >= Motion.CALM_CROSSFADE_MS) Motion.CalmCurve else Motion.MovingCurve
                val f = when (phase) {
                    PassagePhase.REVEALING -> curve.ease(tPhase / revealMs)
                    PassagePhase.SETTLING -> 1f
                    else -> 0f
                }
                out.heroAlpha = 0f
                out.baseAlpha = if (phase == PassagePhase.REVEALING || phase == PassagePhase.SETTLING) 1f else 0f
                out.snapshotAlpha = 1f - f
                out.snapshotScale = 1f; out.snapshotDim = 0f; out.snapshotBlur = 0f
                out.maskOn = false; out.reveal = 1f
                out.tintMix = f
            }
            PassageStyle.GROW, PassageStyle.SWIPE -> {
                val f = when (phase) {
                    PassagePhase.REVEALING -> (tPhase / revealMs).coerceIn(0f, 1f)
                    PassagePhase.SETTLING -> 1f
                    else -> 0f
                }
                out.heroAlpha = if (phase == PassagePhase.SETTLING) 0f else 1f
                out.baseAlpha = f
                out.snapshotAlpha = 0f; out.snapshotScale = 1f; out.snapshotDim = 0f; out.snapshotBlur = 0f
                out.maskOn = false; out.reveal = 1f
                out.tintMix = if (style == PassageStyle.GROW) Motion.Passage.progress(tTotal.toDouble())
                    else (tTotal / Motion.SWIPE_LIVE_MS).coerceIn(0f, 1f)
            }
        }
    }

    companion object {
        const val CAPTURE_TIMEOUT_MS = 100f
        const val PREWARM_MAX_MS = 450f
        const val SETTLE_MS = 120f
        /** P3: the live scene crossfades in under the growing card between 40 % and 55 % of the Passage spring. */
        val GROW_FADE_START_MS = Motion.Passage.settleMs * 0.40f
        val GROW_FADE_MS = Motion.Passage.settleMs * 0.15f
        const val RECEDE_SCALE = 0.03f
        const val RECEDE_DIM = 0.25f
        const val MIN_INTERRUPT_GAP_MS = 340f
    }
}
