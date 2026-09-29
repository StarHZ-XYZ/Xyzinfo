package com.rjy.xyz.apps.xyzinfo.data.soc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SoC 规格匹配算法测试。
 */
class SocSpecRepositoryTest {

    @Test
    fun `型号别名完全一致时命中对应规格`() {
        assertEquals(
            "骁龙 8 Gen 3",
            SocSpecRepository.findBestSpec(listOf("SM8650"), emptyList(), null)?.displayName
        )
        assertEquals(
            "骁龙 8 Elite",
            SocSpecRepository.findBestSpec(listOf("sm8750"), emptyList(), null)?.displayName
        )
        // 带后缀的型号（真实机型常见 SM8650-AB）
        assertEquals(
            "骁龙 8 Gen 3",
            SocSpecRepository.findBestSpec(listOf("SM8650-AB"), emptyList(), null)?.displayName
        )
        // 2025-2026 新增芯片
        assertEquals(
            "骁龙 8 Elite Gen 5",
            SocSpecRepository.findBestSpec(listOf("SM8850"), emptyList(), null)?.displayName
        )
        assertEquals(
            "天玑 9500",
            SocSpecRepository.findBestSpec(listOf("MT6993"), emptyList(), null)?.displayName
        )
        assertEquals(
            "玄戒 O1",
            SocSpecRepository.findBestSpec(listOf("XRING O1"), emptyList(), null)?.displayName
        )
        assertEquals(
            "Google Tensor G3",
            SocSpecRepository.findBestSpec(listOf("gs301"), emptyList(), null)?.displayName
        )
    }

    @Test
    fun `去掉厂商前缀后仍能匹配`() {
        assertEquals(
            "骁龙 8 Gen 2",
            SocSpecRepository.findBestSpec(
                listOf("Qualcomm Snapdragon 8 Gen 2"),
                emptyList(),
                null
            )?.displayName
        )
    }

    @Test
    fun `设备关键词命中时优先返回该机型对应芯片`() {
        assertEquals(
            "骁龙 860",
            SocSpecRepository.findBestSpec(emptyList(), listOf("VAYU"), null)?.displayName
        )
    }

    @Test
    fun `过短的别名只接受精确匹配`() {
        // "O1" 是玄戒 O1 的短别名，不能被别的型号串里出现 "O1" 就误判
        assertNull(SocSpecRepository.findBestSpec(listOf("TENSORO1"), emptyList(), null))
        assertEquals(
            "玄戒 O1",
            SocSpecRepository.findBestSpec(listOf("O1"), emptyList(), null)?.displayName
        )
    }

    @Test
    fun `无法识别时返回 null`() {
        assertNull(SocSpecRepository.findBestSpec(listOf("完全未知的芯片"), emptyList(), null))
        assertNull(SocSpecRepository.findBestSpec(emptyList(), emptyList(), null))
    }

    @Test
    fun `normalize 统一大小写并去掉分隔符`() {
        assertEquals("SM8650", SocSpecRepository.normalize(" sm-8650 "))
        assertEquals("SNAPDRAGON8GEN3", SocSpecRepository.normalize("Qualcomm Snapdragon 8 Gen 3"))
        assertEquals("DIMENSITY7200", SocSpecRepository.normalize("MediaTek Dimensity 7200"))
    }
}
