package com.rjy.xyz.apps.xyzinfo.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import com.rjy.xyz.apps.xyzinfo.model.RamInfo
import com.rjy.xyz.apps.xyzinfo.util.DeviceFacts
import com.rjy.xyz.apps.xyzinfo.util.Labels
import com.rjy.xyz.apps.xyzinfo.util.ProcFs
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 读取运行内存、堆信息与交换分区数据。
 */
object RamInfoProvider {

    private const val MEM_INFO_PATH = "/proc/meminfo"

    private const val DEVFREQ_DIR = "/sys/class/devfreq"

    /** 常见容量档位，用于把实测可用内存吸附回标称容量。 */
    private val NOMINAL_CAPACITY_GIGABYTES = listOf(2, 3, 4, 6, 8, 12, 16, 18, 24, 32)

    /** devfreq 里这些名字代表内存/DDR 总线，优先读取。 */
    private val DDR_NODE_KEYWORDS = listOf("ddr", "dram", "bimc", "mccc", "emi", "dmc", "dvfsrc")

    /** 这些是内存互联（interconnect）节点，频率含义弱一些，作为次选。 */
    private val INTERCONNECT_NODE_KEYWORDS = listOf("llcc", "memlat")

    /** 老平台的固定频率节点，devfreq 扫不到时兜底。 */
    private val FALLBACK_FREQUENCY_PATHS = listOf(
        "/sys/class/devfreq/soc:qcom,cpu-llcc-ddr-bw/cur_freq",
        "/sys/class/devfreq/soc:qcom,cpu-cpu-llcc-bw/cur_freq",
        "/sys/class/devfreq/soc:qcom,memlat-cpu0/cur_freq",
        "/sys/class/devfreq/soc:qcom,memlat-cpu4/cur_freq",
        "/sys/class/devfreq/soc:qcom,memlat-cpu6/cur_freq",
        "/sys/class/devfreq/17000000.qcom,devfreq-l3/cur_freq",
        "/sys/kernel/debug/clk/bimc_clk/clk_rate",
        "/sys/kernel/debug/clk/mccc_clk/clk_rate",
        "/sys/kernel/debug/clk/gcc_ddrss_gpu_axi_clk/clk_rate"
    )

