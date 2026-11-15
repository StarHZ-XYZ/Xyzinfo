package com.rjy.xyz.apps.xyzinfo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceCatalogTest {

    @Test
    fun `内置设备库超过一百款且字段有效`() {
        val context = RuntimeEnvironment.getApplication()
        val devices = DeviceCatalog.load(context)
        assertTrue("内置设备不足 100 款，实际 ${devices.size}", devices.size >= 100)
        assertEquals(devices.size, devices.map { it.name.lowercase() }.distinct().size)
        assertTrue(devices.all { it.name.isNotBlank() && it.brand.isNotBlank() })
        assertTrue(devices.count { it.processor != "未公开" } >= 100)
        assertTrue(devices.count { it.batteryMah > 0 } >= 100)
    }

    @Test
    fun `内置设备可搜索并生成可序列化快照`() {
        val context = RuntimeEnvironment.getApplication()
        val results = DeviceCatalog.search(context, "骁龙")
        assertTrue(results.isNotEmpty())
        val combined = DeviceCatalog.search(context, "小米 骁龙")
        assertTrue("品牌与芯片组合搜索无结果", combined.isNotEmpty())
        assertTrue(combined.all { it.brand == "Xiaomi" && it.processor.contains("Snapdragon") })
        val brands = DeviceCatalog.brands(context)
        assertTrue("品牌筛选项不足", brands.size >= 15)
        val honor = DeviceCatalog.search(context, "", "Honor")
        assertTrue("荣耀品牌筛选无结果", honor.isNotEmpty())
        assertTrue(honor.all { it.brand == "Honor" })
        val snapshot = results.first().toSnapshot("1.1.2")
        val parsed = DeviceSnapshotStore.fromJson(DeviceSnapshotStore.toJson(snapshot))
        assertEquals(snapshot.deviceName, parsed?.deviceName)
        assertEquals(snapshot.rows.size, parsed?.rows?.size)
    }
}
