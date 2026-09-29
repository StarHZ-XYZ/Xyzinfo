package com.rjy.xyz.apps.xyzinfo.model

enum class RootStatus {
    LIKELY_ROOTED,
    CLEAN
}

enum class TrebleStatus {
    CONFIG_PRESENT,
    LIKELY_SUPPORTED,
    UNKNOWN
}

/**
 * 系统版本、设备标识、存储与环境信息。
 */
data class SystemInfo(
    val androidRelease: String,
    val apiLevel: Int,
    val buildId: String,
    val securityPatch: String,
    val kernelVersionLine: String,
    val bootloader: String,
    val brand: String,
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val board: String,
    val hardware: String,
    val fingerprint: String,
    val abiList: String,
    val languageTag: String,
    val timeZoneId: String,
    val timestampMillis: Long,
    val ramTotalBytes: Long?,
    val ramAvailableBytes: Long?,
    val systemPartitionTotalBytes: Long?,
    val systemPartitionAvailableBytes: Long?,
    val dataPartitionTotalBytes: Long?,
    val dataPartitionAvailableBytes: Long?,
    val rootStatus: RootStatus,
    val trebleStatus: TrebleStatus,
    val rawPreview: String
)
