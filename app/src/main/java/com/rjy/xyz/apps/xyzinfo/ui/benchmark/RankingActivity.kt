package com.rjy.xyz.apps.xyzinfo.ui.benchmark

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import com.rjy.xyz.apps.xyzinfo.data.SocInfoProvider
import com.rjy.xyz.apps.xyzinfo.data.benchmark.GeekerwanScores
import com.rjy.xyz.apps.xyzinfo.data.benchmark.RankingCache
import com.rjy.xyz.apps.xyzinfo.data.benchmark.RankingUpdater
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityRankingBinding
import com.rjy.xyz.apps.xyzinfo.databinding.ItemRankingRowBinding
import com.rjy.xyz.apps.xyzinfo.model.ChipScore
import com.rjy.xyz.apps.xyzinfo.model.RankingMetric
import com.rjy.xyz.apps.xyzinfo.ui.common.Anim
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassBottomBar
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
    private var glassBar: GlassBottomBar? = null

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
        glassBar = GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_RANKING)

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

        binding.btnApplyRankingUpdate.setOnClickListener { applyRankingUpdate() }
        binding.btnDismissRankingUpdate.setOnClickListener {
            Anim.pressFeedback(it)
            binding.cardRankingUpdate.visibility = View.GONE
        }

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

        // 芯片识别 + 读本地缓存放后台，读完直接开始逐条显示
        loadDeviceChip()
        // 后台检查榜单数据有没有新版本（只拉一个几十字节的版本号，很轻）
        checkRankingUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        renderToken++
    }

    private fun loadDeviceChip() {
        Thread({
            // 先把下载过的榜单数据集读进内存（没有就用内置的），再识别芯片
            runCatching { RankingUpdater.loadCached(this) }
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
            // 内存里没有，再试磁盘缓存（进程刚起来时走这条）
            if (cache == null) {
                val disk = runCatching { RankingCache.load(this) }.getOrNull()
                runOnUiThread {
                    if (isFinishing || disk == null) return@runOnUiThread
                    if (disk.key == currentKey()) {
                        applyPrepared(disk.toPrepared(), animate = false)
                    }
                }
            }
        }, "xyzinfo-ranking").start()
    }

    /** 磁盘缓存 → 内存模型。 */
    private fun RankingCache.Snapshot.toPrepared(): PreparedRanking = PreparedRanking(
        key = key,
        rows = rows.map {
            RankRow(
                name = it.name,
                value = it.value,
                year = it.year,
                isMeasured = it.measured,
                isMatched = it.matched
            )
        },
        maxValue = maxValue,
        summary = summary
    )

    /**
     * 后台检查榜单数据有没有更新。
     *
     * 只拉一个几十字节的版本号文件；**有更新才**弹出提示条，
     * 用户点了「更新」才真正下载数据。没网 / 连不上就静默跳过，绝不打扰。
     */
    private fun checkRankingUpdate() {
        Thread({
            val result = runCatching { RankingUpdater.check(this) }.getOrNull() ?: return@Thread
            if (!result.hasUpdate) return@Thread
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                binding.cardRankingUpdate.visibility = View.VISIBLE
                binding.tvRankingUpdate.text =
                    "发现更新的榜单数据：${result.currentVersion} → ${result.remoteVersion}"
            }
        }, "xyzinfo-ranking-check").start()
    }

    /** 用户确认更新：下载 → 校验 → 落盘 → 重新整理榜单。 */
    private fun applyRankingUpdate() {
        Anim.pressFeedback(binding.btnApplyRankingUpdate)
        binding.btnApplyRankingUpdate.isEnabled = false
        binding.tvRankingUpdate.text = "正在下载榜单数据…"
        Thread({
            val result = runCatching { RankingUpdater.update(this) }
                .getOrElse { RankingUpdater.UpdateResult(false, it.javaClass.simpleName) }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                binding.btnApplyRankingUpdate.isEnabled = true
                if (result.success) {
                    binding.cardRankingUpdate.visibility = View.GONE
                    // 数据换了，缓存与内存缓存一起作废，重新整理并逐条显示
                    cache = null
                    RankingCache.clear(this)
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                    startLoading()
                } else {
                    binding.tvRankingUpdate.text = "更新失败：${result.message}"
                }
            }
        }, "xyzinfo-ranking-update").start()
    }

    private fun currentKey(): String {
        val measured = SettingsRepository.lastBenchmark(this)
        // 带上榜单数据版本：在线更新过数据之后，旧缓存自动失效
        return "$metric|$brand|$deviceChipName|${measured?.single}|${measured?.multi}|${measured?.gpu}" +
            "|${GeekerwanScores.datasetVersion}"
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
        val prepared = PreparedRanking(
            key = key,
            rows = rows,
            maxValue = rows.firstOrNull()?.value ?: 1,
            summary = deviceSummary()
        )
        // 落盘：下次进页面直接读缓存，不用再筛选 / 排序 / 拼概览
        runCatching {
            RankingCache.save(
                this,
                RankingCache.Snapshot(
                    key = prepared.key,
                    summary = prepared.summary,
                    maxValue = prepared.maxValue,
                    rows = prepared.rows.map {
                        RankingCache.Row(
                            name = it.name,
                            value = it.value,
                            year = it.year,
                            measured = it.isMeasured,
                            matched = it.isMatched
                        )
                    },
                    datasetVersion = GeekerwanScores.datasetVersion,
                    savedAt = System.currentTimeMillis()
                )
            )
        }
        return prepared
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
     *
     * 1.0.1 修：铺行期间**关掉底栏的实时模糊**。底栏每次重建模糊都要把整个页面重绘进位图，
     * 而"每加一批行"都会触发布局变化 → 立刻触发一次重建。点「加载排行榜」时那一下卡死，
     * 就是这么来的（和跑分页 3D 被拖超时是同一个原因）。
     */
    private fun applyPrepared(prepared: PreparedRanking, animate: Boolean) {
        cache = prepared
        state = State.READY
        renderToken++
        val token = renderToken
        glassBar?.setBackdropEnabled(false)

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
        /*
         * **一条一条地显示**（1.0.2 改）。
         *
         * 之前是"每帧塞 5 行"：90 行在不到 20 帧里全部出现，观感就是"啪一下全刷出来"，
         * 而且同一帧里要塞 5 次 inflate + 5 次动画启动，中低端机必然掉帧。
         *
         * 现在按固定节拍一行一行加（见 [ROW_REVEAL_INTERVAL_MILLIS]）：
         * - 每帧（甚至每两帧）只 inflate 一行，主线程毫无压力；
         * - 每行自己带一个很轻的入场（淡入 + 轻微上浮），于是整张榜是"从上往下长出来"的；
         * - 90 行大约 2.4 秒铺完，随时可以滚动、可以点别的，不会卡。
         */
        val reveal = object : Runnable {
            override fun run() {
                if (token != renderToken || isFinishing) return
                if (index >= prepared.rows.size) {
                    binding.tvRankingHint.text = "共 ${prepared.rows.size} 条（已缓存，下次进来直接显示）"
                    // 铺完了再把玻璃取样打开，让底栏恢复实时模糊
                    glassBar?.setBackdropEnabled(true)
                    if (deviceRowIndex >= 0) {
                        val target = binding.layoutRanking.getChildAt(deviceRowIndex)
                        if (target != null) {
                            binding.root.post {
                                binding.root.smoothScrollTo(0, (target.top - dp(120f)).coerceAtLeast(0))
                            }
                        }
                    }
                    return
                }
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
                    duration = if (animate) 260L else 1L,
                    delay = 0L
                )
                binding.layoutRanking.addView(item.root)
                if (animate) {
                    item.root.alpha = 0f
                    item.root.translationY = dp(10f).toFloat()
                    item.root.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(ROW_FADE_IN_MILLIS)
                        .start()
                }
                index++
                binding.tvRankingHint.text = "正在显示 ${index}/${prepared.rows.size}…"
                if (index >= prepared.rows.size) {
                    // 最后一行：等它淡入结束再收尾，避免"最后一行还在淡入就提示完成"
                    handler.postDelayed(this, ROW_FADE_IN_MILLIS)
                } else {
                    handler.postDelayed(this, ROW_REVEAL_INTERVAL_MILLIS)
                }
            }
        }
        handler.post(reveal)
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
                if (selected) ThemeColors.accent(this@RankingActivity) else ContextCompat.getColor(this@RankingActivity, R.color.text_secondary)
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
        /** 一行一行的节拍：26ms ≈ 每秒 38 行，90 行约 2.4 秒铺完。 */
        const val ROW_REVEAL_INTERVAL_MILLIS = 26L

        /** 每行自己的淡入时长。 */
        const val ROW_FADE_IN_MILLIS = 180L

        /**
         * 已经生成好的榜单。放在 companion 里，退出页面再进来可以直接复用，
         * 不用重新排序、重新渲染。
         */
        private var cache: PreparedRanking? = null
    }
}
