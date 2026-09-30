package com.rjy.xyz.apps.xyzinfo.data.benchmark

import com.rjy.xyz.apps.xyzinfo.model.BenchmarkStage
import java.util.Arrays
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.Deflater
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * CPU 跑分（v0.7 全面重写）。
 *
 * 相比 0.5 版：
 * 1. **按时间跑，不按次数跑**：每个阶段跑满固定秒数（合计 60 秒以上），
 *    慢机器不会被固定迭代次数拖成几分钟，快机器也不会因为采样窗口太短而抖；
 * 2. **负载更多样**：整数 / 浮点 / 压缩之外新增排序（访存 + 分支）与位运算混合，
 *    更接近真实使用场景；
 * 3. **给出稳定性**：多核长跑期间每 250ms 采样一次，用后半程与前半程的吞吐比
 *    反映降频程度，而不是只丢一个孤零零的分数。
 */
object CpuBenchmark {

    data class Stage(val name: String, val seconds: Double, val detail: String)

    data class Progress(
        val stageName: String,
        val stageIndex: Int,
        val stageCount: Int,
        val percent: Int,
        val elapsedSeconds: Double,
        val totalSeconds: Double,
        val live: String
    )

    data class Result(
        val singleScore: Int,
        val multiScore: Int,
        val singleIndex: Double,
        val multiIndex: Double,
        val integerOps: Double,
        val floatFlops: Double,
        val compressMb: Double,
        val sortElements: Double,
        val cryptoOps: Double,
        val multiOps: Double,
        val multiFlops: Double,
        val multiCompressMb: Double,
        val cores: Int,
        val threads: Int,
        val stabilityPercent: Int,
        val multiSpeedup: Double,
        val totalSeconds: Double,
        val stages: List<BenchmarkStage>
    )

    /** 纯函数换算结果，便于单元测试。 */
    data class Scores(
        val single: Int,
        val multi: Int,
        val singleIndex: Double,
        val multiIndex: Double,
        val details: List<Int>
    )

    // ---------- 标定基准（骁龙 778G 真机实测 / 同架构估算） ----------

    private const val REF_INTEGER_OPS = 4.84e8
    private const val REF_FLOAT_FLOPS = 4.50e8
    private const val REF_COMPRESS_MB = 606.0
    private const val REF_SORT_ELEMENTS = 2.60e7
    private const val REF_CRYPTO_OPS = 5.50e8
    private const val REF_MULTI_OPS = 2.77e9
    private const val REF_MULTI_FLOPS = 2.50e9
    private const val REF_MULTI_COMPRESS_MB = 2.90e3

    /** 指数 1000（= 骁龙 778G）换算到 Geekbench 6 量级的比例。 */
    private const val SINGLE_SCALE = 1.01
    private const val MULTI_SCALE = 2.90

    private const val BATCH = 4096
    private const val MATRIX_SIZE = 160
    private const val MULTI_MATRIX_SIZE = 96
    private const val SORT_SIZE = 120_000
    private const val COMPRESS_BYTES = 2 * 1024 * 1024
    private const val MULTI_COMPRESS_BYTES = 512 * 1024

    /** 防止 JIT 把循环优化掉。 */
    @Volatile
    private var blackhole = 0L

    /** 阶段计划：标准模式合计约 65 秒。 */
    fun plan(deep: Boolean): List<Stage> {
        val s = if (deep) 2.6 else 1.0
        return listOf(
            Stage("预热", 2.5 * s, "让 JIT 编译热点代码，避免把编译时间算进成绩"),
            Stage("单核整数", 8.0 * s, "线性同余定点链，考察整数 ALU 与流水线"),
            Stage("单核浮点", 9.0 * s, "160×160 矩阵乘，考察浮点吞吐"),
            Stage("单核压缩", 6.0 * s, "Deflater 熵编码 + 内存拷贝"),
            Stage("单核排序", 6.0 * s, "双轴快排 + 数组拷贝，考察访存与分支"),
            Stage("单核位运算", 6.0 * s, "xorshift 混合，考察依赖链延迟"),
            Stage("多核整数", 12.0 * s, "铺满全部核心，同时采样降频曲线"),
            Stage("多核浮点", 10.0 * s, "每线程独立矩阵乘"),
            Stage("多核压缩", 6.0 * s, "每线程独立压缩缓冲区")
        )
    }

