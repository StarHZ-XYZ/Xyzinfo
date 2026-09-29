package com.rjy.xyz.apps.xyzinfo.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 芯片品牌归类测试。
 */
class SocBrandTest {

    @Test
    fun `规格库徽标名对应到正确品牌`() {
        assertEquals(SocBrand.SNAPDRAGON, SocBrand.ofBadge("骁龙"))
        assertEquals(SocBrand.MEDIATEK, SocBrand.ofBadge("联发科"))
        assertEquals(SocBrand.EXYNOS, SocBrand.ofBadge("猎户座"))
        assertEquals(SocBrand.KIRIN, SocBrand.ofBadge("麒麟"))
        assertEquals(SocBrand.UNISOC, SocBrand.ofBadge("展锐"))
        assertEquals(SocBrand.TENSOR, SocBrand.ofBadge("谷歌"))
        assertEquals(SocBrand.XRING, SocBrand.ofBadge("玄戒"))
        assertEquals(SocBrand.UNKNOWN, SocBrand.ofBadge("芯片"))
    }

    @Test
    fun `厂商名兜底也能识别品牌`() {
        assertEquals(SocBrand.SNAPDRAGON, SocBrand.ofBrandName("高通骁龙"))
        assertEquals(SocBrand.TENSOR, SocBrand.ofBrandName("Google Tensor"))
        assertEquals(SocBrand.XRING, SocBrand.ofBrandName("小米玄戒"))
        assertEquals(SocBrand.UNKNOWN, SocBrand.ofBrandName("未知平台"))
    }
}
