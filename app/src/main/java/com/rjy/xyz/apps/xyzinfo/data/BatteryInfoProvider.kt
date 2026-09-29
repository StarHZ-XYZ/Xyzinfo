package com.rjy.xyz.apps.xyzinfo.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.rjy.xyz.apps.xyzinfo.model.BatteryHealth
import com.rjy.xyz.apps.xyzinfo.model.BatteryInfo
import com.rjy.xyz.apps.xyzinfo.model.BatteryStatus
import com.rjy.xyz.apps.xyzinfo.model.PowerSource
import com.rjy.xyz.apps.xyzinfo.util.ProcFs
import kotlin.math.roundToInt

/**
 * 读取电池电量、容量、电流、循环次数等信息。
 */
object BatteryInfoProvider {

    private val DESIGN_CAPACITY_PATHS = listOf(
        "/sys/class/power_supply/battery/charge_full_design",
        "/sys/class/power_supply/battery/batt_full_design",
        "/sys/class/power_supply/battery/energy_full_design",
        "/sys/class/power_supply/maxfg/capacity_raw"
    )

    private val FULL_CAPACITY_PATHS = listOf(
        "/sys/class/power_supply/battery/charge_full",
        "/sys/class/power_supply/battery/batt_full",
        "/sys/class/power_supply/battery/energy_full",
        "/sys/class/power_supply/battery/fg_fullcapnom"
    )

    private val CYCLE_COUNT_PATHS = listOf(
        "/sys/class/power_supply/battery/cycle_count",
        "/sys/class/power_supply/maxfg/cycle_count",
        "/sys/class/power_supply/bms/cycle_count"
    )

    fun load(context: Context): BatteryInfo {
        val batteryIntent = batteryStatusIntent(context)
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentFromIntent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val capacityProperty = property(batteryManager, BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val percent = if (percentFromIntent >= 0) percentFromIntent else capacityProperty ?: -1

        val chargeCounterUah = property(batteryManager, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val fullCapacityMah = readCapacity(FULL_CAPACITY_PATHS)
        val currentNowUa = property(batteryManager, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentAverageUa = property(batteryManager, BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        val energyCounterNwh = property(batteryManager, BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val health = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val temperatureTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val technology = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty()

        return BatteryInfo(
            levelRaw = level,
            scaleRaw = scale,
            percent = percent,
            status = mapStatus(status),
            health = mapHealth(health),
            powerSource = mapPowerSource(plugged),
            voltageMv = voltageMv,
            temperatureTenths = temperatureTenths,
            technology = technology,
            chargeCounterUah = chargeCounterUah,
            currentNowUa = currentNowUa,
            currentAverageUa = currentAverageUa,
            energyCounterNwh = energyCounterNwh,
            designCapacityMah = readCapacity(DESIGN_CAPACITY_PATHS),
            fullCapacityMah = fullCapacityMah,
            cycleCount = readCycleCount(),
            remainingCapacityMah = estimateRemainingCapacity(chargeCounterUah, fullCapacityMah, percent),
            rawPreview = buildRawPreview(
                level = level,
                scale = scale,
                percent = percent,
                status = status,
                health = health,
                plugged = plugged,
                voltageMv = voltageMv,
                temperatureTenths = temperatureTenths,
                technology = technology,
                chargeCounterUah = chargeCounterUah,
                currentNowUa = currentNowUa,
                currentAverageUa = currentAverageUa,
                energyCounterNwh = energyCounterNwh
            )
        )
    }

    /** 原始字段预览，便于机型适配时核对系统返回值。 */
    private fun buildRawPreview(
        level: Int,
        scale: Int,
        percent: Int,
        status: Int,
        health: Int,
        plugged: Int,
        voltageMv: Int,
        temperatureTenths: Int,
        technology: String,
        chargeCounterUah: Int?,
        currentNowUa: Int?,
        currentAverageUa: Int?,
        energyCounterNwh: Int?
    ): String = """
        原始电池信息预览：
        level: $level
        scale: $scale
        percent: $percent
        status: $status
        health: $health
        plugged: $plugged
        voltage_mV: $voltageMv
        temperature_0.1C: $temperatureTenths
        technology: ${technology.ifBlank { "未知" }}
        chargeCounter_uAh: ${chargeCounterUah ?: "未知"}
        currentNow_uA: ${currentNowUa ?: "未知"}
        currentAverage_uA: ${currentAverageUa ?: "未知"}
        energyCounter_nWh: ${energyCounterNwh ?: "未知"}
        model: ${Build.MODEL.ifBlank { "未知" }}
        device: ${Build.DEVICE.ifBlank { "未知" }}
    """.trimIndent()

    /**
     * 读取电池广播快照。
     *
     * Android 13 起注册非导出接收器必须显式声明权限标记。
     */
    private fun batteryStatusIntent(context: Context): Intent? {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(null as BroadcastReceiver?, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(null, filter)
        }
    }

    /** [BatteryManager.getIntProperty] 用 Int.MIN_VALUE 表示不支持，这里统一转成 null。 */
    private fun property(batteryManager: BatteryManager, id: Int): Int? {
        val value = runCatching { batteryManager.getIntProperty(id) }.getOrNull() ?: return null
        return value.takeIf { it != Int.MIN_VALUE }
    }

    /** 设备节点单位不统一（µAh / mAh），统一换算为 mAh。 */
    private fun readCapacity(paths: List<String>): Int? {
        for (path in paths) {
            val raw = ProcFs.readInt(path) ?: continue
            if (raw <= 0) continue
            when {
                raw > 100_000 -> return (raw / 1000.0).roundToInt()
                raw in 500..10_000 -> return raw
            }
        }
        return null
    }

    private fun readCycleCount(): Int? {
        for (path in CYCLE_COUNT_PATHS) {
            val value = ProcFs.readInt(path)
            if (value != null && value >= 0) return value
        }
        return null
    }

    /** 优先用库仑计读数，其次按满充容量与百分比估算。 */
    private fun estimateRemainingCapacity(
        chargeCounterUah: Int?,
        fullCapacityMah: Int?,
        percent: Int
    ): Int? = when {
        chargeCounterUah != null && chargeCounterUah > 0 ->
            (chargeCounterUah / 1000.0).roundToInt()
        fullCapacityMah != null && percent in 0..100 ->
            (fullCapacityMah * (percent / 100.0)).roundToInt()
        else -> null
    }

    private fun mapStatus(status: Int): BatteryStatus = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> BatteryStatus.CHARGING
        BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryStatus.DISCHARGING
        BatteryManager.BATTERY_STATUS_FULL -> BatteryStatus.FULL
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryStatus.NOT_CHARGING
        else -> BatteryStatus.UNKNOWN
    }

    private fun mapHealth(health: Int): BatteryHealth = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
        BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.FAILURE
        BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
        else -> BatteryHealth.UNKNOWN
    }

    private fun mapPowerSource(plugged: Int): PowerSource = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> PowerSource.AC
        BatteryManager.BATTERY_PLUGGED_USB -> PowerSource.USB
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> PowerSource.WIRELESS
        BatteryManager.BATTERY_PLUGGED_DOCK -> PowerSource.DOCK
        0 -> PowerSource.NONE
        else -> PowerSource.UNKNOWN
    }
}
