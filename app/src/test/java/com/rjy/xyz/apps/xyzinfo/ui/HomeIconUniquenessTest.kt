package com.rjy.xyz.apps.xyzinfo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import androidx.appcompat.content.res.AppCompatResources
import com.rjy.xyz.apps.xyzinfo.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 首页图标不能撞车（1.0.5 修）。
 *
 * 用户反馈「首页部分图标是重复的」「品牌左边那个小图标也重复了」——
 * 原因是几个入口直接复用了别的卡片的图标（硬件测试=屏幕、环境检测=系统、温度监控=传感器，
 * 首页信息行的 Android / 内核 / 品牌也复用了系统 / CPU / 通信的图标）。
 *
 * 这里把首页用到的图标逐个渲染成位图，两两比较：只要有两张长得一样就直接失败。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class HomeIconUniquenessTest {

    private val icons = mapOf(
        "CPU" to R.drawable.ic_module_cpu,
        "RAM" to R.drawable.ic_module_ram,
        "屏幕" to R.drawable.ic_module_screen,
        "电池" to R.drawable.ic_module_battery,
        "传感器" to R.drawable.ic_module_sensor,
        "通信" to R.drawable.ic_module_telephony,
        "系统" to R.drawable.ic_module_system,
        "GPS" to R.drawable.ic_module_gps,
        "硬件测试" to R.drawable.ic_module_hardware,
        "杂项工具" to R.drawable.ic_module_benchmark,
        "环境检测" to R.drawable.ic_module_env,
        "温度监控" to R.drawable.ic_module_thermal,
        "网络测速" to R.drawable.ic_module_network,
        "Android 版本" to R.drawable.ic_info_android,
        "内核" to R.drawable.ic_info_kernel,
        "品牌" to R.drawable.ic_info_brand
    )

    @Test
    fun `首页图标两两不重复`() {
        // 图标里用的是 ?attr/colorPrimary，必须套上应用主题才能解析出来
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_XyzInfo)
        val outputDir = File("build/render/icons").apply { mkdirs() }
        val masks = mutableMapOf<String, String>()

        icons.forEach { (label, resId) ->
            val drawable = AppCompatResources.getDrawable(context, resId)
            assertTrue("$label 的图标加载不了", drawable != null)
            val size = 96
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable!!.setBounds(0, 0, size, size)
            drawable.draw(canvas)

            val pixels = IntArray(size * size)
            bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
            // 用 alpha 形状当指纹：图标颜色可能随主题变，形状不会
            val mask = pixels.joinToString("") { if ((it ushr 24) > 40) "1" else "0" }
            assertTrue("$label 的图标是空白的", mask.contains("1"))
            File(outputDir, "$label.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            masks[label] = mask
            bitmap.recycle()
        }

        val labels = masks.keys.toList()
        for (i in labels.indices) {
            for (j in i + 1 until labels.size) {
                val a = labels[i]
                val b = labels[j]
                assertTrue("「$a」和「$b」的图标完全一样，首页不能撞图", masks[a] != masks[b])
            }
        }
    }
}
