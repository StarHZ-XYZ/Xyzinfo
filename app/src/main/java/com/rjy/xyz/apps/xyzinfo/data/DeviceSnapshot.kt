package com.rjy.xyz.apps.xyzinfo.data

/**
 * 一条用于横向对比的字段。
 *
 * 把各检测模块的结果扁平化成「分组 + 标签 + 显示值 + 可选数值」，
 * 序列化（导出 JSON / 导入对比）只围绕这个轻量结构，不牵扯各 Provider 的完整 model。
 *
 * - [numeric] 非 null 时参与「谁大谁优」数值比较（如内存 GB、电池 mAh、刷新率 Hz）；
 * - [betterWhenHigher] 标记数值越大越好（容量 / 频率 / 刷新率等）。
 */
data class CompareRow(
    val group: String,
    val label: String,
    val value: String,
    val numeric: Double? = null,
    val betterWhenHigher: Boolean = true
)

/**
 * 一台设备的完整对比快照。
 *
 * 含机型名 / 品牌（用于对比页顶部标识）和一组扁平化的对比行。
 * 用 [DeviceSnapshotCollector] 采集本机，用 [DeviceSnapshotStore] 导出 / 导入 / 分享。
 */
data class DeviceSnapshot(
    val capturedAt: Long = System.currentTimeMillis(),
    val appVersion: String,
    val deviceName: String,
    val brand: String,
    val rows: List<CompareRow>
) {
    companion object {
        /** 快照 JSON 格式版本，跨版本不兼容时导入直接作废。 */
        const val FORMAT_VERSION = 1
    }
}
