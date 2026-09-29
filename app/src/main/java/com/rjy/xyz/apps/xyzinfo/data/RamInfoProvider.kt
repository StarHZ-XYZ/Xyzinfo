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
import kotlin.math.roundToInt

/**
 * 读取运行内存、堆信息与交换分区数据。
 */
object RamInfoProvider {

    private const val MEM_INFO_PATH = "/proc/meminfo"

    /** 各家平台的 DDR 调频节点，命中即认为是内存频率。 */
    private val RAM_FREQUENCY_PATHS = listOf(
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

        val totalBytes = memoryInfo.totalMem
        val availableBytes = memoryInfo.availMem
        val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)
        val usagePercent = if (totalBytes > 0) {
            ((usedBytes * 100.0) / totalBytes).roundToInt()
        } else {
            0
        }

        val memInfoRaw = ProcFs.readText(MEM_INFO_PATH)
        val swapTotalKb = extractKiloBytes(memInfoRaw, "SwapTotal")
        val swapFreeKb = extractKiloBytes(memInfoRaw, "SwapFree")
        val swapUsedKb = if (swapTotalKb != null && swapFreeKb != null) {
            (swapTotalKb - swapFreeKb).coerceAtLeast(0L)
        } else {
            null
        }

        return RamInfo(
            totalBytes = totalBytes,
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
            nominalFrequencyMHz = detectNominalFrequency(),
            currentFrequencyMHz = readCurrentFrequencyMHz(),
            memInfoPreview = ProcFs.preview(memInfoRaw, "原始 /proc/meminfo")
        )
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

    /** 系统不暴露内存标称频率，保留接口以便后续接入更多平台节点。 */
    private fun detectNominalFrequency(): Int? = null

    /** 读取实时内存频率节点，并把 Hz / KHz 统一换算成 MHz。 */
    private fun readCurrentFrequencyMHz(): Int? {
        val raw = ProcFs.firstLong(RAM_FREQUENCY_PATHS) ?: return null
        if (raw <= 0) return null

        val megaHertz = when {
            raw >= 1_000_000_000L -> (raw / 1_000_000L).toInt()
            raw >= 100_000L -> (raw / 1_000L).toInt()
            raw in 100..12_000 -> raw.toInt()
            else -> return null
        }
        return megaHertz.takeIf { it in 100..12_000 }
    }

    private fun extractKiloBytes(memInfoRaw: String, key: String): Long? {
        val regex = Regex("""^$key:\s+(\d+)\s+kB$""", RegexOption.MULTILINE)
        return regex.find(memInfoRaw)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }
}
