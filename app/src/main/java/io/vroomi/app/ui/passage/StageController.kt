package io.vroomi.app.ui.passage

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.MotionDurationScale
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import io.vroomi.app.ui.motion.Motion
import io.vroomi.app.ui.motion.MotionPrefs
import io.vroomi.character.calibre.SceneTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/** How the next change of the shown theme should travel (set by whoever changes it, consumed once). */
class PassageHint(
    val style: PassageStyle = PassageStyle.ENTRANCE,
    val intensity: Float = 1f,
    /** true: the user is driving it (arrows) and it may interrupt a running passage. */
    val interrupt: Boolean = false,
    val snapshotHeld: Boolean = false,
)

/** Which coordinate space a snapshot was captured in. */
enum class SnapSpace { SCENE, CONTAINER }

/**
 * The Compose side of [ThemePassage]: runs it on the frame clock, publishes its output as state (read in draw
 * lambdas), captures snapshots from the stage's graphics layers, and performs the side effects (release the
 * outgoing theme's caches, fade theme sound). Also holds the snapshot that covers a change of the scene's
 * layout size while its bounds animate.
 */
@Stable
class StageController(initial: SceneTheme, private val scope: CoroutineScope, luminance: (SceneTheme) -> Float) {
    val passage = ThemePassage(initial, luminance)
    private val o = passage.out

    var live by mutableStateOf<SceneTheme?>(initial); private set
    var from by mutableStateOf<SceneTheme?>(initial); private set
    var to by mutableStateOf<SceneTheme?>(initial); private set
    var phase by mutableStateOf(PassagePhase.IDLE); private set
    var style by mutableStateOf(PassageStyle.ENTRANCE); private set
    var maskOn by mutableStateOf(false); private set
    var snapshot by mutableStateOf<ImageBitmap?>(null); private set
    var snapshotSpace by mutableStateOf(SnapSpace.SCENE); private set
    /** The capture failed: draw this theme's hero still as the outgoing image. */
    var snapshotFallback by mutableStateOf<SceneTheme?>(null); private set

    val heroAlpha = mutableFloatStateOf(0f)
    val baseAlpha = mutableFloatStateOf(1f)
    val snapshotAlpha = mutableFloatStateOf(0f)
    val snapshotScale = mutableFloatStateOf(1f)
    val snapshotDim = mutableFloatStateOf(0f)
    val reveal = mutableFloatStateOf(1f)
    val revealTime = mutableFloatStateOf(1f)
    val maskAlpha = mutableFloatStateOf(1f)
    val tintMix = mutableFloatStateOf(1f)
    /** The live scene's entrance accent (SceneOptions.intro). */
    val intro = mutableFloatStateOf(1f)
    var intensity = 1f; private set

    /** Swipe: the outgoing frozen image, the drag offset in px and the neighbour (P5). */
    var swipeSnap by mutableStateOf<ImageBitmap?>(null)
    var swipeNeighbour by mutableStateOf<SceneTheme?>(null)
    val swipeOffset = Animatable(0f)

    /** Bounds change: a snapshot of the old frame covers the scene while its layout size changes. */
    var boundsSnap by mutableStateOf<ImageBitmap?>(null); private set
    val boundsAlpha = Animatable(0f)
    /**
     * True for a change inside the live scene ([crossfade]): the new frame stays fully opaque under the fading
     * cover. A layout change ([coverForBounds]) fades the live scene in under it instead, because its first frames
     * at the new size are not settled yet.
     */
    var coverKeepsLive by mutableStateOf(false); private set

    /** Set by the stage while drawing. */
    var sceneLayer: GraphicsLayer? = null
    var stageLayer: GraphicsLayer? = null

    /** Side effects (set by AppRoot). */
    var onRelease: (keep: SceneTheme?) -> Unit = {}
    var onSoundOut: () -> Unit = {}
    var onSoundIn: () -> Unit = {}

    var hint: PassageHint? = null
    private var capturing: Job? = null
    /** Frame times of the passage drawing (debug overlay). */
    val frameMs = mutableFloatStateOf(0f)

    /** Show [theme] (the shown theme changed): a passage with the pending hint, or none if it is already shown. */
    fun show(theme: SceneTheme) {
        val h = hint ?: PassageHint()
        hint = null
        if (passage.target == theme) { passage.cancelPending(); return }
        val r = PassageRequest(theme, h.style, h.intensity, h.snapshotHeld)
        if (h.interrupt) passage.interrupt(r) else passage.request(r)
        sync()
    }

    fun firstFrame(theme: SceneTheme) = passage.firstFrameReady(theme)

    fun safety(on: Boolean) { passage.safety(on); sync() }
    fun moving(on: Boolean) { passage.moving(on); sync() }

