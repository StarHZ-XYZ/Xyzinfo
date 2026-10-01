package com.rjy.xyz.apps.xyzinfo.data.speed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 网络评价：上下行分开评、等级边界、用途判断、缺项兜底。 */
class SpeedVerdictTest {

    private fun evaluate(
        downloadMbps: Double,
        uploadMbps: Double?,
        pingMs: Double? = 30.0,
        jitterMs: Double? = 8.0,
        uploadReason: String? = null
    ) = SpeedVerdict.evaluate(
        downloadBytesPerSecond = downloadMbps * 1e6 / 8,
        uploadBytesPerSecond = uploadMbps?.let { it * 1e6 / 8 },
        pingMs = pingMs,
        jitterMs = jitterMs,
        uploadUnavailableReason = uploadReason
    )

    @Test
    fun `千兆对称宽带_上下行都优秀`() {
        val v = evaluate(900.0, 900.0, 6.0, 2.0)
        assertEquals("优秀", v.download.grade)
        assertEquals("优秀", v.upload?.grade)
        assertTrue(v.score >= 85)
        assertTrue(v.download.capabilities.contains("4K 视频"))
        assertTrue(v.tierNote!!.contains("千兆"))
    }

    @Test
    fun `下行快上行慢_评价要点出上行拖后腿`() {
        // 典型家宽：下行 500M、上行 3M（上传大文件要等很久）
        val v = evaluate(500.0, 3.0, 25.0, 6.0)
        assertEquals("优秀", v.download.grade)
        assertTrue("上行不该被评为优秀：${v.upload!!.grade}", v.upload!!.score < v.download.score)
        assertTrue("应点出上行拖后腿：${v.headline}", v.headline.contains("上行"))
        assertTrue(v.upload!!.limits.contains("上传大文件 / 云盘备份"))
        // 下行仍然要给足"够用"的判断
        assertTrue(v.download.capabilities.contains("4K 视频"))
    }

    @Test
    fun `下行弱上行强_提示可能是限速或干扰`() {
        val v = evaluate(4.0, 200.0, 40.0, 10.0)
        assertEquals("较差", v.download.grade)
        assertEquals("优秀", v.upload?.grade)
        assertTrue("应点出上行反而更好：${v.headline}", v.headline.contains("反而比"))
    }

    @Test
    fun `没测到上行_给出原因而不是空白`() {
        val v = evaluate(120.0, null, 30.0, 5.0, uploadReason = "该节点只提供下载测速")
        assertNull(v.upload)
        assertNotNull(v.uploadUnavailableReason)
        assertTrue(v.uploadUnavailableReason!!.contains("只提供下载"))
    }

    @Test
    fun `延迟评价分档`() {
        assertTrue(evaluate(100.0, 20.0, 8.0, 3.0).latencyNote.contains("延迟很低"))
        assertTrue(evaluate(100.0, 20.0, 300.0, 60.0).latencyNote.contains("延迟很高"))
        assertTrue(evaluate(100.0, 20.0, 300.0, 60.0).latencyNote.contains("抖动很大"))
    }

    @Test
    fun `分数随下行速度单调不降`() {
        val slow = evaluate(5.0, 5.0).score
        val mid = evaluate(50.0, 5.0).score
        val fast = evaluate(500.0, 5.0).score
        assertTrue(slow <= mid && mid <= fast)
    }

    @Test
    fun `上行阈值符合实际用途`() {
        // 2Mbps 才够视频通话
        assertTrue(evaluate(100.0, 1.0).upload!!.limits.contains("视频通话（对方会看到你卡）"))
        assertTrue(evaluate(100.0, 3.0).upload!!.capabilities.contains("视频通话"))
        // 10Mbps 才谈得上 1080p 推流
        assertTrue(evaluate(100.0, 11.0).upload!!.capabilities.contains("1080p 直播推流"))
        assertTrue(evaluate(100.0, 8.0).upload!!.limits.contains("高清直播推流"))
    }
}
