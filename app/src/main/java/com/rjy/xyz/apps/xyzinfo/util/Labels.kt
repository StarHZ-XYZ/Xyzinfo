package com.rjy.xyz.apps.xyzinfo.util

import android.hardware.Sensor
import com.rjy.xyz.apps.xyzinfo.model.BatteryHealth
import com.rjy.xyz.apps.xyzinfo.model.BatteryStatus
import com.rjy.xyz.apps.xyzinfo.model.PowerSource
import com.rjy.xyz.apps.xyzinfo.model.RootStatus
import com.rjy.xyz.apps.xyzinfo.model.ThermalKind
import com.rjy.xyz.apps.xyzinfo.model.TrebleStatus

/**
 * 系统枚举值 → 中文展示文案。
 *
 * 属于展示层逻辑，集中放在这里，避免每个页面各写一份 when。
 */
object Labels {

    const val UNKNOWN = "未知"
    const val NOT_PUBLIC = "系统未公开"

    fun batteryStatus(status: BatteryStatus): String = when (status) {
        BatteryStatus.CHARGING -> "充电中"
        BatteryStatus.DISCHARGING -> "放电中"
        BatteryStatus.FULL -> "已充满"
        BatteryStatus.NOT_CHARGING -> "未充电"
        BatteryStatus.UNKNOWN -> UNKNOWN
    }

    fun batteryHealth(health: BatteryHealth): String = when (health) {
        BatteryHealth.GOOD -> "良好"
        BatteryHealth.OVERHEAT -> "过热"
        BatteryHealth.DEAD -> "损坏"
        BatteryHealth.OVER_VOLTAGE -> "电压过高"
        BatteryHealth.FAILURE -> "状态异常"
        BatteryHealth.COLD -> "过冷"
        BatteryHealth.UNKNOWN -> UNKNOWN
    }

    fun powerSource(source: PowerSource): String = when (source) {
        PowerSource.AC -> "交流充电"
        PowerSource.USB -> "USB 充电"
        PowerSource.WIRELESS -> "无线充电"
        PowerSource.DOCK -> "底座供电"
        PowerSource.NONE -> "未外接电源"
        PowerSource.UNKNOWN -> UNKNOWN
    }

    /** 电流方向，依据电池电流正负判断。 */
    fun currentDirection(currentUa: Int): String = when {
        currentUa > 0 -> "充电输入"
        currentUa < 0 -> "放电消耗"
        else -> "静止"
    }

    fun thermalZone(kind: ThermalKind, rawType: String): String = when (kind) {
        ThermalKind.CPU -> "CPU 温度"
        ThermalKind.GPU -> "GPU 温度"
        ThermalKind.BATTERY -> "电池温度"
        ThermalKind.BODY -> "机身温度"
        ThermalKind.CHARGER -> "充电温度"
        ThermalKind.SOC -> "SoC 温度"
        ThermalKind.NPU -> "NPU / AI 温度"
        ThermalKind.MODEM -> "基带 / 射频温度"
        ThermalKind.WIFI -> "Wi-Fi 温度"
        ThermalKind.MEMORY -> "内存温度"
        ThermalKind.USB -> "USB 温度"
        ThermalKind.OTHER -> "热区：$rawType"
    }

    fun sensorType(typeId: Int): String = when (typeId) {
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
        else -> "类型 ID: $typeId"
    }

    /** 首页传感器汇总里用到的常见传感器名称。 */
    fun sensorTypeOrNull(typeId: Int): String? = when (typeId) {
        Sensor.TYPE_ACCELEROMETER -> "加速度计"
        Sensor.TYPE_GYROSCOPE -> "陀螺仪"
        Sensor.TYPE_MAGNETIC_FIELD -> "磁力计"
        Sensor.TYPE_LIGHT -> "光线"
        Sensor.TYPE_PROXIMITY -> "距离"
        Sensor.TYPE_PRESSURE -> "气压计"
        Sensor.TYPE_STEP_COUNTER -> "计步器"
        else -> null
    }

    /** SoC 性能等级。 */
    fun socLevelHint(level: String): String = when (level) {
        "顶级旗舰", "旗舰", "次旗舰", "中高端", "中端", "入门" -> "等级：$level"
        else -> "等级：待评定"
    }

    fun rootStatus(status: RootStatus): String = when (status) {
        RootStatus.LIKELY_ROOTED -> "疑似已 Root / 存在 su"
        RootStatus.CLEAN -> "未检测到明显 Root 痕迹"
    }

    fun trebleStatus(status: TrebleStatus): String = when (status) {
        TrebleStatus.CONFIG_PRESENT -> "已支持或系统包含 Treble 相关配置"
        TrebleStatus.LIKELY_SUPPORTED -> "大概率支持"
        TrebleStatus.UNKNOWN -> NOT_PUBLIC
    }
}
