package com.rjy.xyz.apps.xyzinfo.model

enum class BatteryStatus {
    CHARGING,
    DISCHARGING,
    FULL,
    NOT_CHARGING,
    UNKNOWN
}

enum class BatteryHealth {
    GOOD,
    OVERHEAT,
    DEAD,
    OVER_VOLTAGE,
    FAILURE,
    COLD,
    UNKNOWN
}

enum class PowerSource {
    NONE,
    AC,
    USB,
    WIRELESS,
    DOCK,
    UNKNOWN
}

/**
 * 电池信息快照。
 *
 * [percent] 为 -1 表示系统未公开电量，
 * [voltageMv] / [temperatureTenths] 为 -1 表示对应节点未公开。
 */
data class BatteryInfo(
    val levelRaw: Int,
    val scaleRaw: Int,
    val percent: Int,
    val status: BatteryStatus,
    val health: BatteryHealth,
    val powerSource: PowerSource,
    val voltageMv: Int,
    val temperatureTenths: Int,
    val technology: String,
    val chargeCounterUah: Int?,
    val currentNowUa: Int?,
    val currentAverageUa: Int?,
    val energyCounterNwh: Int?,
    val designCapacityMah: Int?,
    val fullCapacityMah: Int?,
    val cycleCount: Int?,
    val remainingCapacityMah: Int?,
    val rawPreview: String
)
