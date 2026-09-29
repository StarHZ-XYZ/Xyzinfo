package com.rjy.xyz.apps.xyzinfo.data.benchmark

import com.rjy.xyz.apps.xyzinfo.model.ChipReference
import kotlin.math.roundToInt

/**
 * 内置参考分数表。
 *
 * 说明：这些是**相对指数**，以骁龙 778G 单核 = 1000 为基准，
 * 依据公开跑分中各家芯片的相对比例折算而来，仅用于横向参照，不代表官方成绩，
 * 也不代表本机跑分算法与那些测试机构完全一致。
 */
object ReferenceScores {

    val all: List<ChipReference> = listOf(
        // 骁龙
        ChipReference("骁龙 8 Elite Gen 5", 2900, 8000, 3600),
        ChipReference("骁龙 8 Elite", 2700, 7400, 3300),
        ChipReference("骁龙 8 Gen 3", 2150, 6200, 2900),
        ChipReference("骁龙 8s Gen 4", 1950, 5300, 2500),
        ChipReference("骁龙 8 Gen 2", 1900, 5300, 2500),
        ChipReference("骁龙 8s Gen 3", 1750, 4700, 2200),
        ChipReference("骁龙 8+ Gen 1", 1550, 4200, 2000),
        ChipReference("骁龙 8 Gen 1", 1450, 3900, 1900),
        ChipReference("骁龙 888", 1300, 3500, 1700),
        ChipReference("骁龙 870", 1250, 3300, 1500),
        ChipReference("骁龙 865", 1150, 3100, 1300),
        ChipReference("骁龙 860", 1100, 2950, 1200),
        ChipReference("骁龙 855", 1000, 2600, 1050),
        ChipReference("骁龙 7+ Gen 3", 1400, 4000, 1700),
        ChipReference("骁龙 7+ Gen 2", 1200, 3200, 1100),
        ChipReference("骁龙 7 Gen 3", 1050, 2600, 950),
        ChipReference("骁龙 7s Gen 3", 950, 2300, 850),
        ChipReference("骁龙 7s Gen 2", 900, 2200, 800),
        ChipReference("骁龙 780G", 1050, 2600, 950),
        ChipReference("骁龙 778G", 1000, 2500, 900),
        ChipReference("骁龙 765G", 850, 2100, 700),
        ChipReference("骁龙 6 Gen 3", 800, 2250, 720),
        ChipReference("骁龙 6 Gen 1", 780, 2200, 700),
        ChipReference("骁龙 695", 700, 1900, 620),
        ChipReference("骁龙 690", 660, 1750, 570),
        ChipReference("骁龙 4 Gen 2", 550, 1500, 450),

        // 联发科
        ChipReference("天玑 9500", 3200, 8500, 4000),
        ChipReference("天玑 9400", 2900, 8000, 3800),
        ChipReference("天玑 9300", 2600, 7300, 3500),
        ChipReference("天玑 9200", 2200, 6100, 3000),
        ChipReference("天玑 9000", 1900, 5500, 2600),
        ChipReference("天玑 8400", 1700, 5000, 2200),
        ChipReference("天玑 8300", 1600, 4700, 2000),
        ChipReference("天玑 8200", 1400, 4200, 1700),
        ChipReference("天玑 8100", 1350, 4000, 1600),
        ChipReference("天玑 7300", 1050, 2900, 1000),
        ChipReference("天玑 7050", 1000, 2700, 950),
        ChipReference("天玑 1200", 1200, 3400, 1300),
        ChipReference("天玑 920", 900, 2400, 800),
        ChipReference("天玑 1080", 850, 2300, 750),
        ChipReference("天玑 720", 700, 1900, 600),
        ChipReference("Helio G99", 650, 1800, 500),

        // 三星
        ChipReference("Exynos 2500", 2500, 7200, 2800),
        ChipReference("Exynos 2400", 2300, 6800, 2600),
        ChipReference("Exynos 2200", 1500, 4000, 1800),
        ChipReference("Exynos 1480", 1300, 3600, 1300),
        ChipReference("Exynos 1380", 1150, 3200, 1100),
        ChipReference("Exynos 990", 1050, 2900, 1200),
        ChipReference("Exynos 9825", 1000, 2700, 1100),

        // 华为
        ChipReference("麒麟 9020", 1800, 4800, 1900),
        ChipReference("麒麟 9010", 1650, 4500, 1800),
        ChipReference("麒麟 9000S", 1500, 4200, 1700),
        ChipReference("麒麟 9000", 1300, 3600, 1600),
        ChipReference("麒麟 990", 1050, 2900, 1200),
        ChipReference("麒麟 985", 950, 2600, 1000),
        ChipReference("麒麟 980", 900, 2500, 900),

        // Google / 小米 / 紫光展锐
        ChipReference("Tensor G5", 1700, 4600, 1800),
        ChipReference("Tensor G4", 1600, 4300, 1700),
        ChipReference("Tensor G3", 1500, 4000, 1600),
        ChipReference("Tensor G2", 1300, 3400, 1300),
        ChipReference("玄戒 O1", 2300, 6800, 2900),
        ChipReference("紫光展锐 T820", 1000, 2600, 900),
        ChipReference("紫光展锐 T618", 600, 1600, 500)
    )

    /**
     * 依据本机识别出的芯片名找参考项。
     *
     * 规则：参考名必须是本机芯片名的前缀（允许「骁龙 778G」匹配「骁龙 778G / 778G+」），
     * 并在多个候选里取最长的那个，避免「骁龙 8 Elite」被更长的「骁龙 8 Elite Gen 5」抢走。
     */
    fun match(chipName: String?): ChipReference? {
        val normalized = chipName?.trim().orEmpty()
        if (normalized.isEmpty()) return null
        return all
            .filter { normalized.startsWith(it.name, ignoreCase = true) }
            .maxByOrNull { it.name.length }
    }

    /** 参考分数相对本机的倍数，例如 2.1 表示「约为本机 2.1 倍」。 */
    fun ratio(reference: Int, device: Int): Double =
        if (device <= 0) 0.0 else reference.toDouble() / device

    /** 用于条形图的百分比（0~100）。 */
    fun percentOfMax(score: Int, max: Int): Int =
        if (max <= 0) 0 else (score * 100.0 / max).roundToInt().coerceIn(0, 100)
}
