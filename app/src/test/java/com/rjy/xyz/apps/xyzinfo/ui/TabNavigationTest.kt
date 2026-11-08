package com.rjy.xyz.apps.xyzinfo.ui

import android.content.Intent
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TabNavigationTest {

    @Test
    fun `标签切换复用已有页面实例`() {
        val activity = Robolectric.buildActivity(
            com.rjy.xyz.apps.xyzinfo.MainActivity::class.java
        ).setup().get()
        val intent = GlassScaffold.tabIntent(activity, GlassScaffold.TAB_BENCHMARK)

        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
        assertNotEquals(activity.isFinishing, true)
    }
}
