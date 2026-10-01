package com.rjy.xyz.apps.xyzinfo.data

import android.content.Context
import com.rjy.xyz.apps.xyzinfo.model.BatteryStatus
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 电池充放电记录 + 容量 / 健康度估算（1.0.6 新增）。
 *
 * 原理很朴素但确实有效：**同一段连续记录里，电量变化 ΔSOC 与流过的电量 ΔQ 应该成正比**，
 * 于是
 *
 * ```
 * 实际满容量 ≈ ΔQ / |ΔSOC| × 100%
 * ```
 *
 * ΔQ 有两种拿法，优先用前者：
 *
 * 1. **库仑计**（`BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER`）：系统直接给的累计电量（µAh），
 *    最准，但不少机型不给；
 * 2. **电流积分**：把每个采样点的电流 × 采样间隔累加（µA·h），拿不到库仑计时用它 ——
 *    精度取决于系统报的电流有多准，所以页面上会写清楚用的是哪种方法。
 *
 * 估算只在一段**电量变化 ≥ 5%**的连续记录上做（变化太小，误差会被放大），
 * 并且结果要落在 500–20000 mAh 这个合理区间内，否则判定为不可用。
 */
object BatteryLogger {

    data class Sample(
        val timestamp: Long,
        val percent: Int,
        val voltageMv: Int,
        val currentUa: Int?,
        val temperatureC: Double?,
        val charging: Boolean,
        val chargeCounterUah: Int?
    )

    data class CapacityEstimate(
        /** 推算出的实际满容量（mAh）；拿不到就是 null。 */
        val fullCapacityMah: Int?,
        /** 设计容量（mAh）。 */
        val designCapacityMah: Int?,
        /** 健康度百分比 = 实际 / 设计。 */
        val healthPercent: Int?,
        /** 用了哪种方法（库仑计 / 电流积分）。 */
        val method: String,
        /** 这一段记录跨了多少分钟。 */
        val spanMinutes: Int,
        /** 这一段里电量变化了多少个百分点。 */
        val levelDelta: Int,
        /** 采样点数量。 */
        val sampleCount: Int,
        /** 记录数量不够之类的原因说明。 */
        val hint: String
    )

    private const val FILE_NAME = "battery_session.csv"
    private const val HEADER = "timestamp,percent,voltageMv,currentUa,temperatureC,charging,chargeCounterUah"

    /** 连续记录之间超过这个间隔就当成两段（比如中间退出了很久）。 */
    private const val SESSION_GAP_MILLIS = 10 * 60_000L

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun app(context: Context) = context.applicationContext

    /** 采一条并落盘（返回采样点，读不到电池信息时返回 null）。 */
    fun logSample(context: Context): Sample? {
        val info = runCatching { BatteryInfoProvider.load(app(context)) }.getOrNull() ?: return null
        val sample = Sample(
            timestamp = System.currentTimeMillis(),
            percent = info.percent,
            voltageMv = info.voltageMv,
            currentUa = info.currentNowUa ?: info.currentAverageUa,
            temperatureC = info.temperatureTenths.takeIf { it != Int.MIN_VALUE }?.let { it / 10.0 },
            charging = info.status == BatteryStatus.CHARGING ||
                info.status == BatteryStatus.FULL ||
                info.status == BatteryStatus.NOT_CHARGING,
            chargeCounterUah = info.chargeCounterUah
        )
        runCatching {
            val target = file(context)
            val isNew = !target.exists()
            target.appendText(
                buildString {
                    if (isNew) appendLine(HEADER)
                    appendLine(
                        listOf(
                            sample.timestamp.toString(),
                            sample.percent.toString(),
                            sample.voltageMv.toString(),
                            sample.currentUa?.toString() ?: "",
                            sample.temperatureC?.let { String.format(Locale.US, "%.1f", it) } ?: "",
                            if (sample.charging) "1" else "0",
                            sample.chargeCounterUah?.toString() ?: ""
                        ).joinToString(",")
                    )
                }
            )
        }
        return sample
    }

    /** 读取最近 [hours] 小时的记录。 */
    fun samples(context: Context, hours: Int = 24 * 30): List<Sample> {
        val target = file(context)
        if (!target.exists()) return emptyList()
        val since = System.currentTimeMillis() - hours * 3600_000L
        return runCatching {
            target.readLines().drop(1).mapNotNull { line ->
                val parts = line.split(',')
                if (parts.size < 7) return@mapNotNull null
                val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
                if (timestamp < since) return@mapNotNull null
                Sample(
                    timestamp = timestamp,
                    percent = parts[1].toIntOrNull() ?: return@mapNotNull null,
                    voltageMv = parts[2].toIntOrNull() ?: 0,
                    currentUa = parts[3].toIntOrNull(),
                    temperatureC = parts[4].toDoubleOrNull(),
                    charging = parts[5] == "1",
                    chargeCounterUah = parts[6].toIntOrNull()
                )
            }
        }.getOrDefault(emptyList())
    }

