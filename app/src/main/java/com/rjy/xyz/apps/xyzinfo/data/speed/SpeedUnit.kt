package com.rjy.xyz.apps.xyzinfo.data.speed

import java.util.Locale

/**
 * 测速单位：Mbps（兆比特每秒）/ MB/s（兆字节每秒）。
 *
 * 换算统一按**十进制**：1 MB/s = 8 Mbps，1 Mbps = 1_000_000 bit/s。
 * 不用 1024 的原因很实际：运营商和各家测速服务都按十进制，混用两种进制会让
 * 同一根网线在两种单位下换算出来的数字对不上，用户会以为软件测错了。
 */
enum class SpeedUnit(val label: String, val suffix: String) {
    MBPS("Mbps", "Mbps"),
    MBS("MB/s", "MB/s");

    /** 字节/秒 → 当前单位下的数值。 */
    fun fromBytesPerSecond(bytesPerSecond: Double): Double = when (this) {
        MBPS -> bytesPerSecond * 8.0 / 1_000_000.0
        MBS -> bytesPerSecond / 1_000_000.0
    }

    /** 当前单位下的数值 → 字节/秒（切换单位时反算回统一口径）。 */
    fun toBytesPerSecond(value: Double): Double = when (this) {
        MBPS -> value * 1_000_000.0 / 8.0
        MBS -> value * 1_000_000.0
    }

    /**
     * 速度数值：小数位随量级自适应，避免「0.42」和「1024.37」两种极端都难读。
     */
    fun valueText(bytesPerSecond: Double): String {
        val value = fromBytesPerSecond(bytesPerSecond)
        // 10 以上保留一位（128.4），10 以下保留两位（2.35），避免出现「1024.37」这种读不完的数字
        val digits = if (value >= 10) 1 else 2
        return String.format(Locale.getDefault(), "%.${digits}f", value)
    }

    /** 「12.3 Mbps」这种完整写法。 */
    fun text(bytesPerSecond: Double): String = "${valueText(bytesPerSecond)} $suffix"

    companion object {
        /** 从设置里存的字符串还原；认不出来就用 Mbps。 */
        fun fromName(name: String?): SpeedUnit =
            entries.firstOrNull { it.name == name } ?: MBPS
    }
}

/** 延迟 / 抖动的统一写法：整数毫秒，超过 1000 也用毫秒（不换单位）。 */
fun formatMillis(value: Double?): String =
    if (value == null) "—" else String.format(Locale.getDefault(), "%.0f ms", value)
