package com.rjy.xyz.apps.xyzinfo.data

import com.rjy.xyz.apps.xyzinfo.data.ThermalLogger.Sample
import com.rjy.xyz.apps.xyzinfo.data.ThermalLogger.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 温度监控重写（1.0.5）的核心逻辑：
 *
 * - 认得出哪些热区算 CPU（tsens / mtktscpu / s5p / apc / cluster…），哪些不算（gpu / 充电 / 摄像头）；
 * - CPU 温度取**多个传感器的平均**，不是只挑最热的那一个；
 * - 长表采样能收成"单条记录"（一次采样一行），新的在最前面，条数上限可用。
 */
class ThermalRecordsTest {

    @Test
    fun `认得出各平台的 CPU 热区`() {
        listOf(
            "cpu-0-0-us", "cpu_therm", "tsens_tz_sensor3", "mtktscpu",
            "s5p-tmu", "apc-thermal", "cluster0", "kryo-l3"
        ).forEach { assertTrue("$it 应该算 CPU 热区", ThermalLogger.isCpuZone(it)) }

        listOf(
            "gpu-therm", "battery", "charger_temp", "usb-therm",
            "camera0", "quiet_therm", "ambient"
        ).forEach { assertFalse("$it 不该算 CPU 热区", ThermalLogger.isCpuZone(it)) }
    }

    @Test
    fun `CPU 温度是多个传感器的平均`() {
        val zones = listOf(
            Zone("cpu-0-0-us", 50.0),
            Zone("cpu-1-0-us", 62.0),
            Zone("tsens_tz_sensor5", 44.0),
            Zone("battery", 35.0),
            Zone("gpu-therm", 58.0)
        )
        val cpu = ThermalLogger.cpuTemperature(zones)
        assertEquals("CPU = (50+62+44)/3", 52.0, cpu!!, 0.001)
        assertEquals("电池不该混进 CPU 平均", 35.0, ThermalLogger.batteryTemperature(null, zones)!!, 0.001)
    }

    @Test
    fun `没有 CPU 热区时退回最热的非电池热区`() {
        val zones = listOf(Zone("battery", 36.0), Zone("quiet_therm", 41.0), Zone("gpu-therm", 55.0))
        assertEquals(55.0, ThermalLogger.cpuTemperature(zones)!!, 0.001)
    }

    @Test
    fun `长表采样收成单条记录_新的在最前`() {
        val samples = listOf(
            Sample(1000L, "cpu-0", 40.0),
            Sample(1000L, "cpu-1", 50.0),
            Sample(1000L, "battery", 33.0),
            Sample(2000L, "cpu-0", 44.0),
            Sample(2000L, "battery", 34.0)
        )
        val records = ThermalLogger.recordsFrom(samples)
        assertEquals(2, records.size)

        val newest = records.first()
        assertEquals(2000L, newest.timestamp)
        assertEquals(44.0, newest.cpuCelsius!!, 0.001)
        assertEquals(34.0, newest.batteryCelsius!!, 0.001)

        val older = records[1]
        assertEquals(1000L, older.timestamp)
        assertEquals("两个 CPU 传感器要取平均", 45.0, older.cpuCelsius!!, 0.001)
        assertEquals(2, older.cpuSensorCount)
        assertEquals("顺带记下最热的那个", 50.0, older.cpuMaxCelsius!!, 0.001)
    }

    @Test
    fun `记录条数上限生效`() {
        val samples = (1..30).map { Sample(it * 1000L, "cpu-0", 40.0 + it) }
        assertEquals(5, ThermalLogger.recordsFrom(samples, limit = 5).size)
        assertEquals(30, ThermalLogger.recordsFrom(samples, limit = 2000).size)
    }

    @Test
    fun `热区名会翻译成人话`() {
        assertEquals("电池", ThermalLogger.label("battery"))
        assertEquals("GPU", ThermalLogger.label("gpu-therm"))
        assertEquals("CPU", ThermalLogger.label("cpu-0-0-us"))
        // 认不出来的原样返回，绝不丢信息
        assertEquals("vendor_xyz_sensor", ThermalLogger.label("vendor_xyz_sensor"))
    }
}
