package com.rjy.xyz.apps.xyzinfo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.MainActivity
import com.rjy.xyz.apps.xyzinfo.ui.battery.BatteryInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.ram.RamInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.screen.ScreenInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.sensor.SensorInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.soc.SocInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.system.SystemInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.benchmark.BenchmarkActivity
import com.rjy.xyz.apps.xyzinfo.ui.benchmark.RankingActivity
import com.rjy.xyz.apps.xyzinfo.ui.settings.SettingsActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 界面渲染自检：把每个页面渲染成 PNG，放到 app/build/render/ 下。
 *
 * 用途：
 * - 改主题 / 布局后，不用真机就能确认浅色与深色两套配色是否正常；
 * - 表里不同机型的兼容性靠真机测，这里只保证布局不炸、配色可读。
 *
 * 运行：./gradlew testDebugUnitTest --tests "*LayoutRenderTest*"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class LayoutRenderTest {

    private val screens: List<Pair<String, Class<out AppCompatActivity>>> = listOf(
        "1-main" to MainActivity::class.java,
        "2-soc" to SocInfoActivity::class.java,
        "3-ram" to RamInfoActivity::class.java,
        "4-screen" to ScreenInfoActivity::class.java,
        "5-battery" to BatteryInfoActivity::class.java,
        "6-sensor" to SensorInfoActivity::class.java,
        "7-system" to SystemInfoActivity::class.java,
        "8-benchmark" to BenchmarkActivity::class.java,
        "9-ranking" to RankingActivity::class.java,
        "10-settings" to SettingsActivity::class.java
    )

    @Test
    fun renderLight() = renderAll("light")

    @Test
    @Config(qualifiers = "w411dp-h915dp-night-xxhdpi")
    fun renderDark() = renderAll("dark")

    private fun renderAll(mode: String) {
        val outputDir = File("build/render/$mode").apply { mkdirs() }
        screens.forEach { (name, activityClass) ->
            val activity = Robolectric.buildActivity(activityClass).setup().get()
            val bitmap = capture(activity.window.decorView)
            File(outputDir, "$name.png").outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            bitmap.recycle()
            activity.finish()
        }
        println("渲染输出目录: ${outputDir.absolutePath}")
    }

    private fun capture(root: View): Bitmap {
        val width = 1080
        val height = 2400
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        return bitmap
    }
}
