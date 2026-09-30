package com.rjy.xyz.apps.xyzinfo.data.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CPU 分数归一化测试（分数与实测速率成正比，且对单项偏差不敏感）。 */
class CpuBenchmarkTest {

    private fun scores(
        integer: Double = 4.84e8,
        flops: Double = 4.50e8,
        compress: Double = 606.0,
        sort: Double = 2.60e7,
        crypto: Double = 5.50e8,
        multiOps: Double = 2.77e9,
        multiFlops: Double = 2.50e9,
        multiCompress: Double = 2.90e3
    ) = CpuBenchmark.scores(
        integerOps = integer,
        floatFlops = flops,
        compressMb = compress,
        sortElements = sort,
        cryptoOps = crypto,
        multiOps = multiOps,
        multiFlops = multiFlops,
        multiCompressMb = multiCompress
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
            integer = 4.84e8 * 2,
            flops = 4.50e8 * 2,
            compress = 606.0 * 2,
            sort = 2.60e7 * 2,
            crypto = 5.50e8 * 2,
            multiOps = 2.77e9 * 2,
            multiFlops = 2.50e9 * 2,
            multiCompress = 2.90e3 * 2
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
            integer = 0.0, flops = 0.0, compress = 0.0, sort = 0.0, crypto = 0.0,
            multiOps = 0.0, multiFlops = 0.0, multiCompress = 0.0
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
