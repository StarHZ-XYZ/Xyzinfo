package com.rjy.xyz.apps.xyzinfo.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * 大肥鱼验机：把各个检测模块的结果汇总成一份「验机体检报告」。
 *
 * 规则是**本地跑的**（不联网、不上传任何数据），逐条对照常见翻新 / 山寨 / 异常特征：
 * 芯片能否识别、内存标称与实测是否吻合、传感器是否齐全、系统签名与 SELinux 状态、
 * 是否存在 root 痕迹、机型库能否命中……最后给出评分与结论。
 *
 * 关于 DeepSeek：报告里的"AI 解读"走 DeepSeek 接口（需要你在设置里填 API Key）。
 * 未配置时只显示本地规则结论，不会偷偷联网。
 */
object DeviceInspector {

    data class Finding(
        val title: String,
        val detail: String,
        val level: EnvironmentCheck.Level
    )

    data class Report(
        val score: Int,
        val verdict: String,
        val findings: List<Finding>
    ) {
        val riskCount: Int get() = findings.count { it.level == EnvironmentCheck.Level.RISK }
        val noticeCount: Int get() = findings.count { it.level == EnvironmentCheck.Level.NOTICE }
    }

    fun inspect(context: Context): Report {
        val findings = mutableListOf<Finding>()
        var score = 100

        // 1) 芯片识别
        val chip = runCatching { SocInfoProvider.findSpec()?.displayName }.getOrNull()
        if (chip.isNullOrBlank()) {
            findings += Finding(
                "芯片识别",
                "没能在内置规格库里认出本机 SoC（代号 ${Build.HARDWARE} / ${Build.BOARD}）。"
                    + "小众机型或工程机比较常见，山寨机也可能出现。",
                EnvironmentCheck.Level.NOTICE
            )
            score -= 8
        } else {
            findings += Finding("芯片识别", "已识别为 $chip", EnvironmentCheck.Level.SAFE)
        }

        // 2) 机型库命中情况（有没有上市机型名）
        val deviceName = runCatching {
            DeviceNameRepository.lookup(context, Build.DEVICE, Build.MODEL)
        }.getOrNull()
        if (deviceName.isNullOrBlank()) {
            findings += Finding(
                "机型名称",
                "内置机型库没有对应条目（设备代号 ${Build.DEVICE} / 型号 ${Build.MODEL}），"
                    + "可以点首页的「机型库更新」拉一次最新名单。",
                EnvironmentCheck.Level.NOTICE
            )
            score -= 4
        } else {
            findings += Finding("机型名称", "机型库命中：$deviceName", EnvironmentCheck.Level.SAFE)
        }

        // 3) 内存：标称与实测的差额
        runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
            val totalGb = info.totalMem / 1024.0 / 1024 / 1024
            val nominal = nearestNominalGb(totalGb)
            val gapPercent = if (nominal > 0) (nominal - totalGb) / nominal * 100 else 0.0
            if (gapPercent > 15) {
                findings += Finding(
                    "内存容量",
                    "实测 ${"%.1f".format(totalGb)}GB，标称档 $nominal" + "GB，差额偏大" +
                        "（${"%.0f".format(gapPercent)}%），建议核对是否改过配置。",
                    EnvironmentCheck.Level.NOTICE
                )
                score -= 6
            } else {
                findings += Finding(
                    "内存容量",
                    "实测 ${"%.1f".format(totalGb)}GB ≈ 标称 $nominal" + "GB，吻合",
                    EnvironmentCheck.Level.SAFE
                )
            }
        }

        // 4) 传感器齐全度（山寨机常缺陀螺仪 / 磁力计）
        val manager = context.packageManager
        val hasAccelerometer = manager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)
        val hasGyroscope = manager.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)
        val hasCompass = manager.hasSystemFeature(PackageManager.FEATURE_SENSOR_COMPASS)
        val sensorIssues = buildList {
            if (!hasAccelerometer) add("加速度计")
            if (!hasGyroscope) add("陀螺仪")
            if (!hasCompass) add("磁力计/指南针")
        }
        if (sensorIssues.isNotEmpty()) {
            findings += Finding(
                "传感器齐全度",
                "缺少：${sensorIssues.joinToString("、")}。千元以上的正规机型通常都带齐，"
                    + "缺失较多时建议谨慎。",
                EnvironmentCheck.Level.NOTICE
            )
            score -= 3 * sensorIssues.size
        } else {
            findings += Finding("传感器齐全度", "加速度计 / 陀螺仪 / 磁力计 均在", EnvironmentCheck.Level.SAFE)
        }

        // 5) 系统签名与调试状态
        if (Build.TAGS.orEmpty().contains("test-keys")) {
            findings += Finding(
                "系统签名",
                "Build.TAGS 含 test-keys：多为开发版或第三方固件（也可能是翻新机刷的包）。",
                EnvironmentCheck.Level.RISK
            )
            score -= 10
        } else {
            findings += Finding("系统签名", "release-keys，正常零售固件", EnvironmentCheck.Level.SAFE)
        }

        // 6) 环境检测结果（复用已有内核）
        val env = runCatching { EnvironmentCheck.run(context) }.getOrNull()
        if (env != null) {
            env.items.filter { it.level != EnvironmentCheck.Level.SAFE }.forEach { item ->
                findings += Finding("环境 · ${item.title}", item.evidence, item.level)
            }
            score -= env.riskCount * 12 + env.noticeCount * 3
            if (env.riskCount == 0 && env.noticeCount == 0) {
                findings += Finding("运行环境", "没有发现 root 或异常环境痕迹", EnvironmentCheck.Level.SAFE)
            }
        }

        // 7) 形态与架构（纯信息，不扣分）
        val form = runCatching { DeviceFormDetector.detect(context) }.getOrNull()
        findings += Finding(
            "设备形态与架构",
            (form?.description ?: "未识别") + " ｜ ABI：" + Build.SUPPORTED_ABIS.joinToString(","),
            EnvironmentCheck.Level.SAFE
        )

        val finalScore = score.coerceIn(0, 100)
        val verdict = when {
            finalScore >= 88 -> "整体正常：没发现明显翻新或异常特征"
            finalScore >= 70 -> "基本正常，但有几处需要你自己确认"
            else -> "存在多处疑点，建议逐条核对下面列出的证据"
        }
        return Report(
            score = finalScore,
            verdict = verdict,
            findings = findings.sortedBy { it.level.ordinal * -1 }
        )
    }

    /** 把实测容量吸附到最近的常见容量档（2/3/4/6/8/12/16/18/24/32GB）。 */
    private fun nearestNominalGb(measured: Double): Int {
        val steps = listOf(2, 3, 4, 6, 8, 12, 16, 18, 24, 32)
        return steps.minByOrNull { kotlin.math.abs(it - measured) } ?: measured.toInt()
    }
}
