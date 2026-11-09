package io.vroomi.app.ui

import io.vroomi.app.ui.design.Ambience
import io.vroomi.app.ui.design.ColorMath
import io.vroomi.app.ui.design.Dimens
import io.vroomi.app.ui.design.MaterialToken
import io.vroomi.app.ui.design.Neutral
import io.vroomi.app.ui.design.Semantic
import io.vroomi.app.ui.design.SmoothCorners
import io.vroomi.app.ui.design.Space
import io.vroomi.app.ui.design.VIcons
import io.vroomi.app.ui.design.VType
import io.vroomi.character.render.theme.ChromeZones
import io.vroomi.app.ui.motion.Motion
import io.vroomi.app.ui.motion.SpringMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/** Motion tokens, Oklab, contrast, the type scale and sizes, the chrome-zone contract and the smooth-corner geometry. */
class DesignTokensTest {

    // ---------------------------------------------------------------- springs

    /** Integrates x'' = -k x - c x' numerically (independent of the closed form) and returns the 1 % settle time. */
    private fun simulatedSettleMs(stiffness: Float, zeta: Float): Double {
        val k = stiffness.toDouble(); val c = 2 * zeta * sqrt(k)
        var x = 1.0; var v = 0.0; var t = 0.0; var last = 0.0
        val dt = 0.00005
        var peak = 0.0
        while (t < 3.0) {
            val a = -k * x - c * v
            v += a * dt; x += v * dt; t += dt
            if (abs(x) > 0.01) last = t
            if (x < 0) peak = maxOf(peak, -x)
        }
        return last * 1000
    }

    @Test fun springTokensSettleInsideTheirRanges() {
        val ranges = mapOf("Press" to 120.0..160.0, "Snappy" to 240.0..320.0, "Smooth" to 380.0..480.0, "Passage" to 650.0..800.0)
        val damping = mapOf("Press" to 0.65..0.75, "Snappy" to 0.85..0.90, "Smooth" to 0.90..0.95, "Passage" to 0.95..1.00)
        for (s in Motion.springs) {
            val ms = simulatedSettleMs(s.stiffness, s.dampingRatio)
            assertTrue("${s.name} settles in $ms ms", ms in ranges.getValue(s.name))
            assertTrue("${s.name} damping ${s.dampingRatio}", s.dampingRatio.toDouble() in damping.getValue(s.name))
        }
    }

    @Test fun overshootRules() {
        // Nothing larger than a quarter of the screen overshoots; small things at most 3 %.
        assertTrue(SpringMath.overshoot(Motion.Smooth.dampingRatio.toDouble()) < 0.001)
        assertTrue(SpringMath.overshoot(Motion.Passage.dampingRatio.toDouble()) < 0.001)
        assertTrue(SpringMath.overshoot(Motion.Snappy.dampingRatio.toDouble()) <= 0.03)
        assertTrue(SpringMath.overshoot(Motion.Press.dampingRatio.toDouble()) <= 0.03)
    }

    @Test fun movingCurveIsMonotonic() {
        var last = 0f
        for (i in 0..100) { val y = Motion.MovingCurve.ease(i / 100f); assertTrue(y >= last - 1e-4f); last = y }
        assertEquals(1f, Motion.MovingCurve.ease(1f), 0f)
    }

    // ---------------------------------------------------------------- colour

    @Test fun oklabRoundTripAndLerp() {
        val lab = FloatArray(3)
        for (c in intArrayOf(0xFF3CC8B4.toInt(), 0xFFF2A93B.toInt(), 0xFF0E1B2B.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF9AA6FF.toInt())) {
            ColorMath.toOklab(c, lab)
            val back = ColorMath.fromOklab(lab[0], lab[1], lab[2])
            for (sh in intArrayOf(16, 8, 0)) assertTrue("round trip %08X".format(c), abs(((c shr sh) and 255) - ((back shr sh) and 255)) <= 1)
        }
        val a = 0xFF3CC8B4.toInt(); val b = 0xFFD2704F.toInt()
        assertEquals(a, ColorMath.lerpOklab(a, b, 0f))
        assertEquals(b, ColorMath.lerpOklab(a, b, 1f))
        // Black to white: the midpoint has Oklab lightness 0.5 (perceptually half way, not the sRGB average).
        val mid = ColorMath.lerpOklab(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f)
        ColorMath.toOklab(mid, lab)
        assertEquals(0.5f, lab[0], 0.01f)
        // Lightness moves monotonically along an Oklab lerp.
        var last = -1f
        for (i in 0..20) { ColorMath.toOklab(ColorMath.lerpOklab(0xFF070A18.toInt(), 0xFFCDB98C.toInt(), i / 20f), lab); assertTrue(lab[0] >= last - 1e-3f); last = lab[0] }
    }

