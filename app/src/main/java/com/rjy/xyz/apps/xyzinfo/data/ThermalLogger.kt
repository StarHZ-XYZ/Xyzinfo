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

    /**
     * 一条**单次记录**（1.0.5 新增，温度页下半部分按这个列表展示）。
     *
     * 长表里同一时刻有很多热区，这里把一次采样收成一行：
     * CPU 取所有 CPU 相关热区的**平均**，电池单独一列。
     */
    data class Record(
        val timestamp: Long,
        val cpuCelsius: Double?,
        val batteryCelsius: Double?,
        val cpuMaxCelsius: Double?,
        val cpuSensorCount: Int,
        val hottestName: String
    )

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
        // 先从 thermal 子系统读；有些机型只把温度放在 hwmon 里，那就再试一次
        val zones = readThermalZones().ifEmpty { readHwmonZones() }
        if (zones.isNotEmpty()) return zones
        val battery = readBatteryTemperature(context)
        return if (battery == null) emptyList() else listOf(battery)
    }

    /** 本机是否只能拿到电池温度（界面据此给一句解释）。 */
    fun isBatteryOnly(context: Context): Boolean =
        readThermalZones().isEmpty() && readHwmonZones().isEmpty() &&
            readBatteryTemperature(context) != null

    /**
     * 第二来源：`/sys/class/hwmon` 下每个 hwmon 目录里的 `temp1_input`。
     *
     * 部分平台（尤其一些平板 / 定制 ROM）不在 thermal 子系统暴露温度，
     * 只在 hwmon 里给一个 temp1_input（毫摄氏度）。多这一条来源，
     * "有些手机点进去什么都不显示"的情况就基本消失了。
     */
    private fun readHwmonZones(): List<Zone> {
        val out = mutableListOf<Zone>()
        val dirs = runCatching {
            File("/sys/class/hwmon").listFiles { f -> f.isDirectory && f.name.startsWith("hwmon") }
        }.getOrNull() ?: return emptyList()
        dirs.forEach { dir ->
            val name = runCatching { File(dir, "name").readText().trim() }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: dir.name
            val celsius = runCatching { File(dir, "temp1_input").readText().trim() }.getOrNull()
                ?.toDoubleOrNull()
                ?.let { if (it > 1000) it / 1000.0 else it }
                ?: return@forEach
            if (celsius in -40.0..150.0) out += Zone(name, celsius)
        }
        return out.sortedByDescending { it.celsius }
    }

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

    /**
     * 这个热区算不算「CPU 温度」。
     *
     * 各平台的命名花样很多：高通是 `tsens_tz_sensor*` / `cpu-0-0-us`，
     * 联发科是 `mtktscpu` / `cpu_therm`，三星是 `s5p-*`，还有 `apc` / `cluster` / `kryo`。
     * 反过来 GPU、充电、摄像头、功放这些都要排除 —— 它们跟 CPU 不是一回事。
     */
    fun isCpuZone(name: String): Boolean {
        val lower = name.lowercase()
        if (EXCLUDE_FROM_CPU.any { lower.contains(it) }) return false
        return CPU_KEYWORDS.any { lower.contains(it) }
    }

    /** 电池 / 电量计相关热区。 */
    fun isBatteryZone(name: String): Boolean {
        val lower = name.lowercase()
        return BATTERY_KEYWORDS.any { lower.contains(it) }
    }

    /** 把驱动里的热区名换成看得懂的中文（认不出来就原样返回）。 */
    fun label(raw: String): String {
        val lower = raw.lowercase()
        LABELS.forEach { (keyword, text) -> if (lower.contains(keyword)) return text }
        return raw
    }

    /** CPU 温度 = 所有 CPU 相关热区的平均值（没有就退回"最热的非电池热区"）。 */
    fun cpuTemperature(zones: List<Zone>): Double? {
        val cpu = zones.filter { isCpuZone(it.name) }
        if (cpu.isNotEmpty()) return cpu.map { it.celsius }.average()
        return zones.filterNot { isBatteryZone(it.name) }.maxByOrNull { it.celsius }?.celsius
    }

    /** 电池温度：优先热区里的电池节点，其次是 BatteryManager。 */
    fun batteryTemperature(context: Context?, zones: List<Zone>): Double? =
        zones.firstOrNull { isBatteryZone(it.name) }?.celsius
            ?: readBatteryTemperature(context)?.celsius

    /**
     * 把历史长表收成单条记录（同一时间戳聚成一条），新的在最前面。
     */
    fun records(context: Context, hours: Int = 24 * 30, limit: Int = 200): List<Record> =
        recordsFrom(history(context, hours), limit)

    /** 纯函数版本，方便单元测试直接用构造好的采样。 */
    fun recordsFrom(samples: List<Sample>, limit: Int = 200): List<Record> {
        if (samples.isEmpty()) return emptyList()
        return samples
            .groupBy { it.timestamp }
            .toSortedMap(reverseOrder())
            .entries
            .take(limit)
            .map { (timestamp, group) ->
                val zones = group.map { Zone(it.name, it.celsius) }
                val cpuZones = zones.filter { isCpuZone(it.name) }
                val used = cpuZones.ifEmpty { zones.filterNot { isBatteryZone(it.name) } }
                Record(
                    timestamp = timestamp,
                    cpuCelsius = cpuTemperature(zones),
                    batteryCelsius = zones.firstOrNull { isBatteryZone(it.name) }?.celsius,
                    cpuMaxCelsius = used.maxByOrNull { it.celsius }?.celsius,
                    cpuSensorCount = used.size,
                    hottestName = used.maxByOrNull { it.celsius }?.name?.let { label(it) }.orEmpty()
                )
            }
    }

    /** 一条记录里 CPU / 电池的显示文案。 */
    fun formatCelsius(value: Double?): String =
        if (value == null) "—" else String.format(Locale.US, "%.1f ℃", value)

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

    private const val CPU_KEYWORDS_RAW =
        "cpu,tsens,apc,cluster,kryo,core,mtkts,gold,silver,big,little,s5p,exynos,thermal_zone_cpu"
    private val CPU_KEYWORDS = CPU_KEYWORDS_RAW.split(',')
    private val EXCLUDE_FROM_CPU = listOf(
        "gpu", "battery", "batt", "charger", "chg", "usb", "pa_", "camera", "cam_",
        "display", "wifi", "modem", "npu", "ambient", "quiet", "backlight", "flash"
    )
    private val BATTERY_KEYWORDS = listOf("battery", "batt", "bms", "fuel")
    private val LABELS = listOf(
        "battery" to "电池",
        "batt" to "电池",
        "tsens_tz_sensor" to "半导体温区",
        "tsens" to "半导体温区",
        "cpu" to "CPU",
        "apc" to "CPU 集群",
        "cluster" to "CPU 集群",
        "kryo" to "CPU 核心",
        "gpu" to "GPU",
        "quiet" to "机身",
        "xo" to "晶振",
        "pa_" to "功放",
        "charger" to "充电",
        "chg" to "充电",
        "usb" to "USB",
        "camera" to "摄像头",
        "cam_" to "摄像头",
        "display" to "屏幕",
        "wifi" to "WiFi",
        "modem" to "基带",
        "npu" to "NPU",
        "ambient" to "环境"
    )
}
