package io.vroomi.app.demo

import io.vroomi.character.calibre.SceneTheme
import io.vroomi.core.vehicle.sim.Keyframe
import io.vroomi.core.vehicle.sim.Scenario

/** Something the driver does with the car; [action] names it in a caption ("왼쪽 깜빡이 → ..."). */
enum class Cue(val action: String) {
    START("출발"), ACCEL("가속"), STAR("부드럽게 달리기"), BRAKE("브레이크"), HARSH("급제동"), STOP("신호 대기"),
    REVERSE("후진"), NAVI_NEAR("목적지 근처"), ARRIVE("도착"), CHARGE("충전"),
    FAN("바람 세게"), COOL("에어컨 켜기"), WARM("온도 올리기"),
    TURN_LEFT("왼쪽 깜빡이"), TURN_RIGHT("오른쪽 깜빡이"), LIGHTS("전조등"), WIPER("와이퍼"), DEFROST("서리 제거"),
    ECO("에코 모드"), SPORT("스포츠 모드"), CRUISE("크루즈"),
}

/** A short demo about one kind of control: its cues play in this order, each with the theme's own reaction. */
enum class Lesson(val title: String, val cues: List<Cue>) {
    DRIVE("출발부터 도착까지", listOf(Cue.START, Cue.ACCEL, Cue.STAR, Cue.BRAKE, Cue.HARSH, Cue.STOP, Cue.REVERSE, Cue.NAVI_NEAR, Cue.ARRIVE, Cue.CHARGE)),
    CLIMATE("온도와 바람", listOf(Cue.FAN, Cue.COOL, Cue.WARM)),
    LIGHTS("깜빡이 · 전조등 · 와이퍼", listOf(Cue.TURN_LEFT, Cue.TURN_RIGHT, Cue.LIGHTS, Cue.WIPER, Cue.DEFROST)),
    MODES("주행 모드", listOf(Cue.ECO, Cue.SPORT, Cue.CRUISE)),
}

/**
 * What each theme does when the driver does something: the one table behind every demo caption that is not written
 * by hand. A theme has an entry only for what its scene really shows (each line restates what the theme's showcase or
 * its simulation documents); a cue without an entry is left out of that theme's demos instead of being described.
 */
object ThemeReactions {
    private fun turns(left: String, right: String = left.replace("왼쪽", "오른쪽")) = arrayOf(Cue.TURN_LEFT to left, Cue.TURN_RIGHT to right)