    @Test fun everyThemeTintPassesContrastInLightAndDark() {
        assertEquals(11, Ambience.all.size)
        for (a in Ambience.all) {
            assertTrue("${a.theme} tint on light ${ColorMath.contrast(a.tintLight, Neutral.SURFACE_LIGHT)}", ColorMath.contrast(a.tintLight, Neutral.SURFACE_LIGHT) >= 4.5f)
            assertTrue("${a.theme} tint on dark", ColorMath.contrast(a.tintDark, Neutral.SURFACE_DARK) >= 4.5f)
            assertTrue("${a.theme} label on light tint", ColorMath.contrast(a.onTint(false), a.tintLight) >= 4.5f)
            assertTrue("${a.theme} label on dark tint", ColorMath.contrast(a.onTint(true), a.tintDark) >= 4.5f)
        }
    }

    @Test fun textTiersOnSurfaces() {
        for (dark in listOf(false, true)) {
            val surface = if (dark) Neutral.SURFACE_DARK else Neutral.SURFACE_LIGHT
            val ink = if (dark) Neutral.INK_DARK else Neutral.INK_LIGHT
            fun tier(a: Float) = ColorMath.over((((a * 255).toInt()) shl 24) or (ink and 0xFFFFFF), surface)
            assertTrue(ColorMath.contrast(ink, surface) >= 4.5f)
            assertTrue("secondary dark=$dark", ColorMath.contrast(tier(Neutral.SECONDARY), surface) >= 4.5f)
        }
    }

