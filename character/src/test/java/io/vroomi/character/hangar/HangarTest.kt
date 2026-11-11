package io.vroomi.character.hangar

import io.vroomi.character.CarSignals
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.KartState
import io.vroomi.character.theme.ThemeSim
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HangarTest {

    private fun car(kmh: Float, turn: TurnSignal = TurnSignal.NONE, brake: Boolean = false, harsh: Int = 0) =
        CarSignals(speedKmh = kmh, accelMps2 = 0f, gear = GearState.DRIVE, moving = kmh > 0.5f, turnSignal = turn, brakePressed = brake,
            harshBrakeSeq = harsh, safetyWarningActive = false, fanLevel = 2, fanMax = 8)

    private var clock = 1_000_000L

    /** Drives [km] more at [kmh] on [s] (a trip that goes on from [fromKm]). */
    private fun driveOn(s: HangarSim, km: Float, kmh: Float = 60f, fromKm: Float = 0f) {
        val frames = (km / kmh * 3600f * 60f).toInt()
        for (i in 0..frames) {
            val at = fromKm + km * i / frames
            s.step(1f / 60f, CharacterTarget(kart = KartState(driving = true), car = car(kmh), journey = Journey(active = true, tripSeq = 1, tripKm = at, milestoneKm = at.toInt())), clock, 14f)
            clock += 16
        }
    }

    private fun drive(km: Float, kmh: Float = 60f): HangarSim = HangarSim().also { driveOn(it, km, kmh) }

    private fun parseP(state: String): Float = state.substringAfter("p=").substringBefore(';').toFloat()

    @Test fun thirtyKmCompletesOneUnit() {
        val s = drive(km = 30f, kmh = 60f)
        assertEquals(1f, s.progress, 0.01f)
        assertEquals(5, s.stage)
        assertEquals(1, s.unit)
    }

    @Test fun speedDoesNotRushAssembly() {
        assertEquals(drive(km = 3f, kmh = 40f).progress, drive(km = 3f, kmh = 160f).progress, 0.005f)
        assertEquals(0.1f, drive(km = 3f).progress, 0.005f)
    }

    @Test fun stagesFollowTheOrder() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5), listOf(0.1f, 0.3f, 0.6f, 0.8f, 0.95f, 1f).map { HangarSim.stageOf(it) })
        val s = drive(km = 15f) // half way: the armour stage, a fifth of the way through it
        assertEquals(2, s.stage)
        assertEquals(0.2f, s.stageProgress, 0.03f)
    }

    @Test fun persistRestoreRoundTrip() {
        val a = drive(km = 7f)
        val b = HangarSim().apply { restore(a.persist()) }
        assertEquals(a.progress, b.progress, 1e-3f)
        assertEquals(a.unit, b.unit)
    }

    @Test fun demoDoesNotAdvanceSavedAssembly() {
        val s = HangarSim().apply { restore("p=0.2;u=1"); demo = true; startDemo() }
        assertEquals(0.6f, s.progress, 1e-3f)
        driveOn(s, km = 5f)
        assertEquals(0.2f, parseP(s.persist()), 1e-3f)
        assertEquals(5, s.stage)           // the showcase reaches the finished robot
        assertEquals(1, s.unit)            // and does not start another
        s.demo = false
        assertEquals(0.2f, s.progress, 1e-3f) // back to the real assembly
    }

    @Test fun nextUnitAfterComplete() {
        val s = drive(km = 31f)
        assertEquals(2, s.unit)
        assertTrue(s.progress < 0.05f)
        assertTrue(s.progress > 0f)
    }

    @Test fun leftBlinkerTurnsHeadLeft() {
        fun run(turn: TurnSignal): HangarSim = HangarSim().also { s ->
            for (i in 0 until 300) s.step(1f / 60f, CharacterTarget(kart = KartState(driving = true), car = car(40f, turn), journey = Journey(active = true, tripSeq = 1, tripKm = i * 0.0001f)), 1_000_000L + i * 16L, 14f)
        }
        assertTrue(run(TurnSignal.LEFT).headTurn < -0.3f)
        assertTrue(run(TurnSignal.RIGHT).headTurn > 0.3f)
        assertEquals(0f, run(TurnSignal.NONE).headTurn, 0.001f)
    }

    @Test fun brakeDipsKneesAndHarshBrakingClosesTheClamps() {
        val s = HangarSim()
        fun run(frames: Int, brake: Boolean, harsh: Int) {
            for (i in 0 until frames) { s.step(1f / 60f, CharacterTarget(kart = KartState(driving = true), car = car(50f, brake = brake, harsh = harsh), journey = Journey(active = true, tripSeq = 1)), clock, 14f); clock += 16 }
        }
        run(60, brake = false, harsh = 0)
        assertEquals(0f, s.kneeDip, 0.001f); assertEquals(0f, s.clamp, 0.001f)
        run(90, brake = true, harsh = 1)
        assertTrue(s.kneeDip > 0.9f); assertTrue(s.clamp > 0.9f)
        run(60 * 8, brake = false, harsh = 1)
        assertEquals(0f, s.clamp, 0.001f) // the clamps open again after a few seconds
    }

    @Test fun chipsAreHonest() {
        val s = HangarSim()
        assertEquals(listOf("조립 중", "장갑을 달아요", "도색 중", "완성!", "동력로 가동", "정비 대기"), s.allChips)
        for (c in s.allChips) for (w in ThemeSim.FORBIDDEN_WORDS) assertTrue("$c / $w", !c.contains(w))
    }
}
