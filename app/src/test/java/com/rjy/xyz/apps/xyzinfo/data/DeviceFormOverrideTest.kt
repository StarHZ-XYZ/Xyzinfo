package com.rjy.xyz.apps.xyzinfo.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 手动形态校正必须覆盖自动识别，保证未公开折叠硬件也能正确显示。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceFormOverrideTest {

    @Test
    fun `手动折叠屏校正覆盖自动识别`() {
        val context = RuntimeEnvironment.getApplication()
        try {
            SettingsRepository.setDeviceFormOverride(context, "foldable")
            assertEquals(DeviceForm.FOLDABLE, DeviceFormDetector.detect(context).form)
        } finally {
            SettingsRepository.setDeviceFormOverride(context, "auto")
        }
    }
}
