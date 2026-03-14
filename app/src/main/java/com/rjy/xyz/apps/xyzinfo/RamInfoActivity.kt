package com.rjy.xyz.apps.xyzinfo

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

class RamInfoActivity : AppCompatActivity() {

    private lateinit var tvRamTitle: TextView
    private lateinit var tvRamSubTitle: TextView
    private lateinit var tvRamType: TextView
    private lateinit var tvRamBrand: TextView
    private lateinit var tvRamFreq: TextView
    private lateinit var tvRamCurrentFreq: TextView

    private lateinit var tvTotalRam: TextView
    private lateinit var tvUsedRam: TextView
    private lateinit var tvAvailRam: TextView
    private lateinit var tvUsagePercent: TextView
    private lateinit var tvLowMemory: TextView
    private lateinit var tvThreshold: TextView

    private lateinit var tvJavaHeapMax: TextView
    private lateinit var tvNativeHeapSize: TextView
    private lateinit var tvNativeHeapAllocated: TextView

    private lateinit var tvSwapTotal: TextView
    private lateinit var tvSwapUsed: TextView
    private lateinit var tvMemInfoRaw: TextView

    private lateinit var cardHeader: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ram_info)

        initViews()
        loadRamInfo()
    }

    private fun initViews() {
        tvRamTitle = findViewById(R.id.tvRamTitle)
        tvRamSubTitle = findViewById(R.id.tvRamSubTitle)
        tvRamType = findViewById(R.id.tvRamType)
        tvRamBrand = findViewById(R.id.tvRamBrand)
        tvRamFreq = findViewById(R.id.tvRamFreq)
        tvRamCurrentFreq = findViewById(R.id.tvRamCurrentFreq)

        tvTotalRam = findViewById(R.id.tvTotalRam)
        tvUsedRam = findViewById(R.id.tvUsedRam)
        tvAvailRam = findViewById(R.id.tvAvailRam)
        tvUsagePercent = findViewById(R.id.tvUsagePercent)
        tvLowMemory = findViewById(R.id.tvLowMemory)
        tvThreshold = findViewById(R.id.tvThreshold)

        tvJavaHeapMax = findViewById(R.id.tvJavaHeapMax)
        tvNativeHeapSize = findViewById(R.id.tvNativeHeapSize)
        tvNativeHeapAllocated = findViewById(R.id.tvNativeHeapAllocated)

        tvSwapTotal = findViewById(R.id.tvSwapTotal)
        tvSwapUsed = findViewById(R.id.tvSwapUsed)
        tvMemInfoRaw = findViewById(R.id.tvMemInfoRaw)

        cardHeader = findViewById(R.id.cardHeader)
    }

    private fun loadRamInfo() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memoryInfo)

        val totalRam = memoryInfo.totalMem
        val availRam = memoryInfo.availMem
        val usedRam = (totalRam - availRam).coerceAtLeast(0L)
        val usagePercent = if (totalRam > 0) {
            ((usedRam * 100.0) / totalRam).roundToInt()
        } else {
            0
        }

        val lowMemory = if (memoryInfo.lowMemory) "是" else "否"
        val threshold = memoryInfo.threshold

        val javaHeapMax = Runtime.getRuntime().maxMemory()
        val nativeHeapSize = android.os.Debug.getNativeHeapSize()
        val nativeHeapAllocated = android.os.Debug.getNativeHeapAllocatedSize()

        val memInfoRaw = readFileText("/proc/meminfo")
        val swapTotalKb = extractMemInfoKb(memInfoRaw, "SwapTotal")
        val swapFreeKb = extractMemInfoKb(memInfoRaw, "SwapFree")
        val swapUsedKb = if (swapTotalKb != null && swapFreeKb != null) {
            (swapTotalKb - swapFreeKb).coerceAtLeast(0L)
        } else null

        val ramTypeGuess = guessRamType()
        val ramBrandGuess = guessRamBrand()
        val ramFreqGuess = guessRamFreq()
        val ramCurrentFreqGuess = readCurrentRamFreq()

        tvRamTitle.text = formatRamTitle(totalRam)
        tvRamSubTitle.text = "内存状态、堆信息与交换分区总览"

        tvRamType.text = "RAM 类型：$ramTypeGuess"
        tvRamBrand.text = "RAM 品牌：$ramBrandGuess"
        tvRamFreq.text = "RAM 标称频率：$ramFreqGuess"
        tvRamCurrentFreq.text = "RAM 当前频率：$ramCurrentFreqGuess"

        tvTotalRam.text = "总内存：${formatBytes(totalRam)}"
        tvUsedRam.text = "当前已用：${formatBytes(usedRam)}"
        tvAvailRam.text = "当前可用：${formatBytes(availRam)}"
        tvUsagePercent.text = "内存占用率：$usagePercent%"
        tvLowMemory.text = "系统低内存状态：$lowMemory"
        tvThreshold.text = "低内存阈值：${formatBytes(threshold)}"

        tvJavaHeapMax.text = "Java 堆上限：${formatBytes(javaHeapMax)}"
        tvNativeHeapSize.text = "Native Heap 总大小：${formatBytes(nativeHeapSize)}"
        tvNativeHeapAllocated.text = "Native Heap 已分配：${formatBytes(nativeHeapAllocated)}"

        tvSwapTotal.text = "Swap / ZRAM 总量：${swapTotalKb?.let { formatBytes(it * 1024) } ?: "未知"}"
        tvSwapUsed.text = "Swap / ZRAM 已用：${swapUsedKb?.let { formatBytes(it * 1024) } ?: "未知"}"

        tvMemInfoRaw.text = buildMemInfoPreview(memInfoRaw)
    }

    private fun formatRamTitle(totalRam: Long): String {
        val gb = totalRam / 1024.0 / 1024.0 / 1024.0
        val rounded = when {
            gb >= 23 -> "24GB RAM"
            gb >= 17 -> "18GB RAM"
            gb >= 15 -> "16GB RAM"
            gb >= 11 -> "12GB RAM"
            gb >= 7 -> "8GB RAM"
            gb >= 5 -> "6GB RAM"
            gb >= 3 -> "4GB RAM"
            gb >= 2 -> "3GB RAM"
            else -> String.format(Locale.getDefault(), "%.1fGB RAM", gb)
        }
        return rounded
    }

    private fun guessRamType(): String {
        val text = (
                readFileText("/proc/meminfo") + " " +
                        safe(Build.MODEL) + " " +
                        safe(Build.PRODUCT) + " " +
                        safe(Build.DEVICE)
                ).lowercase(Locale.getDefault())

        return when {
            "lpddr5x" in text -> "LPDDR5X"
            "lpddr5" in text -> "LPDDR5"
            "lpddr4x" in text -> "LPDDR4X"
            "lpddr4" in text -> "LPDDR4"
            else -> "系统未公开"
        }
    }

    private fun guessRamBrand(): String {
        return "系统未公开"
    }

    private fun guessRamFreq(): String {
        return "系统未公开"
    }

    private fun readCurrentRamFreq(): String {
        val candidatePaths = listOf(
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

        for (path in candidatePaths) {
            val value = readLongSafely(path) ?: continue
            val normalizedMHz = normalizePossibleRamFreqToMHz(value)

            if (normalizedMHz != null && normalizedMHz in 100..12000) {
                return "$normalizedMHz MHz"
            }
        }

        return "系统未公开"
    }

    private fun normalizePossibleRamFreqToMHz(raw: Long): Int? {
        if (raw <= 0) return null

        return when {
            // 常见 Hz
            raw >= 1_000_000_000L -> (raw / 1_000_000L).toInt()
            // 常见 KHz
            raw >= 100_000L -> (raw / 1_000L).toInt()
            // 已经是 MHz
            raw in 100..12000 -> raw.toInt()
            else -> null
        }
    }

    private fun normalizeFreqText(raw: Long): String {
        return when {
            raw >= 1_000_000_000L -> String.format(Locale.getDefault(), "%.2f GHz", raw / 1_000_000_000.0)
            raw >= 1_000_000L -> String.format(Locale.getDefault(), "%.0f MHz", raw / 1_000_000.0)
            raw >= 1_000L -> String.format(Locale.getDefault(), "%.0f KHz", raw / 1_000.0)
            else -> raw.toString()
        }
    }

    private fun extractMemInfoKb(raw: String, key: String): Long? {
        val regex = Regex("""^$key:\s+(\d+)\s+kB$""", RegexOption.MULTILINE)
        return regex.find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private fun buildMemInfoPreview(raw: String): String {
        if (raw.isBlank()) return "原始 /proc/meminfo：读取失败"
        return "原始 /proc/meminfo 预览：\n" +
                raw.lines()
                    .filter { it.isNotBlank() }
                    .take(18)
                    .joinToString("\n")
    }

    private fun readLongSafely(path: String): Long? {
        return try {
            File(path).readText().trim().toLongOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun readFileText(path: String): String {
        return try {
            File(path).readText()
        } catch (_: Exception) {
            ""
        }
    }

    private fun formatBytes(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024

        return when {
            bytes >= gb -> String.format(Locale.getDefault(), "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.getDefault(), "%.2f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.getDefault(), "%.2f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    private fun safe(value: String?): String {
        return if (value.isNullOrBlank()) "未知" else value
    }
}