    private val TABLE: Map<SceneTheme, Map<Cue, String>> = mapOf(
        SceneTheme.KART to mapOf(
            Cue.START to "브루미가 꼬마차에 폴짝 올라타 같이 부웅!",
            Cue.ACCEL to "전기 불꽃 슝, 속도선이 생겨요",
            Cue.STAR to "별이 날아와요 (250m마다)",
            Cue.BRAKE to "브레이크등, 몸이 앞으로 쏠려요",
            Cue.HARSH to "꾸중 대신 \"어지러워요~\"",
            Cue.STOP to "브루미도 같이 기다리며 장난쳐요",
            Cue.NAVI_NEAR to "목적지 깃발, 앞을 살펴요",
            Cue.ARRIVE to "결승선, 축하 점프!",
            Cue.CHARGE to "케이블 연결, 에너지 냠냠",
            Cue.FAN to "브루미가 후~ 바람을 불어요",
            Cue.COOL to "입김, 눈송이, 목도리",
            *turns("꼬마차도 깜빡, 방향 표지판", "꼬마차도 깜빡, 방향 표지판"),
            Cue.LIGHTS to "밤하늘, 별, 랜턴",
            Cue.WIPER to "우비, 와이퍼, 빗길",
            Cue.DEFROST to "브루미가 앞유리를 쓱쓱 닦아요",
            Cue.ECO to "초록 꼬마차, 새싹",
            Cue.SPORT to "빨간 꼬마차, 고글",
            Cue.CRUISE to "선글라스 끼고 여유롭게",
        ),
        SceneTheme.PAPER to mapOf(
            Cue.START to "크라프트지 길 띠가 흐르고 종이 풍경이 미끄러져요",
            Cue.ACCEL to "당김 탭이 움직여 종이 해 바퀴를 돌려요",
            Cue.STAR to "여백에서 색종이 동그라미가 날아와요",
            Cue.BRAKE to "종이 먼지 동그라미가 퐁, 종이학은 고개를 숙여요",
            Cue.HARSH to "종이 자동차가 한 번 기울고 종이학이 흔들려요",
            Cue.ARRIVE to "책이 살며시 덮여요",
            Cue.FAN to "들판의 종이 바람개비가 빨리 돌아요",
            Cue.COOL to "실에 매단 종이 눈송이",
            *turns("종이 화살표가 펼쳐지고 종이학이 날개를 펴요", "종이 화살표가 펼쳐지고 종이학이 날개를 펴요"),
            Cue.LIGHTS to "노란 셀로판 불빛, 실에 매단 종이 달",
            Cue.WIPER to "실에 매단 종이 빗방울이 흔들려요",
        ),
        SceneTheme.CALIBRE to mapOf(
            Cue.START to "크로노그래프 시작, 칼럼휠이 한 칸",
            Cue.ACCEL to "로터가 빨라지며 태엽을 감아요",
            Cue.STAR to "별 하나에 보석 하나가 반짝",
            Cue.HARSH to "충격 방지 스프링이 툭, 로터가 출렁",
            Cue.STOP to "로터가 흔들리다 가라앉아요",
            Cue.REVERSE to "로터가 반대로 돌아요",
            Cue.ARRIVE to "해머가 차임을 울려요",
            Cue.FAN to "크라운이 돌며 태엽을 감아요",
            Cue.COOL to "차가운 백색 조명",
            Cue.WARM to "따뜻한 조명",
            *turns("왼쪽 해머가 공을 쳐요"),
            Cue.LIGHTS to "저녁 조명, 문자판 야광",
            Cue.WIPER to "사파이어 위 물방울, 와이퍼마다 반짝",
            Cue.DEFROST to "크리스털의 김이 아래부터 걷혀요",
            Cue.ECO to "로듐 · 블루 스틸로 바뀌어요",
            Cue.SPORT to "로즈골드로 바뀌어요",
        ),
        SceneTheme.ENGINE to mapOf(
            Cue.START to "가상 8단 변속기가 1단에 물려요",
            Cue.ACCEL to "회전수가 오르고 2단, 3단으로 변속해요",
            Cue.HARSH to "엔진이 마운트 위에서 한 번 출렁",
            Cue.STOP to "공회전으로 돌아가요",
            Cue.REVERSE to "후진 아이들 기어가 돌아요",
            Cue.ARRIVE to "시동이 꺼지고 라디에이터 팬이 잠시 더 돌아요",
            Cue.FAN to "라디에이터 팬이 빨라져요",
            Cue.COOL to "컴프레서 클러치가 딸깍 붙어요",
            *turns("퓨즈박스 릴레이가 딸깍", "퓨즈박스 릴레이가 딸깍"),
            Cue.LIGHTS to "작업등이 어두워지고 발전기 코일이 은은하게",
            Cue.WIPER to "점검창 유리에 물방울",
            Cue.DEFROST to "유리의 김이 아래부터 걷혀요",
            Cue.ECO to "일찍 변속해요",
            Cue.SPORT to "늦게 변속해요",
        ),
        SceneTheme.MUSIC_BOX to mapOf(
            Cue.START to "달린 거리 100m마다 태엽이 한 칸씩 감겨요",
            Cue.ACCEL to "템포가 올라가요 (마디가 바뀔 때)",
            Cue.STAR to "별을 모으면 장식음이 울려요",
            Cue.HARSH to "펠트 댐퍼가 빗살을 눌러요",
            Cue.STOP to "페르마타, 마지막 화음을 길게",
            Cue.ARRIVE to "태엽이 풀리며 느려지다 으뜸화음으로 끝나요",
            *turns("클릭 스프링이 깜빡이 박자에 맞춰 똑딱", "클릭 스프링이 깜빡이 박자에 맞춰 똑딱"),
            Cue.LIGHTS to "밤 조명, 유리에 램프가 비쳐요",
            Cue.WIPER to "유리 뚜껑에 물방울, 김이 서려요",
            Cue.DEFROST to "김이 아래부터 걷혀요",
            Cue.ECO to "부드러운 초록빛 조명",
            Cue.SPORT to "무대 조명이 호박색으로",
        ),
        SceneTheme.PLASMA to mapOf(
            Cue.START to "전류가 깨어나요",
            Cue.ACCEL to "보랏빛 가닥이 늘어나고 진공관이 달아올라요",
            Cue.STAR to "빛점 하나가 유리 위에 머물러요",
            Cue.BRAKE to "가닥이 중심으로 움츠러들어요",
            Cue.ARRIVE to "전류가 다시 조용해져요",
            Cue.FAN to "오실로스코프 파형이 커져요",
            *turns("가닥이 왼쪽 유리면으로 모여요"),
            Cue.WIPER to "유리구에 빗방울이 맺혀요",
        ),
        SceneTheme.HANGAR to mapOf(
            Cue.START to "조립이 시작돼요 (달린 거리만큼만)",
            Cue.ACCEL to "원자로 불빛이 밝아지고 증기가 뿜어져요",
            Cue.STAR to "크레인이 작은 부품을 가져와요",
            Cue.BRAKE to "무릎 유압이 살짝 눌려요",
            Cue.HARSH to "갠트리 클램프가 닫히고 호박색 회전등이 돌아요",
            Cue.ARRIVE to "로봇이 손을 흔들고 정비 대기로 돌아가요",
            *turns("왼쪽 어깨 경광등, 고개도 살짝 돌려요"),
            Cue.LIGHTS to "갠트리 투광등이 로봇을 비춰요",
            Cue.WIPER to "문 밖에 비가 내리고 바닥에 물빛이 어려요",
        ),
        SceneTheme.TERRARIUM to mapOf(
            Cue.START to "1km마다 식물이 조금씩 자라요",
            Cue.ACCEL to "연못 물과 늘어진 잎이 뒤로 살짝 기울어요",
            Cue.STAR to "별을 모으면 새싹, 콤보면 꽃",
            Cue.HARSH to "연못이 한 번 출렁, 이슬이 떨어져요",
            Cue.ARRIVE to "조명이 저녁빛으로, 나방이 랜턴에 앉아요",
            Cue.CHARGE to "배터리가 찰수록 조명이 천천히 밝아져요",
            Cue.FAN to "잎이 더 크게 흔들려요",
            Cue.COOL to "유리 안쪽에 김, 조명이 차가운 빛으로",
            *turns("무당벌레가 잎을 따라 왼쪽으로"),
            Cue.LIGHTS to "작은 랜턴이 켜지고 반딧불이가 천천히 반짝",
            Cue.WIPER to "병 뚜껑 아래 작은 구름이 비를 내려요",
            Cue.DEFROST to "김이 아래부터 걷혀요",
            Cue.ECO to "부드러운 초록빛",
            Cue.SPORT to "황금빛 오후 햇살",
        ),
        SceneTheme.AQUARIUM to mapOf(
            Cue.START to "차속에 맞춰 물고기 떼가 헤엄쳐요",
            Cue.ACCEL to "수면과 수초가 뒤로 기울어요",
            Cue.STAR to "조개가 진주를 보여 줘요",
            Cue.HARSH to "물고기들이 한 번 휙 돌았다가 차분해져요",
            Cue.REVERSE to "물고기 떼가 수초 사이로 뒤로 흘러가요",
            Cue.ARRIVE to "물고기들이 앞유리 쪽으로 모여 지느러미를 흔들어요",
            Cue.CHARGE to "여과기 램프가 켜지고 기포 기둥이 일정하게",
            Cue.FAN to "에어스톤 기포가 많아지고 커져요",
            Cue.COOL to "물빛이 조금 차가워져요",
            *turns("물고기 떼가 왼쪽으로 천천히"),
            Cue.LIGHTS to "달빛 수조, 플랑크톤이 은은하게",
            Cue.WIPER to "수면 아래에서 본 빗방울 동심원",
            Cue.ECO to "초록빛 물",
            Cue.SPORT to "노을빛 햇살 한 줄기",
        ),
        SceneTheme.ORBIT to mapOf(
            Cue.START to "정거장을 떠나 궤도를 돌아요 (1km = 한 바퀴)",
            Cue.ACCEL to "주 엔진 불꽃이 길어져요",
            Cue.STAR to "작은 위성이 생겨요",
            Cue.HARSH to "앞쪽 역추진, 우주선이 한 번 살짝 흔들려요",
            Cue.REVERSE to "역행 궤도로 거꾸로 돌아요",
            Cue.ARRIVE to "정거장으로 돌아가 도킹, 조명이 어두워져요",
            Cue.CHARGE to "태양전지판이 펼쳐져 빛나요",
            Cue.FAN to "태양풍 입자가 지나가요",
            *turns("왼쪽 자세 제어 분사가 깜빡이 박자에"),
            Cue.LIGHTS to "더 깊은 별하늘, 희미한 성운, 우주선 조명",
            Cue.WIPER to "천천히 떨어지는 유성우",
            Cue.ECO to "푸른 이온 불꽃",
            Cue.SPORT to "호박색 불꽃",
        ),
        SceneTheme.TREASURE_MAP to mapOf(
            Cue.START to "지나온 길만 잉크로 그려요",
            Cue.STAR to "그 자리에 ✕ 표시",
            Cue.HARSH to "잉크 방울이 튀어요",
            Cue.NAVI_NEAR to "토큰 옆에 작은 깃발",
            Cue.ARRIVE to "지도가 말리고 밀랍 도장이 찍혀요",
            *turns("토큰 옆에 작은 화살표 낙서", "토큰 옆에 작은 화살표 낙서"),
            Cue.LIGHTS to "촛불 비네팅과 별자리 도장",
            Cue.WIPER to "양피지에 빗방울, 갓 그린 잉크가 살짝 번져요",
        ),
        SceneTheme.PIXEL to mapOf(
            Cue.START to "배기 연기가 퐁퐁",
            Cue.ACCEL to "차속만큼 도트 세계가 빨리 흘러요",
            Cue.STAR to "탐사차가 깡충 뛰어 보석 조각을 잡아요",
            Cue.HARSH to "화면이 한 번 살짝 흔들리고 헬멧 위에 '!'",
            Cue.REVERSE to "세계가 거꾸로 흐르고 후진 표시",
            Cue.NAVI_NEAR to "저 앞에 둥근 지붕 격납고가 보여요",
            Cue.ARRIVE to "격납고에 들어가 조종사가 손을 흔들어요",
            Cue.CHARGE to "충전 기둥과 5칸 도트 배터리",
            Cue.FAN to "안테나 바람개비가 돌아요",
            Cue.COOL to "눈송이 도트가 내려요",
            *turns("탐사차 깜빡이와 길가 화살표 표지판", "탐사차 깜빡이와 길가 화살표 표지판"),
            Cue.LIGHTS to "밤 팔레트로 바뀌고 네온 간판이 켜져요",
            Cue.WIPER to "비 팔레트, 빗줄기, 물웅덩이",
            Cue.DEFROST to "헬멧 바이저의 김이 아래부터 걷혀요",
            Cue.ECO to "초원 팔레트",
            Cue.SPORT to "노을 팔레트",
        ),
    )

