package com.rjy.xyz.apps.xyzinfo.model

/**
 * 运行内存与交换分区信息。
 */
data class RamInfo(
    /** 系统实际可用总量（/proc/meminfo 的 MemTotal）。 */
    val measuredTotalBytes: Long,
    /** 由实测值推断出的标称容量（例如 12GB），无法判断时为 null。 */
    val nominalTotalGigabytes: Int?,
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
    val currentFrequencyMHz: Int?,
    val minFrequencyMHz: Int?,
    val maxFrequencyMHz: Int?,
    /** 频率读自哪个系统节点，便于机型适配排查。 */
    val frequencySource: String?,
    /** 读不到频率时的原因说明（例如找到了节点但无权限）。 */
    val frequencyNote: String?,
    /** 系统不公开内存颗粒时，按芯片型号推断的世代，例如 LPDDR4X。 */
    val inferredMemoryType: String?,
    val memInfoPreview: String
)
