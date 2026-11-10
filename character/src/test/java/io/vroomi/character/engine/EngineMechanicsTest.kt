package io.vroomi.character.engine

import io.vroomi.character.CarSignals
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.core.vehicle.model.DriveModeState
import io.vroomi.core.vehicle.model.GearState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class EngineMechanicsTest {

    private fun car(kmh: Float, gear: GearState = GearState.DRIVE, a: Float = 0f, mode: DriveModeState = DriveModeState.NORMAL, block: CarSignals.() -> CarSignals = { this }) =
        CarSignals(speedKmh = kmh, accelMps2 = a, gear = gear, moving = kmh > 1f, drivingMode = mode, fanLevel = 0, fanMax = 8,
            acOn = false, safetyWarningActive = false, brakePressed = false).block()

    private fun EngineModel.run(seconds: Double, c: (Double) -> CarSignals) { var t = 0.0; while (t < seconds) { step(1.0 / 60, c(t)); t += 1.0 / 60 } }

    // ---------------------------------------------------------------- gearbox

    @Test fun gearRatiosGiveTheSanityNumbers() {
        assertEquals(1724.0, Gearbox.rpmFor(100.0, 8), 10.0)
        assertEquals(1660.0, Gearbox.rpmFor(50.0, 5), 10.0)
    }

    @Test fun cruising100SettlesInEighth() {
        val e = EngineModel()
        e.run(40.0) { t -> car((t * 4).coerceAtMost(100.0).toFloat(), a = if (t < 25) 1.1f else 0f) }
        assertEquals(8, e.gearbox.gear)
        assertEquals(1724.0, e.rpm, 15.0)
    }

    @Test fun fifty_inNormal_settlesInFifth_fromBelowAndFromAbove() {
        val up = EngineModel(); up.run(40.0) { t -> car((t * 5).coerceAtMost(50.0).toFloat(), a = if (t < 10) 1.4f else 0f) }
        assertEquals("from below", 5, up.gearbox.gear)
        assertEquals(1660.0, up.rpm, 20.0)
        val down = EngineModel(); down.run(60.0) { t -> car((100 - t * 3).coerceAtLeast(50.0).toFloat(), a = if (t < 17) -0.8f else 0f) }
        assertEquals("from above", 5, down.gearbox.gear)
    }

    @Test fun noGearHuntingWithSpeedNoise() {
        val rnd = Random(7)
        for (mode in listOf(DriveModeState.ECO, DriveModeState.NORMAL, DriveModeState.SPORT)) for (gear in 1..7) {
            val kmh = Gearbox.up(mode) / Gearbox.rpmFor(1.0, gear) // speed at the base upshift point of this gear
            val e = EngineModel()
            e.run(20.0) { car(kmh.toFloat(), mode = mode) }
            val before = e.gearbox.shifts
            var t = 0.0; var lastShiftAt = -100.0; var count = 0
            while (t < 60.0) {
                val n = (rnd.nextDouble() * 4 - 2).toFloat()
                e.step(1.0 / 60, car(kmh.toFloat() + n, mode = mode, a = n * 0.2f))
                if (e.gearbox.shifts != before + count) { count++; assertTrue("$mode g$gear shifted twice within 10 s", t - lastShiftAt >= 10.0); lastShiftAt = t }
                t += 1.0 / 60
            }
            assertTrue("$mode g$gear shifts=$count", count <= 1)
        }
    }

    @Test fun shiftsAreAtLeast1point2SecondsApart() {
        val e = EngineModel(); var t = 0.0; var last = -100.0; var n = 0
        while (t < 30) {
            e.step(1.0 / 60, car((t * 8).coerceAtMost(160.0).toFloat(), a = 2.2f, mode = DriveModeState.SPORT))
            if (e.gearbox.shifts != n) { n = e.gearbox.shifts; assertTrue(t - last >= Gearbox.MIN_SHIFT_GAP_S - 1e-6); last = t }
            t += 1.0 / 60
        }
        assertTrue("several shifts happened ($n)", n >= 4)
    }

    @Test fun kickDownDropsAGear_twoInSport() {
        for ((mode, drop) in listOf(DriveModeState.NORMAL to 1, DriveModeState.SPORT to 2)) {
            val e = EngineModel()
            e.run(30.0) { car(80f, mode = mode) }
            val g = e.gearbox.gear
            e.run(3.0) { car(80f, a = 2.5f, mode = mode) }
            assertEquals("$mode", g - drop, e.gearbox.gear)
        }
    }

    @Test fun idleInPandN_plusAc_andConverterSlip() {
        for (gear in listOf(GearState.PARK, GearState.NEUTRAL)) {
            val e = EngineModel(); e.run(3.0) { car(0f, gear) }
            assertEquals(750.0, e.rpm, 2.0)
            val ac = EngineModel(); ac.run(3.0) { car(0f, gear) { copy(acOn = true) } }
            assertEquals(800.0, ac.rpm, 2.0)
        }
        val e = EngineModel(); e.run(2.0) { car(4f, a = 2f) }
        assertEquals(1, e.gearbox.gear)
        assertTrue("slip rpm=${e.rpm}", e.rpm > 1400)
        val free = EngineModel(); var t = 0.0; while (t < 3) { free.step(1.0 / 60, car(0f, GearState.NEUTRAL), neutralThrottle = 1.0); t += 1.0 / 60 }
        assertEquals(2950.0, free.rpm, 10.0)
    }

    // ---------------------------------------------------------------- geometry

    @Test fun sliderCrankClosedForm() {
        val r = EngineGeometry.CRANK_R_MM; val l = EngineGeometry.ROD_MM
        assertEquals(r + l, EngineGeometry.pinHeightMm(0.0), 1e-9)      // TDC
        assertEquals(l - r, EngineGeometry.pinHeightMm(PI), 1e-9)       // BDC
        for (k in 0 until 8) {
            val th = k * PI / 4
            val x = EngineGeometry.pinHeightMm(th)
            assertEquals(r * cos(th) + sqrt(l * l - r * r * sin(th) * sin(th)), x, 1e-9)
            // The rod keeps its length: distance from crank pin to piston pin is always l.
            val px = r * sin(th); val py = r * cos(th)
            assertEquals(l, sqrt(px * px + (x - py) * (x - py)), 1e-9)
        }
        assertEquals(1.40, EngineGeometry.displacementL, 0.01)
    }

    @Test fun firingOrderAndOnePowerStrokeAtATime() {
        var deg = 0.0
        while (deg < 720) {
            val power = (0 until 4).count { EngineGeometry.stroke(EngineGeometry.phase(deg + 0.5, it)) == Stroke.POWER }
            assertEquals("at $deg", 1, power)
            deg += 1.0
        }
        // Firing order 1-3-4-2: ignition (phase 0) at cycle 0, 180, 360, 540.
        val order = (0 until 4).sortedBy { EngineGeometry.PHASE_OFFSET_DEG[it] }.map { it + 1 }
        assertEquals(listOf(1, 3, 4, 2), order)
        // Cylinders 1 and 4 share a throw, 2 and 3 are opposite.
        assertEquals(EngineGeometry.crankDeg(100.0, 0), EngineGeometry.crankDeg(100.0, 3), 1e-9)
        assertEquals((EngineGeometry.crankDeg(100.0, 0) + 180) % 360, EngineGeometry.crankDeg(100.0, 1), 1e-9)
    }

    @Test fun valveOverlapOnlyAroundExhaustTdc() {
        var p = 0.0
        while (p < 720) {
            val both = EngineGeometry.exhaustLift(p) > 0 && EngineGeometry.intakeLift(p) > 0
            if (both) assertTrue("overlap at $p", p in 350.0..370.0)
            p += 0.5
        }
        assertEquals(1.0, EngineGeometry.exhaustLift(255.0), 1e-9)
        assertEquals(1.0, EngineGeometry.intakeLift(465.0), 1e-9)
        assertEquals(0.0, EngineGeometry.camRad(255.0, EngineGeometry.EXHAUST_OPEN, EngineGeometry.EXHAUST_CLOSE), 1e-9)
    }

    // ---------------------------------------------------------------- slow motion and flashing

    @Test fun slowMotionCapsAndCombustionRate() {
        assertEquals(0.30, EngineGeometry.visualRps(750.0), 1e-9)
        assertEquals(1.40, EngineGeometry.visualRps(6000.0), 1e-9)
        assertTrue(EngineGeometry.visualRps(9000.0) <= EngineGeometry.MAX_VISUAL_RPS)
        assertEquals(71, EngineGeometry.slowMotionFactor(6000.0, 1.4))

        val m = EngineMechanics()
        val t = CharacterTarget(car = car(0f, GearState.NEUTRAL), journey = Journey())
        var s = 0.0
        while (s < 60) { m.step(1f / 60, t, 0L, 12f, EnginePowertrain.ICE, neutralThrottle = 1.0); s += 1.0 / 60 }
        assertTrue(m.visualRps <= EngineGeometry.MAX_VISUAL_RPS)
        // Pin the estimate at the 6,000 rpm display cap (reverse ratio at 80 km/h is far above it).
        val m2 = EngineMechanics()
        val pinned = CharacterTarget(car = car(80f, GearState.REVERSE, a = 1f), journey = Journey())
        var s2 = 0.0
        while (s2 < 5) { m2.step(1f / 60, pinned, 0L, 12f, EnginePowertrain.ICE); s2 += 1.0 / 60 }
        assertEquals(6000.0, m2.engine.rpm, 1.0)
        val before = m2.combustionEvents
        s2 = 0.0
        while (s2 < 60) { m2.step(1f / 60, pinned, 0L, 12f, EnginePowertrain.ICE); s2 += 1.0 / 60 }
        val rate = (m2.combustionEvents - before) / 60.0
        assertTrue("rate=$rate", rate <= 3.0 && rate > 2.5)
    }

    @Test fun glowIsSoft() {
        val m = EngineMechanics()
        val t = CharacterTarget(car = car(0f, GearState.PARK), journey = Journey())
        var s = 0.0; var maxStep = 0f; var last = 0f
        while (s < 10) {
            m.step(1f / 60, t, 0L, 12f, EnginePowertrain.ICE)
            val g = m.glow(0); maxStep = maxOf(maxStep, g - last); last = g; s += 1.0 / 60
        }
        // 60 ms attack at 60 fps: no single frame jumps by more than about a quarter of the peak.
        assertTrue("step=$maxStep", maxStep < 0.3f)
    }

    // ---------------------------------------------------------------- fuel cut, boost

    @Test fun fuelCutOnDecelerationAndBrake() {
        val e = EngineModel()
        e.run(10.0) { car(60f) }
        assertFalse(e.fuelCut)
        e.step(1.0 / 60, car(60f, a = -1.2f)); assertTrue(e.fuelCut)
        e.step(1.0 / 60, car(60f) { copy(brakePressed = true) }); assertTrue(e.fuelCut)
        e.step(1.0 / 60, car(8f) { copy(brakePressed = true) }); assertFalse("no fuel cut at walking pace", e.fuelCut)
        e.step(1.0 / 60, car(0f, GearState.PARK) { copy(brakePressed = true) }); assertFalse(e.fuelCut)
    }

    @Test fun boostLagsAndWastegateOpensHigh() {
        assertEquals(0.0, EngineModel.boostTarget(1.0, 1200.0), 1e-9)
        assertEquals(1.0, EngineModel.boostTarget(1.0, 3000.0), 1e-9)
        val e = EngineModel()
        e.run(5.0) { car(30f, GearState.DRIVE) }
        e.run(0.6) { car(30f, a = 3f, mode = DriveModeState.SPORT) }
        val after06 = e.boost
        e.run(4.0) { car(30f, a = 3f, mode = DriveModeState.SPORT) }
        assertTrue("lag: $after06 < ${e.boost}", after06 < e.boost * 0.8)
        assertEquals(EngineModel.WASTEGATE_AT, 0.85, 0.0)
        assertTrue(e.boost > 0.85 == e.wastegateOpen)
    }

    // ---------------------------------------------------------------- EV

    @Test fun evSignsAndPower() {
        val m = MotorModel()
        repeat(120) { m.step(1.0 / 60, car(20f, GearState.REVERSE)) }
        assertTrue("reverse rpm=${m.rpm}", m.rpm < -1000)
        val f = MotorModel(); repeat(120) { f.step(1.0 / 60, car(100f)) }
        assertEquals(Gearbox.wheelRpm(100.0) * 9.0, f.rpm, 30.0)
        // Formula at three points
        assertEquals((0.011 * 1900 * 9.81 * 27.7778 + 0.5 * 1.2 * 0.62 * 27.7778 * 27.7778 * 27.7778) / 1000, MotorModel.powerKw(0.0, 100.0), 0.01)
        assertEquals((1900 * 2.0 * 13.8889 + 0.5 * 1.2 * 0.62 * 13.8889 * 13.8889 * 13.8889 + 0.011 * 1900 * 9.81 * 13.8889) / 1000, MotorModel.powerKw(2.0, 50.0), 0.01)
        assertEquals(-60.0, MotorModel.powerKw(-6.0, 90.0), 1e-9)
        val r = MotorModel(); repeat(60) { r.step(1.0 / 60, car(60f, a = -2f)) }
        assertTrue("regen kW=${r.powerKw}", r.regen && r.powerKw < 0)
    }

    @Test fun autoDetectionIsFixedDuringAJourney() {
        val d = PowertrainDetector()
        assertEquals(Powertrain.ICE, d.update(null, null, false, 0))
        assertEquals(Powertrain.EV, d.update(80, null, false, 1_000))
        assertEquals(Powertrain.EV, d.update(null, null, true, 2_000))     // journey starts: locked EV
        assertEquals(Powertrain.EV, d.update(null, null, true, 60_000))    // signal gone, still EV
        assertEquals(Powertrain.ICE, d.update(null, null, false, 70_000))  // parked again: re-detected
        assertEquals(Powertrain.ICE, d.update(null, null, true, 71_000))
        assertEquals(Powertrain.ICE, d.update(55, null, true, 72_000))     // EV data mid-drive: no switch
    }

    // ---------------------------------------------------------------- honesty, safety

    @Test fun chipsNeverClaimDiagnostics() {
        val forbidden = listOf("온도", "℃", "°", "압력", "점검", "경고", "고장", "오일", "연료량", "냉각수")
        for (s in EngineMechanics.ALL_CHIPS) {
            assertTrue("too long: $s", s.length <= 12)
            for (f in forbidden) assertFalse("'$s' contains '$f'", s.contains(f))
        }
    }

    @Test fun safetyFreezesDecorationButNotTheGauge() {
        val m = EngineMechanics()
        val j = Journey(active = true, tripSeq = 1)
        var s = 0.0
        while (s < 5) { m.step(1f / 60, CharacterTarget(car = car(60f), journey = j), 0L, 12f, EnginePowertrain.ICE); s += 1.0 / 60 }
        val warn = car(60f) { copy(safetyWarningActive = true) }
        s = 0.0
        while (s < 2) { m.step(1f / 60, CharacterTarget(car = warn, journey = j), 0L, 12f, EnginePowertrain.ICE); s += 1.0 / 60 }
        val cyc = m.cycleDeg; val deco = m.decoTime; val rpmBefore = m.engine.rpm
        s = 0.0
        while (s < 3) { m.step(1f / 60, CharacterTarget(car = warn.copy(speedKmh = 100f), journey = j), 0L, 12f, EnginePowertrain.ICE); s += 1.0 / 60 }
        assertEquals("decoration frozen", cyc, m.cycleDeg, 1e-6)
        assertEquals(deco, m.decoTime, 1e-6)
        assertTrue("gauge keeps moving: $rpmBefore -> ${m.engine.rpm}", abs(m.engine.rpm - rpmBefore) > 100)
        assertEquals(null, m.chip)
    }

    @Test fun arrivalShutsTheEngineDownInPark() {
        val m = EngineMechanics()
        val j = Journey(active = true, tripSeq = 1)
        var s = 0.0
        while (s < 3) { m.step(1f / 60, CharacterTarget(car = car(20f), journey = j), 0L, 12f, EnginePowertrain.ICE); s += 1.0 / 60 }
        val arrived = car(0f, GearState.PARK) { copy(arrivalSeq = 1, fanLevel = 0) }
        s = 0.0
        while (s < 4) { m.step(1f / 60, CharacterTarget(car = arrived, journey = Journey()), 0L, 12f, EnginePowertrain.ICE); s += 1.0 / 60 }
        assertFalse(m.engine.running)
        assertEquals(0.0, m.engine.rpm, 1.0)
        assertTrue("radiator fan runs on", m.fanRps > 0.5)
        assertEquals("도착", m.chip)
        // Leaving P restarts it.
        m.step(1f / 60, CharacterTarget(car = car(0f, GearState.DRIVE), journey = Journey()), 0L, 12f, EnginePowertrain.ICE)
        assertTrue(m.engine.running)
    }

    @Test fun starsBurnCleanAndHarshBrakeRocks() {
        val m = EngineMechanics()
        var j = Journey(active = true, tripSeq = 1)
        var c = car(50f)
        var s = 0.0
        while (s < 2) { m.step(1f / 60, CharacterTarget(car = c, journey = j), 0L, 12f, EnginePowertrain.ICE); s += 1.0 / 60 }
        j = j.copy(stars = 1, starSeq = 1); c = c.copy(harshBrakeSeq = 1)
        s = 0.0; var clean = false; var rocked = false
        while (s < 3) {
            m.step(1f / 60, CharacterTarget(car = c, journey = j), 0L, 12f, EnginePowertrain.ICE)
            if (m.cleanBurn.any { it }) clean = true
            if (abs(m.rock()) > 0.3f) rocked = true
            s += 1.0 / 60
        }
        assertTrue(clean && rocked)
    }

    // ---------------------------------------------------------------- telemetry curves (upgrade steps)

    private fun peakDeg(intake: Boolean): Int = (0 until 720).maxByOrNull { EngineGeometry.valveLift(it.toFloat(), intake) }!!

    @Test fun intakePeakNear470() {
        assertEquals(470.0, peakDeg(true).toDouble(), 10.0)
        assertEquals(1f, EngineGeometry.valveLift(peakDeg(true).toFloat(), true), 0.01f)
        assertEquals(0f, EngineGeometry.valveLift(100f, true), 0f)
    }

    @Test fun exhaustPeakNear250() {
        assertEquals(250.0, peakDeg(false).toDouble(), 10.0)
        assertEquals(0f, EngineGeometry.valveLift(600f, false), 0f)
    }

    @Test fun raceTorqueAboveBase() {
        fun peak(m: Int) = (750..7000 step 50).maxOf { EngineGeometry.torqueAt(it.toFloat(), m) }
        for (m in 1..4) assertTrue("step $m", peak(m) > peak(m - 1))
        assertTrue(peak(4) > peak(0) * 2f)
        // The turbo steps reach their plateau early; the naturally aspirated ones peak in the mid range.
        assertTrue(EngineGeometry.torqueAt(2200f, 2) > EngineGeometry.torqueAt(2200f, 0) * 1.5f)
        for (m in 0..4) for (r in 0..7200 step 100) assertTrue(EngineGeometry.torqueAt(r.toFloat(), m) > 0f)
    }

    @Test fun sparkAdvanceGrowsWithSpeedAndShrinksWithLoad() {
        assertTrue(EngineGeometry.ignitionAdvanceDeg(5000.0, 0.3) > EngineGeometry.ignitionAdvanceDeg(1000.0, 0.3))
        assertTrue(EngineGeometry.ignitionAdvanceDeg(3000.0, 0.9) < EngineGeometry.ignitionAdvanceDeg(3000.0, 0.2))
    }

    @Test fun noSpoolChipWithoutATurbo() {
        val m = EngineMechanics().also { it.turbo = false }
        var seen = false
        for (i in 0 until 600) {
            val t = i / 60f
            m.step(1f / 60f, CharacterTarget(kart = io.vroomi.character.KartState(driving = true), car = car(60f + t * 9f, a = 2.6f, mode = DriveModeState.SPORT),
                journey = io.vroomi.character.Journey(active = true, tripSeq = 1)), 1_000_000L + i * 16L, 14f, EnginePowertrain.ICE)
            if (m.chip == "터보 스풀업") seen = true
        }
        assertTrue(!seen)
    }
}