    /** What [theme] does on [cue], or null when its scene shows nothing for it. */
    fun of(theme: SceneTheme, cue: Cue): String? = TABLE[theme]?.get(cue)

    /** "내 조작 → 테마의 반응", or null. */
    fun caption(theme: SceneTheme, cue: Cue): String? = of(theme, cue)?.let { "${cue.action} → $it" }

    /** The cues [theme] reacts to, in declaration order. */
    fun cues(theme: SceneTheme): List<Cue> = Cue.entries.filter { of(theme, it) != null }
}

/** One row of the demo picker. */
class DemoItem(val scenario: Scenario, val title: String, val description: String)

/** A titled group of rows in the demo picker. */
class DemoSection(val title: String, val items: List<DemoItem>)

/**
 * The demo drives of one theme: its showcase (everything in one go), a short lesson per kind of control with only the
 * controls that theme reacts to, and, for the kart, the day-drive stories written for it. Other themes' showcases are
 * not listed here; each is one press away on its own page of the theme tab.
 */
object DemoCatalog {
    /** The bundled stories whose captions describe the kart (they are offered with the kart only). */
    val KART_STORIES = listOf("commute", "rainy_night", "family_trip", "ev_charge")

    /** The bundled showcases of [theme], best first. */
    fun showcaseIds(theme: SceneTheme): List<String> = when (theme) {
        SceneTheme.KART -> listOf("highlight")
        SceneTheme.CALIBRE -> listOf("calibre_showcase")
        SceneTheme.ENGINE -> listOf("engine_showcase", "edrive_showcase")
        SceneTheme.MUSIC_BOX -> listOf("music_box_showcase")
        SceneTheme.TERRARIUM -> listOf("terrarium_showcase")
        SceneTheme.TREASURE_MAP -> listOf("map_showcase")
        SceneTheme.AQUARIUM -> listOf("aquarium_showcase")
        SceneTheme.ORBIT -> listOf("orbit_showcase")
        SceneTheme.PLASMA -> listOf("plasma_showcase")
        SceneTheme.HANGAR -> listOf("hangar_showcase")
        SceneTheme.PAPER -> listOf("paper_showcase")
        SceneTheme.PIXEL -> listOf("pixel_showcase")
        else -> emptyList()
    }

