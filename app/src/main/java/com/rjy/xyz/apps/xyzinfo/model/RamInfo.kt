package com.rjy.xyz.apps.xyzinfo.model

/**
 * 运行内存与交换分区信息。
 */
data class RamInfo(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long,
    val usagePercent: Int,
    val lowMemory: Boolean,
    val thresholdBytes: Long,
    val javaHeapMaxBytes: Long,
    val nativeHeapTotalBytes: Long,
    val nativeHeapAllocatedBytes: Long,
    val swapTotalBytes: Long?,
    val swapUsedBytes: Long?,
    val typeName: String,
    /** 系统未公开内存品牌时为 null。 */
    val brandName: String?,
    val nominalFrequencyMHz: Int?,
    val currentFrequencyMHz: Int?,
    val memInfoPreview: String
)
