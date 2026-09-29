package com.rjy.xyz.apps.xyzinfo.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import com.rjy.xyz.apps.xyzinfo.model.RamInfo
import com.rjy.xyz.apps.xyzinfo.util.DeviceFacts
import com.rjy.xyz.apps.xyzinfo.util.Labels
import com.rjy.xyz.apps.xyzinfo.util.ProcFs
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 读取运行内存、堆信息与交换分区数据。
 */
object RamInfoProvider {

    private const val MEM_INFO_PATH = "/proc/meminfo"

    private const val DEVFREQ_CLASS_DIR = "/sys/class/devfreq"

    /** 部分平台（如高通）把 devfreq 节点挂在 /sys/devices/platform/soc/<设备>/devfreq/<节点>。 */
    private const val SOC_PLATFORM_DIR = "/sys/devices/platform/soc"

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

    /** 按芯片型号推断内存世代的关键字（保守列举，界面会标注“按芯片推断”）。 */
    private val LPDDR5X_CHIPS = listOf(
        "8 Elite", "8 Gen 3", "9400", "9500", "9300", "Tensor G5", "玄戒 O3"
    )

    private val LPDDR5_CHIPS = listOf(
        "8 Gen 2", "8 Gen 1", "8+ Gen 1", "888", "865", "870", "8s Gen", "7+ Gen 3", "7 Gen 3",
        "9200", "9000", "1200", "8200", "8300", "8400", "8350",
        "Tensor G1", "Tensor G2", "Tensor G3", "Tensor G4",
        "麒麟 9000", "麒麟 9010", "麒麟 9020", "玄戒 O1"
    )

    private val LPDDR4X_CHIPS = listOf(
        "778G", "780G", "765G", "750G", "730G", "732G", "720G", "7 Gen 1", "7s Gen",
        "695", "690", "680", "685", "675", "662", "665", "660", "636",
        "835", "845", "855", "860",
        "麒麟 990", "麒麟 985", "麒麟 980", "麒麟 970", "麒麟 960", "麒麟 950",
        "麒麟 820", "麒麟 810", "麒麟 710",
        "T820", "T770", "T760", "T730", "T618", "T616", "T610", "T612", "T606",
        "SC9863A", "SC9832E",
        "7050", "7200", "6100", "天玑 930", "1080", "天玑 900", "天玑 800",
        "Helio G99", "Helio G95", "Helio G88", "Helio G85", "Helio G80"
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
        val reading = frequency.reading

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
            currentFrequencyMHz = reading?.currentMHz,
            minFrequencyMHz = reading?.minMHz,
            maxFrequencyMHz = reading?.maxMHz,
            frequencySource = reading?.source,
            frequencyNote = if (reading == null) frequency.note else null,
            inferredMemoryType = inferMemoryType(),
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
     * 依次尝试：标准 devfreq 目录、平台目录下挂载的 devfreq 节点、老平台固定节点。
     * 部分机型（尤其高通新平台）节点存在但被 SELinux 限制，普通应用读不到，
     * 这种情况会返回原因说明，界面直接展示，便于用户判断是不是权限问题。
     */
    private fun readMemoryFrequency(): FrequencyProbe {
        val nodes = collectMemoryDevfreqNodes()

        for (node in nodes) {
            val base = node.absolutePath
            val currentMHz = toMegaHertz(ProcFs.readLong("$base/cur_freq"))
            if (currentMHz == null) continue
            return FrequencyProbe(
                reading = MemoryFrequency(
                    currentMHz = currentMHz,
                    minMHz = toMegaHertz(ProcFs.readLong("$base/min_freq")),
                    maxMHz = toMegaHertz(ProcFs.readLong("$base/max_freq")),
                    source = node.name
                ),
                note = null
            )
        }

        for (path in FALLBACK_FREQUENCY_PATHS) {
            val currentMHz = toMegaHertz(ProcFs.readLong(path)) ?: continue
            return FrequencyProbe(
                reading = MemoryFrequency(
                    currentMHz = currentMHz,
                    minMHz = null,
                    maxMHz = null,
                    source = path.substringAfterLast('/')
                ),
                note = null
            )
        }

        return FrequencyProbe(reading = null, note = unavailableReason(nodes))
    }

    /** 收集与内存相关的 devfreq 节点，DDR 直连节点优先于内存互联节点。 */
    private fun collectMemoryDevfreqNodes(): List<File> {
        val nodes = mutableListOf<File>()

        ProcFs.listFiles(DEVFREQ_CLASS_DIR)
            .filter { it.isDirectory && it.isMemoryRelated() }
            .forEach { nodes += it }

        for (deviceDir in ProcFs.listFiles(SOC_PLATFORM_DIR)) {
            val devfreqDir = File(deviceDir, "devfreq")
            ProcFs.listFiles(devfreqDir.absolutePath)
                .filter { it.isDirectory && it.isMemoryRelated() }
                .forEach { nodes += it }
        }

        return nodes.distinct().sortedBy { nodePriority(it.name) }
    }

    private fun File.isMemoryRelated(): Boolean =
        name.isDdrNode() || name.isInterconnectNode()

    private fun String.isDdrNode(): Boolean {
        val lower = lowercase(Locale.ROOT)
        return DDR_NODE_KEYWORDS.any { it in lower }
    }

    private fun String.isInterconnectNode(): Boolean {
        val lower = lowercase(Locale.ROOT)
        return INTERCONNECT_NODE_KEYWORDS.any { it in lower }
    }

    private fun nodePriority(name: String): Int = if (name.isDdrNode()) 0 else 1

    private fun unavailableReason(nodes: List<File>): String = when {
        nodes.isEmpty() -> "系统中未找到可读的 DDR 调频节点"
        else -> "找到节点 ${nodes.first().name} 但无读取权限（系统限制，部分机型需 root）"
    }

    /**
     * 系统不公开内存颗粒型号，这里按匹配到的芯片型号保守推断世代，
     * 结果显示时会明确标注“按芯片推断”。
     */
    private fun inferMemoryType(): String? {
        val chipName = SocInfoProvider.findSpec()?.displayName ?: return null

        return when {
            chipName.matchesAny(LPDDR5X_CHIPS) -> "LPDDR5X 级别"
            chipName.matchesAny(LPDDR5_CHIPS) -> "LPDDR5 级别"
            chipName.matchesAny(LPDDR4X_CHIPS) -> "LPDDR4X 级别"
            else -> null
        }
    }

    private fun String.matchesAny(keywords: List<String>): Boolean =
        keywords.any { it in this }

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

    private data class FrequencyProbe(
        val reading: MemoryFrequency?,
        val note: String?
    )

    private fun extractKiloBytes(memInfoRaw: String, key: String): Long? {
        val regex = Regex("""^$key:\s+(\d+)\s+kB$""", RegexOption.MULTILINE)
        return regex.find(memInfoRaw)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }
}
