package com.rjy.xyz.apps.xyzinfo.data

import android.os.Build
import com.rjy.xyz.apps.xyzinfo.model.RomInfo
import com.rjy.xyz.apps.xyzinfo.util.SystemPropertiesReader

/**
 * 识别当前系统 UI / ROM 类型：澎湃OS、MIUI、ColorOS、realme UI、OxygenOS、
 * OriginOS / Funtouch、One UI、鸿蒙 / EMUI、Flyme、MagicOS、类原生等。
 *
 * 判定全部基于系统属性，逻辑写成了纯函数 [identify]，方便单元测试。
 */
object RomInfoProvider {

    /** 澎湃OS 起始的 MIUI 版本号：V816（即 HyperOS 1.0）起不再是 MIUI。 */
    private const val HYPER_OS_MIN_MIUI_CODE = 816

    fun load(): RomInfo = identify(SystemPropertiesReader.readAll())

    fun identify(
        properties: Map<String, String>,
        brand: String = properties["ro.product.brand"] ?: Build.BRAND.orEmpty(),
        manufacturer: String = properties["ro.product.manufacturer"] ?: Build.MANUFACTURER.orEmpty()
    ): RomInfo {
        val detected = detectXiaomi(properties)
            ?: detectOppoFamily(properties, brand)
            ?: detectVivo(properties)
            ?: detectSamsung(properties, brand)
            ?: detectHuawei(properties)
            ?: detectHonor(properties, brand)
            ?: detectMeizu(properties)
            ?: detectOthers(properties, brand, manufacturer)
            ?: detectAospLike(properties, brand)

        return RomInfo(
            name = detected.name,
            version = detected.version,
            source = detected.source,
            displayId = properties["ro.build.display.id"],
            buildType = properties["ro.build.type"]
        )
    }

    /** 小米：澎湃OS（HyperOS）与 MIUI 用版本号区分。 */
    private fun detectXiaomi(properties: Map<String, String>): Detected? {
        properties.value("ro.mi.os.version.name")?.let {
            return Detected("澎湃OS（HyperOS）", it, "ro.mi.os.version.name=$it")
        }

        val miuiVersion = properties.value("ro.miui.ui.version.name") ?: return null
        val versionCode = properties["ro.miui.ui.version.code"]?.toIntOrNull()
            ?: miuiVersion.filter { it.isDigit() }.toIntOrNull()

        return if ((versionCode ?: 0) >= HYPER_OS_MIN_MIUI_CODE) {
            Detected(
                name = "澎湃OS（HyperOS）",
                version = hyperOsVersionLabel(properties, miuiVersion),
                source = "ro.miui.ui.version.name=$miuiVersion（≥V816 即澎湃OS）"
            )
        } else {
            Detected("MIUI", miuiVersion, "ro.miui.ui.version.name=$miuiVersion")
        }
    }

    /** 澎湃OS 的版本号优先取独立字段，取不到就用 MIUI 版本号（如 V816）。 */
    private fun hyperOsVersionLabel(properties: Map<String, String>, miuiVersion: String): String {
        val incremental = properties.value("ro.build.version.incremental")
        if (incremental != null && incremental.startsWith("OS", ignoreCase = true)) return incremental
        return miuiVersion
    }

    /** OPPO 系：ColorOS / realme UI / OxygenOS 共用一套属性，用品牌与版本串区分。 */
    private fun detectOppoFamily(properties: Map<String, String>, brand: String): Detected? {
        val key = OPPO_ROM_KEYS.firstOrNull { properties.value(it) != null } ?: return null
        val value = properties.getValue(key)
        val lowerValue = value.lowercase()
        val lowerBrand = brand.lowercase()

        val name = when {
            "oxygen" in lowerValue || properties.value("ro.build.version.oxygen") != null -> "OxygenOS"
            "realme" in lowerValue || lowerBrand.contains("realme") -> "realme UI"
            lowerBrand.contains("oneplus") -> if ("color" in lowerValue) "ColorOS" else "OxygenOS"
            else -> "ColorOS"
        }
        return Detected(name, value, "$key=$value")
    }

    /** vivo：OriginOS / Funtouch OS。 */
    private fun detectVivo(properties: Map<String, String>): Detected? {
        val osName = properties.value("ro.vivo.os.name")
        val osVersion = properties.value("ro.vivo.os.version")
        val originVersion = properties.value("ro.vivo.originos.version")
        val romVersion = properties.value("ro.vivo.rom.version")
        if (osName == null && osVersion == null && originVersion == null && romVersion == null) return null

        val name = when {
            osName?.contains("origin", ignoreCase = true) == true || originVersion != null -> "OriginOS"
            osName?.contains("funtouch", ignoreCase = true) == true -> "Funtouch OS"
            else -> "Funtouch OS / OriginOS"
        }
        return Detected(name, osVersion ?: originVersion ?: romVersion, "ro.vivo.os.name=${osName ?: "-"}")
    }

    /** 三星：One UI，版本号形如 60100 → 6.1。 */
    private fun detectSamsung(properties: Map<String, String>, brand: String): Detected? {
        val oneUiCode = properties["ro.build.version.oneui"]?.toIntOrNull()
        if (oneUiCode == null && !brand.contains("samsung", ignoreCase = true)) return null

        val version = oneUiCode?.let { formatOneUi(it) } ?: properties.value("ro.build.version.release")
        val source = oneUiCode?.let { "ro.build.version.oneui=$it" } ?: "ro.product.brand=samsung"
        return Detected("One UI", version, source)
    }

    private fun formatOneUi(code: Int): String {
        val major = code / 10000
        val minor = (code / 100) % 100
        return if (major > 0) "One UI $major.$minor" else "One UI"
    }

