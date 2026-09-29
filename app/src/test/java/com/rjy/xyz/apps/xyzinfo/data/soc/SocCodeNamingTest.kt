package com.rjy.xyz.apps.xyzinfo.data.soc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 未收录芯片的「系列级」兜底命名测试。 */
class SocCodeNamingTest {

    @Test
    fun `骁龙按系列兜底`() {
        assertEquals("骁龙 8 系（SM8999）", SocCodeNaming.friendlyName("sm8999"))
        assertEquals("骁龙 7 系（SM7650）", SocCodeNaming.friendlyName("SM7650"))
        assertEquals("骁龙平台（SDM765）", SocCodeNaming.friendlyName("SDM765"))
    }

    @Test
    fun `其它平台兜底`() {
        assertEquals("天玑 / Helio 平台（MT9999）", SocCodeNaming.friendlyName("MT9999"))
        assertEquals("Exynos 平台（S5E9999）", SocCodeNaming.friendlyName("S5E9999"))
        assertEquals("麒麟平台（HI3999）", SocCodeNaming.friendlyName("HI3999"))
        assertEquals("紫光展锐平台（UMS9999）", SocCodeNaming.friendlyName("UMS9999"))
    }

    @Test
    fun `无法判断时返回 null`() {
        assertNull(SocCodeNaming.friendlyName(""))
        assertNull(SocCodeNaming.friendlyName("UNKNOWNCHIP"))
    }
}
