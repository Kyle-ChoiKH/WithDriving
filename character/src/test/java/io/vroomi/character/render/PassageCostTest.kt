package io.vroomi.character.render

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.vroomi.character.CharacterTarget
import io.vroomi.character.SizeMode
import io.vroomi.character.calibre.CalibreFinish
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.engine.EnginePowertrain
import io.vroomi.character.render.calibre.CalibreRuntime
import io.vroomi.character.render.calibre.CalibreText
import io.vroomi.character.render.calibre.DEFAULT_NOW_MS
import io.vroomi.character.render.calibre.draw
import io.vroomi.character.render.engine.EngineRuntime
import io.vroomi.character.render.engine.draw
import io.vroomi.character.render.theme.KitShots
import io.vroomi.character.render.theme.LayerCache
import io.vroomi.character.render.theme.newThemeRuntime
import io.vroomi.character.render.theme.releaseThemeLayers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.ZoneId

/**
 * The cost of the three heaviest theme passages at 1920 x 720: the outgoing theme's cached layers, the snapshot,
 * the release, and the incoming theme's first frame (its layers built). Writes build/reports/passage_cost.txt.
 * Times are this PC's JVM (layoutlib), not the car's GPU.
 */
class PassageCostTest {
    @get:Rule val paparazzi = KitShots.wide()

    private val w = 1920; private val h = 720
    private val target = CharacterTarget(sizeMode = SizeMode.FULL)
    private val options = SceneOptions(hourOfDay = 14f)

