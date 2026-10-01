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

    /**
     * root / 模块管理器类应用（1.0.7 新增）。
     *
     * 这些不一定代表"被 root"，但装了就说明用户有意在折腾环境，
     * 所以给 RISK 并写明装的是哪个。
     */
    private val TOOL_PACKAGES = mapOf(
        "com.topjohnwu.magisk" to "Magisk",
        "io.github.huskydg.magisk" to "Magisk Delta",
        "me.bmax.apatch" to "APatch",
        "com.kingroot.kinguser" to "KingRoot",
        "com.kingo.root" to "KingoRoot",
        "com.zachspong.temprootremovejb" to "临时 root",
        "de.robv.android.xposed.installer" to "Xposed Installer",
        "org.meowcat.edxposed.manager" to "EdXposed Manager",
        "org.lsposed.manager" to "LSPosed Manager",
        "com.sollyu.android.appenv" to "应用变量",
        "com.chelpus.lackypatch" to "幸运破解器",
        "me.weishu.exp" to "太极"
    )

    /** 游戏修改器 / 虚拟定位 / 自动化脚本类（常见于"检测外挂"的场景）。 */
    private val CHEAT_PACKAGES = mapOf(
        "catch_.me_.if_.you_.can_" to "GameGuardian",
        "com.cyjh.mobileanjian" to "按键精灵",
        "com.goldou.mobileanjian" to "按键精灵（旧版）",
        "com.lerist.fakelocation" to "Fake Location",
        "com.blogspot.newapphorizons.fakegps" to "Fake GPS",
        "com.lexa.fakegps" to "Fake GPS（旧版）",
        "com.rong.yxt" to "虚拟定位工具"
    )

    fun run(context: Context): Report {
        val items = mutableListOf<Item>()
        items += checkRootFiles(context)
        items += checkBuildTags()
        items += checkRomIntegrity()
        items += checkBootState()
        items += checkMounts()
        items += checkDebugger(context)
        items += checkSelinux()
        items += checkEmulator()
        items += checkXposed()
        items += checkCloneApps(context)
        items += checkRiskPackages(context)
        items += checkAccessibilityServices(context)
        items += checkDeviceAdmins(context)
        items += checkProxyAndVpn(context)
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

    // ---------- 1.0.7 新增的检测项 ----------

    /**
     * 系统属性读取：优先用隐藏的 `android.os.SystemProperties`，
     * 拿不到就退回读 `/system/build.prop` 之类的文本（部分机型 SELinux 会拦）。
     */
    private fun systemProperty(key: String): String? {
        runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java)
            val value = get.invoke(null, key) as? String
            if (!value.isNullOrBlank()) return value
        }
        listOf("/system/build.prop", "/vendor/build.prop", "/odm/etc/build.prop").forEach { path ->
            runCatching {
                File(path).readLines()
                    .firstOrNull { it.startsWith("$key=") }
                    ?.substringAfter('=')
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
            }
        }
        return null
    }

    /** ROM 是否是可调试 / 不安全的构建（userdebug、eng、ro.debuggable=1、ro.secure=0）。 */
    private fun checkRomIntegrity(): Item {
        val type = systemProperty("ro.build.type")
        val debuggable = systemProperty("ro.debuggable")
        val secure = systemProperty("ro.secure")
        val marks = buildList {
            if (type == "userdebug" || type == "eng") add("ro.build.type=$type")
            if (debuggable == "1") add("ro.debuggable=1")
            if (secure == "0") add("ro.secure=0")
        }
        return if (marks.isEmpty()) {
            Item(
                "ROM 构建类型",
                Level.SAFE,
                "正式版构建（ro.build.type=${type ?: "user"}，ro.secure=${secure ?: "1"}）"
            )
        } else {
            Item("ROM 构建类型", Level.NOTICE, "非正式版构建：${marks.joinToString("、")}（第三方 ROM 常见）")
        }
    }

    /** 引导链状态：verified boot 颜色 + bootloader 是否上锁。 */
    private fun checkBootState(): Item {
        val state = systemProperty("ro.boot.verifiedbootstate")
        val locked = systemProperty("ro.boot.flash.locked")
        val verity = systemProperty("ro.boot.veritymode")
        val marks = buildList {
            if (state == "orange") add("verifiedbootstate=orange（引导链被改过）")
            if (state == "yellow") add("verifiedbootstate=yellow（自定义密钥）")
            if (locked == "0") add("bootloader 已解锁")
            if (verity == "disabled") add("dm-verity 已关闭")
        }
        return when {
            marks.isEmpty() -> Item(
                "引导链 / 锁状态",
                Level.SAFE,
                "state=${state ?: "未上报"}，locked=${locked ?: "1"}，verity=${verity ?: "enforcing"}"
            )

            state == "orange" || locked == "0" ->
                Item("引导链 / 锁状态", Level.RISK, marks.joinToString("；"))

            else -> Item("引导链 / 锁状态", Level.NOTICE, marks.joinToString("；"))
        }
    }

    /** 挂载痕迹：Magisk 会把模块挂到 /system 上，overlay 与 rw 挂载也要点出来。 */
    private fun checkMounts(): Item {
        val lines = runCatching { File("/proc/mounts").readLines() }.getOrDefault(emptyList())
        val magisk = lines.filter { it.contains("magisk", true) }
        val overlay = lines.filter {
            it.contains("/system") && (it.contains("overlay") || it.contains("tmpfs"))
        }
        val rwSystem = lines.filter {
            it.contains("/system ") && it.split(" ").getOrNull(3)?.contains("rw") == true
        }
        return when {
            magisk.isNotEmpty() -> Item(
                "挂载痕迹",
                Level.RISK,
                "发现 magisk 挂载：${magisk.first().take(90)}"
            )

            overlay.isNotEmpty() || rwSystem.isNotEmpty() -> Item(
                "挂载痕迹",
                Level.NOTICE,
                "系统分区存在 overlay / 可写挂载（${(overlay + rwSystem).size} 条）"
            )

            else -> Item("挂载痕迹", Level.SAFE, "挂载表里没有 magisk / overlay 痕迹")
        }
    }

    /** 已安装的风险类应用（root 管理器、模块管理器、修改器、虚拟定位）。 */
    private fun checkRiskPackages(context: Context): Item {
        fun installed(map: Map<String, String>): List<String> = map.filter { (pkg, _) ->
            runCatching {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
        }.values.toList()

        val tools = installed(TOOL_PACKAGES)
        val cheats = installed(CHEAT_PACKAGES)
        return when {
            tools.isNotEmpty() -> Item(
                "风险类应用",
                Level.RISK,
                "已安装：${tools.joinToString("、")}" + if (cheats.isNotEmpty()) "；还有 ${cheats.joinToString("、")}" else ""
            )

            cheats.isNotEmpty() -> Item("风险类应用", Level.NOTICE, "已安装：${cheats.joinToString("、")}")
            else -> Item("风险类应用", Level.SAFE, "没有装常见的 root 管理器 / 修改器 / 虚拟定位")
        }
    }

    /** 无障碍服务：自动化脚本和不少恶意软件都靠它，开着就提醒一句。 */
    private fun checkAccessibilityServices(context: Context): Item = runCatching {
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val services = raw.split(':').filter { it.isNotBlank() }
        if (services.isEmpty()) {
            Item("无障碍服务", Level.SAFE, "没有开启任何无障碍服务")
        } else {
            Item(
                "无障碍服务",
                Level.NOTICE,
                "已开启 ${services.size} 个：${services.joinToString("、") { it.substringBefore('/') }.take(160)}"
            )
        }
    }.getOrElse { Item("无障碍服务", Level.NOTICE, "读取失败：${it.message}") }

    /** 设备管理员（部分企业管控 / 恶意软件会注册成设备管理员）。 */
    private fun checkDeviceAdmins(context: Context): Item = runCatching {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
            as? android.app.admin.DevicePolicyManager
        val admins = dpm?.activeAdmins.orEmpty()
        if (admins.isEmpty()) {
            Item("设备管理员", Level.SAFE, "没有应用注册成设备管理员")
        } else {
            Item(
                "设备管理员",
                Level.NOTICE,
                admins.joinToString("、") { it.packageName }
            )
        }
    }.getOrElse { Item("设备管理员", Level.NOTICE, "读取失败：${it.message}") }

    /** 全局代理 / VPN：抓包与"改环境"几乎都会先动这两样。 */
    private fun checkProxyAndVpn(context: Context): Item {
        val proxyHost = runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.HTTP_PROXY)
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val systemProxy = System.getProperty("http.proxyHost")?.takeIf { it.isNotBlank() }
        val vpn = runCatching {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()
                ?.map { it.name }
                ?.filter { name ->
                    name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("wg")
                }
                .orEmpty()
        }.getOrDefault(emptyList())
        val marks = buildList {
            proxyHost?.let { add("全局代理 $it") }
            systemProxy?.let { add("Java 代理 $it") }
            if (vpn.isNotEmpty()) add("虚拟网卡 ${vpn.joinToString("、")}")
        }
        return if (marks.isEmpty()) {
            Item("代理 / VPN", Level.SAFE, "没有设置全局代理，也没有虚拟网卡")
        } else {
            Item("代理 / VPN", Level.NOTICE, marks.joinToString("；") + "（抓包 / 改环境常见前提）")
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
