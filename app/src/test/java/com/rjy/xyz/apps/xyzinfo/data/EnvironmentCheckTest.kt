package com.rjy.xyz.apps.xyzinfo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 环境检测（1.0.7 扩充）：项目要齐、每项都得给证据、等级只能三档。
 *
 * 这里不判断"某台机器是不是被 root"（那取决于真机），只保证**检测项本身没问题**：
 * 老的 8 项 + 新加的 5 项都在，且不会因为某个系统接口读不到就让整份报告炸掉。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class EnvironmentCheckTest {

    private val expectedTitles = listOf(
        // 1.0.0 起就有的
        "root 二进制 / 管理器目录",
        "系统构建签名",
        "调试状态",
        "SELinux",
        "模拟器特征",
        "Xposed / LSPosed",
        "分身 / 双开类应用",
        "用户 CA 证书",
        // 1.0.7 新增
        "ROM 构建类型",
        "引导链 / 锁状态",
        "挂载痕迹",
        "风险类应用",
        "无障碍服务",
        "设备管理员",
        "代理 / VPN"
    )

    @Test
    fun `检测项齐全且每项都有证据`() {
        val report = EnvironmentCheck.run(RuntimeEnvironment.getApplication())
        val titles = report.items.map { it.title }
        expectedTitles.forEach { expected ->
            assertTrue("缺少检测项：$expected（现有：$titles）", titles.contains(expected))
        }
        report.items.forEach { item ->
            assertTrue("${item.title} 没写证据", item.evidence.isNotBlank())
            assertTrue("${item.title} 的证据太长（界面会撑破）", item.evidence.length <= 240)
        }
    }

    @Test
    fun `标题不重复_结论与计数自洽`() {
        val report = EnvironmentCheck.run(RuntimeEnvironment.getApplication())
        assertEquals("标题不该重复", report.items.size, report.items.map { it.title }.toSet().size)
        assertEquals(
            report.items.count { it.level == EnvironmentCheck.Level.RISK },
            report.riskCount
        )
        assertEquals(
            report.items.count { it.level == EnvironmentCheck.Level.NOTICE },
            report.noticeCount
        )
        assertTrue(report.conclusion.isNotBlank())
    }
}
