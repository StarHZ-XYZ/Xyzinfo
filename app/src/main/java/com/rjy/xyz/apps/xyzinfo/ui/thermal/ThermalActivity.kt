package com.rjy.xyz.apps.xyzinfo.ui.thermal

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.data.ThermalLogger
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityThermalBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.Anim
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R

/**
 * 温度监控页（数据层 + 实时显示 + 长期记录）。
 *
 * - 实时：每 2 秒刷新一次各热区温度；
 * - 记录：点「开始记录」后每分钟落一条到私有 CSV（页面开着才记录，浮窗版本下一轮做）；
 * - 历史：显示记录条数、文件大小、24 小时内最高温；
 * - 导出：把最近记录做成文本分享出去（不需要存储权限）。
 */
class ThermalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThermalBinding
    private val handler = Handler(Looper.getMainLooper())
    private var logging = false

    private val refresh = object : Runnable {
        override fun run() {
            refreshNow()
            if (logging) {
                runCatching { ThermalLogger.logSample(this@ThermalActivity) }
            }
            handler.postDelayed(this, REFRESH_MILLIS)
        }
    }

    private val logTick = object : Runnable {
        override fun run() {
            if (!logging) return
            runCatching { ThermalLogger.logSample(this@ThermalActivity) }
            showHistory()
            handler.postDelayed(this, LOG_INTERVAL_MILLIS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThermalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_NONE)

        binding.btnToggleLogging.setOnClickListener {
            Anim.pressFeedback(it)
            logging = !logging
            if (logging) {
                binding.btnToggleLogging.text = "停止记录"
                ThermalLogger.logSample(this)
                handler.postDelayed(logTick, LOG_INTERVAL_MILLIS)
            } else {
                binding.btnToggleLogging.text = "开始记录（每分钟一条）"
                handler.removeCallbacks(logTick)
            }
            showHistory()
        }
        binding.btnExportThermal.setOnClickListener {
            Anim.pressFeedback(it)
            val text = ThermalLogger.exportText(this)
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "XyzInfo 温度记录")
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    "分享温度记录"
                )
            )
        }
        handler.post(refresh)
        showHistory()
        buildRangeChips()
        renderChart()
    }

    private var rangeHours = 24
    /**
     * 页面打开期间的实时采样（每 2 秒一条，内存保留最近 30 分钟）。
     * 这样一进页面十几秒就能看到曲线在动，不用等"每分钟落盘"攒够数据。
     */
    private val liveSamples = mutableListOf<ThermalLogger.Sample>()

    /** 时间窗切换：1 小时 / 24 小时 / 7 天 / 30 天。 */
    private fun buildRangeChips() {
        val options = listOf(1 to "1 小时", 24 to "24 小时", 24 * 7 to "7 天", 24 * 30 to "30 天")
        val density = resources.displayMetrics.density
        binding.layoutThermalRange.removeAllViews()
        options.forEach { (hours, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 12.5f
                gravity = android.view.Gravity.CENTER
                setPadding((12 * density).toInt(), (7 * density).toInt(), (12 * density).toInt(), (7 * density).toInt())
                setTextColor(
                    ContextCompat.getColor(
                        this@ThermalActivity,
                        if (hours == rangeHours) R.color.accent else R.color.text_secondary
                    )
                )
                background = ContextCompat.getDrawable(this@ThermalActivity, R.drawable.bg_chip_filter)
                isSelected = hours == rangeHours
                isClickable = true
                setOnClickListener {
                    rangeHours = hours
                    buildRangeChips()
                    renderChart()
                }
            }
            chip.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * density).toInt()
            }
            binding.layoutThermalRange.addView(chip)
        }
    }

    private fun renderChart() {
        Thread({
            val data = runCatching { ThermalLogger.history(this, rangeHours) }.getOrDefault(emptyList())
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                // 历史（每分钟）+ 本次会话实时点（每 2 秒）合并，曲线立刻有内容
                val cutoff = System.currentTimeMillis() - rangeHours * 3600_000L
                val merged = (data + liveSamples.filter { it.timestamp >= cutoff })
                    .sortedBy { it.timestamp }
                binding.thermalChart.setData(merged, rangeHours)
            }
        }, "xyzinfo-thermal-chart").start()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
        if (logging) {
            // 页面退到后台就停止测量，避免后台耗电；回到页面会自动恢复
            handler.removeCallbacks(logTick)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
        if (logging) handler.postDelayed(logTick, LOG_INTERVAL_MILLIS)
    }

    private fun refreshNow() {
        Thread({
            val zones = runCatching { ThermalLogger.readZones() }.getOrDefault(emptyList())
            val text = if (zones.isEmpty()) {
                "读不到热区（部分机型限制读取 /sys/class/thermal）"
            } else {
                zones.joinToString("\n") { "${it.name}：${"%.1f".format(it.celsius)} ℃" }
            }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                binding.tvThermalNow.text = text
                // 实时点也喂给曲线，并把内存缓冲限制在 30 分钟内
                val now = System.currentTimeMillis()
                zones.forEach { liveSamples += ThermalLogger.Sample(now, it.name, it.celsius) }
                while (liveSamples.size > MAX_LIVE_SAMPLES) liveSamples.removeAt(0)
                val cutoff = now - rangeHours * 3600_000L
                binding.thermalChart.setData(
                    liveSamples.filter { it.timestamp >= cutoff },
                    rangeHours,
                    liveOnly = true
                )
            }
        }, "xyzinfo-thermal").start()
    }

    private fun showHistory() {
        Thread({
            val (lines, bytes) = ThermalLogger.stats(this)
            val day = ThermalLogger.history(this, 24)
            val max = day.maxByOrNull { it.celsius }
            val summary = buildString {
                append("已记录：$lines 条（${"%.1f".format(bytes / 1024.0)} KB）")
                if (max != null) {
                    append("\n最近 24 小时最高温：${"%.1f".format(max.celsius)} ℃（${max.name}）")
                    append("\n记录时间范围：${ThermalLogger.formatTime(day.first().timestamp)} 起")
                } else {
                    append("\n最近 24 小时还没有记录")
                }
                if (logging) append("\n\n状态：正在记录（每分钟一条）")
            }
            runOnUiThread {
                if (!isFinishing) binding.tvThermalHistory.setInfoRow(summary)
            }
        }, "xyzinfo-thermal-history").start()
    }

    private companion object {
        const val REFRESH_MILLIS = 2_000L
        const val LOG_INTERVAL_MILLIS = 60_000L
        /** 30 分钟 × 每 2 秒一条 ≈ 900 条，够画曲线又不占内存。 */
        const val MAX_LIVE_SAMPLES = 900
    }
}
