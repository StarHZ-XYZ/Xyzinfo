package com.rjy.xyz.apps.xyzinfo.ui

import android.view.MotionEvent
import android.view.View
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassBottomBar
import com.rjy.xyz.apps.xyzinfo.ui.settings.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 回归：设置页「外观」卡片顶部的底栏预览，点别的标签时**整块选中态都要跟着走**。
 *
 * 曾经的 bug：预览的回调是空的，点标签只让指示线（光标）滑过去，
 * `selectedIndex` 一直停在「设置」—— 图标选中色和背后那团光晕都不动，
 * 用户看到的就是"光标动了、光晕没跑过去"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class SettingsBottomBarPreviewTest {

    @Test
    fun tappingAnotherTabMovesTheWholeSelection() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val bar = activity.findViewById<GlassBottomBar>(R.id.glassBarPreview)
        assertEquals("预览初始选中「设置」", 4, bar.currentTab)

        layOut(bar)
        tap(bar, 0)

        assertEquals("点第一个标签后，选中态（含光晕）要挪过去", 0, bar.currentTab)

        layOut(bar)
        tap(bar, 2)

        assertEquals("再点第三个标签也一样", 2, bar.currentTab)
    }

    /** 底栏的标签位置要等布局完成才能算出来，测试里手动给一个确定的尺寸。 */
    private fun layOut(bar: GlassBottomBar) {
        bar.measure(
            View.MeasureSpec.makeMeasureSpec(BAR_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(BAR_HEIGHT, View.MeasureSpec.EXACTLY)
        )
        bar.layout(0, 0, BAR_WIDTH, BAR_HEIGHT)
    }

    /** 点在某个标签的正中间（分区是等宽的）。 */
    private fun tap(bar: GlassBottomBar, tabIndex: Int) {
        val tabWidth = BAR_WIDTH / 5f
        val x = tabWidth * tabIndex + tabWidth / 2f
        val y = BAR_HEIGHT / 2f
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(0L, 16L, MotionEvent.ACTION_UP, x, y, 0)
        bar.dispatchTouchEvent(down)
        bar.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private companion object {
        const val BAR_WIDTH = 1000
        const val BAR_HEIGHT = 160
    }
}
