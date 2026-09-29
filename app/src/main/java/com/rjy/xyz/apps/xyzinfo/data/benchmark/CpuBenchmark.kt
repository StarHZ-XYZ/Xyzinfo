package com.rjy.xyz.apps.xyzinfo.data.benchmark

import java.util.zip.Deflater
import kotlin.math.roundToInt

/**
 * CPU 跑分：整数、浮点、压缩三项单核成绩 + 全核多线程成绩。
 *
 * 分数按固定基准归一化（基准由真机实测校准），因此同一台设备重复跑分结果稳定，
 * 不同设备之间可以直接横向比较。
 */
object CpuBenchmark {

    // 每项负载都拉到 0.5~2 秒量级：压得更狠，同时分数抖动更小
    private const val INTEGER_ITERATIONS = 720_000_000
    private const val FLOAT_MATRIX_SIZE = 384
    private const val FLOAT_ROUNDS = 4
    private const val COMPRESS_ROUNDS = 200
    private const val COMPRESS_BYTES = 2 * 1024 * 1024
    private const val MULTI_THREAD_ITERATIONS = 2_000_000_000
    private const val MULTI_THREAD_ROUNDS = 3
    private const val WARMUP_ITERATIONS = 60_000_000

    // 归一化基准：按真机实测校准（Xiaomi Civi / 骁龙 778G），
    // 使该机单核 ≈ 1000、多核 ≈ 2500，与内置参考指数表对齐。
    private const val REFERENCE_INTEGER_OPS = 4.84e8
    private const val REFERENCE_FLOAT_FLOPS = 4.50e8
    private const val REFERENCE_COMPRESS_MB_PER_SECOND = 606.0
    private const val REFERENCE_MULTI_OPS = 1.108e9

    fun run(onProgress: (String) -> Unit): CpuResult {
        // 预热：先让 JIT 把热点方法编译掉，避免把编译时间算进成绩
        integerWork(WARMUP_ITERATIONS)
        floatWork(64, 1)
        compressWork(1)

        onProgress("单核整数运算")
        val integerOps = measureRate(INTEGER_ITERATIONS.toDouble()) {
            integerWork(INTEGER_ITERATIONS)
        }

        onProgress("单核浮点运算")
        val matrixOperations =
            FLOAT_MATRIX_SIZE.toDouble() * FLOAT_MATRIX_SIZE * FLOAT_MATRIX_SIZE * FLOAT_ROUNDS
        val floatFlops = measureRate(matrixOperations) {
            floatWork(FLOAT_MATRIX_SIZE, FLOAT_ROUNDS)
        }

        onProgress("压缩运算")
        val compressBytesTotal = COMPRESS_BYTES.toDouble() * COMPRESS_ROUNDS
        val compressMbPerSecond = measureRate(compressBytesTotal) {
            compressWork(COMPRESS_ROUNDS)
        } / 1024 / 1024

        onProgress("多核并行运算")
        val multiOps = measureMultiThreadRate()

        val (singleScore, multiScore) =
            scores(integerOps, floatFlops, compressMbPerSecond, multiOps)

        return CpuResult(
            singleScore = singleScore,
            multiScore = multiScore,
            integerOpsPerSecond = integerOps,
            floatFlopsPerSecond = floatFlops,
            compressMbPerSecond = compressMbPerSecond,
            multiOpsPerSecond = multiOps,
            cores = Runtime.getRuntime().availableProcessors()
        )
    }

