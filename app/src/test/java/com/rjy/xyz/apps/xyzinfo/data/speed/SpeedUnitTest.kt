package com.rjy.xyz.apps.xyzinfo.data.speed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 测速单位换算与格式化的单元测试。 */
class SpeedUnitTest {

    @Test
    fun `Mb每秒与MB每秒之间是八倍关系`() {
        val bytesPerSecond = 12_500_000.0 // 12.5 MB/s
        assertEquals(100.0, SpeedUnit.MBPS.fromBytesPerSecond(bytesPerSecond), 1e-9)
        assertEquals(12.5, SpeedUnit.MBS.fromBytesPerSecond(bytesPerSecond), 1e-9)

        // 反算回字节/秒必须能对上，切换单位时才不会前后矛盾
        assertEquals(bytesPerSecond, SpeedUnit.MBPS.toBytesPerSecond(100.0), 1e-6)
        assertEquals(bytesPerSecond, SpeedUnit.MBS.toBytesPerSecond(12.5), 1e-6)
    }

    @Test
    fun `速度文案按量级保留小数`() {
        // 2.35 MB/s 这类小数值保留两位，128.4 只保留一位
        assertEquals("2.35", SpeedUnit.MBS.valueText(2_350_000.0))
        assertEquals("128.4", SpeedUnit.MBPS.valueText(16_050_000.0))
        assertEquals("128.4 MB/s", SpeedUnit.MBS.text(128_400_000.0))
    }

    @Test
    fun `存下来的单位名能还原_认不出来就回落到Mbps`() {
        assertEquals(SpeedUnit.MBS, SpeedUnit.fromName("MBS"))
        assertEquals(SpeedUnit.MBPS, SpeedUnit.fromName("MBPS"))
        assertEquals(SpeedUnit.MBPS, SpeedUnit.fromName(""))
        assertEquals(SpeedUnit.MBPS, SpeedUnit.fromName(null))
    }

    @Test
    fun `延迟文案缺值时显示破折号`() {
        assertEquals("—", formatMillis(null))
        assertEquals("42 ms", formatMillis(41.6))
        assertTrue(formatMillis(0.0).startsWith("0"))
    }
}
