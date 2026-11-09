package io.vroomi.character

import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import io.vroomi.core.vehicle.model.VehicleSnapshot
import io.vroomi.core.vehicle.signal.DrivingEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterDirectorTest {
    private val drive = VehicleSnapshot(speedKmh = 40f, gear = GearState.DRIVE, fanLevel = 1, fanLevelRange = 0..8,
        setTempC = 23f, brakePressed = false, turnSignal = TurnSignal.NONE)

    private fun CharacterDirector.at(s: VehicleSnapshot, t: Long, moving: Boolean = (s.speedKmh ?: 0f) > 3f) =
        update(s, moving, userCorner = false, wardrobe = emptySet(), nowMs = t)

    @Test fun gaitBandsWithHysteresis() {
        assertEquals(Gait.JOG, CharacterDirector.nextGait(Gait.IDLE, 25f))
        assertEquals(Gait.JOG, CharacterDirector.nextGait(Gait.JOG, 18f))   // falls only below 17
        assertEquals(Gait.WALK, CharacterDirector.nextGait(Gait.JOG, 16f))
        assertEquals(Gait.JOG, CharacterDirector.nextGait(Gait.JOG, 62f))   // rises only at 63
        assertEquals(Gait.RUN, CharacterDirector.nextGait(Gait.JOG, 63f))
        assertEquals(Gait.SPRINT, CharacterDirector.nextGait(Gait.IDLE, 140f))
        assertEquals(Gait.IDLE, CharacterDirector.nextGait(Gait.WALK, 0f))
    }

    @Test fun dwellTimePreventsFlicker() {
        val d = CharacterDirector()
        assertEquals(Gait.JOG, d.at(drive.copy(speedKmh = 30f), 0).gait)
        assertEquals(Gait.JOG, d.at(drive.copy(speedKmh = 70f), 100).gait)
        assertEquals(Gait.RUN, d.at(drive.copy(speedKmh = 70f), 700).gait)
    }

    @Test fun speedingIsNeverRewarded() {
        val d = CharacterDirector()
        val t = d.at(drive.copy(speedKmh = 160f), 0)
        assertEquals(110f, t.gaitSpeedKmh, 0.01f)
    }

    @Test fun brakePedalWhileRollingSkids() {
        val d = CharacterDirector()
        d.at(drive, 0)
        assertEquals(Action.SKID, d.at(drive.copy(brakePressed = true), 100).action)
    }

    @Test fun harshBrakeTumblesAndIsDizzy() {
        val d = CharacterDirector()
        d.at(drive, 0)
        d.drainVoiceEvents()
        d.onDrivingEvent(DrivingEvent.HARSH_BRAKE, 100)
        val t = d.at(drive, 150)
        assertEquals(Action.TUMBLE, t.action)
        assertEquals(Mood.DIZZY, t.mood)
        assertTrue(VroomiEvent.HARSH_BRAKE in d.drainVoiceEvents())
        assertEquals(Mood.NEUTRAL, d.at(drive, 2_500).mood)
    }

    @Test fun fanUpBlowsAndWindScales() {
        val d = CharacterDirector()
        d.at(drive, 0); d.drainVoiceEvents()
        val t = d.at(drive.copy(fanLevel = 6), 100)
        assertEquals(Action.BLOW, t.action)
        assertEquals(0.75f, t.overlays.windLevel, 0.01f)
        assertTrue(VroomiEvent.FAN_UP in d.drainVoiceEvents())
    }

    @Test fun temperatureMoods() {
        val d = CharacterDirector()
        val cold = d.at(drive.copy(setTempC = 20f), 0)
        assertEquals(Mood.COLD, cold.mood)
        assertTrue(cold.overlays.snow)
        val warm = CharacterDirector().at(drive.copy(setTempC = 27f), 0)
        assertEquals(Mood.WARM, warm.mood)
        assertTrue(Accessory.SCARF in warm.accessories)
    }

    @Test fun turnSignalPointsAndHazardHides() {
        val d = CharacterDirector()
        assertEquals(Action.POINT_LEFT, d.at(drive.copy(turnSignal = TurnSignal.LEFT), 0).action)
        assertEquals(Action.POINT_RIGHT, d.at(drive.copy(turnSignal = TurnSignal.RIGHT), 1_000).action)
        val hz = d.at(drive.copy(turnSignal = TurnSignal.HAZARD), 2_000)
        assertEquals(Action.HAZARD_WAVE, hz.action)
        assertEquals(SizeMode.CORNER, hz.sizeMode)
    }

    @Test fun safetyWarningWinsOverEverything() {
        val d = CharacterDirector()
        d.at(drive, 0)
        d.onDrivingEvent(DrivingEvent.BURST, 50)
        val t = d.at(drive.copy(safetyWarningActive = true, turnSignal = TurnSignal.LEFT), 100)
        assertEquals(SizeMode.CORNER, t.sizeMode)
        assertTrue(t.still)
        assertEquals(Action.NONE, t.action)
        assertNull(t.statusChip)
    }

    @Test fun statusChipIsShortAndOnlyWhileMoving() {
        val d = CharacterDirector()
        val states = listOf(
            drive, drive.copy(fanLevel = 7), drive.copy(wiperActive = true), drive.copy(cruiseOn = true),
            drive.copy(naviGuidanceActive = true, naviRemainingM = 500),
        )
        states.forEachIndexed { i, s ->
            val chip = d.at(s, i * 1_000L).statusChip
            assertTrue("chip '$chip'", chip != null && chip.length <= 12)
        }
        assertNull(CharacterDirector().at(drive.copy(speedKmh = 0f), 0, moving = false).statusChip)
    }

    @Test fun arrivalCelebratesOnce() {
        val d = CharacterDirector()
        d.at(drive, 0); d.drainVoiceEvents()
        val s = drive.copy(speedKmh = 0f, naviArrivedAtMs = 500)
        assertEquals(Action.CELEBRATE, d.at(s, 600, moving = false).action)
        assertEquals(1, d.drainVoiceEvents().count { it == VroomiEvent.ARRIVAL })
        d.at(s, 700, moving = false)
        assertFalse(VroomiEvent.ARRIVAL in d.drainVoiceEvents())
    }

    @Test fun stretchesAfterAMinuteAtARedLightButNapsOnlyWhenParked() {
        val d = CharacterDirector()
        val stopped = drive.copy(speedKmh = 0f)
        d.at(drive, 0)
        d.at(stopped, 1_000, moving = true)
        assertEquals(Action.STRETCH, d.at(stopped, 62_000, moving = true).action)
        assertTrue(VroomiEvent.LONG_STOP in d.drainVoiceEvents())
        assertTrue(d.at(stopped, 400_000, moving = true).action != Action.NAP) // still in D: no nap
        val parked = stopped.copy(gear = GearState.PARK)
        assertEquals(Action.NAP, d.at(parked, 401_000, moving = false).action)
    }

    @Test fun contextualAccessories() {
        val t = CharacterDirector().at(drive.copy(wiperActive = true, headlightsOn = true, cruiseOn = true, naviGuidanceActive = true), 0)
        assertTrue(Accessory.RAINCOAT in t.accessories)
        assertTrue(Accessory.LANTERN in t.accessories)
        assertTrue(Accessory.SUNGLASSES in t.accessories)
        assertTrue(Accessory.MAP_HAT in t.accessories)
        assertTrue(t.overlays.night)
    }
}
