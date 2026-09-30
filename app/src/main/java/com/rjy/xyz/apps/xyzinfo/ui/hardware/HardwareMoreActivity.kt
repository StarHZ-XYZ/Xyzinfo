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
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = if (vibrator.hasAmplitudeControl()) {
                    if (strong) 255 else 180
                } else {
                    VibrationEffect.DEFAULT_AMPLITUDE
                }
                vibrateWithAlarmAttributes(
                    vibrator,
                    VibrationEffect.createOneShot(millis, amplitude)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(millis)
            }
        }
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
            instance.setDisplayOrientation(displayOrientation(info, cameraId))
            instance.setPreviewTexture(surface)
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
