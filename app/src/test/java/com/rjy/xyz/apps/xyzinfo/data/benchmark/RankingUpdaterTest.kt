package com.rjy.xyz.apps.xyzinfo.data.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 榜单数据集：导出 / 解析要能对得上（仓库里发布的就是导出出来的那份 JSON）。
 *
 * 跑在 Robolectric 下：`org.json` 在纯 JVM 单元测试里是空实现（会抛 Stub 异常）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RankingUpdaterTest {

    @Test
    fun `内置榜单导出再解析_内容完全一致`() {
        val json = RankingUpdater.exportBuiltIn()
        val parsed = RankingUpdater.parseDataset(json)
        val builtIn = GeekerwanScores.builtIn
        assertEquals(builtIn.size, parsed.size)
        builtIn.zip(parsed).forEach { (expected, actual) ->
            assertEquals(expected.name, actual.name)
            assertEquals(expected.brand, actual.brand)
            assertEquals(expected.single, actual.single)
            assertEquals(expected.multi, actual.multi)
            assertEquals(expected.gpu, actual.gpu)
            assertEquals(expected.year, actual.year)
        }
    }

    @Test
    fun `脏数据不会崩_缺字段的条目会被跳过`() {
        val json = """[{"name":"有效芯片","brand":"测试","single":100,"multi":200,"gpu":300,"year":2026},
                       {"brand":"没有名字"},
                       {"name":"","brand":"空名字"}]"""
        val parsed = RankingUpdater.parseDataset(json)
        assertEquals(1, parsed.size)
        assertEquals("有效芯片", parsed[0].name)
    }

    @Test
    fun `版本比较_只有真的不一样才算有更新`() {
        assertTrue(RankingUpdater.CheckResult("2026-09", "2026-11").hasUpdate)
        assertFalse(RankingUpdater.CheckResult("2026-09", "2026-09").hasUpdate)
        // 连不上 / 没这个文件时，绝不能提示"有更新"
        assertFalse(RankingUpdater.CheckResult("2026-09", null).hasUpdate)
        assertFalse(RankingUpdater.CheckResult("2026-09", "").hasUpdate)
    }

    @Test
    fun `换数据集后榜单确实换掉了`() {
        val before = GeekerwanScores.all.size
        val custom = listOf(
            com.rjy.xyz.apps.xyzinfo.model.ChipScore("测试芯片", "测试", 1, 2, 3, 2026)
        )
        GeekerwanScores.applyDataset(custom, "test-version")
        try {
            assertEquals(1, GeekerwanScores.all.size)
            assertEquals("test-version", GeekerwanScores.datasetVersion)
            assertTrue(GeekerwanScores.usingDownloaded)
        } finally {
            // 测试之间不能互相污染
            GeekerwanScores.useBuiltIn()
        }
        assertEquals(before, GeekerwanScores.all.size)
        assertFalse(GeekerwanScores.usingDownloaded)
    }
}
