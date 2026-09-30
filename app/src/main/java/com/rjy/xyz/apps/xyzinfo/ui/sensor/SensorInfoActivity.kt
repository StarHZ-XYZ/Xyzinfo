package com.rjy.xyz.apps.xyzinfo.ui.sensor

import android.hardware.Sensor
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.data.SensorInfoProvider
import com.rjy.xyz.apps.xyzinfo.databinding.ActivitySensorInfoBinding
import com.rjy.xyz.apps.xyzinfo.model.SensorEntry
import com.rjy.xyz.apps.xyzinfo.model.SensorInfo
import com.rjy.xyz.apps.xyzinfo.model.ThermalKind
import com.rjy.xyz.apps.xyzinfo.model.ThermalZone
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import com.rjy.xyz.apps.xyzinfo.ui.common.setRawBlock
import com.rjy.xyz.apps.xyzinfo.util.Formats
import com.rjy.xyz.apps.xyzinfo.util.Labels

/**
 * 热区温度与硬件传感器详情页。
 */
class SensorInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySensorInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySensorInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_HOME)

        render(SensorInfoProvider.load(this))
    }

    private fun render(info: SensorInfo) = with(binding) {
        tvSensorTitle.text = "传感器与热区"
        tvSensorSubTitle.text = "温度传感器、热区与常规硬件传感器总览"

        tvThermalSummary.setInfoRow(thermalSummary(info.thermalZones))
        tvThermalList.setRawBlock(thermalList(info.thermalZones))
        tvSensorSummary.setInfoRow(sensorSummary(info.sensors))
        tvSensorList.setRawBlock(sensorList(info.sensors))
    }

    private fun thermalSummary(zones: List<ThermalZone>): String {
        if (zones.isEmpty()) return "热区概览：系统未公开 thermal zone"

        val cpu = "CPU ${maxTemperature(zones, ThermalKind.CPU)?.let { Formats.temperature(it) } ?: Labels.UNKNOWN}"
        val gpu = "GPU ${maxTemperature(zones, ThermalKind.GPU)?.let { Formats.temperature(it) } ?: Labels.UNKNOWN}"
        val battery =
            "电池 ${maxTemperature(zones, ThermalKind.BATTERY)?.let { Formats.temperature(it) } ?: Labels.UNKNOWN}"

        return "热区数量：${zones.size} 个 ｜ $cpu ｜ $gpu ｜ $battery"
    }

    private fun maxTemperature(zones: List<ThermalZone>, kind: ThermalKind): Double? =
        zones.filter { it.kind == kind }.mapNotNull { it.celsius }.maxOrNull()

    private fun thermalList(zones: List<ThermalZone>): String {
        if (zones.isEmpty()) return "未读取到 thermal zone 信息"

        return zones.joinToString("\n\n") { zone ->
            buildString {
                append("热区 #${zone.index}\n")
                append("名称：${Labels.thermalZone(zone.kind, zone.rawType)}\n")
                append("原始类型：${zone.rawType}\n")
                append("当前温度：${zone.celsius?.let { Formats.temperature(it) } ?: Labels.UNKNOWN}")
            }
        }
    }

    private fun sensorSummary(sensors: List<SensorEntry>): String {
        if (sensors.isEmpty()) return "硬件传感器：未检测到"

        val names = HIGHLIGHT_SENSOR_TYPES
            .filter { typeId -> sensors.any { it.typeId == typeId } }
            .mapNotNull { Labels.sensorTypeOrNull(it) }

        val detail = if (names.isNotEmpty()) names.joinToString(" / ") else "基础信息见下方"
        return "硬件传感器数量：${sensors.size} 个 ｜ $detail"
    }

    private fun sensorList(sensors: List<SensorEntry>): String {
        if (sensors.isEmpty()) return "系统未检测到常规传感器"

        return sensors.joinToString("\n\n") { sensor ->
            buildString {
                append("名称：${sensor.name}\n")
                append("类型：${Labels.sensorType(sensor.typeId)}\n")
                append("厂商：${sensor.vendor}\n")
                append("版本：${sensor.version}\n")
                append("最大量程：${Formats.decimal(sensor.maximumRange.toDouble(), 3)}\n")
                append("分辨率：${Formats.decimal(sensor.resolution.toDouble(), 3)}\n")
                append("功耗：${Formats.decimal(sensor.power.toDouble(), 3)} mA")
                append("\n动态传感器：${if (sensor.dynamic) "是" else "否"}")
                append("\n最小延迟：${sensor.minDelayUs} μs")
            }
        }
    }

    private companion object {
        /** 汇总栏里优先展示的常见传感器。 */
        val HIGHLIGHT_SENSOR_TYPES = listOf(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_LIGHT,
            Sensor.TYPE_PROXIMITY,
            Sensor.TYPE_PRESSURE,
            Sensor.TYPE_STEP_COUNTER
        )
    }
}
