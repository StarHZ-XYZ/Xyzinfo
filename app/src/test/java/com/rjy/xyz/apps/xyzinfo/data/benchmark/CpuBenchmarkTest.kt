package com.rjy.xyz.apps.xyzinfo.data.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CPU 分数归一化测试（分数与实测速率成正比，且对单项偏差不敏感）。 */
class CpuBenchmarkTest {

    /**
     * 默认入参就是标定基准本身（骁龙 778G 在 1.0.1 新负载下的实测速率），
     * 所以这组值必须正好换算成榜单给 778G 的 1010 / 2900。
     */
    private fun scores(
        integer: Double = 3.05e8,
        flops: Double = 1.75e8,
        compress: Double = 600.0,
        sort: Double = 1.23e7,
        crypto: Double = 2.00e8,
        memory: Double = 11000.0,
        multiOps: Double = 9.30e8,
        multiFlops: Double = 7.75e8,
        multiCompress: Double = 1530.0,
        multiMemory: Double = 10450.0
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
        assertEquals(1010, result.single)
        assertEquals(2900, result.multi)
    }

    @Test
    fun `所有负载同比例提升时分数同比例提升`() {
        val base = scores()
        val doubled = scores(
            integer = 3.05e8 * 2,
            flops = 1.75e8 * 2,
            compress = 600.0 * 2,
            sort = 1.23e7 * 2,
            crypto = 2.00e8 * 2,
            memory = 11000.0 * 2,
            multiOps = 9.30e8 * 2,
            multiFlops = 7.75e8 * 2,
            multiCompress = 1530.0 * 2,
            multiMemory = 10450.0 * 2
        )
        assertEquals(base.single * 2, doubled.single)
        assertEquals(base.multi * 2, doubled.multi)
    }

    @Test
    fun `单项异常不会把总分带飞`() {
        val outlier = scores(compress = 606.0 * 100)
        assertTrue(outlier.single > 1010)
        assertTrue(outlier.single < 1010 * 2)
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
}
