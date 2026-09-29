package com.rjy.xyz.apps.xyzinfo.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.rjy.xyz.apps.xyzinfo.data.soc.SocSpecRepository
import com.rjy.xyz.apps.xyzinfo.model.SocInfo
import com.rjy.xyz.apps.xyzinfo.util.DeviceFacts
import com.rjy.xyz.apps.xyzinfo.util.Formats
import com.rjy.xyz.apps.xyzinfo.util.Labels
import com.rjy.xyz.apps.xyzinfo.util.ProcFs
import java.util.Locale

/**
 * 读取处理器、GPU 与图形能力信息。
 *
 * 识别流程：先从 /proc/cpuinfo 与 Build 字段里提取候选型号，
 * 再交给 [SocSpecRepository] 打分匹配，匹配不到时退回模糊识别结果。
 */
object SocInfoProvider {

    private val CPU_DIR = "/sys/devices/system/cpu"

    private val MODEL_PATTERNS = listOf(
        Regex("""sm\d{4}[a-z\-]*""", RegexOption.IGNORE_CASE),
        Regex("""sdm\d{3,4}[a-z\-]*""", RegexOption.IGNORE_CASE),
        Regex("""msm\d{4}[a-z\-]*""", RegexOption.IGNORE_CASE),
        Regex("""apq\d{4}[a-z\-]*""", RegexOption.IGNORE_CASE),
        Regex("""mt\d{4}[a-z0-9/\-]*""", RegexOption.IGNORE_CASE),
        Regex("""dimensity\s*\d{3,4}\+?s?""", RegexOption.IGNORE_CASE),
        Regex("""helio\s*[a-z]?\d{2,3}""", RegexOption.IGNORE_CASE),
        Regex("""s5e\d{4,5}""", RegexOption.IGNORE_CASE),
        Regex("""exynos\s*\d{3,4}""", RegexOption.IGNORE_CASE),
        Regex("""universal\d{3,4}""", RegexOption.IGNORE_CASE),
        Regex("""kirin\s*\d{3,4}[a-z0-9]*""", RegexOption.IGNORE_CASE),
        Regex("""hi\d{4}""", RegexOption.IGNORE_CASE),
        Regex("""ums\d{3,4}""", RegexOption.IGNORE_CASE),
        Regex("""t\d{3,4}""", RegexOption.IGNORE_CASE),
        Regex("""sc\d{4}[a-z0-9]*""", RegexOption.IGNORE_CASE)
    )

    private val GPU_MAX_FREQ_PATHS = listOf(
        "/sys/class/kgsl/kgsl-3d0/max_gpuclk",
        "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
        "/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/max_gpuclk",
        "/sys/devices/platform/18500000.mali/max_clock",
        "/sys/class/devfreq/00000000.gpu/max_freq"
    )

    private val GPU_MIN_FREQ_PATHS = listOf(
        "/sys/class/kgsl/kgsl-3d0/min_gpuclk",
        "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq",
        "/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/min_gpuclk",
        "/sys/devices/platform/18500000.mali/min_clock",
        "/sys/class/devfreq/00000000.gpu/min_freq"
    )

    fun load(context: Context): SocInfo {
        val cpuInfoRaw = ProcFs.readText(DeviceFacts.CPU_INFO_PATH)
        val coreCount = Runtime.getRuntime().availableProcessors()
        val gpuMaxFreqMHz = readFrequencyMHz(GPU_MAX_FREQ_PATHS)
        val gpuMinFreqMHz = readFrequencyMHz(GPU_MIN_FREQ_PATHS)

        val candidates = buildCandidates(cpuInfoRaw)
        val spec = SocSpecRepository.findBestSpec(candidates, deviceHints(), gpuMaxFreqMHz)
        val graphics = readGraphicsSupport(context)

        return SocInfo(
            displayName = spec?.displayName ?: collectBestCode(candidates),
            brandName = spec?.brandName ?: detectBrandFallback(cpuInfoRaw),
            badge = spec?.badgeText ?: DEFAULT_BADGE,
            performanceLevel = spec?.performanceLevel ?: Labels.UNKNOWN,
            cpuArchitecture = spec?.cpuArchitecture ?: DeviceFacts.cpuArchitecture(cpuInfoRaw),
            abiList = Build.SUPPORTED_ABIS.joinToString(", "),
            coreCount = coreCount,
            clusters = spec?.cpuClusters ?: detectCpuClusters(),
            manufacturer = socManufacturer(),
            modelCode = collectBestCode(candidates),
            hardware = DeviceFacts.orUnknown(Build.HARDWARE),
            gpuName = spec?.gpuName ?: Labels.UNKNOWN,
            gpuCores = spec?.gpuCores ?: Labels.NOT_PUBLIC,
            gpuMinFreqMHz = gpuMinFreqMHz,
            gpuMaxFreqMHz = gpuMaxFreqMHz,
            gpuMaxFreqFromProfileMHz = spec?.gpuMaxFreqMHz,
            graphicsApiFromProfile = spec?.graphicsApi ?: Labels.UNKNOWN,
            glEsVersion = graphics.glEsVersion,
            vulkanSupported = graphics.vulkanSupported,
            perCoreMaxFreqKHz = readPerCoreMaxFreq(coreCount),
            cpuInfoPreview = ProcFs.preview(cpuInfoRaw, "原始 /proc/cpuinfo")
        )
    }

