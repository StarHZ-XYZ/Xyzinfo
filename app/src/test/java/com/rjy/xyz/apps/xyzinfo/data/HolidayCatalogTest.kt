package com.rjy.xyz.apps.xyzinfo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 节日彩蛋的日期判定。
 *
 * 农历节日最容易出错的地方是"农历表写错一位 → 春节差一天"，
 * 所以这里直接拿**公开的真实日期**（2024–2033 的春节、中秋、七夕…）当基准。
 */
class HolidayCatalogTest {

    @Test
    fun springFestivalMatchesKnownDates() {
        // 公历日期 -> 当年的春节
        val known = mapOf(
            "2024-02-10" to 2024,
            "2025-01-29" to 2025,
            "2026-02-17" to 2026,
            "2027-02-06" to 2027,
            "2028-01-26" to 2028,
            "2029-02-13" to 2029,
            "2030-02-03" to 2030,
            "2031-01-23" to 2031,
            "2032-02-11" to 2032,
            "2033-01-31" to 2033
        )
        known.forEach { (date, year) ->
            val holiday = HolidayCatalog.of(date)
            assertEquals("$date 应该是春节", "春节", holiday?.name)
            assertEquals("$year 春节特效是灯笼", HolidayTheme.LANTERN, holiday?.theme)
        }
    }

    @Test
    fun springFestivalCoversWholeWeek() {
        // 初一 ~ 初五 都算春节；初六就不是了
        for (day in 17..21) {
            assertEquals("2026-02-$day 属于春节假期", "春节", HolidayCatalog.of(2026, 2, day)?.name)
        }
        assertNull(HolidayCatalog.of(2026, 2, 23))
    }

    @Test
    fun newYearEveIsTheLastLunarDayOfTheYear() {
        assertEquals("除夕", HolidayCatalog.of("2025-01-28")?.name)
        assertEquals("除夕", HolidayCatalog.of("2026-02-16")?.name)
        assertEquals("除夕", HolidayCatalog.of("2024-02-09")?.name)
    }

    @Test
    fun midAutumnMatchesKnownDates() {
        listOf(
            "2024-09-17",
            "2025-10-06",
            "2026-09-25",
            "2027-09-15",
            "2028-10-03"
        ).forEach { date ->
            assertEquals("$date 应该是中秋", "中秋节", HolidayCatalog.of(date)?.name)
        }
    }

    @Test
    fun qixiAndDragonBoatAndLanternFestival() {
        assertEquals("七夕节", HolidayCatalog.of("2024-08-10")?.name)
        assertEquals("七夕节", HolidayCatalog.of("2025-08-29")?.name)
        assertEquals("端午节", HolidayCatalog.of("2024-06-10")?.name)
        assertEquals("端午节", HolidayCatalog.of("2025-05-31")?.name)
        assertEquals("元宵节", HolidayCatalog.of("2024-02-24")?.name)
        assertEquals("元宵节", HolidayCatalog.of("2026-03-03")?.name)
    }

    @Test
    fun fixedHolidaysCoverTheirWindows() {
        assertEquals("元旦", HolidayCatalog.of(2026, 1, 1)?.name)
        assertEquals("元旦", HolidayCatalog.of(2025, 12, 31)?.name)
        assertEquals("情人节", HolidayCatalog.of(2026, 2, 14)?.name)
        assertEquals("劳动节", HolidayCatalog.of(2026, 5, 1)?.name)
        assertEquals("儿童节", HolidayCatalog.of(2026, 6, 1)?.name)
        assertEquals("建军节", HolidayCatalog.of(2026, 8, 1)?.name)
        assertEquals("圣诞节", HolidayCatalog.of(2026, 12, 25)?.name)
        // 国庆给 10-01 ~ 10-05 的窗口
        for (day in 1..5) {
            assertEquals("国庆节", HolidayCatalog.of(2026, 10, day)?.name)
        }
        assertNull(HolidayCatalog.of(2026, 10, 8))
    }

    @Test
    fun plainDaysHaveNoHoliday() {
        assertNull(HolidayCatalog.of(2026, 1, 15))
        assertNull(HolidayCatalog.of(2026, 4, 9))
        assertNull(HolidayCatalog.of(2026, 7, 20))
        assertNull(HolidayCatalog.of(2026, 11, 11))
    }

    @Test
    fun singleDayHolidaysWinOverHolidayWindows() {
        // 2024-02-14 在春节假期里，但它本身就是情人节 —— 按情人节走
        assertEquals("情人节", HolidayCatalog.of("2024-02-14")?.name)
        // 2028-10-03 中秋正好落在国庆假期里 —— 按中秋走
        assertEquals("中秋节", HolidayCatalog.of("2028-10-03")?.name)
        // 春节假期里没有专属节日的日子还是春节
        assertEquals("春节", HolidayCatalog.of("2024-02-12")?.name)
        assertEquals("国庆节", HolidayCatalog.of("2028-10-02")?.name)
    }

    @Test
    fun everyHolidayHasItsOwnEffectAndWish() {
        val all = listOf(
            HolidayCatalog.NEW_YEAR,
            HolidayCatalog.VALENTINE,
            HolidayCatalog.NEW_YEAR_EVE,
            HolidayCatalog.SPRING_FESTIVAL,
            HolidayCatalog.LANTERN_FESTIVAL,
            HolidayCatalog.LABOUR,
            HolidayCatalog.CHILDREN,
            HolidayCatalog.DRAGON_BOAT,
            HolidayCatalog.ARMY,
            HolidayCatalog.QIXI,
            HolidayCatalog.MID_AUTUMN,
            HolidayCatalog.NATIONAL,
            HolidayCatalog.CHRISTMAS
        )
        assertEquals("节日数量（含新增的除夕 / 元宵 / 端午等）", 13, all.size)
        assertEquals("节日名不能重复", all.size, all.map { it.name }.toSet().size)
        all.forEach { holiday ->
            assertTrue("${holiday.name} 缺祝福语", holiday.wish.isNotBlank())
            assertTrue("${holiday.name} 缺标题", holiday.title.isNotBlank())
            assertTrue("${holiday.name} 缺表情", holiday.emoji.isNotBlank())
        }
    }

    private fun HolidayCatalog.of(date: String): Holiday? {
        val parts = date.split('-').map { it.toInt() }
        return of(parts[0], parts[1], parts[2])
    }
}
