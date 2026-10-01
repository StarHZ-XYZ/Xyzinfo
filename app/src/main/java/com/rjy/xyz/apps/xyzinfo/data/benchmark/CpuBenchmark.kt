package com.rjy.xyz.apps.xyzinfo.data.benchmark

import android.util.Log
import com.rjy.xyz.apps.xyzinfo.model.BenchmarkStage
import java.util.Arrays
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.Deflater
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * CPU 跑分（v0.7 重写，1.0.1 修偏 + 加压）。
 *
 * 1. **按时间跑，不按次数跑**：每个阶段跑满固定秒数（标准模式合计约 80 秒），
 *    慢机器不会被固定迭代次数拖成几分钟，快机器也不会因为采样窗口太短而抖；
 * 2. **负载更多样也更重**：整数 / 浮点 / 压缩 / 排序 / 位运算之外新增**内存带宽**阶段
 *    （大缓冲区流式拷贝，压内存控制器与缓存层次），矩阵、排序、压缩缓冲全部加大，
 *    让多核阶段真正撞上内存墙，而不是全在缓存里自娱自乐；
 * 3. **给出稳定性**：多核长跑期间每 250ms 采样一次，用后半程与前半程的吞吐比
 *    反映降频程度，而不是只丢一个孤零零的分数。
 *
 * ## 1.0.1 修掉的「第一次跑分偏低」
 *
 * 老实现有两个坑：
 *
 * 1. 只预热了单核的三条路径，**多核那几条路径是第一次在正式测量阶段才被 JIT / OSR 编译**的，
 *    于是第一次跑的时候，多核阶段前半段其实在"边编译边跑"；
 * 2. 每个阶段把**准备动作**（分配缓冲区、首次触页、首次调用 Deflater、建矩阵）也算进了计时窗口，
 *    而这些开销第一次跑最贵；更糟的是速率按**名义秒数**换算，准备慢多少就少记多少工作量。
 *
 * 现在：**预热覆盖全部测量路径**（含多核），每个阶段的准备都放在计时开始之前，
 * 速率一律用**实测耗时**换算。结果就是第一次跑和第二次跑基本一致。
 */
object CpuBenchmark {

    /** 标定用的日志标签：把各阶段实测速率打到 logcat，换机器标定基准时看这里。 */
    private const val TAG = "XyzInfoBench"

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
        val memoryMb: Double,
        val multiOps: Double,
        val multiFlops: Double,
        val multiCompressMb: Double,
        val multiMemoryMb: Double,
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

    // ---------- 标定基准：骁龙 778G（Adreno 642L / 4×A78 + 4×A55）真机实测 ----------
    //
    // 这些数字是"指数 1000"的定义：本机（778G）实测速率填在这里，就等于 1000 分，
    // 乘上 SINGLE_SCALE / MULTI_SCALE 后落在 1010 / 2900 —— 与应用内参考榜单
    // 给「骁龙 778G」的 1010 / 2900 完全对齐，本机实测那一行才不会和榜单打架。

    private const val REF_INTEGER_OPS = 3.05e8
    private const val REF_FLOAT_FLOPS = 1.75e8
    private const val REF_COMPRESS_MB = 600.0
    private const val REF_SORT_ELEMENTS = 1.23e7
    private const val REF_CRYPTO_OPS = 2.00e8
    private const val REF_MEMORY_MB = 11000.0
    private const val REF_MULTI_OPS = 9.30e8
    private const val REF_MULTI_FLOPS = 7.75e8
    private const val REF_MULTI_COMPRESS_MB = 1530.0
    private const val REF_MULTI_MEMORY_MB = 10450.0

    /** 指数 1000（= 骁龙 778G）换算到 Geekbench 6 量级的比例。 */
    private const val SINGLE_SCALE = 1.01
    private const val MULTI_SCALE = 2.90

    private const val BATCH = 4096

    /** 单核矩阵：192³ ≈ 707 万次浮点运算/轮，工作集约 900KB。 */
    private const val MATRIX_SIZE = 192

