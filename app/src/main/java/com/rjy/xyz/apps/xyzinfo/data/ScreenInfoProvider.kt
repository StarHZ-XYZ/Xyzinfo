package com.rjy.xyz.apps.xyzinfo.data

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import com.rjy.xyz.apps.xyzinfo.model.ScreenInfo
import com.rjy.xyz.apps.xyzinfo.util.DeviceFacts
import com.rjy.xyz.apps.xyzinfo.util.Labels
import com.rjy.xyz.apps.xyzinfo.util.ProcFs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 读取屏幕分辨率、密度、刷新率、触控与 HDR 能力。
 */
object ScreenInfoProvider {

    private val TOUCH_RATE_PATHS = listOf(
        "/sys/class/touch/touch_dev/game_rate",
        "/sys/class/touch/touch_dev/touch_rate",
        "/sys/devices/virtual/touch/touch_dev/game_rate",
        "/sys/devices/virtual/touch/touch_dev/touch_rate",
        "/proc/touchpanel/report_rate_white_list",
        "/proc/tp_info"
    )

    private val BRIGHTNESS_PATHS = listOf(
        "/sys/class/backlight/panel0-backlight/max_brightness",
        "/sys/class/backlight/backlight/max_brightness",
        "/sys/leds/lcd-backlight/max_brightness"
    )

    /** 系统不公开触控点数上限，只要检测到触控屏就至少按 10 点处理。 */
    private const val TOUCH_POINTS_MIN_HINT = 10

    fun load(context: Context): ScreenInfo {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = currentDisplay(context, windowManager)
        val metrics = realMetrics(context, display)

        val widthPx = metrics.widthPixels
        val heightPx = metrics.heightPixels
        val xdpi = metrics.xdpi.toDouble()
        val ydpi = metrics.ydpi.toDouble()
        val hdr = readHdrCapabilities(display)

        return ScreenInfo(
            widthPx = widthPx,
            heightPx = heightPx,
            xdpi = xdpi,
            ydpi = ydpi,
            diagonalInches = calculateDiagonalInches(widthPx, heightPx, xdpi, ydpi),
            densityDpi = metrics.densityDpi,
            density = metrics.density,
            densityBucket = densityBucket(metrics.densityDpi),
            landscape = widthPx > heightPx,
            currentRefreshRateHz = display?.refreshRate ?: 0f,
            supportedRefreshRatesHz = supportedRefreshRates(display),
            touchPointsHint = detectTouchPoints(context),
            touchSampleRateHz = detectTouchSampleRate(),
            hdrDetectable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N,
            hdrSupportedTypes = hdr.types,
            hdrMaxLuminance = hdr.maxLuminance,
            hdrMinLuminance = hdr.minLuminance,
            wideColorGamut = detectWideColorGamut(context),
            maxBrightnessNode = ProcFs.firstText(BRIGHTNESS_PATHS)?.trim(),
            rawPreview = buildRawPreview(widthPx, heightPx, xdpi, ydpi, metrics.densityDpi, display)
        )
    }

