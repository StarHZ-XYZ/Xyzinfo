package com.rjy.xyz.apps.xyzinfo.ui.common

import kotlin.math.pow

/**
 * 四季氛围的"重力"换算：把加速度计的读数变成**下落方向的偏角**。
 *
 * ## 方向为什么容易搞反
 *
 * Android 的加速度计给的是**比力**（proper acceleration）：静止时它等于 `-g` 在设备坐标系里的分量。
 * 设备**右侧朝下**倾斜时，重力方向在设备坐标里指向 +X，于是 `values[0]` 是**负**的。
 * 也就是说：要把粒子往右推，得用 `-values[0]`。第一版就是这里没取反，结果整片往反方向飘。
 *
 * ## 幅度为什么容易太小
 *
 * 手持时设备一般只歪 10~20°，`sin` 出来只有 0.17~0.34。直接把这 0.2 当强度用，
 * 粒子几乎看不出变化。这里做了三件事把它放大到"一眼可见"：
 * 1. 去掉 ±0.05g 的死区（手抖不算倾斜）；
 * 2. 归一化后再开 0.65 次幂，小角度被显著抬升；
 * 3. 最终映射到最多 [MAX_TILT_RADIANS]（约 57°）的下落偏角 —— 粒子是真的"斜着落"，
 *    同时贴图也跟着倾斜这个角度，视觉上非常明确。
 */
object SeasonTilt {

    /** 最大下落偏角（弧度）。57° 已经很夸张了，配套的贴图倾斜也按它来。 */
    const val MAX_TILT_RADIANS = 1.0f

    /** 死区：小于这个横向量当作没歪（手抖、轻微不平）。 */
    const val DEAD_ZONE = 0.05f

    /** 提权指数：越小，小角度响应越强。 */
    private const val SHAPING = 0.65

    /**
     * @param accelerometerX 加速度计的 X 轴读数（m/s²，含重力）
     * @return 下落方向相对"竖直向下"的偏角（弧度）：往右歪为正、往左歪为负
     */
    fun tiltFromAccelerometerX(accelerometerX: Float): Float {
        // 关键的一步：设备右倾时读数是负的，所以这里取反才是"屏幕上的右"
        val raw = (-accelerometerX / SensorGravity).coerceIn(-1.5f, 1.5f)
        val magnitude = kotlin.math.abs(raw) - DEAD_ZONE
        if (magnitude <= 0f) return 0f
        val normalized = (magnitude / (1f - DEAD_ZONE)).coerceIn(0f, 1f)
        val shaped = normalized.toDouble().pow(SHAPING).toFloat()
        return if (raw < 0f) -shaped * MAX_TILT_RADIANS else shaped * MAX_TILT_RADIANS
    }

    /** 标准重力加速度（SensorManager.GRAVITY_EARTH 的数值，放这里方便单测）。 */
    const val SensorGravity = 9.80665f
}
