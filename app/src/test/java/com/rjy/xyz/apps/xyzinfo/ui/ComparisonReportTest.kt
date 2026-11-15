package com.rjy.xyz.apps.xyzinfo.ui

import com.rjy.xyz.apps.xyzinfo.data.CompareRow
import com.rjy.xyz.apps.xyzinfo.data.DeviceSnapshot
import com.rjy.xyz.apps.xyzinfo.ui.compare.ComparisonReport
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparisonReportTest {

    @Test
    fun `对比文本包含两台设备与差异标记`() {
        val a = DeviceSnapshot(
            appVersion = "1.1.2",
            deviceName = "本机 A",
            brand = "测试",
            rows = listOf(
                CompareRow("芯片", "型号", "芯片 A"),
                CompareRow("电池", "设计容量", "5000 mAh", 5000.0)
            )
        )
        val b = DeviceSnapshot(
            appVersion = "1.1.2",
            deviceName = "对比机 B",
            brand = "测试",
            rows = listOf(
                CompareRow("芯片", "型号", "芯片 B"),
                CompareRow("电池", "设计容量", "5500 mAh", 5500.0)
            )
        )
        val report = ComparisonReport.build(a, b)
        assertTrue(report.contains("本机：本机 A"))
        assertTrue(report.contains("对比机：对比机 B"))
        assertTrue(report.contains("芯片 A | 芯片 B  ≠"))
        assertTrue(report.contains("5000 mAh | 5500 mAh  ≠"))
    }
}
