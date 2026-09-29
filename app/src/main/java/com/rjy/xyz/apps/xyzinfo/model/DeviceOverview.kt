package com.rjy.xyz.apps.xyzinfo.model

/**
 * 首页“当前设备”卡片展示的设备概要信息。
 */
data class DeviceOverview(
    val displayName: String,
    val androidRelease: String,
    val apiLevel: Int,
    val kernelRelease: String,
    val brand: String,
    val manufacturer: String,
    val deviceCode: String,
    val product: String,
    val abiLabel: String,
    val cpuArchitecture: String
)
