package io.vroomi.app.ui.design

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import io.vroomi.character.render.theme.ChromeZones

/**
 * The 4/8 dp grid. Layout steps are 8, 16, 24, 32, 40, 48 and 64; 4, 12 and 20 are for the inside of components
 * (padding of a chip, the gap between a title and its subtitle).
 */
object Space {
    val s2 = 2.dp
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s20 = 20.dp
    val s24 = 24.dp
    val s32 = 32.dp
    val s40 = 40.dp
    val s48 = 48.dp
    val s56 = 56.dp
    val s64 = 64.dp

    /** Outer margin of a page: 40 dp on the 1920 × 720 display, 24 dp under 900 dp width. */
    fun margin(wide: Boolean): Dp = if (wide) s40 else s24

    /** The parked home chrome's margin and the moving chrome's margin (the chrome-zone contract). */
    val parked = ChromeZones.PARKED_MARGIN.dp
    val moving = ChromeZones.MOVING_MARGIN.dp
}

/**
 * Corner radii (continuous-curvature corners, [SmoothCornerShape]). Four steps and the capsule:
 * 10 small marks and thumbnails, 16 controls inside a card, 24 cards, toggles and list sections, 32 sheets and
 * floating panels, 44 the welcome card. Inner shapes are concentric: inner = outer - inset, never below 8 dp.
 */
object Radius {
    val r10 = 10.dp
    val r16 = 16.dp
    val r24 = 24.dp
    val r32 = 32.dp
    val r44 = 44.dp
    /** Full capsule (clamped to half the height by the shape). */
    val capsule = 999.dp

    fun inner(outer: Dp, inset: Dp): Dp = max(outer - inset, 8.dp)
}

/** Component sizes (everything a screen needs that is not a spacing step). */
object Dimens {
    val hairline = 1.dp
    val ring = 2.dp
    val ringGap = 4.dp

    // Rail
    val railWidth = 132.dp
    val railItem = 84.dp
    val railIcon = 28.dp

    // Touch targets
    val touchMin = 48.dp
    val touch = 64.dp
    /** While moving every target is at least 76 dp. */
    val touchMoving = 76.dp

    // Buttons and controls
    /** The height of every capsule button (primary, secondary, plain). */
    val button = 64.dp
    /** The height of the compact buttons inside a card or a list row. */
    val buttonCompact = 52.dp
    val buttonIcon = 22.dp
    val segmented = 52.dp
    val segmentedInset = 4.dp
    val switchW = 64.dp
    val switchH = 38.dp
    val switchThumbInset = 3.dp
    val listRow = 80.dp
    val chipHeight = 52.dp

    // Driving chrome (the chrome-zone contract)
    val bigToggleW = ChromeZones.TOGGLE_W.dp
    val bigToggleH = ChromeZones.TOGGLE_H.dp
    val bigToggleGap = ChromeZones.TOGGLE_GAP.dp
    val bigToggleIcon = 28.dp
    val statusChipH = ChromeZones.CHIP_H.dp
    val statusChipMaxW = ChromeZones.CHIP_MAX_W.dp
    val statusDot = 10.dp
    val captionW = ChromeZones.CAPTION_W.dp
    val captionMaxH = ChromeZones.CAPTION_MAX_H.dp
    val captionGap = ChromeZones.CAPTION_GAP.dp

    // Home
    val badgeWidth = ChromeZones.BADGE_W.dp
    val badgeHeight = ChromeZones.BADGE_H.dp
    val actionsWidth = ChromeZones.ACTIONS_W.dp

    val closeVisual = 48.dp
    val arrowVisual = 56.dp
    val icon = 24.dp
    val iconSmall = 20.dp
    val progress = 6.dp
    val progressThin = 4.dp
    val tick = 6.dp
    val tickWide = 22.dp
    val bubbleMax = 560.dp
    /** The stage's panel (at least) and the width of its note column. */
    val stagePanel = 132.dp
    val stageNote = 560.dp
    val tripCard = 440.dp
    val sheetMax = 820.dp
    val welcomeMax = 720.dp
    val scoreDisc = 88.dp
    val diaryThumb = 104.dp
    val wardrobeCard = 288.dp
    val portrait = 120.dp
    val miniature = 160.dp
    /** A readable column for list pages (settings, diary) on a wide screen. */
    val readable = 820.dp
    /** Two columns from this pane width. */
    val twoColumns = 980.dp
    /** Below this width there is no side pane: the scene stays full-bleed behind the panel. */
    val wideBreak = 900.dp
    val dot = 10.dp
    val grabber = 40.dp
    val grabberH = 5.dp
    val lift = 4.dp
    /** Backdrop blur behind an open sheet (API 31+, parked). */
    val sheetBlur = 20.dp
    /** The gallery's blur while the stage opens over it (API 31+). */
    val recedeBlur = 24.dp
    /** TREASURE_MAP's rolled parchment edge. */
    val rollEdge = 24.dp
}

/** Fractions used by the layout (not motion). */
object Layout {
    /** The scene's pane on non-home tabs (wide screens). */
    const val SCENE_PANE = 0.42f
    /** Gallery card height as a share of the page height (before the width cap); width = height x 8 / 3. */
    const val CARD_HEIGHT = 0.52f
    const val CARD_ASPECT = 8f / 3f
    const val NEIGHBOUR_SCALE = 0.92f
    const val NEIGHBOUR_BRIGHTNESS = 0.7f
    /** The image inside a card moves at 0.8 x the scroll (at most 24 dp): depth inside the window. */
    const val PARALLAX = 0.2f
}
