package com.rjy.xyz.apps.xyzinfo.ui

import android.view.View
import android.view.ViewGroup
import com.rjy.xyz.apps.xyzinfo.MainActivity
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassBottomBar
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TabNavigationTest {

    @Test
    fun `五个顶栏标签共用同一个页面壳和底栏`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val firstBar = findBottomBar(activity.window.decorView)

        activity.switchTab(GlassScaffold.TAB_BENCHMARK)
        assertEquals(GlassScaffold.TAB_BENCHMARK, firstBar.currentTab)
        assertSame("切页后不能创建第二条底栏", firstBar, findBottomBar(activity.window.decorView))

        activity.switchTab(GlassScaffold.TAB_SETTINGS)
        assertEquals(GlassScaffold.TAB_SETTINGS, firstBar.currentTab)
        assertSame("继续切页仍应复用同一条底栏", firstBar, findBottomBar(activity.window.decorView))

        activity.switchTab(GlassScaffold.TAB_HOME)
        assertEquals(GlassScaffold.TAB_HOME, firstBar.currentTab)
        assertEquals(false, activity.isFinishing)
    }

    private fun findBottomBar(view: View): GlassBottomBar {
        if (view is GlassBottomBar && view.id != R.id.glassBarPreview) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                runCatching { return findBottomBar(view.getChildAt(index)) }
            }
        }
        error("No GlassBottomBar in hierarchy")
    }
}