    fun totalSeconds(deep: Boolean): Double = plan(deep).sumOf { it.seconds }

    /** 跑完整套 CPU 测试；[onProgress] 可能在工作线程被调用。 */
    fun run(deep: Boolean = false, onProgress: (Progress) -> Unit = {}): Result {
        val stages = plan(deep)
        val total = stages.sumOf { it.seconds }
        var elapsed = 0.0
        var lastTick = -10.0

        fun emit(index: Int, fraction: Double) {
            if (fraction < 1.0 && fraction - lastTick < 0.25) return
            lastTick = fraction
            val done = elapsed + fraction * stages[index].seconds
            onProgress(
                Progress(
                    stageName = stages[index].name,
                    stageIndex = index,
                    stageCount = stages.size,
                    percent = ((done / total) * 100).roundToInt().coerceIn(0, 100),
                    elapsedSeconds = done,
                    totalSeconds = total,
                    live = stages[index].detail
                )
            )
        }

        fun ticker(index: Int): (Double) -> Unit = { fraction -> emit(index, fraction) }

        // 0) 预热
        emit(0, 0.0)
        integerLoop(1.2, null)
        floatLoop(0.7, MATRIX_SIZE, null)
        compressLoop(0.6, COMPRESS_BYTES, null)
        elapsed += stages[0].seconds

        emit(1, 0.0)
        val integerOps = integerLoop(stages[1].seconds, ticker(1))
        elapsed += stages[1].seconds

        emit(2, 0.0)
        val floatFlops = floatLoop(stages[2].seconds, MATRIX_SIZE, ticker(2))
        elapsed += stages[2].seconds

        emit(3, 0.0)
        val compressMb = compressLoop(stages[3].seconds, COMPRESS_BYTES, ticker(3)) / 1e6
        elapsed += stages[3].seconds

        emit(4, 0.0)
        val sortElements = sortLoop(stages[4].seconds, ticker(4))
        elapsed += stages[4].seconds

        emit(5, 0.0)
        val cryptoOps = cryptoLoop(stages[5].seconds, ticker(5))
        elapsed += stages[5].seconds

        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 16)

        emit(6, 0.0)
        val multi = multiIntegerLoop(stages[6].seconds, threads, ticker(6))
        elapsed += stages[6].seconds

        emit(7, 0.0)
        val multiFlops = multiFloatLoop(stages[7].seconds, threads, ticker(7))
        elapsed += stages[7].seconds

        emit(8, 0.0)
        val multiCompressMb = multiCompressLoop(stages[8].seconds, threads, ticker(8)) / 1e6
        elapsed += stages[8].seconds

        val scored = scores(
            integerOps = integerOps,
            floatFlops = floatFlops,
            compressMb = compressMb,
            sortElements = sortElements,
            cryptoOps = cryptoOps,
            multiOps = multi.opsPerSecond,
            multiFlops = multiFlops,
            multiCompressMb = multiCompressMb
        )

        onProgress(
            Progress("完成", stages.size, stages.size, 100, total, total, "CPU 测试完成")
        )

