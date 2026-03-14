package com.rjy.xyz.apps.xyzinfo

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.util.Locale

class SocInfoActivity : AppCompatActivity() {

    private lateinit var tvSocModelTitle: TextView
    private lateinit var tvSocBrandSub: TextView
    private lateinit var tvBrandBadge: TextView
    private lateinit var tvSocLevelHint: TextView

    private lateinit var tvCpuArch: TextView
    private lateinit var tvAbi: TextView
    private lateinit var tvCoreCount: TextView
    private lateinit var tvCluster: TextView
    private lateinit var tvSocManufacturer: TextView
    private lateinit var tvSocModelCode: TextView
    private lateinit var tvHardware: TextView

    private lateinit var tvGpuName: TextView
    private lateinit var tvGpuCores: TextView
    private lateinit var tvGpuMinFreq: TextView
    private lateinit var tvGpuMaxFreq: TextView
    private lateinit var tvGraphicsApi: TextView

    private lateinit var tvPerCoreFreq: TextView
    private lateinit var tvCpuInfoRaw: TextView

    private lateinit var cardBrand: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_soc_info)

        initViews()
        loadSocInfo()
    }

    private fun initViews() {
        tvSocModelTitle = findViewById(R.id.tvSocModelTitle)
        tvSocBrandSub = findViewById(R.id.tvSocBrandSub)
        tvBrandBadge = findViewById(R.id.tvBrandBadge)
        tvSocLevelHint = findViewById(R.id.tvSocLevelHint)

        tvCpuArch = findViewById(R.id.tvCpuArch)
        tvAbi = findViewById(R.id.tvAbi)
        tvCoreCount = findViewById(R.id.tvCoreCount)
        tvCluster = findViewById(R.id.tvCluster)
        tvSocManufacturer = findViewById(R.id.tvSocManufacturer)
        tvSocModelCode = findViewById(R.id.tvSocModelCode)
        tvHardware = findViewById(R.id.tvHardware)

        tvGpuName = findViewById(R.id.tvGpuName)
        tvGpuCores = findViewById(R.id.tvGpuCores)
        tvGpuMinFreq = findViewById(R.id.tvGpuMinFreq)
        tvGpuMaxFreq = findViewById(R.id.tvGpuMaxFreq)
        tvGraphicsApi = findViewById(R.id.tvGraphicsApi)

        tvPerCoreFreq = findViewById(R.id.tvPerCoreFreq)
        tvCpuInfoRaw = findViewById(R.id.tvCpuInfoRaw)

        cardBrand = findViewById(R.id.cardBrand)
    }

    private fun loadSocInfo() {
        val cpuInfoRaw = readFileText("/proc/cpuinfo")
        val abiList = Build.SUPPORTED_ABIS.joinToString(", ")
        val coreCount = Runtime.getRuntime().availableProcessors()
        val gpuMaxFreq = readGpuMaxFreqMHz()
        val gpuMinFreq = readGpuMinFreqMHz()

        val candidates = buildCandidates(cpuInfoRaw)
        val deviceHints = buildDeviceHints()
        val spec = SocSpecRepository.findBestSpec(candidates, deviceHints, gpuMaxFreq)

        val socDisplayName = spec?.displayName ?: detectFallbackName(candidates)
        val socBrandName = spec?.brandName ?: detectFallbackBrand(cpuInfoRaw)
        val cpuArch = spec?.cpuArchitecture ?: detectCpuArchitecture(cpuInfoRaw)
        val clusterText = spec?.cpuClusters ?: detectCpuClusterInfo()
        val graphicsApi = buildGraphicsSupportText(spec)

        tvSocModelTitle.text = socDisplayName
        tvSocBrandSub.text = "SoC 品牌：$socBrandName"
        tvBrandBadge.text = spec?.badgeText ?: "芯片"
        tvSocLevelHint.text = buildSocLevelHint(spec?.performanceLevel ?: "未知")

        tvCpuArch.text = "CPU架构：$cpuArch"
        tvAbi.text = "ABI列表：$abiList"
        tvCoreCount.text = "CPU核心数：$coreCount 核"
        tvCluster.text = "CPU集群 / 大小核：$clusterText"
        tvSocManufacturer.text = "SoC制造商：${getSocManufacturer()}"
        tvSocModelCode.text = "SoC代号：${collectBestCode(candidates)}"
        tvHardware.text = "硬件代号：${safe(Build.HARDWARE)}"

        tvGpuName.text = "GPU型号：${spec?.gpuName ?: "未知"}"
        tvGpuCores.text = "GPU核心 / 计算单元：${spec?.gpuCores ?: "未公开"}"
        tvGpuMinFreq.text = "GPU最小频率：${gpuMinFreq?.let { "$it MHz" } ?: "未知"}"
        tvGpuMaxFreq.text =
            "GPU最大频率：${gpuMaxFreq?.let { "$it MHz" } ?: (spec?.gpuMaxFreqMHz?.let { "$it MHz（数据库）" } ?: "未知")}"
        tvGraphicsApi.text = "图形接口与版本：$graphicsApi\n${buildRendererGuessText()}"

        tvPerCoreFreq.text = buildPerCoreFreqText(coreCount)
        tvCpuInfoRaw.text = buildCpuInfoPreview(cpuInfoRaw)

        applyBrandStyle(spec?.badgeText ?: "芯片")
    }

    private fun buildCandidates(cpuInfoRaw: String): List<String> {
        val list = mutableListOf<String>()

        list += getSocModel()
        list += safe(Build.HARDWARE)
        list += safe(Build.BOARD)
        list += safe(Build.PRODUCT)
        list += safe(Build.DEVICE)
        list += safe(Build.MODEL)

        val regexList = listOf(
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

        for (regex in regexList) {
            regex.find(cpuInfoRaw)?.value?.let { list += it }
        }

        return list.filter { it.isNotBlank() && it != "未知" }.distinct()
    }

    private fun buildDeviceHints(): List<String> {
        return listOf(
            safe(Build.MODEL),
            safe(Build.DEVICE),
            safe(Build.PRODUCT),
            safe(Build.BRAND),
            safe(Build.MANUFACTURER)
        )
    }

    private fun collectBestCode(candidates: List<String>): String {
        return candidates.firstOrNull { Regex("""[A-Za-z]{1,6}\d{3,6}""").containsMatchIn(it) } ?: "未知"
    }

    private fun detectFallbackName(candidates: List<String>): String {
        return collectBestCode(candidates)
    }

    private fun detectFallbackBrand(cpuInfoRaw: String): String {
        val allText = (
                cpuInfoRaw + " " +
                        safe(Build.HARDWARE) + " " +
                        safe(Build.BOARD) + " " +
                        safe(Build.PRODUCT)
                ).lowercase(Locale.getDefault())

        return when {
            allText.contains("qualcomm") || allText.contains("qcom") || allText.contains("sm8") || allText.contains("sm7") || allText.contains("sm6") || allText.contains("sdm") || allText.contains("msm") -> "高通骁龙"
            allText.contains("mediatek") || allText.contains("mt") || allText.contains("dimensity") || allText.contains("helio") -> "联发科"
            allText.contains("exynos") || allText.contains("universal") || allText.contains("samsung") -> "三星 Exynos"
            allText.contains("kirin") || allText.contains("hisilicon") || allText.contains("hi36") || allText.contains("hi62") -> "华为麒麟"
            allText.contains("unisoc") || allText.contains("spreadtrum") || allText.contains("sprd") || allText.contains("ums") -> "紫光展锐 / 展讯"
            else -> "未知平台"
        }
    }

    private fun buildSocLevelHint(level: String): String {
        return when (level) {
            "顶级旗舰" -> "等级：顶级旗舰"
            "旗舰" -> "等级：旗舰"
            "次旗舰" -> "等级：次旗舰"
            "中高端" -> "等级：中高端"
            "中端" -> "等级：中端"
            "入门" -> "等级：入门"
            else -> "等级：待评定"
        }
    }

    private fun applyBrandStyle(badge: String) {
        val bgColor = when (badge) {
            "骁龙" -> "#FFF2E8"
            "联发科" -> "#EAF3FF"
            "猎户座" -> "#F1EEFF"
            "麒麟" -> "#ECFFF2"
            "展锐" -> "#FFF0F6"
            else -> "#F3F5F8"
        }
        val badgeColor = when (badge) {
            "骁龙" -> "#FF6A00"
            "联发科" -> "#247DFF"
            "猎户座" -> "#7253FF"
            "麒麟" -> "#18A957"
            "展锐" -> "#FF4F87"
            else -> "#6C7788"
        }

        cardBrand.setCardBackgroundColor(Color.parseColor(bgColor))

        val shape = GradientDrawable()
        shape.cornerRadius = 999f
        shape.setColor(Color.parseColor(badgeColor))
        tvBrandBadge.background = shape
        tvBrandBadge.setTextColor(Color.WHITE)
    }

    private fun detectCpuArchitecture(cpuInfoRaw: String): String {
        val text = (cpuInfoRaw + " " + Build.SUPPORTED_ABIS.joinToString(" "))
            .lowercase(Locale.getDefault())

        return when {
            "aarch64" in text || "arm64-v8a" in text || "armv9" in text || "armv8" in text -> "AArch64 / ARMv8-v9"
            "armeabi-v7a" in text || "armv7" in text -> "ARMv7"
            "x86_64" in text -> "x86_64"
            Regex("""\bx86\b""").containsMatchIn(text) -> "x86"
            "riscv64" in text || "riscv" in text -> "RISC-V"
            else -> "未知"
        }
    }

    private fun detectCpuClusterInfo(): String {
        val freqs = mutableListOf<Int>()

        val cpuDir = File("/sys/devices/system/cpu/")
        val cpuFolders = cpuDir.listFiles()?.filter {
            it.name.matches(Regex("""cpu\d+"""))
        } ?: emptyList()

        for (folder in cpuFolders) {
            val freqFile = File(folder, "cpufreq/cpuinfo_max_freq")
            val value = readIntSafely(freqFile.absolutePath)
            if (value > 0) freqs.add(value)
        }

        if (freqs.isEmpty()) return "无法识别"

        val groups = freqs.groupingBy { it }.eachCount().toList().sortedByDescending { it.first }

        return when (groups.size) {
            1 -> "${groups[0].second} 个核心，统一频率集群"
            2 -> {
                val high = groups[0]
                val low = groups[1]
                "大核：${high.second} 核（${formatFreqGHz(high.first)}）\n小核：${low.second} 核（${formatFreqGHz(low.first)}）"
            }
            3 -> {
                val high = groups[0]
                val mid = groups[1]
                val low = groups[2]
                "大核：${high.second} 核（${formatFreqGHz(high.first)}）\n中核：${mid.second} 核（${formatFreqGHz(mid.first)}）\n小核：${low.second} 核（${formatFreqGHz(low.first)}）"
            }
            else -> {
                groups.mapIndexed { index, pair ->
                    val label = when (index) {
                        0 -> "高频核心组"
                        1 -> "次高频核心组"
                        2 -> "中频核心组"
                        else -> "低频核心组$index"
                    }
                    "$label：${pair.second} 核（${formatFreqGHz(pair.first)}）"
                }.joinToString("\n")
            }
        }
    }

    private fun buildPerCoreFreqText(coreCount: Int): String {
        val builder = StringBuilder()
        builder.append("每核心最大频率：\n")
        for (i in 0 until coreCount) {
            val path = "/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq"
            val khz = readIntSafely(path)
            val line = if (khz > 0) "CPU$i：${formatFreqGHz(khz)}" else "CPU$i：未知"
            builder.append(line).append("\n")
        }
        return builder.toString().trim()
    }

    private fun buildGraphicsSupportText(spec: SocSpec?): String {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val glEsVersion = am.deviceConfigurationInfo?.glEsVersion ?: "未知"

        val pm = packageManager
        val vulkanSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        } else {
            false
        }

        val apiMain = when {
            vulkanSupported -> "OpenGL ES $glEsVersion / Vulkan"
            else -> "OpenGL ES $glEsVersion"
        }

        val dbText = spec?.graphicsApi ?: "未知"
        return "$apiMain ｜ 数据库：$dbText"
    }

    private fun readGpuMaxFreqMHz(): Int? {
        val paths = listOf(
            "/sys/class/kgsl/kgsl-3d0/max_gpuclk",
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
            "/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/max_gpuclk",
            "/sys/devices/platform/18500000.mali/max_clock",
            "/sys/class/devfreq/00000000.gpu/max_freq"
        )

        for (path in paths) {
            val v = readIntSafely(path)
            if (v > 0) return normalizeFreqToMHz(v)
        }
        return null
    }

    private fun readGpuMinFreqMHz(): Int? {
        val paths = listOf(
            "/sys/class/kgsl/kgsl-3d0/min_gpuclk",
            "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq",
            "/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/min_gpuclk",
            "/sys/devices/platform/18500000.mali/min_clock",
            "/sys/class/devfreq/00000000.gpu/min_freq"
        )

        for (path in paths) {
            val v = readIntSafely(path)
            if (v > 0) return normalizeFreqToMHz(v)
        }
        return null
    }

    private fun normalizeFreqToMHz(raw: Int): Int {
        return when {
            raw > 1_000_000 -> raw / 1_000_000
            raw > 1_000 -> raw / 1_000
            else -> raw
        }
    }

    private fun formatFreqGHz(khz: Int): String {
        val ghz = khz / 1_000_000.0
        return String.format(Locale.getDefault(), "%.2f GHz", ghz)
    }

    private fun buildCpuInfoPreview(raw: String): String {
        if (raw.isBlank()) return "原始 /proc/cpuinfo：读取失败"
        return "原始 /proc/cpuinfo 预览：\n" + raw.lines().filter { it.isNotBlank() }.take(18).joinToString("\n")
    }

    private fun getSocManufacturer(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) safe(Build.SOC_MANUFACTURER) else "未知"
    }

    private fun getSocModel(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) safe(Build.SOC_MODEL) else "未知"
    }

    private fun readIntSafely(path: String): Int {
        return try {
            File(path).readText().trim().toInt()
        } catch (_: Exception) {
            -1
        }
    }

    private fun readFileText(path: String): String {
        return try {
            File(path).readText()
        } catch (_: Exception) {
            ""
        }
    }

    private fun safe(value: String?): String {
        return if (value.isNullOrBlank()) "未知" else value
    }

    private fun buildRendererGuessText(): String {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val glEsVersion = am.deviceConfigurationInfo?.glEsVersion ?: "未知"

        val pm = packageManager
        val vulkanSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        } else {
            false
        }

        return if (vulkanSupported) {
            "当前渲染后端：系统可能优先使用 Vulkan（同时支持 OpenGL ES $glEsVersion）"
        } else {
            "当前渲染后端：更可能使用 OpenGL ES $glEsVersion"
        }
    }
}