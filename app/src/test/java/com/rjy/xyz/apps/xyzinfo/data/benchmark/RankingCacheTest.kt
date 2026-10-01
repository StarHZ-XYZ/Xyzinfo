package com.rjy.xyz.apps.xyzinfo.data.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 排行榜本地缓存：存进去能原样读回来，换数据版本就作废。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RankingCacheTest {

    @Test
    fun `缓存可以原样读回`() {
        val context = RuntimeEnvironment.getApplication()
        RankingCache.clear(context)
        assertNull("清空后应该读不到", RankingCache.load(context))

        val snapshot = RankingCache.Snapshot(
            key = "COMPOSITE|null|骁龙 778G|1000|2900|1000|2026-09",
            summary = "本机芯片：骁龙 778G",
            maxValue = 10800,
            rows = listOf(
                RankingCache.Row("骁龙 8 Elite Gen 5", 10800, "2025", measured = false, matched = false),
                RankingCache.Row("本机实测（骁龙 778G）", 1024, "实测", measured = true, matched = false)
            ),
            datasetVersion = "2026-09",
            savedAt = 1234L
        )
        RankingCache.save(context, snapshot)

        val loaded = RankingCache.load(context)
        assertEquals(snapshot.key, loaded?.key)
        assertEquals(snapshot.summary, loaded?.summary)
        assertEquals(snapshot.maxValue, loaded?.maxValue)
        assertEquals(snapshot.rows.size, loaded?.rows?.size)
        assertEquals(snapshot.rows[1].name, loaded?.rows?.get(1)?.name)
        assertEquals(true, loaded?.rows?.get(1)?.measured)
        assertEquals("2026-09", loaded?.datasetVersion)

        RankingCache.clear(context)
        assertNull(RankingCache.load(context))
    }
}
