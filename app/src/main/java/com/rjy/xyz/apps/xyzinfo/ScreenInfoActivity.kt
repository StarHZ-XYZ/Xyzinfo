package com.rjy.xyz.apps.xyzinfo

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import android.view.MotionEvent
import android.view.WindowManager
import android.hardware.input.InputManager
import android.view.InputDevice
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

class ScreenInfoActivity : AppCompatActivity() {

    private lateinit var tvScreenTitle: TextView
    private lateinit var tvScreenSubTitle: TextView

    private lateinit var tvResolution: TextView
    private lateinit var tvScreenSize: TextView
    private lateinit var tvDensity: TextView
    private lateinit var tvOrientation: TextView

    private lateinit var tvCurrentRefreshRate: TextView
    private lateinit var tvRefreshModes: TextView
    private lateinit var tvTouchPoints: TextView
    private lateinit var tvTouchSampleRate: TextView

    private lateinit var tvHdrSupport: TextView
    private lateinit var tvHdrCurrentStatus: TextView
    private lateinit var tvWideColor: TextView
    private lateinit var tvBrightnessHint: TextView
    private lateinit var tvRawDisplayInfo: TextView

    private lateinit var cardHeader: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen_info)

        initViews()
        loadScreenInfo()
    }

    private fun initViews() {
        tvScreenTitle = findViewById(R.id.tvScreenTitle)
        tvScreenSubTitle = findViewById(R.id.tvScreenSubTitle)

        tvResolution = findViewById(R.id.tvResolution)
        tvScreenSize = findViewById(R.id.tvScreenSize)
        tvDensity = findViewById(R.id.tvDensity)
        tvOrientation = findViewById(R.id.tvOrientation)

        tvCurrentRefreshRate = findViewById(R.id.tvCurrentRefreshRate)
        tvRefreshModes = findViewById(R.id.tvRefreshModes)
        tvTouchPoints = findViewById(R.id.tvTouchPoints)
        tvTouchSampleRate = findViewById(R.id.tvTouchSampleRate)

        tvHdrSupport = findViewById(R.id.tvHdrSupport)
        tvHdrCurrentStatus = findViewById(R.id.tvHdrCurrentStatus)
        tvWideColor = findViewById(R.id.tvWideColor)
        tvBrightnessHint = findViewById(R.id.tvBrightnessHint)
        tvRawDisplayInfo = findViewById(R.id.tvRawDisplayInfo)

        cardHeader = findViewById(R.id.cardHeader)
    }

    @Suppress("DEPRECATION")
    private fun loadScreenInfo() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this.display
        } else {
            wm.defaultDisplay
        }

        val realMetrics = DisplayMetrics()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val currentMetrics = resources.displayMetrics
            realMetrics.setTo(currentMetrics)
        } else {
            display?.getRealMetrics(realMetrics)
        }

        val widthPx = realMetrics.widthPixels
        val heightPx = realMetrics.heightPixels
        val xdpi = realMetrics.xdpi.toDouble()
        val ydpi = realMetrics.ydpi.toDouble()

        val screenInches = calculateScreenSizeInches(widthPx, heightPx, xdpi, ydpi)
        val densityDpi = realMetrics.densityDpi
        val densityText = buildDensityText(densityDpi, realMetrics.density)
        val orientationText = if (widthPx > heightPx) "横屏" else "竖屏"

        val currentRefresh = display?.refreshRate ?: 0f
        val refreshModes = getSupportedRefreshRates(display)
        val touchPoints = detectMaxTouchPoints()
        val touchSampleRate = detectTouchSampleRate()
        val hdrSupport = detectHdrSupport(display)
        val hdrCurrentStatus = detectHdrCurrentStatus(display)
        val wideColor = detectWideColorSupport()
        val brightnessHint = detectBrightnessHint()

        tvScreenTitle.text = "${widthPx} × ${heightPx}"
        tvScreenSubTitle.text = "分辨率、刷新率与触控能力总览"

        tvResolution.text = "屏幕分辨率：${widthPx} × ${heightPx}"
        tvScreenSize.text = "屏幕尺寸：$screenInches 英寸"
        tvDensity.text = "屏幕密度：$densityText"
        tvOrientation.text = "当前方向：$orientationText"

        tvCurrentRefreshRate.text =
            "当前刷新率：${String.format(Locale.getDefault(), "%.1f Hz", currentRefresh)}"
        tvRefreshModes.text = "支持刷新率档位：$refreshModes"
        tvTouchPoints.text = "最大触控点数：$touchPoints"
        tvTouchSampleRate.text = "触控采样率：$touchSampleRate"

        tvHdrSupport.text = "HDR 支持：$hdrSupport"
        tvHdrCurrentStatus.text = "HDR 当前状态：$hdrCurrentStatus"
        tvWideColor.text = "广色域支持：$wideColor"
        tvBrightnessHint.text = "亮度提示：$brightnessHint"

        tvRawDisplayInfo.text = buildRawDisplayInfo(
            widthPx = widthPx,
            heightPx = heightPx,
            xdpi = xdpi,
            ydpi = ydpi,
            densityDpi = densityDpi,
            refreshModes = refreshModes,
            hdrSupport = hdrSupport,
            hdrCurrentStatus = hdrCurrentStatus
        )
    }

    private fun calculateScreenSizeInches(
        widthPx: Int,
        heightPx: Int,
        xdpi: Double,
        ydpi: Double
    ): String {
        return try {
            if (xdpi <= 0.0 || ydpi <= 0.0) return "未知"
            val widthInches = widthPx / xdpi
            val heightInches = heightPx / ydpi
            val diagonal = sqrt(widthInches.pow(2.0) + heightInches.pow(2.0))
            String.format(Locale.getDefault(), "%.2f", diagonal)
        } catch (_: Exception) {
            "未知"
        }
    }

    private fun buildDensityText(densityDpi: Int, density: Float): String {
        val bucket = when {
            densityDpi >= 640 -> "xxxhdpi"
            densityDpi >= 480 -> "xxhdpi"
            densityDpi >= 320 -> "xhdpi"
            densityDpi >= 240 -> "hdpi"
            densityDpi >= 160 -> "mdpi"
            else -> "ldpi"
        }
        return "$densityDpi dpi / ${String.format(Locale.getDefault(), "%.2f", density)}x / $bucket"
    }

    @Suppress("DEPRECATION")
    private fun getSupportedRefreshRates(display: Display?): String {
        if (display == null) return "未知"

        val rates = mutableSetOf<Float>()

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                display.supportedModes.forEach { mode ->
                    rates.add(mode.refreshRate)
                }
            } else {
                rates.add(display.refreshRate)
            }

            if (rates.isEmpty()) {
                "未知"
            } else {
                rates.sorted().joinToString(" / ") {
                    String.format(Locale.getDefault(), "%.0fHz", it)
                }
            }
        } catch (_: Exception) {
            "未知"
        }
    }

    private fun detectMaxTouchPoints(): String {
        return try {
            val inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
            var maxPoints = 0

            inputManager.inputDeviceIds.forEach { id ->
                val device = inputManager.getInputDevice(id)
                if (device != null &&
                    device.sources and InputDevice.SOURCE_TOUCHSCREEN == InputDevice.SOURCE_TOUCHSCREEN
                ) {
                    val range = device.getMotionRange(MotionEvent.AXIS_X)
                    if (range != null) {
                        maxPoints = maxPoints.coerceAtLeast(10)
                    }
                }
            }

            if (maxPoints > 0) "${maxPoints} 点或以上" else "系统未公开"
        } catch (_: Exception) {
            "系统未公开"
        }
    }

    private fun detectTouchSampleRate(): String {
        val candidatePaths = listOf(
            "/sys/class/touch/touch_dev/game_rate",
            "/sys/class/touch/touch_dev/touch_rate",
            "/sys/devices/virtual/touch/touch_dev/game_rate",
            "/sys/devices/virtual/touch/touch_dev/touch_rate",
            "/proc/touchpanel/report_rate_white_list",
            "/proc/tp_info"
        )

        for (path in candidatePaths) {
            val text = readFileText(path)
            if (text.isNotBlank()) {
                val regex = Regex("""(\d{2,4})\s*hz""", RegexOption.IGNORE_CASE)
                val match = regex.find(text)
                if (match != null) {
                    return "${match.groupValues[1]} Hz"
                }

                val number = text.trim().toIntOrNull()
                if (number != null && number in 60..2000) {
                    return "$number Hz"
                }
            }
        }
        return "系统未公开"
    }

    private fun detectHdrSupport(display: Display?): String {
        if (display == null) return "未知"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val caps: Display.HdrCapabilities? = display.hdrCapabilities
                val types = caps?.supportedHdrTypes ?: IntArray(0)

                if (types.isEmpty()) {
                    "不支持或系统未公开"
                } else {
                    val names = types.map {
                        when (it) {
                            Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                            Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                            Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                            Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
                            else -> "其他HDR"
                        }
                    }.distinct()

                    names.joinToString(" / ")
                }
            } else {
                "系统版本过低"
            }
        } catch (_: Exception) {
            "系统未公开"
        }
    }

    private fun detectHdrCurrentStatus(display: Display?): String {
        if (display == null) return "系统未公开"

        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return "系统版本过低"
            }

            val caps: Display.HdrCapabilities? = display.hdrCapabilities
            val supported = caps?.supportedHdrTypes?.isNotEmpty() == true

            if (!supported) {
                return "当前不可用"
            }

            val maxLum = caps?.desiredMaxLuminance ?: -1f
            val minLum = caps?.desiredMinLuminance ?: -1f

            when {
                maxLum > 0f && minLum >= 0f -> "支持 HDR，系统未公开实时状态"
                else -> "支持 HDR，但系统未公开当前状态"
            }
        } catch (_: Exception) {
            "系统未公开"
        }
    }

    private fun detectWideColorSupport(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (resources.configuration.isScreenWideColorGamut) "支持" else "不支持"
            } else {
                "系统版本过低"
            }
        } catch (_: Exception) {
            "系统未公开"
        }
    }

    private fun detectBrightnessHint(): String {
        val paths = listOf(
            "/sys/class/backlight/panel0-backlight/max_brightness",
            "/sys/class/backlight/backlight/max_brightness",
            "/sys/class/leds/lcd-backlight/max_brightness"
        )

        for (path in paths) {
            val value = readFileText(path).trim()
            if (value.isNotBlank()) {
                return "系统最大亮度节点值：$value"
            }
        }
        return "系统未公开"
    }

    private fun buildRawDisplayInfo(
        widthPx: Int,
        heightPx: Int,
        xdpi: Double,
        ydpi: Double,
        densityDpi: Int,
        refreshModes: String,
        hdrSupport: String,
        hdrCurrentStatus: String
    ): String {
        return """
            原始显示信息预览：
            widthPixels: $widthPx
            heightPixels: $heightPx
            xdpi: ${String.format(Locale.getDefault(), "%.2f", xdpi)}
            ydpi: ${String.format(Locale.getDefault(), "%.2f", ydpi)}
            densityDpi: $densityDpi
            refreshModes: $refreshModes
            hdrSupport: $hdrSupport
            hdrCurrentStatus: $hdrCurrentStatus
            model: ${safe(Build.MODEL)}
            device: ${safe(Build.DEVICE)}
        """.trimIndent()
    }

    private fun readFileText(path: String): String {
        return try {
            File(path).readText()
        } catch (_: Exception) {
            ""
        }
    }

    private fun safe(value: String?): String {
        return if (value.isNullOrBlank()) "未知" else value
    }
}