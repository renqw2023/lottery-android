// 生肖
sealed class Zodiac(
    val celestialType: CelestialType,
    val yinYang: YinYang,
    val season: Season,
    val direction: Direction,
    val gender: Gender,
    val luck: Luck
) {
    class RAT(celestialType: CelestialType, yinYang: YinYang, season: Season, 
             direction: Direction, gender: Gender, luck: Luck) : 
        Zodiac(celestialType, yinYang, season, direction, gender, luck)
    class OX(celestialType: CelestialType, yinYang: YinYang, season: Season, 
            direction: Direction, gender: Gender, luck: Luck) : 
        Zodiac(celestialType, yinYang, season, direction, gender, luck)
    class TIGER(celestialType: CelestialType, yinYang: YinYang, season: Season, 
               direction: Direction, gender: Gender, luck: Luck) : 
        Zodiac(celestialType, yinYang, season, direction, gender, luck)
    class RABBIT(celestialType: CelestialType, yinYang: YinYang, season: Season, 
                direction: Direction, gender: Gender, luck: Luck) : 
        Zodiac(celestialType, yinYang, season, direction, gender, luck)
    class DRAGON(celestialType: CelestialType, yinYang: YinYang, season: Season, 
                direction: Direction, gender: Gender, luck: Luck) : 
        Zodiac(celestialType, yinYang, season, direction, gender, luck)
    class SNAKE(celestialType: CelestialType, yinYang: YinYang, season: Season, 
               direction: Direction, gender: Gender, luck: Luck) : 
        Zodiac(celestialType, yinYang, season, direction, gender, luck)
    class HORSE(celestialType: CelestialType, yinYang: YinYang, season: Season, 
               direction: Direction, gender: Gender, luck: Luck) : 
        Zodiac(celestialType, yinYang, season, direction, gender, luck)
    class GOAT(celestialType: CelestialType, yinYang: YinYang, season: Season, 
              direction: Direction, gender: Gender, luck: Luck) : 
        Zodiac(celestialType, yinYang, season, direction, gender, luck)
    class MONKEY(celestialType: CelestialType, yinYang: YinYang, season: Season, 
                direction: Direction, gender: Gender, luck: Luck) : 
        Zodiac(celestialType, yinYang, season, direction, gender, luck)
    class ROOSTER(celestialType: CelestialType, yinYang: YinYang, season: Season, 
                 direction: Direction, gender: Gender, luck: Luck) : 
        Zodiac(celestialType, yinYang, season, direction, gender, luck)
    class DOG(celestialType: CelestialType, yinYang: YinYang, season: Season, 
             direction: Direction, gender: Gender, luck: Luck) : 
        Zodiac(celestialType, yinYang, season, direction, gender, luck)
    class PIG(celestialType: CelestialType, yinYang: YinYang, season: Season, 
             direction: Direction, gender: Gender, luck: Luck) : 
        Zodiac(celestialType, yinYang, season, direction, gender, luck)
}

// 天地属性
enum class CelestialType {
    SKY,    // 天肖
    EARTH   // 地肖
}

// 阴阳属性
enum class YinYang {
    YIN,    // 阴
    YANG    // 阳
}

// 季节
enum class Season {
    SPRING,  // 春
    SUMMER,  // 夏
    AUTUMN,  // 秋
    WINTER   // 冬
}

// 方位
enum class Direction {
    EAST,   // 东
    SOUTH,  // 南
    WEST,   // 西
    NORTH   // 北
}

// 性别
enum class Gender {
    MALE,   // 男
    FEMALE  // 女
}

// 吉凶
enum class Luck {
    GOOD,   // 吉
    BAD     // 凶
}

// 五行
enum class Element {
    GOLD,   // 金
    WOOD,   // 木
    WATER,  // 水
    FIRE,   // 火
    EARTH   // 土
}

// 颜色
enum class Color {
    RED,    // 红波
    BLUE,   // 蓝波
    GREEN   // 绿波
} 