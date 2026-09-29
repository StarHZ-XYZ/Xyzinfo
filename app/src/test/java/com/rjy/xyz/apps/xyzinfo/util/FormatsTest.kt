package com.rjy.xyz.apps.xyzinfo.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * 展示层格式化工具的单元测试。
 *
 * 固定 Locale，避免不同机器的默认语言影响小数分隔符。
 */
class FormatsTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `bytes 按单位换算并保留两位小数`() {
        assertEquals("0 B", Formats.bytes(0L))
        assertEquals("1.00 KB", Formats.bytes(1024L))
        assertEquals("1.00 MB", Formats.bytes(1024L * 1024))
        assertEquals("1.50 GB", Formats.bytes(1536L * 1024 * 1024))
        assertEquals("12.00 GB", Formats.bytes(12L * 1024 * 1024 * 1024))
    }

    @Test
    fun `bytesOrUnknown 在无数据时返回系统未公开`() {
        assertEquals(Labels.NOT_PUBLIC, Formats.bytesOrUnknown(null))
        assertEquals("2.00 GB", Formats.bytesOrUnknown(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `频率换算为 GHz`() {
        assertEquals("2.84 GHz", Formats.kiloHertzAsGigaHertz(2_841_600))
        assertEquals("1.80 GHz", Formats.kiloHertzAsGigaHertz(1_800_000))
    }

    @Test
    fun `刷新率档位不带空格`() {
        assertEquals("60Hz", Formats.refreshRateTag(60f))
        assertEquals("120Hz", Formats.refreshRateTag(120f))
    }

    @Test
    fun `电池相关单位换算`() {
        assertEquals("36.5 ℃", Formats.temperatureFromTenths(365))
        assertEquals("3.850 V", Formats.volts(3850))
        assertEquals("1234 mA", Formats.milliamps(-1234))
        assertEquals("4500", Formats.microAmpHoursAsMilliAmpHours(4_500_000))
    }

    @Test
    fun `整数带千分位`() {
        assertEquals("1,234,567", Formats.grouped(1_234_567))
    }
}
