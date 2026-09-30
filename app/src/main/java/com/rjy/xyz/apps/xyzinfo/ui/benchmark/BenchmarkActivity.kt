package com.rjy.xyz.apps.xyzinfo.ui.benchmark

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import com.rjy.xyz.apps.xyzinfo.data.SocInfoProvider
import com.rjy.xyz.apps.xyzinfo.data.benchmark.CpuBenchmark
import com.rjy.xyz.apps.xyzinfo.data.benchmark.GeekerwanScores
import com.rjy.xyz.apps.xyzinfo.data.benchmark.MemoryBenchmark
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityBenchmarkBinding
import com.rjy.xyz.apps.xyzinfo.model.BenchmarkResult
import com.rjy.xyz.apps.xyzinfo.model.BenchmarkStage
import com.rjy.xyz.apps.xyzinfo.model.GpuResult
import com.rjy.xyz.apps.xyzinfo.ui.common.Anim
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassBottomBar
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.animateTo
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.countUpWith
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import com.rjy.xyz.apps.xyzinfo.ui.common.setRawBlock
import com.rjy.xyz.apps.xyzinfo.util.Labels
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * 性能测试页（v0.7 重写）。
 *
 * - CPU 阶段由 [CpuBenchmark] 按时间跑满（标准 60 秒以上），并给出稳定性；
 * - GPU 阶段把压力视图**全屏**铺开，20 秒 / 4 种场景轮换，同时给出 1% low 与填充率；
 * - 结果会存起来，排行榜页据此插入「本机实测」那一行。
 */
class BenchmarkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBenchmarkBinding
    private var glassBar: GlassBottomBar? = null

    private var running = false
    private var deviceChipName: String? = null
    private var deviceGpuName: String? = null
    /** 最近一次内存测试结果（0.8 新增）。 */
    private var lastMemory: MemoryBenchmark.Result? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBenchmarkBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        glassBar = GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_BENCHMARK)

        // 跑分期间别让屏幕熄灭
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.btnStartBenchmark.setOnClickListener { startBenchmark() }
        binding.btnOpenRanking.setOnClickListener {
            startActivity(
                Intent(this, RankingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }
        Anim.pressFeedback(binding.btnOpenRanking)

        showChipContext()
        showSavedResult()
    }

    /** 先显示本机芯片与它在极客湾榜单里的参考水平，让用户知道对比基准。 */
    private fun showChipContext() {
        val spec = runCatching { SocInfoProvider.findSpec() }.getOrNull()
        deviceChipName = spec?.displayName
        deviceGpuName = spec?.gpuName
        val reference = GeekerwanScores.match(deviceChipName)
        val text = buildString {
            append("本机芯片：")
            append(deviceChipName ?: Labels.UNKNOWN)
            if (reference != null) {
                append("\n榜单参考：综合 ")
                append(reference.composite)
                append("（单核 ")
                append(reference.single)
                append(" / 多核 ")
                append(reference.multi)
                append(" / GPU ")
                append(reference.gpu)
                append("）")
            } else {
                append("\n该芯片暂未收录到极客湾榜单")
            }
        }
        binding.tvCompareChip.setInfoRow(text)
    }

    /** 进入页面时把上次的成绩也显示出来，方便对比这次是不是更稳。 */
    private fun showSavedResult() {
        val saved = SettingsRepository.lastBenchmark(this) ?: return
        binding.tvCpuSingleScore.setInfoRow("CPU 单核：${saved.single}")
        binding.tvCpuMultiScore.setInfoRow("CPU 多核：${saved.multi}")
        binding.tvGpuScore.setInfoRow("GPU：${saved.gpu ?: "未测"}")
        binding.tvStability.setInfoRow("稳定性：上次成绩已保存，可再跑一次对比")
    }

    private fun startBenchmark() {
        if (running) return
        running = true
        binding.btnStartBenchmark.isEnabled = false
        binding.btnStartBenchmark.alpha = 0.6f
        binding.progressBenchmark.visibility = View.VISIBLE
        binding.progressBenchmark.progress = 0
        binding.layoutStages.removeAllViews()

        val deep = SettingsRepository.deepBenchmarkEnabled(this)
        val cpuSeconds = CpuBenchmark.totalSeconds(deep)
        val memSeconds = if (deep) 30.0 else 18.0
        val gpuSeconds = if (deep) 30.0 else 20.0
        val totalSeconds = cpuSeconds + memSeconds + gpuSeconds
        setStatus("测试中，请勿离开本页面", 0.0, totalSeconds)

        Thread({
            val startedAt = System.currentTimeMillis()
            val cpu = CpuBenchmark.run(deep) { progress ->
                setStatus(progress.stageName, progress.elapsedSeconds, totalSeconds, progress.live)
            }
            // 内存测试：顺序带宽 / 随机访问延迟 / 分配速率
            val memory = MemoryBenchmark.run(memSeconds) { elapsed ->
                setStatus("内存测试", cpuSeconds + elapsed, totalSeconds, "顺序带宽 · 随机延迟 · 分配速率")
            }
            lastMemory = memory
            val gpu = runGpuStress(gpuSeconds, cpuSeconds + memSeconds, totalSeconds)

            val result = BenchmarkResult(
                cpuSingleScore = cpu.singleScore,
                cpuMultiScore = cpu.multiScore,
                gpuScore = gpu?.score,
                cpuSingleDetail = cpu.stages.take(5).joinToString("\n") {
                    "${it.name}：${it.detail}"
                },
                cpuMultiDetail = cpu.stages.drop(5).joinToString("\n") {
                    "${it.name}：${it.detail}"
                },
                gpuDetail = gpu?.let {
                    String.format(
                        Locale.US,
                        "GPU：%.1f 帧/秒｜1%% low：%.1f 帧/秒｜填充率：%.1f Gpx/s｜%d 帧",
                        it.framesPerSecond, it.lowFramesPerSecond, it.pixelRateGiga, it.frames
                    )
                } ?: "GPU：${deviceGpuName ?: "未知"}（本次未取得有效帧率）",
                stabilityPercent = cpu.stabilityPercent,
                totalSeconds = (System.currentTimeMillis() - startedAt) / 1000.0,
                stages = cpu.stages
            )
            SettingsRepository.saveBenchmark(
                this, result.cpuSingleScore, result.cpuMultiScore, result.gpuScore
            )
            runOnUiThread { if (!isFinishing) renderResult(result, cpu, gpu) }
        }, "xyzinfo-benchmark").start()
    }

    /**
     * GPU 阶段：全屏铺开压力视图，跑满 [seconds] 秒。
     *
     * 全屏是 0.7 的关键改动——0.5 版只有 180dp 高，填充量太小，
     * 很多机器直接顶到垂直同步上限，测出来的其实是刷新率而不是 GPU 性能。
     */
    private fun runGpuStress(seconds: Double, cpuSeconds: Double, totalSeconds: Double): GpuResult? {
        val latch = CountDownLatch(1)
        var raw: GpuCanvasStressView.RawResult? = null

        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            glassBar?.visibility = View.GONE
            binding.viewGpuOverlay.visibility = View.VISIBLE
            binding.viewGpuOverlay.alpha = 0f
            binding.viewGpuOverlay.animate()
                .alpha(1f)
                .setDuration(Anim.DURATION_MEDIUM)
                .start()
            binding.gpuStressView.startMeasure(
                seconds,
                { elapsed, _, fps ->
                    val scene = (elapsed / 5.0).toInt().coerceIn(0, 3) + 1
                    binding.tvGpuScene.text = String.format(
                        Locale.US,
                        "第 %d/4 组负载 ｜ 剩余 %d 秒 ｜ 当前 %d 帧/秒",
                        scene,
                        (seconds - elapsed).roundToInt().coerceAtLeast(0),
                        fps.roundToInt()
                    )
                    setStatus("GPU 渲染测速", cpuSeconds + elapsed, totalSeconds)
                },
                { value ->
                    raw = value
                    latch.countDown()
                }
            )
        }

        latch.await((seconds + 15).toLong(), TimeUnit.SECONDS)

        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            binding.gpuStressView.stopMeasure()
            binding.viewGpuOverlay.visibility = View.GONE
            glassBar?.visibility = View.VISIBLE
        }

        val value = raw ?: return null
        if (value.frames < 30 || value.pixelsPerSecond <= 0.0) return null
        return GpuResult(
            score = (value.pixelsPerSecond / REFERENCE_PIXELS_PER_SECOND * 1000).roundToInt(),
            framesPerSecond = value.framesPerSecond,
            lowFramesPerSecond = value.lowFramesPerSecond,
            renderer = deviceGpuName ?: "硬件加速 2D 渲染",
            pixelRateGiga = value.pixelsPerSecond / 1e9,
            frames = value.frames
        )
    }

    private fun setStatus(stage: String, elapsed: Double, total: Double, live: String? = null) {
        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            binding.tvBenchmarkStatus.setInfoRow("当前阶段：$stage")
            binding.tvBenchmarkStage.text = buildString {
                append("已用 ")
                append(String.format(Locale.US, "%.0f", elapsed))
                append(" 秒 / 共 ")
                append(String.format(Locale.US, "%.0f", total))
                append(" 秒")
                if (live != null) {
                    append(" ｜ ")
                    append(live)
                }
            }
            binding.progressBenchmark.animateTo((elapsed / total * 100).roundToInt())
        }
    }

    private fun renderResult(
        result: BenchmarkResult,
        cpu: CpuBenchmark.Result,
        gpu: GpuResult?
    ) {
        running = false
        binding.btnStartBenchmark.isEnabled = true
        binding.btnStartBenchmark.animate().alpha(1f).setDuration(Anim.DURATION_SHORT).start()
        binding.progressBenchmark.animateTo(100)
        binding.tvBenchmarkStatus.setInfoRow("测试完成：总耗时 ${result.totalSeconds.roundToInt()} 秒")
        binding.tvBenchmarkStage.text = "可再点一次看稳定性差异（连续跑分时分数通常会略降）"

        binding.tvCpuSingleScore.countUpWith(
            result.cpuSingleScore,
            render = { "CPU 单核：$it" }
        )
        binding.tvCpuMultiScore.countUpWith(
            result.cpuMultiScore,
            render = { "CPU 多核：$it" }
        )
        binding.tvGpuScore.countUpWith(
            result.gpuScore ?: 0,
            render = { if (result.gpuScore == null) "GPU：未取得有效帧率" else "GPU：$it" }
        )
        binding.tvStability.setInfoRow(
            "稳定性：${result.stabilityPercent}% ｜ 多核加速比：${
                String.format(Locale.US, "%.2fx", cpu.multiSpeedup)
            } ｜ ${cpu.threads} 线程"
        )
        lastMemory?.let { memory ->
            binding.tvMemoryScore.countUpWith(memory.score, render = { "内存：$it" })
        }
        binding.tvBenchmarkDetail.setRawBlock(
            listOfNotNull(
                result.cpuSingleDetail,
                result.cpuMultiDetail,
                result.gpuDetail,
                lastMemory?.detail
            ).joinToString("\n")
        )
        renderStages(result.stages)
    }

    /** 阶段明细：每行「阶段名 + 原始指标」配一个指数分。 */
    private fun renderStages(stages: List<BenchmarkStage>) {
        binding.layoutStages.removeAllViews()
        val nameColor = ContextCompat.getColor(this, R.color.text_primary)
        val detailColor = ContextCompat.getColor(this, R.color.text_secondary)
        val scoreColor = ContextCompat.getColor(this, R.color.accent)

        stages.forEach { stage ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(7f), 0, dp(7f))
            }
            val left = TextView(this).apply {
                text = "${stage.name}\n${stage.detail}"
                textSize = 12.5f
                setTextColor(nameColor)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val value = TextView(this).apply {
                text = stage.score.toString()
                textSize = 13f
                setTextColor(scoreColor)
                setPadding(dp(10f), 0, 0, 0)
            }
            row.addView(left)
            row.addView(value)
            binding.layoutStages.addView(row)
        }
        binding.layoutStages.setPadding(0, dp(4f), 0, 0)
        Anim.revealChildren(binding.layoutStages, step = 40L)
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        /**
         * GPU 填充率基准：以骁龙 778G（0.5 版真机 33 帧/秒 × 约 1.05 亿像素/帧）折算的
         * 3.5 Gpx/s 为 1000 分，这样中端机仍在 1000 附近，旗舰能拉开差距。
         */
        const val REFERENCE_PIXELS_PER_SECOND = 3.5e9
    }
}
