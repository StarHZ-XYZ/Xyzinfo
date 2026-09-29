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
            "Snapdragon 8 Gen 3",
            SocSpecRepository.findBestSpec(listOf("SM8650"), emptyList(), null)?.displayName
        )
        assertEquals(
            "Snapdragon 8 Elite",
            SocSpecRepository.findBestSpec(listOf("sm8750"), emptyList(), null)?.displayName
        )
    }

    @Test
    fun `去掉厂商前缀后仍能匹配`() {
        assertEquals(
            "Snapdragon 8 Gen 2",
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
            "Snapdragon 860",
            SocSpecRepository.findBestSpec(emptyList(), listOf("VAYU"), null)?.displayName
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