    fun stats(context: Context): Pair<Int, Long> {
        val target = file(context)
        if (!target.exists()) return 0 to 0L
        val lines = runCatching { target.readLines().size - 1 }.getOrDefault(0)
        return lines to target.length()
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /**
     * 估算实际容量与健康度。
     *
     * 从所有记录里挑**电量跨度最大**的那一段连续记录来算，跨度越大越准。
     */
    fun estimate(context: Context): CapacityEstimate {
        val info = runCatching { BatteryInfoProvider.load(app(context)) }.getOrNull()
        val design = info?.designCapacityMah
        val all = samples(context)
        if (all.size < 3) {
            return CapacityEstimate(
                fullCapacityMah = null,
                designCapacityMah = design,
                healthPercent = null,
                method = "还没有足够记录",
                spanMinutes = 0,
                levelDelta = 0,
                sampleCount = all.size,
                hint = "至少要有 3 条记录（打开页面会自动记，或点「马上记一条」）"
            )
        }

        val best = sessions(all).mapNotNull { score(it, design) }.maxByOrNull { it.levelDelta }
        if (best == null) {
            return CapacityEstimate(
                fullCapacityMah = null,
                designCapacityMah = design,
                healthPercent = null,
                method = "跨度过小",
                spanMinutes = 0,
                levelDelta = 0,
                sampleCount = all.size,
                hint = "这一批记录里电量变化都不到 5%，推算出来的数字误差太大。" +
                    "充满电后正常用一会儿（或充电到 80% 以上）再来算"
            )
        }
        return best
    }

    /** 纯函数版：给一批采样点直接算，方便单元测试。 */
    fun estimateFrom(samples: List<Sample>, designCapacityMah: Int?): CapacityEstimate {
        if (samples.size < 3) {
            return CapacityEstimate(null, designCapacityMah, null, "还没有足够记录", 0, 0, samples.size, "记录太少")
        }
        val best = sessions(samples).mapNotNull { score(it, designCapacityMah) }.maxByOrNull { it.levelDelta }
        return best ?: CapacityEstimate(
            null, designCapacityMah, null, "跨度过小", 0, 0, samples.size, "电量变化不到 5%"
        )
    }

    /** 按时间间隔切段。 */
    private fun sessions(samples: List<Sample>): List<List<Sample>> {
        val sorted = samples.sortedBy { it.timestamp }
        val out = mutableListOf<List<Sample>>()
        var current = mutableListOf<Sample>()
        sorted.forEach { sample ->
            if (current.isNotEmpty() && sample.timestamp - current.last().timestamp > SESSION_GAP_MILLIS) {
                out += current
                current = mutableListOf()
            }
            current += sample
        }
        if (current.isNotEmpty()) out += current
        return out
    }

    /** 对一段记录做估算；不满足条件返回 null。 */
    private fun score(session: List<Sample>, design: Int?): CapacityEstimate? {
        if (session.size < 3) return null
        val first = session.first()
        val last = session.last()
        val levelDelta = last.percent - first.percent
        if (abs(levelDelta) < MIN_LEVEL_DELTA) return null
        val spanMinutes = ((last.timestamp - first.timestamp) / 60_000L).toInt().coerceAtLeast(0)

        // 1) 库仑计优先
        val q1 = first.chargeCounterUah
        val q2 = last.chargeCounterUah
        var capacityMah: Double? = null
        var method = "电流积分"
        if (q1 != null && q2 != null && q2 != q1) {
            val deltaMah = abs(q2 - q1) / 1000.0
            capacityMah = deltaMah / abs(levelDelta) * 100.0
            method = "库仑计"
        }
        // 2) 退而求其次：电流 × 间隔 积分
        if (capacityMah == null) {
            var microAmpHours = 0.0
            for (index in 1 until session.size) {
                val prev = session[index - 1]
                val now = session[index]
                val current = abs(((prev.currentUa ?: 0) + (now.currentUa ?: 0)) / 2.0)
                val hours = (now.timestamp - prev.timestamp) / 3_600_000.0
                microAmpHours += current * hours
            }
            if (microAmpHours <= 0.0) return null
            capacityMah = (microAmpHours / 1000.0) / abs(levelDelta) * 100.0
        }

        val rounded = (capacityMah ?: return null).roundToInt()
        if (rounded !in MIN_CAPACITY_MAH..MAX_CAPACITY_MAH) return null
        val health = design?.takeIf { it > 0 }?.let { (rounded * 100.0 / it).roundToInt().coerceIn(0, 200) }
        return CapacityEstimate(
            fullCapacityMah = rounded,
            designCapacityMah = design,
            healthPercent = health,
            method = method,
            spanMinutes = spanMinutes,
            levelDelta = abs(levelDelta),
            sampleCount = session.size,
            hint = "这一段跨 $spanMinutes 分钟，电量变化 ${abs(levelDelta)}%"
        )
    }

    /** 健康度评级。 */
    fun healthGrade(percent: Int?): String = when {
        percent == null -> "—"
        percent >= 90 -> "优秀"
        percent >= 80 -> "良好"
        percent >= 70 -> "一般（损耗明显）"
        else -> "偏差（建议换电池）"
    }

    /** 电流显示：充电为正、放电为负，带瓦数。 */
    fun formatCurrent(currentUa: Int?, voltageMv: Int?): String {
        if (currentUa == null) return "—"
        val ma = currentUa / 1000.0
        val watts = if (voltageMv != null) currentUa / 1_000_000.0 * voltageMv / 1000.0 else null
        return buildString {
            append(String.format(Locale.US, "%+.0f mA", ma))
            if (watts != null) append(String.format(Locale.US, "（%.2f W）", abs(watts)))
        }
    }

    private const val MIN_LEVEL_DELTA = 5
    private const val MIN_CAPACITY_MAH = 500
    private const val MAX_CAPACITY_MAH = 20000

}
