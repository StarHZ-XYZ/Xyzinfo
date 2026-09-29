package com.rjy.xyz.apps.xyzinfo.model

/**
 * 芯片品牌，界面据此显示品牌图标与配色。
 */
enum class SocBrand {
    SNAPDRAGON,
    MEDIATEK,
    EXYNOS,
    KIRIN,
    UNISOC,
    TENSOR,
    XRING,
    UNKNOWN;

    companion object {

        /** 依据规格库里的徽标名归类。 */
        fun ofBadge(badgeText: String): SocBrand = when (badgeText) {
            "骁龙" -> SNAPDRAGON
            "联发科" -> MEDIATEK
            "猎户座" -> EXYNOS
            "麒麟" -> KIRIN
            "展锐" -> UNISOC
            "谷歌" -> TENSOR
            "玄戒" -> XRING
            else -> UNKNOWN
        }

        /** 规格库没匹配上时，用厂商名兜底判断品牌。 */
        fun ofBrandName(brandName: String): SocBrand = when {
            "骁龙" in brandName || "高通" in brandName -> SNAPDRAGON
            "联发科" in brandName || "MediaTek" in brandName -> MEDIATEK
            "Exynos" in brandName || "三星" in brandName -> EXYNOS
            "麒麟" in brandName || "华为" in brandName -> KIRIN
            "展锐" in brandName || "展讯" in brandName -> UNISOC
            "Tensor" in brandName || "谷歌" in brandName -> TENSOR
            "玄戒" in brandName -> XRING
            else -> UNKNOWN
        }
    }
}
