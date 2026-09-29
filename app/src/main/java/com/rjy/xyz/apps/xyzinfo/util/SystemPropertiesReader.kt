package com.rjy.xyz.apps.xyzinfo.util

/**
 * 读取系统属性。
 *
 * Android 没有给普通应用开放 `android.os.SystemProperties`（隐藏 API），
 * 因此这里调用 `/system/bin/getprop` 一次性取回全部属性再解析。
 * 不需要 root，绝大多数机型可用；被 SELinux 限制时返回空表，界面会退回默认文案。
 */
object SystemPropertiesReader {

    private const val GETPROP_PATH = "/system/bin/getprop"

    private val LINE_PATTERN = Regex("""^\[([^\]]+)]:\s*\[(.*)]$""")

    fun readAll(): Map<String, String> = runCatching {
        val process = ProcessBuilder(GETPROP_PATH).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        parse(output)
    }.getOrDefault(emptyMap())

    /** 解析 getprop 输出，格式为 `[key]: [value]`。 */
    fun parse(output: String): Map<String, String> {
        val properties = mutableMapOf<String, String>()
        output.lineSequence().forEach { line ->
            val match = LINE_PATTERN.find(line) ?: return@forEach
            properties[match.groupValues[1]] = match.groupValues[2]
        }
        return properties
    }
}
