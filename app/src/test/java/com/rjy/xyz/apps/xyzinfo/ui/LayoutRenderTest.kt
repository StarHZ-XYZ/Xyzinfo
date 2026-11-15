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
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.hardware.ScreenTestActivity
import com.rjy.xyz.apps.xyzinfo.ui.hardware.HardwareMoreActivity
import com.rjy.xyz.apps.xyzinfo.ui.misc.MiscActivity
import com.rjy.xyz.apps.xyzinfo.ui.about.ChangelogActivity
import com.rjy.xyz.apps.xyzinfo.ui.gps.GpsInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.env.EnvironmentCheckActivity
import com.rjy.xyz.apps.xyzinfo.ui.thermal.ThermalActivity
import com.rjy.xyz.apps.xyzinfo.ui.inspect.DeviceInspectActivity
import com.rjy.xyz.apps.xyzinfo.ui.network.NetworkSpeedActivity
import com.rjy.xyz.apps.xyzinfo.ui.share.ShareCardActivity
import com.rjy.xyz.apps.xyzinfo.ui.compare.DeviceCompareActivity
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
        "2-soc" to SocInfoActivity::class.java,
        "3-ram" to RamInfoActivity::class.java,
        "4-screen" to ScreenInfoActivity::class.java,
        "5-battery" to BatteryInfoActivity::class.java,
        "6-sensor" to SensorInfoActivity::class.java,
        "7-system" to SystemInfoActivity::class.java,
        // 0.9 新增页面：全部纳入渲染自检，用来抓「布局里用了不支持的属性」这类
        // 只有真正 inflate 才会暴露的崩溃（TextureView 加 background 就是这么发现的）
        "11-gps" to GpsInfoActivity::class.java,
        "12-screen-test" to ScreenTestActivity::class.java,
        "13-hardware-more" to HardwareMoreActivity::class.java,
        "14-misc" to MiscActivity::class.java,
        "15-changelog" to ChangelogActivity::class.java,
        "16-env-check" to EnvironmentCheckActivity::class.java,
        "17-inspect" to DeviceInspectActivity::class.java,
        "19-thermal" to ThermalActivity::class.java,
        "20-network-speed" to NetworkSpeedActivity::class.java,
        "21-share-card" to ShareCardActivity::class.java,
        "22-device-compare" to DeviceCompareActivity::class.java
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
        val shell = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        listOf(
            "1-main" to GlassScaffold.TAB_HOME,
            "8-benchmark" to GlassScaffold.TAB_BENCHMARK,
            "9-ranking" to GlassScaffold.TAB_RANKING,
            "10-settings" to GlassScaffold.TAB_SETTINGS,
            "18-fish-ai" to GlassScaffold.TAB_FISH
        ).forEach { (name, tab) ->
            if (tab != GlassScaffold.TAB_HOME) shell.switchTab(tab)
            val bitmap = capture(shell.window.decorView)
            File(outputDir, "$name.png").outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            bitmap.recycle()
        }
        shell.finish()
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
