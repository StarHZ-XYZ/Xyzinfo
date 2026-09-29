package com.rjy.xyz.apps.xyzinfo.ui.benchmark

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.SocInfoProvider
import com.rjy.xyz.apps.xyzinfo.data.benchmark.CpuBenchmark
import com.rjy.xyz.apps.xyzinfo.data.benchmark.ReferenceScores
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityBenchmarkBinding
import com.rjy.xyz.apps.xyzinfo.databinding.ItemBenchmarkRowBinding
import com.rjy.xyz.apps.xyzinfo.model.BenchmarkResult
import com.rjy.xyz.apps.xyzinfo.model.ChipReference
import com.rjy.xyz.apps.xyzinfo.model.GpuResult
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import com.rjy.xyz.apps.xyzinfo.ui.common.setRawBlock
import com.rjy.xyz.apps.xyzinfo.util.Labels
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * 性能测试页：CPU（整数/浮点/压缩/多核）与 GPU 跑分，并与内置参考机型对比。
 */
class BenchmarkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBenchmarkBinding

    private var running = false
    private var deviceChipName: String? = null
    private var deviceGpuName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBenchmarkBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()

        // 跑分过程中别让屏幕熄灭
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.btnStartBenchmark.setOnClickListener { startBenchmark() }
        showChipContext()
    }

    /** 先显示本机芯片与它的参考指数，让用户知道对比基准。 */
    private fun showChipContext() {
        val spec = runCatching { SocInfoProvider.findSpec() }.getOrNull()
        deviceChipName = spec?.displayName
        deviceGpuName = spec?.gpuName
        val reference = ReferenceScores.match(deviceChipName)

        val text = buildString {
            append("本机芯片：")
            append(deviceChipName ?: Labels.UNKNOWN)
            if (reference != null) {
                append("（参考指数 单核 ")
                append(reference.cpuSingle)
                append(" / 多核 ")
                append(reference.cpuMulti)
                append(" / GPU ")
                append(reference.gpu)
                append("）")
            }
        }
        binding.tvCompareChip.setInfoRow(text)
    }

    private fun startBenchmark() {
        if (running) return
        running = true
        binding.btnStartBenchmark.isEnabled = false
        binding.progressBenchmark.visibility = View.VISIBLE
        binding.layoutCompare.removeAllViews()
        binding.tvBenchmarkStatus.text = "准备中…"

        Thread({
            val cpu = CpuBenchmark.run { step -> postStatus(step) }
            val gpu = runGpuStress()

            val result = BenchmarkResult(
                cpuSingleScore = cpu.singleScore,
                cpuMultiScore = cpu.multiScore,
                gpuScore = gpu?.score,
                cpuSingleDetail = String.format(
                    Locale.US,
                    "单核整数：%.0f Mops/s\n单核浮点：%.0f Mflops/s\n压缩：%.0f MB/s",
                    cpu.integerOpsPerSecond / 1e6,
                    cpu.floatFlopsPerSecond / 1e6,
                    cpu.compressMbPerSecond
                ),
                cpuMultiDetail = String.format(
                    Locale.US,
                    "多核整数：%.0f Mops/s（%d 线程）",
                    cpu.multiOpsPerSecond / 1e6,
                    cpu.cores
                ),
                gpuDetail = gpu?.let {
                    String.format(Locale.US, "GPU：%.0f 帧/秒（%s）", it.framesPerSecond, it.renderer)
                } ?: "GPU：${deviceGpuName ?: "未知"}（未跑分：部分机型驱动在着色器编译时会原生崩溃，压力测试暂缓启用）"
            )

            runOnUiThread { if (!isFinishing) renderResult(result) }
        }, "xyzinfo-benchmark").start()
    }

    /**
     * GPU 测试走硬件加速 2D 管线：显示压力视图 → 等它报回帧率 → 收起。
     *
     * 不用 OpenGL 着色器是因为部分机型（HyperOS + Adreno 6xx）驱动在编译着色器时
     * 会原生崩溃，直接把 App 杀掉。
     */
    private fun runGpuStress(): GpuResult? {
        val latch = CountDownLatch(1)
        var framesPerSecond: Double? = null

        postStatus("GPU 渲染测速")
        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            binding.gpuStressView.visibility = View.VISIBLE
            binding.gpuStressView.startMeasure { value ->
                framesPerSecond = value
                latch.countDown()
            }
        }

        latch.await(GPU_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            binding.gpuStressView.stopMeasure()
            binding.gpuStressView.visibility = View.GONE
        }

        val fps = framesPerSecond ?: return null
        return GpuResult(
            score = (fps * GPU_SCORE_PER_FPS).roundToInt(),
            framesPerSecond = fps,
            renderer = deviceGpuName ?: "硬件加速 2D 渲染"
        )
    }

    private fun postStatus(step: String) {
        runOnUiThread {
            if (!isFinishing) binding.tvBenchmarkStatus.text = "$step…"
        }
    }

    private fun renderResult(result: BenchmarkResult) {
        running = false
        binding.btnStartBenchmark.isEnabled = true
        binding.progressBenchmark.visibility = View.GONE
        binding.tvBenchmarkStatus.text = "测试完成，可再点一次看稳定性"

        binding.tvCpuSingleScore.setInfoRow("CPU 单核：${result.cpuSingleScore}")
        binding.tvCpuMultiScore.setInfoRow("CPU 多核：${result.cpuMultiScore}")
        binding.tvGpuScore.setInfoRow(
            "GPU：${result.gpuScore?.toString() ?: "未跑分（见下方说明）"}"
        )
        binding.tvBenchmarkDetail.setRawBlock(
            listOfNotNull(result.cpuSingleDetail, result.cpuMultiDetail, result.gpuDetail)
                .joinToString("\n")
        )
        renderRanking(result)
    }

    /**
     * 性能排行榜：本机与全部内置参考机型放在同一张榜里按多核分数排名，
     * 单核 / 多核并排显示，GPU 单独一列。
     */
    private fun renderRanking(result: BenchmarkResult) {
        val device = ChipReference(
            name = deviceChipName ?: "本机",
            cpuSingle = result.cpuSingleScore,
            cpuMulti = result.cpuMultiScore,
            gpu = result.gpuScore ?: 0
        )

        val rows = (ReferenceScores.all + device).sortedByDescending { it.cpuMulti }
        val deviceRank = rows.indexOfFirst { it === device } + 1
        val deviceColor = ContextCompat.getColor(this, R.color.accent)
        val referenceColor = ContextCompat.getColor(this, R.color.text_secondary)

        binding.tvCompareChip.setInfoRow(
            "本机排名：第 $deviceRank 名 / 共 ${rows.size} 款" +
                "（本机 单核 ${device.cpuSingle} ｜ 多核 ${device.cpuMulti} ｜ " +
                "GPU ${device.gpu.takeIf { it > 0 } ?: "—"}）"
        )

        binding.layoutCompare.removeAllViews()
        rows.forEachIndexed { index, row ->
            val item = ItemBenchmarkRowBinding.inflate(layoutInflater, binding.layoutCompare, false)
            val isDevice = row === device

            item.tvRowRank.text = "#${index + 1}"
            item.tvRowName.text = if (isDevice) "${row.name}（本机）" else row.name
            item.tvRowName.setTextColor(if (isDevice) deviceColor else referenceColor)
            item.tvRowSingle.text = row.cpuSingle.toString()
            item.tvRowMulti.text = row.cpuMulti.toString()
            item.tvRowMulti.setTextColor(if (isDevice) deviceColor else referenceColor)
            item.tvRowGpu.text = if (row.gpu > 0) row.gpu.toString() else "—"
            item.tvRowGpu.setTextColor(if (isDevice) deviceColor else referenceColor)

            binding.layoutCompare.addView(item.root)
        }
    }

    private companion object {
        const val GPU_TIMEOUT_SECONDS = 20L

        /** GPU 分数换算：中端机（骁龙 778G）约 25 帧 → 900 分左右，后续按真机校准。 */
        const val GPU_SCORE_PER_FPS = 27.0
    }
}
