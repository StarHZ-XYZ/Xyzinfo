package com.rjy.xyz.apps.xyzinfo.data.benchmark

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 排行榜的**本地缓存**。
 *
 * 进页面时会做三件事：筛选 → 排序 → 拼概览，再铺 90 行。之前每进一次都要重算一遍，
 * 进程被杀掉再进来更是从零开始。现在把「整理好的榜单」直接落盘：
 *
 * - 进页面先读缓存（几十毫秒），立刻就能开始逐条显示；
 * - 只有缓存失效（换了筛选维度 / 换了芯片 / 重跑过跑分 / 榜单数据版本变了）才重新整理；
 * - 缓存里带 `datasetVersion`：在线更新过榜单数据后旧缓存自动作废，不会显示上一版的榜。
 */
object RankingCache {

    private const val FILE_NAME = "ranking_cache.json"
    private const val FORMAT_VERSION = 1
    private const val MAX_ROWS = 400

    data class Row(
        val name: String,
        val value: Int,
        val year: String,
        val measured: Boolean,
        val matched: Boolean
    )

    data class Snapshot(
        val key: String,
        val summary: String,
        val maxValue: Int,
        val rows: List<Row>,
        val datasetVersion: String,
        val savedAt: Long
    )

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun load(context: Context): Snapshot? {
        val target = file(context)
        if (!target.exists()) return null
        return runCatching {
            val json = JSONObject(target.readText())
            if (json.optInt("v") != FORMAT_VERSION) return null
            val rowsJson = json.optJSONArray("rows") ?: return null
            val rows = ArrayList<Row>(rowsJson.length())
            for (index in 0 until rowsJson.length()) {
                val item = rowsJson.optJSONObject(index) ?: continue
                rows += Row(
                    name = item.optString("n"),
                    value = item.optInt("v"),
                    year = item.optString("y"),
                    measured = item.optBoolean("m"),
                    matched = item.optBoolean("d")
                )
            }
            if (rows.isEmpty()) return null
            Snapshot(
                key = json.optString("key"),
                summary = json.optString("summary"),
                maxValue = json.optInt("max", 1),
                rows = rows,
                datasetVersion = json.optString("dataVersion"),
                savedAt = json.optLong("at")
            )
        }.getOrNull()
    }

    fun save(context: Context, snapshot: Snapshot) {
        runCatching {
            val rows = JSONArray()
            snapshot.rows.take(MAX_ROWS).forEach { row ->
                rows.put(
                    JSONObject().apply {
                        put("n", row.name)
                        put("v", row.value)
                        put("y", row.year)
                        put("m", row.measured)
                        put("d", row.matched)
                    }
                )
            }
            val json = JSONObject().apply {
                put("v", FORMAT_VERSION)
                put("key", snapshot.key)
                put("summary", snapshot.summary)
                put("max", snapshot.maxValue)
                put("rows", rows)
                put("dataVersion", snapshot.datasetVersion)
                put("at", System.currentTimeMillis())
            }
            file(context).writeText(json.toString())
        }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
