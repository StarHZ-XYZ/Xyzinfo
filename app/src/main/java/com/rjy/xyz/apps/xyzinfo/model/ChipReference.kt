package com.rjy.xyz.apps.xyzinfo.model

/**
 * 本机跑分结果（v0.7 起带上了稳定性与各阶段明细）。
 *
 * CPU 单核 / 多核是 **XYZ Performance Index**，标定机骁龙 778G = 1000；
 * GPU 是本应用图形负载的相对指数。CPU 不再冒充 Geekbench 6，也不会和
 * [ChipScore] 的外部参考分数混排。
 */
data class BenchmarkResult(
    val cpuSingleScore: Int,
    val cpuMultiScore: Int,
    val gpuScore: Int?,
    val cpuSingleDetail: String,
    val cpuMultiDetail: String,
    val gpuDetail: String?,
    /** 0.8 新增：内存测试明细。 */
    val memoryDetail: String? = null,
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
    val frames: Int,
    /** 这次用的是什么负载：「3D 引擎」或「2D 填充（3D 不可用时的退路）」。 */
    val mode: String = "2D 填充",
    /** 3D 模式下每帧渲染的通道数（自适应加重后的结果）。 */
    val passes: Int = 0,
    /** 3D 模式下每帧提交的三角形数量。 */
    val trianglesPerFrame: Int = 0
)
