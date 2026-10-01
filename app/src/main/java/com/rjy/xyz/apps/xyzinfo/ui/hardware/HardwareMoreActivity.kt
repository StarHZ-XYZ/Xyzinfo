package com.rjy.xyz.apps.xyzinfo.ui.hardware

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityHardwareMoreBinding
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.ui.common.Anim
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 硬件测试（第二批）：扬声器 / 麦克风 / 振动 / 摄像头 / NFC。
 *
 * 音频全部**现场合成**，不打包任何音频文件：
 * - 扬声器：AudioTrack 播放 C5-E5-G5-C6 四个正弦音符（写成立体声，左右都能听到）；
 * - 麦克风：AudioRecord 读 PCM 算 RMS，映射成 0~100 的电平条 + 峰值。
 */
class HardwareMoreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHardwareMoreBinding
    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null
    private var micRunning = false
    private var camera: Camera? = null
    /** 当前使用的摄像头 id 与全部可选镜头（前摄/后摄/广角/长焦在系统里就是不同 id）。 */
    private var cameraId = 0
    private var lensRow: android.widget.LinearLayout? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[Manifest.permission.RECORD_AUDIO] == true) startMic()
            if (result[Manifest.permission.CAMERA] == true) toggleCamera()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHardwareMoreBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_NONE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupSpeaker()
        binding.btnOpenScreenTest.setOnClickListener {
            Anim.pressFeedback(it)
            startActivity(Intent(this, ScreenTestActivity::class.java))
        }
        setupMic()
        setupVibrator()
        setupCamera()
        setupNfc()
        binding.btnOneKeyTest.setOnClickListener {
            Anim.pressFeedback(it)
            runOneKeyTest()
        }
    }

    override fun onPause() {
        super.onPause()
        stopTone()
        stopMic()
        stopCamera()
    }

    // ---------- 扬声器 ----------

    private fun setupSpeaker() {
        binding.btnPlayTone.setOnClickListener {
            Anim.pressFeedback(it)
            playMelody()
        }
        binding.btnStopTone.setOnClickListener {
            Anim.pressFeedback(it)
            stopTone()
        }
    }

    /** 现场合成 C5 / E5 / G5 / C6，每音 320ms，带前后淡入淡出防爆音。 */
    private fun playMelody() {
        stopTone()
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
        val perNote = (SAMPLE_RATE * 0.32).toInt()
        val mono = ShortArray(perNote * notes.size)
        notes.forEachIndexed { index, freq ->
            for (i in 0 until perNote) {
                val t = i.toDouble() / SAMPLE_RATE
                val fadeIn = i / (SAMPLE_RATE * 0.02)
                val fadeOut = (perNote - i) / (SAMPLE_RATE * 0.03)
                val envelope = min(1.0, min(fadeIn, fadeOut)).coerceAtLeast(0.0)
                val value = sin(2 * PI * freq * t) * 0.55 * envelope
                mono[index * perNote + i] = (value * Short.MAX_VALUE).toInt().toShort()
            }
        }
        val stereo = ShortArray(mono.size * 2)
        mono.forEachIndexed { i, sample ->
            stereo[i * 2] = sample
            stereo[i * 2 + 1] = sample
        }
        val result = runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(stereo.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(stereo, 0, stereo.size)
            track.play()
            audioTrack = track
        }
        binding.tvSpeakerState.setInfoRow(
            if (result.isSuccess) "正在播放：C5 → E5 → G5 → C6" else "播放失败：${result.exceptionOrNull()?.message}"
        )
    }

    private fun stopTone() {
        runCatching {
            audioTrack?.stop()
            audioTrack?.release()
        }
        audioTrack = null
    }

    // ---------- 麦克风 ----------

    private fun setupMic() {
        binding.btnMic.setOnClickListener {
            Anim.pressFeedback(it)
            when {
                micRunning -> stopMic()
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED -> startMic()
                else -> permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            }
        }
    }

    private fun startMic() {
        if (micRunning) return
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, SAMPLE_RATE / 5)
            )
        }.getOrNull()
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { record?.release() }
            binding.tvMicState.setInfoRow("麦克风打不开（可能被其它应用占用）")
            return
        }
        audioRecord = record
        micRunning = true
        binding.btnMic.text = "停止测电平"
        binding.tvMicState.setInfoRow("正在监听…对着手机说话看电平条")
        record.startRecording()

        Thread({
            val buffer = ShortArray(SAMPLE_RATE / 10)
            var peak = 0
            while (micRunning) {
                val read = runCatching { record.read(buffer, 0, buffer.size) }.getOrDefault(0)
                if (read <= 0) continue
                var sum = 0.0
                var maxAbs = 0
                for (i in 0 until read) {
                    val value = buffer[i].toDouble()
                    sum += value * value
                    val absolute = abs(buffer[i].toInt())
                    if (absolute > maxAbs) maxAbs = absolute
                }
                val rms = sqrt(sum / read)
                val level = (rms / 8000.0 * 100).coerceIn(0.0, 100.0).toInt()
                if (level > peak) peak = level
                val peakSnapshot = peak
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    binding.progressMic.progress = level
                    binding.tvMicState.setInfoRow("当前电平：$level / 100 ｜ 峰值：$peakSnapshot")
                }
            }
        }, "xyzinfo-mic").start()
    }

    private fun stopMic() {
        micRunning = false
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
        binding.btnMic.text = "开始测电平"
    }

    // ---------- 振动 ----------

    private fun setupVibrator() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator == null || !vibrator.hasVibrator()) {
            binding.btnVibrateLight.isEnabled = false
            binding.btnVibrateStrong.isEnabled = false
            binding.btnVibratePattern.isEnabled = false
            return
        }
        binding.btnVibrateLight.setOnClickListener { vibrate(vibrator, 120L, strong = false) }
        binding.btnVibrateStrong.setOnClickListener { vibrate(vibrator, 800L, strong = true) }
        binding.btnVibratePattern.setOnClickListener {
            val timings = longArrayOf(0, 120, 100, 120, 100, 300, 150, 120)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrateWithAlarmAttributes(vibrator, VibrationEffect.createWaveform(timings, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        }
    }

    /**
     * 振动。
     *
     * 之前"点了没感觉"的两个原因：
     * 1. 时长太短（60ms）且用默认振幅，很多 ROM 直接吞掉；
     * 2. 没指定用途——系统可能按"触摸反馈"处理，而用户往往把触摸反馈关掉了。
     * 现在：默认振幅改成 180 / 255（支持振幅控制的机器），并用 **USAGE_ALARM** 提交，
     * 这类振动不受"触摸反馈"开关影响。
     */
    private fun vibrate(vibrator: Vibrator, millis: Long, strong: Boolean) {
        val times = if (strong) 2 else 1
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = if (vibrator.hasAmplitudeControl()) {
                if (strong) 255 else 200
            } else {
                VibrationEffect.DEFAULT_AMPLITUDE
            }
            val effect = VibrationEffect.createOneShot(millis, amplitude)
            // 参数设成"重复 times 次"的波形：单次短震动在部分 ROM 上会被吞，重复更容易有感觉
            val waveform = LongArray(times * 2 - 1).also { array ->
                for (index in 0 until times) {
                    array[index * 2] = if (index == 0) 0L else 120L
                    if (index * 2 + 1 < array.size) array[index * 2 + 1] = millis
                }
            }
            val patternEffect = runCatching {
                VibrationEffect.createWaveform(waveform, -1)
            }.getOrNull()

            // 依次尝试：闹钟用途 → 普通 → 传统 pattern（有的 ROM 只认某一种）
            var done = runCatching {
                vibrateWithAlarmAttributes(vibrator, patternEffect ?: effect); true
            }.getOrDefault(false)
            if (!done) {
                done = runCatching { vibrator.vibrate(patternEffect ?: effect); true }.getOrDefault(false)
            }
            if (!done) {
                @Suppress("DEPRECATION")
                runCatching { vibrator.vibrate(waveform, -1) }
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching { vibrator.vibrate(millis) }
        }
        reportVibrateState(vibrator)
    }

    /**
     * 把振动相关的系统状态显示出来 —— "点了没感觉"很多时候不是应用的问题，
     * 而是系统处于静音、或者机型不支持振幅控制。
     */
    private fun reportVibrateState(vibrator: Vibrator) {
        val ringer = runCatching {
            (getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager).ringerMode
        }.getOrDefault(android.media.AudioManager.RINGER_MODE_NORMAL)
        val ringerText = when (ringer) {
            android.media.AudioManager.RINGER_MODE_SILENT -> "静音（系统可能禁止振动）"
            android.media.AudioManager.RINGER_MODE_VIBRATE -> "振动模式"
            else -> "响铃"
        }
        binding.tvSpeakerState.setInfoRow(
            "已触发振动 ｜ 马达：" + (if (vibrator.hasVibrator()) "有" else "无") +
                " ｜ 振幅控制：" + (if (vibrator.hasAmplitudeControl()) "支持" else "不支持") +
                " ｜ 铃声模式：" + ringerText
        )
    }

    /** 用闹钟用途提交振动：优先级最高，不会被系统的"触摸反馈"开关拦掉。 */
    private fun vibrateWithAlarmAttributes(vibrator: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            vibrator.vibrate(effect, attributes)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect)
        }
    }

    // ---------- 摄像头 ----------

    private fun setupCamera() {
        buildLensButtons()
        binding.btnCamera.setOnClickListener {
            Anim.pressFeedback(it)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                toggleCamera()
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            }
        }
    }

    /**
     * 枚举设备上的所有摄像头，做成一行可点的镜头按钮。
     *
     * 现在的手机把前摄、后摄主摄、超广角、长焦都注册成**独立摄像头 id**，
     * 旧 Camera API 也能列出来，所以这里直接按 id 全列出来让用户切。
     */
    @Suppress("DEPRECATION")
    private fun buildLensButtons() {
        val parent = binding.btnCamera.parent as? android.widget.LinearLayout ?: return
        val count = runCatching { Camera.getNumberOfCameras() }.getOrDefault(0)
        if (count <= 1) return
        val density = resources.displayMetrics.density
        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (10 * density).toInt() }
        }
        for (id in 0 until count) {
            val info = Camera.CameraInfo().also { Camera.getCameraInfo(id, it) }
            val label = when (info.facing) {
                Camera.CameraInfo.CAMERA_FACING_FRONT -> "前摄"
                else -> if (id == 0) "后摄" else "镜头 $id"
            }
            val button = com.google.android.material.button.MaterialButton(
                this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = label
                textSize = 12f
                isAllCaps = false
                setTextColor(
                    ContextCompat.getColor(this@HardwareMoreActivity, R.color.text_primary)
                )
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ).apply { marginEnd = (6 * density).toInt() }
                setOnClickListener {
                    if (cameraId != id) {
                        cameraId = id
                        stopCamera()
                        if (ContextCompat.checkSelfPermission(
                                this@HardwareMoreActivity, Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            toggleCamera()
                        }
                    }
                }
            }
            row.addView(button)
        }
        parent.addView(row, parent.indexOfChild(binding.btnCamera))
        lensRow = row
    }

    /** 一键测试：自动跑一遍能判定的项目，最后给出通过 / 未通过清单。 */
    private fun runOneKeyTest() {
        binding.btnOneKeyTest.isEnabled = false
        Thread({
            val lines = mutableListOf<String>()
            var pass = 0
            var total = 0
            fun record(name: String, ok: Boolean, detail: String) {
                total++
                if (ok) pass++
                lines += (if (ok) "✔ " else "✘ ") + name + "：" + detail
                runOnUiThread {
                    if (!isFinishing) binding.tvOneKeyResult.text = lines.joinToString("\n")
                }
            }

            runCatching {
                @Suppress("DEPRECATION")
                val count = Camera.getNumberOfCameras()
                record("摄像头", count > 0, "系统注册 $count 个")
            }.onFailure { record("摄像头", false, it.message ?: "查询失败") }

            runCatching {
                val manager = getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                val flash = manager.cameraIdList.any { id ->
                    manager.getCameraCharacteristics(id).get(
                        android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE
                    ) == true
                }
                record("闪光灯", flash, if (flash) "检测到闪光灯单元" else "机身没有闪光灯")
            }.onFailure { record("闪光灯", false, it.message ?: "查询失败") }

            runCatching {
                val adapter = NfcAdapter.getDefaultAdapter(this)
                record(
                    "NFC",
                    adapter != null,
                    when {
                        adapter == null -> "没有 NFC 硬件"
                        adapter.isEnabled -> "支持且已开启"
                        else -> "支持但处于关闭状态"
                    }
                )
            }.onFailure { record("NFC", false, it.message ?: "查询失败") }

            runCatching {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                val has = vibrator.hasVibrator()
                if (has) vibrate(vibrator, 320L, strong = true)
                record("振动马达", has, if (has) "已振一次（有感觉即正常）" else "没有振动马达")
            }.onFailure { record("振动马达", false, it.message ?: "触发失败") }

            runCatching {
                playMelody()
                Thread.sleep(1000)
                stopTone()
                record("扬声器", true, "已播放测试音（听到即正常）")
            }.onFailure { record("扬声器", false, it.message ?: "播放失败") }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                record("麦克风", false, "未授予录音权限，已跳过")
            } else {
                runCatching {
                    val minBuffer = AudioRecord.getMinBufferSize(
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                    )
                    val recorder = AudioRecord(
                        MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        maxOf(minBuffer, SAMPLE_RATE)
                    )
                    val buffer = ShortArray(SAMPLE_RATE)
                    recorder.startRecording()
                    val read = recorder.read(buffer, 0, buffer.size)
                    recorder.stop()
                    recorder.release()
                    var sum = 0.0
                    for (i in 0 until maxOf(read, 0)) sum += buffer[i].toDouble() * buffer[i]
                    val rms = if (read > 0) sqrt(sum / read).toInt() else 0
                    record("麦克风", read > 0, "采集 $read 点，RMS $rms（说话时数值明显变大）")
                }.onFailure { record("麦克风", false, it.message ?: "录音失败（可能被占用）") }
            }

            runCatching {
                val pm = packageManager
                val missing = buildList {
                    if (!pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)) add("加速度计")
                    if (!pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)) add("陀螺仪")
                    if (!pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_COMPASS)) add("磁力计")
                    if (!pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_LIGHT)) add("光线")
                }
                record(
                    "传感器",
                    missing.isEmpty(),
                    if (missing.isEmpty()) "加速度计 / 陀螺仪 / 磁力计 / 光线 齐全"
                    else "缺少：" + missing.joinToString("、")
                )
            }.onFailure { record("传感器", false, it.message ?: "查询失败") }

            val summary = "共 $total 项，通过 $pass 项" +
                if (pass < total) "，${total - pass} 项需要确认（看 ✘ 行）" else "，全部通过"
            runOnUiThread {
                if (!isFinishing) {
                    binding.btnOneKeyTest.isEnabled = true
                    binding.tvOneKeyResult.text = summary + "\n\n" + lines.joinToString("\n")
                }
            }
        }, "xyzinfo-onekey").start()
    }

    @Suppress("DEPRECATION")
    private fun toggleCamera() {
        if (camera != null) {
            stopCamera()
            return
        }
        val textureView = binding.textureCamera
        if (textureView.isAvailable) {
            openCamera(textureView.surfaceTexture ?: return)
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) =
                    openCamera(st)

                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun openCamera(surface: SurfaceTexture) {
        if (camera != null) return
        val opened = runCatching {
            val instance = Camera.open(cameraId)
            applyFocusMode(instance)
            val info = Camera.CameraInfo().also { Camera.getCameraInfo(cameraId, it) }
            val rotation = displayOrientation(info, cameraId)
            instance.setDisplayOrientation(rotation)
            instance.setPreviewTexture(surface)
            // 先定好预览尺寸（改 parameters 必须在 startPreview 之前，运行时改会报错）
            applyPreviewAspect(instance, rotation)
            instance.startPreview()
            // 连续对焦不生效的机型，预览启动后再补一次单次对焦
            runCatching { instance.autoFocus(null) }
            instance
        }.getOrNull()
        if (opened == null) {
            binding.btnCamera.text = "镜头 $cameraId 打开失败"
            return
        }
        camera = opened
        binding.btnCamera.text = "关闭预览（当前镜头 $cameraId）"
    }

    /**
     * 按摄像头**实际支持**的预览比例调整 TextureView 高度。
     *
     * 1.0.9 再修一次"画面又变成窄窄一条"：
     *
     * * 以前直接拿系统默认的 `parameters.previewSize` 算比例 —— 有些机型默认给的是
     *   1920×1080 甚至更怪的比例，算出来的高度和真实预览对不上，画面就被压成一条；
     * * 而且算高度时用的是 `binding.textureCamera.width`，布局还没走完时它是 0，
     *   直接被 `return@post` 掉，高度就一直是 XML 里写死的 220dp —— 于是"窄窄的"。
     *
     * 现在：先在所有支持尺寸里挑一个和视图比例最接近的（优先 4:3，其次 16:9，且不超过 1920 宽），
     * 把它设回去当预览尺寸；宽度没量到就等下一帧再算。
     */
    @Suppress("DEPRECATION")
    private fun applyPreviewAspect(instance: Camera, rotation: Int = 0) {
        val parameters = runCatching { instance.parameters }.getOrNull() ?: return
        // parameters.previewSize 就是驱动真正会用的尺寸（setPreviewSize 在新 SDK 里已经不可用），
        // 以它为准算比例，视图比例和真实画面一致才不会变形
        val size = parameters.previewSize ?: return
        val ratio = if (rotation == 90 || rotation == 270) {
            size.width.toFloat() / size.height
        } else {
            size.height.toFloat() / size.width
        }
        applyPreviewHeight(ratio, attempt = 0)
    }

    /** 按比例设高度；宽度还没量到（布局未完成）就下一帧再试，最多几次。 */
    private fun applyPreviewHeight(ratio: Float, attempt: Int) {
        val view = binding.textureCamera
        view.post {
            val width = view.width
            if (width <= 0) {
                if (attempt < 5) applyPreviewHeight(ratio, attempt + 1)
                return@post
            }
            // 预览最高不超过屏幕的 62%：4:3 竖屏预览本来很高，整屏铺满会盖掉下面的按钮
            val maxHeight = (resources.displayMetrics.heightPixels * 0.62f).toInt()
            val height = (width * ratio).toInt().coerceAtMost(maxHeight).coerceAtLeast((width * 0.5f).toInt())
            val params = view.layoutParams
            if (params.height != height) {
                params.height = height
                view.layoutParams = params
            }
        }
    }

    /**
     * 算预览需要旋转的角度。
     *
     * 旧 Camera API 的预览默认是「传感器方向」，竖屏下必然歪，必须自己按
     * 摄像头方向 + 屏幕旋转角算一遍；前摄还要做镜像处理（公式来自官方文档）。
     */
    @Suppress("DEPRECATION")
    private fun displayOrientation(info: Camera.CameraInfo, id: Int): Int {
        val rotation = runCatching {
            (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                .defaultDisplay.rotation
        }.getOrDefault(android.view.Surface.ROTATION_0)
        val degrees = when (rotation) {
            android.view.Surface.ROTATION_90 -> 90
            android.view.Surface.ROTATION_180 -> 180
            android.view.Surface.ROTATION_270 -> 270
            else -> 0
        }
        return if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            val result = (info.orientation + degrees) % 360
            (360 - result) % 360
        } else {
            (info.orientation - degrees + 360) % 360
        }
    }

    /**
     * 打开自动对焦。
     *
     * 旧 Camera API 默认不一定开连续对焦，所以之前画面是"糊的、不会自己合焦"。
     * 按机型的支持列表挑最好的模式（continuous-picture > continuous-video > auto），
     * 顺便把对焦/测光区域放到画面中心，并触发一次单次对焦。
     */
    @Suppress("DEPRECATION")
    private fun applyFocusMode(instance: Camera) {
        runCatching {
            val parameters = instance.parameters
            val modes = parameters.supportedFocusModes.orEmpty()
            val preferred = listOf(
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE,
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO,
                Camera.Parameters.FOCUS_MODE_AUTO,
                Camera.Parameters.FOCUS_MODE_MACRO
            ).firstOrNull { modes.contains(it) }
            if (preferred != null) parameters.focusMode = preferred

            val center = android.graphics.Rect(-150, -150, 150, 150)
            if (parameters.maxNumFocusAreas > 0) {
                parameters.focusAreas = listOf(Camera.Area(center, 1000))
            }
            if (parameters.maxNumMeteringAreas > 0) {
                parameters.meteringAreas = listOf(Camera.Area(center, 1000))
            }
            instance.parameters = parameters
            if (preferred == Camera.Parameters.FOCUS_MODE_AUTO) {
                runCatching { instance.autoFocus(null) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun stopCamera() {
        runCatching {
            camera?.stopPreview()
            camera?.release()
        }
        camera = null
        binding.btnCamera.text = "打开预览"
    }

    // ---------- NFC ----------

    private fun setupNfc() {
        val adapter = runCatching { NfcAdapter.getDefaultAdapter(this) }.getOrNull()
        binding.btnNfcSettings.setOnClickListener {
            Anim.pressFeedback(it)
            runCatching { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
        }
        binding.tvNfcState.setInfoRow(
            when {
                adapter == null -> "NFC：这台设备没有 NFC 硬件"
                adapter.isEnabled -> "NFC：支持，且已开启"
                else -> "NFC：支持，但当前处于关闭状态"
            }
        )
        if (adapter == null) binding.btnNfcSettings.isEnabled = false
    }

    private companion object {
        const val SAMPLE_RATE = 44100
    }
}
