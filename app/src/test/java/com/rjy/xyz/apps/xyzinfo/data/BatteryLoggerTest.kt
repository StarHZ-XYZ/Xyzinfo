package com.rjy.xyz.apps.xyzinfo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 电池容量 / 健康度估算（1.0.6）。
 *
 * 核心公式：实际满容量 ≈ 流过的电量 ΔQ ÷ 电量变化 ΔSOC × 100%。
 * ΔQ 优先用库仑计（charge counter），没有就退到电流积分。
 */
class BatteryLoggerTest {

    private fun sample(
        minute: Long,
        percent: Int,
        currentUa: Int? = null,
        chargeCounterUah: Int? = null
    ) = BatteryLogger.Sample(
        timestamp = minute * 60_000L,
        percent = percent,
        voltageMv = 3800,
        currentUa = currentUa,
        temperatureC = 32.0,
        charging = false,
        chargeCounterUah = chargeCounterUah
    )

    @Test
    fun `库仑计优先_放电一成对应 950mAh 就是 9500mAh 满容量`() {
        val samples = listOf(
            sample(0, 80, chargeCounterUah = 3_000_000),
            sample(5, 75, chargeCounterUah = 2_525_000),
            sample(10, 70, chargeCounterUah = 2_050_000)
        )
        val estimate = BatteryLogger.estimateFrom(samples, designCapacityMah = 10_000)
        assertEquals("方法应该是库仑计", "库仑计", estimate.method)
        assertEquals(9500, estimate.fullCapacityMah)
        assertEquals(95, estimate.healthPercent)
        assertEquals(10, estimate.levelDelta)
    }

    @Test
    fun `没有库仑计就用电流积分`() {
        // 40 分钟里以 1.32A 放电，电量从 100% 掉到 80%：
        // 电量 = 1.32A × (2/3)h = 0.88Ah = 880mAh，880 / 20% × 100% = 4400mAh
        val samples = listOf(
            sample(0, 100, currentUa = -1_320_000),
            sample(10, 95, currentUa = -1_320_000),
            sample(20, 90, currentUa = -1_320_000),
            sample(30, 85, currentUa = -1_320_000),
            sample(40, 80, currentUa = -1_320_000)
        )
        val estimate = BatteryLogger.estimateFrom(samples, designCapacityMah = 4500)
        assertEquals("电流积分", estimate.method)
        assertEquals(4400, estimate.fullCapacityMah)
        assertEquals("4400/4500 = 98%", 98, estimate.healthPercent)
        assertEquals("优秀", BatteryLogger.healthGrade(estimate.healthPercent))
        assertEquals(40, estimate.spanMinutes)
    }

    @Test
    fun `电量变化太小就不出结果`() {
        val samples = listOf(
            sample(0, 62, currentUa = -500_000),
            sample(10, 61, currentUa = -500_000),
            sample(20, 60, currentUa = -500_000)
        )
        val estimate = BatteryLogger.estimateFrom(samples, designCapacityMah = 4500)
        assertNull("变化只有 2%，不能硬算", estimate.fullCapacityMah)
        assertTrue("要给出原因：${estimate.hint}", estimate.hint.contains("5%"))
    }

    @Test
    fun `记录太少时给出提示而不是空白`() {
        val estimate = BatteryLogger.estimateFrom(listOf(sample(0, 50)), designCapacityMah = 4500)
        assertNull(estimate.fullCapacityMah)
        assertEquals(4500, estimate.designCapacityMah)
        assertTrue(estimate.hint.isNotBlank())
    }

    @Test
    fun `间隔太久会切成两段_各算各的`() {
        // 中间空了 40 分钟：不能把两段拼起来当一次放电
        val samples = listOf(
            sample(0, 90, chargeCounterUah = 4_000_000),
            sample(1, 89, chargeCounterUah = 3_950_000),
            sample(2, 88, chargeCounterUah = 3_900_000),
            sample(42, 70, chargeCounterUah = 3_000_000),
            sample(43, 55, chargeCounterUah = 2_250_000),
            sample(44, 40, chargeCounterUah = 1_500_000)
        )
        val estimate = BatteryLogger.estimateFrom(samples, designCapacityMah = 4500)
        // 第二段：ΔQ = 1,500,000µAh = 1500mAh，ΔSOC = 30% → 5000mAh
        assertNotNull(estimate.fullCapacityMah)
        assertEquals(5000, estimate.fullCapacityMah)
        assertEquals(30, estimate.levelDelta)
    }

    @Test
    fun `电流与健康度文案`() {
        assertTrue(BatteryLogger.formatCurrent(-1_250_000, 3800).contains("-1250 mA"))
        assertTrue(BatteryLogger.formatCurrent(2_500_000, 4000).contains("+2500 mA"))
        assertTrue(BatteryLogger.formatCurrent(2_500_000, 4000).contains("10.00 W"))
        assertEquals("—", BatteryLogger.formatCurrent(null, 4000))
        assertEquals("良好", BatteryLogger.healthGrade(85))
        assertEquals("偏差（建议换电池）", BatteryLogger.healthGrade(60))
    }
}
