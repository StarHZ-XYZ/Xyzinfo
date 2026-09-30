package com.rjy.xyz.apps.xyzinfo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 把自适应图标的两层（背景 + 前景）渲染成一张 PNG，输出到 app/build/render/logo.png。
 *
 * 用途：改图标后不用装到手机上就能先看一眼，也方便做版本对比。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class LogoRenderTest {

    @Test
    fun renderLogo() {
        val context = RuntimeEnvironment.getApplication()
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        ContextCompat.getDrawable(context, R.drawable.ic_launcher_background)?.apply {
            setBounds(0, 0, size, size)
            draw(canvas)
        }

        // 自适应图标：108dp 前景里只有中间 72dp 会显示，这里按同样比例内缩，避免预览失真
        val inset = (size * 18f / 108f).toInt()
        ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)?.apply {
            setBounds(inset, inset, size - inset, size - inset)
            draw(canvas)
        }

        val output = File("build/render/logo.png").apply { parentFile?.mkdirs() }
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        println("logo 渲染完成: ${output.absolutePath}")
    }
}
