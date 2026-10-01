package com.rjy.xyz.apps.xyzinfo.data

import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.model.SocBrand

/**
 * 芯片厂商 logo（1.0.4 起的「超级 logo 包」）。
 *
 * 和 [BrandLogoCatalog] 一样：这些都是把用户提供的原图**矢量化**得到的 VectorDrawable，
 * 不是位图，放大到任何尺寸都不糊。型号里认不出厂商时返回 0，由界面退回原来的通用图标。
 */
object ChipLogoCatalog {

    /** 芯片品牌 → logo 资源。 */
    fun logoOf(brand: SocBrand): Int = when (brand) {
        SocBrand.SNAPDRAGON -> R.drawable.ic_logo_snapdragon
        SocBrand.MEDIATEK -> R.drawable.ic_logo_mediatek
        SocBrand.KIRIN -> R.drawable.ic_logo_kirin
        SocBrand.UNISOC -> R.drawable.ic_logo_unisoc
        SocBrand.TENSOR -> R.drawable.ic_logo_google
        SocBrand.EXYNOS -> R.drawable.ic_logo_samsung
        SocBrand.XRING -> 0
        SocBrand.UNKNOWN -> 0
    }

    /** 芯片品牌中文名（图鉴页用）。 */
    fun displayName(brand: SocBrand): String = when (brand) {
        SocBrand.SNAPDRAGON -> "骁龙 Qualcomm"
        SocBrand.MEDIATEK -> "联发科 MediaTek"
        SocBrand.KIRIN -> "麒麟 Kirin"
        SocBrand.UNISOC -> "紫光展锐 Unisoc"
        SocBrand.TENSOR -> "谷歌 Tensor"
        SocBrand.EXYNOS -> "三星 Exynos"
        SocBrand.XRING -> "小米玄戒"
        SocBrand.UNKNOWN -> "未知"
    }
}
