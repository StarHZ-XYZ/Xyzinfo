package com.rjy.xyz.apps.xyzinfo.data

import android.content.Context
import android.os.Build
import com.rjy.xyz.apps.xyzinfo.BuildConfig
import com.rjy.xyz.apps.xyzinfo.data.benchmark.CpuBenchmark
import com.rjy.xyz.apps.xyzinfo.data.benchmark.GeekerwanScores
import com.rjy.xyz.apps.xyzinfo.data.benchmark.RankingCache
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 隐藏诊断中心的数据整理与安全修复。这里只清理可再生成的缓存，不动用户记录与设置。 */
object DiagnosticCenter {

    data class RepairResult(val removedFiles: Int, val removedBytes: Long) {
        val sizeLabel: String
            get() = when {
                removedBytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", removedBytes / 1048576.0)
                removedBytes >= 1024 -> String.format(Locale.US, "%.1f KB", removedBytes / 1024.0)
                else -> "$removedBytes B"
            }
    }

    fun report(context: Context): String {
        val form = runCatching { DeviceFormDetector.detect(context) }.getOrNull()
        val model = runCatching { DeviceNameRepository.lookup(context) }.getOrNull()
        val crash = CrashRecovery.latestSummary(context)
        val lastBenchmark = SettingsRepository.lastBenchmark(context)
        return buildString {
            appendLine("XYZ-Devinfo 诊断报告")
            appendLine("生成时间：${timestamp(System.currentTimeMillis())}")
            appendLine("版本：${BuildConfig.VERSION_NAME}（${BuildConfig.BUILD_NUMBER}）")
            appendLine("设备：${model ?: "未识别"} / ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("形态：${form?.description ?: "未识别"}")
            appendLine("安全状态：${SecurityGuard.diagnosticStatus(context)}")
            appendLine("XYZ Index：v${CpuBenchmark.INDEX_VERSION}")
            appendLine("跑分口径：${SettingsRepository.BENCHMARK_ENGINE_VERSION}")
            appendLine("榜单快照：${GeekerwanScores.datasetVersion}（${if (GeekerwanScores.usingDownloaded) "在线数据" else "内置数据"}）")
            appendLine("轻量稳定模式：${if (SettingsRepository.lightweightMode(context)) "开启" else "关闭"}")
            appendLine("崩溃记录：${CrashRecovery.count(context)} 条")
            appendLine("临时缓存：${formatBytes(directorySize(context.cacheDir))}")
            appendLine("最近跑分：${lastBenchmark?.let { "单核 ${it.single} / 多核 ${it.multi} / GPU ${it.gpu ?: "未测"}" } ?: "无有效记录"}")
            if (crash != null) {
                appendLine()
                appendLine("最近崩溃摘要：")
                appendLine(crash)
            }
        }.trimEnd()
    }

    fun repairTransient(context: Context): RepairResult {
        var files = 0
        var bytes = 0L
        val cache = context.cacheDir
        cache.listFiles().orEmpty().forEach { child ->
            val size = directorySize(child)
            if (child.deleteRecursively()) {
                files++
                bytes += size
            }
        }
        RankingCache.clear(context)
        CrashRecovery.prune(context)
        return RepairResult(files, bytes)
    }

    private fun directorySize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles().orEmpty().sumOf(::directorySize)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun timestamp(value: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(value))
}