    /** 多核矩阵：8 线程 × 3 × 128² × 8B ≈ 3.1MB，刻意超过本机 L3，压内存带宽。 */
    private const val MULTI_MATRIX_SIZE = 128

    /** 排序：25 万元素 × 2 个数组 ≈ 2MB。 */
    private const val SORT_SIZE = 250_000

    private const val COMPRESS_BYTES = 2 * 1024 * 1024
    private const val MULTI_COMPRESS_BYTES = 512 * 1024

    /**
     * 内存带宽：单核 8MB（读+写 16MB/轮），远超任何手机 L3。
     * 不取更大是因为这些缓冲区同时活在 Java 堆里，堆压力本身会拖慢后面的阶段。
     */
    private const val MEMORY_BYTES = 8 * 1024 * 1024

    /** 多核内存带宽：每线程 2MB，8 线程合计 16MB 工作集。 */
    private const val MULTI_MEMORY_BYTES = 2 * 1024 * 1024

    /** 防止 JIT 把循环优化掉。 */
    @Volatile
    private var blackhole = 0L

    /**
     * 每个阶段的**稳定期比例**：前 40% 只跑不计数，成绩取后 60%。
     *
     * 这是"第一次跑分偏低"的第三道保险，也是最关键的一道：即使预热把代码跑热了，
     * 每个阶段刚进测量窗口的那一小段仍然会偏慢 —— ART 的优化编译器在后台重编译热点方法、
     * CPU 频率也还在往上爬，而这两件事都要**真实时间**，跟预热跑了多久无关。
     * 干脆每段都先跑 40% 当稳定期，只统计后半段，这样第一次跑和第二次跑基本重合
     * （测速与跑分工具里的常规做法：丢掉 warm-up，只测稳态）。
     */
    private const val STAGE_SETTLE_FRACTION = 0.4

    /**
     * 通用稳态测量：跑满 [seconds]，只统计稳定期之后的工作量。
     *
     * @param round 一轮负载，返回这一轮完成的工作量（必须与耗时成正比）
     */
    private inline fun measureSteady(
        seconds: Double,
        noinline tick: ((Double) -> Unit)?,
        round: () -> Long
    ): Double {
        val start = System.nanoTime()
        val end = start + (seconds * 1e9).toLong()
        val settleAt = start + (seconds * STAGE_SETTLE_FRACTION * 1e9).toLong()
        var counting = false
        var countStart = start
        var work = 0L
        while (System.nanoTime() < end) {
            val now = System.nanoTime()
            if (!counting && now >= settleAt) {
                counting = true
                countStart = now
                work = 0
            }
            val produced = round()
            if (counting) work += produced
            tick?.invoke(fraction(start, seconds))
        }
        val counted = ((System.nanoTime() - countStart) / 1e9).coerceAtLeast(1e-3)
        return work / counted
    }

    /**
     * 把当前线程提到「前台关键」优先级。
     *
     * 不这么做的话，跑分线程会被系统随手丢到小核（A55）上：同一个浮点矩阵乘，
     * 落在大核能跑 455 Mflops/s，被调度到小核只剩 170 —— 同一台机器两次跑分差 2.7 倍，
     * 全是调度噪声。跑分工具提线程优先级是常规做法，这里只提到 -8（URGENT_DISPLAY），
     * 不去抢音频 / 系统线程的档位。
     */
    private fun prioritizeCurrentThread() {
        runCatching {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
        }
    }

