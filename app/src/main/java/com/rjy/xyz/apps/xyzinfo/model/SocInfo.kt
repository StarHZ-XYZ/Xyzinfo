package com.rjy.xyz.apps.xyzinfo.model

/**
 * SoC（处理器 / GPU）相关信息。
 *
 * 除原始节点预览外，其余字段都是结构化数据，展示文案在界面层生成。
 */
data class SocInfo(
    val displayName: String,
    val brandName: String,
    val badge: String,
    val performanceLevel: String,
    val cpuArchitecture: String,
    val abiList: String,
    val coreCount: Int,
    val clusters: String,
    val manufacturer: String,
    val modelCode: String,
    val hardware: String,
    val gpuName: String,
    val gpuCores: String,
    val gpuMinFreqMHz: Int?,
    val gpuMaxFreqMHz: Int?,
    val gpuMaxFreqFromProfileMHz: Int?,
    val graphicsApiFromProfile: String,
    val glEsVersion: String,
    val vulkanSupported: Boolean,
    /** 每个核心的最大频率（kHz），读不到的核为 null。 */
    val perCoreMaxFreqKHz: List<Int?>,
    val cpuInfoPreview: String
)
