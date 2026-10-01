package com.rjy.xyz.apps.xyzinfo.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 温度记录：读取各热区温度，并把每分钟一条记录长期保存到私有目录 CSV。
 *
 * 数据格式（长表，热区数量变化也不会写乱）：
 * ```
 * 时间戳毫秒,热区名,温度℃
 * ```
 * 之所以不用宽表：不同机型、不同时刻热区数量会变，长表读起来更稳。
 */
object ThermalLogger {

    data class Zone(val name: String, val celsius: Double)

    data class Sample(val timestamp: Long, val name: String, val celsius: Double)

    private const val FILE_NAME = "thermal.csv"
    private const val HEADER = "timestamp,zone,celsius"

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /**
     * 读取当前所有热区温度（摄氏度）。
     *
     * **为什么要在多个路径 / 多个文件名里找**：不同厂商暴露温度的位置完全不一样 ——
     * 有的在 `/sys/class/thermal/thermal_zone<编号>/temp`，有的只在
     * `/sys/devices/virtual/thermal/`，文件名还可能是 `temperature`。
     * 之前只试了第一条路径，于是"有些手机点进去什么都不显示"。
     *
     * 最后**一定有兜底**：热区全读不到时用电池温度（`BatteryManager` 提供，
     * 任何 Android 设备都能拿到），并标明来源，绝不会给用户一个空白页。
     */
    fun readZones(context: Context? = null): List<Zone> {
        val zones = readThermalZones()
        if (zones.isNotEmpty()) return zones
        val battery = readBatteryTemperature(context)
        return if (battery == null) emptyList() else listOf(battery)
    }

    /** 本机是否只能拿到电池温度（界面据此给一句解释）。 */
    fun isBatteryOnly(context: Context): Boolean =
        readThermalZones().isEmpty() && readBatteryTemperature(context) != null

    private fun readThermalZones(): List<Zone> {
        val roots = listOf(
            File("/sys/class/thermal"),
            File("/sys/devices/virtual/thermal")
        )
        val zones = mutableListOf<Zone>()
        val seen = mutableSetOf<String>()
        roots.forEach { root ->
            val dirs = runCatching {
                root.listFiles { f -> f.isDirectory && f.name.startsWith("thermal_zone") }
            }.getOrNull() ?: return@forEach
            dirs.forEach { dir ->
                val name = runCatching { File(dir, "type").readText().trim() }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: dir.name
                // 有的平台叫 temp，有的叫 temperature；单位多是毫摄氏度
                val celsius = listOf("temp", "temperature")
                    .firstNotNullOfOrNull { fileName ->
                        runCatching { File(dir, fileName).readText().trim() }.getOrNull()
                            ?.toDoubleOrNull()
                    }
                    ?.let { value -> if (value > 1000) value / 1000.0 else value }
                    ?: return@forEach
                if (celsius in -40.0..150.0 && seen.add(name)) zones += Zone(name, celsius)
            }
        }
        return zones.sortedByDescending { it.celsius }
    }

    /** 电池温度（摄氏）：`EXTRA_TEMPERATURE` 的单位是 0.1℃。 */
    private fun readBatteryTemperature(context: Context?): Zone? {
        val ctx = context?.applicationContext ?: return null
        val intent = runCatching {
            ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return null
        val raw = intent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (raw == Int.MIN_VALUE) return null
        val celsius = raw / 10.0
        if (celsius !in -40.0..150.0) return null
        return Zone("电池", celsius)
    }

    /** 追加一条采样记录。 */
    fun logSample(context: Context): List<Zone> {
        val zones = readZones(context)
        if (zones.isEmpty()) return zones
        runCatching {
            val target = file(context)
            val isNew = !target.exists()
            target.appendText(
                buildString {
                    if (isNew) appendLine(HEADER)
                    val now = System.currentTimeMillis()
                    zones.forEach { appendLine("$now,${it.name},${"%.1f".format(it.celsius)}") }
                }
            )
        }
        return zones
    }

    /** 读取最近 [hours] 小时内的采样。 */
    fun history(context: Context, hours: Int): List<Sample> {
        val target = file(context)
        if (!target.exists()) return emptyList()
        val since = System.currentTimeMillis() - hours * 3600_000L
        return runCatching {
            target.readLines().drop(1).mapNotNull { line ->
                val parts = line.split(',')
                if (parts.size < 3) return@mapNotNull null
                val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
                if (timestamp < since) return@mapNotNull null
                val celsius = parts[2].toDoubleOrNull() ?: return@mapNotNull null
                Sample(timestamp, parts[1], celsius)
            }
        }.getOrDefault(emptyList())
    }

    /** 记录文件大小与行数，用于界面显示"已记录多少"。 */
    fun stats(context: Context): Pair<Int, Long> {
        val target = file(context)
        if (!target.exists()) return 0 to 0L
        val lines = runCatching { target.readLines().size - 1 }.getOrDefault(0)
        return lines to target.length()
    }

    /** 导出成可以直接分享的文本。 */
    fun exportText(context: Context): String {
        val target = file(context)
        if (!target.exists()) return "还没有记录：先到温度监控页点「开始记录」。"
        val samples = history(context, 24 * 30).takeLast(2000)
        return buildString {
            appendLine("XyzInfo 温度记录导出（最近 ${samples.size} 条）")
            appendLine("时间,热区,温度℃")
            samples.forEach {
                appendLine("${timeFormat.format(Date(it.timestamp))},${it.name},${"%.1f".format(it.celsius)}")
            }
        }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    fun formatTime(timestamp: Long): String = timeFormat.format(Date(timestamp))
}
