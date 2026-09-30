package com.rjy.xyz.apps.xyzinfo.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * 机型库更新：从项目仓库拉取最新的机型映射表，存到应用私有目录后优先于内置资源使用。
 *
 * v0.7 修掉了「首页那个更新按钮点了根本没用」的三个原因：
 * 1. 应用之前**没有申请 INTERNET 权限**，所有网络请求都会被系统直接拒绝；
 * 2. 只有一个 raw.githubusercontent.com 地址，国内网络经常连不上——
 *    现在按顺序尝试 raw 与两个 jsDelivr 镜像；
 * 3. 下载内容不做校验，404 页面也会被当数据写进缓存，结果机型名全没了——
 *    现在会校验 gzip 魔数与条目数，校验不过就整体放弃，不动旧数据。
 */
object DeviceNameUpdater {

    private const val REPO = "StarHZ-XYZ/Xyzinfo"
    private const val BRANCH = "codex/refactor-v0.2"

    /** 依次尝试的数据源：官方 raw → jsDelivr 两个节点。 */
    private val SOURCES = listOf(
        "https://raw.githubusercontent.com/$REPO/$BRANCH/data",
        "https://cdn.jsdelivr.net/gh/$REPO@$BRANCH/data",
        "https://fastly.jsdelivr.net/gh/$REPO@$BRANCH/data"
    )

    private const val VERSION_FILE = "device_names_version.txt"
    private const val MIN_ENTRIES = 500

    private val DATA_FILES = listOf(
        "${DeviceNameRepository.DEVICE_FILE}.tsv.gz",
        "${DeviceNameRepository.MODEL_FILE}.tsv.gz"
    )

    data class UpdateResult(val success: Boolean, val message: String)

    fun localVersion(context: Context): String =
        File(dir(context), VERSION_FILE).takeIf { it.exists() }
            ?.readText()?.trim()?.takeIf { it.isNotBlank() }
            ?: "内置版本"

    /** 先判断有没有网络，省得用户对着「正在检查…」白等十几秒。 */
    fun hasNetwork(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** 依次尝试各个镜像；全部失败时返回最后一个错误原因。 */
    fun update(context: Context): UpdateResult {
        if (!hasNetwork(context)) {
            return UpdateResult(false, "没有可用网络，连上 Wi-Fi 或流量后重试")
        }
        var lastError = "未知错误"
        SOURCES.forEachIndexed { index, base ->
            val result = trySource(context, base)
            if (result.success) return result
            lastError = result.message
            if (index < SOURCES.size - 1) {
                // 换镜像之间歇一下，避免被判定为突发请求
                runCatching { Thread.sleep(300) }
            }
        }
        return UpdateResult(false, lastError)
    }

    private fun trySource(context: Context, base: String): UpdateResult {
        val versionBytes = download("$base/$VERSION_FILE")
            ?: return UpdateResult(false, "连不上更新服务器（网络受限或被拦截）")
        val version = versionBytes.decodeToString().trim().take(64).ifBlank { "未知版本" }

        val targetDir = dir(context).apply { mkdirs() }
        val staging = File(targetDir, "staging").apply {
            deleteRecursively()
            mkdirs()
        }

        var totalEntries = 0
        for (name in DATA_FILES) {
            val bytes = download("$base/$name")
                ?: return UpdateResult(false, "下载 $name 失败（连接中断）")
            val entries = validateGzip(bytes)
            if (entries < MIN_ENTRIES) {
                return UpdateResult(false, "$name 数据异常（只解析到 $entries 条）")
            }
            File(staging, name).writeBytes(bytes)
            totalEntries += entries
        }

        // 全部校验通过后再落盘，避免更新到一半把旧数据弄坏
        DATA_FILES.forEach { name ->
            File(staging, name).copyTo(File(targetDir, name), overwrite = true)
        }
        File(targetDir, VERSION_FILE).writeText(version)
        staging.deleteRecursively()
        DeviceNameRepository.reload(context)
        return UpdateResult(true, "已更新到 $version，合计 $totalEntries 条机型记录")
    }

    /** 下载一个小文件，失败返回 null。 */
    private fun download(url: String): ByteArray? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "XyzInfo-Android")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /** 校验 gzip 魔数并数出条目数；不是合法 gzip 就返回 0。 */
    private fun validateGzip(bytes: ByteArray): Int {
        if (bytes.size < 1024) return 0
        if (bytes[0] != 0x1F.toByte() || bytes[1] != 0x8B.toByte()) return 0
        return runCatching {
            var count = 0
            GZIPInputStream(bytes.inputStream()).bufferedReader().useLines { lines ->
                lines.forEach { if (it.contains('\t')) count++ }
            }
            count
        }.getOrDefault(0)
    }

    /** 删除已下载的数据，回到内置资源。 */
    fun resetToBuiltIn(context: Context): Boolean {
        val directory = dir(context)
        if (!directory.exists()) return false
        val removed = directory.deleteRecursively()
        DeviceNameRepository.reload(context)
        return removed
    }

    private fun dir(context: Context): File = DeviceNameRepository.updatedDir(context)
}