    /** Runs the passage on the frame clock for as long as the composition lives. */
    suspend fun run(prefs: () -> MotionPrefs) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 16.7f else ((now - last) / 1_000_000f).coerceIn(0f, 64f)
                last = now
                val p = prefs()
                passage.reducedMotion = p.reduced
                if (!passage.isIdle) {
                    val t0 = System.nanoTime()
                    passage.tick(dt / p.slow / systemScale)
                    sync()
                    frameMs.floatValue = frameMs.floatValue * 0.8f + (System.nanoTime() - t0) / 1e6f * 0.2f
                }
            }
            systemScale = (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f).let { if (it <= 0f) 1f else it }
        }
    }

    private var systemScale = 1f

    private fun sync() {
        if (o.release) onRelease(o.to)
        if (o.soundOut) onSoundOut()
        if (o.soundIn) onSoundIn()
        if (o.capture && capturing == null) capture(o.captureComposite)
        if (live != o.live) live = o.live
        if (from != o.from) from = o.from
        if (to != o.to) to = o.to
        if (phase != o.phase) phase = o.phase
        if (style != o.style) style = o.style
        if (maskOn != o.maskOn) maskOn = o.maskOn
        intensity = o.intensity
        heroAlpha.floatValue = o.heroAlpha
        baseAlpha.floatValue = o.baseAlpha
        snapshotAlpha.floatValue = o.snapshotAlpha
        snapshotScale.floatValue = o.snapshotScale
        snapshotDim.floatValue = o.snapshotDim
        reveal.floatValue = o.reveal
        revealTime.floatValue = o.revealTime
        maskAlpha.floatValue = o.maskAlpha
        tintMix.floatValue = o.tintMix
        intro.floatValue = o.intro
        val fb = if (o.snapshotMissing && o.snapshotVisible) o.from else null
        if (snapshotFallback != fb) snapshotFallback = fb
        if (o.phase == PassagePhase.IDLE && snapshot != null && !o.capture) snapshot = null
    }

    private fun capture(composite: Boolean) {
        val layer = if (composite) stageLayer else sceneLayer
        if (layer == null) return
        capturing = scope.launch {
            val img = runCatching { layer.toImageBitmap() }.getOrNull()
            if (img != null) {
                snapshot = img
                snapshotSpace = if (composite) SnapSpace.CONTAINER else SnapSpace.SCENE
                passage.captured()
            }
            capturing = null
        }
    }

    /** Use [img] as the outgoing image of the next passage requested with snapshotHeld (a card's still). */
    fun holdSnapshot(img: ImageBitmap?, space: SnapSpace = SnapSpace.SCENE) { snapshot = img; snapshotSpace = space }

    /**
     * Previews and snapshot tests: run the passage for [ms] without a frame clock. A capture is answered at once
     * with [outgoing]; the incoming scene reports its first frame one frame after it is mounted.
     */
    fun advanceForTest(ms: Float, outgoing: ImageBitmap?, until: (PassageOutput) -> Boolean = { false }) {
        var t = 0f
        val dt = 1000f / 60f
        var mounted: SceneTheme? = null
        while (t < ms && !until(o)) {
            if (o.capture) { snapshot = outgoing; snapshotSpace = SnapSpace.SCENE; passage.captured() }
            o.live?.let { if (it == mounted) passage.firstFrameReady(it) }
            mounted = o.live
            passage.tick(dt)
            sync()
            t += dt
        }
        if (o.snapshotVisible && snapshot == null) snapshot = outgoing
    }

    /** Previews and snapshot tests: the cover of a change of layout size, at [alpha]. */
    fun coverForTest(img: ImageBitmap?, alpha: Float, keepsLive: Boolean = false) {
        boundsSnap = img
        coverKeepsLive = keepsLive
        scope.launch { boundsAlpha.snapTo(alpha) }
    }

    /** Captures the live scene as it is now (a swipe's frozen world, a closing preview). */
    suspend fun captureScene(): ImageBitmap? = runCatching { sceneLayer?.toImageBitmap() }.getOrNull()

    /**
     * The scene's layout size is about to change while its bounds animate: cover it with the current frame,
     * then [change] (switch the layout and start the bounds animation). [uncover] fades the cover away once the
     * bounds settled (Motion.SWAP_MS).
     */
    fun coverForBounds(change: () -> Unit) {
        scope.launch {
            val img = captureScene()
            if (img != null) { boundsSnap = img; coverKeepsLive = false; boundsAlpha.snapTo(1f) }
            change()
        }
    }

    /** A change inside the live scene (another friend): the old frame covers it, then fades away. */
    fun crossfade(prefs: MotionPrefs, change: () -> Unit) {
        scope.launch {
            val img = captureScene()
            if (img != null && !prefs.moving) { boundsSnap = img; coverKeepsLive = true; boundsAlpha.snapTo(1f) }
            change()
            withFrameNanos { }; withFrameNanos { }
            if (boundsSnap != null) {
                boundsAlpha.animateTo(0f, prefs.fade(Motion.FADE_IN_MS))
                boundsSnap = null
            }
        }
    }

    fun uncover(prefs: MotionPrefs) {
        if (boundsSnap == null) return
        scope.launch {
            boundsAlpha.animateTo(0f, prefs.fade(Motion.SWAP_MS))
            boundsSnap = null
        }
    }
}