    /**
     * 把实测速率换算成分数，纯函数，便于单元测试。
     *
     * 单核分数取三项的平均，避免某一项（例如压缩）受系统库实现影响过大。
     */
    fun scores(
        integerOpsPerSecond: Double,
        floatFlopsPerSecond: Double,
        compressMbPerSecond: Double,
        multiOpsPerSecond: Double
    ): Pair<Int, Int> {
        val integerScore = integerOpsPerSecond / REFERENCE_INTEGER_OPS * 1000
        val floatScore = floatFlopsPerSecond / REFERENCE_FLOAT_FLOPS * 1000
        val compressScore = compressMbPerSecond / REFERENCE_COMPRESS_MB_PER_SECOND * 1000
        val single = listOf(integerScore, floatScore, compressScore).average().roundToInt()
        val multi = (multiOpsPerSecond / REFERENCE_MULTI_OPS * 1000).roundToInt()
        return single to multi
    }

    /** 运行 [block] 并返回「单位数 / 秒」。 */
    private fun measureRate(units: Double, block: () -> Unit): Double {
        val start = System.nanoTime()
        block()
        val seconds = (System.nanoTime() - start) / 1_000_000_000.0
        return if (seconds > 0) units / seconds else 0.0
    }

    /** 线性同余迭代：整数运算为主，结果参与累加避免被优化掉。 */
    private fun integerWork(iterations: Int): Long {
        var state = 123456789L
        var accumulator = 0L
        repeat(iterations) {
            state = state * 6364136223846793005L + 1442695040888963407L
            accumulator += (state ushr 33) xor (state shr 7)
        }
        return accumulator
    }

    /** 矩阵乘法：浮点乘加，典型 FP 负载。 */
    private fun floatWork(size: Int, rounds: Int = 1): Double {
        val left = Array(size) { row -> DoubleArray(size) { col -> ((row * 31 + col * 17) % 100) / 10.0 } }
        val right = Array(size) { row -> DoubleArray(size) { col -> ((row * 13 + col * 7) % 100) / 10.0 } }
        val result = Array(size) { DoubleArray(size) }

        var sum = 0.0
        repeat(rounds) {
            for (row in 0 until size) {
                val leftRow = left[row]
                val resultRow = result[row]
                for (k in 0 until size) {
                    val factor = leftRow[k]
                    val rightRow = right[k]
                    for (col in 0 until size) {
                        resultRow[col] += factor * rightRow[col]
                    }
                }
            }
            sum += result[0][0]
        }
        return sum
    }

    /**
     * 压缩：真实混合负载（内存 + 分支 + 熵编码）。
     *
     * 缓冲区只分配一次，重复压缩若干轮，避免把 GC 时间算进成绩。
     */
    private fun compressWork(rounds: Int): Int {
        val input = ByteArray(COMPRESS_BYTES) { index -> ((index * 31 + index / 7) % 251).toByte() }
        val output = ByteArray(COMPRESS_BYTES)
        var totalWritten = 0

        repeat(rounds) {
            val deflater = Deflater(Deflater.BEST_SPEED)
            deflater.setInput(input)
            deflater.finish()
            totalWritten += deflater.deflate(output)
            deflater.end()
        }
        return totalWritten
    }

    /** 所有核心各跑一段整数运算，返回总运算量 / 秒。 */
    private fun measureMultiThreadRate(): Double {
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        val iterationsPerThread = (MULTI_THREAD_ITERATIONS / threads).coerceAtLeast(1)

        val start = System.nanoTime()
        repeat(MULTI_THREAD_ROUNDS) {
            val workers = (0 until threads).map { Thread { integerWork(iterationsPerThread) } }
            workers.forEach { it.start() }
            workers.forEach { it.join() }
        }
        val seconds = (System.nanoTime() - start) / 1_000_000_000.0

        val totalOperations = iterationsPerThread.toDouble() * threads * MULTI_THREAD_ROUNDS
        return if (seconds > 0) totalOperations / seconds else 0.0
    }
}

/** CPU 跑分结果（分数 + 原始指标）。 */
data class CpuResult(
    val singleScore: Int,
    val multiScore: Int,
    val integerOpsPerSecond: Double,
    val floatFlopsPerSecond: Double,
    val compressMbPerSecond: Double,
    val multiOpsPerSecond: Double,
    val cores: Int
)
