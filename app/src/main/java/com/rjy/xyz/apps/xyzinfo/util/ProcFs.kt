package com.rjy.xyz.apps.xyzinfo.util

import java.io.File

/**
 * 读取 /proc、/sys 等系统节点的小工具。
 *
 * 这些节点在不同机型上可能不存在、无权限或内容格式不同，
 * 因此所有读取都做容错处理，失败时返回 null 或空字符串，绝不抛异常。
 */
object ProcFs {

    /** 读取节点文本，失败或不存在时返回空字符串。 */
    fun readText(path: String): String =
        runCatching { File(path).readText() }.getOrDefault("")

    /** 读取节点文本，空白内容视为读取失败。 */
    fun readTextOrNull(path: String): String? = readText(path).takeIf { it.isNotBlank() }

    /** 读取节点并解析为 Int，失败时返回 null。 */
    fun readInt(path: String): Int? = readText(path).trim().toIntOrNull()

    /** 读取节点并解析为 Long，失败时返回 null。 */
    fun readLong(path: String): Long? = readText(path).trim().toLongOrNull()

    fun exists(path: String): Boolean = runCatching { File(path).exists() }.getOrDefault(false)

    /** 按顺序尝试多个候选节点，返回第一个有内容的文本。 */
    fun firstText(paths: List<String>): String? {
        for (path in paths) {
            val text = readText(path)
            if (text.isNotBlank()) return text
        }
        return null
    }

    /** 按顺序尝试多个候选节点，返回第一个能解析成 Int 的值。 */
    fun firstInt(paths: List<String>): Int? {
        for (path in paths) {
            readInt(path)?.let { return it }
        }
        return null
    }

    /** 按顺序尝试多个候选节点，返回第一个能解析成 Long 的值。 */
    fun firstLong(paths: List<String>): Long? {
        for (path in paths) {
            readLong(path)?.let { return it }
        }
        return null
    }

    /** 按顺序尝试多个候选节点，返回第一个大于 0 的 Int 值（频率类节点常用）。 */
    fun firstPositiveInt(paths: List<String>): Int? {
        for (path in paths) {
            val value = readInt(path)
            if (value != null && value > 0) return value
        }
        return null
    }

    /** 读取目录下的子文件，目录不存在时返回空列表。 */
    fun listFiles(path: String): List<File> =
        runCatching { File(path).listFiles()?.toList() }.getOrNull().orEmpty()

    /**
     * 生成“原始节点”预览文本，供界面底部的原始信息区域展示。
     *
     * @param title 例如 “原始 /proc/cpuinfo”。
     */
    fun preview(raw: String, title: String, maxLines: Int = 18): String {
        if (raw.isBlank()) return "$title：读取失败"
        val lines = raw.lineSequence()
            .filter { it.isNotBlank() }
            .take(maxLines)
            .joinToString("\n")
        return "$title 预览：\n$lines"
    }
}
