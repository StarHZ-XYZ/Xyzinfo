package com.rjy.xyz.apps.xyzinfo.ui.benchmark

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
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
import com.rjy.xyz.apps.xyzinfo.util.Formats
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import com.rjy.xyz.apps.xyzinfo.ui.common.ThemeColors

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
    /** 状态文字上次推到界面的时间（跑分期间节流用）。 */
    @Volatile private var lastStatusPostAt = 0L
    /** 最近一次内存测试结果（0.8 新增）。 */
    private var lastMemory: MemoryBenchmark.Result? = null

    /** 3D 起不来时的原因，会写进结果明细里，让用户知道这次用了退路。 */
    private var gpu3dFailure: String? = null

    /**
     * 3D 跑分页用 startActivityForResult 的方式打开（这样它退出后会自然回到本页）。
     *
     * **刻意不用它的回调来判定结果**：跨进程 Activity 结果的回调时机在个别 ROM 上并不保证
     * （实测 HyperOS 上要么不来、要么早来），所以真正的判据是子进程写下的结果文件，见
     * [runGpu3d] 里的轮询。
     */
    private val gpu3dLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* 结果从文件读，这里不处理 */ }

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
        binding.tvStability.setInfoRow(
            if (saved.stabilityPercent > 0) {
                "稳定性：${saved.stabilityPercent}%（上次成绩已保存，可再跑一次对比）"
            } else {
                "稳定性：上次成绩已保存，可再跑一次对比"
            }
        )
        // 上次的原始速率明细也摆出来，省得用户只能看到一个孤零零的分数
        saved.detail?.takeIf { it.isNotBlank() }?.let {
            binding.tvBenchmarkDetail.setRawBlock(it)
        }
    }

    private fun startBenchmark() {
        // 进程级互斥：万一页面上出现了两个实例（标签切换 / 系统重建都可能），
        // 也绝不让两个跑分同时在跑 —— 互相抢 CPU 会把两轮成绩一起毁掉。
        if (running || runInProgress) return
        running = true
        runInProgress = true
        binding.btnStartBenchmark.isEnabled = false
        binding.btnStartBenchmark.alpha = 0.6f
        binding.progressBenchmark.visibility = View.VISIBLE
        binding.progressBenchmark.progress = 0
        binding.layoutStages.removeAllViews()
        /*
         * 跑分期间把玻璃底栏收起来，并停掉它的实时模糊。
         *
         * 这不是"看着清爽"的问题：底栏每次重建模糊都要把整个页面重绘进位图，而页面上
         * 任何布局变化（跑分每 250ms 刷一次状态文字）都会立刻触发一次重建。实测主线程
         * 因此被占满，连 `startActivity` 都被推迟了 44 秒才执行 —— 3D 跑分就是这么被拖超时的。
         */
        glassBar?.setBackdropEnabled(false)
        glassBar?.visibility = View.GONE

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
                cpuSingleDetail = cpu.stages.take(6).joinToString("\n") {
                    "${it.name}：${it.detail}"
                },
                cpuMultiDetail = cpu.stages.drop(6).joinToString("\n") {
                    "${it.name}：${it.detail}"
                },
                gpuDetail = gpu?.let { buildGpuDetail(it) }
                    ?: buildGpuFallbackDetail(),
                stabilityPercent = cpu.stabilityPercent,
                totalSeconds = (System.currentTimeMillis() - startedAt) / 1000.0,
                stages = cpu.stages
            )
            SettingsRepository.saveBenchmark(
                context = this,
                single = result.cpuSingleScore,
                multi = result.cpuMultiScore,
                gpu = result.gpuScore,
                // 明细一起存下来：换机器重新标定基准、或者用户回头核对原始速率时都有用
                detail = listOfNotNull(
                    result.cpuSingleDetail,
                    result.cpuMultiDetail,
                    result.gpuDetail,
                    lastMemory?.detail
                ).joinToString("\n"),
                stabilityPercent = result.stabilityPercent
            )
            runOnUiThread { if (!isFinishing) renderResult(result, cpu, gpu) }
        }, "xyzinfo-benchmark").start()
    }

    /**
     * GPU 阶段：**优先跑 3D 引擎**（独立进程里的 GLES 3.0 场景），
     * 只有 3D 起不来（驱动编译失败 / 进程被驱动带走 / 超时）才退回 2D 填充测试。
     *
     * 为什么要换成 3D：2D 填充对现代 GPU 太轻，画面很容易顶在垂直同步上（60/90/120 帧），
     * 于是分数被刷新率锁死，旗舰机和中端机拉不开差距 —— 测出来的是屏幕而不是 GPU。
     * 3D 场景把着色做得足够重，让 GPU 自己成为瓶颈，分数才反映真实图形性能。
     */
    private fun runGpuStress(seconds: Double, cpuSeconds: Double, totalSeconds: Double): GpuResult? {
        val three = runGpu3d(seconds, cpuSeconds, totalSeconds)
        if (three != null) return three
        return runCanvasGpuStress(seconds, cpuSeconds, totalSeconds)
    }

    /** 3D 引擎阶段：交给独立进程跑，主线程只负责显示进度和等结果。 */
    private fun runGpu3d(seconds: Double, cpuSeconds: Double, totalSeconds: Double): GpuResult? {
        val launchedAt = System.currentTimeMillis()
        gpu3dFailure = null

        runOnUiThread {
            if (isFinishing) {
                gpu3dFailure = "跑分页面已结束"
                return@runOnUiThread
            }
            glassBar?.visibility = View.GONE
            Gpu3dActivity.deleteStaleResult(this)
            gpu3dLauncher.launch(Gpu3dActivity.intent(this, seconds))
        }

        /*
         * 轮询子进程写下的结果文件，而不是等 Activity 回调：
         * 回调在个别 ROM 上不来或早来（都会让这一轮白白退化成 2D 测试），
         * 而结果文件带写入时间戳，出现在磁盘上就等于"这次 3D 真的跑完了"。
         */
        val deadline = System.currentTimeMillis() + ((seconds + 16) * 1000).toLong()
        val startedAt = System.currentTimeMillis()
        var result: Gpu3dResult? = null
        var readNote = "等待超时（子进程可能被显卡驱动带崩）"
        while (System.currentTimeMillis() < deadline) {
            val (candidate, note) = Gpu3dActivity.readResultWithReason(this, launchedAt)
            readNote = note
            if (candidate != null) {
                result = candidate
                break
            }
            val elapsed = (System.currentTimeMillis() - startedAt) / 1000.0
            setStatus(
                "3D 引擎渲染测速",
                cpuSeconds + elapsed.coerceAtMost(seconds),
                totalSeconds,
                "OpenGL ES 3.0 场景负载"
            )
            Thread.sleep(400)
        }

        /*
         * 退路测试必须等本页真的回到前台再跑：压力视图不可见 / 窗口没焦点时不会出帧，
         * 2D 填充测试就会"跑了个寂寞"（结果直接是未测）。
         */
        waitUntilVisible(10_000L)
        runOnUiThread { if (!isFinishing) glassBar?.visibility = View.VISIBLE }

        if (result == null) {
            gpu3dFailure = "3D 测试没有返回结果（$readNote）"
            return null
        }
        if (result.error != null || result.frames <= 0 || result.shadedPixelsPerSecond <= 0.0) {
            gpu3dFailure = result.error ?: "3D 测试未取得有效帧率"
            return null
        }
        return GpuResult(
            score = (result.shadedPixelsPerSecond / REFERENCE_3D_PIXELS_PER_SECOND * 1000).roundToInt(),
            framesPerSecond = result.framesPerSecond,
            lowFramesPerSecond = result.lowFramesPerSecond,
            renderer = result.renderer,
            pixelRateGiga = result.shadedPixelsPerSecond / 1e9,
            frames = result.frames,
            mode = "3D 引擎",
            passes = result.passes,
            trianglesPerFrame = result.trianglesPerFrame
        )
    }

    /** 等本页重新可见（最多 [timeoutMillis]），避免在后台跑需要绘制帧的测试。 */
    private fun waitUntilVisible(timeoutMillis: Long) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val visible = CountDownLatch(1)
            var hasFocus = false
            runOnUiThread {
                hasFocus = !isFinishing && window.decorView.hasWindowFocus()
                visible.countDown()
            }
            visible.await(2, TimeUnit.SECONDS)
            if (hasFocus) return
            Thread.sleep(250)
        }
    }

    /**
     * 2D 填充退路：全屏铺开压力视图，跑满 [seconds] 秒。
     *
     * 全屏是 0.7 的关键改动——0.5 版只有 180dp 高，填充量太小，
     * 很多机器直接顶到垂直同步上限，测出来的其实是刷新率而不是 GPU 性能。
     */
    private fun runCanvasGpuStress(
        seconds: Double,
        cpuSeconds: Double,
        totalSeconds: Double
    ): GpuResult? {
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
            frames = value.frames,
            mode = "2D 填充（3D 退路）"
        )
    }

    private fun setStatus(stage: String, elapsed: Double, total: Double, live: String? = null) {
        // 状态刷新节流：600ms 一次足够看清进度，也少给主线程添活
        val now = System.currentTimeMillis()
        if (now - lastStatusPostAt < STATUS_THROTTLE_MS) return
        lastStatusPostAt = now
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
        runInProgress = false
        binding.btnStartBenchmark.isEnabled = true
        binding.btnStartBenchmark.animate().alpha(1f).setDuration(Anim.DURATION_SHORT).start()
        glassBar?.visibility = View.VISIBLE
        glassBar?.setBackdropEnabled(true)
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
        val scoreColor = ThemeColors.accent(this)

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

    /**
     * GPU 明细：3D 模式额外说明通道数（自适应加压的结果）与每帧三角形数量，
     * 这样用户能看出"这一轮到底压了多重"；退路的 2D 模式也会写明原因。
     */
    private fun buildGpuDetail(gpu: GpuResult): String = buildString {
        append(
            String.format(
                Locale.US,
                "GPU（%s）：%.1f 帧/秒｜1%% low：%.1f 帧/秒｜着色率：%.1f Gpx/s｜%d 帧",
                gpu.mode, gpu.framesPerSecond, gpu.lowFramesPerSecond, gpu.pixelRateGiga, gpu.frames
            )
        )
        if (gpu.mode.startsWith("3D")) {
            append(String.format(
                Locale.US,
                "\n3D 负载：每帧 %d 个着色通道 ｜ %s 个三角形 ｜ 着色率 %.2f Gpx/s ｜ %s",
                gpu.passes, Formats.grouped(gpu.trianglesPerFrame), gpu.pixelRateGiga, gpu.renderer
            ))
        }
        gpu3dFailure?.let { append("\n注：$it，已使用 2D 填充退路") }
    }

    /** GPU 完全没出成绩时的说明：把 3D 那一步卡在哪写清楚，方便定位（也方便用户反馈）。 */
    private fun buildGpuFallbackDetail(): String = buildString {
        append("GPU：${deviceGpuName ?: "未知"}（本次未取得有效帧率）")
        gpu3dFailure?.let { append("\n原因：$it") }
    }

    private companion object {
        /** 进程级互斥标志：同一时刻只允许一轮跑分。 */
        @Volatile
        private var runInProgress = false

        /**
         * GPU 填充率基准：以骁龙 778G（0.5 版真机 33 帧/秒 × 约 1.05 亿像素/帧）折算的
         * 3.5 Gpx/s 为 1000 分，这样中端机仍在 1000 附近，旗舰能拉开差距。
         */
        const val REFERENCE_PIXELS_PER_SECOND = 3.5e9

        /**
         * 3D 引擎的着色率基准：骁龙 778G（Adreno 642L）真机实测值，对应 1000 分。
         * 与参考榜单给它的 GPU = 1000 同一刻度。
         */
        const val REFERENCE_3D_PIXELS_PER_SECOND = 1.18e8

        /** 状态文字最快多久推一次界面（跑分期间主线程要留给测量本身）。 */
        const val STATUS_THROTTLE_MS = 600L
    }
}
