package com.rjy.xyz.apps.xyzinfo.ui

import android.graphics.Color
import android.widget.ImageView
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.ui.soc.SocInfoActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 回归：深色模式下 CPU 页的芯片 logo 必须看得清。
 *
 * 曾经的 bug：夜里底板跟着品牌底色一起变深（骁龙是深红），而 logo 本身也是深红，
 * 两者糊成一片 —— 用户反馈"cpu 页面看不清 cpulogo"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-night-xxhdpi")
class SocBrandTileTest {

    @Test
    fun nightModeUsesLightTileBehindChipLogo() {
        val activity = Robolectric.buildActivity(SocInfoActivity::class.java).setup().get()
        val icon = activity.findViewById<ImageView>(R.id.ivBrandIcon)

        val tint = requireNotNull(icon.backgroundTintList) { "芯片 logo 底板缺少配色" }.defaultColor
        val luminance = (
            0.299 * Color.red(tint) + 0.587 * Color.green(tint) + 0.114 * Color.blue(tint)
            ) / 255.0
        assertTrue("深色模式下底板太暗（亮度 $luminance），logo 会糊在一起", luminance > 0.85)
    }

    @Test
    fun tileIsWideEnoughForWordmark() {
        val activity = Robolectric.buildActivity(SocInfoActivity::class.java).setup().get()
        val icon = activity.findViewById<ImageView>(R.id.ivBrandIcon)

        // 宽字标不能再被塞进正方框：控件宽度至少等于高度，字标铺得开才看得清
        assertTrue(
            "logo 槽位是 ${icon.layoutParams.width}px 宽 / ${icon.layoutParams.height}px 高，宽字标会被压扁",
            icon.layoutParams.width >= icon.layoutParams.height
        )
    }
}
