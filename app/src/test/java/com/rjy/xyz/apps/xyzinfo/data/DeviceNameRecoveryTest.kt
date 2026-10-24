package com.rjy.xyz.apps.xyzinfo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** 下载的机型库损坏时必须自动回载内置资源。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceNameRecoveryTest {

    @Test
    fun `损坏缓存不会让型号识别永久失效`() {
        val context = RuntimeEnvironment.getApplication()
        val dir = DeviceNameRepository.updatedDir(context).apply { mkdirs() }
        val broken = File(dir, "${DeviceNameRepository.DEVICE_FILE}.tsv.gz")
        broken.writeBytes(byteArrayOf(0x1F, 0x8B.toByte(), 0x00, 0x01, 0x02))
        try {
            DeviceNameRepository.reload(context)
            assertTrue(DeviceNameRepository.entryCount() > 10_000)
            assertEquals(
                "荣耀 200",
                DeviceNameRepository.lookup(context, "hneli", "ELI-AN00", "hneli")
            )
            assertTrue("坏缓存应被删除", !broken.exists())
        } finally {
            dir.deleteRecursively()
            DeviceNameRepository.reload(context)
        }
    }
}