    @Test fun primaryTextOnEveryMaterialOverAnyScene() {
        for (dark in listOf(false, true)) for (m in MaterialToken.entries) for (blurred in listOf(true, false)) {
            val ink = if (dark) Neutral.INK_DARK else Neutral.INK_LIGHT
            for (scene in intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF808080.toInt())) {
                val surface = ColorMath.over(m.fill(dark, blurred), scene)
                assertTrue("$m dark=$dark blurred=$blurred over %08X: %.2f".format(scene, ColorMath.contrast(ink, surface)), ColorMath.contrast(ink, surface) >= 4.5f)
            }
        }
    }


    // ---------------------------------------------------------------- the interface pass: accents, type, sizes, zones

    @Test fun accentIsTamedForInterfaceUse() {
        val lab = FloatArray(3)
        for (a in Ambience.all) for (dark in listOf(false, true)) {
            val c = a.tintFor(dark)
            // Chroma capped: no theme tint is used at full strength (ORBIT's phosphor cyan becomes a quiet sea green).
            assertTrue("${a.theme} dark=$dark chroma ${ColorMath.chroma(c)}", ColorMath.chroma(c) <= 0.125f)
            ColorMath.toOklab(c, lab)
            // A dark-mode accent stays a soft light tone (never a glaring one), a light-mode accent a deep one.
            if (dark) assertTrue("${a.theme} dark L ${lab[0]}", lab[0] in 0.62f..0.90f) else assertTrue("${a.theme} light L ${lab[0]}", lab[0] <= 0.62f)
        }
        // A near-neutral tint (CALIBRE's steel) becomes graphite in light mode, not a washed-out grey.
        ColorMath.toOklab(Ambience.of(io.vroomi.character.calibre.SceneTheme.CALIBRE).tintLight, lab)
        assertTrue("graphite L ${lab[0]}", lab[0] <= 0.40f)
    }

    @Test fun accentTextOnStaticMaterialsAndToggleWash() {
        for (a in Ambience.all) for (dark in listOf(false, true)) {
            val accent = a.tintFor(dark)
            // The demo caption's "데모" and an on toggle: the static Thick material over any scene.
            for (scene in intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF808080.toInt())) {
                val surface = ColorMath.over(MaterialToken.Thick.fill(dark, blurred = false), scene)
                assertTrue("${a.theme} dark=$dark accent on Thick over %08X".format(scene), ColorMath.contrast(accent, surface) >= 4.5f)
            }
            // The big toggle's on state: its label in the accent deepened on the accent wash.
            val wash = ColorMath.over((((if (dark) 0.24f else 0.18f) * 255).toInt() shl 24) or (accent and 0xFFFFFF), Neutral.surface(dark))
            val ink = ColorMath.ensureContrast(accent, wash, 4.6f)
            assertTrue("${a.theme} dark=$dark toggle label", ColorMath.contrast(ink, wash) >= 4.5f)
            // Text on the grouped page surfaces (settings, sheets' lists).
            assertTrue("${a.theme} accent on grouped", ColorMath.contrast(accent, Neutral.grouped(dark)) >= 4.5f)
        }
    }

    @Test fun textTiersOnPageSurfacesAndSemanticColours() {
        for (dark in listOf(false, true)) {
            val ink = Neutral.ink(dark)
            for (bg in intArrayOf(Neutral.background(dark), Neutral.grouped(dark), Neutral.surface(dark))) {
                val secondary = ColorMath.over((((Neutral.SECONDARY * 255).toInt()) shl 24) or (ink and 0xFFFFFF), bg)
                assertTrue("primary dark=$dark on %08X".format(bg), ColorMath.contrast(ink, bg) >= 4.5f)
                assertTrue("secondary dark=$dark on %08X".format(bg), ColorMath.contrast(secondary, bg) >= 4.5f)
                for (c in intArrayOf(Semantic.positive(dark), Semantic.caution(dark), Semantic.critical(dark)))
                    assertTrue("semantic %08X dark=$dark on %08X".format(c, bg), ColorMath.contrast(c, bg) >= 4.5f)
            }
        }
    }

    @Test fun typeScaleHasSixRolesAndTheDrivingFloor() {
        assertEquals(6, VType.parkedRoles.size)
        val sizes = VType.parkedRoles.map { it.fontSize.value }
        // A strict hierarchy: every role is smaller than the one above it.
        for (i in 1 until sizes.size) assertTrue("role $i", sizes[i] < sizes[i - 1])
        assertTrue(sizes.last() >= VType.PARKED_FLOOR_SP)
        // While moving nothing is smaller than 24 sp.
        for (r in VType.movingRoles) assertTrue("moving role ${r.fontSize}", r.fontSize.value >= VType.MOVING_FLOOR_SP)
        assertEquals(24f, VType.MOVING_FLOOR_SP, 0f)
        // Line heights sit on the 4 dp grid; Hangul is never tracked tighter than -1 %.
        for (r in VType.parkedRoles + VType.movingRoles) {
            assertEquals(0f, r.lineHeight.value % 4f, 0.001f)
            assertTrue(r.letterSpacing.value >= -0.0101f)
        }
    }

    @Test fun minimumSizesAndTheToggle() {
        assertTrue(Dimens.bigToggleW.value >= 168f && Dimens.bigToggleH.value >= 96f)
        assertTrue(Dimens.touchMoving.value >= 76f)
        assertTrue(Dimens.button.value >= Dimens.touch.value)
        assertTrue(Dimens.buttonCompact.value >= Dimens.touchMin.value)
        assertTrue(Dimens.listRow.value >= Dimens.touchMoving.value)
        assertTrue(Dimens.railItem.value >= Dimens.touchMoving.value)
        assertTrue(Dimens.segmented.value >= Dimens.touchMin.value)
        // Every spacing step is on the 4 dp grid (2 dp is the one optical exception).
        for (d in listOf(Space.s4, Space.s8, Space.s12, Space.s16, Space.s20, Space.s24, Space.s32, Space.s40, Space.s48, Space.s56, Space.s64))
            assertEquals(0f, d.value % 4f, 0f)
    }

    @Test fun drivingChromeFitsTheChromeZones() {
        val w = 1920f; val h = 720f
        // The status chip and the toggle rows the overlay draws, from the same numbers as the zones.
        val chip = ChromeZones.zones(ChromeZones.Mode.MOVING, w, h).first { it.name == "chip" }
        assertTrue(Space.moving.value + Dimens.statusChipMaxW.value <= chip.right)
        assertTrue(Space.moving.value + Dimens.statusChipH.value <= chip.bottom)
        val toggles = ChromeZones.zones(ChromeZones.Mode.MOVING, w, h).first { it.name == "toggles" }
        assertTrue(w - Space.moving.value - (2 * Dimens.bigToggleW.value + Dimens.bigToggleGap.value) >= toggles.left)
        assertTrue(h - Space.moving.value - Dimens.bigToggleH.value >= toggles.top)
        val dock = ChromeZones.zones(ChromeZones.Mode.MOVING_DEMO, w, h).first { it.name == "dock" }
        assertEquals(3 * Dimens.bigToggleW.value + 2 * Dimens.bigToggleGap.value, Dimens.captionW.value, 0f)
        assertTrue(w - Space.moving.value - Dimens.captionW.value >= dock.left)
        assertTrue(h - Space.moving.value - Dimens.bigToggleH.value - Dimens.captionGap.value - Dimens.captionMaxH.value >= dock.top)
        // Parked: the badge and the action stack.
        val status = ChromeZones.zones(ChromeZones.Mode.PARKED, w, h).first { it.name == "status" }
        assertTrue(Space.parked.value + Dimens.badgeWidth.value <= status.right && Space.parked.value + Dimens.badgeHeight.value <= status.bottom)
        val actions = ChromeZones.zones(ChromeZones.Mode.PARKED, w, h).first { it.name == "actions" }
        assertTrue(w - Space.parked.value - Dimens.actionsWidth.value >= actions.left)
        assertTrue(h - Space.parked.value - (2 * Dimens.button.value + Space.s12.value) >= actions.top)
    }

    @Test fun iconsStayInsideTheirGrid() {
        for (icon in VIcons.all) {
            assertEquals(24f, icon.viewportWidth, 0f)
            assertEquals(24f, icon.viewportHeight, 0f)
            assertEquals(24f, icon.defaultWidth.value, 0f)
        }
    }

    // ---------------------------------------------------------------- smooth corners

    private fun sample(g: SmoothCorners.Geometry): List<Pair<Float, Float>> {
        val pts = ArrayList<Pair<Float, Float>>()
        var x = g.startX; var y = g.startY
        pts += x to y
        for (s in g.segs) {
            if (s.cubic) {
                for (i in 1..16) {
                    val t = i / 16f; val u = 1 - t
                    pts += (u * u * u * x + 3 * u * u * t * s.x1 + 3 * u * t * t * s.x2 + t * t * t * s.x) to
                        (u * u * u * y + 3 * u * u * t * s.y1 + 3 * u * t * t * s.y2 + t * t * t * s.y)
                }
            } else pts += s.x to s.y
            x = s.x; y = s.y
        }
        return pts
    }

    @Test fun smoothCornerPathIsClosedSymmetricAndInside() {
        for ((w, h, r) in listOf(Triple(400f, 160f, 32f), Triple(730f, 274f, 44f), Triple(120f, 56f, 999f), Triple(90f, 90f, 24f))) {
            val g = SmoothCorners.geometry(w, h, r, 0.6f)
            val last = g.segs.last()
            assertEquals("closed x", g.startX, last.x, 1e-3f)
            assertEquals("closed y", g.startY, last.y, 1e-3f)
            val pts = sample(g)
            for ((px, py) in pts) assertTrue("inside $w x $h: $px,$py", px in -0.01f..w + 0.01f && py in -0.01f..h + 0.01f)
            // Every control point is inside too (the hull bounds the curve).
            for (s in g.segs) if (s.cubic) assertTrue(s.x1 in -0.01f..w + 0.01f && s.y1 in -0.01f..h + 0.01f && s.x2 in -0.01f..w + 0.01f && s.y2 in -0.01f..h + 0.01f)
            // Mirror symmetry: for every sampled point, its mirror across both centre lines is on the outline too.
            for ((px, py) in pts) {
                val mx = w - px; val my = h - py
                assertTrue("mirror x of $px,$py", pts.any { hypot(it.first - mx, it.second - py) < 1.5f })
                assertTrue("mirror y of $px,$py", pts.any { hypot(it.first - px, it.second - my) < 1.5f })
            }
        }
    }

    @Test fun smoothCornerArcLiesOnTheCornerCircle() {
        val r = 40f
        val g = SmoothCorners.geometry(400f, 300f, r, 0.6f)
        // Top-right corner: the arc segment (second cubic of the corner) starts and ends on the circle at (w - r, r).
        val arc = g.segs[2]; val before = g.segs[1]
        assertEquals(r, hypot(before.x - (400f - r), before.y - r), 0.05f)
        assertEquals(r, hypot(arc.x - (400f - r), arc.y - r), 0.05f)
        // Zero smoothing is a plain quarter circle starting r from the corner.
        val plain = SmoothCorners.geometry(400f, 300f, r, 0f)
        assertEquals(400f - r, plain.segs[0].x, 1e-3f)
    }
}
