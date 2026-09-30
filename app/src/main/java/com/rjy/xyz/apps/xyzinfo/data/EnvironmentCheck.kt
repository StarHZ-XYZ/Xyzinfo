package com.rjy.xyz.apps.xyzinfo.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.provider.Settings
import java.io.File
import java.security.KeyStore

/**
 * 环境检测（root / 痕迹 / 风险环境）。
 *
 * 原则：**只报「发现了什么痕迹」并给出证据，不直接下"你的手机被 root 了"的结论**，
 * 因为有些痕迹来自官方开发版固件或用户自己开的调试选项，不一定代表被入侵。
 *
 * 每一项都返回等级 + 证据文案，界面按等级分组展示：
 * SAFE 安全 / NOTICE 注意 / RISK 风险。
 */
object EnvironmentCheck {

    enum class Level { SAFE, NOTICE, RISK }

    data class Item(val title: String, val level: Level, val evidence: String)

    data class Report(val items: List<Item>) {
        val riskCount: Int get() = items.count { it.level == Level.RISK }
        val noticeCount: Int get() = items.count { it.level == Level.NOTICE }
        val conclusion: String
            get() = when {
                riskCount > 0 -> "检测到 $riskCount 项风险痕迹，$noticeCount 项需要注意"
                noticeCount > 0 -> "没有发现 root 类风险，但有 $noticeCount 项需要注意"
                else -> "没有发现明显痕迹，环境干净"
            }
    }

    /** root 二进制与常见管理器目录。 */
    private val ROOT_PATHS = listOf(
        "/system/bin/su", "/system/xbin/su", "/system/sbin/su", "/sbin/su",
        "/su/bin/su", "/vendor/bin/su", "/system/bin/failsafe/su",
        "/data/adb/magisk", "/data/adb/ksu", "/data/adb/modules", "/sbin/.magisk",
        "/system/app/Superuser.apk", "/system/xbin/daemonsu"
    )

    /** 分身 / 双开类应用的包名（常被用来隐藏环境）。 */
    private val CLONE_PACKAGES = listOf(
        "com.lbe.parallel", "com.excelliance.dualaid", "com.parallel.space",
        "com.ludashi.dualspace", "com.qihoo.magic"
    )

    fun run(context: Context): Report {
        val items = mutableListOf<Item>()
        items += checkRootFiles(context)
        items += checkBuildTags()
        items += checkDebugger(context)
        items += checkSelinux()
        items += checkEmulator()
        items += checkXposed()
        items += checkCloneApps(context)
        items += checkUserCertificates(context)
        return Report(items)
    }

    private fun checkRootFiles(context: Context): Item {
        val found = ROOT_PATHS.filter { path ->
            runCatching { File(path).exists() }.getOrDefault(false)
        }
        return if (found.isEmpty()) {
            Item("root 二进制 / 管理器目录", Level.SAFE, "常见 su 与 Magisk / KernelSU 路径均不存在")
        } else {
            Item(
                "root 二进制 / 管理器目录", Level.RISK,
                "发现：${found.joinToString("、")}"
            )
        }
    }

    private fun checkBuildTags(): Item {
        val tags = Build.TAGS.orEmpty()
        return if (tags.contains("test-keys")) {
            Item("系统构建签名", Level.NOTICE, "Build.TAGS 含 test-keys（多为开发版 / 第三方固件）")
        } else {
            Item("系统构建签名", Level.SAFE, "Build.TAGS = ${tags.ifBlank { "release-keys" }}")
        }
    }

    private fun checkDebugger(context: Context): Item {
        val attached = Debug.isDebuggerConnected()
        val adb = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        }.getOrDefault(false)
        val dev = runCatching {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
            ) == 1
        }.getOrDefault(false)
        return when {
            attached -> Item("调试状态", Level.RISK, "当前有调试器连接")
            adb -> Item("调试状态", Level.NOTICE, "USB 调试已开启${if (dev) "，开发者选项也已打开" else ""}")
            dev -> Item("调试状态", Level.NOTICE, "开发者选项已打开")
            else -> Item("调试状态", Level.SAFE, "未连接调试器，开发者也未开启")
        }
    }

    private fun checkSelinux(): Item = runCatching {
        // android.os.SELinux 不是公开 API，直接读 sysfs 更稳
        val raw = File("/sys/fs/selinux/enforce").readText().trim()
        if (raw == "1") {
            Item("SELinux", Level.SAFE, "强制模式（Enforcing）")
        } else {
            Item("SELinux", Level.RISK, "处于宽容模式（Permissive，enforce=$raw）——正常零售机不会这样")
        }
    }.getOrElse { Item("SELinux", Level.NOTICE, "读不到状态（部分机型不可读）：${it.message}") }

    private fun checkEmulator(): Item {
        val marks = buildList {
            if (Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("unknown")) add("FINGERPRINT=${Build.FINGERPRINT}")
            if (Build.MODEL.contains("sdk", true) || Build.MODEL.contains("emulator", true)) add("MODEL=${Build.MODEL}")
            if (Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu")) add("HARDWARE=${Build.HARDWARE}")
            if (Build.PRODUCT.contains("sdk")) add("PRODUCT=${Build.PRODUCT}")
        }
        return if (marks.isEmpty()) {
            Item("模拟器特征", Level.SAFE, "没有命中 QEMU / 模拟器特征")
        } else {
            Item("模拟器特征", Level.RISK, marks.joinToString("；"))
        }
    }

    private fun checkXposed(): Item {
        val hooked = runCatching {
            Throwable().stackTrace.any { frame ->
                val name = frame.className.lowercase()
                name.contains("xposed") || name.contains("lsposed") || name.contains("edxposed")
            }
        }.getOrDefault(false)
        return if (hooked) {
            Item("Xposed / LSPosed", Level.RISK, "调用栈里出现 Xposed 相关类名")
        } else {
            Item("Xposed / LSPosed", Level.SAFE, "调用栈未发现 Xposed 痕迹")
        }
    }

    private fun checkCloneApps(context: Context): Item {
        val installed = CLONE_PACKAGES.filter { pkg ->
            runCatching {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
        }
        return if (installed.isEmpty()) {
            Item("分身 / 双开类应用", Level.SAFE, "未安装常见分身类应用")
        } else {
            Item("分身 / 双开类应用", Level.NOTICE, "已安装：${installed.joinToString("、")}")
        }
    }

    /** 用户自己装的 CA 证书（被用来做中间人抓包的常见前提）。 */
    private fun checkUserCertificates(context: Context): Item = runCatching {
        val keyStore = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        val userCount = keyStore.aliases().toList().count { alias ->
            alias.toString().startsWith("user:")
        }
        if (userCount == 0) {
            Item("用户 CA 证书", Level.SAFE, "系统里没有用户安装的 CA 证书")
        } else {
            Item("用户 CA 证书", Level.NOTICE, "存在 $userCount 张用户 CA 证书（常用于抓包 / 代理）")
        }
    }.getOrElse { Item("用户 CA 证书", Level.NOTICE, "读取失败：${it.message}") }

    @Suppress("unused")
    private fun pm(context: Context): PackageManager = context.packageManager
}
