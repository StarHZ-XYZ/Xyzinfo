package com.rjy.xyz.apps.xyzinfo.data

import com.rjy.xyz.apps.xyzinfo.model.DeviceOverview
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSummaryFormatterTest {

    @Test
    fun `设备摘要包含机型系统与硬件标识`() {
        val overview = DeviceOverview(
            displayName = "测试机型",
            rawModel = "TEST-01",
            androidRelease = "16",
            apiLevel = 36,
            romName = "TestOS 1.0",
            kernelRelease = "6.6.1",
            brand = "测试品牌",
            manufacturer = "测试厂商",
            deviceCode = "test_device",
            product = "test_product",
            abiLabel = "arm64-v8a",
            cpuArchitecture = "arm64"
        )
        val text = DeviceSummaryFormatter.build("测试机型 Pro", overview, "手机")
        assertTrue(text.startsWith("XYZ-Devinfo 设备摘要"))
        assertTrue(text.contains("机型：测试机型 Pro"))
        assertTrue(text.contains("品牌：测试品牌 / 测试厂商"))
        assertTrue(text.contains("Android：16（API 36）"))
        assertTrue(text.contains("设备形态：手机"))
        assertTrue(text.contains("设备代号：test_device"))
        assertTrue(text.endsWith("架构：arm64-v8a"))
    }
}
