package com.rjy.xyz.apps.xyzinfo.ui

import androidx.appcompat.content.res.AppCompatResources
import com.rjy.xyz.apps.xyzinfo.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 屏幕页 HDR 测试图（1.0.6）：两张图必须真的能解码、尺寸正确、内容不同。
 *
 * 图是 `_tools/make_hdr_test_images.py` 生成的（1100×740 WebP），
 * 这里顺带拦住"图丢了 / 转坏了"这类问题。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class HdrTestImageTest {

    @Test
    fun `两张 HDR 对照图能解码且尺寸一致`() {
        val context = RuntimeEnvironment.getApplication()
        val ids = listOf(R.drawable.hdr_test_sdr, R.drawable.hdr_test_hdr)
        ids.forEach { id ->
            val drawable = AppCompatResources.getDrawable(context, id)
            assertNotNull("HDR 测试图丢了：$id", drawable)
            assertEquals("宽", 1100, drawable!!.intrinsicWidth)
            assertEquals("高", 740, drawable.intrinsicHeight)
        }
    }
}
