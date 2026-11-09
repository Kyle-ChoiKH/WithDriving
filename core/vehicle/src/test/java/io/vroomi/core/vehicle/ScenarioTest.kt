package io.vroomi.core.vehicle

import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import io.vroomi.core.vehicle.model.VehicleSnapshot
import io.vroomi.core.vehicle.sim.ScenarioParser
import io.vroomi.core.vehicle.sim.ScenarioSampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScenarioTest {
    private val json = """
        { "id": "t", "title": "test", "durationS": 20, "keyframes": [
          { "t": 0,  "gear": "P", "speed": 0, "fan": 1, "note": "ignored" },
          { "t": 10, "gear": "D", "speed": 50, "turn": "LEFT" },
          { "t": 15, "arrive": true },
          { "t": 20, "speed": 0, "fan": 4 }
        ] }
    """.trimIndent()

    @Test fun captionsShowForAWhileThenClear() {
        val s = ScenarioParser.parse(""" { "id": "c", "title": "c", "description": "d", "order": 3, "keyframes": [
            { "t": 0, "speed": 0, "caption": "출발" }, { "t": 5, "brake": true }, { "t": 20, "caption": "도착" } ] } """)
        val sampler = ScenarioSampler(s)
        assertEquals("출발", sampler.captionAt(1f))
        assertEquals("출발", sampler.captionAt(6f))
        assertNull(sampler.captionAt(12f))
        assertEquals("도착", sampler.captionAt(20f))
        assertEquals(3, s.order); assertEquals("d", s.description)
    }

    @Test fun optionalThemeAndPowertrain() {
        val s = ScenarioParser.parse(""" { "id": "e", "title": "e", "theme": "engine", "powertrain": "ev", "keyframes": [ { "t": 0, "speed": 0 } ] } """)
        assertEquals("ENGINE", s.theme); assertEquals("EV", s.powertrain)
        val plain = ScenarioParser.parse(json)
        assertNull(plain.theme); assertNull(plain.powertrain)
    }

    @Test fun parsesAndSorts() {
        val s = ScenarioParser.parse(json)
        assertEquals("t", s.id)
        assertEquals(4, s.keyframes.size)
        assertEquals(20f, s.durationS, 0f)
    }

    @Test fun interpolatesSpeedAndStepsTheRest() {
        val sampler = ScenarioSampler(ScenarioParser.parse(json))
        val base = VehicleSnapshot()
        val a = sampler.sample(5f, base, 5_000, 0)
        assertEquals(25f, a.speedKmh!!, 0.01f)
        assertEquals(GearState.PARK, a.gear)
        assertEquals(1, a.fanLevel)
        assertNull(a.turnSignal)

        val b = sampler.sample(12f, base, 12_000, 0)
        assertEquals(GearState.DRIVE, b.gear)
        assertEquals(TurnSignal.LEFT, b.turnSignal)
        assertEquals(40f, b.speedKmh!!, 0.01f)
        assertNull(b.naviArrivedAtMs)

        val c = sampler.sample(16f, base, 16_000, 1_000)
        assertEquals(16_000L, c.naviArrivedAtMs) // 1_000 start + 15 s
        assertEquals(1, c.fanLevel)

        val d = sampler.sample(25f, base, 25_000, 0)
        assertEquals(0f, d.speedKmh!!, 0.01f)
        assertEquals(4, d.fanLevel)
    }
}
