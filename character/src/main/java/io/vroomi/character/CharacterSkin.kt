package io.vroomi.character

/**
 * Who rides in the kart: 15 themes. All are original designs drawn in code (see docs/character-originality.md):
 * they borrow a mood or an object (rice cake, penguin, robot, automatic watch, engine...), never an existing character.
 * Mechanical themes move their parts with the car: needles follow the speed, rotors and pistons speed up, etc.
 */
enum class CharacterSkin(val label: String, val intro: String, val mechanical: Boolean) {
    // Cute friends
    VROOMI("브루미", "바람과 배터리에서 태어난 바람불씨. 귀가 바람에 펄럭여요", false),
    SSUK("쑥떡이", "말랑말랑 쑥떡. 가만히 있어도 몸이 출렁출렁", false),
    EOLEUM("얼음콩", "추위를 좋아하는 꼬마 펭귄. 에어컨 바람이 제일 좋아요", false),
    MUNGE("뭉게", "뭉게구름 친구. 와이퍼를 켜면 같이 보슬비를 뿌려요", false),
    DOTOL("도톨이", "숲에서 굴러온 도토리. 모자를 꼭 붙잡고 달려요", false),

    // Mechanical friends
    NUT("너트", "볼트와 너트로 만든 꼬마 로봇. 신호를 받으면 안테나가 반짝", true),
    CHOCHIM("초침이", "오토매틱 시계. 달릴수록 등의 로터가 돌며 태엽이 감겨요. 바늘은 진짜 지금 시각", true),
    PISTON("피스톤", "엔진 블록 친구. 속도만큼 머리 위 피스톤이 위아래로 쿵쿵", true),
    BOOST("부스트", "터보차저 친구. 가속하면 배의 임펠러가 휘이잉 빨라져요", true),
    TOPNI("톱니", "놋쇠 톱니바퀴. 달린 만큼 몸이 데굴데굴 돌아가요", true),
    BANEUL("바늘이", "계기판 친구. 배의 바늘이 지금 속도를 가리켜요", true),
    TAEYEOP("태엽이", "양철 태엽 장난감. 등의 태엽 열쇠가 끼릭끼릭 돌아요", true),
    ORGEL("오르골", "나무 오르골. 달리면 실린더가 돌며 음표가 흘러나와요", true),
    PILA("필라", "필라멘트 전구. 속도가 오르면 필라멘트가 환하게 달아올라요", true),
    NACHIM("나침이", "놋쇠 나침반. 깜빡이를 켜면 바늘이 그쪽으로 휙", true),
}
