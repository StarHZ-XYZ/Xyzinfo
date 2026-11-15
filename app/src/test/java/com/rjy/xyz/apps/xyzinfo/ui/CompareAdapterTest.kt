package com.rjy.xyz.apps.xyzinfo.ui

import com.rjy.xyz.apps.xyzinfo.data.CompareRow
import com.rjy.xyz.apps.xyzinfo.data.DeviceSnapshot
import com.rjy.xyz.apps.xyzinfo.ui.compare.CompareAdapter
import com.rjy.xyz.apps.xyzinfo.ui.compare.CompareItem
import org.junit.Assert.assertEquals
import org.junit.Test

class CompareAdapterTest {

    @Test
    fun `对比表会显示对比机独有的相机参数`() {
        val a = DeviceSnapshot(
            appVersion = "1.1.2",
            deviceName = "本机",
            brand = "测试",
            rows = listOf(CompareRow("芯片", "型号", "测试芯片"))
        )
        val b = DeviceSnapshot(
            appVersion = "1.1.2",
            deviceName = "内置机",
            brand = "测试",
            rows = listOf(
                CompareRow("芯片", "型号", "内置芯片"),
                CompareRow("相机", "后置", "50 MP")
            )
        )
        val rows = CompareAdapter().buildCompareItems(a, b)
            .filterIsInstance<CompareItem.Row>()
        assertEquals(listOf("型号", "后置"), rows.map { it.a?.label ?: it.b?.label })
        assertEquals("50 MP", rows.last().b?.value)
    }
}
