package com.rjy.xyz.apps.xyzinfo.data.benchmark

import com.rjy.xyz.apps.xyzinfo.model.RankingMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 极客湾参考榜单：匹配规则、排序与综合指数。 */
class GeekerwanScoresTest {

    @Test
    fun `带后缀的芯片名也能匹配到参考项`() {
        assertEquals("骁龙 778G", GeekerwanScores.match("骁龙 778G / 778G+")?.name)
        // 榜单里 Galaxy 专属版本是单独一条，识别到 for Galaxy 就该匹配它
        assertEquals("骁龙 8 Gen 3 for Galaxy", GeekerwanScores.match("骁龙 8 Gen 3 for Galaxy")?.name)
        assertEquals("骁龙 8 Gen 3", GeekerwanScores.match("骁龙 8 Gen 3")?.name)
        assertEquals("Exynos 9825", GeekerwanScores.match("Exynos 9825 / 9820")?.name)
    }

    @Test
    fun `更长的型号名不会被短名抢走`() {
        assertEquals("骁龙 8 Elite", GeekerwanScores.match("骁龙 8 Elite")?.name)
        assertEquals("骁龙 8 Elite Gen 5", GeekerwanScores.match("骁龙 8 Elite Gen 5")?.name)
        assertEquals("骁龙 8 Elite Gen 6", GeekerwanScores.match("骁龙 8 Elite Gen 6")?.name)
        assertEquals(
            "骁龙 8 Elite Extreme Gen 6",
            GeekerwanScores.match("骁龙 8 Elite Extreme Gen 6")?.name
        )
    }

    @Test
    fun `未知芯片返回 null`() {
        assertNull(GeekerwanScores.match("骁龙 7 系（SM7650）"))
        assertNull(GeekerwanScores.match(null))
        assertNull(GeekerwanScores.match(""))
    }

    @Test
    fun `榜单按维度降序`() {
        RankingMetric.entries.forEach { metric ->
            val ranked = GeekerwanScores.ranked(metric)
            assertTrue(ranked.size > 80)
            ranked.zipWithNext().forEach { (a, b) ->
                assertTrue(metric.valueOf(a) >= metric.valueOf(b))
            }
        }
    }

    @Test
    fun `按品牌筛选只保留该品牌`() {
        val snapdragons = GeekerwanScores.ranked(RankingMetric.MULTI, "骁龙")
        assertTrue(snapdragons.isNotEmpty())
        assertTrue(snapdragons.all { it.brand == "骁龙" })
    }

    @Test
    fun `骁龙 778G 的综合指数约为 1000`() {
        val score = GeekerwanScores.match("骁龙 778G")!!
        assertEquals(1000, score.composite)
    }

    @Test
    fun `倍数换算`() {
        assertEquals(2.0, GeekerwanScores.ratio(2000, 1000), 0.001)
        assertEquals(0.0, GeekerwanScores.ratio(2000, 0), 0.001)
    }

    @Test
    fun `第六代双旗舰已收录且排序正确`() {
        val extreme = GeekerwanScores.match("骁龙 8 Elite Extreme Gen 6")!!
        val standard = GeekerwanScores.match("骁龙 8 Elite Gen 6")!!
        val previous = GeekerwanScores.match("骁龙 8 Elite Gen 5")!!
        assertEquals(2026, extreme.year)
        assertTrue(extreme.single > standard.single)
        assertTrue(standard.single > previous.single)
        assertTrue(extreme.multi > standard.multi)
        assertTrue(standard.multi > previous.multi)
        assertEquals("2026-09-22", extreme.dateLabel)
        assertEquals("2025-09", previous.dateLabel)
    }

    @Test
    fun `已知芯片档位不会发生代际倒挂`() {
        val sevenSevenEight = GeekerwanScores.match("骁龙 778G")!!
        val dimensity9000 = GeekerwanScores.match("天玑 9000")!!
        val gen3 = GeekerwanScores.match("骁龙 8 Gen 3")!!
        val elite5 = GeekerwanScores.match("骁龙 8 Elite Gen 5")!!

        assertTrue(sevenSevenEight.single < dimensity9000.single)
        assertTrue(sevenSevenEight.multi < dimensity9000.multi)
        assertTrue(gen3.single < elite5.single)
        assertTrue(gen3.multi < elite5.multi)
        assertTrue(gen3.composite < elite5.composite)
        assertEquals("2023年", gen3.dateLabel)
    }
}
