package io.vroomi.character.hangar

import io.vroomi.character.theme.ThemeSim

/**
 * HANGAR, a lit giant-robot hangar where the user's own robot is assembled by distance driven (spec 5.12). This is
 * the skeleton: it keeps only the shared kit readings and one chip. The assembly progress, its persistence, the demo
 * rule and the robot's reactions come with the theme's own task.
 */
class HangarSim : ThemeSim() {

    override val allChips: List<String> = listOf(CHIP_BUILD)

    override fun advance(dt: Double, ddt: Double, nowMs: Long) {}

    override fun chooseChip(): String? = if (moving && kmh > 3f) CHIP_BUILD else null

    companion object {
        const val CHIP_BUILD = "조립 중"
    }
}
