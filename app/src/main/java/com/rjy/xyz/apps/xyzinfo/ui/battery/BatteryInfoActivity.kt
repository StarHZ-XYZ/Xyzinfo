package com.rjy.xyz.apps.xyzinfo.ui.battery

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.data.BatteryInfoProvider
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityBatteryInfoBinding
import com.rjy.xyz.apps.xyzinfo.model.BatteryInfo
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import com.rjy.xyz.apps.xyzinfo.ui.common.setRawBlock
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
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_NONE)

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

        tvBatteryPercent.setInfoRow("当前电量：${percent(info.percent)}")
        tvBatteryRemainMah.setInfoRow("当前剩余容量：${milliAmpHour(info.remainingCapacityMah)}")
        tvBatteryCapacity.setInfoRow("额定 / 满充容量：${milliAmpHour(info.fullCapacityMah)}")
        tvBatteryDesignCapacity.setInfoRow("设计容量：${milliAmpHour(info.designCapacityMah)}")

        tvBatteryStatus.setInfoRow("电池状态：${Labels.batteryStatus(info.status)}")
        tvBatteryHealth.setInfoRow("电池健康：${Labels.batteryHealth(info.health)}")
        tvBatteryTechnology.setInfoRow("电池技术：${info.technology.ifBlank { Labels.NOT_PUBLIC }}")
        tvBatteryTemperature.setInfoRow("电池温度：${temperature(info.temperatureTenths)}")

        tvBatteryVoltage.setInfoRow("电池电压：${voltage(info.voltageMv)}")
        tvBatteryCurrentNow.setInfoRow("当前电流：${current(info)}")
        tvBatteryPowerSource.setInfoRow("供电来源：${Labels.powerSource(info.powerSource)}")
        tvBatteryCycleCount.setInfoRow("循环次数：${info.cycleCount ?: Labels.NOT_PUBLIC}")

        tvBatteryChargeCounter.setInfoRow("Charge Counter：${chargeCounter(info.chargeCounterUah)}")
        tvBatteryEnergyCounter.setInfoRow("Energy Counter：${energyCounter(info.energyCounterNwh)}")

        tvBatteryRawInfo.setRawBlock(info.rawPreview)
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