        return Result(
            singleScore = scored.single,
            multiScore = scored.multi,
            singleIndex = scored.singleIndex,
            multiIndex = scored.multiIndex,
            integerOps = integerOps,
            floatFlops = floatFlops,
            compressMb = compressMb,
            sortElements = sortElements,
            cryptoOps = cryptoOps,
            multiOps = multi.opsPerSecond,
            multiFlops = multiFlops,
            multiCompressMb = multiCompressMb,
            cores = Runtime.getRuntime().availableProcessors(),
            threads = threads,
            stabilityPercent = multi.stabilityPercent,
            multiSpeedup = if (scored.single > 0) scored.multi.toDouble() / scored.single else 0.0,
            totalSeconds = total,
            stages = listOf(
                BenchmarkStage("单核整数", fmt("%.0f Mops/s", integerOps / 1e6), scored.details[0]),
                BenchmarkStage("单核浮点", fmt("%.0f Mflops/s", floatFlops / 1e6), scored.details[1]),
                BenchmarkStage("单核压缩", fmt("%.0f MB/s", compressMb), scored.details[2]),
                BenchmarkStage("单核排序", fmt("%.1f 万元素/s", sortElements / 1e4), scored.details[3]),
                BenchmarkStage("单核位运算", fmt("%.0f Mops/s", cryptoOps / 1e6), scored.details[4]),
                BenchmarkStage("多核整数", fmt("%.0f Mops/s（%d 线程）", multi.opsPerSecond / 1e6, threads), scored.details[5]),
                BenchmarkStage("多核浮点", fmt("%.0f Mflops/s", multiFlops / 1e6), scored.details[6]),
                BenchmarkStage("多核压缩", fmt("%.0f MB/s", multiCompressMb), scored.details[7])
            )
        )
    }

    // ---------- 分数换算 ----------

    /**
     * 把实测速率换成同一刻度的分数。
     *
     * 单核 / 多核都取各项比值的**加权几何平均**（比值先夹到 0.2~5 倍），
     * 这样某一项因为实现差异偏得离谱时不会把总分带飞；
     * 最后再乘 [SINGLE_SCALE] / [MULTI_SCALE]，让指数 1000（骁龙 778G）
     * 落在 Geekbench 6 量级的 1010 / 2900 附近。
     */
    fun scores(
        integerOps: Double,
        floatFlops: Double,
        compressMb: Double,
        sortElements: Double,
        cryptoOps: Double,
        multiOps: Double,
        multiFlops: Double,
        multiCompressMb: Double
    ): Scores {
        val singleRates = listOf(
            integerOps / REF_INTEGER_OPS to 0.30,
            floatFlops / REF_FLOAT_FLOPS to 0.25,
            compressMb / REF_COMPRESS_MB to 0.20,
            sortElements / REF_SORT_ELEMENTS to 0.15,
            cryptoOps / REF_CRYPTO_OPS to 0.10
        )
        val multiRates = listOf(
            multiOps / REF_MULTI_OPS to 0.40,
            multiFlops / REF_MULTI_FLOPS to 0.35,
            multiCompressMb / REF_MULTI_COMPRESS_MB to 0.25
        )

        // 几何平均给出的是「相对基准的倍数」，乘 1000 换成指数（基准机 = 1000）
        val singleIndex = weightedGeometricMean(singleRates) * 1000
        val multiIndex = weightedGeometricMean(multiRates) * 1000

        return Scores(
            single = (singleIndex * SINGLE_SCALE).roundToInt(),
            multi = (multiIndex * MULTI_SCALE).roundToInt(),
            singleIndex = singleIndex,
            multiIndex = multiIndex,
            details = (singleRates + multiRates).map { (it.first * 1000).roundToInt() }
        )
    }

    private fun weightedGeometricMean(rates: List<Pair<Double, Double>>): Double {
        if (rates.any { it.first <= 0.0 || !it.first.isFinite() }) return 0.0
        var weighted = 0.0
        var weight = 0.0
        rates.forEach { (ratio, w) ->
            weighted += w * ln(ratio.coerceIn(0.2, 5.0))
            weight += w
        }
        return exp(weighted / weight)
    }

    // ---------- 各阶段负载 ----------

    private fun deadline(seconds: Double) = System.nanoTime() + (seconds * 1e9).toLong()

    private fun fraction(startNanos: Long, seconds: Double): Double =
        ((System.nanoTime() - startNanos) / (seconds * 1e9)).coerceIn(0.0, 1.0)

    /** 整数：线性同余链 + 位混洗。 */
    private fun integerLoop(seconds: Double, tick: ((Double) -> Unit)?): Double {
        val end = deadline(seconds)
        val start = System.nanoTime()
        var state = 0x2545F4914F6CDD1DL
        var acc = 0L
        var ops = 0L
        while (System.nanoTime() < end) {
            for (i in 0 until BATCH) {
                state = state * 6364136223846793005L + 1442695040888963407L
                acc += (state ushr 33) xor (state shr 7)
            }
            ops += BATCH
            tick?.invoke(fraction(start, seconds))
        }
        blackhole = acc
        return ops.toDouble() / seconds
    }

    /** 位运算：xorshift64* 依赖链。 */
    private fun cryptoLoop(seconds: Double, tick: ((Double) -> Unit)?): Double {
        val end = deadline(seconds)
        val start = System.nanoTime()
        var x = 0x9E3779B97F4A7C15UL.toLong()
        var ops = 0L
        while (System.nanoTime() < end) {
            for (i in 0 until BATCH) {
                x = x xor (x shl 13)
                x = x xor (x ushr 7)
                x = x xor (x shl 17)
                x *= 0x2545F4914F6CDD1DL
            }
            ops += BATCH
            tick?.invoke(fraction(start, seconds))
        }
        blackhole = x
        return ops.toDouble() / seconds
    }

    /** 浮点：矩阵乘。计数方式与 0.5 版一致（size³），便于沿用标定值。 */
    private fun floatLoop(seconds: Double, size: Int, tick: ((Double) -> Unit)?): Double {
        val end = deadline(seconds)
        val start = System.nanoTime()
        val left = Array(size) { r -> DoubleArray(size) { c -> ((r * 31 + c * 17) % 100) / 10.0 } }
        val right = Array(size) { r -> DoubleArray(size) { c -> ((r * 13 + c * 7) % 100) / 10.0 } }
        val result = Array(size) { DoubleArray(size) }
        val perRound = size.toDouble() * size * size
        var rounds = 0L
        var sink = 0.0
        while (System.nanoTime() < end) {
            for (row in 0 until size) {
                val leftRow = left[row]
                val resultRow = result[row]
                for (k in 0 until size) {
                    val factor = leftRow[k]
                    val rightRow = right[k]
                    for (col in 0 until size) resultRow[col] += factor * rightRow[col]
                }
            }
            sink += result[0][0]
            rounds++
            tick?.invoke(fraction(start, seconds))
        }
        blackhole = sink.toRawBits()
        return rounds * perRound / seconds
    }

    /** 压缩：Deflater + 缓冲区读写。 */
    private fun compressLoop(seconds: Double, bytes: Int, tick: ((Double) -> Unit)?): Double {
        val end = deadline(seconds)
        val start = System.nanoTime()
        val input = ByteArray(bytes) { index -> ((index * 31 + index / 7) % 251).toByte() }
        val output = ByteArray(bytes)
        var written = 0L
        while (System.nanoTime() < end) {
            val deflater = Deflater(Deflater.BEST_SPEED)
            deflater.setInput(input)
            deflater.finish()
            written += deflater.deflate(output)
            deflater.end()
            tick?.invoke(fraction(start, seconds))
        }
        blackhole = written
        return written.toDouble() / seconds
    }

    /** 排序：数组拷贝 + 双轴快排，考察访存与分支预测。 */
    private fun sortLoop(seconds: Double, tick: ((Double) -> Unit)?): Double {
        val end = deadline(seconds)
        val start = System.nanoTime()
        var seed = 12345
        val source = IntArray(SORT_SIZE) {
            seed = seed * 1103515245 + 12345
            seed ushr 8
        }
        val buffer = IntArray(SORT_SIZE)
        var elements = 0L
        while (System.nanoTime() < end) {
            source.copyInto(buffer)
            Arrays.sort(buffer)
            blackhole = (buffer[0] + buffer[SORT_SIZE / 2]).toLong()
            elements += SORT_SIZE
            tick?.invoke(fraction(start, seconds))
        }
        return elements.toDouble() / seconds
    }

    // ---------- 多核 ----------

    private data class MultiResult(val opsPerSecond: Double, val stabilityPercent: Int)

    /**
     * 多核整数：所有线程同时跑，另外开一个采样循环，每 250ms 记一次累计吞吐，
     * 用后 40% 与前 40% 的中位数比值作为稳定性。
     */
    private fun multiIntegerLoop(
        seconds: Double,
        threads: Int,
        tick: ((Double) -> Unit)?
    ): MultiResult {
        val end = deadline(seconds)
        val start = System.nanoTime()
        val counter = AtomicLong()
        val workers = (0 until threads).map { index ->
            Thread({
                var state = 0x2545F4914F6CDD1DL + index * 0x9E3779B9L
                var acc = 0L
                while (System.nanoTime() < end) {
                    for (i in 0 until BATCH) {
                        state = state * 6364136223846793005L + 1442695040888963407L
                        acc += (state ushr 33) xor (state shr 7)
                    }
                    counter.addAndGet(BATCH.toLong())
                }
                blackhole = acc
            }, "xyzinfo-cpu-$index")
        }
        workers.forEach { it.start() }

        val samples = ArrayList<Long>(64)
        var previous = 0L
        while (System.nanoTime() < end) {
            Thread.sleep(250)
            val now = counter.get()
            samples += (now - previous) * 4
            previous = now
            tick?.invoke(fraction(start, seconds))
        }
        workers.forEach { it.join() }

        val totalOps = counter.get().toDouble()
        return MultiResult(totalOps / seconds, stability(samples))
    }

    private fun multiFloatLoop(seconds: Double, threads: Int, tick: ((Double) -> Unit)?): Double {
        val end = deadline(seconds)
        val start = System.nanoTime()
        val perThread = DoubleArray(threads)
        val workers = (0 until threads).map { index ->
            Thread({
                val size = MULTI_MATRIX_SIZE
                val left = Array(size) { r -> DoubleArray(size) { c -> ((r * 31 + c * 17) % 100) / 10.0 } }
                val right = Array(size) { r -> DoubleArray(size) { c -> ((r * 13 + c * 7) % 100) / 10.0 } }
                val result = Array(size) { DoubleArray(size) }
                var rounds = 0L
                var sink = 0.0
                while (System.nanoTime() < end) {
                    for (row in 0 until size) {
                        val leftRow = left[row]
                        val resultRow = result[row]
                        for (k in 0 until size) {
                            val factor = leftRow[k]
                            val rightRow = right[k]
                            for (col in 0 until size) resultRow[col] += factor * rightRow[col]
                        }
                    }
                    sink += result[0][0]
                    rounds++
                }
                perThread[index] = rounds.toDouble() * size * size * size
                blackhole = sink.toRawBits()
            }, "xyzinfo-fp-$index")
        }
        workers.forEach { it.start() }
        while (System.nanoTime() < end) {
            Thread.sleep(250)
            tick?.invoke(fraction(start, seconds))
        }
        workers.forEach { it.join() }
        return perThread.sum() / seconds
    }

    private fun multiCompressLoop(seconds: Double, threads: Int, tick: ((Double) -> Unit)?): Double {
        val end = deadline(seconds)
        val start = System.nanoTime()
        val perThread = DoubleArray(threads)
        val workers = (0 until threads).map { index ->
            Thread({
                val input = ByteArray(MULTI_COMPRESS_BYTES) { i -> ((i * 31 + i / 7 + index) % 251).toByte() }
                val output = ByteArray(MULTI_COMPRESS_BYTES)
                var written = 0L
                while (System.nanoTime() < end) {
                    val deflater = Deflater(Deflater.BEST_SPEED)
                    deflater.setInput(input)
                    deflater.finish()
                    written += deflater.deflate(output)
                    deflater.end()
                }
                perThread[index] = written.toDouble()
                blackhole = written
            }, "xyzinfo-zip-$index")
        }
        workers.forEach { it.start() }
        while (System.nanoTime() < end) {
            Thread.sleep(250)
            tick?.invoke(fraction(start, seconds))
        }
        workers.forEach { it.join() }
        return perThread.sum() / seconds
    }

    /** 稳定性：后 40% 与前 40% 采样的中位数比值（100 = 完全不掉速）。 */
    private fun stability(samples: List<Long>): Int {
        if (samples.size < 6) return 100
        val head = samples.take((samples.size * 0.4).toInt().coerceAtLeast(1)).sorted()
        val tail = samples.takeLast((samples.size * 0.4).toInt().coerceAtLeast(1)).sorted()
        val headMedian = head[head.size / 2]
        val tailMedian = tail[tail.size / 2]
        if (headMedian <= 0) return 100
        return ((tailMedian.toDouble() / headMedian) * 100).roundToInt().coerceIn(0, 130)
    }

    private fun fmt(pattern: String, vararg args: Any): String =
        String.format(Locale.US, pattern, *args)
}