    /** 华为：鸿蒙 HarmonyOS / EMUI。 */
    private fun detectHuawei(properties: Map<String, String>): Detected? {
        val harmonyKey = HARMONY_KEYS.firstOrNull { properties.value(it) != null }
        if (harmonyKey != null) {
            val value = properties.getValue(harmonyKey)
            return Detected("HarmonyOS（鸿蒙）", value, "$harmonyKey=$value")
        }

        val emui = properties.value("ro.build.version.emui") ?: return null
        return Detected("EMUI", emui.removePrefix("EmotionUI_"), "ro.build.version.emui=$emui")
    }

    /** 荣耀：MagicOS。 */
    private fun detectHonor(properties: Map<String, String>, brand: String): Detected? {
        val key = MAGIC_KEYS.firstOrNull { properties.value(it) != null }
        if (key == null && !brand.contains("honor", ignoreCase = true)) return null

        val value = key?.let { properties.getValue(it) }
        return Detected("MagicOS", value, key?.let { "$it=$value" } ?: "ro.product.brand=honor")
    }

    /** 魅族：Flyme / Flyme AIOS。 */
    private fun detectMeizu(properties: Map<String, String>): Detected? {
        val key = FLYME_KEYS.firstOrNull { properties.value(it) != null }
        val displayId = properties.value("ro.build.display.id")
        val displayLooksFlyme = displayId?.contains("flyme", ignoreCase = true) == true
        if (key == null && !displayLooksFlyme) return null

        val isAios = properties.value("ro.flyme.aios.version") != null ||
            displayId?.contains("aios", ignoreCase = true) == true
        val version = key?.let { properties.getValue(it) } ?: displayId
        return Detected(
            name = if (isAios) "Flyme AIOS" else "Flyme",
            version = version,
            source = key?.let { "$it=$version" } ?: "ro.build.display.id=$displayId"
        )
    }

    /** 其它厂商系统：中兴 MyOS、联想 ZUI、摩托罗拉的 My UX、Nothing OS、索尼、华硕等。 */
    private fun detectOthers(
        properties: Map<String, String>,
        brand: String,
        manufacturer: String
    ): Detected? = when {
        properties.value("ro.build.MiFavor") != null || brand.contains("ZTE", true) ->
            Detected("MyOS", properties.value("ro.build.MiFavor"), "ro.build.MiFavor")

        properties.value("ro.zui.version") != null || brand.contains("Lenovo", true) ->
            Detected("ZUI", properties.value("ro.zui.version"), "ro.zui.version")

        properties.value("ro.mot.build.version") != null || brand.contains("motorola", true) ->
            Detected("My UX", properties.value("ro.mot.build.version"), "ro.mot.build.version")

        properties.value("ro.nothing.build.version") != null || brand.contains("Nothing", true) ->
            Detected("Nothing OS", properties.value("ro.nothing.build.version"), "ro.nothing.build.version")

        properties.value("ro.semc.version.sw") != null || brand.contains("Sony", true) ->
            Detected("Xperia 系统", properties.value("ro.semc.version.sw"), "ro.semc.version.sw")

        brand.contains("asus", true) || manufacturer.contains("asus", true) ->
            Detected("ZenUI / ROG UI", properties.value("ro.build.display.id"), "ro.product.brand=asus")

        else -> null
    }

    /** 类原生 / 第三方 ROM：LineageOS、crDroid、Evolution X、Pixel 原生等。 */
    private fun detectAospLike(properties: Map<String, String>, brand: String): Detected {
        properties.value("ro.lineage.version")?.let {
            return Detected("LineageOS", it, "ro.lineage.version=$it")
        }
        properties.value("ro.cm.version")?.let {
            return Detected("LineageOS（CM 系）", it, "ro.cm.version=$it")
        }
        properties.value("ro.crdroid.version")?.let {
            return Detected("crDroid", it, "ro.crdroid.version=$it")
        }
        properties.value("ro.evolution.version")?.let {
            return Detected("Evolution X", it, "ro.evolution.version=$it")
        }
        properties.value("ro.modversion")?.let {
            return Detected("第三方 ROM", it, "ro.modversion=$it")
        }

        if (brand.contains("google", ignoreCase = true)) {
            return Detected("Pixel 原生系统", properties.value("ro.build.id"), "ro.product.brand=google")
        }

        val flavor = properties.value("ro.build.flavor")
        if (flavor?.contains("aosp", ignoreCase = true) == true) {
            return Detected("类原生（AOSP）", flavor, "ro.build.flavor=$flavor")
        }

        return Detected(
            name = "类原生 / 未识别厂商系统",
            version = null,
            source = "未匹配到厂商系统特征属性"
        )
    }

    private fun Map<String, String>.value(key: String): String? =
        this[key]?.takeIf { it.isNotBlank() }

    private data class Detected(val name: String, val version: String?, val source: String?)

    private val OPPO_ROM_KEYS = listOf(
        "ro.build.version.oplusrom",
        "ro.build.version.oplusrom.conf",
        "ro.build.version.opporom",
        "ro.oplus.version",
        "ro.build.version.realmeui",
        "ro.build.version.oxygen",
        "ro.oxygen.version"
    )

    private val HARMONY_KEYS = listOf(
        "ro.build.version.harmony",
        "ro.build.version.harmonyos",
        "const.product.software.version",
        "hw_sc.build.platform.version"
    )

    private val MAGIC_KEYS = listOf(
        "ro.build.version.magic",
        "ro.build.version.magicui",
        "ro.build.version.magicui.version"
    )

    private val FLYME_KEYS = listOf(
        "ro.build.version.flyme",
        "ro.flyme.version",
        "ro.flyme.os.version",
        "ro.flyme.aios.version"
    )
}
