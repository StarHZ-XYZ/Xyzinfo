package com.rjy.xyz.apps.xyzinfo.data

import com.rjy.xyz.apps.xyzinfo.model.DeviceOverview

/** 首页「复制设备摘要」的纯文本格式，便于发帖、反馈问题或保存到笔记。 */
object DeviceSummaryFormatter {

    fun build(displayName: String, overview: DeviceOverview, formLabel: String): String {
        val brand = overview.brand +
            if (overview.manufacturer.isNotBlank() && overview.manufacturer != overview.brand) {
                " / ${overview.manufacturer}"
            } else {
                ""
            }
        return buildString {
            appendLine("XYZ-Devinfo 设备摘要")
            appendLine("机型：${displayName.ifBlank { overview.displayName }}")
            appendLine("品牌：$brand")
            appendLine("Android：${overview.androidRelease}（API ${overview.apiLevel}）")
            appendLine("系统界面：${overview.romName ?: "未公开"}")
            appendLine("设备形态：$formLabel")
            appendLine("原始型号：${overview.rawModel}")
            appendLine("设备代号：${overview.deviceCode}")
            appendLine("内核：${overview.kernelRelease}")
            append("架构：${overview.abiLabel}")
        }
    }
}
