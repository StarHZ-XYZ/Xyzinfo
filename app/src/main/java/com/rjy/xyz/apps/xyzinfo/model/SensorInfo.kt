package com.rjy.xyz.apps.xyzinfo.model

/**
 * 热区（/sys/class/thermal）大类，用于汇总同类温度。
 */
enum class ThermalKind {
    CPU,
    GPU,
    BATTERY,
    BODY,
    CHARGER,
    SOC,
    NPU,
    MODEM,
    WIFI,
    MEMORY,
    USB,
    OTHER;

    companion object {
        /** 依据热区原始类型名归类。 */
        fun of(rawType: String): ThermalKind {
            val type = rawType.lowercase()
            return when {
                "cpu" in type -> CPU
                "gpu" in type -> GPU
                "battery" in type || "batt" in type -> BATTERY
                "skin" in type || "shell" in type || "xo_therm" in type -> BODY
                "charger" in type || "charge" in type -> CHARGER
                "soc" in type -> SOC
                "npu" in type || "apu" in type -> NPU
                "modem" in type || "pa" in type -> MODEM
                "wifi" in type || "wlan" in type -> WIFI
                "ddr" in type || "dram" in type -> MEMORY
                "usb" in type -> USB
                else -> OTHER
            }
        }
    }
}

data class ThermalZone(
    val index: Int,
    val rawType: String,
    val kind: ThermalKind,
    val celsius: Double?
)

/**
 * 单个硬件传感器的快照，[typeId] 对应 android.hardware.Sensor.TYPE_* 。
 */
data class SensorEntry(
    val name: String,
    val typeId: Int,
    val vendor: String,
    val version: Int,
    val maximumRange: Float,
    val resolution: Float,
    val power: Float,
    val dynamic: Boolean,
    val minDelayUs: Int
)

data class SensorInfo(
    val thermalZones: List<ThermalZone>,
    val sensors: List<SensorEntry>
)
