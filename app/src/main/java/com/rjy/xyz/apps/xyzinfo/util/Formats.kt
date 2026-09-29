package com.rjy.xyz.apps.xyzinfo.util

import java.util.Locale
import kotlin.math.abs

/**
 * 界面展示用的数值格式化工具，统一所有页面的小数位数与单位写法。
 */
object Formats {

    private const val KILO = 1024.0
    private const val MEGA = KILO * 1024
    private const val GIGA = MEGA * 1024

    /** 字节数转可读文本，例如 12.00 GB、512.00 MB。 */
    fun bytes(value: Long): String = when {
        value >= GIGA -> String.format(Locale.getDefault(), "%.2f GB", value / GIGA)
        value >= MEGA -> String.format(Locale.getDefault(), "%.2f MB", value / MEGA)
        value >= KILO -> String.format(Locale.getDefault(), "%.2f KB", value / KILO)
        else -> "$value B"
    }

    fun bytesOrUnknown(value: Long?): String = value?.let { bytes(it) } ?: Labels.NOT_PUBLIC

    /** 固定小数位格式化，默认两位。 */
    fun decimal(value: Double, digits: Int = 2): String =
        String.format(Locale.getDefault(), "%.${digits}f", value)

    /** 摄氏度，例如 36.5 ℃。 */
    fun temperature(celsius: Double): String =
        String.format(Locale.getDefault(), "%.1f ℃", celsius)

    /** 系统节点常见的“0.1 摄氏度”单位，例如 365 表示 36.5 ℃。 */
    fun temperatureFromTenths(tenths: Int): String =
        String.format(Locale.getDefault(), "%.1f ℃", tenths / 10.0)

    /** 千赫兹转 GHz，例如 2841600 -> 2.84 GHz。 */
    fun kiloHertzAsGigaHertz(kiloHertz: Int): String =
        String.format(Locale.getDefault(), "%.2f GHz", kiloHertz / 1_000_000.0)

    /** 刷新率，例如 120.0 Hz。 */
    fun hertz(hz: Float, digits: Int = 1): String =
        String.format(Locale.getDefault(), "%.${digits}f Hz", hz)

    /** 刷新率档位标签，例如 120Hz（无空格，用于 “60Hz / 120Hz” 这类列表）。 */
    fun refreshRateTag(hz: Float): String =
        String.format(Locale.getDefault(), "%.0fHz", hz)

    /** 电压，例如 3.850 V。 */
    fun volts(voltageMv: Int): String =
        String.format(Locale.getDefault(), "%.3f V", voltageMv / 1000.0)

    /** 电流绝对值，例如 1234 mA。 */
    fun milliamps(currentUa: Int): String =
        String.format(Locale.getDefault(), "%.0f mA", abs(currentUa) / 1000.0)

    /** 微安时转毫安时，例如 4500000 -> 4500。 */
    fun microAmpHoursAsMilliAmpHours(microAmpHours: Int): String =
        String.format(Locale.getDefault(), "%.0f", microAmpHours / 1000.0)

    /** 带千分位的整数，用于计数类字段。 */
    fun grouped(value: Int): String = String.format(Locale.getDefault(), "%,d", value)

    fun grouped(value: Long): String = String.format(Locale.getDefault(), "%,d", value)

    /** 触控采样率等节点的原始数字，例如 480 Hz。 */
    fun hertzFromInt(hz: Int): String = "$hz Hz"
}
