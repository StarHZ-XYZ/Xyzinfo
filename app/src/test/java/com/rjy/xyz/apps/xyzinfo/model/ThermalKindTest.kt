package com.rjy.xyz.apps.xyzinfo.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 热区类型归类测试。
 */
class ThermalKindTest {

    @Test
    fun `常见热区名称归类正确`() {
        assertEquals(ThermalKind.CPU, ThermalKind.of("cpu0-thermal"))
        assertEquals(ThermalKind.GPU, ThermalKind.of("gpu"))
        assertEquals(ThermalKind.BATTERY, ThermalKind.of("battery"))
        assertEquals(ThermalKind.BODY, ThermalKind.of("skin-therm"))
        assertEquals(ThermalKind.CHARGER, ThermalKind.of("charger_temp"))
        assertEquals(ThermalKind.WIFI, ThermalKind.of("wlan"))
        assertEquals(ThermalKind.MEMORY, ThermalKind.of("ddr"))
    }

    @Test
    fun `电池热区优先于充电热区`() {
        assertEquals(ThermalKind.BATTERY, ThermalKind.of("battery_charge"))
    }

    @Test
    fun `未知名称归入 OTHER`() {
        assertEquals(ThermalKind.OTHER, ThermalKind.of("unknown_sensor"))
    }

    @Test
    fun `大小写不影响归类`() {
        assertEquals(ThermalKind.CPU, ThermalKind.of("CPU-THERM"))
    }
}
