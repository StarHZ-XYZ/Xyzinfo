package com.rjy.xyz.apps.xyzinfo.model

/**
 * 首页“当前设备”卡片展示的设备概要信息。
 */
data class DeviceOverview(
    val displayName: String,
    /** 系统原始型号字符串（Build.MODEL），小字展示。 */
    val rawModel: String,
    val androidRelease: String,
    val apiLevel: Int,
    /** 系统 UI 名称，例如「澎湃OS（HyperOS） V816」。 */
    val romName: String?,
    val kernelRelease: String,
    val brand: String,
    val manufacturer: String,
    val deviceCode: String,
    val product: String,
    val abiLabel: String,
    val cpuArchitecture: String
)
