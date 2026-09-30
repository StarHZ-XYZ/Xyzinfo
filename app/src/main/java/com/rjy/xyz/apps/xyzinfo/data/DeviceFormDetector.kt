package com.rjy.xyz.apps.xyzinfo.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat

/** 设备形态。 */
enum class DeviceForm(val label: String) {
    PHONE("手机"),
    TABLET("平板"),
    FOLDABLE("折叠屏")
}

/** 形态识别结果。 */
data class DeviceFormInfo(
    val form: DeviceForm,
    val widthDp: Int,
    val heightDp: Int,
    val smallestWidthDp: Int,
    /** 折叠屏专有：当前是展开还是折叠（按屏宽推断）。 */
    val unfolded: Boolean?
) {
    /** 界面用的完整描述，例如「折叠屏（已展开）｜ 673×727dp」。 */
    val description: String
        get() = buildString {
            append(form.label)
            if (unfolded != null) append(if (unfolded) "（已展开）" else "（已折叠）")
            append("｜ $widthDp×$heightDp dp")
        }
}

/**
 * 识别设备是手机 / 平板 / 折叠屏。
 *
 * 判据（按可靠性排序）：
 * 1. 有铰链角度传感器（`android.sensor.hinge_angle`，Android 11+）→ 折叠屏，最硬的证据；
 * 2. `smallestScreenWidthDp >= 600` → 平板（Android 官方对平板的定义就是这条）；
 * 3. 其余算手机。
 *
 * 折叠屏的「展开 / 折叠」不读传感器（读一次要注册监听、还要考虑权限与耗电），
 * 直接用当前屏宽推断：折叠态一般 < 600dp，展开态 >= 600dp。
 */
object DeviceFormDetector {

    fun detect(context: Context): DeviceFormInfo {
        val configuration = context.resources.configuration
        val widthDp = configuration.screenWidthDp
        val heightDp = configuration.screenHeightDp
        val smallest = configuration.smallestScreenWidthDp
        val hasHinge = hasHingeSensor(context)

        val form = when {
            hasHinge -> DeviceForm.FOLDABLE
            smallest >= TABLET_MIN_WIDTH_DP -> DeviceForm.TABLET
            else -> DeviceForm.PHONE
        }
        return DeviceFormInfo(
            form = form,
            widthDp = widthDp,
            heightDp = heightDp,
            smallestWidthDp = smallest,
            unfolded = if (form == DeviceForm.FOLDABLE) widthDp >= TABLET_MIN_WIDTH_DP else null
        )
    }

    /** 宽屏（平板 / 展开态折叠屏）——排版要按宽屏适配。 */
    fun isWide(info: DeviceFormInfo): Boolean =
        info.widthDp >= TABLET_MIN_WIDTH_DP || info.form == DeviceForm.TABLET

    private fun hasHingeSensor(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return runCatching {
            val manager = ContextCompat.getSystemService(context, SensorManager::class.java)
                ?: return false
            manager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null
        }.getOrDefault(false)
    }

    /** Android 对平板的官方定义：最小宽度 600dp。 */
    const val TABLET_MIN_WIDTH_DP = 600
}
