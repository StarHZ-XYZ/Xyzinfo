package com.rjy.xyz.apps.xyzinfo.ui

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.ui.network.NetworkSpeedActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 网络测速页的排版回归测试。
 *
 * 这个页面的两个坑都是"看图才发现"的那种，靠人眼盯图片很容易漏，所以写成断言：
 * 1. 并行连接数那一排 chip 一旦被挤到换行，整排会突然高一截，看起来像布局坏了；
 * 2. 「开始测速」是主按钮，掉到首屏下面就得先滚动才能点，体验直接打折。
 *
 * 尺寸固定成 411×915dp（与渲染自检同一套 qualifiers），所以这些数字是确定的。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class NetworkSpeedLayoutTest {

    private companion object {
        const val SCREEN_WIDTH = 1080
        const val SCREEN_HEIGHT = 2400
    }

    private fun setup(): AppCompatActivity {
        val activity: AppCompatActivity =
            Robolectric.buildActivity(NetworkSpeedActivity::class.java).setup().get()
        val root = activity.window.decorView
        root.measure(
            View.MeasureSpec.makeMeasureSpec(SCREEN_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(SCREEN_HEIGHT, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT)
        return activity
    }

    private fun view(activity: AppCompatActivity, name: String): View {
        val id = activity.resources.getIdentifier(name, "id", activity.packageName)
        assertTrue("布局里没有 $name", id != 0)
        return activity.window.decorView.findViewById(id)
            ?: error("布局里没有 $name")
    }

    @Test
    fun `关键控件都排得下_没有被挤成零尺寸`() {
        val activity = setup()
        listOf(
            "tvGeoStatus", "tvNodeName", "tvNodeDetail", "btnRelocate", "btnSwitchNode",
            "btnProbeNodes", "tvSpeedValue", "tvSpeedUnit", "progressSpeed",
            "tvDownload", "tvUpload", "tvPing", "tvJitter", "btnStart",
            "layoutUnitChips", "layoutParallelChips", "btnCompare"
        ).forEach { name ->
            val target = view(activity, name)
            assertTrue("$name 宽度为 0", target.width > 0)
            assertTrue("$name 高度为 0", target.height > 0)
        }
        activity.finish()
    }

    @Test
    fun `单位与并行chip都是单行且不超出可用宽度`() {
        val activity = setup()
        listOf("layoutUnitChips", "layoutParallelChips").forEach { rowName ->
            val row = view(activity, rowName) as LinearLayout
            assertTrue("$rowName 没有子项", row.childCount > 0)
            var used = 0
            for (index in 0 until row.childCount) {
                val chip = row.getChildAt(index) as TextView
                val margins = chip.layoutParams as ViewGroup.MarginLayoutParams
                used += chip.width + margins.marginStart + margins.marginEnd
                assertEquals(
                    "$rowName 里的「${chip.text}」被挤成了多行",
                    1,
                    chip.layout.lineCount
                )
            }
            assertTrue("$rowName 内容超出容器宽度（$used > ${row.width}）", used <= row.width)
        }
        activity.finish()
    }

    @Test
    fun `开始测速按钮在首屏内可以点到`() {
        val activity = setup()
        val button = view(activity, "btnStart")
        val location = IntArray(2)
        button.getLocationInWindow(location)
        val bottom = location[1] + button.height
        assertTrue(
            "开始测速按钮底部在 $bottom，已经掉出首屏（可用高度约 $SCREEN_HEIGHT）",
            bottom <= SCREEN_HEIGHT
        )
        activity.finish()
    }
}
