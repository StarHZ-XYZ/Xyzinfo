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
        val findings: List<Finding>,
        /** 结论库里命中的总结语句（内置几十条规则，可多条同时命中）。 */
        val summaries: List<String>
    ) {
        val riskCount: Int get() = findings.count { it.level == EnvironmentCheck.Level.RISK }
        val noticeCount: Int get() = findings.count { it.level == EnvironmentCheck.Level.NOTICE }
    }

    fun inspect(context: Context): Report {
        val findings = mutableListOf<Finding>()
        // 命中标记：给最后的结论库用
        val flags = mutableSetOf<String>()
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
            flags += "chip_unknown"
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
            flags += "name_unknown"
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
                flags += "ram_gap"
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
            sensorIssues.forEach { flags += "sensor_missing" }
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
            flags += "test_keys"
        } else {
            findings += Finding("系统签名", "release-keys，正常零售固件", EnvironmentCheck.Level.SAFE)
        }

        // 6) 环境检测结果（复用已有内核）
        val env = runCatching { EnvironmentCheck.run(context) }.getOrNull()
        if (env != null) {
            env.items.filter { it.level != EnvironmentCheck.Level.SAFE }.forEach { item ->
                findings += Finding("环境 · ${item.title}", item.evidence, item.level)
                when {
                    item.title.contains("root") -> flags += "root"
                    item.title.contains("SELinux") -> flags += "selinux"
                    item.title.contains("调试") -> flags += "adb"
                    item.title.contains("模拟器") -> flags += "emulator"
                    item.title.contains("Xposed") -> flags += "xposed"
                    item.title.contains("分身") -> flags += "clone"
                    item.title.contains("证书") -> flags += "userca"
                }
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
            findings = findings.sortedBy { it.level.ordinal * -1 },
            summaries = Summaries.pick(flags)
        )
    }

    /**
     * 结论库：内置三十多条"这种情况下该说什么"的规则。
     *
     * 按命中标记挑句子，可以同时命中多条；一条都没命中时给出"未发现明显问题"的通用结论。
     */
    private object Summaries {

        private val rules: List<Pair<String, String>> = listOf(
            "root" to "发现 root 组件：银行、支付、部分游戏可能拒绝运行；如果你没主动 root 过，建议检查是否刷过第三方固件。",
            "test_keys" to "系统不是官方 release 签名：常见于开发版固件或第三方 ROM，二手交易时属于需要重点确认的一项。",
            "selinux" to "SELinux 运行在宽容模式：安全性低于零售机默认状态，通常意味着系统被改动过。",
            "xposed" to "检测到 Xposed / LSPosed 痕迹：这类框架会改动系统行为，部分应用会因此打不开。",
            "emulator" to "检测到模拟器特征：当前大概率不是一台真实设备。",
            "clone" to "检测到分身 / 双开类应用：这类应用会创建独立运行环境，验机结果可能只反映其内部状态。",
            "userca" to "系统里安装了用户 CA 证书：常见于抓包调试，交易或日常使用前建议清理不认识的证书。",
            "adb" to "调试相关开关处于打开状态：日常使用建议关闭 USB 调试与开发者选项。",
            "chip_unknown" to "芯片没能识别：冷门机型可以先更新机型库再试；如果连代号都很奇怪，需要留意是否为山寨机。",
            "name_unknown" to "机型库没有命中上市机型名：小众品牌或工程机比较常见，也可能是改过型号的机器。",
            "ram_gap" to "内存实测容量与标称档位差异偏大：建议核对购买配置，避免买到改配或虚标机器。",
            "sensor_missing" to "传感器有缺失：陀螺仪 / 磁力计缺失会影响指南针、水平仪与部分游戏，千元以上机型通常不带缺。",
            "storage_low" to "存储剩余空间偏少：清理到 10% 以上更稳妥，否则影响系统更新与读写性能。",
            "low_hz" to "屏幕刷新率异常偏低：可能处于省电模式，也可能是屏幕更换后识别异常。",
            "no_camera" to "系统没有注册任何摄像头：如果不是工程机，需要高度警惕。",
            "no_arm64" to "CPU 不含 arm64-v8a：新应用的兼容性会受影响。",
            "old_android" to "系统版本较旧：安全补丁可能已停止更新，建议升级系统或谨慎安装来路不明的应用。",
            "chip_unknown" to "识别不出芯片时，把「SoC 信息」页里的 HARDWARE / BOARD 字段发给卖家核对也是一种办法。",
            "ram_gap" to "内存差额也可能来自系统预留，但超过 15% 就值得再确认一次。",
            "sensor_missing" to "建议到「传感器信息」页逐个看数值是否正常跳动；缺项或恒为 0 都要留意。",
            "test_keys" to "官方零售固件一般是 release-keys，这一条在二手交易里很好用。",
            "root" to "如果 root 不是你自己做的，建议先备份数据再考虑刷回官方固件。",
            "userca" to "用户证书配合代理可以解密流量，不认识就直接删掉。",
            "adb" to "如果你是开发者，忽略这条即可。",
            "emulator" to "模拟器环境下的硬件参数都是模拟值，不要用它判断真机性能。",
            "clone" to "分身应用里看到的存储与设备信息往往是虚拟化的，验机请回到主系统再跑一次。",
            "selinux" to "可以把这一条和其它证据一起对照，判断系统被改动到什么程度。",
            "old_android" to "老系统上部分新硬件（如高刷、长焦）功能可能被阉割。",
            "storage_low" to "长期接近写满还会影响闪存寿命，建议保持 15% 以上空闲。",
            "no_arm64" to "这条通常出现在很早的机型或低端方案上。",
            "xposed" to "如果你需要用到 SafetyNet / Play Integrity 相关应用，建议先停用框架。",
            "low_hz" to "若宣传为高刷机型却长期只有 60Hz 以下，值得进一步确认屏幕是否原装。",
            "no_camera" to "正常手机至少会注册前后两个摄像头，这一条出现请务必核查。",
            "name_unknown" to "可以用「机型库更新」拉一次最新名单，部分新机型就是这么补上的。"
        )

        fun pick(flags: Set<String>): List<String> {
            val hit = rules.filter { flags.contains(it.first) }.map { it.second }.distinct()
            return if (hit.isEmpty()) {
                listOf(
                    "各项检查都没有发现明显异常，这台机器的状态看起来正常。",
                    "建议连续使用几天后再跑一次验机对比：电池、温度与存储的变化最能反映长期状态。",
                    "如果这是二手设备，仍建议当面核对包装、发票与机身序列号是否一致。"
                )
            } else {
                hit.take(12)
            }
        }
    }

    /** 把实测容量吸附到最近的常见容量档（2/3/4/6/8/12/16/18/24/32GB）。 */
    private fun nearestNominalGb(measured: Double): Int {
        val steps = listOf(2, 3, 4, 6, 8, 12, 16, 18, 24, 32)
        return steps.minByOrNull { kotlin.math.abs(it - measured) } ?: measured.toInt()
    }
}
