package com.rjy.xyz.apps.xyzinfo.data

import android.content.Context
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 计分口径升级后，旧版跑分不能继续显示。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BenchmarkStorageTest {

    @Test
    fun `旧计分版本的成绩自动失效`() {
        val context = RuntimeEnvironment.getApplication()
        SettingsRepository.clearBenchmark(context)
        SettingsRepository.saveBenchmark(context, single = 1000, multi = 1000, gpu = 1000)
        assertNotNull(SettingsRepository.lastBenchmark(context))

        context.getSharedPreferences("xyzinfo_settings", Context.MODE_PRIVATE)
            .edit()
            .putInt("last_engine_version", 1)
            .commit()

        assertNull(SettingsRepository.lastBenchmark(context))
    }
}
