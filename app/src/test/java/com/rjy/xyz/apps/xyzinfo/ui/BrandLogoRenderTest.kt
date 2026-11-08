package com.rjy.xyz.apps.xyzinfo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.rjy.xyz.apps.xyzinfo.data.BrandLogoCatalog
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 渲染首页荣耀字标的真实槽位，用于检查是否仍被拉成超宽扁条或被裁切。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class BrandLogoRenderTest {

    @Test
    fun renderHonorAtHomeSize() {
        val context = RuntimeEnvironment.getApplication()
        val brand = BrandLogoCatalog.BRANDS.first { it.displayName == "荣耀" }
        val drawable = BrandLogoCatalog.drawable(context, brand, 22 * 3)
        val padding = 24
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth + padding * 2,
            drawable.intrinsicHeight + padding * 2,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(247, 248, 250))
        drawable.setBounds(
            padding,
            padding,
            padding + drawable.intrinsicWidth,
            padding + drawable.intrinsicHeight
        )
        drawable.draw(canvas)
        val output = File("build/render/honor-brand.png").apply { parentFile?.mkdirs() }
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