    /** The picker's sections for [theme], from the [bundled] scenarios. */
    fun sections(theme: SceneTheme, bundled: List<Scenario>): List<DemoSection> {
        fun item(id: String) = bundled.firstOrNull { it.id == id }?.let { DemoItem(it, it.title, it.description) }
        val route = bundled.firstOrNull { it.id in showcaseIds(theme) }?.route
        val out = ArrayList<DemoSection>()
        showcaseIds(theme).mapNotNull(::item).takeIf { it.isNotEmpty() }?.let { out += DemoSection("한눈에 보기", it) }
        Lesson.entries.mapNotNull { l -> lesson(theme, l, route)?.let { DemoItem(it, it.title, it.description) } }
            .takeIf { it.isNotEmpty() }?.let { out += DemoSection("조작별로 보기", it) }
        if (theme == SceneTheme.KART) KART_STORIES.mapNotNull(::item).takeIf { it.isNotEmpty() }?.let { out += DemoSection("하루 주행 이야기", it) }
        return out
    }

    /** Every scenario the picker of [theme] can start (bundled and built). */
    fun scenarios(theme: SceneTheme, bundled: List<Scenario>): List<Scenario> = sections(theme, bundled).flatMap { s -> s.items.map { it.scenario } }

    /**
     * The lesson [lesson] for [theme]: only the cues the theme reacts to, each captioned with the theme's own
     * reaction; null when fewer than two remain. [route] is the scripted route of the map theme's demos.
     */
    fun lesson(theme: SceneTheme, lesson: Lesson, route: FloatArray? = null): Scenario? {
        val cues = lesson.cues.filter { ThemeReactions.of(theme, it) != null }
        // A turn to the right alone repeats the left one; the lone approach to a destination is not a lesson.
        if (cues.count { it != Cue.TURN_RIGHT && it != Cue.NAVI_NEAR } < 2) return null
        val s = Script(theme)
        s.key("gear" to "P", "speed" to 0, "door" to false, "fan" to 1, "temp" to 22, "ac" to false, "lights" to false, "wiper" to false,
            "defrost" to false, "turn" to "NONE", "brake" to false, "mode" to "NORMAL", "safety" to false, "battery" to 60, "evCharging" to false,
            "cruise" to false, "navi" to false)
        s.wait(2f)
        if (lesson != Lesson.DRIVE) { s.key("gear" to "D"); s.wait(1f); s.speedTo(50f, 5f); s.wait(1.5f) }
        for ((i, cue) in cues.withIndex()) s.play(cue, next = cues.getOrNull(i + 1))
        if (s.gear != "P") { s.halt(); s.key("gear" to "P") }
        s.wait(2f)
        return Scenario(
            id = "lesson_${theme.name.lowercase()}_${lesson.name.lowercase()}",
            title = "${lesson.title} (${duration(s.t)})",
            durationS = s.t,
            keyframes = s.frames,
            description = cues.filter { it != Cue.TURN_RIGHT }.joinToString(" · ") { if (it == Cue.TURN_LEFT) "깜빡이" else it.action },
            theme = theme.name,
            powertrain = if (theme == SceneTheme.ENGINE) "ICE" else null,
            route = route,
            model = 4,
        )
    }

