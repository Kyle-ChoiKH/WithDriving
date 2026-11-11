package io.vroomi.character.render.paper

/**
 * The pop-up book's upgrade ladder (spec 5.9). Every step keeps what the one before had:
 * 1 a two-ink print (blue and warm red), 2 a third ink (yellow, so greens and oranges appear where plates overlap),
 * 3 one more pop-up plane behind the mountains, 4 a pull tab that slides two clouds across the sky (only while
 * parked), 5 the special edition: fluorescent pink in place of the red, and gold foil.
 */
internal object PaperStep {
    fun threeInks(model: Int): Boolean = model >= 1
    fun farPlane(model: Int): Boolean = model >= 2
    fun pullTab(model: Int): Boolean = model >= 3
    fun special(model: Int): Boolean = model >= 4
}
