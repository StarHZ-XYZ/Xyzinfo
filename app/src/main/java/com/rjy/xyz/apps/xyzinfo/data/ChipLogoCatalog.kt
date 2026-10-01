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

    /**
     * 按 logo 自己的比例给"宽槽位"的 drawable（没有内置 logo 时返回 null）。
     *
     * 芯片厂的 logo 也大多是宽字标（联发科接近 4:1），塞进正方框会压成一条细线；
     * 这里按高度 [heightPx] 反推宽度，最多放宽到 3 倍，字标就能铺开。
     */
    fun wideDrawable(context: android.content.Context, brand: SocBrand, heightPx: Int): android.graphics.drawable.Drawable? {
        val res = logoOf(brand)
        if (res == 0) return null
        val logo = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, res) ?: return null
        val aspect = if (logo.intrinsicHeight > 0) {
            logo.intrinsicWidth.toFloat() / logo.intrinsicHeight
        } else {
            1f
        }
        val width = (heightPx * aspect.coerceIn(0.6f, 3.0f)).toInt().coerceAtLeast(heightPx)
        logo.setBounds(0, 0, width, heightPx)
        return logo
    }
}
