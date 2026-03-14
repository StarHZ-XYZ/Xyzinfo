package com.rjy.xyz.apps.xyzinfo

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.util.Locale

class SensorInfoActivity : AppCompatActivity() {

    private lateinit var tvSensorTitle: TextView
    private lateinit var tvSensorSubTitle: TextView

    private lateinit var tvThermalSummary: TextView
    private lateinit var tvThermalList: TextView
    private lateinit var tvSensorSummary: TextView
    private lateinit var tvSensorList: TextView

    private lateinit var cardHeader: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensor_info)

        initViews()
        loadSensorInfo()
    }

    private fun initViews() {
        tvSensorTitle = findViewById(R.id.tvSensorTitle)
        tvSensorSubTitle = findViewById(R.id.tvSensorSubTitle)

        tvThermalSummary = findViewById(R.id.tvThermalSummary)
        tvThermalList = findViewById(R.id.tvThermalList)
        tvSensorSummary = findViewById(R.id.tvSensorSummary)
        tvSensorList = findViewById(R.id.tvSensorList)

        cardHeader = findViewById(R.id.cardHeader)
    }

    private fun loadSensorInfo() {
        val thermalZones = readThermalZones()
        val thermalSummary = buildThermalSummary(thermalZones)
        val thermalText = buildThermalListText(thermalZones)

        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        val sensorSummary = buildSensorSummary(sensors)
        val sensorText = buildSensorListText(sensors)

        tvSensorTitle.text = "传感器与热区"
        tvSensorSubTitle.text = "温度传感器、热区与常规硬件传感器总览"

        tvThermalSummary.text = thermalSummary
        tvThermalList.text = thermalText

        tvSensorSummary.text = sensorSummary
        tvSensorList.text = sensorText
    }

    private data class ThermalZoneInfo(
        val index: Int,
        val rawType: String,
        val displayType: String,
        val tempCelsius: Double?
    )

    private fun readThermalZones(): List<ThermalZoneInfo> {
        val baseDir = File("/sys/class/thermal")
        if (!baseDir.exists() || !baseDir.isDirectory) return emptyList()

        val zones = baseDir.listFiles()
            ?.filter { it.name.startsWith("thermal_zone") }
            ?.sortedBy {
                it.name.removePrefix("thermal_zone").toIntOrNull() ?: Int.MAX_VALUE
            }
            ?: emptyList()

        val result = mutableListOf<ThermalZoneInfo>()

        for (zone in zones) {
            val index = zone.name.removePrefix("thermal_zone").toIntOrNull() ?: -1
            val rawType = readFileText(File(zone, "type").absolutePath).trim().ifBlank { "unknown" }
            val rawTemp = readFileText(File(zone, "temp").absolutePath).trim()
            val tempCelsius = normalizeThermalTemp(rawTemp)
            val displayType = mapThermalType(rawType)

            result.add(
                ThermalZoneInfo(
                    index = index,
                    rawType = rawType,
                    displayType = displayType,
                    tempCelsius = tempCelsius
                )
            )
        }

        return result
    }

    private fun normalizeThermalTemp(raw: String): Double? {
        val value = raw.toDoubleOrNull() ?: return null
        return when {
            value > 1000 -> value / 1000.0
            value > 200 -> value / 10.0
            value in -50.0..200.0 -> value
            else -> null
        }
    }

    private fun mapThermalType(type: String): String {
        val t = type.lowercase(Locale.getDefault())

        return when {
            "cpu" in t -> "CPU 温度"
            "gpu" in t -> "GPU 温度"
            "battery" in t || "batt" in t -> "电池温度"
            "skin" in t || "shell" in t || "xo_therm" in t -> "机身温度"
            "charger" in t || "charge" in t -> "充电温度"
            "soc" in t -> "SoC 温度"
            "npu" in t || "apu" in t -> "NPU / AI 温度"
            "modem" in t || "pa" in t -> "基带 / 射频温度"
            "wifi" in t || "wlan" in t -> "Wi-Fi 温度"
            "ddr" in t || "dram" in t -> "内存温度"
            "usb" in t -> "USB 温度"
            else -> "热区：$type"
        }
    }

    private fun buildThermalSummary(zones: List<ThermalZoneInfo>): String {
        if (zones.isEmpty()) return "热区概览：系统未公开 thermal zone"

        val cpuTemps = zones.filter { it.displayType.contains("CPU") }.mapNotNull { it.tempCelsius }
        val gpuTemps = zones.filter { it.displayType.contains("GPU") }.mapNotNull { it.tempCelsius }
        val batteryTemps = zones.filter { it.displayType.contains("电池") }.mapNotNull { it.tempCelsius }

        val cpuText = cpuTemps.maxOrNull()?.let { "CPU ${formatTemp(it)}" } ?: "CPU 未知"
        val gpuText = gpuTemps.maxOrNull()?.let { "GPU ${formatTemp(it)}" } ?: "GPU 未知"
        val battText = batteryTemps.maxOrNull()?.let { "电池 ${formatTemp(it)}" } ?: "电池 未知"

        return "热区数量：${zones.size} 个 ｜ $cpuText ｜ $gpuText ｜ $battText"
    }

    private fun buildThermalListText(zones: List<ThermalZoneInfo>): String {
        if (zones.isEmpty()) return "未读取到 thermal zone 信息"

        return zones.joinToString("\n\n") { zone ->
            buildString {
                append("热区 #${zone.index}\n")
                append("名称：${zone.displayType}\n")
                append("原始类型：${zone.rawType}\n")
                append("当前温度：${zone.tempCelsius?.let { formatTemp(it) } ?: "未知"}")
            }
        }
    }

    private fun buildSensorSummary(sensors: List<Sensor>): String {
        if (sensors.isEmpty()) return "硬件传感器：未检测到"

        val names = mutableListOf<String>()
        if (hasSensorType(sensors, Sensor.TYPE_ACCELEROMETER)) names += "加速度计"
        if (hasSensorType(sensors, Sensor.TYPE_GYROSCOPE)) names += "陀螺仪"
        if (hasSensorType(sensors, Sensor.TYPE_MAGNETIC_FIELD)) names += "磁力计"
        if (hasSensorType(sensors, Sensor.TYPE_LIGHT)) names += "光线"
        if (hasSensorType(sensors, Sensor.TYPE_PROXIMITY)) names += "距离"
        if (hasSensorType(sensors, Sensor.TYPE_PRESSURE)) names += "气压计"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && hasSensorType(sensors, Sensor.TYPE_STEP_COUNTER)) names += "计步器"

        return "硬件传感器数量：${sensors.size} 个 ｜ ${if (names.isNotEmpty()) names.joinToString(" / ") else "基础信息见下方"}"
    }

    private fun hasSensorType(sensors: List<Sensor>, type: Int): Boolean {
        return sensors.any { it.type == type }
    }

    private fun buildSensorListText(sensors: List<Sensor>): String {
        if (sensors.isEmpty()) return "系统未检测到常规传感器"

        return sensors.sortedBy { it.type }.joinToString("\n\n") { sensor ->
            buildString {
                append("名称：${sensor.name}\n")
                append("类型：${mapSensorType(sensor.type)}\n")
                append("厂商：${sensor.vendor}\n")
                append("版本：${sensor.version}\n")
                append("最大量程：${formatFloat(sensor.maximumRange)}\n")
                append("分辨率：${formatFloat(sensor.resolution)}\n")
                append("功耗：${formatFloat(sensor.power)} mA")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    append("\n动态传感器：${if (sensor.isDynamicSensor) "是" else "否"}")
                }

                append("\n最小延迟：${sensor.minDelay} μs")
            }
        }
    }

    private fun mapSensorType(type: Int): String {
        return when (type) {
            Sensor.TYPE_ACCELEROMETER -> "加速度计"
            Sensor.TYPE_MAGNETIC_FIELD -> "磁力计"
            Sensor.TYPE_ORIENTATION -> "方向传感器"
            Sensor.TYPE_GYROSCOPE -> "陀螺仪"
            Sensor.TYPE_LIGHT -> "光线传感器"
            Sensor.TYPE_PRESSURE -> "气压计"
            Sensor.TYPE_TEMPERATURE -> "温度传感器（旧）"
            Sensor.TYPE_PROXIMITY -> "距离传感器"
            Sensor.TYPE_GRAVITY -> "重力传感器"
            Sensor.TYPE_LINEAR_ACCELERATION -> "线性加速度"
            Sensor.TYPE_ROTATION_VECTOR -> "旋转矢量"
            Sensor.TYPE_RELATIVE_HUMIDITY -> "湿度传感器"
            Sensor.TYPE_AMBIENT_TEMPERATURE -> "环境温度"
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "未校准磁力计"
            Sensor.TYPE_GAME_ROTATION_VECTOR -> "游戏旋转矢量"
            Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "未校准陀螺仪"
            Sensor.TYPE_SIGNIFICANT_MOTION -> "显著运动"
            Sensor.TYPE_STEP_DETECTOR -> "步行检测"
            Sensor.TYPE_STEP_COUNTER -> "计步器"
            Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "地磁旋转矢量"
            Sensor.TYPE_HEART_RATE -> "心率"
            Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "未校准加速度计"
            Sensor.TYPE_POSE_6DOF -> "6DoF 姿态"
            Sensor.TYPE_STATIONARY_DETECT -> "静止检测"
            Sensor.TYPE_MOTION_DETECT -> "运动检测"
            Sensor.TYPE_HEART_BEAT -> "心跳"
            Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> "离身检测"
            else -> "类型 ID: $type"
        }
    }

    private fun formatTemp(value: Double): String {
        return String.format(Locale.getDefault(), "%.1f ℃", value)
    }

    private fun formatFloat(value: Float): String {
        return String.format(Locale.getDefault(), "%.3f", value)
    }

    private fun readFileText(path: String): String {
        return try {
            File(path).readText()
        } catch (_: Exception) {
            ""
        }
    }
}