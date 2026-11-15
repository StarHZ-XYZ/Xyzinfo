package com.rjy.xyz.apps.xyzinfo.data

import android.content.Context
import org.json.JSONObject

/** 内置设备规格库：只保存硬件事实，不包含价格、评分等易过时营销字段。 */
object DeviceCatalog {

    data class Device(
        val name: String,
        val brand: String,
        val processor: String,
        val cores: Int,
        val ramGb: Int,
        val storage: String,
        val batteryMah: Int,
        val screenSize: String,
        val resolution: String,
        val refreshHz: Int,
        val rearCamera: String,
        val frontCamera: String,
        val os: String
    ) {
        fun toSnapshot(appVersion: String): DeviceSnapshot = DeviceSnapshot(
            capturedAt = 0L,
            appVersion = appVersion,
            deviceName = name,
            brand = brand,
            rows = buildList {
                add(CompareRow("身份", "机型名", name))
                add(CompareRow("身份", "品牌", brand))
                add(CompareRow("身份", "Android", os))
                add(CompareRow("芯片", "型号", processor))
                add(
                    CompareRow(
                        "芯片", "核心数", "$cores 核",
                        numeric = cores.toDouble(), betterWhenHigher = true
                    )
                )
                add(
                    CompareRow(
                        "内存", "容量", "$ramGb GB",
                        numeric = ramGb.toDouble(), betterWhenHigher = true
                    )
                )
                add(
                    CompareRow(
                        "电池", "设计容量", "$batteryMah mAh",
                        numeric = batteryMah.toDouble(), betterWhenHigher = true
                    )
                )
                add(
                    CompareRow(
                        "屏幕", "尺寸", "$screenSize 英寸",
                        numeric = screenSize.toDoubleOrNull(), betterWhenHigher = true
                    )
                )
                add(CompareRow("屏幕", "分辨率", resolution.replace('x', '×')))
                add(
                    CompareRow(
                        "屏幕", "刷新率", if (refreshHz > 0) "$refreshHz Hz" else "未公开",
                        numeric = refreshHz.takeIf { it > 0 }?.toDouble(), betterWhenHigher = true
                    )
                )
                if (rearCamera.isNotBlank()) add(CompareRow("相机", "后置", rearCamera))
                if (frontCamera.isNotBlank()) add(CompareRow("相机", "前置", frontCamera))
                if (storage.isNotBlank()) add(CompareRow("系统", "存储", storage))
            }
        )
    }

    @Volatile
    private var cached: List<Device>? = null

    @Volatile
    var sourceLabel: String = ""
        private set

    fun load(context: Context): List<Device> = cached ?: synchronized(this) {
        cached ?: read(context).also { cached = it }
    }

    fun count(context: Context): Int = load(context).size

    fun search(context: Context, query: String): List<Device> {
        val q = query.trim()
        val all = load(context)
        if (q.isEmpty()) return all
        var mapped = q
        listOf(
            "骁龙" to "Snapdragon",
            "天玑" to "Dimensity",
            "猎户座" to "Exynos",
            "麒麟" to "Kirin",
            "荣耀" to "Honor",
            "三星" to "Samsung",
            "红米" to "Redmi",
            "小米" to "Xiaomi",
            "一加" to "OnePlus",
            "真我" to "Realme",
            "摩托罗拉" to "Motorola",
            "苹果" to "Apple",
            "谷歌" to "Google"
        ).forEach { (chinese, english) ->
            mapped = mapped.replace(chinese, english)
        }
        val tokens = mapped.split(Regex("\\s+")).filter { it.isNotBlank() }
        return all.filter {
            val searchable = "${it.name} ${it.brand} ${it.processor}"
            tokens.all { token -> searchable.contains(token, ignoreCase = true) }
        }
    }

    private fun read(context: Context): List<Device> = runCatching {
        val root = JSONObject(
            context.assets.open("device_catalog.json").bufferedReader().use { it.readText() }
        )
        sourceLabel = root.optString("source")
        val array = root.getJSONArray("devices")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val name = item.optString("name").trim()
                if (name.isBlank()) continue
                add(
                    Device(
                        name = name,
                        brand = item.optString("brand").trim().ifBlank { "其他" },
                        processor = item.optString("processor").trim().ifBlank { "未公开" },
                        cores = item.optInt("cores"),
                        ramGb = item.optInt("ram"),
                        storage = item.optString("storage").trim(),
                        batteryMah = item.optInt("battery"),
                        screenSize = item.optString("screenSize").trim(),
                        resolution = item.optString("resolution").trim(),
                        refreshHz = item.optInt("refresh"),
                        rearCamera = item.optString("rear").trim(),
                        frontCamera = item.optString("front").trim(),
                        os = item.optString("os").trim().ifBlank { "未公开" }
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}
