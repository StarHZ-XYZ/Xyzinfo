package com.rjy.xyz.apps.xyzinfo.ui.benchmark

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityGpu3dBinding
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * 3D 引擎跑分页（**独立进程**）。
 *
 * 为什么单独开一个进程：GLSL 编译在个别 Adreno + HyperOS 组合上会触发驱动级 SIGSEGV，
 * 原生崩溃捕不住，主进程会一起挂掉。放到 `:gpu` 进程里，最坏情况是这一页崩掉，
 * 主流程等超时后自动退回 2D 填充测试，跑分照样能跑完 —— 只是结果里会注明是退路。
 *
 * 结果通过私有目录里的一个 JSON 文件回传（两个进程共用同一个 files 目录），
 * 并且带上写入时间戳，避免主流程误读上一次的旧结果。
 */
class Gpu3dActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGpu3dBinding
    private val handler = Handler(Looper.getMainLooper())
    private var finished = false
    private var watchdog: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGpu3dBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 跑分期间别熄屏
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val seconds = intent.getDoubleExtra(EXTRA_SECONDS, DEFAULT_SECONDS)
        val renderer = Gpu3dRenderer(
            seconds = seconds,
            onProgress = { elapsed, total, fps ->
                handler.post { showStatus(elapsed, total, fps) }
            },
            // 渲染器回调发生在 GL 线程，而 finish() 必须在主线程调
            onFinished = { result -> handler.post { submit(result) } }
        )

        binding.gpu3dSurface.setEGLContextClientVersion(3)
        binding.gpu3dSurface.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        binding.gpu3dSurface.setRenderer(renderer)
        binding.gpu3dSurface.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        // 兜底：GL 起不来 / 驱动把帧吞了 / 迟迟不回调，都不能让主流程一直等
        val guard = Runnable {
            if (!finished) {
                submit(
                    Gpu3dResult(
                        frames = 0, framesPerSecond = 0.0, lowFramesPerSecond = 0.0,
                        shadedPixelsPerSecond = 0.0, passes = 0, trianglesPerFrame = 0,
                        renderer = "未知", elapsedSeconds = 0.0,
                        timestamp = System.currentTimeMillis(),
                        error = renderer.failureReason() ?: "3D 测试超时（驱动未响应）"
                    )
                )
            }
        }
        watchdog = guard
        handler.postDelayed(guard, ((seconds + 8) * 1000).toLong())
    }

    override fun onResume() {
        super.onResume()
        binding.gpu3dSurface.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.gpu3dSurface.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        watchdog?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
    }

    private fun showStatus(elapsed: Double, total: Double, fps: Double) {
        if (finished || isFinishing) return
        binding.tvGpu3dStatus.text = String.format(
            Locale.US,
            "3D 场景渲染中 ｜ 剩余 %.0f 秒 ｜ 当前 %.0f 帧/秒",
            (total - elapsed).coerceAtLeast(0.0),
            fps
        )
    }

    private fun submit(result: Gpu3dResult) {
        if (finished) return
        finished = true
        val ok = runCatching { writeResult(result) }.isSuccess
        setResult(if (ok && result.error == null && result.frames > 0) RESULT_OK else RESULT_CANCELED)
        finish()
    }

    private fun writeResult(result: Gpu3dResult) {
        val json = JSONObject().apply {
            put("frames", result.frames)
            put("fps", result.framesPerSecond)
            put("lowFps", result.lowFramesPerSecond)
            put("pixelsPerSecond", result.shadedPixelsPerSecond)
            put("passes", result.passes)
            put("triangles", result.trianglesPerFrame)
            put("renderer", result.renderer)
            put("elapsed", result.elapsedSeconds)
            put("timestamp", System.currentTimeMillis())
            put("error", result.error ?: "")
        }
        File(filesDir, RESULT_FILE).writeText(json.toString())
    }

    companion object {
        const val EXTRA_SECONDS = "seconds"
        const val RESULT_FILE = "gpu3d_result.json"
        private const val DEFAULT_SECONDS = 20.0

        fun intent(activity: Activity, seconds: Double): Intent =
            Intent(activity, Gpu3dActivity::class.java).putExtra(EXTRA_SECONDS, seconds)

        /** 开跑前先清掉上一次的结果，免得把旧成绩当成这次的。 */
        fun deleteStaleResult(context: Context) {
            runCatching { File(context.filesDir, RESULT_FILE).delete() }
        }

        /** 读取结果；[notBefore] 之前写下的旧结果一律不认。 */
        fun readResult(context: Context, notBefore: Long): Gpu3dResult? =
            readResultWithReason(context, notBefore).first

        /**
         * 同 [readResult]，但把失败原因一起返回。
         *
         * 跨进程取结果这条链路（删旧文件 → 子进程写文件 → 主进程读文件）出问题时，
         * "没拿到成绩"这句提示帮不上忙，所以这里把卡在哪一步说清楚，
         * 界面与已保存的明细都会带上它。
         */
        fun readResultWithReason(context: Context, notBefore: Long): Pair<Gpu3dResult?, String> {
            val file = File(context.filesDir, RESULT_FILE)
            if (!file.exists()) return null to "3D 结果文件不存在（子进程可能没跑起来）"
            return runCatching {
                val json = JSONObject(file.readText())
                val timestamp = json.optLong("timestamp", 0L)
                if (timestamp < notBefore) {
                    return null to "拿到的是上一次的旧结果（$timestamp < $notBefore）"
                }
                val error = json.optString("error").takeIf { it.isNotEmpty() }
                val result = Gpu3dResult(
                    frames = json.optInt("frames"),
                    framesPerSecond = json.optDouble("fps", 0.0),
                    lowFramesPerSecond = json.optDouble("lowFps", 0.0),
                    shadedPixelsPerSecond = json.optDouble("pixelsPerSecond", 0.0),
                    passes = json.optInt("passes"),
                    trianglesPerFrame = json.optInt("triangles"),
                    renderer = json.optString("renderer"),
                    elapsedSeconds = json.optDouble("elapsed", 0.0),
                    timestamp = timestamp,
                    error = error
                )
                result to "ok"
            }.getOrElse { null to "结果文件解析失败：${it.javaClass.simpleName}: ${it.message}" }
        }
    }
}
