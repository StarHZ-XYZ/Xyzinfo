package com.rjy.xyz.apps.xyzinfo.data.soc

/**
 * 单个 SoC 的规格描述。
 */
data class SocSpec(
    val displayName: String,
    val brandName: String,
    val badgeText: String,
    val performanceLevel: String = "中端",
    val aliases: List<String>,
    val deviceKeywords: List<String> = emptyList(),
    val cpuClusters: String = "未知",
    val cpuArchitecture: String = "未知",
    val gpuName: String = "未知",
    val gpuCores: String = "未公开",
    val gpuMinFreqMHz: Int? = null,
    val gpuMaxFreqMHz: Int? = null,
    val graphicsApi: String = "未知"
)