    /** 从 Build 字段与 /proc/cpuinfo 里收集可能的 SoC 型号。 */
    fun buildCandidates(cpuInfoRaw: String): List<String> {
        val list = mutableListOf<String>()
        list += socModel()
        list += DeviceFacts.orUnknown(Build.HARDWARE)
        list += DeviceFacts.orUnknown(Build.BOARD)
        list += DeviceFacts.orUnknown(Build.PRODUCT)
        list += DeviceFacts.orUnknown(Build.DEVICE)
        list += DeviceFacts.orUnknown(Build.MODEL)

        for (pattern in MODEL_PATTERNS) {
            pattern.find(cpuInfoRaw)?.value?.let { list += it }
        }

        return list
            .filter { it.isNotBlank() && it != Labels.UNKNOWN }
            .distinct()
    }

    private fun deviceHints(): List<String> = listOf(
        DeviceFacts.orUnknown(Build.MODEL),
        DeviceFacts.orUnknown(Build.DEVICE),
        DeviceFacts.orUnknown(Build.PRODUCT),
        DeviceFacts.orUnknown(Build.BRAND),
        DeviceFacts.orUnknown(Build.MANUFACTURER)
    )

    /** 取出候选列表里最像型号编码的一项，例如 sm8650。 */
    private fun collectBestCode(candidates: List<String>): String =
        candidates.firstOrNull { CODE_PATTERN.containsMatchIn(it) } ?: Labels.UNKNOWN

    private fun detectBrandFallback(cpuInfoRaw: String): String {
        val allText = (
            cpuInfoRaw + " " +
                DeviceFacts.orUnknown(Build.HARDWARE) + " " +
                DeviceFacts.orUnknown(Build.BOARD) + " " +
                DeviceFacts.orUnknown(Build.PRODUCT)
            ).lowercase(Locale.ROOT)

        return when {
            allText.containsAny(
                "qualcomm", "qcom", "sm8", "sm7", "sm6", "sdm", "msm"
            ) -> "高通骁龙"
            allText.containsAny("mediatek", "mt", "dimensity", "helio") -> "联发科"
            allText.containsAny("exynos", "universal", "samsung") -> "三星 Exynos"
            allText.containsAny("kirin", "hisilicon", "hi36", "hi62") -> "华为麒麟"
            allText.containsAny("unisoc", "spreadtrum", "sprd", "ums") -> "紫光展锐 / 展讯"
            else -> "未知平台"
        }
    }

    /**
     * 依据各核心最大频率推断大小核结构。
     *
     * 相同频率的核心会被视为同一集群。
     */
    private fun detectCpuClusters(): String {
        val frequencies = readPerCoreMaxFreq().filterNotNull()
        if (frequencies.isEmpty()) return "无法识别"

        val groups = frequencies
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.first }

        return when (groups.size) {
            1 -> "${groups[0].second} 个核心，统一频率集群"
            2 -> clusterLine("大核", groups[0]) + "\n" + clusterLine("小核", groups[1])
            3 -> clusterLine("大核", groups[0]) + "\n" +
                clusterLine("中核", groups[1]) + "\n" +
                clusterLine("小核", groups[2])
            else -> groups.mapIndexed { index, group ->
                val label = when (index) {
                    0 -> "高频核心组"
                    1 -> "次高频核心组"
                    2 -> "中频核心组"
                    else -> "低频核心组$index"
                }
                clusterLine(label, group)
            }.joinToString("\n")
        }
    }

    private fun clusterLine(label: String, group: Pair<Int, Int>): String =
        "$label：${group.second} 核（${Formats.kiloHertzAsGigaHertz(group.first)}）"

    private fun readPerCoreMaxFreq(coreCount: Int = Runtime.getRuntime().availableProcessors()): List<Int?> =
        (0 until coreCount).map { index ->
            ProcFs.readInt("$CPU_DIR/cpu$index/cpufreq/cpuinfo_max_freq")
                ?.takeIf { it > 0 }
        }

    private fun readGraphicsSupport(context: Context): GraphicsSupport {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val glEsVersion = activityManager.deviceConfigurationInfo?.glEsVersion ?: Labels.UNKNOWN
        val vulkanSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        return GraphicsSupport(glEsVersion, vulkanSupported)
    }

    /** 读取 GPU 频率节点并统一换算成 MHz。 */
    private fun readFrequencyMHz(paths: List<String>): Int? {
        val raw = ProcFs.firstPositiveInt(paths) ?: return null
        return when {
            raw > 1_000_000 -> raw / 1_000_000
            raw > 1_000 -> raw / 1_000
            else -> raw
        }
    }

    private fun socManufacturer(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DeviceFacts.orUnknown(Build.SOC_MANUFACTURER)
        } else {
            Labels.UNKNOWN
        }

    private fun socModel(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DeviceFacts.orUnknown(Build.SOC_MODEL)
        } else {
            Labels.UNKNOWN
        }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { it in this }

    private data class GraphicsSupport(
        val glEsVersion: String,
        val vulkanSupported: Boolean
    )

    private val CODE_PATTERN = Regex("""[A-Za-z]{1,6}\d{3,6}""")

    private const val DEFAULT_BADGE = "芯片"
}
