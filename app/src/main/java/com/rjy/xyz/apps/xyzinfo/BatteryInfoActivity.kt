package com.rjy.xyz.apps.xyzinfo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class BatteryInfoActivity : AppCompatActivity() {

    private lateinit var tvBatteryTitle: TextView
    private lateinit var tvBatterySubTitle: TextView

    private lateinit var tvBatteryPercent: TextView
    private lateinit var tvBatteryRemainMah: TextView
    private lateinit var tvBatteryCapacity: TextView
    private lateinit var tvBatteryDesignCapacity: TextView

    private lateinit var tvBatteryStatus: TextView
    private lateinit var tvBatteryHealth: TextView
    private lateinit var tvBatteryTechnology: TextView
    private lateinit var tvBatteryTemperature: TextView

    private lateinit var tvBatteryVoltage: TextView
    private lateinit var tvBatteryCurrentNow: TextView
    private lateinit var tvBatteryPowerSource: TextView
    private lateinit var tvBatteryCycleCount: TextView

    private lateinit var tvBatteryChargeCounter: TextView
    private lateinit var tvBatteryEnergyCounter: TextView
    private lateinit var tvBatteryRawInfo: TextView

    private lateinit var cardHeader: MaterialCardView

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadBatteryInfo()
            refreshHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battery_info)

        initViews()
        loadBatteryInfo()
    }

    override fun onResume() {
        super.onResume()
        refreshHandler.removeCallbacks(refreshRunnable)
        refreshHandler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    private fun initViews() {
        tvBatteryTitle = findViewById(R.id.tvBatteryTitle)
        tvBatterySubTitle = findViewById(R.id.tvBatterySubTitle)

        tvBatteryPercent = findViewById(R.id.tvBatteryPercent)
        tvBatteryRemainMah = findViewById(R.id.tvBatteryRemainMah)
        tvBatteryCapacity = findViewById(R.id.tvBatteryCapacity)
        tvBatteryDesignCapacity = findViewById(R.id.tvBatteryDesignCapacity)

        tvBatteryStatus = findViewById(R.id.tvBatteryStatus)
        tvBatteryHealth = findViewById(R.id.tvBatteryHealth)
        tvBatteryTechnology = findViewById(R.id.tvBatteryTechnology)
        tvBatteryTemperature = findViewById(R.id.tvBatteryTemperature)

        tvBatteryVoltage = findViewById(R.id.tvBatteryVoltage)
        tvBatteryCurrentNow = findViewById(R.id.tvBatteryCurrentNow)
        tvBatteryPowerSource = findViewById(R.id.tvBatteryPowerSource)
        tvBatteryCycleCount = findViewById(R.id.tvBatteryCycleCount)

        tvBatteryChargeCounter = findViewById(R.id.tvBatteryChargeCounter)
        tvBatteryEnergyCounter = findViewById(R.id.tvBatteryEnergyCounter)
        tvBatteryRawInfo = findViewById(R.id.tvBatteryRawInfo)

        cardHeader = findViewById(R.id.cardHeader)
    }

    private fun loadBatteryInfo() {
        val batteryIntent = registerReceiverCompat(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentFromIntent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val health = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val temperatureTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val technology = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty()

        val chargeCounterUah = getBatteryPropertySafe(bm, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val currentNowUa = getBatteryPropertySafe(bm, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentAverageUa = getBatteryPropertySafe(bm, BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        val energyCounterNwh = getBatteryPropertySafe(bm, BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
        val capacityPercentProperty = getBatteryPropertySafe(bm, BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val designCapacityMah = readDesignCapacityMah()
        val fullCapacityMah = readFullCapacityMah()
        val cycleCount = readCycleCount()

        val remainPercent = if (percentFromIntent >= 0) {
            percentFromIntent
        } else {
            capacityPercentProperty ?: -1
        }

        val remainMah = estimateRemainingMah(
            chargeCounterUah = chargeCounterUah,
            fullCapacityMah = fullCapacityMah,
            percent = remainPercent
        )

        val titleText = if (remainPercent >= 0) "$remainPercent% 电量" else "电池信息"
        val subText = buildBatterySubText(status, plugged)

        tvBatteryTitle.text = titleText
        tvBatterySubTitle.text = subText

        tvBatteryPercent.text = "当前电量：${if (remainPercent >= 0) "$remainPercent%" else "未知"}"
        tvBatteryRemainMah.text =
            "当前剩余容量：${remainMah?.let { "${formatMah(it)} mAh" } ?: "系统未公开"}"
        tvBatteryCapacity.text =
            "额定 / 满充容量：${fullCapacityMah?.let { "${formatMah(it)} mAh" } ?: "系统未公开"}"
        tvBatteryDesignCapacity.text =
            "设计容量：${designCapacityMah?.let { "${formatMah(it)} mAh" } ?: "系统未公开"}"

        tvBatteryStatus.text = "电池状态：${mapBatteryStatus(status)}"
        tvBatteryHealth.text = "电池健康：${mapBatteryHealth(health)}"
        tvBatteryTechnology.text = "电池技术：${if (technology.isNotBlank()) technology else "系统未公开"}"
        tvBatteryTemperature.text = "电池温度：${formatTemperature(temperatureTenths)}"

        tvBatteryVoltage.text = "电池电压：${formatVoltageRealtime(voltageMv)}"
        tvBatteryCurrentNow.text = "当前电流：${formatCurrentRealtime(currentNowUa, currentAverageUa)}"
        tvBatteryPowerSource.text = "供电来源：${mapPlugged(plugged)}"
        tvBatteryCycleCount.text = "循环次数：${cycleCount?.toString() ?: "系统未公开"}"

        tvBatteryChargeCounter.text =
            "Charge Counter：${chargeCounterUah?.let { "${formatUahToMah(it)} mAh" } ?: "系统未公开"}"
        tvBatteryEnergyCounter.text =
            "Energy Counter：${energyCounterNwh?.let { "${formatNwh(it)} nWh" } ?: "系统未公开"}"

        tvBatteryRawInfo.text = buildRawBatteryInfo(
            level = level,
            scale = scale,
            percent = remainPercent,
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
    }

    private fun registerReceiverCompat(
        receiver: BroadcastReceiver?,
        filter: IntentFilter
    ): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
    }

    private fun getBatteryPropertySafe(bm: BatteryManager, id: Int): Int? {
        return try {
            val value = bm.getIntProperty(id)
            if (value == Int.MIN_VALUE) null else value
        } catch (_: Exception) {
            null
        }
    }

    private fun buildBatterySubText(status: Int, plugged: Int): String {
        val statusText = mapBatteryStatus(status)
        val sourceText = mapPlugged(plugged)
        return "$statusText ｜ $sourceText"
    }

    private fun mapBatteryStatus(status: Int): String {
        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
            BatteryManager.BATTERY_STATUS_FULL -> "已充满"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
            BatteryManager.BATTERY_STATUS_UNKNOWN -> "未知"
            else -> "未知"
        }
    }

    private fun mapBatteryHealth(health: Int): String {
        return when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
            BatteryManager.BATTERY_HEALTH_DEAD -> "损坏"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "电压过高"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "状态异常"
            BatteryManager.BATTERY_HEALTH_COLD -> "过冷"
            BatteryManager.BATTERY_HEALTH_UNKNOWN -> "未知"
            else -> "未知"
        }
    }

    private fun mapPlugged(plugged: Int): String {
        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "交流充电"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB 充电"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线充电"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "底座供电"
            0 -> "未外接电源"
            else -> "未知"
        }
    }

    private fun formatTemperature(tempTenths: Int): String {
        if (tempTenths < 0) return "未知"
        return String.format(Locale.getDefault(), "%.1f ℃", tempTenths / 10.0)
    }

    private fun formatVoltageRealtime(voltageMv: Int): String {
        if (voltageMv <= 0) return "系统未公开"
        return String.format(Locale.getDefault(), "%.3f V（实时）", voltageMv / 1000.0)
    }

    private fun formatCurrentRealtime(currentNowUa: Int?, currentAverageUa: Int?): String {
        val current = currentNowUa ?: currentAverageUa ?: return "系统未公开"

        val absMa = abs(current) / 1000.0
        val direction = when {
            current > 0 -> "充电输入"
            current < 0 -> "放电消耗"
            else -> "静止"
        }

        return String.format(Locale.getDefault(), "%.0f mA（%s，实时）", absMa, direction)
    }

    private fun formatUahToMah(uah: Int): String {
        return String.format(Locale.getDefault(), "%.0f", uah / 1000.0)
    }

    private fun formatMah(value: Int): String {
        return value.toString()
    }

    private fun formatNwh(nwh: Int): String {
        return String.format(Locale.getDefault(), "%,d", nwh)
    }

    private fun estimateRemainingMah(
        chargeCounterUah: Int?,
        fullCapacityMah: Int?,
        percent: Int
    ): Int? {
        if (chargeCounterUah != null && chargeCounterUah > 0) {
            return (chargeCounterUah / 1000.0).roundToInt()
        }
        if (fullCapacityMah != null && percent in 0..100) {
            return (fullCapacityMah * (percent / 100.0)).roundToInt()
        }
        return null
    }

    private fun readDesignCapacityMah(): Int? {
        val paths = listOf(
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/battery/batt_full_design",
            "/sys/class/power_supply/battery/energy_full_design",
            "/sys/class/power_supply/maxfg/capacity_raw"
        )

        for (path in paths) {
            val raw = readIntFromFile(path) ?: continue
            val normalized = normalizeBatteryCapacityValue(raw)
            if (normalized != null) return normalized
        }
        return null
    }

    private fun readFullCapacityMah(): Int? {
        val paths = listOf(
            "/sys/class/power_supply/battery/charge_full",
            "/sys/class/power_supply/battery/batt_full",
            "/sys/class/power_supply/battery/energy_full",
            "/sys/class/power_supply/battery/fg_fullcapnom"
        )

        for (path in paths) {
            val raw = readIntFromFile(path) ?: continue
            val normalized = normalizeBatteryCapacityValue(raw)
            if (normalized != null) return normalized
        }
        return null
    }

    private fun normalizeBatteryCapacityValue(raw: Int): Int? {
        if (raw <= 0) return null
        return when {
            raw > 100000 -> (raw / 1000.0).roundToInt()
            raw in 500..10000 -> raw
            else -> null
        }
    }

    private fun readCycleCount(): Int? {
        val paths = listOf(
            "/sys/class/power_supply/battery/cycle_count",
            "/sys/class/power_supply/maxfg/cycle_count",
            "/sys/class/power_supply/bms/cycle_count"
        )

        for (path in paths) {
            val value = readIntFromFile(path)
            if (value != null && value >= 0) return value
        }
        return null
    }

    private fun readIntFromFile(path: String): Int? {
        return try {
            File(path).readText().trim().toIntOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun buildRawBatteryInfo(
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
    ): String {
        return """
            原始电池信息预览：
            level: $level
            scale: $scale
            percent: $percent
            status: $status
            health: $health
            plugged: $plugged
            voltage_mV: $voltageMv
            temperature_0.1C: $temperatureTenths
            technology: ${if (technology.isBlank()) "未知" else technology}
            chargeCounter_uAh: ${chargeCounterUah ?: "未知"}
            currentNow_uA: ${currentNowUa ?: "未知"}
            currentAverage_uA: ${currentAverageUa ?: "未知"}
            energyCounter_nWh: ${energyCounterNwh ?: "未知"}
            model: ${safe(Build.MODEL)}
            device: ${safe(Build.DEVICE)}
        """.trimIndent()
    }

    private fun safe(value: String?): String {
        return if (value.isNullOrBlank()) "未知" else value
    }
}