    /** 阶段计划：标准模式合计约 100 秒（预热 12 秒已包含在内）。 */
    fun plan(deep: Boolean): List<Stage> {
        val s = if (deep) 2.6 else 1.0
        return listOf(
            Stage("预热", 12.0 * s, "把所有测量路径（含多核）都跑热，JIT 与升频都不计入成绩"),
            Stage("单核整数", 8.0 * s, "线性同余定点链，考察整数 ALU 与流水线"),
            Stage("单核浮点", 9.0 * s, "192×192 双精度矩阵乘，考察浮点吞吐"),
            Stage("单核压缩", 6.0 * s, "Deflater 熵编码 + 内存读写"),
            Stage("单核排序", 6.0 * s, "双轴快排 + 数组拷贝，考察访存与分支"),
            Stage("单核位运算", 6.0 * s, "xorshift 混合，考察依赖链延迟"),
            Stage("单核内存带宽", 5.0 * s, "8MB 缓冲区流式拷贝，考察内存控制器"),
            /*
             * 多核阶段故意跑得更久（18 / 14 / 8 / 8 秒）。
             *
             * 手机的性能调度有一段"持续负载"逻辑：刚开测时大核还在按需升频，跑上一两分钟
             * 才会稳定在高档位。多核负载对这件事最敏感（要同时吃满八个核），所以第一次跑分
             * 的多核成绩会明显低于第二次。把多核阶段拉长、并且只统计后 60%
             * （见 STAGE_SETTLE_FRACTION），第一次跑就能落在稳定值附近。
             */
            Stage("多核整数", 18.0 * s, "铺满全部核心，同时采样降频曲线"),
            Stage("多核浮点", 14.0 * s, "每线程独立矩阵乘（工作集超过 L3）"),
            Stage("多核压缩", 8.0 * s, "每线程独立压缩缓冲区"),
            Stage("多核内存带宽", 8.0 * s, "每线程独立缓冲区，抢内存控制器")
        )
    }

    fun totalSeconds(deep: Boolean): Double = plan(deep).sumOf { it.seconds }

    /** 跑完整套 CPU 测试；[onProgress] 可能在工作线程被调用。 */
    fun run(deep: Boolean = false, onProgress: (Progress) -> Unit = {}): Result {
        prioritizeCurrentThread()
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

        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 16)

        /*
         * 0) 预热：每一条测量路径都跑一遍（含多核），而且**跑到频率拉起来为止**。
         *
         * 这一步是"第一次跑分偏低"的解药，要解决两件事：
         * 1. 多核那几个线程里的循环以前从没被执行过，第一次正式测量时才被 JIT/OSR 编译，
         *    前半程等于在边编译边跑；
         * 2. 机器刚从待机状态进来时 CPU 还在低频，直接开测等于测了个"冷启动"。
         *    所以预热里放了一段 2.5 秒的多核长跑，先把大小核的调度与频率顶起来。
         */
        emit(0, 0.0)
        integerLoop(1.5, null)
        floatLoop(1.2, MATRIX_SIZE, null)
        compressLoop(1.0, COMPRESS_BYTES, null)
        sortLoop(0.8, null)
        cryptoLoop(0.8, null)
        memoryLoop(0.8, null)
        multiIntegerLoop(2.5, threads, null)
        multiFloatLoop(1.5, threads, null)
        multiCompressLoop(1.0, threads, null)
        multiMemoryLoop(1.0, threads, null)
        emit(0, 1.0)
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

        emit(6, 0.0)
        val memoryMb = memoryLoop(stages[6].seconds, ticker(6)) / 1e6
        elapsed += stages[6].seconds

        emit(7, 0.0)
        val multi = multiIntegerLoop(stages[7].seconds, threads, ticker(7))
        elapsed += stages[7].seconds

        emit(8, 0.0)
        val multiFlops = multiFloatLoop(stages[8].seconds, threads, ticker(8))
        elapsed += stages[8].seconds

        emit(9, 0.0)
        val multiCompressMb = multiCompressLoop(stages[9].seconds, threads, ticker(9)) / 1e6
        elapsed += stages[9].seconds

        emit(10, 0.0)
        val multiMemoryMb = multiMemoryLoop(stages[10].seconds, threads, ticker(10)) / 1e6
        elapsed += stages[10].seconds

        val scored = scores(
            integerOps = integerOps,
            floatFlops = floatFlops,
            compressMb = compressMb,
            sortElements = sortElements,
            cryptoOps = cryptoOps,
            memoryMb = memoryMb,
            multiOps = multi.opsPerSecond,
            multiFlops = multiFlops,
            multiCompressMb = multiCompressMb,
            multiMemoryMb = multiMemoryMb
        )

