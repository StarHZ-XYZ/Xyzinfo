package com.rjy.xyz.apps.xyzinfo.model

/**
 * 本机跑分结果（v0.7 起带上了稳定性与各阶段明细）。
 *
 * 分数与 [ChipScore] 在同一刻度上：单核 / 多核是 Geekbench 6 量级，
 * GPU 是以骁龙 778G = 1000 为基准的相对指数，因此可以直接和排行榜比。
 */
data class BenchmarkResult(
    val cpuSingleScore: Int,
    val cpuMultiScore: Int,
    val gpuScore: Int?,
    val cpuSingleDetail: String,
    val cpuMultiDetail: String,
    val gpuDetail: String?,
    /** 稳定性：多核长跑「后半程 / 前半程」的吞吐比，100 表示完全不掉速。 */
    val stabilityPercent: Int,
    /** 本次跑分总耗时（秒）。 */
    val totalSeconds: Double,
    /** 每个阶段的明细，用于在界面上逐条展示。 */
    val stages: List<BenchmarkStage>
)

/** 单个测试阶段的成绩。 */
data class BenchmarkStage(
    val name: String,
    val detail: String,
    val score: Int
)

/** GPU 跑分结果。 */
data class GpuResult(
    val score: Int,
    val framesPerSecond: Double,
    /** 1% low：把最慢的 1% 帧单独拎出来算的帧率，反映卡顿。 */
    val lowFramesPerSecond: Double,
    val renderer: String,
    /** 实测像素填充率（Gpx/s）。 */
    val pixelRateGiga: Double,
    val frames: Int
)
