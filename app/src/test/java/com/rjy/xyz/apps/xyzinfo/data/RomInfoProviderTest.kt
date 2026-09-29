package com.rjy.xyz.apps.xyzinfo.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 系统 UI / ROM 识别规则测试。 */
class RomInfoProviderTest {

    @Test
    fun `小米 V816 起判定为澎湃OS`() {
        val rom = RomInfoProvider.identify(
            mapOf(
                "ro.miui.ui.version.name" to "V816",
                "ro.miui.ui.version.code" to "816",
                "ro.product.brand" to "Xiaomi"
            ),
            brand = "Xiaomi"
        )
        assertEquals("澎湃OS（HyperOS）", rom.name)
        assertEquals("V816", rom.version)
    }

    @Test
    fun `MIUI 14 仍然判定为 MIUI`() {
        val rom = RomInfoProvider.identify(
            mapOf(
                "ro.miui.ui.version.name" to "V14.0.5",
                "ro.miui.ui.version.code" to "14",
                "ro.product.brand" to "Xiaomi"
            ),
            brand = "Xiaomi"
        )
        assertEquals("MIUI", rom.name)
        assertEquals("V14.0.5", rom.version)
    }

    @Test
    fun `澎湃OS 独立属性优先`() {
        val rom = RomInfoProvider.identify(
            mapOf(
                "ro.mi.os.version.name" to "OS2.0.1.0",
                "ro.miui.ui.version.name" to "V816",
                "ro.product.brand" to "Xiaomi"
            ),
            brand = "Xiaomi"
        )
        assertEquals("澎湃OS（HyperOS）", rom.name)
        assertEquals("OS2.0.1.0", rom.version)
    }

    @Test
    fun `OPPO 系按品牌与版本串区分`() {
        assertEquals(
            "ColorOS",
            RomInfoProvider.identify(
                mapOf("ro.build.version.oplusrom" to "ColorOS 14.0.1"),
                brand = "OPPO"
            ).name
        )
        assertEquals(
            "realme UI",
            RomInfoProvider.identify(
                mapOf("ro.build.version.oplusrom" to "ColorOS 13.0"),
                brand = "realme"
            ).name
        )
        assertEquals(
            "OxygenOS",
            RomInfoProvider.identify(
                mapOf("ro.build.version.oplusrom" to "OxygenOS 14.0.0"),
                brand = "OnePlus"
            ).name
        )
    }

    @Test
    fun `vivo 区分 OriginOS 与 Funtouch`() {
        assertEquals(
            "OriginOS",
            RomInfoProvider.identify(
                mapOf("ro.vivo.os.name" to "OriginOS", "ro.vivo.os.version" to "12.0"),
                brand = "vivo"
            ).name
        )
        assertEquals(
            "Funtouch OS",
            RomInfoProvider.identify(
                mapOf("ro.vivo.os.name" to "Funtouch", "ro.vivo.os.version" to "11.1"),
                brand = "vivo"
            ).name
        )
    }

    @Test
    fun `三星 One UI 版本号换算`() {
        val rom = RomInfoProvider.identify(
            mapOf("ro.build.version.oneui" to "60100"),
            brand = "samsung"
        )
        assertEquals("One UI", rom.name)
        assertEquals("One UI 6.1", rom.version)
    }

    @Test
    fun `华为鸿蒙与 EMUI`() {
        assertEquals(
            "HarmonyOS（鸿蒙）",
            RomInfoProvider.identify(
                mapOf("const.product.software.version" to "5.0.0.112"),
                brand = "HUAWEI"
            ).name
        )
        assertEquals(
            "EMUI",
            RomInfoProvider.identify(
                mapOf("ro.build.version.emui" to "EmotionUI_12.0.0"),
                brand = "HUAWEI"
            ).name
        )
    }

    @Test
    fun `魅族 Flyme 与 Flyme AIOS`() {
        assertEquals(
            "Flyme",
            RomInfoProvider.identify(
                mapOf("ro.build.version.flyme" to "Flyme 10.5.0"),
                brand = "Meizu"
            ).name
        )
        assertEquals(
            "Flyme AIOS",
            RomInfoProvider.identify(
                mapOf("ro.flyme.aios.version" to "1.0.0"),
                brand = "Meizu"
            ).name
        )
    }

    @Test
    fun `荣耀 MagicOS`() {
        assertEquals(
            "MagicOS",
            RomInfoProvider.identify(mapOf("ro.build.version.magic" to "8.0.0"), brand = "HONOR").name
        )
    }

    @Test
    fun `类原生与第三方 ROM`() {
        assertEquals(
            "LineageOS",
            RomInfoProvider.identify(mapOf("ro.lineage.version" to "21.0"), brand = "Xiaomi").name
        )
        assertEquals(
            "Pixel 原生系统",
            RomInfoProvider.identify(emptyMap(), brand = "google").name
        )
        assertEquals(
            "类原生 / 未识别厂商系统",
            RomInfoProvider.identify(emptyMap(), brand = "Xiaomi", manufacturer = "Xiaomi").name
        )
    }
}
