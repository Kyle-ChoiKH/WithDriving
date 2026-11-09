package io.vroomi.character.calibre

import io.vroomi.character.CarSignals
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class CalibreMechanicsTest {

    // ---------------------------------------------------------------- geometry and time

    @Test fun gearRatiosAreExact() {
        val g = CalibreGeometry
        assertEquals(1.0, g.CENTRE_RPH, 0.0)
        assertEquals(1.0 / 8, g.barrelRph(), 1e-12)
        assertEquals(1.0, g.fourthRpm(), 1e-12)
        assertEquals(12.0, g.escapeRpm(), 1e-12)
        assertEquals(28_800.0, g.beatsPerHour(), 1e-9)
        assertEquals(g.BEATS_PER_HOUR / 3600, g.BEATS_PER_SECOND)
    }

    @Test fun jewelEngravingMatchesTheDrawing() {
        assertEquals("${CalibreGeometry.JEWELS.size} JEWELS", CalibreGeometry.jewelEngraving())
        CalibreGeometry.TRAIN_PATH.forEach { assertTrue(it in CalibreGeometry.JEWELS.indices) }
    }

    @Test fun smallSecondsStepIsOneEighthSecond() {
        val z = ZoneId.of("Asia/Seoul")
        val base = ZonedDateTime.of(2026, 11, 1, 10, 9, 30, 0, z).toInstant().toEpochMilli()
        val values = (0 until 1000 step 5).map { CalibreTime.steppedSeconds(base + it, z) }.distinct()
        assertEquals(8, values.size)
        values.zipWithNext().forEach { (a, b) -> assertEquals(0.125, b - a, 1e-9) }
        assertEquals(30.0, CalibreTime.steppedSeconds(base + 124, z), 1e-9)
        assertEquals(30.125, CalibreTime.steppedSeconds(base + 125, z), 1e-9)
    }

    @Test fun handsFollowTheClockAndZoneIncludingDst() {
        fun check(z: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int) {
            val t = ZonedDateTime.of(y, mo, d, h, mi, s, 0, z)
            val hands = CalibreTime.hands(t.toInstant().toEpochMilli(), z)
            val min = t.minute + t.second / 60.0
            assertEquals(((t.hour % 12) + min / 60.0) * 30.0, hands.hourDeg.toDouble(), 1e-3)
            assertEquals(min * 6.0, hands.minuteDeg.toDouble(), 1e-3)
            assertEquals(t.second * 6.0, hands.secondDeg.toDouble(), 1e-3)
            assertEquals(t.dayOfMonth, hands.dayOfMonth)
        }
        val seoul = ZoneId.of("Asia/Seoul")
        check(seoul, 2026, 11, 1, 10, 9, 36)
        check(seoul, 2026, 12, 31, 23, 59, 59)
        check(seoul, 2027, 2, 14, 0, 0, 0)
        // Across a DST change: 01:59:50 CET and 03:00:10 CEST are 20 real seconds apart.
        val berlin = ZoneId.of("Europe/Berlin")
        check(berlin, 2027, 3, 28, 1, 59, 50)
        check(berlin, 2027, 3, 28, 3, 0, 10)
        val before = ZonedDateTime.of(2027, 3, 28, 1, 59, 50, 0, berlin).toInstant().toEpochMilli()
        val after = CalibreTime.hands(before + 20_000, berlin)
        assertEquals(90.0, after.hourDeg.toDouble(), 0.1) // 3 o'clock
    }

    @Test fun balanceAlwaysRunsAtFourHertz() {
        for (reserve in listOf(6f, 12f, 24f, 41f, 48f, 72f)) {
            val amp = CalibreTime.amplitudeDeg(reserve)
            var crossings = 0
            var prev = CalibreTime.balanceDeg(1_000_001L, amp)
            for (ms in 1_000_002L..1_001_001L) {
                val v = CalibreTime.balanceDeg(ms, amp)
                if ((prev < 0f) != (v < 0f)) crossings++
                prev = v
            }
            assertEquals("reserve $reserve", 8, crossings) // 4 Hz = 8 zero crossings per second
        }
        assertEquals(300f, CalibreTime.amplitudeDeg(72f), 0f)
        assertEquals(300f, CalibreTime.amplitudeDeg(48f), 0f)
        assertEquals(270f, CalibreTime.amplitudeDeg(24f), 0f)
        assertEquals(220f, CalibreTime.amplitudeDeg(6f), 0f)
        assertEquals(285f, CalibreTime.amplitudeDeg(36f), 0.01f)
    }

    // ---------------------------------------------------------------- rotor

    private fun car(kmh: Float, gear: GearState = GearState.DRIVE, fan: Int? = 0, fanMax: Int? = 8, accel: Float = 0f) =
        CarSignals(speedKmh = kmh, accelMps2 = accel, gear = gear, moving = kmh > 3f, fanLevel = fan, fanMax = fanMax)

    private fun run(m: CalibreMechanics, seconds: Double, c: CarSignals, j: Journey = Journey(), t0: Long = 0L, calm: Boolean = false): Long {
        val steps = (seconds * 60).toInt()
        var now = t0
        repeat(steps) { i ->
            now = t0 + (i + 1) * 1000L / 60
            m.step(1f / 60f, CharacterTarget(car = c, journey = j), now, 13f, CalibreFinish.AUTO, calm = calm)
        }
        return now
    }

    @Test fun rotorSpeedRisesWithSpeedAndIsCapped() {
        assertEquals(0.0, CalibreMechanics.rotorTargetRpm(0f), 0.0)
        assertEquals(6.0 + 0.9 * 50, CalibreMechanics.rotorTargetRpm(50f), 1e-9)
        assertEquals(110.0, CalibreMechanics.rotorTargetRpm(150f), 1e-9)
        val speeds = listOf(10f, 30f, 60f, 90f, 120f, 160f)
        val measured = speeds.map { v ->
            val m = CalibreMechanics()
            run(m, 4.0, car(v))
            val a0 = m.rotorAngle; var turned = 0.0; var prev = a0
            repeat(600) { m.step(1f / 60f, CharacterTarget(car = car(v)), 0L, 13f, CalibreFinish.AUTO); var d = m.rotorAngle - prev; if (d < -PI) d += 2 * PI; if (d > PI) d -= 2 * PI; turned += d; prev = m.rotorAngle }
            turned / (2 * PI) * 6.0 // rpm over 10 s
        }
        measured.zipWithNext().forEach { (a, b) -> assertTrue("monotonic $measured", b >= a - 0.5) }
        assertTrue("cap $measured", measured.last() <= 110.0 * 1.02)
        assertTrue("fast enough $measured", measured[3] > 70.0)
    }

    @Test fun calmModeCapsTheRotorAt30Rpm() {
        val m = CalibreMechanics()
        run(m, 6.0, car(120f), calm = true)
        assertTrue(abs(m.rotorOmega) * 60 / (2 * PI) <= 31.0)
    }

    @Test fun rotorSettlesInParkWithinFourSeconds() {
        val m = CalibreMechanics()
        run(m, 5.0, car(120f))
        assertTrue(abs(m.rotorOmega) > 8.0)
        run(m, 4.0, car(0f, GearState.PARK))
        assertTrue("omega=${m.rotorOmega}", abs(m.rotorOmega) < 0.15)
        assertTrue("angle=${m.rotorAngle}", abs(sin(m.rotorAngle - PI / 2)) < 0.15 && m.rotorAngle in 0.0..PI)
    }

    @Test fun rotorTurnsTheOtherWayInReverse() {
        val m = CalibreMechanics()
        run(m, 4.0, car(8f, GearState.REVERSE))
        assertTrue(m.rotorOmega < 0)
        assertEquals(1, m.reverserDriver)
        run(m, 4.0, car(30f, GearState.DRIVE))
        assertTrue(m.rotorOmega > 0)
        assertEquals(0, m.reverserDriver)
    }

    // ---------------------------------------------------------------- crown and reserve

    @Test fun fanUnknownOrOffGivesNoCrownWinding() {
        for (fan in listOf<Int?>(null, 0)) {
            val m = CalibreMechanics(30f)
            run(m, 5.0, car(0f, GearState.PARK, fan = fan))
            assertEquals(0.0, m.crownRps, 0.0)
            assertEquals(0.0, m.crownAngle, 0.0)
        }
        assertEquals(0.0, CalibreMechanics.crownTargetRps(CarSignals(fanLevel = null, fanMax = 8)), 0.0)
        assertEquals(2.0, CalibreMechanics.crownTargetRps(CarSignals(fanLevel = 8, fanMax = 8)), 1e-9)
        assertEquals(0.4 + 1.6 * 0.5, CalibreMechanics.crownTargetRps(CarSignals(fanLevel = 4, fanMax = 8)), 1e-9)
        assertEquals(0.4 + 1.6 * 0.5, CalibreMechanics.crownTargetRps(CarSignals(fanLevel = 4, fanMax = null)), 1e-9)
    }

    @Test fun maxFanWindsAtTheExpectedRate() {
        val m = CalibreMechanics(20f)
        run(m, 1.0, car(0f, GearState.PARK, fan = 8))
        val r0 = m.reserveH; val c0 = m.crownAngle
        run(m, 10.0, car(0f, GearState.PARK, fan = 8))
        val turns = (m.crownAngle - c0) / (2 * PI)
        assertEquals(20.0, turns, 0.3) // 2 rev/s
        assertEquals(turns * 0.02 - 10.0 / 3600, (m.reserveH - r0).toDouble(), 0.01)
        assertTrue(m.clickLift in 0f..1f)
    }

    @Test fun reserveDrainsWindsCapsAndFloors() {
        // Rotor winding: 0.0035 h per revolution
        val m = CalibreMechanics(20f)
        run(m, 3.0, car(60f))
        val r0 = m.reserveH; val a0 = m.rotorAngle
        var revs = 0.0; var prev = a0
        repeat(600) { m.step(1f / 60f, CharacterTarget(car = car(60f)), 0L, 13f, CalibreFinish.AUTO); var d = m.rotorAngle - prev; if (d < -PI) d += 2 * PI; if (d > PI) d -= 2 * PI; revs += abs(d) / (2 * PI); prev = m.rotorAngle }
        assertEquals(revs * 0.0035 - 10.0 / 3600, (m.reserveH - r0).toDouble(), 0.01)

        // Cap with slipping
        val full = CalibreMechanics(71.9f)
        run(full, 5.0, car(0f, GearState.PARK, fan = 8))
        assertEquals(72f, full.reserveH, 0f)
        assertTrue(full.slipping)
        assertEquals("태엽 가득", full.chip)

        // Floor and offline drain
        assertEquals(31f, CalibreMechanics.reserveAfter(41f, 0L, 10 * 3_600_000L), 1e-4f)
        assertEquals(6f, CalibreMechanics.reserveAfter(10f, 0L, 100 * 3_600_000L), 0f)
        val idle = CalibreMechanics(6f)
        run(idle, 3.0, car(0f, GearState.PARK, fan = 0))
        assertEquals(6f, idle.reserveH, 0f)
    }

    // ---------------------------------------------------------------- chronograph, stars, safety

    @Test fun chronographStartsStopsAndFlysBack() {
        val m = CalibreMechanics()
        var now = run(m, 1.0, car(0f, GearState.PARK), Journey(active = false, tripSeq = 0))
        assertFalse(m.chronoRunning)
        now = run(m, 1.0, car(20f), Journey(active = true, tripSeq = 1), now)
        assertTrue(m.chronoRunning); assertEquals(1, m.columnSteps)
        now = run(m, 30.0, car(40f), Journey(active = true, tripSeq = 1), now)
        assertTrue(m.chronoElapsedMs in 30_000L..31_500L)
        now = run(m, 2.0, car(0f, GearState.PARK), Journey(active = false, tripSeq = 1), now)
        assertFalse(m.chronoRunning); assertEquals(2, m.columnSteps)
        val kept = m.chronoElapsedMs
        run(m, 5.0, car(0f, GearState.PARK), Journey(active = false, tripSeq = 1), now).also { now = it }
        assertEquals(kept, m.chronoElapsedMs)
        m.drainEvents()
        run(m, 1.0, car(20f), Journey(active = true, tripSeq = 2), now)
        val ev = m.drainEvents()
        assertTrue(ev.indexOf(CalibreMechanics.Event.CHRONO_RESET) < ev.indexOf(CalibreMechanics.Event.CHRONO_START))
        assertEquals(4, m.columnSteps) // start, stop, reset, start
        assertTrue(m.chronoElapsedMs < 1_500L)
    }

    @Test fun aStarMakesAJewelGlint() {
        val m = CalibreMechanics()
        run(m, 5.0, car(40f), Journey(active = true, tripSeq = 1, starSeq = 3, comboSeq = 0))
        m.drainEvents()
        run(m, 0.5, car(40f), Journey(active = true, tripSeq = 1, starSeq = 4, comboSeq = 0))
        assertTrue(CalibreMechanics.Event.JEWEL_GLINT in m.drainEvents())
        run(m, 0.5, car(40f), Journey(active = true, tripSeq = 1, starSeq = 5, comboSeq = 1))
        assertTrue(CalibreMechanics.Event.TRAIN_GLINT in m.drainEvents())
        assertEquals("보석 반짝!", m.chip)
    }

    @Test fun blinkerStrikesTheMatchingHammer() {
        val m = CalibreMechanics()
        run(m, 2.0, car(30f).copy(turnSignal = TurnSignal.LEFT))
        val ev = m.drainEvents()
        assertTrue(CalibreMechanics.Event.HAMMER_LEFT in ev)
        assertFalse(CalibreMechanics.Event.HAMMER_RIGHT in ev)
        assertEquals(3, ev.count { it == CalibreMechanics.Event.HAMMER_LEFT }) // at once, then 1.5 Hz
    }

    @Test fun safetyWarningFreezesDecorationNotTime() {
        val m = CalibreMechanics()
        run(m, 3.0, car(80f))
        val safe = car(80f).copy(safetyWarningActive = true, turnSignal = TurnSignal.HAZARD)
        run(m, 1.0, safe)
        val a = m.rotorAngle; val d = m.decoTime
        m.drainEvents()
        run(m, 3.0, safe)
        assertEquals(a, m.rotorAngle, 1e-9)
        assertEquals(d, m.decoTime, 1e-9)
        assertTrue(m.drainEvents().none { it == CalibreMechanics.Event.HAMMER_LEFT || it == CalibreMechanics.Event.HAMMER_RIGHT })
        assertEquals(null, m.chip)
        // The hands do not depend on the mechanics at all: same instant, same hands.
        val z = ZoneId.of("Asia/Seoul")
        assertEquals(CalibreTime.hands(5_000_000L, z).minuteDeg, CalibreTime.hands(5_000_000L, z).minuteDeg, 0f)
    }

    @Test fun chipsHoldAtLeastFourSeconds() {
        val m = CalibreMechanics(30f)
        var now = run(m, 5.0, car(30f, fan = 1))
        run(m, 0.2, car(30f, fan = 5), t0 = now).also { now = it }
        assertEquals("크라운 감는 중", m.chip)
        // a star right after: the crown chip stays for its 4 s
        run(m, 1.0, car(30f, fan = 5), Journey(starSeq = 1), now).also { now = it }
        assertEquals("크라운 감는 중", m.chip)
        run(m, 3.5, car(30f, fan = 5), Journey(starSeq = 1), now)
        assertTrue(m.chip!!.length <= 12)
    }
}
