package com.rjy.xyz.apps.xyzinfo.ui.battery

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.data.BatteryInfoProvider
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityBatteryInfoBinding
import com.rjy.xyz.apps.xyzinfo.model.BatteryInfo
import com.rjy.xyz.apps.xyzinfo.util.Formats
import com.rjy.xyz.apps.xyzinfo.util.Labels

/**
 * 电池详情页，页面可见时每秒刷新一次实时数据。
 */
class BatteryInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBatteryInfoBinding

    private val refreshHandler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            render(BatteryInfoProvider.load(this@BatteryInfoActivity))
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBatteryInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        render(BatteryInfoProvider.load(this))
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

    private fun render(info: BatteryInfo) = with(binding) {
        tvBatteryTitle.text =
            if (info.percent >= 0) "${info.percent}% 电量" else "电池信息"
        tvBatterySubTitle.text =
            "${Labels.batteryStatus(info.status)} ｜ ${Labels.powerSource(info.powerSource)}"

        tvBatteryPercent.text = "当前电量：${percent(info.percent)}"
        tvBatteryRemainMah.text = "当前剩余容量：${milliAmpHour(info.remainingCapacityMah)}"
        tvBatteryCapacity.text = "额定 / 满充容量：${milliAmpHour(info.fullCapacityMah)}"
        tvBatteryDesignCapacity.text = "设计容量：${milliAmpHour(info.designCapacityMah)}"

        tvBatteryStatus.text = "电池状态：${Labels.batteryStatus(info.status)}"
        tvBatteryHealth.text = "电池健康：${Labels.batteryHealth(info.health)}"
        tvBatteryTechnology.text =
            "电池技术：${info.technology.ifBlank { Labels.NOT_PUBLIC }}"
        tvBatteryTemperature.text = "电池温度：${temperature(info.temperatureTenths)}"

        tvBatteryVoltage.text = "电池电压：${voltage(info.voltageMv)}"
        tvBatteryCurrentNow.text = "当前电流：${current(info)}"
        tvBatteryPowerSource.text = "供电来源：${Labels.powerSource(info.powerSource)}"
        tvBatteryCycleCount.text = "循环次数：${info.cycleCount ?: Labels.NOT_PUBLIC}"

        tvBatteryChargeCounter.text = "Charge Counter：${chargeCounter(info.chargeCounterUah)}"
        tvBatteryEnergyCounter.text = "Energy Counter：${energyCounter(info.energyCounterNwh)}"

        tvBatteryRawInfo.text = info.rawPreview
    }

    private fun percent(value: Int): String = if (value >= 0) "$value%" else Labels.UNKNOWN

    private fun milliAmpHour(value: Int?): String =
        value?.let { "$it mAh" } ?: Labels.NOT_PUBLIC

    private fun temperature(tenths: Int): String =
        if (tenths < 0) Labels.UNKNOWN else Formats.temperatureFromTenths(tenths)

    private fun voltage(voltageMv: Int): String =
        if (voltageMv <= 0) Labels.NOT_PUBLIC else "${Formats.volts(voltageMv)}（实时）"

    /** 实时电流优先取即时值，取不到时退回平均值。 */
    private fun current(info: BatteryInfo): String {
        val value = info.currentNowUa ?: info.currentAverageUa ?: return Labels.NOT_PUBLIC
        return "${Formats.milliamps(value)}（${Labels.currentDirection(value)}，实时）"
    }

    private fun chargeCounter(value: Int?): String =
        value?.let { "${Formats.microAmpHoursAsMilliAmpHours(it)} mAh" } ?: Labels.NOT_PUBLIC

    private fun energyCounter(value: Int?): String =
        value?.let { "${Formats.grouped(it)} nWh" } ?: Labels.NOT_PUBLIC

    private companion object {
        const val REFRESH_INTERVAL_MS = 1000L
    }
}
