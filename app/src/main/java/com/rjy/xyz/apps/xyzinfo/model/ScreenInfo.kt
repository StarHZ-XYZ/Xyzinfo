package com.rjy.xyz.apps.xyzinfo.model

/**
 * 屏幕分辨、密度、刷新率与触控相关信息。
 */
data class ScreenInfo(
    val widthPx: Int,
    val heightPx: Int,
    val xdpi: Double,
    val ydpi: Double,
    val diagonalInches: Double?,
    val densityDpi: Int,
    val density: Float,
    val densityBucket: String,
    val landscape: Boolean,
    val currentRefreshRateHz: Float,
    /** 系统支持的刷新率档位，读不到时为空列表。 */
    val supportedRefreshRatesHz: List<Float>,
    /** 触控点数下限，系统未公开准确上限时使用 10 作为已知下限；无法判断时为 null。 */
    val touchPointsHint: Int?,
    val touchSampleRateHz: Int?,
    /** 是否支持 HDR（系统版本过低时不可判断）。 */
    val hdrDetectable: Boolean,
    val hdrSupportedTypes: List<String>,
    val hdrMaxLuminance: Float,
    val hdrMinLuminance: Float,
    /** 广色域支持情况，null 表示系统版本过低。 */
    val wideColorGamut: Boolean?,
    val maxBrightnessNode: String?,
    val rawPreview: String
)
