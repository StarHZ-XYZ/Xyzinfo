package com.rjy.xyz.apps.xyzinfo.ui.benchmark

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * 3D 引擎压力测试的测量结果。
 *
 * [shadedPixelsPerSecond] 是**每帧着色的像素总量 × 帧率**：场景通道按实际渲染分辨率与
 * 通道数累加。它比单纯看帧率可靠 —— 帧率会被垂直同步卡在 60/90/120，而"每秒着色了多少像素"
 * 只跟 GPU 的真实吞吐有关。
 */
data class Gpu3dResult(
    val frames: Int,
    val framesPerSecond: Double,
    val lowFramesPerSecond: Double,
    val shadedPixelsPerSecond: Double,
    val passes: Int,
    val trianglesPerFrame: Int,
    val renderer: String,
    val elapsedSeconds: Double,
    val timestamp: Long,
    val error: String? = null
)

/**
 * 自研 mini 3D 管线（OpenGL ES 3.0）：真实三维几何 + 动态光照 + 重着色器 + 后处理。
 *
 * ## 为什么值得为它冒风险
 *
 * 0.7 的 GPU 测试走的是硬件加速 2D 填充（Canvas），它的问题是**很容易撞到垂直同步上限**：
 * 2D 填充对现代 GPU 太轻，画面 60 帧封顶，于是"帧率 × 像素"算出来的分数被刷新率锁死，
 * 旗舰机和中端机拉不开差距 —— 测的其实是屏幕刷新率，不是 GPU。
 *
 * 3D 管线把负载压到 GPU 自己身上：
 * - **几何**：程序化生成的圆环体，24 个实例各自独立旋转/公转（`glDrawArraysInstanced`）；
 * - **着色**：4 个动态点光源 + 法线扰动 + 高光 + 菲涅尔 + 32 次迭代的程序化细节，
 *   像素着色是故意做重的，让填充率而不是 vsync 决定帧时间；
 * - **后处理**：场景渲染到离屏 FBO，再经色调映射画到屏幕；
 * - **自适应负载**：先用 1.2 秒探一次，如果帧率还顶在 50 以上就把每帧通道数翻倍，
 *   直到 GPU 真的成为瓶颈（旗舰机也能压住），最多 16 个通道。
 *
 * ## 崩溃隔离
 *
 * 这个渲染器**跑在独立进程**（`android:process=":gpu"`）。原因：历史上在
 * 小米 HyperOS + Adreno 6xx 上，GLSL 编译触发过驱动级 SIGSEGV —— 原生崩溃捕获不了，
 * 主进程一起陪葬。放到子进程里，最坏情况只是这一页崩掉，主程序收到超时后自动退回
 * 2D 填充测试继续把跑分跑完。
 */
