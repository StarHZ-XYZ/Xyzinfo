package com.rjy.xyz.apps.xyzinfo.ui

import android.graphics.Bitmap
import android.graphics.Color
import com.rjy.xyz.apps.xyzinfo.ui.share.ShareCardRenderer
import com.rjy.xyz.apps.xyzinfo.ui.share.ShareTheme
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
 * 分享卡片渲染回归（1.0.5）。
 *
 * 三个配色都要真的画出来：尺寸对、不是纯色、文字和卡片块都落在该在的位置。
 * 顺便把 PNG 写到 `build/render/share-card-*.png`，改版式时可以直接肉眼看。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class ShareCardRendererTest {

    private val content = ShareCardRenderer.Content(
        deviceName = "小米 Civi 1S",
        modelLine = "型号 2109119BC ｜ 代号 zijin",
        brandLabel = "Xiaomi",
        logo = null,
        specs = listOf(
            ShareCardRenderer.Spec("处理器", "骁龙 778G 5G"),
            ShareCardRenderer.Spec("图形", "Adreno 642L"),
            ShareCardRenderer.Spec("内存", "7.5 GB · LPDDR4X"),
            ShareCardRenderer.Spec("屏幕", "1080×2400 · 120Hz"),
            ShareCardRenderer.Spec("电池", "4500 mAh"),
            ShareCardRenderer.Spec("系统", "Android 14（API 34）"),
            ShareCardRenderer.Spec("架构", "arm64-v8a"),
            ShareCardRenderer.Spec("系统 UI", "澎湃OS（HyperOS） OS1.0"),
            ShareCardRenderer.Spec("设备代号", "zijin"),
            ShareCardRenderer.Spec("内核", "5.4.274-qgki-g07a0d7446d58")
        ),
        footerLeft = "XYZ-Devinfo · 星幻终（RJYZ）",
        footerRight = "v1.0.5 · 2026-10-01"
    )

    @Test
    fun everyThemeRendersARealCard() {
        val context = RuntimeEnvironment.getApplication()
        val outputDir = File("build/render").apply { mkdirs() }
        ShareTheme.values().forEach { theme ->
            val bitmap = ShareCardRenderer.render(context, content, theme, 0xFF3AA6A0.toInt())
            assertEquals("卡片宽度", ShareCardRenderer.CARD_WIDTH, bitmap.width)
            assertEquals("卡片高度", ShareCardRenderer.CARD_HEIGHT, bitmap.height)

            val colors = distinctColors(bitmap)
            assertTrue("$theme 的卡片只画出 ${colors} 种颜色，基本是空白的", colors > 40)

            // 标题那一带（机型名）必须有明显区别于底色的像素 —— 说明字真的画上去了
            assertTrue("$theme 的卡片标题区没有内容", titleBandHasInk(bitmap, theme))

            File(outputDir, "share-card-${theme.id}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    @Test
    fun lightAndDarkThemesLookDifferent() {
        val context = RuntimeEnvironment.getApplication()
        val dark = ShareCardRenderer.render(context, content, ShareTheme.DEPTH)
        val light = ShareCardRenderer.render(context, content, ShareTheme.CLOUD)
        val darkTopLeft = dark.getPixel(20, 20)
        val lightTopLeft = light.getPixel(20, 20)
        assertTrue("深空主题应该是深色", luminance(darkTopLeft) < 0.25)
        assertTrue("云白主题应该是浅色", luminance(lightTopLeft) > 0.8)
        dark.recycle()
        light.recycle()
    }

    private fun distinctColors(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.toHashSet().size
    }

    /** 标题区（机型名那一行）里有没有明显的"墨迹"。 */
    private fun titleBandHasInk(bitmap: Bitmap, theme: ShareTheme): Boolean {
        val dark = theme != ShareTheme.CLOUD
        val y = 200
        var ink = 0
        for (x in 300 until bitmap.width - 72) {
            val color = bitmap.getPixel(x, y)
            val lum = luminance(color)
            if (dark && lum > 0.6) ink++
            if (!dark && lum < 0.35) ink++
        }
        return ink > 60
    }

    private fun luminance(color: Int): Double =
        (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
}
