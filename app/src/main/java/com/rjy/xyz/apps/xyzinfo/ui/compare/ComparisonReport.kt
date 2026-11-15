package com.rjy.xyz.apps.xyzinfo.ui.compare

import com.rjy.xyz.apps.xyzinfo.data.DeviceSnapshot

/** 把当前对比表整理成适合微信、论坛或聊天里直接粘贴的纯文本。 */
object ComparisonReport {

    fun build(a: DeviceSnapshot, b: DeviceSnapshot): String = buildString {
        appendLine("XYZ-Devinfo 设备对比")
        appendLine("本机：${a.deviceName}")
        appendLine("对比机：${b.deviceName}")
        appendLine()

        val aRows = a.rows.associateBy { it.group to it.label }
        val bRows = b.rows.associateBy { it.group to it.label }
        val groups = LinkedHashSet<String>().apply {
            addAll(a.rows.map { it.group })
            addAll(b.rows.map { it.group })
        }
        groups.forEach { group ->
            appendLine("【$group】")
            val labels = LinkedHashSet<String>().apply {
                addAll(a.rows.filter { it.group == group }.map { it.label })
                addAll(b.rows.filter { it.group == group }.map { it.label })
            }
            labels.forEach { label ->
                val left = aRows[group to label]?.value ?: "未提供"
                val right = bRows[group to label]?.value ?: "未提供"
                val marker = if (left != right) "  ≠" else ""
                appendLine("$label：$left | $right$marker")
            }
            appendLine()
        }
        append("“≠”表示两台设备该项不同 · 由 XYZ-Devinfo 生成")
    }
}
