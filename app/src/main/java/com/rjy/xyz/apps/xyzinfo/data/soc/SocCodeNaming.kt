package com.rjy.xyz.apps.xyzinfo.data.soc

/**
 * 规格库没收录某颗芯片时，至少按型号代号给出「系列级」的友好名称。
 *
 * 例如 SM8650 会显示为「骁龙 8 系（SM8650）」，而不是孤零零一行代号。
 */
object SocCodeNaming {

    fun friendlyName(rawCode: String): String? {
        val code = rawCode.uppercase().trim()
        if (code.isEmpty()) return null

        return when {
            code.startsWith("SM8") -> "骁龙 8 系（$code）"
            code.startsWith("SM7") -> "骁龙 7 系（$code）"
            code.startsWith("SM6") -> "骁龙 6 系（$code）"
            code.startsWith("SM4") -> "骁龙 4 系（$code）"
            code.startsWith("SM2") -> "骁龙 2 系（$code）"
            code.startsWith("SDM") || code.startsWith("MSM") || code.startsWith("APQ") ->
                "骁龙平台（$code）"
            code.startsWith("MT") -> "天玑 / Helio 平台（$code）"
            code.startsWith("S5E") -> "Exynos 平台（$code）"
            code.startsWith("HI") && code.length >= 5 -> "麒麟平台（$code）"
            code.startsWith("UMS") || code.startsWith("SC") -> "紫光展锐平台（$code）"
            code.startsWith("GS") -> "Google Tensor 平台（$code）"
            else -> null
        }
    }
}
