package com.rjy.xyz.apps.xyzinfo.ui.thermal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import com.rjy.xyz.apps.xyzinfo.data.ThermalLogger
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityThermalBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.Anim
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.ThemeColors
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 温度监控页（1.0.5 重写）。
 *
 * 用户提的三件事都在这里：
 *
 * 1. **兼容大部分手机**：热区读取多一条来源（`/sys/class/hwmon`），一条都读不到时
 *    还有电池温度兜底，并且把"数据从哪来"写在页面上；
 * 2. **只看 CPU 与电池**：CPU 温度取所有 CPU 相关热区的**平均值**
 *    （高通 tsens / 联发科 mtktscpu / 三星 s5p / apc / cluster / kryo 都认），
 *    电池温度优先热区里的电池节点、其次 BatteryManager；
 * 3. **曲线改成单条记录**：原来的多序列大曲线拿掉，改成下面可滚动的记录列表 ——
 *    一条记录 = 一次采样（CPU 平均 + 电池 + 温度条），可以按 1 / 5 / 30 分钟自动记录。
 */
class ThermalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThermalBinding
    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    private var logging = false
    private var intervalMinutes = 1
    private var recordLimit = 100
    private var pendingOverlayStart = false

    private val refresh = object : Runnable {
        override fun run() {
            refreshNow()
            handler.postDelayed(this, REFRESH_MILLIS)
        }
    }

    private val logTick = object : Runnable {
        override fun run() {
            if (!logging) return
            logOnce(notify = false)
            handler.postDelayed(this, intervalMinutes * 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThermalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_NONE)

        logging = SettingsRepository.thermalLogEnabled(this)
        intervalMinutes = SettingsRepository.thermalIntervalMinutes(this)
        recordLimit = SettingsRepository.thermalRecordLimit(this)

        binding.switchThermalLog.isChecked = logging
        binding.switchThermalLog.setOnCheckedChangeListener { _, checked ->
            logging = checked
            SettingsRepository.setThermalLogEnabled(this, checked)
            handler.removeCallbacks(logTick)
            if (checked) {
                logOnce(notify = false)
                handler.postDelayed(logTick, intervalMinutes * 60_000L)
                toast("已开启自动记录（每 $intervalMinutes 分钟一条）")
            }
        }

        binding.btnLogNow.setOnClickListener {
            Anim.pressFeedback(it)
            logOnce(notify = true)
        }
        binding.btnExportThermal.setOnClickListener {
            Anim.pressFeedback(it)
            shareRecords()
        }
        binding.btnClearThermal.setOnClickListener {
            Anim.pressFeedback(it)
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("清空温度记录？")
                .setMessage("本机保存的温度记录会被删除，无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空") { _, _ ->
                    ThermalLogger.clear(this)
                    refreshRecords()
                    toast("温度记录已清空")
                }
                .show()
        }
        binding.btnThermalOverlay.setOnClickListener {
            Anim.pressFeedback(it)
            if (ThermalOverlayService.isRunning(this)) {
                ThermalOverlayService.stop(this)
                ThermalOverlayService.setRunning(this, false)
                refreshOverlayButton()
                toast("温度浮窗已关闭")
            } else {
                ensureOverlayPermissionThenStart()
            }
        }

        buildIntervalChips()
        buildLimitChips()
        refreshOverlayButton()
        handler.post(refresh)
        refreshRecords()
        if (logging) handler.postDelayed(logTick, intervalMinutes * 60_000L)
    }

    // ---------- 实时温度 ----------

    private fun refreshNow() {
        Thread({
            val zones = runCatching { ThermalLogger.readZones(this) }.getOrDefault(emptyList())
            val cpu = ThermalLogger.cpuTemperature(zones)
            val battery = ThermalLogger.batteryTemperature(this, zones)
            val cpuZones = zones.filter { ThermalLogger.isCpuZone(it.name) }
            val hottest = zones.maxByOrNull { it.celsius }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                binding.tvCpuTemp.text = ThermalLogger.formatCelsius(cpu)
                binding.tvCpuDetail.text = when {
                    cpu == null -> "读不到 CPU 热区"
                    cpuZones.size >= 2 -> "${cpuZones.size} 个传感器平均 ｜ " +
                        "${ThermalLogger.formatCelsius(cpuZones.minOf { it.celsius })}" +
                        " ~ ${ThermalLogger.formatCelsius(cpuZones.maxOf { it.celsius })}"

                    else -> ThermalLogger.label(cpuZones.firstOrNull()?.name ?: "CPU")
                }
                binding.tvBatteryTemp.text = ThermalLogger.formatCelsius(battery)
                binding.tvBatteryDetail.text = when {
                    battery == null -> "读不到电池温度"
                    zones.any { ThermalLogger.isBatteryZone(it.name) } -> "来自电池热区"
                    else -> "来自系统 BatteryManager"
                }
                binding.tvThermalZoneNote.text = thermalNote(zones, hottest)
            }
        }, "xyzinfo-thermal").start()
    }

    /** 热区明细说明：读到什么、从哪来、为什么只有电池。 */
    private fun thermalNote(zones: List<ThermalLogger.Zone>, hottest: ThermalLogger.Zone?): String = when {
        zones.isEmpty() ->
            "本机既读不到热区节点、也拿不到电池温度 —— 系统层面完全屏蔽了温度信息。"

        zones.size == 1 && ThermalLogger.isBatteryZone(zones.first().name) ->
            "本机不向应用开放热区节点（Android 10+ 常见限制），只有电池温度。" +
                "它比芯片温度低一些，但发热趋势一致。"

        else -> buildString {
            append("共读到 ${zones.size} 个热区。")
            if (hottest != null) {
                append("最热的是「${ThermalLogger.label(hottest.name)}」")
                append(ThermalLogger.formatCelsius(hottest.celsius)).append("。")
            }
            append("\n")
            append(
                zones.take(12).joinToString("　") { zone ->
                    "${ThermalLogger.label(zone.name)} ${"%.1f".format(zone.celsius)}℃"
                }
            )
            if (zones.size > 12) append(" …")
        }
    }

    // ---------- 记录 ----------

    private fun logOnce(notify: Boolean) {
        Thread({
            val zones = runCatching { ThermalLogger.logSample(this) }.getOrDefault(emptyList())
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (notify) {
                    toast(
                        if (zones.isEmpty()) "读不到温度，没能记录"
                        else "已记录 ${ThermalLogger.formatCelsius(ThermalLogger.cpuTemperature(zones))}"
                    )
                }
                refreshRecords()
            }
        }, "xyzinfo-thermal-log").start()
    }

    private fun refreshRecords() {
        val limit = recordLimit
        Thread({
            val records = runCatching { ThermalLogger.records(this, limit = limit) }
                .getOrDefault(emptyList())
            val (lines, bytes) = ThermalLogger.stats(this)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                renderRecords(records)
                binding.tvThermalLogStatus.text = buildString {
                    append("已记录 $lines 条")
                    if (bytes > 0) append("（${"%.1f".format(bytes / 1024.0)} KB）")
                    append(" ｜ 下面显示最近 ${records.size} 条")
                    if (logging) append(" ｜ 每 $intervalMinutes 分钟自动记一条")
                }
            }
        }, "xyzinfo-thermal-records").start()
    }

    private fun renderRecords(records: List<ThermalLogger.Record>) {
        binding.layoutThermalRecords.removeAllViews()
        binding.tvThermalRecordsEmpty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        // 记录太多时只画前 200 行，剩下的靠"记录条数"调；一次画 2000 行会明显卡
        records.take(MAX_RENDERED_ROWS).forEach { record -> binding.layoutThermalRecords.addView(buildRow(record)) }
        if (records.size > MAX_RENDERED_ROWS) {
            binding.layoutThermalRecords.addView(
                TextView(this).apply {
                    text = "还有 ${records.size - MAX_RENDERED_ROWS} 条没画出来（把上面的「记录条数」调小一点看最近的）"
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                    setPadding(0, dp(8f), 0, 0)
                }
            )
        }
    }

    /** 一行 = 一条记录：时间 + CPU + 电池 + 一根温度条。 */
    private fun buildRow(record: ThermalLogger.Record): View {
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@ThermalActivity, R.drawable.bg_row_panel)
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6f) }
        }

        val header = TextView(this).apply {
            val cpuText = ThermalLogger.formatCelsius(record.cpuCelsius)
            val batteryText = ThermalLogger.formatCelsius(record.batteryCelsius)
            text = "${timeFormat.format(Date(record.timestamp))}　CPU $cpuText　电池 $batteryText"
            textSize = 12.5f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        }
        row.addView(header)

        // 温度条：20℃ 起，80℃ 满格（直观看出"这次比上次热")
        val value = record.cpuCelsius ?: record.batteryCelsius ?: return row
        val fraction = ((value - 20.0) / 60.0).coerceIn(0.04, 1.0).toFloat()
        val track = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(5f)
            ).apply { topMargin = dp(8f) }
            background = GradientDrawable().apply {
                cornerRadius = dp(3f).toFloat()
                setColor(ContextCompat.getColor(context, R.color.divider))
            }
        }
        track.addView(
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, fraction)
                background = GradientDrawable().apply {
                    cornerRadius = dp(3f).toFloat()
                    setColor(temperatureColor(value))
                }
            }
        )
        track.addView(
            View(this),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - fraction)
        )
        row.addView(track)

        if (record.cpuSensorCount > 1) {
            row.addView(
                TextView(this).apply {
                    text = "${record.cpuSensorCount} 个 CPU 传感器平均" +
                        if (record.cpuMaxCelsius != null) {
                            "，最高 ${ThermalLogger.formatCelsius(record.cpuMaxCelsius)}" +
                                if (record.hottestName.isNotBlank()) "（${record.hottestName}）" else ""
                        } else {
                            ""
                        }
                    textSize = 10.5f
                    setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                    setPadding(0, dp(6f), 0, 0)
                }
            )
        }
        return row
    }

    /** 40℃ 以下绿、60℃ 以下黄、80℃ 以上红，中间线性过渡。 */
    private fun temperatureColor(celsius: Double): Int {
        val cool = ThemeColors.accent(this)
        val warm = ContextCompat.getColor(this, R.color.status_warning)
        val hot = 0xFFD62828.toInt()
        return when {
            celsius <= 40 -> cool
            celsius <= 60 -> blend(cool, warm, (celsius - 40) / 20.0)
            celsius <= 80 -> blend(warm, hot, (celsius - 60) / 20.0)
            else -> hot
        }
    }

    private fun blend(from: Int, to: Int, fraction: Double): Int {
        val f = fraction.coerceIn(0.0, 1.0)
        fun mix(a: Int, b: Int) = (a + (b - a) * f).roundToInt().coerceIn(0, 255)
        return android.graphics.Color.rgb(
            mix(android.graphics.Color.red(from), android.graphics.Color.red(to)),
            mix(android.graphics.Color.green(from), android.graphics.Color.green(to)),
            mix(android.graphics.Color.blue(from), android.graphics.Color.blue(to))
        )
    }

    private fun shareRecords() {
        val text = ThermalLogger.exportText(this)
        runCatching {
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
        }.onFailure { toast("没有可用的分享目标") }
    }

    // ---------- 选项 chip ----------

    private fun buildIntervalChips() {
        val options = listOf(1 to "1 分钟", 5 to "5 分钟", 30 to "30 分钟")
        binding.layoutThermalInterval.removeAllViews()
        options.forEach { (minutes, label) ->
            binding.layoutThermalInterval.addView(
                createChip(label, minutes == intervalMinutes) {
                    intervalMinutes = minutes
                    SettingsRepository.setThermalIntervalMinutes(this, minutes)
                    buildIntervalChips()
                    if (logging) {
                        handler.removeCallbacks(logTick)
                        handler.postDelayed(logTick, minutes * 60_000L)
                    }
                    refreshRecords()
                }
            )
        }
    }

    private fun buildLimitChips() {
        val options = listOf(50, 100, 500, 2000)
        binding.layoutThermalLimit.removeAllViews()
        options.forEach { limit ->
            binding.layoutThermalLimit.addView(
                createChip("$limit 条", limit == recordLimit) {
                    recordLimit = limit
                    SettingsRepository.setThermalRecordLimit(this, limit)
                    buildLimitChips()
                    refreshRecords()
                }
            )
        }
    }

    private fun createChip(label: String, selected: Boolean, onClick: () -> Unit): TextView {
        val density = resources.displayMetrics.density
        val chip = TextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(
                (12 * density).toInt(), (7 * density).toInt(),
                (12 * density).toInt(), (7 * density).toInt()
            )
            setTextColor(
                if (selected) ThemeColors.accent(this@ThermalActivity)
                else ContextCompat.getColor(this@ThermalActivity, R.color.text_secondary)
            )
            background = ContextCompat.getDrawable(this@ThermalActivity, R.drawable.bg_chip_filter)
            isSelected = selected
            isClickable = true
            setOnClickListener { onClick() }
        }
        chip.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = (8 * density).toInt() }
        return chip
    }

    // ---------- 浮窗 / 生命周期 ----------

    private fun ensureOverlayPermissionThenStart() {
        if (!Settings.canDrawOverlays(this)) {
            pendingOverlayStart = true
            toast("请先允许「显示在其他应用上层」，返回后会自动继续")
            runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATION)
            }
            startOverlayNow()
            return
        }
        startOverlayNow()
    }

    private fun startOverlayNow() {
        ThermalOverlayService.start(this)
        ThermalOverlayService.setRunning(this, true)
        binding.btnThermalOverlay.postDelayed({ refreshOverlayButton() }, 400L)
        refreshOverlayButton()
        toast("温度浮窗已开启")
    }

    private fun refreshOverlayButton() {
        val running = ThermalOverlayService.isRunning(this)
        binding.btnThermalOverlay.text = if (running) "关闭温度浮窗" else "开启温度浮窗"
        binding.tvOverlayHint.text = when {
            running -> "浮窗运行中：悬浮显示 CPU 与电池温度，退出本页也会继续。"
            !Settings.canDrawOverlays(this) -> "需要「显示在其他应用上层」权限：点下面的按钮会跳到系统授权页。"
            else -> "把 CPU / 电池温度悬浮在其它应用上面，退出本页也继续显示。"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATION) refreshOverlayButton()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
        // 自动记录只在页面开着时跑：后台读 /sys 很费电
        if (logging) handler.postDelayed(logTick, intervalMinutes * 60_000L)
        if (!::binding.isInitialized) return
        refreshOverlayButton()
        refreshRecords()
        if (pendingOverlayStart) {
            pendingOverlayStart = false
            if (Settings.canDrawOverlays(this)) startOverlayNow()
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
        handler.removeCallbacks(logTick)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val REFRESH_MILLIS = 2000L
        const val REQ_NOTIFICATION = 2101

        /** 列表一次最多画多少行：2000 条全画出来会让页面卡，超出部分用文字提示。 */
        const val MAX_RENDERED_ROWS = 200
    }
}