    @Suppress("DEPRECATION")
    private fun currentDisplay(context: Context, windowManager: WindowManager): Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 少数 Context（如仅在后台创建的场景）取不到 Display，退回默认显示
            runCatching { context.display }.getOrNull() ?: windowManager.defaultDisplay
        } else {
            windowManager.defaultDisplay
        }

    @Suppress("DEPRECATION")
    private fun realMetrics(context: Context, display: Display?): DisplayMetrics {
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            metrics.setTo(context.resources.displayMetrics)
        } else {
            display?.getRealMetrics(metrics)
        }
        return metrics
    }

    /** 由像素宽高与 xdpi/ydpi 计算对角线英寸数。 */
    private fun calculateDiagonalInches(
        widthPx: Int,
        heightPx: Int,
        xdpi: Double,
        ydpi: Double
    ): Double? {
        if (xdpi <= 0.0 || ydpi <= 0.0) return null
        val widthInches = widthPx / xdpi
        val heightInches = heightPx / ydpi
        return runCatching { sqrt(widthInches.pow(2.0) + heightInches.pow(2.0)) }.getOrNull()
    }

    private fun densityBucket(densityDpi: Int): String = when {
        densityDpi >= 640 -> "xxxhdpi"
        densityDpi >= 480 -> "xxhdpi"
        densityDpi >= 320 -> "xhdpi"
        densityDpi >= 240 -> "hdpi"
        densityDpi >= 160 -> "mdpi"
        else -> "ldpi"
    }

    @Suppress("DEPRECATION")
    private fun supportedRefreshRates(display: Display?): List<Float> {
        if (display == null) return emptyList()
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                display.supportedModes.map { it.refreshRate }.distinct().sorted()
            } else {
                listOf(display.refreshRate)
            }
        }.getOrDefault(emptyList())
    }

    /** 系统只暴露“存在触控屏”，因此返回已知下限；无触控屏时返回 null。 */
    private fun detectTouchPoints(context: Context): Int? = runCatching {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val hasTouchScreen = inputManager.inputDeviceIds.any { id ->
            val device = inputManager.getInputDevice(id) ?: return@any false
            val isTouchScreen =
                device.sources and InputDevice.SOURCE_TOUCHSCREEN == InputDevice.SOURCE_TOUCHSCREEN
            isTouchScreen && device.getMotionRange(MotionEvent.AXIS_X) != null
        }
        if (hasTouchScreen) TOUCH_POINTS_MIN_HINT else null
    }.getOrNull()

    private fun detectTouchSampleRate(): Int? {
        for (path in TOUCH_RATE_PATHS) {
            val text = ProcFs.readText(path)
            if (text.isBlank()) continue

            Regex("""(\d{2,4})\s*hz""", RegexOption.IGNORE_CASE)
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?.let { return it }

            text.trim().toIntOrNull()
                ?.takeIf { it in 60..2000 }
                ?.let { return it }
        }
        return null
    }

    private fun readHdrCapabilities(display: Display?): HdrCapabilities {
        if (display == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return HdrCapabilities()
        }

        return runCatching {
            val capabilities = display.hdrCapabilities ?: return HdrCapabilities()
            HdrCapabilities(
                types = capabilities.supportedHdrTypes.map { hdrTypeName(it) }.distinct(),
                maxLuminance = capabilities.desiredMaxLuminance,
                minLuminance = capabilities.desiredMinLuminance
            )
        }.getOrDefault(HdrCapabilities())
    }

    private fun hdrTypeName(type: Int): String = when (type) {
        Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
        Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
        else -> "其他HDR"
    }

    private fun detectWideColorGamut(context: Context): Boolean? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.resources.configuration.isScreenWideColorGamut }.getOrNull()
        } else {
            null
        }

    @Suppress("DEPRECATION")
    private fun buildRawPreview(
        widthPx: Int,
        heightPx: Int,
        xdpi: Double,
        ydpi: Double,
        densityDpi: Int,
        display: Display?
    ): String {
        val refreshModes = supportedRefreshRates(display)
            .joinToString(" / ") { it.toString() }
            .ifBlank { Labels.UNKNOWN }
        val hdr = readHdrCapabilities(display)
        val hdrText = hdr.types.joinToString(" / ").ifBlank { "不支持或系统未公开" }

        return """
            原始显示信息预览：
            widthPixels: $widthPx
            heightPixels: $heightPx
            xdpi: ${"%.2f".format(xdpi)}
            ydpi: ${"%.2f".format(ydpi)}
            densityDpi: $densityDpi
            refreshModes: $refreshModes
            hdrSupport: $hdrText
            hdrCurrentStatus: 系统未公开
            model: ${DeviceFacts.orUnknown(Build.MODEL)}
            device: ${DeviceFacts.orUnknown(Build.DEVICE)}
        """.trimIndent()
    }

    private data class HdrCapabilities(
        val types: List<String> = emptyList(),
        val maxLuminance: Float = -1f,
        val minLuminance: Float = -1f
    )
}
