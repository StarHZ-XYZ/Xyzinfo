package com.rjy.xyz.apps.xyzinfo.data.benchmark

import com.rjy.xyz.apps.xyzinfo.model.ChipScore
import com.rjy.xyz.apps.xyzinfo.model.RankingMetric

/**
 * 处理器参考榜单（数据整理自极客湾 / Geekerwan 公开榜单）。
 *
 * 说明：
 * - 单核 / 多核采用 Geekbench 6 量级的参考值，GPU 为相对指数（骁龙 778G = 1000），
 *   三个维度都做了同一刻度归一，便于在应用内横向排队；
 * - 同一颗芯片在不同机型上的实际成绩会因散热、频率策略、系统版本而浮动，
 *   榜单给的是「该芯片的典型水平」，不是某一台机器的实测值；
 * - 榜单里的分数用于给本机成绩一个参照，**不是官方成绩**，也不代表本机跑分
 *   采用了与 Geekbench / 极客湾相同的算法。
 */
object GeekerwanScores {

    const val SNAPSHOT = "2026-09"

    /**
     * 内置榜单（随 App 一起发布的那一份）。
     *
     * 不叫 `all` 是因为**在线更新下来的数据集会覆盖它**：见 [applyDataset]。
     */
    val builtIn: List<ChipScore> = listOf(
        // ---------- 高通 骁龙 ----------
        ChipScore("骁龙 8 Elite Gen 5", "骁龙", 3500, 10500, 4300, 2025),
        ChipScore("骁龙 8 Elite Gen 5 for Galaxy", "骁龙", 3600, 10800, 4500, 2026),
        ChipScore("骁龙 8 Elite", "骁龙", 3050, 9600, 3900, 2024),
        ChipScore("骁龙 8 Elite for Galaxy", "骁龙", 3150, 10000, 4100, 2025),
        ChipScore("骁龙 8 Gen 3", "骁龙", 2250, 6900, 3100, 2023),
        ChipScore("骁龙 8 Gen 3 for Galaxy", "骁龙", 2320, 7100, 3200, 2024),
        ChipScore("骁龙 8s Gen 4", "骁龙", 2150, 6300, 2700, 2025),
        ChipScore("骁龙 8s Gen 3", "骁龙", 1900, 5100, 2400, 2024),
        ChipScore("骁龙 8 Gen 2", "骁龙", 2050, 5700, 2600, 2022),
        ChipScore("骁龙 8 Gen 2 for Galaxy", "骁龙", 2120, 5900, 2750, 2023),
        ChipScore("骁龙 8+ Gen 1", "骁龙", 1750, 4500, 2200, 2022),
        ChipScore("骁龙 8 Gen 1", "骁龙", 1600, 4100, 2000, 2021),
        ChipScore("骁龙 888+", "骁龙", 1550, 4000, 1800, 2021),
        ChipScore("骁龙 888", "骁龙", 1500, 3900, 1700, 2020),
        ChipScore("骁龙 870", "骁龙", 1400, 3600, 1500, 2021),
        ChipScore("骁龙 865+", "骁龙", 1300, 3500, 1350, 2020),
        ChipScore("骁龙 865", "骁龙", 1250, 3400, 1300, 2019),
        ChipScore("骁龙 860", "骁龙", 1200, 3200, 1200, 2021),
        ChipScore("骁龙 855+", "骁龙", 1100, 2900, 1100, 2020),
        ChipScore("骁龙 855", "骁龙", 1050, 2750, 1050, 2018),
        ChipScore("骁龙 845", "骁龙", 850, 2300, 800, 2017),
        ChipScore("骁龙 835", "骁龙", 700, 1800, 600, 2016),
        ChipScore("骁龙 7+ Gen 3", "骁龙", 1550, 4400, 1750, 2024),
        ChipScore("骁龙 7+ Gen 2", "骁龙", 1400, 3900, 1250, 2023),
        ChipScore("骁龙 7 Gen 4", "骁龙", 1300, 3700, 1300, 2025),
        ChipScore("骁龙 7 Gen 3", "骁龙", 1200, 3300, 1050, 2023),
        ChipScore("骁龙 7s Gen 3", "骁龙", 1100, 3000, 950, 2024),
        ChipScore("骁龙 7 Gen 1", "骁龙", 1050, 2900, 1000, 2022),
        ChipScore("骁龙 7s Gen 2", "骁龙", 950, 2600, 850, 2023),
        ChipScore("骁龙 780G", "骁龙", 1080, 3000, 1050, 2020),
        ChipScore("骁龙 778G+", "骁龙", 1020, 2950, 950, 2021),
        ChipScore("骁龙 778G", "骁龙", 1010, 2900, 1000, 2021),
        ChipScore("骁龙 765G", "骁龙", 930, 2300, 700, 2019),
        ChipScore("骁龙 750G", "骁龙", 850, 2200, 650, 2020),
        ChipScore("骁龙 732G", "骁龙", 720, 1850, 500, 2020),
        ChipScore("骁龙 6 Gen 4", "骁龙", 1150, 3200, 800, 2025),
        ChipScore("骁龙 6 Gen 3", "骁龙", 950, 2700, 700, 2024),
        ChipScore("骁龙 6 Gen 1", "骁龙", 900, 2500, 650, 2022),
        ChipScore("骁龙 695", "骁龙", 950, 2200, 600, 2021),
        ChipScore("骁龙 690", "骁龙", 800, 2000, 500, 2020),
        ChipScore("骁龙 4 Gen 2", "骁龙", 820, 2000, 450, 2023),
        ChipScore("骁龙 4 Gen 1", "骁龙", 750, 1800, 400, 2022),

        // ---------- 联发科 天玑 / Helio ----------
        ChipScore("天玑 9500", "天玑", 3300, 10000, 4100, 2025),
        ChipScore("天玑 9400", "天玑", 2900, 9200, 3700, 2024),
        ChipScore("天玑 9400e", "天玑", 2650, 8300, 3300, 2025),
        ChipScore("天玑 9300+", "天玑", 2300, 7800, 3500, 2024),
        ChipScore("天玑 9300", "天玑", 2250, 7600, 3400, 2023),
        ChipScore("天玑 9200+", "天玑", 2050, 5800, 3100, 2023),
        ChipScore("天玑 9200", "天玑", 1950, 5500, 3000, 2022),
        ChipScore("天玑 9000+", "天玑", 1750, 4600, 2700, 2022),
        ChipScore("天玑 9000", "天玑", 1700, 4400, 2600, 2021),
        ChipScore("天玑 8500", "天玑", 1900, 5600, 2400, 2025),
        ChipScore("天玑 8400", "天玑", 1650, 5100, 2200, 2024),
        ChipScore("天玑 8300", "天玑", 1550, 4700, 2000, 2023),
        ChipScore("天玑 8200", "天玑", 1250, 4000, 1500, 2022),
        ChipScore("天玑 8100", "天玑", 1150, 3800, 1400, 2022),
        ChipScore("天玑 8000", "天玑", 1050, 3300, 1250, 2021),
        ChipScore("天玑 7400", "天玑", 1150, 3200, 950, 2025),
        ChipScore("天玑 7300", "天玑", 1050, 3000, 900, 2024),
        ChipScore("天玑 7200", "天玑", 1100, 3100, 900, 2023),
        ChipScore("天玑 7050", "天玑", 1000, 2800, 850, 2022),
        ChipScore("天玑 1300", "天玑", 1050, 3100, 900, 2021),
        ChipScore("天玑 1200", "天玑", 1000, 3000, 850, 2021),
        ChipScore("天玑 1100", "天玑", 950, 2900, 800, 2021),
        ChipScore("天玑 1080", "天玑", 850, 2300, 700, 2021),
        ChipScore("天玑 920", "天玑", 900, 2400, 750, 2019),
        ChipScore("天玑 900", "天玑", 850, 2200, 650, 2020),
        ChipScore("天玑 820", "天玑", 750, 2000, 550, 2019),
        ChipScore("天玑 720", "天玑", 700, 1900, 500, 2019),
        ChipScore("天玑 700", "天玑", 620, 1700, 420, 2020),
        ChipScore("天玑 6300", "天玑", 650, 1800, 350, 2024),
        ChipScore("天玑 6080", "天玑", 600, 1650, 320, 2023),
        ChipScore("天玑 7025", "天玑", 720, 2000, 380, 2023),
        ChipScore("Helio G99", "天玑", 780, 2100, 450, 2022),
        ChipScore("Helio G96", "天玑", 700, 1900, 400, 2021),
        ChipScore("Helio G90T", "天玑", 620, 1700, 380, 2019),

        // ---------- 三星 Exynos ----------
        ChipScore("Exynos 2600", "猎户座", 2900, 9000, 3500, 2026),
        ChipScore("Exynos 2500", "猎户座", 2400, 7600, 2700, 2024),
        ChipScore("Exynos 2400", "猎户座", 2050, 6600, 2300, 2024),
        ChipScore("Exynos 2200", "猎户座", 1350, 3600, 1500, 2022),
        ChipScore("Exynos 2100", "猎户座", 1250, 3400, 1400, 2021),
        ChipScore("Exynos 1580", "猎户座", 1150, 3400, 1200, 2024),
        ChipScore("Exynos 1480", "猎户座", 1120, 3300, 1100, 2024),
        ChipScore("Exynos 1380", "猎户座", 1050, 3100, 1000, 2023),
        ChipScore("Exynos 1280", "猎户座", 950, 2800, 800, 2022),
        ChipScore("Exynos 1080", "猎户座", 1000, 2800, 950, 2020),
        ChipScore("Exynos 990", "猎户座", 1000, 2900, 1200, 2020),
        ChipScore("Exynos 9825", "猎户座", 950, 2700, 1100, 2019),

        // ---------- 华为 麒麟 ----------
        ChipScore("麒麟 9020", "麒麟", 1600, 4700, 2000, 2024),
        ChipScore("麒麟 9010", "麒麟", 1500, 4400, 1900, 2024),
        ChipScore("麒麟 9000S", "麒麟", 1350, 3900, 1600, 2023),
        ChipScore("麒麟 9000", "麒麟", 1300, 3700, 1600, 2020),
        ChipScore("麒麟 9000E", "麒麟", 1250, 3500, 1500, 2020),
        ChipScore("麒麟 990 5G", "麒麟", 1050, 2900, 1200, 2019),
        ChipScore("麒麟 990", "麒麟", 1000, 2800, 1150, 2019),
        ChipScore("麒麟 985", "麒麟", 950, 2600, 1000, 2020),
        ChipScore("麒麟 980", "麒麟", 900, 2500, 900, 2018),
        ChipScore("麒麟 820", "麒麟", 880, 2500, 850, 2020),
        ChipScore("麒麟 810", "麒麟", 780, 2200, 650, 2019),

        // ---------- Google Tensor ----------
        ChipScore("Tensor G5", "谷歌", 1900, 4900, 2100, 2025),
        ChipScore("Tensor G4", "谷歌", 1650, 4200, 1800, 2024),
        ChipScore("Tensor G3", "谷歌", 1450, 3900, 1550, 2023),
        ChipScore("Tensor G2", "谷歌", 1350, 3300, 1350, 2022),
        ChipScore("Tensor G1", "谷歌", 1050, 2800, 1050, 2021),

        // ---------- 小米玄戒 / 紫光展锐 ----------
        ChipScore("玄戒 O1", "玄戒", 2750, 8300, 3200, 2025),
        ChipScore("紫光展锐 T820", "展锐", 900, 2400, 850, 2023),
        ChipScore("紫光展锐 T760", "展锐", 750, 2000, 600, 2023),
        ChipScore("紫光展锐 T618", "展锐", 620, 1600, 450, 2021),
        ChipScore("紫光展锐 T610", "展锐", 560, 1500, 400, 2020)
    )

