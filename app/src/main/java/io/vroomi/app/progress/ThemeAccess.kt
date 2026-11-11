package io.vroomi.app.progress

import io.vroomi.character.theme.ThemeModels

/**
 * Which upgrade steps a grade opens (spec 4.2): every theme is open from the start, and grade n opens the steps
 * 1..n of every theme ([io.vroomi.character.render.SceneOptions.model] 0..n-1).
 */
object ThemeAccess {
    const val STEPS = ThemeModels.COUNT

    /** The highest step (model) [grade] opens. */
    fun maxModel(grade: Grade): Int = grade.level - 1

    /** The grade that opens step [model]. */
    fun modelUnlockGrade(model: Int): Grade = Grade.of(model + 1)

    /**
     * The step a theme shows: the stored [choice] as far as [grade] allows (a step chosen before a diary reset is
     * lowered to the best open one), or the best open step when nothing was chosen.
     */
    fun effectiveModel(grade: Grade, choice: Int?): Int = choice?.coerceIn(0, maxModel(grade)) ?: maxModel(grade)

    /**
     * Colourways open like the steps (one more per grade), but a new one is never put on by itself: without a
     * choice the theme keeps its own colours, and a choice above the grade falls back to them.
     */
    fun effectiveColor(grade: Grade, choice: Int?): Int = choice?.takeIf { it in 0..maxModel(grade) } ?: 0

    /** How many of a theme's props the grade has opened (silver 1, gold 2, platinum and above 3). */
    fun openDecor(grade: Grade): Int = (grade.level - 1).coerceIn(0, io.vroomi.character.theme.ThemeDecor.COUNT)

    /** The props shown: those switched on among the ones the grade has opened. */
    fun effectiveDecor(grade: Grade, mask: Int?): Int = (mask ?: 0) and ((1 shl openDecor(grade)) - 1)
}
