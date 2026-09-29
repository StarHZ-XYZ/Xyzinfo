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
import com.rjy.xyz.apps.xyzinfo.data.benchmark.GpuResult
import com.rjy.xyz.apps.xyzinfo.data.benchmark.GpuStressRenderer
import com.rjy.xyz.apps.xyzinfo.data.benchmark.ReferenceScores
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityBenchmarkBinding
import com.rjy.xyz.apps.xyzinfo.databinding.ItemBenchmarkRowBinding
import com.rjy.xyz.apps.xyzinfo.model.BenchmarkResult
import com.rjy.xyz.apps.xyzinfo.model.ChipReference
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import com.rjy.xyz.apps.xyzinfo.ui.common.setRawBlock
import com.rjy.xyz.apps.xyzinfo.util.Labels
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * 性能测试页：CPU（整数/浮点/压缩/多核）与 GPU 跑分，并与内置参考机型对比。
 */
class BenchmarkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBenchmarkBinding

    private var running = false
    private var deviceChipName: String? = null

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
        deviceChipName = runCatching { SocInfoProvider.findSpec()?.displayName }.getOrNull()
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
                }
            )

            runOnUiThread { if (!isFinishing) renderResult(result) }
        }, "xyzinfo-benchmark").start()
    }

    /**
     * GPU 测试必须显示在屏幕上（离屏 pbuffer 在部分高通机型上渲染不落盘），
     * 所以这里让出主线程：显示 GLSurfaceView → 等渲染器报回帧率 → 收起。
     */
    private fun runGpuStress(): GpuResult? {
        val latch = CountDownLatch(1)
        var measured: GpuResult? = null
        val renderer = GpuStressRenderer { result ->
            measured = result
            latch.countDown()
        }

        postStatus("GPU 渲染测速")
        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            binding.glSurface.visibility = View.VISIBLE
            binding.glSurface.setRenderer(renderer)
            binding.glSurface.onResume()
            binding.glSurface.queueEvent { renderer.startMeasure() }
        }

        latch.await(GPU_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            binding.glSurface.onPause()
            binding.glSurface.visibility = View.GONE
        }
        return measured
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
            "GPU：${result.gpuScore?.toString() ?: "本次未完成（设备或驱动不支持离屏渲染）"}"
        )
        binding.tvBenchmarkDetail.setRawBlock(
            listOfNotNull(result.cpuSingleDetail, result.cpuMultiDetail, result.gpuDetail)
                .joinToString("\n")
        )
        renderComparison(result)
    }

    /** 列出与本机多核分数最接近的参考机型，用条形图横向比较。 */
    private fun renderComparison(result: BenchmarkResult) {
        val device = ChipReference(
            name = deviceChipName ?: "本机",
            cpuSingle = result.cpuSingleScore,
            cpuMulti = result.cpuMultiScore,
            gpu = result.gpuScore ?: 0
        )

        val nearby = ReferenceScores.all
            .sortedBy { abs(it.cpuMulti - device.cpuMulti) }
            .take(NEARBY_REFERENCE_COUNT)
            .sortedByDescending { it.cpuMulti }

        val rows = listOf(device) + nearby
        val maxMulti = rows.maxOf { it.cpuMulti }.coerceAtLeast(1)
        val deviceColor = ContextCompat.getColor(this, R.color.accent)
        val referenceColor = ContextCompat.getColor(this, R.color.text_secondary)

        binding.tvCompareChip.setInfoRow(
            "对比基准：多核分数 ｜ 本机 ${device.cpuMulti}，下列为最接近的 $NEARBY_REFERENCE_COUNT 款参考机型"
        )

        binding.layoutCompare.removeAllViews()
        rows.forEach { row ->
            val item = ItemBenchmarkRowBinding.inflate(layoutInflater, binding.layoutCompare, false)
            val isDevice = row === device

            item.tvRowName.text = if (isDevice) "本机" else row.name
            item.tvRowName.setTextColor(if (isDevice) deviceColor else referenceColor)

            val percent = ReferenceScores.percentOfMax(row.cpuMulti, maxMulti)
            (item.rowBarFill.layoutParams as LinearLayout.LayoutParams).weight = percent.toFloat()
            (item.rowBarRest.layoutParams as LinearLayout.LayoutParams).weight =
                (100 - percent).toFloat()

            item.tvRowValue.text = if (isDevice) {
                row.cpuMulti.toString()
            } else {
                ratioLabel(row.cpuMulti, device.cpuMulti)
            }

            binding.layoutCompare.addView(item.root)
        }
    }

    private fun ratioLabel(referenceMulti: Int, deviceMulti: Int): String {
        if (deviceMulti <= 0) return "—"
        return String.format(Locale.US, "×%.2f", referenceMulti.toDouble() / deviceMulti)
    }

    private companion object {
        const val NEARBY_REFERENCE_COUNT = 15
        const val GPU_TIMEOUT_SECONDS = 20L
    }
}
