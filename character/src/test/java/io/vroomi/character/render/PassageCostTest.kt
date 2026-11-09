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

    private fun live(theme: SceneTheme, tm: TextMeasurer, density: Density): Live = when (theme) {
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
        for ((a, b) in listOf(SceneTheme.CALIBRE to SceneTheme.ENGINE, SceneTheme.AQUARIUM to SceneTheme.MARBLE, SceneTheme.ORBIT to SceneTheme.PIXEL)) {
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
}
