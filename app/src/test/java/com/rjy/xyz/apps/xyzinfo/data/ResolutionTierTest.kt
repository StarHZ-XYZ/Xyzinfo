package com.rjy.xyz.apps.xyzinfo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 分辨率档位推断（1.0.6）：1080p 不能叫 2400p，1440p 才算 2K。 */
class ResolutionTierTest {

    @Test
    fun `常见手机分辨率的档位`() {
        assertEquals("1080p（FHD）", ResolutionTier.of(2400, 1080))
        assertEquals("1080p（FHD）", ResolutionTier.of(1920, 1080))
        assertEquals("2K（QHD 1440p）", ResolutionTier.of(3200, 1440))
        assertEquals("4K（UHD 2160p）", ResolutionTier.of(3840, 2160))
        assertEquals("720p（HD）", ResolutionTier.of(1600, 720))
        assertEquals("8K（4320p）", ResolutionTier.of(7680, 4320))
    }

    @Test
    fun `1220p 这类叫 1_5K`() {
        assertEquals("1.5K（1220p）", ResolutionTier.of(2712, 1220))
        assertEquals("1.5K（1260p）", ResolutionTier.of(2800, 1260))
    }

    @Test
    fun `短边判定_横竖屏结论一致`() {
        assertEquals(ResolutionTier.of(2400, 1080), ResolutionTier.of(1080, 2400))
    }

    @Test
    fun `长宽比只认常见档`() {
        assertEquals("20:9", ResolutionTier.coarseRatio(2400, 1080))
        assertEquals("16:9", ResolutionTier.coarseRatio(1920, 1080))
        assertEquals("4:3", ResolutionTier.coarseRatio(2048, 1536))
        // 折叠屏内屏这种特殊比例不硬套
        assertEquals(null, ResolutionTier.coarseRatio(2200, 2480))
    }

    @Test
    fun `非法尺寸不崩`() {
        assertEquals("未知", ResolutionTier.of(0, 0))
        assertTrue(ResolutionTier.describe(2400, 1080).contains("1080p"))
    }
}
