package com.rjy.xyz.apps.xyzinfo.data.benchmark

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GPU 压力渲染器。
 *
 * 在 GLSurfaceView 上持续渲染一段高负载片元着色器（每个像素上千次三角函数迭代），
 * 统计固定时间内的帧数得到 GPU 分数。
 *
 * 之前试过离屏 EGL pbuffer，在部分高通机型上回读不到像素（渲染没真正落盘），
 * 所以改成屏幕上渲染：虽然会被垂直同步封顶，但至少保证测的是真 GPU。
 */
class GpuStressRenderer(
    private val onMeasured: (GpuResult) -> Unit
) : GLSurfaceView.Renderer {

    @Volatile
    private var program = 0

    @Volatile
    private var positionHandle = -1

    @Volatile
    private var measuring = false

    @Volatile
    private var frames = 0

    @Volatile
    private var measureStartNanos = 0L

    @Volatile
    private var rendererName = "未知"

    /** 由界面触发开始计时；第一帧只用来对齐时间基准。 */
    fun startMeasure() {
        frames = 0
        measureStartNanos = 0L
        measuring = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = createProgram() ?: 0
        positionHandle = if (program != 0) {
            GLES20.glGetAttribLocation(program, "aPosition")
        } else {
            -1
        }
        rendererName = GLES20.glGetString(GLES20.GL_RENDERER) ?: "未知"
        GLES20.glClearColor(0.02f, 0.03f, 0.05f, 1f)
        Log.i(TAG, "GPU 渲染器: $rendererName, program=$program")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (program == 0 || positionHandle < 0) return

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, VERTICES)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT)

        if (!measuring) return
        if (measureStartNanos == 0L) {
            measureStartNanos = System.nanoTime()
            return
        }

        frames++
        val seconds = (System.nanoTime() - measureStartNanos) / 1_000_000_000.0
        if (seconds >= MEASURE_SECONDS) {
            measuring = false
            val framesPerSecond = frames / seconds
            onMeasured(
                GpuResult(
                    score = (framesPerSecond * SCORE_PER_FPS).toInt(),
                    framesPerSecond = framesPerSecond,
                    renderer = rendererName
                )
            )
        }
    }

    private fun createProgram(): Int? {
        val vertexShader = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        if (vertexShader == null) return null

        val fragmentShader = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        if (fragmentShader == null) {
            GLES20.glDeleteShader(vertexShader)
            return null
        }

        val linked = GLES20.glCreateProgram()
        GLES20.glAttachShader(linked, vertexShader)
        GLES20.glAttachShader(linked, fragmentShader)
        GLES20.glLinkProgram(linked)

        val status = IntArray(1)
        GLES20.glGetProgramiv(linked, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        if (status[0] != GLES20.GL_TRUE) {
            Log.w(TAG, "着色器链接失败: ${GLES20.glGetProgramInfoLog(linked)}")
            GLES20.glDeleteProgram(linked)
            return null
        }
        return linked
    }

    private fun compile(type: Int, source: String): Int? {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            Log.w(TAG, "着色器编译失败: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return null
        }
        return shader
    }

    private companion object {
        const val TAG = "GpuStressRenderer"
        const val MEASURE_SECONDS = 3.0
        const val VERTEX_COUNT = 4

        /** 归一化基准：中端机（骁龙 778G）在 1080p 下约 25~30 帧，对应 900 分左右。 */
        const val SCORE_PER_FPS = 32.0

        val VERTICES: FloatBuffer = ByteBuffer
            .allocateDirect(8 * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
                position(0)
            }

        const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            varying vec2 vCoordinate;
            void main() {
                vCoordinate = aPosition;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        /** 每个像素 1024 次迭代，足以让旗舰 GPU 也跑不到满帧，从而区分出性能差距。 */
        const val FRAGMENT_SHADER = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            varying vec2 vCoordinate;
            void main() {
                vec2 p = vCoordinate * 3.0;
                float accumulator = 0.0;
                for (int i = 0; i < 1024; i++) {
                    p = vec2(p.x * 1.011 - p.y * 0.017, p.y * 1.013 + p.x * 0.019);
                    accumulator += sin(p.x) * cos(p.y) + 0.3 * sin(p.x + p.y);
                }
                float value = fract(abs(accumulator) * 0.01);
                gl_FragColor = vec4(value, value * 0.55, 0.32, 1.0);
            }
        """
    }
}

/** GPU 跑分结果。 */
data class GpuResult(
    val score: Int,
    val framesPerSecond: Double,
    val renderer: String
)
