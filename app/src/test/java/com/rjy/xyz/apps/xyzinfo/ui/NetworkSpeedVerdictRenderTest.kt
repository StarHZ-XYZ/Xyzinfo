package com.rjy.xyz.apps.xyzinfo.ui

import android.widget.TextView
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import com.rjy.xyz.apps.xyzinfo.ui.network.NetworkSpeedActivity
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 网络评价卡片的渲染测试。
 *
 * 有上一次成绩时，进页面就应该直接把评价还原出来（而不是留一张空卡）。
 * 这里预置一份「下行快、上行慢」的成绩，验证：
 * - 总评等级不是占位符；
 * - 下行、上行两段各自有内容，且上行段落里带「上行」字样；
 * - 延迟说明也在。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class NetworkSpeedVerdictRenderTest {

    @Test
    fun `有上次成绩时评价卡会被填充`() {
        val context = RuntimeEnvironment.getApplication()
        // 下行 120Mbps、上行 3Mbps、延迟 28ms、抖动 6ms
        SettingsRepository.saveSpeedResult(
            context = context,
            downloadBytesPerSecond = 120e6 / 8,
            uploadBytesPerSecond = 3e6 / 8,
            pingMs = 28.0,
            jitterMs = 6.0,
            serverName = "测试节点"
        )

        val activity = Robolectric.buildActivity(NetworkSpeedActivity::class.java).setup().get()
        fun text(id: Int) = activity.findViewById<TextView>(id).text.toString()

        val grade = text(R.id.tvVerdictGrade)
        assertNotEquals("等级不该还是占位符", "—", grade)
        assertTrue("总评应有点评：${text(R.id.tvVerdictHeadline)}", text(R.id.tvVerdictHeadline).isNotBlank())

        val download = text(R.id.tvVerdictDownload)
        assertTrue("下行段落应写明方向与等级：$download", download.contains("下行") && download.contains("｜"))
        val upload = text(R.id.tvVerdictUpload)
        assertTrue("上行段落应写明方向与等级：$upload", upload.contains("上行"))
        assertTrue("下行说明应有内容", text(R.id.tvVerdictDownloadDetail).contains("够用"))
        assertTrue("上行说明应有内容", text(R.id.tvVerdictUploadDetail).contains("吃力"))
        assertTrue("延迟说明应有点评", text(R.id.tvVerdictLatency).contains("延迟"))
        activity.finish()
    }
}