        Log.i(
            TAG,
            String.format(
                Locale.US,
                "实测速率 整数=%.0fMops/s 浮点=%.0fMflops/s 压缩=%.0fMB/s 排序=%.1f万元素/s " +
                    "位运算=%.0fMops/s 内存=%.0fMB/s | 多核整数=%.0fMops/s 多核浮点=%.0fMflops/s " +
                    "多核压缩=%.0fMB/s 多核内存=%.0fMB/s (%d 线程) | 指数 单核=%.0f 多核=%.0f " +
                    "→ 分数 单核=%d 多核=%d 稳定性=%d%%",
                integerOps / 1e6, floatFlops / 1e6, compressMb, sortElements / 1e4,
                cryptoOps / 1e6, memoryMb,
                multi.opsPerSecond / 1e6, multiFlops / 1e6, multiCompressMb, multiMemoryMb, threads,
                scored.singleIndex, scored.multiIndex,
                scored.single, scored.multi, multi.stabilityPercent
            )
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
            memoryMb = memoryMb,
            multiOps = multi.opsPerSecond,
            multiFlops = multiFlops,
            multiCompressMb = multiCompressMb,
            multiMemoryMb = multiMemoryMb,
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
                BenchmarkStage("单核内存带宽", fmt("%.0f MB/s", memoryMb), scored.details[5]),
                BenchmarkStage("多核整数", fmt("%.0f Mops/s（%d 线程）", multi.opsPerSecond / 1e6, threads), scored.details[6]),
                BenchmarkStage("多核浮点", fmt("%.0f Mflops/s", multiFlops / 1e6), scored.details[7]),
                BenchmarkStage("多核压缩", fmt("%.0f MB/s", multiCompressMb), scored.details[8]),
                BenchmarkStage("多核内存带宽", fmt("%.0f MB/s", multiMemoryMb), scored.details[9])
            )
        )
    }

    // ---------- 分数换算 ----------

    /**
     * 把实测速率换成同一刻度的分数。
     *
     * 单核 / 多核都取各项比值的**加权几何平均**（比值先夹到 0.2~5 倍），
     * 这样某一项因为实现差异偏得离谱时不会把总分带飞；最后再乘 [SINGLE_SCALE] /
     * [MULTI_SCALE]，让指数 1000（骁龙 778G）落在 1010 / 2900 —— 与参考榜单给
     * 「骁龙 778G」的 1010 / 2900 同一刻度。
     */
    fun scores(
        integerOps: Double,
        floatFlops: Double,
        compressMb: Double,
        sortElements: Double,
        cryptoOps: Double,
        memoryMb: Double,
        multiOps: Double,
        multiFlops: Double,
        multiCompressMb: Double,
        multiMemoryMb: Double
    ): Scores {
        val singleRates = listOf(
            integerOps / REF_INTEGER_OPS to 0.25,
            floatFlops / REF_FLOAT_FLOPS to 0.20,
            compressMb / REF_COMPRESS_MB to 0.15,
            sortElements / REF_SORT_ELEMENTS to 0.10,
            cryptoOps / REF_CRYPTO_OPS to 0.10,
            memoryMb / REF_MEMORY_MB to 0.20
        )
        val multiRates = listOf(
            multiOps / REF_MULTI_OPS to 0.30,
            multiFlops / REF_MULTI_FLOPS to 0.25,
            multiCompressMb / REF_MULTI_COMPRESS_MB to 0.15,
            multiMemoryMb / REF_MULTI_MEMORY_MB to 0.30
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

    // ---------- 计时工具 ----------

    private fun fraction(startNanos: Long, seconds: Double): Double =
        ((System.nanoTime() - startNanos) / (seconds * 1e9)).coerceIn(0.0, 1.0)

    // ---------- 单核负载 ----------

    /** 整数：线性同余链 + 位混洗。 */
    private fun integerLoop(seconds: Double, tick: ((Double) -> Unit)?): Double {
        var state = 0x2545F4914F6CDD1DL
        var acc = 0L
        val rate = measureSteady(seconds, tick) {
            for (i in 0 until BATCH) {
                state = state * 6364136223846793005L + 1442695040888963407L
                acc += (state ushr 33) xor (state shr 7)
            }
            BATCH.toLong()
        }
        blackhole = acc
        return rate
    }

    /** 位运算：xorshift64* 依赖链（考察乱序执行深度）。 */
    private fun cryptoLoop(seconds: Double, tick: ((Double) -> Unit)?): Double {
        var x = 0x9E3779B97F4A7C15UL.toLong()
        val rate = measureSteady(seconds, tick) {
            for (i in 0 until BATCH) {
                x = x xor (x shl 13)
                x = x xor (x ushr 7)
                x = x xor (x shl 17)
                x *= 0x2545F4914F6CDD1DL
            }
            BATCH.toLong()
        }
        blackhole = x
        return rate
    }

    /** 浮点：双精度矩阵乘，计数按 size³。矩阵在计时前建好，不算进成绩。 */
    private fun floatLoop(seconds: Double, size: Int, tick: ((Double) -> Unit)?): Double {
        val left = Array(size) { r -> DoubleArray(size) { c -> ((r * 31 + c * 17) % 100) / 10.0 } }
        val right = Array(size) { r -> DoubleArray(size) { c -> ((r * 13 + c * 7) % 100) / 10.0 } }
        val result = Array(size) { DoubleArray(size) }
        var sink = 0.0
        val rate = measureSteady(seconds, tick) {
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
            size.toLong() * size * size
        }
        blackhole = sink.toRawBits()
        return rate
    }

    /**
     * 压缩：Deflater + 缓冲区读写，统计的是**输入吞吐**（MB/s）。
     *
     * 老实现统计的是 deflate 写出的字节数，而这个数既取决于数据好不好压、又取决于
     * 一次调用能不能把缓冲区写满 —— 换个机型差异极大，分数也就飘。改成「每秒处理多少输入」
     * 之后口径固定，跨机型才可比。
     */
    private fun compressLoop(seconds: Double, bytes: Int, tick: ((Double) -> Unit)?): Double {
        val input = ByteArray(bytes) { index -> ((index * 31 + index / 7) % 251).toByte() }
        // 输出留 2% 余量：即使遇到完全压不动的内容，也不会把 deflate 卡住
        val output = ByteArray(bytes + bytes / 50 + 64)
        val rate = measureSteady(seconds, tick) {
            val deflater = Deflater(Deflater.BEST_SPEED)
            deflater.setInput(input)
            deflater.finish()
            // 要取到 finished 为止：只调一次的话缓冲区没写满就返回，等于少算了工作量
            var guard = 0
            while (!deflater.finished() && guard < 64) {
                deflater.deflate(output)
                guard++
            }
            deflater.end()
            bytes.toLong()
        }
        blackhole = bytes.toLong()
        return rate
    }

    /** 排序：数组拷贝 + 双轴快排，考察访存与分支预测。 */
    private fun sortLoop(seconds: Double, tick: ((Double) -> Unit)?): Double {
        var seed = 12345
        val source = IntArray(SORT_SIZE) {
            seed = seed * 1103515245 + 12345
            seed ushr 8
        }
        val buffer = IntArray(SORT_SIZE)
        return measureSteady(seconds, tick) {
            source.copyInto(buffer)
            Arrays.sort(buffer)
            blackhole = (buffer[0] + buffer[SORT_SIZE / 2]).toLong()
            SORT_SIZE.toLong()
        }
    }

    /**
     * 内存带宽：大缓冲区流式拷贝。
     *
     * 16MB 的源 + 16MB 的目标，远超手机 L3（通常 2~4MB），所以这轮压的是内存控制器
     * 与内存颗粒本身。System.arraycopy 在 ART 里是 intrinsic（NEON 向量化），
     * 拿它当带宽探针比手写循环稳得多。
     */
    private fun memoryLoop(seconds: Double, tick: ((Double) -> Unit)?): Double {
        val size = MEMORY_BYTES
        val source = ByteArray(size)
        val target = ByteArray(size)
        for (index in 0 until size step 4096) source[index] = (index % 251).toByte()
        val rate = measureSteady(seconds, tick) {
            System.arraycopy(source, 0, target, 0, size)
            // 一读一写，算两倍流量
            size.toLong() * 2
        }
        blackhole = target[size / 2].toLong()
        return rate
    }

    // ---------- 多核负载 ----------

    private data class MultiResult(val opsPerSecond: Double, val stabilityPercent: Int)

    /**
     * 多核阶段统一入口：**先让所有线程把缓冲区准备好，再放闸门开始计时**。
     *
     * 这样"分配内存 + 首次触页 + 首次调用本地库"这些一次性的开销不会落进计时窗口 ——
     * 它正是老实现里第一次跑分偏低的主要来源之一。
     *
     * 每个线程还各自记录"自己真正跑了多久"，最终吞吐按**每线程自己的速率相加**，
     * 这样即使某条线程被系统晚调度了几百毫秒，也不会把整段成绩拖低。
     *
     * @param worker 返回该线程完成的工作量（单位由调用方决定，必须与耗时成正比）
     * @return 全部线程的吞吐之和（单位 / 秒）
     */
    private fun multiPhase(
        seconds: Double,
        threads: Int,
        tick: ((Double) -> Unit)?,
        worker: (index: Int, countFrom: Long, endAt: Long) -> Long
    ): Double {
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        val endAt = AtomicLong(0L)
        val countFrom = AtomicLong(0L)
        val work = LongArray(threads)
        val list = (0 until threads).map { index ->
            Thread({
                prioritizeCurrentThread()
                ready.countDown()
                go.await()
                work[index] = worker(index, countFrom.get(), endAt.get())
            }, "xyzinfo-cpu-$index")
        }
        list.forEach { it.start() }
        // 等所有线程把缓冲区建好（最多等 5 秒，避免某台机器上卡死）
        ready.await(5, TimeUnit.SECONDS)

        val start = System.nanoTime()
        countFrom.set(start + (seconds * STAGE_SETTLE_FRACTION * 1e9).toLong())
        endAt.set(start + (seconds * 1e9).toLong())
        go.countDown()
        // 采样循环：顺便给界面报进度
        while (System.nanoTime() < endAt.get()) {
            Thread.sleep(200)
            tick?.invoke(fraction(start, seconds))
        }
        list.forEach { it.join() }

        // 只统计稳定期之后那段窗口，所以分母就是固定的"后 60% 秒数"
        val countedSeconds = (seconds * (1.0 - STAGE_SETTLE_FRACTION)).coerceAtLeast(1e-3)
        return work.sum() / countedSeconds
    }

    /**
     * 多核整数：所有线程同时跑，另外每 250ms 记一次累计吞吐，
     * 用后 40% 与前 40% 的中位数比值作为稳定性。
     */
    private fun multiIntegerLoop(
        seconds: Double,
        threads: Int,
        tick: ((Double) -> Unit)?
    ): MultiResult {
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        val endAt = AtomicLong(0L)
        val countFrom = AtomicLong(0L)
        val counter = AtomicLong()
        val perThreadOps = LongArray(threads)
        val list = (0 until threads).map { index ->
            Thread({
                var state = 0x2545F4914F6CDD1DL + index * 0x9E3779B9L
                var acc = 0L
                var ops = 0L
                prioritizeCurrentThread()
                ready.countDown()
                go.await()
                val from = countFrom.get()
                val end = endAt.get()
                while (System.nanoTime() < end) {
                    for (i in 0 until BATCH) {
                        state = state * 6364136223846793005L + 1442695040888963407L
                        acc += (state ushr 33) xor (state shr 7)
                    }
                    // 稳定期只跑不计数（稳定性采样也跟着延后到稳定期之后）
                    if (System.nanoTime() >= from) {
                        ops += BATCH
                        counter.addAndGet(BATCH.toLong())
                    }
                }
                perThreadOps[index] = ops
                blackhole = acc
            }, "xyzinfo-cpu-$index")
        }
        list.forEach { it.start() }
        ready.await(5, TimeUnit.SECONDS)
        val start = System.nanoTime()
        countFrom.set(start + (seconds * STAGE_SETTLE_FRACTION * 1e9).toLong())
        endAt.set(start + (seconds * 1e9).toLong())
        go.countDown()

        // 每 250ms 记一次累计吞吐（×4 换算成"每秒"），用来算降频后的稳定性
        val samples = ArrayList<Long>(64)
        var previous = 0L
        var skippedSettleSample = false
        while (System.nanoTime() < endAt.get()) {
            Thread.sleep(250)
            val now = counter.get()
            val delta = now - previous
            previous = now
            // 稳定期刚结束的那个采样窗口是半截的，不能用（会显得"掉速"）
            if (skippedSettleSample) samples += delta * 4 else skippedSettleSample = true
            tick?.invoke(fraction(start, seconds))
        }
        list.forEach { it.join() }

        val countedSeconds = (seconds * (1.0 - STAGE_SETTLE_FRACTION)).coerceAtLeast(1e-3)
        return MultiResult(perThreadOps.sum() / countedSeconds, stability(samples))
    }

    private fun multiFloatLoop(seconds: Double, threads: Int, tick: ((Double) -> Unit)?): Double {
        return multiPhase(seconds, threads, tick) { _, countFrom, endAt ->
            val size = MULTI_MATRIX_SIZE
            val left = Array(size) { r -> DoubleArray(size) { c -> ((r * 31 + c * 17) % 100) / 10.0 } }
            val right = Array(size) { r -> DoubleArray(size) { c -> ((r * 13 + c * 7) % 100) / 10.0 } }
            val result = Array(size) { DoubleArray(size) }
            var sink = 0.0
            var rounds = 0L
            while (System.nanoTime() < endAt) {
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
                if (System.nanoTime() >= countFrom) rounds++
            }
            blackhole = sink.toRawBits()
            rounds * size.toLong() * size * size
        }
    }

    private fun multiCompressLoop(seconds: Double, threads: Int, tick: ((Double) -> Unit)?): Double {
        return multiPhase(seconds, threads, tick) { index, countFrom, endAt ->
            val input = ByteArray(MULTI_COMPRESS_BYTES) { i -> ((i * 31 + i / 7 + index) % 251).toByte() }
            val output = ByteArray(MULTI_COMPRESS_BYTES + MULTI_COMPRESS_BYTES / 50 + 64)
            var processed = 0L
            while (System.nanoTime() < endAt) {
                val deflater = Deflater(Deflater.BEST_SPEED)
                deflater.setInput(input)
                deflater.finish()
                var guard = 0
                while (!deflater.finished() && guard < 64) {
                    deflater.deflate(output)
                    guard++
                }
                deflater.end()
                if (System.nanoTime() >= countFrom) processed += MULTI_COMPRESS_BYTES
            }
            blackhole = processed
            processed
        }
    }

    private fun multiMemoryLoop(seconds: Double, threads: Int, tick: ((Double) -> Unit)?): Double {
        return multiPhase(seconds, threads, tick) { index, countFrom, endAt ->
            val size = MULTI_MEMORY_BYTES
            val source = ByteArray(size)
            val target = ByteArray(size)
            for (p in index until size step 4096) source[p] = (p % 251).toByte()
            var bytes = 0L
            while (System.nanoTime() < endAt) {
                System.arraycopy(source, 0, target, 0, size)
                if (System.nanoTime() >= countFrom) bytes += size.toLong() * 2
            }
            blackhole = target[size / 2].toLong()
            bytes
        }
    }

    /** 稳定性：后 40% 与前 40% 采样的中位数比值（100 = 完全不掉速）。 */
    private fun stability(samples: List<Long>): Int {
        if (samples.size < 6) return 100
        val headCount = (samples.size * 0.4).toInt().coerceAtLeast(1)
        val head = samples.take(headCount).sorted()
        val tail = samples.takeLast(headCount).sorted()
        val headMedian = head[head.size / 2]
        val tailMedian = tail[tail.size / 2]
        if (headMedian <= 0) return 100
        return ((tailMedian.toDouble() / headMedian) * 100).roundToInt().coerceIn(0, 130)
    }

    private fun fmt(pattern: String, vararg args: Any): String =
        String.format(Locale.US, pattern, *args)
}