    /** Bytes of every ImageBitmap reachable from [o] (two levels: fields, then maps, lists and their elements' fields). */
    private fun bitmapBytes(o: Any?, depth: Int = 0, seen: MutableSet<Any> = HashSet()): Long {
        if (o == null || depth > 3 || !seen.add(o)) return 0
        if (o is ImageBitmap) return o.width.toLong() * o.height * 4
        if (o is Map<*, *>) return o.values.sumOf { bitmapBytes(it, depth + 1, seen) }
        if (o is Iterable<*>) return o.sumOf { bitmapBytes(it, depth + 1, seen) }
        if (o.javaClass.name.startsWith("java.") || o.javaClass.name.startsWith("kotlin.")) return 0
        var sum = 0L
        var c: Class<*>? = o.javaClass
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
                runCatching { f.isAccessible = true; sum += bitmapBytes(f.get(o), depth + 1, seen) }
            }
            c = c.superclass
        }
        return sum
    }

    /** [release] drops the theme's caches and returns what is still held (the app also drops a kit theme's runtime). */
    private class Live(val theme: SceneTheme, val draw: (CanvasDrawScope, ImageBitmap) -> Unit, val caches: () -> Long, val release: () -> Long)

    private fun live(theme: SceneTheme, tm: TextMeasurer, density: Density, options: SceneOptions = this.options, kartDriving: Boolean = true): Live = when (theme) {
        SceneTheme.KART -> {
            // The kart scene keeps no cached layers: it is drawn whole every frame.
            val r = SceneRuntime(seed = 3)
            val driving = CharacterTarget(sizeMode = SizeMode.FULL, kart = io.vroomi.character.KartState(driving = kartDriving))
            for (i in 0 until 90) r.step(1f / 60f, driving, options, DEFAULT_NOW_MS + i * 16L)
            val cache = TextCache()
            Live(theme, { s, img -> s.draw(density, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) { r.draw(this, driving, options, tm, cache) } }, { 0L }, { 0L })
        }
        SceneTheme.CALIBRE -> {
            val r = CalibreRuntime()
            for (i in 0 until 90) r.step(1f / 60f, target, options, CalibreFinish.AUTO, DEFAULT_NOW_MS + i * 16L)
            val text = CalibreText(tm, density)
            Live(theme, { s, img -> s.draw(density, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) { r.draw(this, tm, text, DEFAULT_NOW_MS, ZoneId.of("Asia/Seoul")) } },
                { bitmapBytes(r) }, { r.releaseLayers(); bitmapBytes(r) })
        }
        SceneTheme.ENGINE -> {
            val r = EngineRuntime()
            for (i in 0 until 90) r.step(1f / 60f, target, options, EnginePowertrain.AUTO, null, DEFAULT_NOW_MS + i * 16L)
            Live(theme, { s, img -> s.draw(density, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) { r.draw(this, tm) } },
                { bitmapBytes(r) }, { r.releaseLayers(); bitmapBytes(r) })
        }
        else -> {
            val r = requireNotNull(newThemeRuntime(theme))
            for (i in 0 until 90) r.step(1f / 60f, target, options, DEFAULT_NOW_MS + i * 16L)
            Live(theme, { s, img -> s.draw(density, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) { r.draw(this, tm) } },
                { LayerCache.bytesInUse() + bitmapBytes(r.painter) }, { releaseThemeLayers(null); LayerCache.bytesInUse() })
        }
    }

    @Test fun heaviestPairs() {
        val density = Density(1f)
        val tm = TextMeasurer(createFontFamilyResolver(paparazzi.context), density, LayoutDirection.Ltr)
        val out = StringBuilder("passage cost at 1920 x 720 (JVM layoutlib on the build PC, not the car)\n")
        val snapshot = w.toLong() * h * 4
        val stills = 3L * 960 * 360 * 4
        for ((a, b) in listOf(SceneTheme.CALIBRE to SceneTheme.ENGINE, SceneTheme.AQUARIUM to SceneTheme.HANGAR, SceneTheme.ORBIT to SceneTheme.PIXEL)) {
            releaseThemeLayers(null)
            val img = ImageBitmap(w, h)
            val s = CanvasDrawScope()
            val outgoing = live(a, tm, density)
            outgoing.draw(s, img)                      // warm: layers built
            val t0 = System.nanoTime(); outgoing.draw(s, img); val steady = (System.nanoTime() - t0) / 1e6
            val outBytes = outgoing.caches()
            val afterRelease = outgoing.release()
            val incoming = live(b, tm, density)
            val t1 = System.nanoTime(); incoming.draw(s, img); val first = (System.nanoTime() - t1) / 1e6
            val inBytes = incoming.caches()
            val peak = maxOf(outBytes + snapshot, snapshot + inBytes) + stills
            out.append("%s -> %s: outgoing layers %.1f MB (after release %.1f MB), incoming layers %.1f MB, snapshot %.1f MB, hero stills %.1f MB, peak %.1f MB; outgoing frame %.1f ms, incoming first frame %.1f ms\n".format(
                a, b, outBytes / 1e6, afterRelease / 1e6, inBytes / 1e6, snapshot / 1e6, stills / 1e6, peak / 1e6, steady, first))
            assertTrue("$a released its layers", afterRelease == 0L)
            incoming.release()
        }
        File("build/reports").mkdirs()
        File("build/reports/passage_cost.txt").writeText(out.toString())
        println(out)
    }

    /**
     * Step 5 (model 4) of all twelve themes: the cached layers stay within [LAYER_LIMIT] (the 48 MB layer budget), every layer is released, and a steady frame's time is written to
     * build/reports/passage_cost_step5.txt (times are this PC's JVM, so they are recorded, not asserted).
     */
    @Test fun stepFiveOfEveryTheme() {
        val density = Density(1f)
        val tm = TextMeasurer(createFontFamilyResolver(paparazzi.context), density, LayoutDirection.Ltr)
        val five = options.copy(model = 4)
        val out = StringBuilder("step 5 cost at 1920 x 720 (JVM layoutlib on the build PC, not the car)\n")
        for (theme in SceneTheme.entries) {
            releaseThemeLayers(null)
            val img = ImageBitmap(w, h)
            val s = CanvasDrawScope()
            val l = live(theme, tm, density, five)
            l.draw(s, img)                              // warm: layers built
            val t0 = System.nanoTime(); l.draw(s, img); val steady = (System.nanoTime() - t0) / 1e6
            val bytes = l.caches()
            val after = l.release()
            out.append("%-13s layers %5.1f MB, steady frame %6.1f ms\n".format(theme, bytes / 1e6, steady))
            assertTrue("$theme released its layers", after == 0L)
            assertTrue("$theme step 5 layers ${bytes / 1e6} MB", bytes <= LAYER_LIMIT)
        }
        File("build/reports").mkdirs()
        File("build/reports/passage_cost_step5.txt").writeText(out.toString())
        println(out)
    }

    /**
     * A chosen step has to show at once: on a fresh install with the car parked (what the step picker's stage shows),
     * every step of every theme differs from the one before it over a visible part of the picture. Writes the
     * measured shares to build/reports/step_visibility.txt.
     */
    @Test fun everyStepShowsWhenParked() {
        val density = Density(1f)
        val tm = TextMeasurer(createFontFamilyResolver(paparazzi.context), density, LayoutDirection.Ltr)
        val out = StringBuilder("share of the picture that changes from one step to the next (parked, fresh install, 1920 x 720)\n")
        val weak = ArrayList<String>()
        for (theme in SceneTheme.entries) {
            var before: IntArray? = null
            val shares = ArrayList<Float>()
            for (model in 0 until 5) {
                releaseThemeLayers(null)
                val img = ImageBitmap(w, h)
                val l = live(theme, tm, density, options.copy(model = model), kartDriving = false)
                l.draw(CanvasDrawScope(), img)
                val px = IntArray(w * h)
                img.readPixels(px)
                l.release()
                before?.let { b ->
                    var n = 0
                    for (i in px.indices) {
                        val p = px[i]; val q = b[i]
                        if (p != q && (kotlin.math.abs((p shr 16 and 255) - (q shr 16 and 255)) > 24 || kotlin.math.abs((p shr 8 and 255) - (q shr 8 and 255)) > 24 || kotlin.math.abs((p and 255) - (q and 255)) > 24)) n++
                    }
                    shares += n.toFloat() / px.size
                }
                before = px
            }
            out.append("%-13s %s\n".format(theme, shares.joinToString("  ") { "%5.2f%%".format(it * 100f) }))
            shares.forEachIndexed { i, s -> if (s < MIN_STEP_SHARE) weak += "$theme Lv.${i + 1} -> Lv.${i + 2}: ${"%.2f".format(s * 100f)}%" }
        }
        File("build/reports").mkdirs()
        File("build/reports/step_visibility.txt").writeText(out.toString())
        println(out)
        assertTrue("steps that hardly show: $weak", weak.isEmpty())
    }

    private companion object {
        /** The least share of the picture a step must change (a small sprite alone is about 0.3 %). */
        const val MIN_STEP_SHARE = 0.01f

        /** The shared budget of the static layer bitmaps (48 MB; the app holds one theme's layers at a time). */
        const val LAYER_LIMIT = 48L * 1024 * 1024
    }
}
