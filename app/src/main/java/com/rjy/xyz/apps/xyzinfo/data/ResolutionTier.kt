package com.rjy.xyz.apps.xyzinfo.data

import kotlin.math.min

/**
 * 按分辨率推断「几 K / 什么档位」（1.0.6 新增）。
 *
 * 判据用**短边**：手机竖屏时短边就是那个"p"数 —— 2400×1080 是 1080p，
 * 3200×1440 是 1440p（俗称 2K），3840×2160 是 4K。用长边会得出"2400p"这种没人这么叫的名字。
 *
 * 各档名字用大家真正在用的叫法：720p(HD) / 1080p(FHD) / 1.5K / 2K(QHD) / 4K(UHD)。
 */
object ResolutionTier {

    /** 返回人类可读的档位名，例如「1080p（FHD）」；识别不出来时给「约 NNNNp」。 */
    fun of(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return "未知"
        val short = min(width, height)
        return when {
            short >= 4320 -> "8K（4320p）"
            short >= 2880 -> "5K 级（2880p）"
            short >= 2160 -> "4K（UHD 2160p）"
            short >= 1800 -> "2K+ 级（${short}p）"
            short >= 1600 -> "2K 级（${short}p）"
            short >= 1440 -> "2K（QHD 1440p）"
            // 1220p / 1260p 这类"比 1080p 高一档又不到 2K"的，市面上都叫 1.5K
            short >= 1200 -> "1.5K（${short}p）"
            short >= 1080 -> "1080p（FHD）"
            short >= 960 -> "1080p 级（${short}p）"
            short >= 720 -> "720p（HD）"
            short >= 540 -> "540p（qHD）"
            else -> "低于 540p"
        }
    }

    /** 一行完整说明：档位 + 长宽比。 */
    fun describe(width: Int, height: Int): String {
        val tier = of(width, height)
        val ratio = coarseRatio(width, height)
        return if (ratio == null) tier else "$tier ｜ $ratio"
    }

    /**
     * 粗略长宽比（只认常见档，避免出现 "19.98:9" 这种噪声比例）。
     * 在常见比例里找最接近的一个，差得太多就不硬套（折叠屏内屏之类）。
     */
    fun coarseRatio(width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        val long = maxOf(width, height)
        val short = min(width, height)
        val actual = long.toDouble() / short
        val candidates = listOf(
            16.0 / 9 to "16:9",
            18.0 / 9 to "18:9",
            18.5 / 9 to "18.5:9",
            19.0 / 9 to "19:9",
            19.5 / 9 to "19.5:9",
            20.0 / 9 to "20:9",
            21.0 / 9 to "21:9",
            4.0 / 3 to "4:3",
            3.0 / 2 to "3:2",
            16.0 / 10 to "16:10"
        )
        val best = candidates.minByOrNull { kotlin.math.abs(it.first - actual) } ?: return null
        return if (kotlin.math.abs(best.first - actual) <= 0.06) best.second else null
    }
}
