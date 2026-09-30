package com.rjy.xyz.apps.xyzinfo.data.benchmark

import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * 内存测试（0.8 新增）。
 *
 * 三项，都是真实内存子系统的关键指标：
 * 1. **顺序带宽**：32MB 缓冲区反复 memcpy + 顺序读，直接反映内存控制器吞吐（GB/s）；
 * 2. **随机访问延迟**：8M 个 int（32MB，远超缓存）做依赖链随机跳转，
 *    测的是 DRAM 的真实延迟（ns/次），这一项对流畅度的影响比带宽更大；
 * 3. **分配速率**：反复分配并部分填充 1MB 数组，反映分配器 + GC 的吞吐（MB/s）。
 *
 * 分数换算同样以骁龙 778G（LPDDR4X）为 1000 基准，与 CPU / GPU 分在同一刻度上。
 * 每项都按时间跑满，避免快慢机采样窗口不一致。
 */
object MemoryBenchmark {

    data class Result(
        val bandwidthGbs: Double,
        val latencyNs: Double,
        val allocMbs: Double,
        val score: Int,
        val detail: String
    )

    /** 标定基准：骁龙 778G + LPDDR4X 的典型值（真机量级估算）。 */
    private const val REF_BANDWIDTH_GBS = 6.0
    private const val REF_LATENCY_NS = 120.0
    private const val REF_ALLOC_MBS = 1200.0

    private const val SEQUENCE_BYTES = 32 * 1024 * 1024
    private const val RANDOM_ARRAY_INTS = 8 * 1024 * 1024
    private const val ALLOC_CHUNK = 1024 * 1024

    @Volatile
    private var blackhole = 0L

    /**
     * @param seconds 总时长，按 3:3:2 分配给带宽 / 延迟 / 分配
     * @param onProgress 已用秒数（可能在工作线程回调）
     */
    fun run(seconds: Double = 18.0, onProgress: (Double) -> Unit = {}): Result {
        val bandwidthSeconds = seconds * 3.0 / 8.0
        val latencySeconds = seconds * 3.0 / 8.0
        val allocSeconds = seconds * 2.0 / 8.0

        val bandwidth = measureBandwidth(bandwidthSeconds) { onProgress(it) }
        val latency = measureLatency(latencySeconds) { onProgress(bandwidthSeconds + it) }
        val alloc = measureAlloc(allocSeconds) { onProgress(bandwidthSeconds + latencySeconds + it) }
        onProgress(seconds)

        val score = score(bandwidth, latency, alloc)
        val detail = String.format(
            Locale.US,
            "内存顺序带宽：%.2f GB/s\n内存随机延迟：%.1f ns/次\n内存分配速率：%.0f MB/s\n内存指数：%d",
            bandwidth, latency, alloc, score
        )
        return Result(bandwidth, latency, alloc, score, detail)
    }

    /** 顺序带宽：拷贝 + 顺序读取，统计总搬运字节数。 */
    private fun measureBandwidth(seconds: Double, tick: (Double) -> Unit): Double {
        val size = SEQUENCE_BYTES
        val source = ByteArray(size)
        val target = ByteArray(size)
        // 预热 + 让页表就位
        System.arraycopy(source, 0, target, 0, size)

        val start = System.nanoTime()
        val deadline = start + (seconds * 1e9).toLong()
        var moved = 0L
        var checksum = 0L
        while (System.nanoTime() < deadline) {
            System.arraycopy(source, 0, target, 0, size)
            moved += size.toLong() * 2 // 读 + 写
            // 顺序读一遍（每 64 字节取一个样本，避免被优化掉又把 CPU 拖成瓶颈）
            var index = 0
            while (index < size) {
                checksum += target[index].toLong()
                index += 64
            }
            moved += size.toLong()
            tick(((System.nanoTime() - start) / 1e9).coerceAtMost(seconds))
            if (moved > 60L * 1024 * 1024 * 1024) break
        }
        blackhole = checksum
        val elapsed = (System.nanoTime() - start) / 1e9
        return if (elapsed > 0) moved / elapsed / 1e9 else 0.0
    }

    /** 随机访问延迟：依赖链随机跳转，一次访问一次命中，直接反映 DRAM 延迟。 */
    private fun measureLatency(seconds: Double, tick: (Double) -> Unit): Double {
        val size = RANDOM_ARRAY_INTS
        val mask = size - 1
        val array = IntArray(size)
        // 用固定步长填充成「随机跳转表」，保证访问链既随机又必然命中
        var seed = 12345
        for (index in 0 until size) {
            seed = seed * 1103515245 + 12345
            array[index] = (seed ushr 8) and mask
        }

        val start = System.nanoTime()
        val deadline = start + (seconds * 1e9).toLong()
        var cursor = 0
        var accesses = 0L
        while (System.nanoTime() < deadline) {
            // 一批 4096 次，降低时间检查开销
            for (step in 0 until 4096) {
                cursor = array[cursor]
            }
            accesses += 4096
            tick(((System.nanoTime() - start) / 1e9).coerceAtMost(seconds))
        }
        blackhole = cursor.toLong()
        val elapsed = (System.nanoTime() - start) / 1e9
        return if (accesses > 0) elapsed / accesses * 1e9 else 0.0
    }

    /** 分配速率：反复分配 1MB 并填充一部分，压分配器与 GC。 */
    private fun measureAlloc(seconds: Double, tick: (Double) -> Unit): Double {
        val start = System.nanoTime()
        val deadline = start + (seconds * 1e9).toLong()
        var allocated = 0L
        while (System.nanoTime() < deadline) {
            val chunk = ByteArray(ALLOC_CHUNK)
            // 只写一部分，模拟真实应用的「分配 + 局部使用」
            var index = 0
            while (index < ALLOC_CHUNK) {
                chunk[index] = (index and 0xFF).toByte()
                index += 512
            }
            allocated += ALLOC_CHUNK
            blackhole = chunk[0].toLong()
            tick(((System.nanoTime() - start) / 1e9).coerceAtMost(seconds))
        }
        val elapsed = (System.nanoTime() - start) / 1e9
        return if (elapsed > 0) allocated / elapsed / 1e6 else 0.0
    }

    /**
     * 三项比值做加权几何平均，延迟是「越小越好」，所以取它的倒数。
     * 顺序带宽 0.45、随机延迟 0.35、分配速率 0.20。
     */
    fun score(bandwidthGbs: Double, latencyNs: Double, allocMbs: Double): Int {
        if (bandwidthGbs <= 0 || latencyNs <= 0 || allocMbs <= 0) return 0
        val ratios = listOf(
            (bandwidthGbs / REF_BANDWIDTH_GBS) to 0.45,
            (REF_LATENCY_NS / latencyNs) to 0.35,
            (allocMbs / REF_ALLOC_MBS) to 0.20
        )
        var weighted = 0.0
        ratios.forEach { (ratio, weight) -> weighted += weight * ln(ratio.coerceIn(0.2, 5.0)) }
        return (exp(weighted) * 1000).roundToInt()
    }
}
