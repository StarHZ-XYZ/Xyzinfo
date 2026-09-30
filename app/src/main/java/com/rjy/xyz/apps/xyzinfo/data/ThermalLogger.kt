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

    /** 读取当前所有热区温度（摄氏度）。 */
    fun readZones(): List<Zone> {
        val root = File("/sys/class/thermal")
        val dirs = root.listFiles { f -> f.isDirectory && f.name.startsWith("thermal_zone") } ?: return emptyList()
        val zones = mutableListOf<Zone>()
        dirs.forEach { dir ->
            val name = runCatching { File(dir, "type").readText().trim() }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: dir.name
            val raw = runCatching { File(dir, "temp").readText().trim() }.getOrNull() ?: return@forEach
            val value = raw.toDoubleOrNull() ?: return@forEach
            // 多数平台是毫摄氏度；也有直接给摄氏度的
            val celsius = if (value > 1000) value / 1000.0 else value
            if (celsius in -40.0..150.0) zones += Zone(name, celsius)
        }
        return zones.sortedByDescending { it.celsius }
    }

    /** 追加一条采样记录。 */
    fun logSample(context: Context): List<Zone> {
        val zones = readZones()
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