    /** "40초", "1분", "1분 20초" (to the nearest ten seconds). */
    fun duration(seconds: Float): String {
        val s = ((seconds + 5f) / 10f).toInt() * 10
        return when {
            s < 60 -> "${s}초"
            s % 60 == 0 -> "${s / 60}분"
            else -> "${s / 60}분 ${s % 60}초"
        }
    }

    /** Writes a lesson's keyframes with a running clock; it keeps the car's speed and gear so each cue starts from where the last one ended. */
    private class Script(val theme: SceneTheme) {
        val frames = ArrayList<Keyframe>()
        var t = 0f
        var speed = 0f
        var gear = "P"

        fun key(vararg kv: Pair<String, Any>, cue: Cue? = null) {
            val m = HashMap<String, Any>()
            for ((k, v) in kv) { m[k] = v; if (k == "gear") gear = v as String }
            cue?.let { c -> ThemeReactions.caption(theme, c)?.let { m["caption"] = it } }
            frames += Keyframe(t, m)
        }

        /** Metres driven so far (a star comes every 250 m of smooth driving). */
        var metres = 0f

        fun wait(s: Float) { t += s; metres += speed / 3.6f * s }

        /** Holds the present speed until now, then goes to [v] over [over] seconds. */
        fun speedTo(v: Float, over: Float) {
            key("speed" to speed); t += over; metres += (speed + v) / 2f / 3.6f * over; speed = v; key("speed" to v)
        }

