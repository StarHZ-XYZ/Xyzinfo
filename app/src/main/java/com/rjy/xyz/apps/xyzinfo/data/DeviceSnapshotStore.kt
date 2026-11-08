package com.rjy.xyz.apps.xyzinfo.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 设备快照的持久化：导出 JSON 文件、解析 JSON、以及通过系统分享面板发出去。
 *
 * 复用项目已有的两套范式：
 * - JSON 读写：[com.rjy.xyz.apps.xyzinfo.data.benchmark.RankingCache]（内置 [org.json]，无第三方库）；
 * - 文件分享：[com.rjy.xyz.apps.xyzinfo.ui.share.ShareCardActivity] 的 FileProvider + ACTION_SEND。
 *
 * 导出文件落在 `cache/share/`，而 FileProvider 已通过 `file_paths.xml` 暴露该目录，可直接分享。
 */
object DeviceSnapshotStore {

    private const val FILE_NAME = "device_snapshot.json"

    /** 快照序列化为可读 JSON 文本。 */
    fun toJson(snapshot: DeviceSnapshot): String {
        val rows = JSONArray()
        snapshot.rows.forEach { r ->
            rows.put(JSONObject().apply {
                put("g", r.group)
                put("l", r.label)
                put("v", r.value)
                if (r.numeric != null) put("n", r.numeric)
                put("b", r.betterWhenHigher)
            })
        }
        return JSONObject().apply {
            put("v", DeviceSnapshot.FORMAT_VERSION)
            put("at", snapshot.capturedAt)
            put("av", snapshot.appVersion)
            put("dn", snapshot.deviceName)
            put("br", snapshot.brand)
            put("rows", rows)
        }.toString(2)
    }

    /** 解析 JSON 文本为快照；格式版本不符或内容为空时返回 null。 */
    fun fromJson(text: String): DeviceSnapshot? = runCatching {
        val json = JSONObject(text)
        if (json.optInt("v") != DeviceSnapshot.FORMAT_VERSION) return null
        val rowsJson = json.optJSONArray("rows") ?: return null
        val rows = ArrayList<CompareRow>(rowsJson.length())
        for (i in 0 until rowsJson.length()) {
            val item = rowsJson.optJSONObject(i) ?: continue
            rows.add(
                CompareRow(
                    group = item.optString("g").orEmpty(),
                    label = item.optString("l").orEmpty(),
                    value = item.optString("v").orEmpty(),
                    numeric = if (item.has("n")) item.optDouble("n") else null,
                    betterWhenHigher = item.optBoolean("b", true)
                )
            )
        }
        if (rows.isEmpty()) return null
        DeviceSnapshot(
            capturedAt = json.optLong("at", System.currentTimeMillis()),
            appVersion = json.optString("av").orEmpty(),
            deviceName = json.optString("dn").takeIf { it.isNotBlank() } ?: "未知设备",
            brand = json.optString("br").orEmpty(),
            rows = rows
        )
    }.getOrNull()

    /** 导出到 cache/share/device_snapshot.json（FileProvider 已暴露该目录）。 */
    fun exportFile(context: Context, snapshot: DeviceSnapshot): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, FILE_NAME)
        file.writeText(toJson(snapshot))
        return file
    }

    /** 通过系统分享面板把快照文件发出去（微信 / 蓝牙 / 邮件 / 另存都行）。 */
    fun shareFile(context: Context, file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "设备信息快照 · XYZ-Devinfo 星幻终")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享设备快照"))
        }
    }
}
