package com.rjy.xyz.apps.xyzinfo.data

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 最小化未捕获异常记录器。
 *
 * 只在私有目录保留最近几份堆栈，记录完成后继续交给系统原有异常处理器。
 * 不自动重启、不吞异常，也不采集账号、文件内容等用户数据。
 */
object CrashRecovery {

    private const val MAX_REPORTS = 5
    private const val MAX_REPORT_CHARS = 64 * 1024

    @Volatile
    private var installed = false

    fun install(app: Application) {
        synchronized(this) {
            if (installed) return
            installed = true
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                runCatching { record(app, thread.name, error) }
                if (previous != null) {
                    previous.uncaughtException(thread, error)
                } else {
                    Process.killProcess(Process.myPid())
                    kotlin.system.exitProcess(10)
                }
            }
            prune(app)
        }
    }

    internal fun record(context: Context, threadName: String, error: Throwable) {
        val timestamp = System.currentTimeMillis()
        val stack = StringWriter().also { writer ->
            PrintWriter(writer).use { print -> error.printStackTrace(print) }
        }.toString().take(MAX_REPORT_CHARS)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
        val report = buildString {
            appendLine("XYZ-Devinfo crash report")
            appendLine("time=$stamp")
            appendLine("thread=$threadName")
            appendLine("version=${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
            appendLine("android=${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} / ${Build.DEVICE}")
            appendLine()
            append(stack)
        }
        directory(context).mkdirs()
        File(directory(context), "crash-$timestamp.txt").writeText(report)
        prune(context)
    }

    fun reports(context: Context): List<File> =
        directory(context).listFiles()
            ?.filter { it.isFile && it.name.startsWith("crash-") && it.extension == "txt" }
            ?.sortedByDescending { it.name }
            .orEmpty()

    fun count(context: Context): Int = reports(context).size

    fun latestSummary(context: Context): String? =
        reports(context).firstOrNull()?.let { file ->
            runCatching { file.readLines().take(8).joinToString("\n") }.getOrNull()
        }

    fun clear(context: Context) {
        reports(context).forEach { runCatching { it.delete() } }
    }

    fun prune(context: Context) {
        reports(context).drop(MAX_REPORTS).forEach { runCatching { it.delete() } }
    }

    private fun directory(context: Context): File = File(context.filesDir, "diagnostics")
}
