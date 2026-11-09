package io.vroomi.character.pixel

import io.vroomi.character.Biome

/**
 * The world generator: the world is made of 32-pixel chunks, each generated deterministically from
 * (tripSeq, chunk index, layer) and the biome that was current when the chunk was first generated.
 * A biome change therefore applies only to chunks generated after the change: the new tile set scrolls in
 * from the right edge, no screen transition needed.
 */
class PixelWorld(seed: Int = 1) {
    var seed = seed; private set
    var biome = Biome.CITY
    private val memo = HashMap<Long, Int>()

    fun reset(seed: Int, biome: Biome) { this.seed = seed; this.biome = biome; memo.clear() }

    private fun key(layer: Int, chunk: Int): Long = (layer.toLong() shl 40) or (chunk.toLong() and 0xffffffffL)

    /** The biome of a chunk: fixed when the chunk is first generated. */
    fun biomeOf(layer: Int, chunk: Int): Biome {
        val k = key(layer, chunk)
        memo[k]?.let { return Biome.entries[it] }
        memo[k] = biome.ordinal
        return biome
    }

    /** Fixes the biome of every chunk visible on [layer] for a camera at [cameraPx] (layer-scaled). */
    fun generateVisible(layer: Int, cameraPx: Float) {
        val first = Math.floorDiv(cameraPx.toInt(), PixelModels.CHUNK) - 1
        val last = first + PixelModels.VW / PixelModels.CHUNK + 3
        for (c in first..last) biomeOf(layer, c)
        if (memo.size > MAX_MEMO) {
            // Forget chunks far behind or far ahead of this layer's view (never the visible ones).
            memo.keys.removeAll { k -> (k ushr 40).toInt() == layer && (k.toInt() < first - 64 || k.toInt() > last + 64) }
        }
    }

    fun thing(layer: Int, chunk: Int): PixelThing {
        val b = biomeOf(layer, chunk)
        val arr = if (layer >= 3) PixelBiomes.near(b) else PixelBiomes.skyline(b)
        return arr[(PixelModels.hash(seed, chunk, layer) * arr.size).toInt().coerceIn(0, arr.size - 1)]
    }

    fun variant(layer: Int, chunk: Int): Int = (PixelModels.hash(seed, chunk, layer + 7) * 4).toInt()

    /**
     * Height (px) of the far silhouette at a column, per tileset (deterministic): flat-topped stepped mesas in the
     * canyon, a low flat horizon on the desert highway, a saw-tooth ridge of pines, needle spires in the crystal
     * caves and round domes over the arcade town.
     */
    fun ridge(layer: Int, x: Int): Int {
        val chunk = Math.floorDiv(x, PixelModels.CHUNK)
        val b = biomeOf(layer, chunk)
        val u = Math.floorMod(x, PixelModels.CHUNK)
        return when (b) {
            Biome.MOUNTAIN -> {
                // A mesa is two chunks wide; its sides step down three rows per three columns.
                val block = Math.floorDiv(chunk, 2)
                val hi = PixelModels.hash(seed, block, 31) > 0.35f
                val top = if (hi) 34 + (PixelModels.hash(seed, block, 32) * 16).toInt() else 12
                val inBlock = Math.floorMod(x, PixelModels.CHUNK * 2)
                val edge = minOf(inBlock, PixelModels.CHUNK * 2 - 1 - inBlock)
                if (!hi) top else minOf(top, 12 + (edge / 3) * 4)
            }
            Biome.FIELD -> 8 + if (PixelModels.hash(seed, chunk, 33) > 0.75f) (if (u in 8..23) 12 else if (u in 5..26) 6 else 0) else 0
            Biome.PARK -> {
                val tooth = u % 16
                val peak = 18 + (PixelModels.hash(seed, chunk * 2 + u / 16, 34) * 18).toInt()
                peak - minOf(tooth, 16 - tooth) * 2
            }
            Biome.SEASIDE -> {
                val spire = u % 8
                val peak = 10 + (PixelModels.hash(seed, chunk * 4 + u / 8, 35) * 34).toInt()
                maxOf(6, peak - minOf(spire, 8 - spire) * (peak / 4))
            }
            Biome.CITY -> {
                val r = 10 + (PixelModels.hash(seed, chunk, 36) * 6).toInt()
                val d = u - 16
                val dome = if (d * d < r * r) kotlin.math.sqrt((r * r - d * d).toFloat()).toInt() else 0
                12 + dome + (if (PixelModels.hash(seed, chunk, 37) > 0.6f && u == 24) 14 else 0)
            }
        }
    }

    companion object { const val MAX_MEMO = 2048 }
}
