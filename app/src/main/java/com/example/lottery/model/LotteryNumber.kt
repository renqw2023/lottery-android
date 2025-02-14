data class LotteryNumber(
    val number: Int,
    val zodiac: Zodiac,
    val color: Color,
    val element: Element,
    val isOdd: Boolean
)

enum class Zodiac(
    val numbers: List<Int>,
    val celestialType: CelestialType,
    val yinYang: YinYang,
    val season: Season,
    val direction: Direction,
    val gender: Gender,
    val luck: Luck
)

enum class Color {
    RED, BLUE, GREEN
}

enum class Element {
    GOLD, WOOD, WATER, FIRE, EARTH
} 