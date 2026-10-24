package com.rjy.xyz.apps.xyzinfo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashRecoveryTest {

    @Test
    fun `崩溃记录可写入诊断报告并清除`() {
        val context = RuntimeEnvironment.getApplication()
        CrashRecovery.clear(context)

        CrashRecovery.record(context, "worker", IllegalStateException("测试异常"))

        assertEquals(1, CrashRecovery.count(context))
        assertTrue(CrashRecovery.latestSummary(context).orEmpty().contains("IllegalStateException"))
        val report = DiagnosticCenter.report(context)
        assertTrue(report.contains("崩溃记录：1 条"))
        assertTrue(report.contains("XYZ Index：v2"))

        CrashRecovery.clear(context)
        assertEquals(0, CrashRecovery.count(context))
    }

    @Test
    fun `轻量模式覆盖动画设置但退出后恢复`() {
        val context = RuntimeEnvironment.getApplication()
        SettingsRepository.setAnimationsEnabled(context, true)
        SettingsRepository.setLightweightMode(context, true)
        assertEquals(false, SettingsRepository.animationsEnabled(context))

        SettingsRepository.setLightweightMode(context, false)
        assertEquals(true, SettingsRepository.animationsEnabled(context))
    }
}
