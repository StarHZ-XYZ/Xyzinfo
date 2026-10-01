package com.rjy.xyz.apps.xyzinfo.data

import android.os.Build
import com.rjy.xyz.apps.xyzinfo.model.DeviceOverview
import com.rjy.xyz.apps.xyzinfo.util.DeviceFacts

/**
 * 首页设备概要。
 */
object DeviceOverviewProvider {

    /**
     * 缓存：这些值在一次进程生命周期内不会变，而读 /proc、/sys、包管理等加起来要 80ms 以上。
     * 首页（以及开屏预热）每次都要，缓存下来能显著减少启动与切页开销。
     */
    @Volatile
    private var cached: DeviceOverview? = null

    fun load(): DeviceOverview = cached ?: synchronized(this) {
        cached ?: build().also { cached = it }
    }

    /** 需要重新读取时调用。 */
    fun invalidate() {
        cached = null
    }

    private fun build(): DeviceOverview = DeviceOverview(
        displayName = DeviceFacts.friendlyDeviceName(),
        rawModel = DeviceFacts.orUnknown(Build.MODEL),
        androidRelease = DeviceFacts.orUnknown(Build.VERSION.RELEASE),
        apiLevel = Build.VERSION.SDK_INT,
        romName = romLabel(),
        kernelRelease = DeviceFacts.kernelRelease(),
        brand = DeviceFacts.orUnknown(Build.BRAND),
        manufacturer = DeviceFacts.orUnknown(Build.MANUFACTURER),
        deviceCode = DeviceFacts.orUnknown(Build.DEVICE),
        product = DeviceFacts.orUnknown(Build.PRODUCT),
        abiLabel = DeviceFacts.abiLabel(DeviceFacts.primaryAbi()),
        cpuArchitecture = DeviceFacts.cpuArchitecture()
    )

    /** 首页显示的系统 UI 名称，例如「澎湃OS（HyperOS） V816」。 */
    private fun romLabel(): String? {
        val rom = RomInfoProvider.load()
        return listOfNotNull(rom.name, rom.version)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
    }
}
