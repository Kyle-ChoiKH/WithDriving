package io.vroomi.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.vroomi.app.R
import io.vroomi.app.runtime.VroomiRuntime
import io.vroomi.core.vehicle.model.Capability
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import io.vroomi.core.vehicle.model.VehicleSnapshot
import kotlin.math.roundToInt

/**
 * Hidden developer panel (5 taps on the version label, parked only):
 * active source, speed source, capability map, live values, scenario player and manual controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugPanel(rt: VroomiRuntime) {
    val snap by rt.hub.snapshot.collectAsStateWithLifecycle()
    val caps by rt.hub.capabilities.collectAsStateWithLifecycle()
    val active by rt.hub.active.collectAsStateWithLifecycle()
    val speedSrc by rt.hub.speedSource.collectAsStateWithLifecycle()
    val playing by rt.simulator.playing.collectAsStateWithLifecycle()
    val progress by rt.simulator.progress.collectAsStateWithLifecycle()
    val sim = rt.simulator
    val scenarios = remember { sim.scenarioIds() }

    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.debug_title), style = MaterialTheme.typography.titleLarge)
            Mono("source=$active  speedSource=$speedSrc  (sim scenario: ${playing?.id ?: "-"})")
            Mono(liveLine(snap))
            val st by rt.settingsStore.settings.collectAsStateWithLifecycle()
            // Calibre: hidden options (audio focus on Pleos not verified yet; frame time in debug builds only)
            SettingSwitch(stringResource(R.string.debug_mech_sound), st.mechSound) { on -> rt.settingsStore.update { it.copy(mechSound = on) } }
            if (io.vroomi.app.BuildConfig.DEBUG) SettingSwitch(stringResource(R.string.debug_frame_time), st.devFrameTime) { on -> rt.settingsStore.update { it.copy(devFrameTime = on) } }
            if (io.vroomi.app.BuildConfig.DEBUG) SettingSwitch(stringResource(R.string.debug_slow_motion), st.devSlowMotion) { on -> rt.settingsStore.update { it.copy(devSlowMotion = on) } }
            // Every grade (and so every theme's top step) for a preview; never stored, the real grade is untouched.
            if (io.vroomi.app.BuildConfig.DEBUG) SettingSwitch(stringResource(R.string.dev_all_grades), st.devAllGrades) { on -> rt.settingsStore.update { it.copy(devAllGrades = on) } }
            Mono("capabilities: " + caps.available.sortedBy { it.ordinal }.joinToString { it.name.lowercase() })
            if (caps.unavailable.isNotEmpty()) Mono("missing: " + caps.unavailable.sortedBy { it.ordinal }.joinToString { it.name.lowercase() })
            Mono("HVAC control: ${if (rt.hub.canControlClimate()) "allowed" else "not approved (read-only)"}")

            Text("시나리오", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                scenarios.forEach { id -> BigButton(id) { sim.loadScenario(id)?.let { sim.play(it) } } }
                if (playing != null) BigButton("정지", danger = true) { sim.stopScenario() }
            }
            if (playing != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())

            Text("수동 조작 (시뮬레이터)", style = MaterialTheme.typography.titleMedium)
            LabeledSlider("속도 ${snap.speedKmh?.roundToInt() ?: 0} km/h", (sim.snapshot.value.speedKmh ?: 0f), 0f..140f) { v ->
                sim.setManual { it.copy(speedKmh = v, gear = if (v > 0.5f) GearState.DRIVE else it.gear) }
            }
            LabeledSlider("풍량 ${snap.fanLevel ?: "-"}", (sim.snapshot.value.fanLevel ?: 0).toFloat(), 0f..8f, steps = 7) { v ->
                sim.setManual { it.copy(fanLevel = v.roundToInt()) }
            }
            LabeledSlider("설정 온도 ${snap.setTempC?.let { "%.1f".format(it) } ?: "-"}°C", sim.snapshot.value.setTempC ?: 23f, 16f..30f) { v ->
                sim.setManual { it.copy(setTempC = (v * 2).roundToInt() / 2f) }
            }
            LabeledSlider("남은 거리 ${snap.naviRemainingM ?: "-"} m", (sim.snapshot.value.naviRemainingM ?: 5000).toFloat(), 0f..5000f) { v ->
                sim.setManual { it.copy(naviRemainingM = v.roundToInt()) }
            }
            LabeledSlider("배터리 ${snap.evBatteryPct ?: "-"}%", (sim.snapshot.value.evBatteryPct ?: 70).toFloat(), 0f..100f) { v ->
                sim.setManual { it.copy(evBatteryPct = v.roundToInt()) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Toggle("브레이크", snap.brakePressed == true) { on -> sim.setManual { it.copy(brakePressed = on) } }
                Toggle("P단", snap.gear == GearState.PARK) { on -> sim.setManual { it.copy(gear = if (on) GearState.PARK else GearState.DRIVE) } }
                listOf(TurnSignal.NONE to "깜빡이 끔", TurnSignal.LEFT to "좌", TurnSignal.RIGHT to "우", TurnSignal.HAZARD to "비상등").forEach { (t, l) ->
                    Toggle(l, snap.turnSignal == t) { sim.setManual { it.copy(turnSignal = t) } }
                }
                Toggle("에어컨", snap.acOn == true) { on -> sim.setManual { it.copy(acOn = on) } }
                Toggle("최대 냉방", snap.maxAcOn == true) { on -> sim.setManual { it.copy(maxAcOn = on) } }
                Toggle("앞유리 서리", snap.defrostOn == true) { on -> sim.setManual { it.copy(defrostOn = on) } }
                Toggle("공기청정", snap.airCleanerOn == true) { on -> sim.setManual { it.copy(airCleanerOn = on) } }
                Toggle("내기순환", snap.recircOn == true) { on -> sim.setManual { it.copy(recircOn = on) } }
                Toggle("공조 AUTO", snap.hvacAutoOn == true) { on -> sim.setManual { it.copy(hvacAutoOn = on) } }
                Toggle("열선", (snap.seatHeatLevel ?: 0) > 0) { on -> sim.setManual { it.copy(seatHeatLevel = if (on) 2 else 0) } }
                Toggle("와이퍼", snap.wiperActive == true) { on -> sim.setManual { it.copy(wiperActive = on) } }
                Toggle("전조등", snap.headlightsOn == true) { on -> sim.setManual { it.copy(headlightsOn = on) } }
                Toggle("문 열림", snap.doorOpen == true) { on -> sim.setManual { it.copy(doorOpen = on) } }
                Toggle("충전 중", snap.evCharging == true) { on -> sim.setManual { it.copy(evCharging = on) } }
                Toggle("크루즈", snap.cruiseOn == true) { on -> sim.setManual { it.copy(cruiseOn = on) } }
                Toggle("길안내", snap.naviGuidanceActive == true) { on -> sim.setManual { it.copy(naviGuidanceActive = on) } }
                Toggle("안전 경고", snap.safetyWarningActive == true) { on -> sim.setManual { it.copy(safetyWarningActive = on) } }
                BigButton("도착!") { sim.setManual { it.copy(naviArrivedAtMs = System.currentTimeMillis(), naviGuidanceActive = false) } }
            }

            Text("음성 명령 테스트", style = MaterialTheme.typography.titleMedium)
            var cmd by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(cmd, { cmd = it }, Modifier.weight(1f), singleLine = true, label = { Text("예: 시원하게 해줘") })
                Spacer(Modifier.width(12.dp))
                BigButton("보내기") { if (cmd.isNotBlank()) { rt.command(cmd); cmd = "" } }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun liveLine(s: VehicleSnapshot): String =
    "v=${s.speedKmh?.let { "%.1f".format(it) }} a=${s.accelMps2?.let { "%.2f".format(it) }} brake=${s.brakePressed} gear=${s.gear} " +
        "fan=${s.fanLevel} set=${s.setTempC} ac=${s.acOn} turn=${s.turnSignal} wiper=${s.wiperActive} " +
        "lights=${s.headlightsOn} door=${s.doorOpen} ev=${s.evBatteryPct}%/${s.evCharging} navi=${s.naviGuidanceActive}/${s.naviRemainingM} " +
        "safety=${s.safetyWarningActive} odo=${s.odometerKm?.let { "%.2f".format(it) }}"

@Composable
private fun Mono(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    BigButton(if (on) "● $label" else label, danger = false) { onChange(!on) }
}
