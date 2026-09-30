package com.rjy.xyz.apps.xyzinfo.ui.gps

import android.content.Context

/**
 * 收音机（FM）能力探测。
 *
 * 现状要说清楚：Android 没有给第三方 App 开放调频收音机的公开 API，
 * 调谐器通常由厂商自己的收音机 App（走私有 HAL）独占。所以这里能做的是：
 * 1. 查系统是否真的声明了 `android.hardware.fmradio` 硬件特性；
 * 2. 扫一遍常见厂商的收音机应用，找得到就直接拉起来，让用户能听。
 */
object RadioProbe {

    data class FmApp(val label: String, val packageName: String)

    data class Result(
        val hardwareSupported: Boolean,
        val apps: List<FmApp>
    ) {
        val available: Boolean get() = apps.isNotEmpty()
    }

    /** 常见厂商收音机包名（覆盖小米 / 华为 / 三星 / 高通 / 联发科 / OPPO / vivo / 中兴等）。 */
    private val CANDIDATES = listOf(
        "com.android.fmradio" to "系统收音机",
        "com.miui.fm" to "小米收音机",
        "com.miui.fmradio" to "小米收音机",
        "com.huawei.android.FMRadio" to "华为收音机",
        "com.sec.android.app.fm" to "三星收音机",
        "com.samsung.android.fmradio" to "三星收音机",
        "com.caf.fmradio" to "高通收音机",
        "com.mediatek.FMRadio" to "联发科收音机",
        "com.mediatek.fmradio" to "联发科收音机",
        "com.coloros.fmradio" to "OPPO 收音机",
        "com.oplus.fmradio" to "OPPO 收音机",
        "com.vivo.fm" to "vivo 收音机",
        "com.zte.fmradio" to "中兴收音机",
        "com.tcl.fmradio" to "TCL 收音机",
        "com.android.fm" to "收音机",
        "com.android.fmradio.anrd" to "收音机"
    )

    fun probe(context: Context): Result {
        val packageManager = context.packageManager
        val hardware = runCatching {
            packageManager.hasSystemFeature(FEATURE_FM_RADIO)
        }.getOrDefault(false)
        val apps = CANDIDATES.mapNotNull { (packageName, label) ->
            val intent = runCatching { packageManager.getLaunchIntentForPackage(packageName) }.getOrNull()
            if (intent != null) FmApp(label, packageName) else null
        }.distinctBy { it.packageName }
        return Result(hardware, apps)
    }

    /**
     * 用字符串而不是 PackageManager.FEATURE_FM_RADIO 常量：
     * 这个特性名在不同 API 上并非都有公开常量，用字符串最稳。
     */
    private const val FEATURE_FM_RADIO = "android.hardware.fmradio"
}
