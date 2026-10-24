package com.rjy.xyz.apps.xyzinfo.data

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 折叠屏身份兜底：不能因为展开后最小宽度变大就显示成平板。 */
class DeviceFormDetectorTest {

    @Test
    fun `主流折叠屏市场名可以识别`() {
        listOf(
            "HONOR Magic V3",
            "Samsung Galaxy Z Fold6",
            "Xiaomi MIX Fold 4",
            "Xiaomi MIX Flip",
            "HUAWEI Mate X6",
            "HUAWEI Pocket 2",
            "OPPO Find N5",
            "vivo X Fold5",
            "Google Pixel 9 Pro Fold",
            "OnePlus Open",
            "motorola razr 50 ultra",
            "TECNO Phantom V Fold2"
        ).forEach { name ->
            assertTrue("$name 应该识别为折叠屏", DeviceFormDetector.looksLikeFoldable(name))
        }
    }

    @Test
    fun `普通手机与平板不会误判`() {
        listOf(
            "Xiaomi Civi 1S",
            "HONOR Magic6 Pro",
            "Samsung Galaxy S24 Ultra",
            "Xiaomi Pad 6 Pro",
            "Samsung Galaxy Tab S9",
            "Google Pixel Tablet",
            "OnePlus Pad 2"
        ).forEach { name ->
            assertFalse("$name 不应识别为折叠屏", DeviceFormDetector.looksLikeFoldable(name))
        }
    }

    @Test
    fun `OEM 折叠属性可以兜底`() {
        assertTrue(
            DeviceFormDetector.looksLikeFoldable(
                identity = "unknown-device",
                properties = mapOf("ro.build.characteristics" to "default,foldable")
            )
        )
        assertTrue(
            DeviceFormDetector.looksLikeFoldable(
                identity = "unknown-device",
                properties = mapOf("ro.product.is_foldable" to "true")
            )
        )
        assertFalse(
            DeviceFormDetector.looksLikeFoldable(
                identity = "unknown-device",
                properties = mapOf("ro.config.foldable" to "false")
            )
        )
    }

    @Test
    fun `车机手表电视优先于大屏平板判定`() {
        assertEquals(
            DeviceForm.CAR,
            DeviceFormDetector.classify(
                DeviceFormDetector.FormSignals(
                    uiModeType = Configuration.UI_MODE_TYPE_NORMAL,
                    hasAutomotiveFeature = true,
                    smallestWidthDp = 1100
                )
            )
        )
        assertEquals(
            DeviceForm.WATCH,
            DeviceFormDetector.classify(
                DeviceFormDetector.FormSignals(
                    uiModeType = Configuration.UI_MODE_TYPE_WATCH,
                    smallestWidthDp = 320
                )
            )
        )
        assertEquals(
            DeviceForm.TV,
            DeviceFormDetector.classify(
                DeviceFormDetector.FormSignals(
                    uiModeType = Configuration.UI_MODE_TYPE_NORMAL,
                    hasTvFeature = true,
                    smallestWidthDp = 960
                )
            )
        )
    }

    @Test
    fun `其他特殊模式与普通设备正确分类`() {
        assertEquals(
            DeviceForm.OTHER,
            DeviceFormDetector.classify(
                DeviceFormDetector.FormSignals(
                    uiModeType = Configuration.UI_MODE_TYPE_APPLIANCE,
                    smallestWidthDp = 320
                )
            )
        )
        assertEquals(
            DeviceForm.OTHER,
            DeviceFormDetector.classify(
                DeviceFormDetector.FormSignals(
                    uiModeType = Configuration.UI_MODE_TYPE_NORMAL,
                    hasOtherFeature = true,
                    smallestWidthDp = 1000
                )
            )
        )
        assertEquals(
            DeviceForm.TABLET,
            DeviceFormDetector.classify(
                DeviceFormDetector.FormSignals(
                    uiModeType = Configuration.UI_MODE_TYPE_NORMAL,
                    smallestWidthDp = 700
                )
            )
        )
        assertEquals(
            DeviceForm.PHONE,
            DeviceFormDetector.classify(
                DeviceFormDetector.FormSignals(
                    uiModeType = Configuration.UI_MODE_TYPE_NORMAL,
                    smallestWidthDp = 411
                )
            )
        )
    }
}
