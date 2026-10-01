package com.rjy.xyz.apps.xyzinfo.data.benchmark

import android.content.Context
import com.rjy.xyz.apps.xyzinfo.model.ChipScore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 排行榜数据集的在线更新。
 *
 * 榜单本身是内置的（`GeekerwanScores.builtIn`），这里只负责两件事：
 *
 * 1. **本地缓存一份下载到的数据集**：下载成功后落盘，下次启动直接读，不用再联网；
 * 2. **后台检查有没有新版本**：只拉一个几十字节的 `ranking_version.txt`，版本号一样就什么都不做；
 *    不一样才提示用户「发现新榜单，是否更新」——用户点了才下载真正的数据。
 *
 * 数据源与机型库更新用的是同一套镜像（官方 raw → jsDelivr 两个节点），
 * 国内网络下命中率更高；任何一步失败都当作"没有更新"，绝不影响页面打开。
 */
object RankingUpdater {

    private const val REPO = "StarHZ-XYZ/Xyzinfo"
    private const val BRANCH = "codex/refactor-v0.2"
    private const val DATA_DIR = "data"

    private val SOURCES = listOf(
        "https://raw.githubusercontent.com/$REPO/$BRANCH/$DATA_DIR",
        "https://cdn.jsdelivr.net/gh/$REPO@$BRANCH/$DATA_DIR",
        "https://fastly.jsdelivr.net/gh/$REPO@$BRANCH/$DATA_DIR"
    )

    private const val VERSION_FILE = "ranking_version.txt"
    private const val DATA_FILE = "geekerwan_scores.json"
    private const val MIN_ENTRIES = 20

    data class CheckResult(val currentVersion: String, val remoteVersion: String?) {
        val hasUpdate: Boolean
            get() = !remoteVersion.isNullOrBlank() && remoteVersion != currentVersion
    }

    data class UpdateResult(val success: Boolean, val message: String, val version: String? = null)

    // ---------- 本地缓存 ----------

    private fun dir(context: Context): File = File(context.filesDir, "ranking").apply { mkdirs() }

    fun localVersion(context: Context): String {
        val file = File(dir(context), VERSION_FILE)
        return file.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotBlank() }
            ?: GeekerwanScores.SNAPSHOT
    }

    /** 启动时调一次：把下载过的数据集读进内存（没有就跳过）。 */
    fun loadCached(context: Context) {
        val file = File(dir(context), DATA_FILE)
        if (!file.exists()) {
            GeekerwanScores.useBuiltIn()
            return
        }
        val chips = runCatching { parseDataset(file.readText()) }.getOrNull()
        if (chips.isNullOrEmpty()) {
            // 文件坏了就丢掉，回到内置，别让排行榜开不出来
            runCatching { file.delete() }
            GeekerwanScores.useBuiltIn()
        } else {
            GeekerwanScores.applyDataset(chips, localVersion(context))
        }
    }

    fun resetToBuiltIn(context: Context) {
        runCatching { dir(context).deleteRecursively() }
        GeekerwanScores.useBuiltIn()
    }

    // ---------- 检查 / 更新 ----------

    /** 后台检查远端版本号（**很轻**：只拉一个几十字节的文本）。任何失败都返回"没有更新"。 */
    fun check(context: Context): CheckResult {
        val current = localVersion(context)
        SOURCES.forEach { base ->
            val remote = runCatching { getText("$base/$VERSION_FILE") }.getOrNull()
            if (!remote.isNullOrBlank()) {
                return CheckResult(current, remote.trim().take(32))
            }
        }
        return CheckResult(current, null)
    }

    /** 下载并启用新数据集。必须在后台线程调用。 */
    fun update(context: Context): UpdateResult {
        var lastError = "连不上更新服务器（网络受限或被拦截）"
        SOURCES.forEach { base ->
            val version = runCatching { getText("$base/$VERSION_FILE") }.getOrNull()?.trim()
            if (version.isNullOrBlank()) return@forEach
            val json = runCatching { getText("$base/$DATA_FILE") }.getOrNull()
            if (json.isNullOrBlank()) {
                lastError = "榜单数据下载失败"
                return@forEach
            }
            val chips = runCatching { parseDataset(json) }.getOrNull()
            if (chips == null || chips.size < MIN_ENTRIES) {
                // 校验不过整体放弃，绝不用半截数据覆盖用户手里的榜单
                lastError = "榜单数据格式不对（只解析到 ${chips?.size ?: 0} 条）"
                return@forEach
            }
            runCatching {
                val target = dir(context)
                File(target, DATA_FILE).writeText(json)
                File(target, VERSION_FILE).writeText(version)
            }
            GeekerwanScores.applyDataset(chips, version)
            return UpdateResult(true, "已更新到 $version，共 ${chips.size} 条", version)
        }
        return UpdateResult(false, lastError)
    }

    // ---------- 解析 ----------

    internal fun parseDataset(json: String): List<ChipScore> {
        val array = JSONArray(json)
        val chips = ArrayList<ChipScore>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            if (name.isEmpty()) continue
            chips += ChipScore(
                name = name,
                brand = item.optString("brand").ifBlank { "其他" },
                single = item.optInt("single"),
                multi = item.optInt("multi"),
                gpu = item.optInt("gpu"),
                year = item.optInt("year")
            )
        }
        return chips
    }

    /** 把内置榜单导出成更新用的 JSON（生成仓库里的 data/geekerwan_scores.json 时用）。 */
    fun exportBuiltIn(): String {
        val array = JSONArray()
        GeekerwanScores.builtIn.forEach { chip ->
            array.put(
                JSONObject().apply {
                    put("name", chip.name)
                    put("brand", chip.brand)
                    put("single", chip.single)
                    put("multi", chip.multi)
                    put("gpu", chip.gpu)
                    put("year", chip.year)
                }
            )
        }
        return array.toString(1)
    }

    private fun getText(url: String): String? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "XyzInfo-Android")
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            runCatching { connection.disconnect() }
        }
    }.getOrNull()
}
