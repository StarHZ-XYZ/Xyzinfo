package com.rjy.xyz.apps.xyzinfo.util

import android.os.Build
import java.util.Locale

/**
 * 对 [Build]、/proc/cpuinfo 等系统原始字段做统一整理。
 *
 * 这里只负责把系统返回值变成“更可读的原始值”，
 * 界面文案（含中文前后缀）由各页面自己拼接。
 */
object DeviceFacts {

    /** 设备品牌名在系统里常为全小写，这里统一成常见写法。 */
    private val brandDisplayNames = mapOf(
        "redmi" to "Redmi",
        "poco" to "POCO",
        "xiaomi" to "Xiaomi",
        "meizu" to "Meizu",
        "realme" to "realme",
        "oneplus" to "OnePlus",
        "huawei" to "HUAWEI",
        "honor" to "HONOR",
        "samsung" to "Samsung",
        "oppo" to "OPPO",
        "vivo" to "vivo"
    )

    /** 品牌 + 型号拼出的友好设备名，例如 “Xiaomi 15 Pro”。 */
    fun friendlyDeviceName(): String {
        val brand = orUnknown(Build.BRAND).trim()
        val manufacturer = orUnknown(Build.MANUFACTURER).trim()
        val model = orUnknown(Build.MODEL).trim()

        if (model.startsWith(brand, ignoreCase = true) ||
            model.startsWith(manufacturer, ignoreCase = true)
        ) {
            return beautifyDeviceName(model)
        }

        val mainBrand = brandDisplayNames[brand.lowercase(Locale.ROOT)]
            ?: when {
                manufacturer.isNotBlank() && manufacturer != Labels.UNKNOWN ->
                    manufacturer.capitalizeFirst()
                else -> brand.capitalizeFirst()
            }

        return beautifyDeviceName("$mainBrand $model")
    }

    /** 首选 ABI，例如 arm64-v8a。 */
    fun primaryAbi(): String? =
        Build.SUPPORTED_ABIS.firstOrNull()
            ?: Build.SUPPORTED_32_BIT_ABIS.firstOrNull()
            ?: Build.SUPPORTED_64_BIT_ABIS.firstOrNull()

    /** ABI 的中文说明，例如 “arm64-v8a (64位 ARM)”。 */
    fun abiLabel(abi: String?): String {
        if (abi.isNullOrBlank()) return Labels.UNKNOWN

        return when (abi.lowercase(Locale.ROOT)) {
            "arm64-v8a" -> "arm64-v8a (64位 ARM)"
            "armeabi-v7a" -> "armeabi-v7a (32位 ARM)"
            "armeabi" -> "armeabi (32位 ARM)"
            "x86_64" -> "x86_64 (64位 x86)"
            "x86" -> "x86 (32位 x86)"
            "riscv64" -> "riscv64 (64位 RISC-V)"
            else -> abi
        }
    }

    /** 由 /proc/cpuinfo 与 ABI 列表推断 CPU 架构名称。 */
    fun cpuArchitecture(cpuInfoRaw: String = ProcFs.readText(CPU_INFO_PATH)): String {
        val text = (cpuInfoRaw + " " + Build.SUPPORTED_ABIS.joinToString(" "))
            .lowercase(Locale.ROOT)

        return when {
            // 先判 ARMv9（明确写着 armv9 才认），否则按 ARMv8 处理
            "armv9" in text -> "AArch64 / ARMv9"
            "aarch64" in text || "arm64-v8a" in text || "armv8" in text -> "AArch64 / ARMv8-A"
            "armeabi-v7a" in text || "armv7" in text -> "ARMv7"
            "armv6" in text -> "ARMv6"
            "x86_64" in text -> "x86_64"
            "intel" in text -> "x86 / Intel"
            "amd" in text -> "x86 / AMD"
            "riscv" in text -> "RISC-V"
            Regex("""\bx86\b""").containsMatchIn(text) -> "x86"
            else -> Labels.UNKNOWN
        }
    }

    /** 内核版本号，例如 5.10.198-android13。 */
    fun kernelRelease(): String {
        val versionLine = ProcFs.readText("/proc/version")
        Regex("Linux version\\s+([^\\s]+)").find(versionLine)?.let { match ->
            return match.groupValues[1]
        }
        return orUnknown(System.getProperty("os.version"))
    }

    /** 内核完整版本行（/proc/version 原始内容）。 */
    fun kernelVersionLine(): String =
        ProcFs.readText("/proc/version").trim().ifBlank {
            orUnknown(System.getProperty("os.version"))
        }

    /** 空值统一显示为“未知”。 */
    fun orUnknown(value: String?): String =
        if (value.isNullOrBlank()) Labels.UNKNOWN else value

    private fun beautifyDeviceName(raw: String): String =
        raw.replace("_", " ").replace(Regex("\\s+"), " ").trim()

    private fun String.capitalizeFirst(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    const val CPU_INFO_PATH = "/proc/cpuinfo"
}
