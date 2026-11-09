package io.vroomi.character.paper

import io.vroomi.character.Action
import io.vroomi.character.CarSignals
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.KartState
import io.vroomi.core.vehicle.model.GearState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PaperTest {

    private class Book(val sim: PaperSim = PaperSim()) {
        var now = 1_000_000L
        fun run(seconds: Float, kmh: Float, block: (CarSignals) -> CarSignals = { it }, action: Action = Action.NONE, each: () -> Unit = {}) {
            repeat((seconds * 60).toInt()) {
                val c = block(CarSignals(speedKmh = kmh, gear = if (kmh > 0) GearState.DRIVE else GearState.PARK, moving = kmh > 0.5f))
                sim.step(1f / 60f, CharacterTarget(action = action, kart = KartState(driving = kmh > 0.5f), car = c, journey = Journey(active = true, tripSeq = 1)), now, 14f)
                now += 1000L / 60
                each()
            }
        }
    }

    @Test fun pagesTurnOnlyWhileParked() {
        val b = Book()
        b.run(2f, 50f)
        assertTrue("no page turn while driving", !b.sim.requestFlip())
        b.run(3f, 0f)
        assertTrue(b.sim.requestFlip())
        assertTrue("one turn at a time", !b.sim.requestFlip())
        b.run(1.3f, 0f)
        assertEquals(1, b.sim.spread)
    }

    @Test fun jointsStayWithinTheirLimits() {
        val b = Book()
        var maxAngle = 0f
        b.run(5f, 60f, action = Action.CELEBRATE) { maxAngle = maxOf(maxAngle, abs(b.sim.hingeAngle)) }
        b.run(3f, 30f, block = { it.copy(harshBrakeSeq = 1, accelMps2 = -8f) }) { maxAngle = maxOf(maxAngle, abs(b.sim.hingeAngle)) }
        assertTrue(maxAngle > 3f)
        assertTrue("joint $maxAngle°", maxAngle <= PaperSim.MAX_JOINT)
    }

    @Test fun arrivalClosesTheBookAndDrivingOpensIt() {
        val b = Book()
        b.run(2f, 40f)
        b.run(4f, 0f, block = { it.copy(arrivalSeq = 1) })
        assertTrue(b.sim.closed > 0.95f)
        b.run(4f, 40f, block = { it.copy(arrivalSeq = 1) })
        assertTrue(b.sim.closed < 0.05f)
    }
}
