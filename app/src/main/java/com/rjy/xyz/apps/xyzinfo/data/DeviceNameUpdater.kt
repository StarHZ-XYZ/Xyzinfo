package com.rjy.xyz.apps.xyzinfo.data

import android.content.Context
import java.io.File
import java.net.URL

/**
 * 机型库更新：从项目仓库拉取最新的机型映射表，存到应用私有目录后优先于内置资源使用。
 *
 * 数据文件放在仓库的 data/ 目录：
 * - device_names_version.txt  版本标识（自定义字符串，例如 2026-09-30）
 * - device_names.tsv.gz       设备代号 → 机型名
 * - model_names.tsv.gz        系统型号 → 机型名
 */
object DeviceNameUpdater {

    /** 数据地址：目前指向开发分支，合并到 main 后改为 main 即可。 */
    private const val BASE_URL =
        "https://raw.githubusercontent.com/StarHZ-XYZ/Xyzinfo/codex/refactor-v0.2/data"

    private const val VERSION_FILE = "device_names_version.txt"

    private val DATA_FILES = listOf(
        "${DeviceNameRepository.DEVICE_FILE}.tsv.gz",
        "${DeviceNameRepository.MODEL_FILE}.tsv.gz"
    )

    data class UpdateResult(val success: Boolean, val message: String)

    fun localVersion(context: Context): String =
        File(dir(context), VERSION_FILE).takeIf { it.exists() }
            ?.readText()?.trim()?.takeIf { it.isNotBlank() }
            ?: "内置版本"

    /** 读取远端版本标识，失败返回 null。 */
    fun remoteVersion(): String? = runCatching {
        URL("$BASE_URL/$VERSION_FILE").openStream().use { it.readBytes().decodeToString().trim() }
            .takeIf { it.isNotBlank() }
    }.getOrNull()

    /** 下载并替换映射表；成功后立即重载。 */
    fun update(context: Context): UpdateResult {
        val version = remoteVersion()
            ?: return UpdateResult(false, "无法连接更新服务器（请检查网络后重试）")

        val targetDir = dir(context).apply { mkdirs() }
        for (name in DATA_FILES) {
            val bytes = runCatching {
                URL("$BASE_URL/$name").openStream().use { it.readBytes() }
            }.getOrNull() ?: return UpdateResult(false, "下载 $name 失败")

            if (bytes.size < 1_000) {
                return UpdateResult(false, "$name 内容异常（仅 ${bytes.size} 字节）")
            }
            File(targetDir, name).writeBytes(bytes)
        }

        File(targetDir, VERSION_FILE).writeText(version)
        DeviceNameRepository.reload(context)
        return UpdateResult(
            true,
            "更新完成：$version（当前合计 ${DeviceNameRepository.entryCount()} 条机型记录）"
        )
    }

    private fun dir(context: Context): File = DeviceNameRepository.updatedDir(context)
}
