package com.rjy.xyz.apps.xyzinfo.data

import java.util.Calendar

/** 节日特效的"造型"。同样的造型可以用不同的配色区分节日。 */
enum class HolidayTheme {
    /** 灯笼（春节 / 除夕 / 元宵）。 */
    LANTERN,

    /** 爱心（情人节 / 七夕）。 */
    HEART,

    /** 五角星（国庆 / 建军节）。 */
    STAR,

    /** 烟花（元旦 / 劳动节）。 */
    FIREWORK,

    /** 月饼（中秋）。 */
    MOONCAKE,

    /** 礼物盒（圣诞）。 */
    GIFT,

    /** 粽子（端午）。 */
    DUMPLING,

    /** 气球（儿童节）。 */
    BALLOON
}

/**
 * 一个节日彩蛋：特效造型 + 进场弹的那条祝福。
 *
 * [accent] 是主色（粒子 / 祝福卡底色），[accentDeep] 是渐变的深色端。
 */
data class Holiday(
    val name: String,
    val emoji: String,
    val title: String,
    val wish: String,
    val theme: HolidayTheme,
    val accent: Int,
    val accentDeep: Int
)

/**
 * 节日彩蛋目录（1.0.4 新增）。
 *
 * 公历节日直接按月日匹配；春节 / 除夕 / 元宵 / 端午 / 七夕 / 中秋是农历节日，
 * 交给 [LunarCalendar] 换算后再匹配。国庆、春节这种连着的节日给一个几天的小窗口，
 * 免得"只在那一天才看得到彩蛋"。
 *
 * 同一天命中多个节日时**公历优先**（比如 2031-10-01 中秋撞国庆，按国庆走）。
 */
object HolidayCatalog {

