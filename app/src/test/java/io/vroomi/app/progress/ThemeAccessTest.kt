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
}
