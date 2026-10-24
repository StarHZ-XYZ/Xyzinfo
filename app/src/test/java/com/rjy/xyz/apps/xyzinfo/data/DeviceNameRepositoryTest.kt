package com.rjy.xyz.apps.xyzinfo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 荣耀 / iQOO 等子品牌的型号映射与显示整理。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceNameRepositoryTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `荣耀内部型号能显示市场名`() {
        assertEquals(
            "荣耀 200",
            DeviceNameRepository.lookup(context, "hneli", "ELI-AN00", "hneli")
        )
        assertEquals(
            "荣耀 200",
            DeviceNameRepository.lookup(context, null, "HONOR ELI-AN00")
        )
    }

    @Test
    fun `iqoo 不再把 vivo 当成展示品牌`() {
        val name = DeviceNameRepository.lookup(context, "pd2408", "V2408A", "pd2408")
        assertEquals("iQOO 13", name)
    }

    @Test
    fun `旧荣耀不会被华为前缀抢走`() {
        val name = DeviceNameRepository.lookup(context, "hwdua-m", "DUA-TL00")
        assertTrue("实际显示：$name", name?.startsWith("荣耀") == true)
        assertFalse("实际显示：$name", name?.contains("Honor", ignoreCase = true) == true)
        assertFalse("实际显示：$name", name?.startsWith("华为") == true)
    }

    @Test
    fun `市场名整理支持中文和平板写法`() {
        assertEquals(
            "荣耀X80 Pro Max",
            DeviceNameRepository.localizeMarketName("Honor 荣耀X80 Pro Max")
        )
        assertEquals(
            "iQOO Neo11 至尊版",
            DeviceNameRepository.localizeMarketName("Vivo iQOO Neo11 至尊版")
        )
    }

    @Test
    fun `设备代号更完整时不能退回短型号名`() {
        assertEquals(
            "小米 Civi 1S",
            DeviceNameRepository.lookup(context, "zijin", "2109119BC", "miku")
        )
    }
}
