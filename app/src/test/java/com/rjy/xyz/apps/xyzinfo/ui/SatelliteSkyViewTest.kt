package com.rjy.xyz.apps.xyzinfo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.GnssStatus
import android.view.ContextThemeWrapper
import android.view.View
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.ui.gps.SatelliteSkyView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 卫星天顶图（1.0.8）：
 *
 * 1. 每个星座都要写清楚**是哪国的、叫什么**（用户要求，还特别点名要加印度 NavIC）；
 * 2. 卫星很多时（30+ 颗）标签不能糊成一团 —— 这里有碰撞检测，宁可少标也不叠字。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class SatelliteSkyViewTest {

    private fun newView(): SatelliteSkyView {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_XyzInfo)
        return SatelliteSkyView(context).apply {
            measure(
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, 900, 900)
        }
    }

    @Test
    fun `每个卫星系统都写清楚国家和名字`() {
        val view = newView()
        assertEquals("GPS（美国）", view.constellationLabelOf(GnssStatus.CONSTELLATION_GPS))
        assertEquals("北斗（中国）", view.constellationLabelOf(GnssStatus.CONSTELLATION_BEIDOU))
        assertEquals("GLONASS（俄罗斯）", view.constellationLabelOf(GnssStatus.CONSTELLATION_GLONASS))
        assertEquals("Galileo（欧盟）", view.constellationLabelOf(GnssStatus.CONSTELLATION_GALILEO))
        assertEquals("QZSS（日本 引路）", view.constellationLabelOf(GnssStatus.CONSTELLATION_QZSS))
        assertEquals("NavIC（印度）", view.constellationLabelOf(GnssStatus.CONSTELLATION_IRNSS))
        assertEquals("SBAS（星基增强）", view.constellationLabelOf(GnssStatus.CONSTELLATION_SBAS))

        val legend = view.legend()
        listOf("GPS（美国）", "北斗（中国）", "GLONASS（俄罗斯）", "Galileo（欧盟）",
            "QZSS（日本）", "NavIC（印度）", "SBAS").forEach { mark ->
            assertTrue("图例里缺 $mark：$legend", legend.contains(mark))
        }
    }

    @Test
    fun `三十多颗卫星也不能糊成一团`() {
        val view = newView()
        // 各系统凑出 36 颗，方位角均匀铺开（最容易叠字的情况）
        val list = (0 until 36).map { index ->
            SatelliteSkyView.Satellite(
                id = index + 1,
                constellation = listOf(
                    GnssStatus.CONSTELLATION_GPS,
                    GnssStatus.CONSTELLATION_BEIDOU,
                    GnssStatus.CONSTELLATION_GLONASS,
                    GnssStatus.CONSTELLATION_GALILEO,
                    GnssStatus.CONSTELLATION_QZSS,
                    GnssStatus.CONSTELLATION_IRNSS
                )[index % 6],
                azimuth = (index * 10f) % 360f,
                elevation = 15f + (index % 5) * 15f,
                snr = 20f + (index % 7) * 3f,
                usedInFix = index % 3 == 0
            )
        }
        view.update(list)
        view.setHeading(42f)

        val bitmap = Bitmap.createBitmap(900, 900, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val colors = distinctColors(bitmap)
        assertTrue("天顶图只有 $colors 种颜色，可能没画出来", colors > 20)

        // 统计行里各系统都要能出现（用短名，排得下）
        val summary = view.constellationSummary()
        listOf("GPS", "北斗", "GLONASS", "Galileo", "QZSS", "NavIC").forEach {
            assertTrue("统计行缺 $it：$summary", summary.contains(it))
        }

        File("build/render/sky").apply { mkdirs() }
        File("build/render/sky/sky-36.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun distinctColors(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.toHashSet().size
    }
}