class Gpu3dRenderer(
    private val seconds: Double,
    private val onProgress: (elapsed: Double, total: Double, fps: Double) -> Unit,
    private val onFinished: (Gpu3dResult) -> Unit
) : GLSurfaceView.Renderer {

    private enum class Phase { CALIBRATE, MEASURE, DONE }

    private var phase = Phase.CALIBRATE
    private var program = 0
    private var postProgram = 0
    private var sceneVao = 0
    private var quadVao = 0
    private var meshVbo = 0
    private var meshIbo = 0
    private var instanceVbo = 0
    private var quadVbo = 0
    private var framebuffer = 0
    private var colorTexture = 0
    private var depthBuffer = 0
    private var indexCount = 0
    private var instanceCount = 0
    private var renderWidth = 0
    private var renderHeight = 0
    private var viewWidth = 1
    private var viewHeight = 1

    private var passCount = START_PASSES
    private var phaseStartNanos = 0L
    private var lastFrameNanos = 0L
    private var lastReportMillis = 0L
    private var startNanos = 0L
    private var frameIntervals = ArrayList<Int>(4096)
    private var frames = 0
    private var shadedPixels = 0.0

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val lightPositions = FloatArray(12)
    private val lightColors = FloatArray(12)

    private var uMvp = 0
    private var uCamPos = 0
    private var uTime = 0
    private var uLightPos = 0
    private var uLightColor = 0
    private var uSceneTex = 0
    private var uExposure = 0

    private var rendererName = "OpenGL ES 3.0"
    private var failed: String? = null
    private var reportedFailure = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            rendererName = GLES30.glGetString(GLES30.GL_RENDERER) ?: rendererName
            Log.i(TAG, "GL 初始化：${GLES30.glGetString(GLES30.GL_VERSION)} / $rendererName")
            program = buildProgram(SCENE_VERTEX_SHADER, SCENE_FRAGMENT_SHADER)
            postProgram = buildProgram(QUAD_VERTEX_SHADER, QUAD_FRAGMENT_SHADER)
            uMvp = GLES30.glGetUniformLocation(program, "uMvp")
            uCamPos = GLES30.glGetUniformLocation(program, "uCamPos")
            uTime = GLES30.glGetUniformLocation(program, "uTime")
            uLightPos = GLES30.glGetUniformLocation(program, "uLightPos")
            uLightColor = GLES30.glGetUniformLocation(program, "uLightColor")
            uSceneTex = GLES30.glGetUniformLocation(postProgram, "uScene")
            uExposure = GLES30.glGetUniformLocation(postProgram, "uExposure")
            buildGeometry()
            buildQuad()
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        } catch (t: Throwable) {
            failed = t.message ?: t.javaClass.simpleName
            Log.e(TAG, "GL 初始化失败", t)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width.coerceAtLeast(1)
        viewHeight = height.coerceAtLeast(1)
        if (failed != null) return
        try {
            createRenderTargets()
            Matrix.perspectiveM(projection, 0, 45f, viewWidth.toFloat() / viewHeight, 0.5f, 120f)
            Matrix.setLookAtM(view, 0, 0f, 1.6f, 9.5f, 0f, 0f, 0f, 0f, 1f, 0f)
            Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
        } catch (t: Throwable) {
            failed = t.message ?: t.javaClass.simpleName
            Log.e(TAG, "GL 表面创建失败", t)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        if (failed != null) {
            // 初始化失败立刻上报一次：主流程还要赶在退路测试之前拿到结论，不能白等到超时
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            if (!reportedFailure) {
                reportedFailure = true
                onFinished(
                    Gpu3dResult(
                        frames = 0, framesPerSecond = 0.0, lowFramesPerSecond = 0.0,
                        shadedPixelsPerSecond = 0.0, passes = 0, trianglesPerFrame = 0,
                        renderer = rendererName, elapsedSeconds = 0.0,
                        timestamp = System.currentTimeMillis(), error = failed
                    )
                )
            }
            return
        }
        if (startNanos == 0L) {
            startNanos = now
            phaseStartNanos = now
            lastFrameNanos = now
            return
        }

        try {
            val elapsed = (now - startNanos) / 1e9
            val time = elapsed.toFloat()
            for (pass in 0 until passCount) {
                drawSceneToFbo(time + pass * 0.37f)
            }
            drawFboToScreen()

            frames++
            shadedPixels += (renderWidth.toDouble() * renderHeight * passCount +
                renderWidth.toDouble() * renderHeight)
            if (lastFrameNanos > 0) {
                val deltaMs = ((now - lastFrameNanos) / 1_000_000L).toInt()
                if (deltaMs > 0 && frameIntervals.size < MAX_FRAMES) frameIntervals.add(deltaMs)
            }
            lastFrameNanos = now

            when (phase) {
                Phase.CALIBRATE -> {
                    val probeSeconds = (now - phaseStartNanos) / 1e9
                    if (probeSeconds >= CALIBRATE_SECONDS) {
                        val fps = framesPerSecond(frames, probeSeconds)
                        if (fps >= VSYNC_LIMIT_FPS && passCount < MAX_PASSES) {
                            // 还顶在刷新率上：说明压得不够，通道数翻倍重来
                            passCount *= 2
                            frames = 0
                            frameIntervals.clear()
                            phaseStartNanos = now
                            Log.i(TAG, "帧率仍受限（${"%.0f".format(fps)} fps），通道数提到 $passCount")
                        } else {
                            phase = Phase.MEASURE
                            frames = 0
                            frameIntervals.clear()
                            startNanos = now
                            phaseStartNanos = now
                            Log.i(TAG, "开始测量：通道 $passCount，探测帧率 ${"%.0f".format(fps)}")
                        }
                    }
                }

                Phase.MEASURE -> {
                    val measured = (now - startNanos) / 1e9
                    val millis = (measured * 1000).toLong()
                    if (millis - lastReportMillis >= 250) {
                        lastReportMillis = millis
                        val fps = framesPerSecond(frames, measured.coerceAtLeast(0.001))
                        onProgress(measured, seconds, fps)
                    }
                    if (measured >= seconds) {
                        phase = Phase.DONE
                        onFinished(buildResult(measured))
                    }
                }

                Phase.DONE -> Unit
            }
        } catch (t: Throwable) {
            failed = t.message ?: t.javaClass.simpleName
            Log.e(TAG, "渲染过程中出错", t)
            onFinished(
                Gpu3dResult(
                    frames = 0, framesPerSecond = 0.0, lowFramesPerSecond = 0.0,
                    shadedPixelsPerSecond = 0.0, passes = passCount, trianglesPerFrame = 0,
                    renderer = rendererName, elapsedSeconds = 0.0,
                    timestamp = System.currentTimeMillis(), error = failed
                )
            )
        }
    }

    /** 上层发现 GL 起不来时调用，用来给主流程一个明确的失败原因。 */
    fun failureReason(): String? = failed

    private fun buildResult(measuredSeconds: Double): Gpu3dResult {
        val sorted = frameIntervals.sorted()
        val p99 = if (sorted.isEmpty()) {
            0.0
        } else {
            sorted[((sorted.size - 1) * 0.99).toInt().coerceIn(0, sorted.size - 1)].toDouble()
        }
        val fps = framesPerSecond(frames, measuredSeconds)
        return Gpu3dResult(
            frames = frames,
            framesPerSecond = fps,
            lowFramesPerSecond = if (p99 > 0) 1000.0 / p99 else 0.0,
            shadedPixelsPerSecond = fps * (renderWidth.toDouble() * renderHeight * passCount +
                renderWidth.toDouble() * renderHeight),
            passes = passCount,
            trianglesPerFrame = indexCount / 3 * instanceCount * passCount,
            renderer = rendererName,
            elapsedSeconds = measuredSeconds,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun framesPerSecond(frames: Int, seconds: Double): Double =
        if (seconds <= 0.05) 0.0 else frames / seconds

    // ---------- 绘制 ----------

    private fun drawSceneToFbo(time: Float) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glViewport(0, 0, renderWidth, renderHeight)
        GLES30.glClearColor(0.02f, 0.03f, 0.06f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, viewProjection, 0)
        GLES30.glUniform3f(uCamPos, 0f, 1.6f, 9.5f)
        GLES30.glUniform1f(uTime, time)
        fillLights(time)
        GLES30.glUniform3fv(uLightPos, 4, lightPositions, 0)
        GLES30.glUniform3fv(uLightColor, 4, lightColors, 0)

        GLES30.glBindVertexArray(sceneVao)
        GLES30.glDrawElementsInstanced(
            GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0, instanceCount
        )
        GLES30.glBindVertexArray(0)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
    }

    /** 四个绕场景转的点光源：颜色与位置都在动，避免出现"每帧完全一样"的缓存红利。 */
    private fun fillLights(time: Float) {
        for (index in 0 until 4) {
            val phase = time * (0.4f + index * 0.17f) + index * 1.7f
            val radius = 3.2f + index * 0.8f
            lightPositions[index * 3] = cos(phase) * radius
            lightPositions[index * 3 + 1] = sin(phase * 1.3f) * 2.2f + 1.5f
            lightPositions[index * 3 + 2] = sin(phase) * radius
            lightColors[index * 3] = 0.5f + 0.5f * sin(phase * 0.7f) * 0.5f + 0.25f
            lightColors[index * 3 + 1] = 0.45f + 0.3f * cos(phase * 0.9f).coerceAtLeast(0f)
            lightColors[index * 3 + 2] = 0.6f + 0.4f * sin(phase * 1.1f) * 0.5f + 0.2f
        }
    }

    private fun drawFboToScreen() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, viewWidth, viewHeight)
        GLES30.glUseProgram(postProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTexture)
        GLES30.glUniform1i(uSceneTex, 0)
        GLES30.glUniform1f(uExposure, 1.05f)
        GLES30.glBindVertexArray(quadVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    // ---------- 资源构建 ----------

    private fun createRenderTargets() {
        // 固定内部分辨率：与屏幕无关，换台机器也是同一份负载，分数才有可比性
        renderWidth = RENDER_WIDTH
        renderHeight = RENDER_HEIGHT

        if (framebuffer != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
            GLES30.glDeleteTextures(1, intArrayOf(colorTexture), 0)
            GLES30.glDeleteRenderbuffers(1, intArrayOf(depthBuffer), 0)
        }
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        colorTexture = textures[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTexture)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, renderWidth, renderHeight, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        val buffers = IntArray(1)
        GLES30.glGenRenderbuffers(1, buffers, 0)
        depthBuffer = buffers[0]
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depthBuffer)
        GLES30.glRenderbufferStorage(
            GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT16, renderWidth, renderHeight
        )

        val fbos = IntArray(1)
        GLES30.glGenFramebuffers(1, fbos, 0)
        framebuffer = fbos[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, colorTexture, 0
        )
        GLES30.glFramebufferRenderbuffer(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT,
            GLES30.GL_RENDERBUFFER, depthBuffer
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw IllegalStateException("离屏帧缓冲不完整：0x${Integer.toHexString(status)}")
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, 0)
    }

    /**
     * 程序化圆环体：位置 / 法线 / 纹理坐标 + 每实例的随机参数。
     *
     * 不用外部模型文件，省得给包里塞资源，也保证任何机器上几何完全一致。
     */
    private fun buildGeometry() {
        val segments = TORUS_SEGMENTS
        val sides = TORUS_SIDES
        val majorRadius = 1.0f
        val minorRadius = 0.38f
        val vertices = ArrayList<Float>((segments + 1) * (sides + 1) * 8)
        for (i in 0..segments) {
            val u = i.toFloat() / segments * TWO_PI
            val cosU = cos(u)
            val sinU = sin(u)
            for (j in 0..sides) {
                val v = j.toFloat() / sides * TWO_PI
                val cosV = cos(v)
                val sinV = sin(v)
                val x = (majorRadius + minorRadius * cosV) * cosU
                val y = minorRadius * sinV
                val z = (majorRadius + minorRadius * cosV) * sinU
                val nx = cosV * cosU
                val ny = sinV
                val nz = cosV * sinU
                vertices.add(x); vertices.add(y); vertices.add(z)
                vertices.add(nx); vertices.add(ny); vertices.add(nz)
                vertices.add(i.toFloat() / segments); vertices.add(j.toFloat() / sides)
            }
        }
        val indices = ShortArray(segments * sides * 6)
        var pointer = 0
        for (i in 0 until segments) {
            for (j in 0 until sides) {
                val a = (i * (sides + 1) + j).toShort()
                val b = (a + sides + 1).toShort()
                indices[pointer++] = a
                indices[pointer++] = b
                indices[pointer++] = (a + 1).toShort()
                indices[pointer++] = b
                indices[pointer++] = (b + 1).toShort()
                indices[pointer++] = (a + 1).toShort()
            }
        }
        indexCount = indices.size

        val instanceData = FloatArray(INSTANCE_COUNT * 4)
        var seed = 987654321
        for (index in 0 until INSTANCE_COUNT) {
            seed = seed * 1103515245 + 12345
            instanceData[index * 4] = ((seed ushr 8) % 1000) / 1000f * TWO_PI
            instanceData[index * 4 + 1] = 0.55f + ((seed ushr 5) % 100) / 200f
            instanceData[index * 4 + 2] = 1.6f + ((seed ushr 11) % 100) / 40f
            instanceData[index * 4 + 3] = ((seed ushr 3) % 1000) / 1000f * TWO_PI
        }
        instanceCount = INSTANCE_COUNT

        val vao = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        sceneVao = vao[0]
        val buffers = IntArray(3)
        GLES30.glGenBuffers(3, buffers, 0)
        meshVbo = buffers[0]
        meshIbo = buffers[1]
        instanceVbo = buffers[2]

        GLES30.glBindVertexArray(sceneVao)
        val vertexBuffer = vertices.toFloatArray().toFloatBuffer()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, meshVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER, vertexBuffer.capacity() * 4, vertexBuffer, GLES30.GL_STATIC_DRAW
        )
        val stride = 8 * 4
        GLES30.glEnableVertexAttribArray(ATTRIB_POSITION)
        GLES30.glVertexAttribPointer(ATTRIB_POSITION, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(ATTRIB_NORMAL)
        GLES30.glVertexAttribPointer(ATTRIB_NORMAL, 3, GLES30.GL_FLOAT, false, stride, 3 * 4)
        GLES30.glEnableVertexAttribArray(ATTRIB_UV)
        GLES30.glVertexAttribPointer(ATTRIB_UV, 2, GLES30.GL_FLOAT, false, stride, 6 * 4)

        val indexBuffer = indices.toShortBuffer()
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, meshIbo)
        GLES30.glBufferData(
            GLES30.GL_ELEMENT_ARRAY_BUFFER, indexBuffer.capacity() * 2, indexBuffer,
            GLES30.GL_STATIC_DRAW
        )

        val instanceBuffer = instanceData.toFloatBuffer()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER, instanceBuffer.capacity() * 4, instanceBuffer,
            GLES30.GL_STATIC_DRAW
        )
        GLES30.glEnableVertexAttribArray(ATTRIB_INSTANCE)
        GLES30.glVertexAttribPointer(ATTRIB_INSTANCE, 4, GLES30.GL_FLOAT, false, 4 * 4, 0)
        GLES30.glVertexAttribDivisor(ATTRIB_INSTANCE, 1)
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun buildQuad() {
        val vao = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        quadVao = vao[0]
        val buffers = IntArray(1)
        GLES30.glGenBuffers(1, buffers, 0)
        quadVbo = buffers[0]
        val quad = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        GLES30.glBindVertexArray(quadVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo)
        val buffer = quad.toFloatBuffer()
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER, buffer.capacity() * 4, buffer, GLES30.GL_STATIC_DRAW
        )
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * 4, 0)
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun buildProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compile(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragment = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        val id = GLES30.glCreateProgram()
        GLES30.glAttachShader(id, vertex)
        GLES30.glAttachShader(id, fragment)
        GLES30.glLinkProgram(id)
        val status = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(id)
            GLES30.glDeleteProgram(id)
            throw IllegalStateException("着色器链接失败：$log")
        }
        GLES30.glDeleteShader(vertex)
        GLES30.glDeleteShader(fragment)
        return id
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw IllegalStateException("着色器编译失败：$log")
        }
        return shader
    }

    private fun FloatArray.toFloatBuffer(): FloatBuffer =
        ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().put(this).apply { position(0) }

    private fun ShortArray.toShortBuffer(): ShortBuffer =
        ByteBuffer.allocateDirect(size * 2).order(ByteOrder.nativeOrder())
            .asShortBuffer().put(this).apply { position(0) }

    companion object {
        private const val TAG = "XyzInfoGpu3d"
        private const val TWO_PI = 6.2831855f

        private const val ATTRIB_POSITION = 0
        private const val ATTRIB_NORMAL = 1
        private const val ATTRIB_UV = 2
        private const val ATTRIB_INSTANCE = 3

        /** 离屏渲染分辨率：固定值，保证任何屏幕上的负载一致。 */
        private const val RENDER_WIDTH = 1280
        private const val RENDER_HEIGHT = 720

        private const val TORUS_SEGMENTS = 128
        private const val TORUS_SIDES = 32
        private const val INSTANCE_COUNT = 24

        private const val START_PASSES = 2
        private const val MAX_PASSES = 16
        private const val CALIBRATE_SECONDS = 1.2

        /** 帧率高于这个值就认为还顶在垂直同步上，需要加重负载。 */
        private const val VSYNC_LIMIT_FPS = 50.0
        private const val MAX_FRAMES = 20000

        private val SCENE_VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNormal;
            layout(location = 2) in vec2 aUv;
            layout(location = 3) in vec4 aSeed;
            uniform mat4 uMvp;
            uniform float uTime;
            out vec3 vWorld;
            out vec3 vNormal;
            out vec2 vUv;

            // 罗德里格斯公式：绕任意轴旋转（省掉每实例的模型矩阵属性）
            mat3 rotate(vec3 axis, float angle) {
                float c = cos(angle);
                float s = sin(angle);
                float t = 1.0 - c;
                vec3 a = normalize(axis);
                return mat3(
                    t * a.x * a.x + c,        t * a.x * a.y - s * a.z, t * a.x * a.z + s * a.y,
                    t * a.x * a.y + s * a.z,  t * a.y * a.y + c,       t * a.y * a.z - s * a.x,
                    t * a.x * a.z - s * a.y,  t * a.y * a.z + s * a.x, t * a.z * a.z + c
                );
            }

            void main() {
                vec3 axis = normalize(vec3(sin(aSeed.x * 1.7), cos(aSeed.y * 1.3), sin(aSeed.x + 1.1)));
                float angle = aSeed.x + uTime * (0.35 + 0.4 * aSeed.y);
                vec3 scaled = aPos * aSeed.y;
                vec3 rotated = rotate(axis, angle) * scaled;
                vec3 orbit = vec3(
                    cos(aSeed.z + uTime * 0.45),
                    sin(aSeed.w + uTime * 0.31),
                    sin(aSeed.z * 1.7)
                ) * aSeed.z;
                vWorld = rotated + orbit;
                vNormal = rotate(axis, angle) * aNormal;
                vUv = aUv;
                gl_Position = uMvp * vec4(vWorld, 1.0);
            }
        """.trimIndent()

        private val SCENE_FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            in vec3 vWorld;
            in vec3 vNormal;
            in vec2 vUv;
            uniform vec3 uCamPos;
            uniform vec3 uLightPos[4];
            uniform vec3 uLightColor[4];
            uniform float uTime;
            out vec4 outColor;

            float hash(vec3 p) {
                p = fract(p * 0.3183099 + vec3(0.71, 0.113, 0.419));
                p *= 17.0;
                return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
            }

            float noise(vec3 p) {
                vec3 i = floor(p);
                vec3 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                float n000 = hash(i);
                float n100 = hash(i + vec3(1.0, 0.0, 0.0));
                float n010 = hash(i + vec3(0.0, 1.0, 0.0));
                float n110 = hash(i + vec3(1.0, 1.0, 0.0));
                float n001 = hash(i + vec3(0.0, 0.0, 1.0));
                float n101 = hash(i + vec3(1.0, 0.0, 1.0));
                float n011 = hash(i + vec3(0.0, 1.0, 1.0));
                float n111 = hash(i + vec3(1.0, 1.0, 1.0));
                return mix(
                    mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
                    mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y),
                    f.z
                );
            }

            void main() {
                vec3 view = normalize(uCamPos - vWorld);
                // 法线扰动：三层噪声，够重也够有细节
                vec3 perturbation = vec3(
                    noise(vWorld * 3.1 + uTime * 0.05),
                    noise(vWorld * 3.3 + 11.7),
                    noise(vWorld * 2.9 + 23.1)
                ) - 0.5;
                vec3 normal = normalize(vNormal + perturbation * 0.55);

                vec3 color = vec3(0.0);
                for (int i = 0; i < 4; i++) {
                    vec3 toLight = uLightPos[i] - vWorld;
                    float distance = length(toLight);
                    vec3 lightDir = toLight / max(distance, 0.001);
                    float attenuation = 1.0 / (1.0 + 0.07 * distance * distance);
                    float diffuse = max(dot(normal, lightDir), 0.0);
                    vec3 halfway = normalize(lightDir + view);
                    float specular = pow(max(dot(normal, halfway), 0.0), 48.0);
                    float fresnel = pow(1.0 - max(dot(normal, view), 0.0), 3.0);
                    color += uLightColor[i] * attenuation * (diffuse * 0.85 + specular * 0.7 + fresnel * 0.3);
                }

                // 程序化细节：32 次迭代的正弦叠加，刻意把像素开销压到显眼位置
                float detail = 0.0;
                for (int i = 0; i < 32; i++) {
                    float f = float(i);
                    detail += sin(vWorld.x * 0.7 + f) * cos(vWorld.y * 0.9 + f * 1.3)
                            * sin(vWorld.z * 1.1 + f * 0.7 + uTime * 0.3);
                }
                color *= 0.65 + 0.35 * abs(detail) / 32.0;

                // 边缘光，让轮廓在深色背景里也清楚
                color += pow(max(dot(normal, view), 0.0), 6.0) * vec3(0.12, 0.16, 0.24);
                color += vec3(vUv.y) * 0.03;
                outColor = vec4(color, 1.0);
            }
        """.trimIndent()

        private val QUAD_VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec2 aPos;
            out vec2 vUv;
            void main() {
                vUv = aPos * 0.5 + 0.5;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
        """.trimIndent()

        private val QUAD_FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            in vec2 vUv;
            uniform sampler2D uScene;
            uniform float uExposure;
            out vec4 outColor;

            void main() {
                vec3 color = texture(uScene, vUv).rgb * uExposure;
                // 简单色调映射 + 暗角，算是这条管线自己的后处理
                color = color / (color + vec3(0.85));
                float vignette = 1.0 - 0.35 * pow(length(vUv - 0.5) * 1.4, 2.2);
                outColor = vec4(color * vignette, 1.0);
            }
        """.trimIndent()
    }
}
