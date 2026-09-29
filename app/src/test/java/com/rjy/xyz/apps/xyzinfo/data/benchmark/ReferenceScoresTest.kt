package com.rjy.xyz.apps.xyzinfo.data.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 参考机型匹配与条形图换算测试。 */
class ReferenceScoresTest {

    @Test
    fun `带后缀的芯片名也能匹配到参考项`() {
        assertEquals(
            "骁龙 778G",
            ReferenceScores.match("骁龙 778G / 778G+")?.name
        )
        assertEquals(
            "骁龙 8 Gen 3",
            ReferenceScores.match("骁龙 8 Gen 3 for Galaxy")?.name
        )
        assertEquals(
            "Exynos 9825",
            ReferenceScores.match("Exynos 9825 / 9820")?.name
        )
    }

    @Test
    fun `更长的型号名不会被短名抢走`() {
        // 「骁龙 8 Elite」必须匹配自己，而不是「骁龙 8 Elite Gen 5」
        assertEquals("骁龙 8 Elite", ReferenceScores.match("骁龙 8 Elite")?.name)
        assertEquals("骁龙 8 Elite Gen 5", ReferenceScores.match("骁龙 8 Elite Gen 5")?.name)
    }

    @Test
    fun `未知芯片返回 null`() {
        assertNull(ReferenceScores.match("骁龙 7 系（SM7650）"))
        assertNull(ReferenceScores.match(null))
        assertNull(ReferenceScores.match(""))
    }

    @Test
    fun `倍数与百分比换算`() {
        assertEquals(2.0, ReferenceScores.ratio(2000, 1000), 0.001)
        assertEquals(0.0, ReferenceScores.ratio(2000, 0), 0.001)
        assertEquals(50, ReferenceScores.percentOfMax(50, 100))
        assertEquals(100, ReferenceScores.percentOfMax(150, 100))
        assertEquals(0, ReferenceScores.percentOfMax(10, 0))
    }
}
