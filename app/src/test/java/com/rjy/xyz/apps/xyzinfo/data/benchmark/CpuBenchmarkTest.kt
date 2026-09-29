package com.rjy.xyz.apps.xyzinfo.data.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CPU 分数归一化测试（分数与实测速率成正比）。 */
class CpuBenchmarkTest {

    @Test
    fun `基准速率对应基准分`() {
        val (single, multi) = CpuBenchmark.scores(
            integerOpsPerSecond = 4.84e8,
            floatFlopsPerSecond = 4.50e8,
            compressMbPerSecond = 606.0,
            multiOpsPerSecond = 1.108e9
        )
        assertEquals(1000, single)
        assertEquals(1000, multi)
    }

    @Test
    fun `骁龙 778G 实测速率对应参考指数`() {
        // 真机实测数据：单核 484 Mops/s、450 Mflops/s、606 MB/s，多核 2770 Mops/s
        val (single, multi) = CpuBenchmark.scores(
            integerOpsPerSecond = 4.84e8,
            floatFlopsPerSecond = 4.50e8,
            compressMbPerSecond = 606.0,
            multiOpsPerSecond = 2.77e9
        )
        assertEquals(1000, single)
        assertEquals(2500, multi)
    }

    @Test
    fun `速率为零时分数为零`() {
        val (single, multi) = CpuBenchmark.scores(0.0, 0.0, 0.0, 0.0)
        assertEquals(0, single)
        assertEquals(0, multi)
        assertTrue(single >= 0 && multi >= 0)
    }
}
