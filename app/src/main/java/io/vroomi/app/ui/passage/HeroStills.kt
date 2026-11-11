package io.vroomi.app.ui.passage

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.render.SceneStillRenderer
import io.vroomi.character.render.StillSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A small LRU of half-resolution theme stills (960 x 360, at most [CAPACITY], about 4.1 MB) for the gallery
 * cards, the growing card and the swipe. Each is drawn off the main thread by the same runtimes and painters
 * as the live scenes; PIXEL lands on exactly 2x (480 x 180 virtual pixels). Reading [version] in composition
 * recomposes when a still arrives.
 */
@Stable
class HeroStills(private val renderer: SceneStillRenderer, private val scope: CoroutineScope, private val synchronous: Boolean = false) {
    private class Entry(val key: String, val image: ImageBitmap)

    private val entries = ArrayList<Entry>(CAPACITY)
    private val inFlight = HashSet<String>()
    var spec: StillSpec = StillSpec()
    /** The upgrade step each theme shows (its still is drawn at that step). */
    var modelOf: (SceneTheme) -> Int = { spec.model }
    var version by mutableIntStateOf(0)
        private set

    private fun key(theme: SceneTheme): String = "${theme.name}|${spec.skin}|${spec.finish}|${spec.powertrain}|${modelOf(theme)}"

    /** The still if it is ready (and marks it recently used); otherwise starts drawing it and returns null. */
    fun get(theme: SceneTheme): ImageBitmap? {
        val k = key(theme)
        val i = entries.indexOfFirst { it.key == k }
        if (i >= 0) {
            val e = entries.removeAt(i); entries.add(e)
            return e.image
        }
        request(theme)
        return null
    }

    /** Only reads (no LRU change, no request): for drawing code. */
    fun peek(theme: SceneTheme): ImageBitmap? = entries.lastOrNull { it.key == key(theme) }?.image

    @OptIn(ExperimentalCoroutinesApi::class)
    fun request(theme: SceneTheme) {
        val k = key(theme)
        if (entries.any { it.key == k } || !inFlight.add(k)) return
        val s = spec.copy(model = modelOf(theme))
        if (synchronous) {
            put(k, renderer.render(theme, W, H, s))
            inFlight.remove(k)
            return
        }
        scope.launch {
            val img = runCatching { withContext(renderThread) { renderer.render(theme, W, H, s) } }.getOrNull()
            inFlight.remove(k)
            if (img != null) put(k, img)
        }
    }

    /** Replace a theme's still with a fresher image (for example the moment a previewed stage closed). */
    fun put(theme: SceneTheme, image: ImageBitmap) = put(key(theme), image)

    private fun put(k: String, image: ImageBitmap) {
        entries.removeAll { it.key == k }
        entries.add(Entry(k, image))
        while (entries.size > CAPACITY) entries.removeAt(0)
        version++
    }

    fun clear() { entries.clear(); version++ }

    companion object {
        const val CAPACITY = 3
        const val W = 960
        const val H = 360
        /** One background thread: a renderer is used by one thread at a time. */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val renderThread = Dispatchers.Default.limitedParallelism(1)
    }
}
