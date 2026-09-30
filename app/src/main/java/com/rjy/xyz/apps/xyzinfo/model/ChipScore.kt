package com.rjy.xyz.apps.xyzinfo.model

import kotlin.math.roundToInt

/**
 * 一款处理器在参考榜单里的成绩。
 *
 * 三个维度都是**同一套刻度**上的参考值，方便直接横向比较：
 * - [single] / [multi]：Geekbench 6 量级的单核 / 多核参考分；
 * - [gpu]：GPU 相对指数，以骁龙 778G = 1000 为基准。
 *
 * 数据整理自极客湾（Geekerwan）公开榜单，属于参考值而非官方成绩。
 */
data class ChipScore(
    val name: String,
    val brand: String,
    val single: Int,
    val multi: Int,
    val gpu: Int,
    val year: Int
) {
    /** 综合指数：以骁龙 778G = 1000 归一（单核 / 多核 / GPU 加权）。 */
    val composite: Int
        get() = (
            0.35 * single / SINGLE_ANCHOR +
                0.35 * multi / MULTI_ANCHOR +
                0.30 * gpu / GPU_ANCHOR
            ).times(1000.0).roundToInt()

    companion object {
        /** 归一锚点：骁龙 778G 的参考成绩。 */
        const val SINGLE_ANCHOR = 1010.0
        const val MULTI_ANCHOR = 2900.0
        const val GPU_ANCHOR = 1000.0
    }
}

/** 排行榜可选的排序维度。 */
enum class RankingMetric(val label: String) {
    COMPOSITE("综合"),
    SINGLE("单核"),
    MULTI("多核"),
    GPU("GPU");

    fun valueOf(score: ChipScore): Int = when (this) {
        COMPOSITE -> score.composite
        SINGLE -> score.single
        MULTI -> score.multi
        GPU -> score.gpu
    }
}
