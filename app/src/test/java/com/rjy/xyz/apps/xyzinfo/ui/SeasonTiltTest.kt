package com.rjy.xyz.apps.xyzinfo.ui

import com.rjy.xyz.apps.xyzinfo.ui.common.SeasonTilt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 四季重力换算的回归测试。
 *
 * 这一块第一版做错过两件事，所以直接用测试钉住：
 * 1. **方向反了** —— 设备右侧朝下时，加速度计 X 是**负**的，必须取反才是"屏幕上的右"；
 * 2. **幅度太小** —— 手持只歪十几度，sin 出来才 0.2 上下，不做提权基本看不出效果。
 */
class SeasonTiltTest {

    private val g = SeasonTilt.SensorGravity

    @Test
    fun `设备右倾时粒子往右落（方向不能再反）`() {
        // 右倾：设备坐标里重力指向 +X，所以加速度计读数是负的
        val tilt = SeasonTilt.tiltFromAccelerometerX(-g)
        assertTrue("右倾应该是正偏角，实际 $tilt", tilt > 0f)
        assertTrue("满偏时应该接近最大角度，实际 $tilt", tilt > SeasonTilt.MAX_TILT_RADIANS * 0.9f)
    }

    @Test
    fun `设备左倾时粒子往左落`() {
        assertTrue(SeasonTilt.tiltFromAccelerometerX(g) < 0f)
    }

    @Test
    fun `放平或手抖不产生偏移`() {
        assertEquals(0f, SeasonTilt.tiltFromAccelerometerX(0f), 0.0001f)
        assertEquals(0f, SeasonTilt.tiltFromAccelerometerX(-0.2f), 0.0001f)
        assertEquals(0f, SeasonTilt.tiltFromAccelerometerX(0.2f), 0.0001f)
    }

    @Test
    fun `手持常见的十几度倾斜也要有明显幅度`() {
        // 歪 15°：sin(15°) ≈ 0.26g
        val tilt = SeasonTilt.tiltFromAccelerometerX(-0.26f * g)
        val degrees = Math.toDegrees(tilt.toDouble())
        assertTrue("15° 倾斜应给出至少 12° 的下落偏角，实际 $degrees", degrees >= 12.0)
        assertTrue("但也不该夸张到满偏，实际 $degrees", degrees <= 40.0)
    }

    @Test
    fun `幅度随倾角单调增加`() {
        val small = SeasonTilt.tiltFromAccelerometerX(-0.15f * g)
        val medium = SeasonTilt.tiltFromAccelerometerX(-0.5f * g)
        val large = SeasonTilt.tiltFromAccelerometerX(-0.95f * g)
        assertTrue(small < medium)
        assertTrue(medium < large)
    }
}