        /** A smooth stop with the pedal (7 km/h a second, well under a harsh one). */
        fun halt() {
            if (speed <= 0f) return
            key("brake" to true); speedTo(0f, (speed / 7f).coerceAtLeast(3f)); key("brake" to false)
        }

        /** Brings the car to at least [v] km/h in drive (about 9 km/h a second). */
        private fun cruiseAt(v: Float) {
            if (gear != "D") { key("gear" to "D"); wait(1f) }
            if (speed < v - 1f) { speedTo(v, ((v - speed) / 9f).coerceIn(2f, 6f)); wait(1f) }
        }

        fun play(cue: Cue, next: Cue?) {
            when (cue) {
                Cue.START -> { key("gear" to "D", cue = cue); wait(1.5f); speedTo(40f, 5f); wait(2f) }
                Cue.ACCEL -> { cruiseAt(40f); key(cue = cue); speedTo(80f, 5f); wait(3f) }
                Cue.STAR -> {
                    cruiseAt(60f); if (speed > 61f) speedTo(60f, 2.5f)
                    // The caption comes up shortly before the next star is due.
                    val toStar = 250f - metres % 250f
                    if (toStar > 60f) wait((toStar - 40f) / (speed / 3.6f))
                    key(cue = cue); wait(9f)
                }
                Cue.BRAKE -> { cruiseAt(60f); key("brake" to true, cue = cue); speedTo(35f, 4f); key("brake" to false); wait(3f) }
                Cue.HARSH -> { cruiseAt(70f); key("brake" to true, cue = cue); speedTo(25f, 1.5f); wait(1f); key("brake" to false); wait(4.5f) }
                Cue.STOP -> { cruiseAt(30f); key("brake" to true); speedTo(0f, (speed / 7f).coerceAtLeast(3f)); key(cue = cue); wait(6.5f); key("brake" to false) }
                Cue.REVERSE -> {
                    halt()
                    key("gear" to "R", cue = cue); wait(1f); speedTo(6f, 1f); wait(3.5f); speedTo(0f, 1.5f); key("gear" to "D"); wait(1f)
                }
                // The approach is told on the way; the arrival itself follows as its own cue.
                Cue.NAVI_NEAR -> { key("navi" to true, "naviRemainingM" to 280); cruiseAt(40f); key(cue = cue); wait(6f) }
                Cue.ARRIVE -> {
                    if (frames.none { it.values["navi"] == true }) { key("navi" to true, "naviRemainingM" to 280); cruiseAt(30f) }
                    halt(); key("naviRemainingM" to 0); wait(0.5f)
                    key("gear" to "P", "arrive" to true, cue = cue); wait(7f)
                }
                Cue.CHARGE -> {
                    if (gear != "P") { halt(); key("gear" to "P"); wait(1f) }
                    key("evCharging" to true, "battery" to 64, cue = cue); wait(7f)
                }
                Cue.FAN -> { key("fan" to 8, cue = cue); wait(6.5f); key("fan" to 2); wait(1f) }
                Cue.COOL -> { key("ac" to true, "temp" to 18, cue = cue); wait(7f); key("ac" to false, "temp" to 22); wait(1f) }
                Cue.WARM -> { key("temp" to 27, cue = cue); wait(7f); key("temp" to 22); wait(1f) }
                Cue.TURN_LEFT -> { key("turn" to "LEFT", cue = cue); wait(6f); key("turn" to "NONE"); wait(1.5f) }
                Cue.TURN_RIGHT -> { key("turn" to "RIGHT", cue = cue); wait(6f); key("turn" to "NONE"); wait(1.5f) }
                Cue.LIGHTS -> { key("lights" to true, cue = cue); wait(7f); key("lights" to false); wait(2f) }
                // The wipers stay on while the glass is cleared: the mist comes from the rain.
                Cue.WIPER -> { key("wiper" to true, cue = cue); wait(if (next == Cue.DEFROST) 10f else 7.5f); if (next != Cue.DEFROST) { key("wiper" to false); wait(2f) } }
                Cue.DEFROST -> { key("defrost" to true, cue = cue); wait(7f); key("defrost" to false, "wiper" to false); wait(2f) }
                Cue.ECO -> { key("mode" to "ECO", cue = cue); wait(7f); key("mode" to "NORMAL"); wait(1f) }
                Cue.SPORT -> { key("mode" to "SPORT", cue = cue); wait(7f); key("mode" to "NORMAL"); wait(1f) }
                Cue.CRUISE -> { key("cruise" to true, cue = cue); wait(7f); key("cruise" to false); wait(1f) }
            }
        }
    }
}
