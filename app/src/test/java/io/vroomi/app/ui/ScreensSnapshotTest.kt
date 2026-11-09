package io.vroomi.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import io.vroomi.app.diary.TripRecord
import io.vroomi.character.Accessory
import io.vroomi.character.Action
import io.vroomi.character.Biome
import io.vroomi.character.Blinker
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Gait
import io.vroomi.character.Journey
import io.vroomi.character.KartState
import io.vroomi.character.SizeMode
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.Season
import io.vroomi.character.render.VroomiSceneStill
import io.vroomi.core.vehicle.sim.Scenario
import org.junit.Rule
import org.junit.Test

/** Whole-screen renders of the app UI over the scene (docs/screenshots). */
class ScreensSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = TestScreen.device,
        maxPercentDifference = 100.0,
    )

    private val day = SceneOptions(hourOfDay = 14f, season = Season.AUTUMN)

    private fun driving(chip: String, kart: KartState = KartState(driving = true), action: Action = Action.NONE) = CharacterTarget(
        gait = Gait.JOG, gaitSpeedKmh = 45f, action = action, actionStartedMs = 1_000_000L, sizeMode = SizeMode.AMBIENT,
        statusChip = chip, kart = kart, journey = Journey(stars = 4, starSeq = 4, combo = 4, biome = Biome.PARK),
    )

    // Snapshots start settled: no entrance animation is waiting for a frame clock.
    private fun screen(night: Boolean = false, content: @Composable () -> Unit) {
        io.vroomi.character.render.theme.ChromeZones.screenH = TestScreen.dpH
        paparazzi.snapshot {
            VroomiTheme(night = night) {
                androidx.compose.runtime.CompositionLocalProvider(io.vroomi.app.ui.motion.LocalMotion provides io.vroomi.app.ui.motion.MotionPrefs(still = true)) { content() }
            }
        }
    }

    @Test fun drivingDemo() = screen {
        val t = driving("부드러운 주행 중", KartState(driving = true, blinker = Blinker.LEFT), Action.POINT_LEFT)
        Box(Modifier.fillMaxSize()) {
            VroomiSceneStill(t, day, modifier = Modifier.fillMaxSize())
            MovingOverlay(t.statusChip, muted = false, calm = false, onMute = {}, onCalm = {},
                demoCaption = "깜빡이 → 꼬마차도 깜빡, 방향 표지판", demoProgress = 0.18f, onEndDemo = {})
        }
    }

    @Test fun drivingReal() = screen {
        val t = driving("별 4개 모았어요")
        Box(Modifier.fillMaxSize()) {
            VroomiSceneStill(t, day, modifier = Modifier.fillMaxSize())
            MovingOverlay(t.statusChip, muted = false, calm = false, onMute = {}, onCalm = {},
                demoCaption = null, demoProgress = null, onEndDemo = {})
        }
    }

    @Test fun welcome() = screen {
        Box(Modifier.fillMaxSize()) {
            VroomiSceneStill(CharacterTarget(action = Action.WAVE_HELLO, actionStartedMs = 1_000_000L), day, modifier = Modifier.fillMaxSize())
            WelcomeCard(onDemo = {}, onStart = {})
        }
    }

    @Test fun tripCard() = screen {
        Box(Modifier.fillMaxSize()) {
            VroomiSceneStill(CharacterTarget(action = Action.WAVE_BYE, actionStartedMs = 1_000_000L), day, modifier = Modifier.fillMaxSize())
            TripSummaryCard(
                TripRecord(1, 0, 1_200_000, 12.4f, 2, 0, 1, 0, 3, 0, 94, stars = 41, bestCombo = 22, biomes = 5),
                Modifier.align(Alignment.CenterEnd).padding(end = 28.dp),
            ) {}
        }
    }

    @Test fun themePickerCalibre() = screen {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            FriendsPicker(selected = io.vroomi.character.CharacterSkin.CHOCHIM, theme = io.vroomi.character.calibre.SceneTheme.CALIBRE,
                finish = io.vroomi.character.calibre.CalibreFinish.AUTO, live = false) {}
        }
    }

    @Test fun drivingCalibre() = drivingCalibre(true)
    @Test fun drivingCalibreReal() = drivingCalibre(false)
    @Test fun drivingCalibreNight() = drivingCalibre(true, night = true)
    private fun drivingCalibre(demo: Boolean, night: Boolean = false) = screen(night) {
        val car = io.vroomi.character.CarSignals(speedKmh = 68f, accelMps2 = 0f, gear = io.vroomi.core.vehicle.model.GearState.DRIVE, moving = true,
            fanLevel = 3, fanMax = 8, setTempC = 22f, safetyWarningActive = false)
        val t = driving("크라운 감는 중").copy(car = car, journey = Journey(active = true, tripSeq = 1, stars = 6, starSeq = 6))
        Box(Modifier.fillMaxSize()) {
            io.vroomi.character.render.calibre.CalibreSceneStill(t, day, io.vroomi.character.calibre.CalibreFinish.AUTO, Modifier.fillMaxSize(), seconds = 75f)
            MovingOverlay(t.statusChip, muted = false, calm = false, onMute = {}, onCalm = {},
                demoCaption = if (demo) "공조 바람을 켜면 → 크라운이 돌며 태엽을 감아요" else null, demoProgress = if (demo) 0.16f else null, onEndDemo = {}, sceneTheme = io.vroomi.character.calibre.SceneTheme.CALIBRE)
        }
    }

    @Test fun themePickerEngine() = screen {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            FriendsPicker(selected = io.vroomi.character.CharacterSkin.PISTON, theme = io.vroomi.character.calibre.SceneTheme.ENGINE,
                powertrain = io.vroomi.character.engine.EnginePowertrain.AUTO, live = false) {}
        }
    }

    @Test fun drivingEngine() = drivingEngine(true)
    @Test fun drivingEngineReal() = drivingEngine(false)
    private fun drivingEngine(demo: Boolean, night: Boolean = false) = screen(night) {
        val car = io.vroomi.character.CarSignals(speedKmh = 64f, accelMps2 = 0.6f, gear = io.vroomi.core.vehicle.model.GearState.DRIVE, moving = true,
            fanLevel = 4, fanMax = 8, acOn = true, setTempC = 21f, drivingMode = io.vroomi.core.vehicle.model.DriveModeState.SPORT,
            safetyWarningActive = false, brakePressed = false)
        val t = driving("에어컨 클러치").copy(car = car, journey = Journey(active = true, tripSeq = 1, stars = 4, starSeq = 4))
        Box(Modifier.fillMaxSize()) {
            io.vroomi.character.render.engine.EngineSceneStill(t, day, io.vroomi.character.engine.EnginePowertrain.ICE, Modifier.fillMaxSize(), seconds = 8f)
            MovingOverlay(t.statusChip, muted = false, calm = false, onMute = {}, onCalm = {},
                demoCaption = if (demo) "에어컨 → 컴프레서 클러치가 딸깍 붙어요" else null, demoProgress = if (demo) 0.24f else null, onEndDemo = {})
        }
    }

    @Test fun themePickerMusicBox() = screen {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            FriendsPicker(selected = io.vroomi.character.CharacterSkin.CHOCHIM, theme = io.vroomi.character.calibre.SceneTheme.MUSIC_BOX, live = false) {}
        }
    }

    @Test fun drivingMusicBox() = drivingMusicBox(true)
    @Test fun drivingMusicBoxReal() = drivingMusicBox(false)
    private fun drivingMusicBox(demo: Boolean, night: Boolean = false) = screen(night) {
        val car = io.vroomi.character.CarSignals(speedKmh = 58f, accelMps2 = 0.2f, gear = io.vroomi.core.vehicle.model.GearState.DRIVE, moving = true,
            fanLevel = 3, fanMax = 8, setTempC = 22f, drivingMode = io.vroomi.core.vehicle.model.DriveModeState.NORMAL,
            safetyWarningActive = false, brakePressed = false, wiperActive = false)
        val t = driving("태엽 감는 중").copy(car = car, journey = Journey(active = true, tripSeq = 2, tripKm = 0.6f, stars = 4, starSeq = 4))
        Box(Modifier.fillMaxSize()) {
            io.vroomi.character.render.theme.ThemeSceneStill(io.vroomi.character.calibre.SceneTheme.MUSIC_BOX, t, day, Modifier.fillMaxSize(), seconds = 9f)
            MovingOverlay("태엽 감는 중", muted = false, calm = false, onMute = {}, onCalm = {},
                demoCaption = if (demo) "핀 실린더가 곧 연주할 악보예요. 4마디 앞까지 미리 꽂혀요" else null, demoProgress = if (demo) 0.2f else null, onEndDemo = {})
        }
    }

    @Test fun themePickerTerrarium() = screen {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            FriendsPicker(selected = io.vroomi.character.CharacterSkin.CHOCHIM, theme = io.vroomi.character.calibre.SceneTheme.TERRARIUM, live = false) {}
        }
    }

    @Test fun drivingTerrarium() = drivingTerrarium(true)
    @Test fun drivingTerrariumReal() = drivingTerrarium(false)
    private fun drivingTerrarium(demo: Boolean, night: Boolean = false) = screen(night) {
        val car = io.vroomi.character.CarSignals(speedKmh = 52f, accelMps2 = 0.1f, gear = io.vroomi.core.vehicle.model.GearState.DRIVE, moving = true,
            fanLevel = 4, fanMax = 8, setTempC = 23f, drivingMode = io.vroomi.core.vehicle.model.DriveModeState.ECO,
            safetyWarningActive = false, brakePressed = false, wiperActive = true)
        val t = driving("병 속에 비").copy(car = car, journey = Journey(active = true, tripSeq = 2, tripKm = 0.4f, stars = 3, starSeq = 3))
        Box(Modifier.fillMaxSize()) {
            io.vroomi.character.render.theme.ThemeSceneStill(io.vroomi.character.calibre.SceneTheme.TERRARIUM, t, day, Modifier.fillMaxSize(), seconds = 8f,
                restore = "g=24,36,16,14,12,16;b=2;u=2;r=2;s=1")
            MovingOverlay("병 속에 비", muted = false, calm = false, onMute = {}, onCalm = {},
                demoCaption = if (demo) "와이퍼 → 병 뚜껑 아래 작은 구름이 비를 내려요" else null, demoProgress = if (demo) 0.55f else null, onEndDemo = {})
        }
    }

    @Test fun themePickerMap() = screen {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            FriendsPicker(selected = io.vroomi.character.CharacterSkin.CHOCHIM, theme = io.vroomi.character.calibre.SceneTheme.TREASURE_MAP, live = false) {}
        }
    }

    @Test fun drivingMap() = drivingMap(true)
    @Test fun drivingMapReal() = drivingMap(false)
    private fun drivingMap(demo: Boolean, night: Boolean = false) = screen(night) {
        io.vroomi.core.vehicle.route.LiveRoute.demoRoute = floatArrayOf(0f, 0f, 400f, 0f, 700f, 150f, 900f, 450f, 1000f, 800f, 1300f, 1000f, 1700f, 1050f)
        val car = io.vroomi.character.CarSignals(speedKmh = 60f, accelMps2 = 0f, gear = io.vroomi.core.vehicle.model.GearState.DRIVE, moving = true,
            fanLevel = 2, fanMax = 8, setTempC = 22f, drivingMode = io.vroomi.core.vehicle.model.DriveModeState.NORMAL,
            safetyWarningActive = false, brakePressed = false)
        val t = driving("지도를 그려요").copy(car = car, journey = Journey(active = true, tripSeq = 3, tripKm = 1.9f, stars = 2, starSeq = 2, milestoneKm = 1, biome = Biome.PARK))
        Box(Modifier.fillMaxSize()) {
            io.vroomi.character.render.theme.ThemeSceneStill(io.vroomi.character.calibre.SceneTheme.TREASURE_MAP, t, day, Modifier.fillMaxSize(), seconds = 4f)
            MovingOverlay("지도를 그려요", muted = false, calm = false, onMute = {}, onCalm = {},
                demoCaption = if (demo) "지나온 길만 잉크로 그려요. 앞으로 갈 길은 그리지 않아요" else null, demoProgress = if (demo) 0.3f else null, onEndDemo = {})
        }
    }

    @Test fun themePickerAquarium() = screen {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            FriendsPicker(selected = io.vroomi.character.CharacterSkin.CHOCHIM, theme = io.vroomi.character.calibre.SceneTheme.AQUARIUM, live = false) {}
        }
    }

    @Test fun drivingAquarium() = drivingAquarium(true)
    @Test fun drivingAquariumReal() = drivingAquarium(false)
    private fun drivingAquarium(demo: Boolean, night: Boolean = false) = screen(night) {
        val car = io.vroomi.character.CarSignals(speedKmh = 55f, accelMps2 = 0.8f, gear = io.vroomi.core.vehicle.model.GearState.DRIVE, moving = true,
            fanLevel = 5, fanMax = 8, setTempC = 22f, drivingMode = io.vroomi.core.vehicle.model.DriveModeState.NORMAL,
            safetyWarningActive = false, brakePressed = false)
        val t = driving("함께 헤엄").copy(car = car, journey = Journey(active = true, tripSeq = 2, tripKm = 0.5f, stars = 3, starSeq = 3))
        Box(Modifier.fillMaxSize()) {
            io.vroomi.character.render.theme.ThemeSceneStill(io.vroomi.character.calibre.SceneTheme.AQUARIUM, t, day, Modifier.fillMaxSize(), seconds = 10f)
            MovingOverlay("함께 헤엄", muted = false, calm = false, onMute = {}, onCalm = {},
                demoCaption = if (demo) "가속하면 수면과 수초가 뒤로 기울어요" else null, demoProgress = if (demo) 0.15f else null, onEndDemo = {})
        }
    }

    @Test fun themePickerOrbit() = screen {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            FriendsPicker(selected = io.vroomi.character.CharacterSkin.CHOCHIM, theme = io.vroomi.character.calibre.SceneTheme.ORBIT, live = false) {}
        }
    }

    @Test fun drivingOrbit() = drivingOrbit(true)
    @Test fun drivingOrbitReal() = drivingOrbit(false)
    @Test fun drivingOrbitNight() = drivingOrbit(true, night = true)
    private fun drivingOrbit(demo: Boolean, night: Boolean = false) = screen(night) {
        val car = io.vroomi.character.CarSignals(speedKmh = 60f, accelMps2 = 0.9f, gear = io.vroomi.core.vehicle.model.GearState.DRIVE, moving = true,
            fanLevel = 2, fanMax = 8, setTempC = 22f, drivingMode = io.vroomi.core.vehicle.model.DriveModeState.ECO,
            safetyWarningActive = false, brakePressed = false, evBatteryPct = 68)
        val t = driving("궤도 비행").copy(car = car, journey = Journey(active = true, tripSeq = 2, tripKm = 0.4f, stars = 3, starSeq = 3))
        Box(Modifier.fillMaxSize()) {
            io.vroomi.character.render.theme.ThemeSceneStill(io.vroomi.character.calibre.SceneTheme.ORBIT, t, day, Modifier.fillMaxSize(), seconds = 10f)
            MovingOverlay("궤도 비행", muted = false, calm = false, onMute = {}, onCalm = {},
                demoCaption = if (demo) "에코 모드 → 푸른 이온 불꽃. 별을 받으면 작은 위성이 생겨요" else null, demoProgress = if (demo) 0.3f else null, onEndDemo = {}, sceneTheme = io.vroomi.character.calibre.SceneTheme.ORBIT)
        }
    }

    @Test fun themePickerPaper() = screen {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            FriendsPicker(selected = io.vroomi.character.CharacterSkin.CHOCHIM, theme = io.vroomi.character.calibre.SceneTheme.PAPER, live = false) {}
        }
    }

    @Test fun drivingPaper() = drivingPaper(true)
    @Test fun drivingPaperReal() = drivingPaper(false)
    private fun drivingPaper(demo: Boolean, night: Boolean = false) = screen(night) {
        val car = io.vroomi.character.CarSignals(speedKmh = 55f, accelMps2 = 0.2f, gear = io.vroomi.core.vehicle.model.GearState.DRIVE, moving = true,
            fanLevel = 5, fanMax = 8, setTempC = 22f, drivingMode = io.vroomi.core.vehicle.model.DriveModeState.NORMAL,
            safetyWarningActive = false, brakePressed = false)
        val t = driving("책을 펼쳐요").copy(car = car, journey = Journey(active = true, tripSeq = 2, tripKm = 0.4f, stars = 2, starSeq = 2, biome = Biome.PARK))
        Box(Modifier.fillMaxSize()) {
            io.vroomi.character.render.theme.ThemeSceneStill(io.vroomi.character.calibre.SceneTheme.PAPER, t, day.copy(skin = io.vroomi.character.CharacterSkin.CHOCHIM), Modifier.fillMaxSize(), seconds = 6f)
            MovingOverlay("책을 펼쳐요", muted = false, calm = false, onMute = {}, onCalm = {},
                demoCaption = if (demo) "숨은 종이 띠를 따라 풍경이 미끄러져요" else null, demoProgress = if (demo) 0.2f else null, onEndDemo = {})
        }
    }

    @Test fun themePickerPixel() = screen {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            // Scrolled to the "놀이" row: the Pixel World card and its options.
            FriendsPicker(selected = io.vroomi.character.CharacterSkin.CHOCHIM, theme = io.vroomi.character.calibre.SceneTheme.PIXEL, live = false, initialScroll = 1110) {}
        }
    }

    @Test fun drivingPixel() = drivingPixel(true)
    @Test fun drivingPixelReal() = drivingPixel(false)
    private fun drivingPixel(demo: Boolean, night: Boolean = false) = screen(night) {
        val car = io.vroomi.character.CarSignals(speedKmh = 55f, accelMps2 = 0.6f, gear = io.vroomi.core.vehicle.model.GearState.DRIVE, moving = true,
            fanLevel = 4, fanMax = 8, setTempC = 22f, drivingMode = io.vroomi.core.vehicle.model.DriveModeState.NORMAL,
            safetyWarningActive = false, brakePressed = false)
        val t = driving("도트 월드").copy(car = car, journey = Journey(active = true, tripSeq = 2, tripKm = 0.4f, stars = 5, starSeq = 5, biome = Biome.PARK))
        Box(Modifier.fillMaxSize()) {
            io.vroomi.character.render.pixel.PixelSceneStill(t, day.copy(skin = io.vroomi.character.CharacterSkin.CHOCHIM), Modifier.fillMaxSize(), seconds = 5f)
            MovingOverlay("도트 월드", muted = false, calm = false, onMute = {}, onCalm = {},
                demoCaption = if (demo) "차속만큼 도트 세계가 흘러요. 100km/h면 1초에 240픽셀" else null, demoProgress = if (demo) 0.2f else null, onEndDemo = {})
        }
    }

    @Test fun friendsPicker() = screen {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) { FriendsPicker(selected = io.vroomi.character.CharacterSkin.CHOCHIM) {} }
    }

    @Test fun demoPicker() = screen {
        Box(Modifier.fillMaxSize()) {
            VroomiSceneStill(CharacterTarget(sizeMode = SizeMode.FULL, accessories = setOf(Accessory.SCARF)), day, modifier = Modifier.fillMaxSize())
            DemoPicker(
                listOf(
                    Scenario("highlight", "브루미 하이라이트 (3분)", 172f, emptyList(), "출발부터 도착까지, 브루미가 반응하는 모든 순간을 한 번에"),
                    Scenario("commute", "출근길 (3분)", 182f, emptyList(), "도시 출퇴근: 신호 대기, 깜빡이, 에어컨, 급제동, 고속도로"),
                    Scenario("rainy_night", "비 오는 밤 퇴근길 (2분)", 122f, emptyList(), "전조등, 비, 서리 제거, 길안내, 도착 축하"),
                ),
                onPick = {}, onClose = {},
            )
        }
    }
}
