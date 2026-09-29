package com.rjy.xyz.apps.xyzinfo.data

import android.os.Build
import com.rjy.xyz.apps.xyzinfo.model.DeviceOverview
import com.rjy.xyz.apps.xyzinfo.util.DeviceFacts

/**
 * 首页设备概要。
 */
object DeviceOverviewProvider {

    fun load(): DeviceOverview = DeviceOverview(
        displayName = DeviceFacts.friendlyDeviceName(),
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
