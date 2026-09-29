package com.rjy.xyz.apps.xyzinfo.model

/**
 * 参考机型分数（相对指数）。
 *
 * 以骁龙 778G 单核 = 1000 为基准，按公开跑分中各家芯片的相对比例折算，
 * 用于给本机成绩一个横向参照，**不是官方跑分**。
 */
data class ChipReference(
    val name: String,
    val cpuSingle: Int,
    val cpuMulti: Int,
    val gpu: Int
)

/** 本机跑分结果。 */
data class BenchmarkResult(
    val cpuSingleScore: Int,
    val cpuMultiScore: Int,
    val gpuScore: Int?,
    val cpuSingleDetail: String,
    val cpuMultiDetail: String,
    val gpuDetail: String?
)

/** GPU 跑分结果。 */
data class GpuResult(
    val score: Int,
    val framesPerSecond: Double,
    val renderer: String
)
