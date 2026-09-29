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
        kernelRelease = DeviceFacts.kernelRelease(),
        brand = DeviceFacts.orUnknown(Build.BRAND),
        manufacturer = DeviceFacts.orUnknown(Build.MANUFACTURER),
        deviceCode = DeviceFacts.orUnknown(Build.DEVICE),
        product = DeviceFacts.orUnknown(Build.PRODUCT),
        abiLabel = DeviceFacts.abiLabel(DeviceFacts.primaryAbi()),
        cpuArchitecture = DeviceFacts.cpuArchitecture()
    )
}
