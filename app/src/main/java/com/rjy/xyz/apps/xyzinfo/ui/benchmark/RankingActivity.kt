package com.rjy.xyz.apps.xyzinfo.ui.benchmark

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import com.rjy.xyz.apps.xyzinfo.data.SocInfoProvider
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
import com.rjy.xyz.apps.xyzinfo.ui.common.ThemeColors

/**
 * 性能排行榜（数据来自极客湾公开榜单）。
 *
 * 交互按反馈改过：
 * - **不自动加载**：整张榜上百行，进页面就铺开会明显发顿，现在要点「加载排行榜」才生成；
 * - **分块渲染**：每帧只铺 12 行，铺的时候界面还能滑，不会卡住；
 * - **结果缓存**：数据放在 companion 里，退出再进来直接复用上一次的榜单；
 *   想更新点「刷新榜单」即可。
 */
class RankingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRankingBinding
    private val handler = Handler(Looper.getMainLooper())

    private var metric = RankingMetric.COMPOSITE
    private var brand: String? = null
    private var matched: ChipScore? = null
    private var deviceChipName: String? = null

    private var state = State.IDLE
    private var renderToken = 0

    private enum class State { IDLE, RENDERING, READY }

    /** 榜单里的一行：要么是参考芯片，要么是本机实测。 */
    private data class RankRow(
        val name: String,
        val value: Int,
        val year: String,
        val isMeasured: Boolean,
        val isMatched: Boolean
    )

    /** 生成好的榜单（行 + 最大值 + 概览文案），跨页面复用。 */
    private data class PreparedRanking(
        val key: String,
        val rows: List<RankRow>,
        val maxValue: Int,
        val summary: String
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
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
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

        binding.btnLoadRanking.setOnClickListener {
            Anim.pressFeedback(it)
            startLoading()
        }
        binding.btnRefreshRanking.setOnClickListener {
            Anim.pressFeedback(it)
            cache = null
            startLoading()
        }
        Anim.pressFeedback(binding.btnRunBenchmark)

        // 芯片识别放后台，读完再决定是直接吃缓存还是等用户点加载
        loadDeviceChip()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        renderToken++
    }

    private fun loadDeviceChip() {
        Thread({
            val spec = runCatching { SocInfoProvider.findSpec() }.getOrNull()
            deviceChipName = spec?.displayName
            matched = GeekerwanScores.match(deviceChipName)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val cached = cache
                if (cached != null && cached.key == currentKey()) {
                    applyPrepared(cached, animate = false)
                } else {
                    showIdle()
                }
            }
        }, "xyzinfo-ranking").start()
    }

    private fun currentKey(): String {
        val measured = SettingsRepository.lastBenchmark(this)
        return "$metric|$brand|$deviceChipName|${measured?.single}|${measured?.multi}|${measured?.gpu}"
    }

    private fun showIdle() {
        state = State.IDLE
        binding.layoutRanking.removeAllViews()
        binding.btnLoadRanking.visibility = View.VISIBLE
        binding.btnRefreshRanking.visibility = View.GONE
        binding.tvRankingHint.text = "点「加载排行榜」生成榜单；加载一次会缓存，下次进来直接显示，想更新点刷新"
        binding.tvDeviceSummary.setInfoRow(deviceSummary())
    }

    private fun startLoading() {
        if (state == State.RENDERING) return
        state = State.RENDERING
        binding.btnLoadRanking.isEnabled = false
        binding.btnRefreshRanking.isEnabled = false
        binding.btnLoadRanking.visibility = if (cache == null) View.VISIBLE else View.GONE
        binding.btnRefreshRanking.visibility = if (cache == null) View.GONE else View.VISIBLE
        binding.tvRankingHint.text = "正在整理榜单数据…"
        binding.tvDeviceSummary.setInfoRow(deviceSummary())

        val key = currentKey()
        Thread({
            val prepared = prepare(key)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                applyPrepared(prepared, animate = true)
            }
        }, "xyzinfo-ranking-build").start()
    }

    /** 只做数据整理（排序 / 筛选 / 拼概览），不碰 UI，所以放后台线程。 */
    private fun prepare(key: String): PreparedRanking {
        val measured = SettingsRepository.lastBenchmark(this)
        val rows = ArrayList<RankRow>(96)
        GeekerwanScores.ranked(metric, brand).take(90).forEach { score ->
            rows += RankRow(
                name = if (score.name == matched?.name) "${score.name}（本机芯片）" else score.name,
                value = metric.valueOf(score),
                year = score.year.toString(),
                isMeasured = false,
                isMatched = score.name == matched?.name
            )
        }
        if (measured != null) {
            rows += RankRow(
                name = "本机实测（${deviceChipName ?: "当前设备"}）",
                value = measuredValue(measured),
                year = "实测",
                isMeasured = true,
                isMatched = false
            )
        }
        rows.sortByDescending { it.value }
        return PreparedRanking(
            key = key,
            rows = rows,
            maxValue = rows.firstOrNull()?.value ?: 1,
            summary = deviceSummary()
        )
    }

    private fun deviceSummary(): String {
        val measured = SettingsRepository.lastBenchmark(this)
        return buildString {
            append("本机芯片：")
            append(deviceChipName ?: "未识别")
            val reference = matched
            if (reference != null) {
                append("\n参考水平：综合 ${reference.composite}")
                append("（单核 ${reference.single} / 多核 ${reference.multi} / GPU ${reference.gpu}）")
            } else {
                append("\n该芯片暂未收录到榜单，下面仍可看到全部参考机型")
            }
            if (measured != null) {
                append("\n本机实测：综合 ${measuredValue(measured, RankingMetric.COMPOSITE)}")
                append("（单核 ${measured.single} / 多核 ${measured.multi}")
                if (measured.gpu != null) append(" / GPU ${measured.gpu}")
                append("）")
            } else {
                append("\n本机还没跑过分，跑一次就会插进榜单")
            }
        }
    }

    /**
     * 应用榜单：先显示概览，再**每帧铺 12 行**，铺的过程中界面保持可响应。
     */
    private fun applyPrepared(prepared: PreparedRanking, animate: Boolean) {
        cache = prepared
        state = State.READY
        renderToken++
        val token = renderToken

        binding.tvDeviceSummary.setInfoRow(prepared.summary)
        binding.tvRankingHint.text = "共 ${prepared.rows.size} 条（已缓存，下次进来直接显示）"
        binding.btnLoadRanking.visibility = View.GONE
        binding.btnLoadRanking.isEnabled = true
        binding.btnRefreshRanking.visibility = View.VISIBLE
        binding.btnRefreshRanking.isEnabled = true
        binding.layoutRanking.removeAllViews()

        val deviceColor = ThemeColors.accent(this)
        val warningColor = ContextCompat.getColor(this, R.color.status_warning)
        val idleColor = ContextCompat.getColor(this, R.color.text_primary)
        val deviceBar = ContextCompat.getDrawable(this, R.drawable.bg_rank_bar_device)
        val normalBar = ContextCompat.getDrawable(this, R.drawable.bg_rank_bar)

        var index = 0
        var deviceRowIndex = -1
        // 用 object 而不是 lambda：分块递归需要拿到这个 Runnable 自己
        val chunk = object : Runnable {
          override fun run() {
            if (token != renderToken || isFinishing) return
            val end = minOf(index + CHUNK_SIZE, prepared.rows.size)
            while (index < end) {
                val row = prepared.rows[index]
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
                        if (deviceRowIndex < 0) deviceRowIndex = index
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
                val fraction = if (prepared.maxValue > 0) row.value.toFloat() / prepared.maxValue else 0f
                item.viewBar.growBar(
                    fraction,
                    duration = if (animate) 480L else 1L,
                    delay = if (animate && index < 12) index * 14L else 0L
                )
                binding.layoutRanking.addView(item.root)
                index++
            }
            if (index < prepared.rows.size) {
                binding.tvRankingHint.text = "正在渲染 ${index}/${prepared.rows.size}…"
                binding.layoutRanking.postOnAnimation(this)
            } else {
                binding.tvRankingHint.text = "共 ${prepared.rows.size} 条（已缓存，下次进来直接显示）"
                if (deviceRowIndex >= 0) {
                    val target = binding.layoutRanking.getChildAt(deviceRowIndex) ?: return
                    binding.root.post {
                        binding.root.smoothScrollTo(0, (target.top - dp(120f)).coerceAtLeast(0))
                    }
                }
            }
          }
        }
        handler.post(chunk)
    }

    private fun buildMetricChips() {
        binding.layoutMetricChips.removeAllViews()
        RankingMetric.entries.forEach { item ->
            binding.layoutMetricChips.addView(
                createChip(item.label, item == metric) {
                    if (metric != item) {
                        metric = item
                        buildMetricChips()
                        onFilterChanged()
                    }
                }
            )
        }
    }

    private fun buildBrandChips() {
        binding.layoutBrandChips.removeAllViews()
        (listOf<String?>(null) + GeekerwanScores.brands).forEach { item ->
            binding.layoutBrandChips.addView(
                createChip(item ?: "全部", item == brand) {
                    if (brand != item) {
                        brand = item
                        buildBrandChips()
                        onFilterChanged()
                    }
                }
            )
        }
    }

    /** 切换口径 / 品牌：已经加载过就顺手重新生成，否则保持等待用户点加载。 */
    private fun onFilterChanged() {
        if (state == State.READY || cache != null) {
            startLoading()
        } else {
            showIdle()
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
            setOnClickListener { Anim.pressFeedback(this); onClick() }
        }
        chip.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(8f) }
        return chip
    }

    private fun measuredValue(
        measured: SettingsRepository.SavedBenchmark,
        metricOverride: RankingMetric = metric
    ): Int = when (metricOverride) {
        RankingMetric.SINGLE -> measured.single
        RankingMetric.MULTI -> measured.multi
        RankingMetric.GPU -> measured.gpu ?: 0
        RankingMetric.COMPOSITE -> measuredComposite(measured)
    }

    /** 本机实测综合指数：GPU 缺失时按 CPU 两项重新归一，避免被拖到榜底。 */
    private fun measuredComposite(measured: SettingsRepository.SavedBenchmark): Int {
        val cpu = 0.35 * measured.single / ChipScore.SINGLE_ANCHOR +
            0.35 * measured.multi / ChipScore.MULTI_ANCHOR
        val gpu = measured.gpu
        val weight = if (gpu == null) 0.70 else 1.0
        val gpuPart = if (gpu == null) 0.0 else 0.30 * gpu / ChipScore.GPU_ANCHOR
        return ((cpu + gpuPart) / weight * 1000).roundToInt()
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        /** 每帧铺多少行：太大会卡，太小会慢。 */
        const val CHUNK_SIZE = 12

        /**
         * 已经生成好的榜单。放在 companion 里，退出页面再进来可以直接复用，
         * 不用重新排序、重新渲染。
         */
        private var cache: PreparedRanking? = null
    }
}
