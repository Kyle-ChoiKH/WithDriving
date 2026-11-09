package io.vroomi.character.plasma

import io.vroomi.character.theme.ThemeSim

/**
 * PLASMA, a high-voltage plasma globe on a dark lab bench (spec 5.11). This is the skeleton: it keeps only the shared
 * kit readings (speed, blinkers, brake, safety freeze) and one chip. The filaments, the blinker gather, the counter
 * tubes, the valves, the scope and the ladder come with the theme's own task.
 */
class PlasmaSim : ThemeSim() {

    override val allChips: List<String> = listOf(CHIP_FLOW)

    override fun advance(dt: Double, ddt: Double, nowMs: Long) {}

    override fun chooseChip(): String? = if (moving && kmh > 3f) CHIP_FLOW else null

    companion object {
        const val CHIP_FLOW = "전류가 일렁여요"
    }
}