    fun load(context: Context): RamInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val memInfoRaw = ProcFs.readText(MEM_INFO_PATH)
        val totalBytes = readMemTotalBytes(memInfoRaw) ?: memoryInfo.totalMem
        val availableBytes = memoryInfo.availMem
        val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)
        val usagePercent = if (totalBytes > 0) {
            ((usedBytes * 100.0) / totalBytes).roundToInt()
        } else {
            0
        }

        val swapTotalKb = extractKiloBytes(memInfoRaw, "SwapTotal")
        val swapFreeKb = extractKiloBytes(memInfoRaw, "SwapFree")
        val swapUsedKb = if (swapTotalKb != null && swapFreeKb != null) {
            (swapTotalKb - swapFreeKb).coerceAtLeast(0L)
        } else {
            null
        }
        val frequency = readMemoryFrequency()

        return RamInfo(
            measuredTotalBytes = totalBytes,
            nominalTotalGigabytes = nominalCapacityGigabytes(totalBytes),
            availableBytes = availableBytes,
            usedBytes = usedBytes,
            usagePercent = usagePercent,
            lowMemory = memoryInfo.lowMemory,
            thresholdBytes = memoryInfo.threshold,
            javaHeapMaxBytes = Runtime.getRuntime().maxMemory(),
            nativeHeapTotalBytes = Debug.getNativeHeapSize(),
            nativeHeapAllocatedBytes = Debug.getNativeHeapAllocatedSize(),
            swapTotalBytes = swapTotalKb?.times(1024),
            swapUsedBytes = swapUsedKb?.times(1024),
            typeName = detectType(memInfoRaw),
            brandName = detectBrand(),
            currentFrequencyMHz = frequency?.currentMHz,
            minFrequencyMHz = frequency?.minMHz,
            maxFrequencyMHz = frequency?.maxMHz,
            frequencySource = frequency?.source,
            memInfoPreview = ProcFs.preview(memInfoRaw, "原始 /proc/meminfo")
        )
    }

    /**
     * 读取总量。
     *
     * 优先用 /proc/meminfo 的 MemTotal：它比 ActivityManager 更接近内核视角，
     * 单位是 kB，需要乘 1024。
     */
    private fun readMemTotalBytes(memInfoRaw: String): Long? =
        extractKiloBytes(memInfoRaw, "MemTotal")?.takeIf { it > 0 }?.times(1024)

    /**
     * 把实测可用内存吸附到最近的常见容量档位。
     *
     * 系统会给内核、显示缓冲等预留一部分内存（通常 3%~10%），
     * 所以 12GB 机器实测常常只有 11.2GB 左右；只有在合理区间内才吸附，
     * 差得太多就返回 null，避免把 10GB 之类读成 12GB。
     */
    private fun nominalCapacityGigabytes(measuredBytes: Long): Int? {
        val measuredGigabytes = measuredBytes / 1024.0 / 1024 / 1024
        if (measuredGigabytes <= 0) return null

        val nearest = NOMINAL_CAPACITY_GIGABYTES.minByOrNull { abs(it - measuredGigabytes) }
            ?: return null
        val withinRange = measuredGigabytes >= nearest * 0.85 && measuredGigabytes <= nearest * 1.02
        return nearest.takeIf { withinRange }
    }

    /** 由 /proc/meminfo 与设备型号字符串猜测内存类型。 */
    private fun detectType(memInfoRaw: String): String {
        val text = (
            memInfoRaw + " " +
                DeviceFacts.orUnknown(Build.MODEL) + " " +
                DeviceFacts.orUnknown(Build.PRODUCT) + " " +
                DeviceFacts.orUnknown(Build.DEVICE)
            ).lowercase(Locale.ROOT)

        return when {
            "lpddr5x" in text -> "LPDDR5X"
            "lpddr5" in text -> "LPDDR5"
            "lpddr4x" in text -> "LPDDR4X"
            "lpddr4" in text -> "LPDDR4"
            else -> Labels.NOT_PUBLIC
        }
    }

    /** 系统不暴露内存品牌，保留接口以便后续接入更多平台节点。 */
    private fun detectBrand(): String? = null

    /**
     * 读取内存频率。
     *
     * 先扫描 /sys/class/devfreq 下与 DDR 相关的调频节点（覆盖高通、联发科、三星、麒麟等平台），
     * 扫不到再退回老平台的固定节点列表。
     */
    private fun readMemoryFrequency(): MemoryFrequency? {
        val candidates = ProcFs.listFiles(DEVFREQ_DIR)
            .filter { it.isDirectory }
            .map { dir ->
                val name = dir.name.lowercase(Locale.ROOT)
                val priority = when {
                    DDR_NODE_KEYWORDS.any { it in name } -> 0
                    INTERCONNECT_NODE_KEYWORDS.any { it in name } -> 1
                    else -> 2
                }
                dir to priority
            }
            .filter { it.second < 2 }
            .sortedBy { it.second }

        for ((dir, _) in candidates) {
            val base = dir.absolutePath
            val currentMHz = toMegaHertz(ProcFs.readLong("$base/cur_freq")) ?: continue
            return MemoryFrequency(
                currentMHz = currentMHz,
                minMHz = toMegaHertz(ProcFs.readLong("$base/min_freq")),
                maxMHz = toMegaHertz(ProcFs.readLong("$base/max_freq")),
                source = dir.name
            )
        }

        for (path in FALLBACK_FREQUENCY_PATHS) {
            val currentMHz = toMegaHertz(ProcFs.readLong(path)) ?: continue
            return MemoryFrequency(
                currentMHz = currentMHz,
                minMHz = null,
                maxMHz = null,
                source = path.substringAfterLast('/')
            )
        }
        return null
    }

    /**
     * 把节点原始值统一成 MHz。
     *
     * 不同平台分别以 Hz 或 kHz 上报（例如 4266000 表示 4266 MHz）。
     */
    private fun toMegaHertz(raw: Long?): Int? {
        if (raw == null || raw <= 0) return null
        val megaHertz = when {
            raw >= 100_000_000L -> raw / 1_000_000L
            raw >= 10_000L -> raw / 1_000L
            else -> raw
        }
        return megaHertz.toInt().takeIf { it in 100..20_000 }
    }

    private data class MemoryFrequency(
        val currentMHz: Int,
        val minMHz: Int?,
        val maxMHz: Int?,
        val source: String
    )

    private fun extractKiloBytes(memInfoRaw: String, key: String): Long? {
        val regex = Regex("""^$key:\s+(\d+)\s+kB$""", RegexOption.MULTILINE)
        return regex.find(memInfoRaw)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }
}
