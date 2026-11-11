package io.vroomi.app.progress

import io.vroomi.character.theme.ThemeModels
import org.junit.Assert.assertEquals
import org.junit.Test

/** Grade n opens the steps 1..n of every theme (spec 4.2); review focus 4. */
class ThemeAccessTest {
    @Test fun stepEqualsGrade() { assertEquals(listOf(0, 1, 2, 3, 4), Grade.entries.map { ThemeAccess.maxModel(it) }) }
    @Test fun unlockGradeOfStep() { assertEquals(Grade.PLATINUM, ThemeAccess.modelUnlockGrade(3)) }
    @Test fun storedModelClampsToUnlocked() { assertEquals(0, ThemeAccess.effectiveModel(Grade.BRONZE, choice = 4)) }
    @Test fun noChoiceUsesBestUnlocked() { assertEquals(3, ThemeAccess.effectiveModel(Grade.PLATINUM, choice = null)) }
    @Test fun lowerChoiceIsKept() { assertEquals(1, ThemeAccess.effectiveModel(Grade.DIAMOND, choice = 1)) }
    @Test fun stepsMatchTheThemeModels() { assertEquals(ThemeModels.COUNT, ThemeAccess.STEPS) }

    // Colourways open with the grade like the steps, but a new one is never put on by itself.
    @Test fun noColourChoiceKeepsTheThemesOwnColours() { assertEquals(0, ThemeAccess.effectiveColor(Grade.DIAMOND, choice = null)) }
    @Test fun anOpenColourIsKept() { assertEquals(2, ThemeAccess.effectiveColor(Grade.GOLD, choice = 2)) }
    @Test fun aLockedColourFallsBackToTheThemesOwn() { assertEquals(0, ThemeAccess.effectiveColor(Grade.SILVER, choice = 3)) }
}
