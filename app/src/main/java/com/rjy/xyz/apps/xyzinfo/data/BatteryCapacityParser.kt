package com.rjy.xyz.apps.xyzinfo.data

import kotlin.math.roundToInt

/** 电池节点报告的是电荷容量还是能量容量。两者不能按同一个公式换算。 */
internal enum class CapacitySourceKind {
    CHARGE,
    ENERGY
}

/**
 * 把不同 power_supply 节点统一换算成 mAh。
 *
 * - `charge_full*` / `fg_fullcapnom` 是电荷量，通常以 µAh 或 mAh 保存；
 * - `energy_full*` 是能量，通常以 µWh 或 mWh 保存，必须除以电压才能得到 mAh。
 *
 * 以前两类节点混用 `/ 1000`，会把 19,000,000 µWh 错误显示成 19,000 mAh，
 * 而不是按 3.8V 换算出的 5,000 mAh。
 */
internal object BatteryCapacityParser {

    fun toMah(raw: Long, kind: CapacitySourceKind, voltageMv: Int): Int? {
        if (raw <= 0L) return null
        val nominalVoltageMv = voltageMv.takeIf { it in 2_500..5_000 } ?: 3_800
        val capacityMah = when (kind) {
            CapacitySourceKind.CHARGE ->
                if (raw >= MICRO_THRESHOLD) raw / 1_000.0 else raw.toDouble()

            CapacitySourceKind.ENERGY ->
                if (raw >= MICRO_THRESHOLD) {
                    raw / nominalVoltageMv.toDouble()
                } else {
                    raw * 1_000.0 / nominalVoltageMv
                }
        }.roundToInt()

        return capacityMah.takeIf { it in MIN_CAPACITY_MAH..MAX_CAPACITY_MAH }
    }

    private const val MICRO_THRESHOLD = 100_000L
    private const val MIN_CAPACITY_MAH = 500
    private const val MAX_CAPACITY_MAH = 20_000
}
