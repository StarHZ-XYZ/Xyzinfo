package com.rjy.xyz.apps.xyzinfo.data.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CPU 分数归一化测试（分数与实测速率成正比，且对单项偏差不敏感）。 */
class CpuBenchmarkTest {

    /**
 * 默认入参就是标定基准本身（骁龙 778G），两个 XYZ 指数都必须正好是 1000。
     */
    private fun scores(
        integer: Double = 1.091e9,
        flops: Double = 5.98e8,
        compress: Double = 910.0,
        sort: Double = 1.5751e7,
        crypto: Double = 4.81e8,
        memory: Double = 10299.0,
        multiOps: Double = 4.558e9,
        multiFlops: Double = 4.297e9,
        multiCompress: Double = 3091.0,
        multiMemory: Double = 9348.0
    ) = CpuBenchmark.scores(
        integerOps = integer,
        floatFlops = flops,
        compressMb = compress,
        sortElements = sort,
        cryptoOps = crypto,
        memoryMb = memory,
        multiOps = multiOps,
        multiFlops = multiFlops,
        multiCompressMb = multiCompress,
        multiMemoryMb = multiMemory
    )

    @Test
    fun `标定基准对应骁龙 778G 的参考分`() {
        val result = scores()
        assertEquals(1000, result.single)
        assertEquals(1000, result.multi)
    }

    @Test
    fun `所有负载同比例提升时分数同比例提升`() {
        val base = scores()
        val doubled = scores(
            integer = 1.091e9 * 2,
            flops = 5.98e8 * 2,
            compress = 910.0 * 2,
            sort = 1.5751e7 * 2,
            crypto = 4.81e8 * 2,
            memory = 10299.0 * 2,
            multiOps = 4.558e9 * 2,
            multiFlops = 4.297e9 * 2,
            multiCompress = 3091.0 * 2,
            multiMemory = 9348.0 * 2
        )
        assertEquals(base.single * 2, doubled.single)
        assertEquals(base.multi * 2, doubled.multi)
    }

    @Test
    fun `单项异常不会把总分带飞`() {
        val outlier = scores(compress = 606.0 * 100)
        assertTrue(outlier.single > 1000)
        assertTrue(outlier.single < 1000 * 2)
    }

    @Test
    fun `速率为零时分数为零`() {
        val zero = scores(
            integer = 0.0, flops = 0.0, compress = 0.0, sort = 0.0, crypto = 0.0, memory = 0.0,
            multiOps = 0.0, multiFlops = 0.0, multiCompress = 0.0, multiMemory = 0.0
        )
        assertEquals(0, zero.single)
        assertEquals(0, zero.multi)
    }

    @Test
    fun `阶段计划合计不低于一分钟`() {
        assertTrue(CpuBenchmark.totalSeconds(deep = false) >= 60.0)
        assertTrue(CpuBenchmark.totalSeconds(deep = true) >= 150.0)
    }

    @Test
    fun `XYZ 指数不借用 Geekbench 固定倍率`() {
        val base = scores()
        assertEquals(CpuBenchmark.ANCHOR_SCORE.toInt(), base.single)
        assertEquals(CpuBenchmark.ANCHOR_SCORE.toInt(), base.multi)
        assertEquals(2, CpuBenchmark.INDEX_VERSION)
    }

    @Test
    fun `多核加速比使用同类负载真实吞吐`() {
        val speedup = CpuBenchmark.multiSpeedup(
            integerOps = 1.091e9,
            floatFlops = 5.98e8,
            compressMb = 910.0,
            memoryMb = 10299.0,
            multiOps = 4.495e9,
            multiFlops = 4.233e9,
            multiCompressMb = 3048.0,
            multiMemoryMb = 9290.0
        )
        assertTrue("778G 多核应有明显加速，实际 $speedup", speedup > 2.5)
        assertTrue("加速比不应被固定倍率夸大，实际 $speedup", speedup < 4.5)
    }

    @Test
    fun `更高吞吐的芯片组合保持代际顺序`() {
        val gen3 = scores(
            integer = 1.091e9 * 1.55,
            flops = 5.98e8 * 1.45,
            compress = 910.0 * 1.35,
            sort = 1.5751e7 * 1.40,
            crypto = 4.81e8 * 1.35,
            memory = 10299.0 * 1.45,
            multiOps = 4.558e9 * 2.50,
            multiFlops = 4.297e9 * 2.30,
            multiCompress = 3091.0 * 2.00,
            multiMemory = 9348.0 * 2.20
        )
        val gen5 = scores(
            integer = 1.091e9 * 2.15,
            flops = 5.98e8 * 2.00,
            compress = 910.0 * 1.85,
            sort = 1.5751e7 * 1.90,
            crypto = 4.81e8 * 1.90,
            memory = 10299.0 * 2.10,
            multiOps = 4.558e9 * 3.55,
            multiFlops = 4.297e9 * 3.35,
            multiCompress = 3091.0 * 2.90,
            multiMemory = 9348.0 * 3.15
        )
        assertTrue("8E5 单核指数应高于 8 Gen 3", gen5.single > gen3.single)
        assertTrue("8E5 多核指数应高于 8 Gen 3", gen5.multi > gen3.multi)
    }
}
