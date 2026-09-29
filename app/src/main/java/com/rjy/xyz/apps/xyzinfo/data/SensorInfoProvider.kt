package com.rjy.xyz.apps.xyzinfo.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import com.rjy.xyz.apps.xyzinfo.model.SensorEntry
import com.rjy.xyz.apps.xyzinfo.model.SensorInfo
import com.rjy.xyz.apps.xyzinfo.model.ThermalKind
import com.rjy.xyz.apps.xyzinfo.model.ThermalZone
import com.rjy.xyz.apps.xyzinfo.util.ProcFs
import java.io.File

/**
 * 读取热区温度与硬件传感器列表。
 */
object SensorInfoProvider {

    private const val THERMAL_DIR = "/sys/class/thermal"
    private const val THERMAL_ZONE_PREFIX = "thermal_zone"

    fun load(context: Context): SensorInfo {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
            .map { it.toEntry() }
            .sortedBy { it.typeId }

        return SensorInfo(
            thermalZones = readThermalZones(),
            sensors = sensors
        )
    }

    private fun readThermalZones(): List<ThermalZone> =
        ProcFs.listFiles(THERMAL_DIR)
            .filter { it.isDirectory && it.name.startsWith(THERMAL_ZONE_PREFIX) }
            .sortedBy { it.thermalSortKey() }
            .map { zone ->
                val rawType = ProcFs.readText(File(zone, "type").absolutePath)
                    .trim()
                    .ifBlank { "unknown" }
                val rawTemp = ProcFs.readText(File(zone, "temp").absolutePath).trim()
                ThermalZone(
                    index = zone.thermalIndex(),
                    rawType = rawType,
                    kind = ThermalKind.of(rawType),
                    celsius = normalizeTemperature(rawTemp)
                )
            }

    /** 热区节点单位可能是 0.001 ℃ 或 0.1 ℃，统一换算成摄氏度。 */
    private fun normalizeTemperature(raw: String): Double? {
        val value = raw.toDoubleOrNull() ?: return null
        return when {
            value > 1000 -> value / 1000.0
            value > 200 -> value / 10.0
            value in -50.0..200.0 -> value
            else -> null
        }
    }

    /** thermal_zone12 -> 12，解析失败时返回 -1。 */
    private fun File.thermalIndex(): Int =
        name.removePrefix(THERMAL_ZONE_PREFIX).toIntOrNull() ?: -1

    /** 排序用，解析失败的节点排在最后。 */
    private fun File.thermalSortKey(): Int =
        name.removePrefix(THERMAL_ZONE_PREFIX).toIntOrNull() ?: Int.MAX_VALUE

    private fun Sensor.toEntry(): SensorEntry = SensorEntry(
        name = name,
        typeId = type,
        vendor = vendor,
        version = version,
        maximumRange = maximumRange,
        resolution = resolution,
        power = power,
        dynamic = isDynamicSensor,
        minDelayUs = minDelay
    )
}
