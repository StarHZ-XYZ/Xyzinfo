package com.rjy.xyz.apps.xyzinfo.ui.benchmark

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import com.rjy.xyz.apps.xyzinfo.data.SocInfoProvider
import com.rjy.xyz.apps.xyzinfo.data.benchmark.CpuBenchmark
import com.rjy.xyz.apps.xyzinfo.data.benchmark.GeekerwanScores
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityRankingBinding
import com.rjy.xyz.apps.xyzinfo.databinding.ItemRankingRowBinding
import com.rjy.xyz.apps.xyzinfo.model.ChipScore
import com.rjy.xyz.apps.xyzinfo.model.RankingMetric
import com.rjy.xyz.apps.xyzinfo.ui.common.Anim
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.growBar
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import kotlin.math.roundToInt

/**
 * 性能排行榜（v0.7 重写）。
 *
 * 数据换成极客湾（Geekerwan）公开榜单，支持按综合 / 单核 / 多核 / GPU 切换口径，
 * 按品牌筛选；如果本机已经跑过分，会把「本机实测」直接插进榜单里对应的位置，
 * 和同芯片的参考值并排比较。
 */
class RankingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRankingBinding
    private var metric = RankingMetric.COMPOSITE
    private var brand: String? = null
    private var matched: ChipScore? = null
    private var deviceChipName: String? = null
    private var firstRender = true

    /** 榜单里的一行：要么是参考芯片，要么是本机实测。 */
    private data class RankRow(
        val name: String,
        val value: Int,
        val year: String,
        val isMeasured: Boolean,
        val isMatched: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRankingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_RANKING)

        binding.btnRunBenchmark.setOnClickListener {
            startActivity(
                Intent(this, BenchmarkActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }
        binding.tvRankingNote.setInfoRow(
            "数据来源：极客湾（Geekerwan）公开榜单，快照 ${GeekerwanScores.SNAPSHOT}"
        )
        binding.tvRankingNote.append(
            "\n\n单核 / 多核为 Geekbench 6 量级的参考值，GPU 为相对指数（骁龙 778G = 1000）。" +
                "同一颗芯片在不同机型上的成绩会因散热与频率策略浮动，榜单给的是该芯片的典型水平；" +
                "本机实测用的是本应用的算法，只是换算到同一刻度上便于比较，不代表官方成绩。"
        )

        buildMetricChips()
        buildBrandChips()
        loadDeviceChip()
    }

    override fun onResume() {
        super.onResume()
        if (!firstRender) render()
    }

    /** 芯片识别要读系统节点，放后台线程，读完再渲染榜单。 */
    private fun loadDeviceChip() {
        Thread({
            val spec = runCatching { SocInfoProvider.findSpec() }.getOrNull()
            deviceChipName = spec?.displayName
            matched = GeekerwanScores.match(deviceChipName)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                render()
            }
        }, "xyzinfo-ranking").start()
    }

    private fun buildMetricChips() {
        binding.layoutMetricChips.removeAllViews()
        RankingMetric.entries.forEach { item ->
            binding.layoutMetricChips.addView(
                createChip(item.label, item == metric) {
                    if (metric != item) {
                        metric = item
                        buildMetricChips()
                        render()
                    }
                }
            )
        }
    }

    private fun buildBrandChips() {
        binding.layoutBrandChips.removeAllViews()
        val options = listOf<String?>(null) + GeekerwanScores.brands
        options.forEach { item ->
            binding.layoutBrandChips.addView(
                createChip(item ?: "全部", item == brand) {
                    if (brand != item) {
                        brand = item
                        buildBrandChips()
                        render()
                    }
                }
            )
        }
    }

    private fun createChip(text: String, selected: Boolean, onClick: () -> Unit): TextView {
        val chip = TextView(this).apply {
            this.text = text
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding(dp(14f), dp(7f), dp(14f), dp(7f))
            setTextColor(
                ContextCompat.getColor(
                    this@RankingActivity,
                    if (selected) R.color.accent else R.color.text_secondary
                )
            )
            background = ContextCompat.getDrawable(this@RankingActivity, R.drawable.bg_chip_filter)
            isSelected = selected
            isClickable = true
            setOnClickListener {
                Anim.pressFeedback(this)
                onClick()
            }
        }
        chip.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(8f) }
        return chip
    }

    private fun render() {
        firstRender = false
        val measured = SettingsRepository.lastBenchmark(this)

        val summary = buildString {
            append("本机芯片：")
            append(deviceChipName ?: "未识别")
            val reference = matched
            if (reference != null) {
                append("\n参考水平：综合 ${reference.composite}")
                append("（单核 ${reference.single} / 多核 ${reference.multi} / GPU ${reference.gpu}）")
            } else {
                append("\n该芯片暂未收录到榜单，下面仍可看到全部参考机型的成绩")
            }
            if (measured != null) {
                append("\n本机实测：综合 ${measuredComposite(measured)}")
                append("（单核 ${measured.single} / 多核 ${measured.multi}")
                if (measured.gpu != null) append(" / GPU ${measured.gpu}")
                append("）")
            } else {
                append("\n本机还没跑过分，点下面的按钮跑一次就会插进榜单")
            }
        }
        binding.tvDeviceSummary.setInfoRow(summary)

        // 只渲染前 90 条：全量 110+ 行会让滚动丢帧，尾部名次意义也不大
        val referenceRows = GeekerwanScores.ranked(metric, brand).take(90).map { score ->
            RankRow(
                name = if (score.name == matched?.name) "${score.name}（本机芯片）" else score.name,
                value = metric.valueOf(score),
                year = score.year.toString(),
                isMeasured = false,
                isMatched = score.name == matched?.name
            )
        }

        val rows = ArrayList<RankRow>(referenceRows.size + 1)
        rows.addAll(referenceRows)
        if (measured != null) {
            rows.add(
                RankRow(
                    name = "本机实测（${deviceChipName ?: "当前设备"}）",
                    value = measuredValue(measured),
                    year = "实测",
                    isMeasured = true,
                    isMatched = false
                )
            )
        }
        rows.sortByDescending { it.value }

        val max = rows.firstOrNull()?.value ?: 1
        val deviceColor = ContextCompat.getColor(this, R.color.accent)
        val deviceBar = ContextCompat.getDrawable(this, R.drawable.bg_rank_bar_device)
        val normalBar = ContextCompat.getDrawable(this, R.drawable.bg_rank_bar)
        val idleColor = ContextCompat.getColor(this, R.color.text_primary)
        val warningColor = ContextCompat.getColor(this, R.color.status_warning)

        binding.layoutRanking.removeAllViews()
        var deviceRowIndex = -1
        rows.forEachIndexed { index, row ->
            val item = ItemRankingRowBinding.inflate(layoutInflater, binding.layoutRanking, false)
            item.tvRank.text = "#${index + 1}"
            item.tvName.text = row.name
            item.tvYear.text = row.year
            item.tvScore.text = row.value.toString()

            when {
                row.isMeasured -> {
                    item.tvName.setTextColor(deviceColor)
                    item.tvScore.setTextColor(deviceColor)
                    item.tvRank.setTextColor(deviceColor)
                    item.viewBar.background = deviceBar
                    deviceRowIndex = index
                }

                row.isMatched -> {
                    item.tvName.setTextColor(warningColor)
                    item.tvScore.setTextColor(warningColor)
                    item.viewBar.background = deviceBar
                    if (deviceRowIndex < 0) deviceRowIndex = index
                }

                else -> {
                    item.tvName.setTextColor(idleColor)
                    item.viewBar.background = normalBar
                }
            }

            val fraction = if (max > 0) row.value.toFloat() / max else 0f
            item.viewBar.growBar(
                fraction,
                duration = 520L,
                // 只给前 15 行做错峰延迟，避免一次排几百个动画
                delay = if (index < 15) index * 12L else 0L
            )
            binding.layoutRanking.addView(item.root)
        }

        // 前若干行做一次依次入场，滚动到本机那一行
        val visible = (0 until minOf(binding.layoutRanking.childCount, 12))
            .map { binding.layoutRanking.getChildAt(it) }
        Anim.staggerIn(visible, step = 35L, travelDp = 10f, duration = 320L)
        if (deviceRowIndex >= 0) {
            val target = binding.layoutRanking.getChildAt(deviceRowIndex) ?: return
            binding.root.post {
                binding.root.smoothScrollTo(0, (target.top - dp(120f)).coerceAtLeast(0))
            }
        }
    }

    private fun measuredValue(measured: SettingsRepository.SavedBenchmark): Int =
        when (metric) {
            RankingMetric.SINGLE -> measured.single
            RankingMetric.MULTI -> measured.multi
            RankingMetric.GPU -> measured.gpu ?: 0
            RankingMetric.COMPOSITE -> measuredComposite(measured)
        }

    /** 本机实测的综合指数：GPU 缺失时按 CPU 两项重新归一，避免被拖到榜底。 */
    private fun measuredComposite(measured: SettingsRepository.SavedBenchmark): Int {
        val cpu = 0.35 * measured.single / ChipScore.SINGLE_ANCHOR +
            0.35 * measured.multi / ChipScore.MULTI_ANCHOR
        val gpu = measured.gpu
        val weight = if (gpu == null) 0.70 else 1.0
        val gpuPart = if (gpu == null) 0.0 else 0.30 * gpu / ChipScore.GPU_ANCHOR
        return ((cpu + gpuPart) / weight * 1000).roundToInt()
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).roundToInt()
}
