package io.vroomi.app.ui

import io.vroomi.app.ui.passage.Entrances
import io.vroomi.app.ui.passage.PassageOutput
import io.vroomi.app.ui.passage.PassageRequest
import io.vroomi.app.ui.passage.ThemePassage
import io.vroomi.character.calibre.SceneTheme
import org.json.JSONObject
import java.io.File

/**
 * Drives a [ThemePassage] the way the UI does (captures one frame after asked, the incoming scene draws its
 * first frame one frame after it is mounted) and computes the screen's mean relative luminance from the
 * passage's output with the same entrance model the drawing code uses.
 */
internal class PassageSim(val lum: (SceneTheme) -> Float, initial: SceneTheme, private val backdrop: Float = 0.01f) {
    val p = ThemePassage(initial, lum)
    var snapLuma = lum(initial)
    var screen = lum(initial)
    val trace = ArrayList<Float>()
    val lives = ArrayList<SceneTheme?>()
    val phases = ArrayList<io.vroomi.app.ui.passage.PassagePhase>()
    private var mountedFrames = 0
    private var askedToCapture = false
    private var lastLive: SceneTheme? = initial
    var dt = 1000f / 60f

    fun step() {
        val o = p.out
        // The UI captures one frame after it is asked (GraphicsLayer.toImageBitmap).
        if (o.capture && askedToCapture) {
            snapLuma = if (o.captureComposite) screen else (o.live?.let(lum) ?: screen)
            p.captured()
            askedToCapture = false
        } else if (o.capture) askedToCapture = true
        if (o.live != lastLive) { mountedFrames = 0; lastLive = o.live } else mountedFrames++
        o.live?.let { if (mountedFrames >= 1) p.firstFrameReady(it) }
        p.tick(dt)
        if (o.snapshotMissing) o.from?.let { snapLuma = lum(it) }
        screen = luma(o)
        trace += screen
        lives += o.live
        phases += o.phase
    }

    fun run(ms: Float) { repeat((ms / dt).toInt()) { step() } }

    fun runUntilIdle(maxMs: Float = 4000f) {
        var t = 0f
        while (t < maxMs) { step(); t += dt; if (p.isIdle && p.pending == null) break }
    }

    fun request(r: PassageRequest) { p.request(r) }

    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

    fun luma(o: PassageOutput): Float {
        var l = backdrop
        o.to?.let { l = mix(l, lum(it), o.heroAlpha) }
        val live = o.live?.let(lum)
        if (live != null) l = mix(l, live, o.baseAlpha)
        if (o.snapshotAlpha > 0f) {
            val s2 = o.snapshotScale * o.snapshotScale
            val ls = s2 * snapLuma * (1f - o.snapshotDim) + (1f - s2) * backdrop
            l = mix(l, ls, o.snapshotAlpha)
        }
        val t = o.to
        if (o.maskOn && live != null && t != null) {
            val c = Entrances.coverage(t, o.reveal, o.intensity)
            val a = Entrances.inAlpha(t, o.revealTime) * o.maskAlpha
            val d = Entrances.inDim(t, o.reveal, o.intensity)
            l = mix(l, live * (1f - d), c * a)
            l *= 1f - Entrances.globalDim(t, o.reveal, o.revealTime, o.intensity) * o.maskAlpha
            l = mix(l, Entrances.MIST_LUMA, Entrances.mist(t, o.reveal, o.revealTime, o.intensity) * o.maskAlpha)
        }
        return l
    }

    companion object {
        /** The measured luminance of every theme (written by ThemeLuminanceSnapshotTest). */
        fun measured(): Map<SceneTheme, Float> {
            val f = File("src/test/resources/theme_luminance.json")
            require(f.exists()) { "theme_luminance.json is missing: record it with VROOMI_LUMA=1 (ThemeLuminanceSnapshotTest)" }
            val o = JSONObject(f.readText())
            return SceneTheme.entries.filter { o.has(it.name) }.associateWith { o.getDouble(it.name).toFloat() }
        }
    }
}
