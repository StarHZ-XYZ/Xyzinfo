package com.rjy.xyz.apps.xyzinfo.ui.battery

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.data.BatteryInfoProvider
import com.rjy.xyz.apps.xyzinfo.data.BatteryLogger
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityBatteryInfoBinding
import com.rjy.xyz.apps.xyzinfo.model.BatteryInfo
import com.rjy.xyz.apps.xyzinfo.ui.common.Anim
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import com.rjy.xyz.apps.xyzinfo.ui.common.setRawBlock
import com.rjy.xyz.apps.xyzinfo.util.Formats
import com.rjy.xyz.apps.xyzinfo.util.Labels
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import android.widget.Toast

/**
 * 电池详情页，页面可见时每秒刷新一次实时数据。
 */
class BatteryInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBatteryInfoBinding
    private val logHandler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    private var logging = false

    /** 每 30 秒落一条：跑一段充放电就能推算容量。 */
    private val logTick = object : Runnable {
        override fun run() {
            if (!logging) return
            logOnce(notify = false)
            logHandler.postDelayed(this, LOG_INTERVAL_MS)
        }
    }

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

        logging = SettingsRepository.batteryLogEnabled(this)
        binding.switchBatteryLog.isChecked = logging
        binding.switchBatteryLog.setOnCheckedChangeListener { _, checked ->
            logging = checked
            SettingsRepository.setBatteryLogEnabled(this, checked)
            logHandler.removeCallbacks(logTick)
            if (checked) {
                logOnce(notify = false)
                logHandler.postDelayed(logTick, LOG_INTERVAL_MS)
                toast("已开启充放电记录（每 30 秒一条）")
            }
        }
        binding.btnBatteryLogNow.setOnClickListener {
            Anim.pressFeedback(it)
            logOnce(notify = true)
        }
        binding.btnBatteryClear.setOnClickListener {
            Anim.pressFeedback(it)
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("清空充放电记录？")
                .setMessage("本机保存的电池记录会被删除，之后重新积累才能估算容量。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空") { _, _ ->
                    BatteryLogger.clear(this)
                    refreshBatteryLog()
                    toast("电池记录已清空")
                }
                .show()
        }

        render(BatteryInfoProvider.load(this))
        refreshBatteryLog()
    }

    override fun onResume() {
        super.onResume()
        refreshHandler.removeCallbacks(refreshRunnable)
        refreshHandler.post(refreshRunnable)
        if (logging) logHandler.postDelayed(logTick, LOG_INTERVAL_MS)
        refreshBatteryLog()
    }

    override fun onPause() {
        super.onPause()
        refreshHandler.removeCallbacks(refreshRunnable)
        logHandler.removeCallbacks(logTick)
    }

    override fun onDestroy() {
        super.onDestroy()
        refreshHandler.removeCallbacks(refreshRunnable)
        logHandler.removeCallbacks(logTick)
    }

    // ---------- 充放电记录 ----------

    private fun logOnce(notify: Boolean) {
        Thread({
            val sample = runCatching { BatteryLogger.logSample(this) }.getOrNull()
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (notify) {
                    toast(
                        if (sample == null) "读不到电池信息，没能记录"
                        else "已记录：${sample.percent}% ｜ ${BatteryLogger.formatCurrent(sample.currentUa, sample.voltageMv)}"
                    )
                }
                refreshBatteryLog()
            }
        }, "xyzinfo-battery-log").start()
    }

    private fun refreshBatteryLog() {
        Thread({
            val samples = runCatching { BatteryLogger.samples(this) }.getOrDefault(emptyList())
            val estimate = runCatching { BatteryLogger.estimate(this) }.getOrNull()
            val (lines, bytes) = BatteryLogger.stats(this)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                binding.tvBatteryEstimate.text = estimateText(estimate)
                binding.tvBatteryLogStatus.text = buildString {
                    append("已记录 $lines 条")
                    if (bytes > 0) append("（${"%.1f".format(bytes / 1024.0)} KB）")
                    if (estimate != null && estimate.sampleCount > 0) {
                        append(" ｜ 用最长的一段算：${estimate.hint}")
                    }
                    if (logging) append(" ｜ 每 30 秒自动记一条")
                }
                renderSamples(samples)
            }
        }, "xyzinfo-battery-records").start()
    }

    private fun estimateText(estimate: BatteryLogger.CapacityEstimate?): String {
        if (estimate == null) return "实际容量：—"
        val design = estimate.designCapacityMah
        return buildString {
            append("实际容量：")
            append(estimate.fullCapacityMah?.let { "$it mAh" } ?: "还测不出来")
            if (design != null) append("　设计容量：$design mAh")
            append("\n健康度：")
            if (estimate.healthPercent != null) {
                append("${estimate.healthPercent}%（${BatteryLogger.healthGrade(estimate.healthPercent)}）")
            } else {
                append("—")
            }
            append("　方法：${estimate.method}")
            if (estimate.fullCapacityMah == null) {
                append("\n").append(estimate.hint)
            }
        }
    }

    private fun renderSamples(samples: List<BatteryLogger.Sample>) {
        binding.layoutBatterySamples.removeAllViews()
        if (samples.isEmpty()) {
            binding.layoutBatterySamples.addView(
                TextView(this).apply {
                    text = "还没有记录：打开上面的开关，或点「马上记一条」"
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                }
            )
            return
        }
        // 新的在最上面，最多画 100 行（外面是滚动窗格）
        samples.sortedByDescending { it.timestamp }.take(MAX_RENDERED_SAMPLES).forEach { sample ->
            binding.layoutBatterySamples.addView(
                TextView(this).apply {
                    val moment = timeFormat.format(Date(sample.timestamp))
                    val direction = if (sample.charging) "充电" else "放电"
                    val temp = sample.temperatureC?.let { " ｜ ${"%.1f".format(it)}℃" } ?: ""
                    text = "$moment　${sample.percent}%　$direction　" +
                        BatteryLogger.formatCurrent(sample.currentUa, sample.voltageMv) + temp
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    setLineSpacing(dp(3f).toFloat(), 1f)
                    background = ContextCompat.getDrawable(this@BatteryInfoActivity, R.drawable.bg_row_panel)
                    setPadding(dp(12f), dp(9f), dp(12f), dp(9f))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(6f) }
                }
            )
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

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

        /** 充放电记录间隔：30 秒一条，一小时 120 条，文件很小。 */
        const val LOG_INTERVAL_MS = 30_000L

        /** 列表一次最多画 100 行（外面是滚动窗格）。 */
        const val MAX_RENDERED_SAMPLES = 100
    }
}
