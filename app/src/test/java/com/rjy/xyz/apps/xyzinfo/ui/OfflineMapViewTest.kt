package com.rjy.xyz.apps.xyzinfo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.View
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.ui.gps.OfflineMapView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 定位地图（1.0.7 重写）：**定位之后不能是一片空白**。
 *
 * 老版本是拿 assets 里的世界地图放大，放到 4 倍以上视野里只剩一片同色模糊区域，
 * 用户看到的就是"定位之后显示空白"。现在改成以本机为中心的经纬网格 + 距离环 + 比例尺，
 * 这里直接把这个视图画出来，检查画面里确实有东西：
 *
 * 1. 颜色数量够多（底图 + 网格 + 距离环 + 标记）；
 * 2. 网格线上应该有明显比背景亮的像素（缩放到最大时也一样）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class OfflineMapViewTest {

    private val width = 1080
    private val height = 820

    private fun renderMap(zoomSteps: Int): Bitmap {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_XyzInfo)
        val view = OfflineMapView(context)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
        // 天安门附近，精度 25 米
        view.updatePosition(39.9042, 116.4074, 25f, recenter = true)
        repeat(zoomSteps) { view.zoomIn() }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    @Test
    fun `定位后地图有内容_放大后也不会变空白`() {
        val outputDir = File("build/render/map").apply { mkdirs() }
        listOf(0, 1, 3, 5).forEach { steps ->
            val bitmap = renderMap(steps)
            val colors = distinctColors(bitmap)
            // 纯色空白的画面颜色数会是个位数；有底图 / 网格 / 环 / 标记会明显更多
            assertTrue("放大 $steps 档后画面只有 $colors 种颜色，基本是空白", colors > 12)

            // 画面里最亮的那些像素应该来自网格线 / 标记，而不是整片同色
            val bright = brightestRatio(bitmap)
            assertTrue("放大 $steps 档后几乎没有亮像素（$bright）", bright > 0.0005)

            File(outputDir, "map-zoom-$steps.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    private fun distinctColors(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.toHashSet().size
    }

    /** 明显亮于背景（网格线 / 标记 / 比例尺）的像素占比。 */
    private fun brightestRatio(bitmap: Bitmap): Double {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val bright = pixels.count { color ->
            val luminance = 0.299 * ((color shr 16) and 0xFF) +
                0.587 * ((color shr 8) and 0xFF) +
                0.114 * (color and 0xFF)
            luminance > 120
        }
        return bright.toDouble() / pixels.size
    }
}