    /** 榜单里出现的品牌顺序（按旗舰水平排列，用作筛选条的顺序）。 */
    val brands: List<String> = all.map { it.brand }.distinct()

    // ---------- 在线更新下来的数据集 ----------

    /** 下载到的数据集；为 null 表示用内置那份。 */
    @Volatile
    private var downloaded: List<ChipScore>? = null

    /** 当前生效的数据集版本（内置版号或下载到的版号）。 */
    @Volatile
    var datasetVersion: String = SNAPSHOT
        private set

    /** 当前生效的榜单：优先用下载到的，没有就用内置。 */
    val all: List<ChipScore> get() = downloaded ?: builtIn

    /** 是否正在用下载的数据集。 */
    val usingDownloaded: Boolean get() = downloaded != null

    /**
     * 换用一份新的数据集（已经解析成 [ChipScore] 的列表）。
     * [version] 会参与排行榜缓存的有效性判断，换版本会自动让旧缓存失效。
     */
    fun applyDataset(chips: List<ChipScore>, version: String) {
        if (chips.isEmpty()) return
        downloaded = chips
        datasetVersion = version
    }

    /** 回到内置数据集。 */
    fun useBuiltIn() {
        downloaded = null
        datasetVersion = SNAPSHOT
    }

    /** 按维度排序后的榜单。 */
    fun ranked(metric: RankingMetric, brand: String? = null): List<ChipScore> {
        val filtered = if (brand == null) all else all.filter { it.brand == brand }
        return filtered.sortedByDescending { metric.valueOf(it) }
    }

    /**
     * 依据本机识别出的芯片名找参考条目。
     *
     * 规则：参考名必须能对上本机芯片名的开头（允许「骁龙 778G」匹配「骁龙 778G+」这类
     * 带后缀的写法），多个候选取最长的那个，避免「骁龙 8 Elite」被
     * 「骁龙 8 Elite Gen 5」抢走。
     */
    fun match(chipName: String?): ChipScore? {
        val normalized = chipName?.trim().orEmpty()
        if (normalized.isEmpty()) return null
        // 只要求「本机芯片名以参考名为前缀」：这样「骁龙 778G / 778G+」会匹配到
        // 「骁龙 778G」而不是更长的「骁龙 778G+」，取最长匹配只是为了避免被短名抢走。
        return all
            .filter { candidate -> normalized.startsWith(candidate.name, ignoreCase = true) }
            .maxByOrNull { it.name.length }
    }

    /** 参考成绩相对本机的倍数，例如 2.1 表示「约为本机 2.1 倍」。 */
    fun ratio(reference: Int, device: Int): Double =
        if (device <= 0) 0.0 else reference.toDouble() / device
}
