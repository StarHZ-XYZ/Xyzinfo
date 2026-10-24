package com.rjy.xyz.apps.xyzinfo.ui

import android.content.Context
import android.view.View
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.ui.settings.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsHiddenFeatureTest {

    @Test
    fun `关于信息七连击解锁诊断入口`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("xyzinfo_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("diagnostics_unlocked", false)
            .commit()
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val about = activity.findViewById<View>(R.id.tvAbout)
        val button = activity.findViewById<View>(R.id.btnOpenDiagnostics)
        assertEquals(View.GONE, button.visibility)

        repeat(7) { about.performClick() }

        assertEquals(View.VISIBLE, button.visibility)
    }
}