    fun today(): Holiday? = with(Calendar.getInstance()) {
        of(
            get(Calendar.YEAR),
            get(Calendar.MONTH) + 1,
            get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * 指定公历日期对应的节日（没有则返回 null）。
     *
     * 判定顺序是「**单日节日**优先于**连假窗口**」：
     * 2024-02-14 既是情人节又在春节假期里 → 按情人节；2028-10-03 既是中秋又在国庆假期里 → 按中秋。
     * 这样"当天本来就有专属节日"的那些日子永远不会被大假期吃掉。
     */
    fun of(year: Int, month: Int, day: Int): Holiday? {
        val lunar = LunarCalendar.solarToLunar(year, month, day)
        return singleDay(month, day, lunar) ?: holidayWindow(month, day, lunar)
    }

    private fun singleDay(
        month: Int,
        day: Int,
        lunar: LunarCalendar.LunarDate
    ): Holiday? = when {
        month == 2 && day == 14 -> VALENTINE
        month == 6 && day == 1 -> CHILDREN
        month == 8 && day == 1 -> ARMY
        // 除夕：腊月的最后一天
        lunar.month == 12 && lunar.day == LunarCalendar.monthDays(lunar.year, 12) -> NEW_YEAR_EVE
        lunar.month == 1 && !lunar.leap && lunar.day == 15 -> LANTERN_FESTIVAL
        lunar.month == 5 && !lunar.leap && lunar.day == 5 -> DRAGON_BOAT
        lunar.month == 7 && !lunar.leap && lunar.day == 7 -> QIXI
        lunar.month == 8 && !lunar.leap && lunar.day == 15 -> MID_AUTUMN
        else -> null
    }

    private fun holidayWindow(
        month: Int,
        day: Int,
        lunar: LunarCalendar.LunarDate
    ): Holiday? = when {
        // 跨年：12-31 与 01-01 都算元旦
        (month == 12 && day == 31) || (month == 1 && day == 1) -> NEW_YEAR
        month == 5 && day in 1..2 -> LABOUR
        month == 10 && day in 1..5 -> NATIONAL
        month == 12 && day in 24..25 -> CHRISTMAS
        // 春节：初一 ~ 初五
        lunar.month == 1 && !lunar.leap && lunar.day in 1..5 -> SPRING_FESTIVAL
        else -> null
    }

    val NEW_YEAR = Holiday(
        name = "元旦",
        emoji = "🎆",
        title = "元旦快乐",
        wish = "新的一年，万事顺遂",
        theme = HolidayTheme.FIREWORK,
        accent = 0xFF5B6BFF.toInt(),
        accentDeep = 0xFF2E3AA8.toInt()
    )

    val VALENTINE = Holiday(
        name = "情人节",
        emoji = "💖",
        title = "情人节快乐",
        wish = "愿你有爱相伴，有人惦记",
        theme = HolidayTheme.HEART,
        accent = 0xFFF0506E.toInt(),
        accentDeep = 0xFFC9184A.toInt()
    )

    val NEW_YEAR_EVE = Holiday(
        name = "除夕",
        emoji = "🧨",
        title = "除夕快乐",
        wish = "辞旧迎新，年夜饭要吃饱",
        theme = HolidayTheme.LANTERN,
        accent = 0xFFD62828.toInt(),
        accentDeep = 0xFF8B1A1A.toInt()
    )

    val SPRING_FESTIVAL = Holiday(
        name = "春节",
        emoji = "🏮",
        title = "新春快乐",
        wish = "阖家团圆，万事大吉",
        theme = HolidayTheme.LANTERN,
        accent = 0xFFE01E1E.toInt(),
        accentDeep = 0xFF9B1111.toInt()
    )

    val LANTERN_FESTIVAL = Holiday(
        name = "元宵节",
        emoji = "🏮",
        title = "元宵节快乐",
        wish = "花好月圆，人月两团圆",
        theme = HolidayTheme.LANTERN,
        accent = 0xFFF08C00.toInt(),
        accentDeep = 0xFFB35C00.toInt()
    )

    val LABOUR = Holiday(
        name = "劳动节",
        emoji = "🌷",
        title = "劳动节快乐",
        wish = "辛苦了，今天好好休息",
        theme = HolidayTheme.FIREWORK,
        accent = 0xFF2F9E44.toInt(),
        accentDeep = 0xFF1B6B2B.toInt()
    )

    val CHILDREN = Holiday(
        name = "儿童节",
        emoji = "🎈",
        title = "儿童节快乐",
        wish = "愿你永远有童心",
        theme = HolidayTheme.BALLOON,
        accent = 0xFF4DABF7.toInt(),
        accentDeep = 0xFF1864AB.toInt()
    )

    val DRAGON_BOAT = Holiday(
        name = "端午节",
        emoji = "🐉",
        title = "端午安康",
        wish = "粽香满屋，安康顺遂",
        theme = HolidayTheme.DUMPLING,
        accent = 0xFF2B8A3E.toInt(),
        accentDeep = 0xFF17521F.toInt()
    )

    val ARMY = Holiday(
        name = "建军节",
        emoji = "🎖️",
        title = "建军节快乐",
        wish = "致敬最可爱的人",
        theme = HolidayTheme.STAR,
        accent = 0xFF5C7A29.toInt(),
        accentDeep = 0xFF33471A.toInt()
    )

    val QIXI = Holiday(
        name = "七夕节",
        emoji = "💫",
        title = "七夕快乐",
        wish = "愿你被温柔以待",
        theme = HolidayTheme.HEART,
        accent = 0xFFD6336C.toInt(),
        accentDeep = 0xFFA61E4D.toInt()
    )

    val MID_AUTUMN = Holiday(
        name = "中秋节",
        emoji = "🥮",
        title = "中秋快乐",
        wish = "但愿人长久，千里共婵娟",
        theme = HolidayTheme.MOONCAKE,
        accent = 0xFFE8A33D.toInt(),
        accentDeep = 0xFFA86A17.toInt()
    )

    val NATIONAL = Holiday(
        name = "国庆节",
        emoji = "🇨🇳",
        title = "国庆快乐",
        wish = "山河锦绣，国泰民安",
        theme = HolidayTheme.STAR,
        accent = 0xFFC1121F.toInt(),
        accentDeep = 0xFF7A0B14.toInt()
    )

    val CHRISTMAS = Holiday(
        name = "圣诞节",
        emoji = "🎄",
        title = "圣诞快乐",
        wish = "Merry Christmas · 愿你被礼物砸中",
        theme = HolidayTheme.GIFT,
        accent = 0xFFC92A2A.toInt(),
        accentDeep = 0xFF1E7A46.toInt()
    )
}
