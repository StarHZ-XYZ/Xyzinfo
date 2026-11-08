package com.rjy.xyz.apps.xyzinfo.data

import android.content.Context
import android.os.Build
import com.rjy.xyz.apps.xyzinfo.util.Formats
import com.rjy.xyz.apps.xyzinfo.util.Labels

/**
 * 采集本机全部可对比的检测项，扁平化成 [DeviceSnapshot]。
 *
 * 范式照搬 [com.rjy.xyz.apps.xyzinfo.ui.share.ShareCardRenderer.collect]：
 * 逐个 Provider 用 [runCatching] 包裹（单模块失败不影响整体），机型名走 [DeviceNameRepository]。
 * 不引入新字段、不改任何现有 Provider。
 */
object DeviceSnapshotCollector {

    fun collect(context: Context, appVersion: String): DeviceSnapshot {
        val overview = runCatching { DeviceOverviewProvider.load() }.getOrNull()
        val soc = runCatching { SocInfoProvider.load(context) }.getOrNull()
        val ram = runCatching { RamInfoProvider.load(context) }.getOrNull()
        val screen = runCatching { ScreenInfoProvider.load(context) }.getOrNull()
        val battery = runCatching { BatteryInfoProvider.load(context) }.getOrNull()
        val system = runCatching { SystemInfoProvider.load(context) }.getOrNull()
        val telephony = runCatching { TelephonyInfoProvider.load(context) }.getOrNull()
        val sensor = runCatching { SensorInfoProvider.load(context) }.getOrNull()

        val deviceName = DeviceNameRepository.lookup(context)
            ?: overview?.displayName?.takeIf { it.isNotBlank() }
            ?: Build.MODEL
        val brand = overview?.manufacturer?.takeIf { it.isNotBlank() }
            ?: overview?.brand ?: Build.MANUFACTURER

        val rows = buildList {
            // ---------- 身份 ----------
            add(CompareRow("身份", "机型名", deviceName))
            add(CompareRow("身份", "内部型号", overview?.rawModel ?: Build.MODEL))
            add(CompareRow("身份", "品牌", brand))
            add(CompareRow("身份", "系统 UI", overview?.romName ?: Labels.NOT_PUBLIC))
            add(
                CompareRow(
                    "身份", "Android",
                    "Android ${overview?.androidRelease ?: Build.VERSION.RELEASE}（API ${overview?.apiLevel ?: Build.VERSION.SDK_INT}）"
                )
            )
            add(CompareRow("身份", "内核", overview?.kernelRelease ?: Labels.NOT_PUBLIC))
            add(CompareRow("身份", "架构", overview?.abiLabel ?: System.getProperty("os.arch").orEmpty()))

            // ---------- 芯片 ----------
            add(CompareRow("芯片", "型号", soc?.displayName ?: Labels.UNKNOWN))
            add(CompareRow("芯片", "图形", soc?.gpuName ?: Labels.UNKNOWN))
            add(
                CompareRow(
                    "芯片", "核心数",
                    soc?.coreCount?.let { "$it 核" } ?: Labels.UNKNOWN,
                    numeric = soc?.coreCount?.toDouble(), betterWhenHigher = true
                )
            )
            add(
                CompareRow(
                    "芯片", "GPU 频率",
                    soc?.gpuMaxFreqMHz?.let { Formats.decimal(it / 1000.0, 2) + " GHz" } ?: Labels.NOT_PUBLIC,
                    numeric = soc?.gpuMaxFreqMHz?.toDouble(), betterWhenHigher = true
                )
            )
            add(CompareRow("芯片", "Vulkan", if (soc?.vulkanSupported == true) "支持" else "不支持"))

            // ---------- 内存 ----------
            val ramGb = ram?.nominalTotalGigabytes?.toDouble()
                ?: ram?.measuredTotalBytes?.let { it / 1_073_741_824.0 }
            add(
                CompareRow(
                    "内存", "容量",
                    ram?.nominalTotalGigabytes?.let { "$it GB" }
                        ?: Formats.bytesOrUnknown(ram?.measuredTotalBytes),
                    numeric = ramGb, betterWhenHigher = true
                )
            )
            add(CompareRow("内存", "类型", ram?.typeName ?: Labels.NOT_PUBLIC))
            add(
                CompareRow(
                    "内存", "当前频率",
                    ram?.currentFrequencyMHz?.let { Formats.decimal(it / 1000.0, 2) + " GHz" } ?: Labels.NOT_PUBLIC,
                    numeric = ram?.currentFrequencyMHz?.toDouble(), betterWhenHigher = true
                )
            )

            // ---------- 电池 ----------
            add(
                CompareRow(
                    "电池", "设计容量",
                    battery?.designCapacityMah?.let { "$it mAh" } ?: Labels.NOT_PUBLIC,
                    numeric = battery?.designCapacityMah?.toDouble(), betterWhenHigher = true
                )
            )
            add(CompareRow("电池", "健康", battery?.health?.name ?: Labels.UNKNOWN))
            add(CompareRow("电池", "技术", battery?.technology ?: Labels.UNKNOWN))

            // ---------- 屏幕 ----------
            add(CompareRow("屏幕", "分辨率", screen?.let { "${it.widthPx}×${it.heightPx}" } ?: Labels.UNKNOWN))
            add(
                CompareRow(
                    "屏幕", "刷新率",
                    screen?.let { Formats.hertz(it.currentRefreshRateHz) } ?: Labels.UNKNOWN,
                    numeric = screen?.currentRefreshRateHz?.toDouble(), betterWhenHigher = true
                )
            )
            add(
                CompareRow(
                    "屏幕", "触控采样率",
                    screen?.touchSampleRateHz?.let { "$it Hz" } ?: Labels.NOT_PUBLIC,
                    numeric = screen?.touchSampleRateHz?.toDouble(), betterWhenHigher = true
                )
            )
            add(
                CompareRow(
                    "屏幕", "HDR",
                    if (screen?.hdrSupportedTypes?.isNotEmpty() == true) screen.hdrSupportedTypes.joinToString(" / ")
                    else "不支持"
                )
            )

            // ---------- 系统 / 存储 ----------
            val storageGb = system?.dataPartitionTotalBytes?.let { it / 1_073_741_824.0 }
            add(
                CompareRow(
                    "系统", "存储",
                    system?.dataPartitionTotalBytes?.let { Formats.bytes(it) } ?: Labels.NOT_PUBLIC,
                    numeric = storageGb, betterWhenHigher = true
                )
            )
            add(CompareRow("系统", "Root", system?.rootStatus?.name ?: Labels.UNKNOWN))
            add(CompareRow("系统", "Treble", system?.trebleStatus?.name ?: Labels.UNKNOWN))

            // ---------- 通信 ----------
            add(CompareRow("通信", "基带", telephony?.basebandVersion ?: Labels.NOT_PUBLIC))
            add(
                CompareRow(
                    "通信", "卡槽数",
                    telephony?.phoneCount?.let { "$it 个" } ?: Labels.UNKNOWN,
                    numeric = telephony?.phoneCount?.toDouble(), betterWhenHigher = true
                )
            )
            add(CompareRow("通信", "数据网络", telephony?.dataNetworkType ?: Labels.NOT_PUBLIC))

            // ---------- 传感器 ----------
            add(
                CompareRow(
                    "传感器", "传感器数",
                    sensor?.sensors?.size?.let { "$it 个" } ?: "0",
                    numeric = sensor?.sensors?.size?.toDouble(), betterWhenHigher = true
                )
            )
            add(
                CompareRow(
                    "传感器", "热区数",
                    sensor?.thermalZones?.size?.let { "$it 个" } ?: "0",
                    numeric = sensor?.thermalZones?.size?.toDouble(), betterWhenHigher = true
                )
            )
            val hottest = sensor?.thermalZones?.mapNotNull { it.celsius }?.maxOrNull()
            add(CompareRow("传感器", "最高温", hottest?.let { Formats.temperature(it) } ?: Labels.NOT_PUBLIC))
        }

        return DeviceSnapshot(
            appVersion = appVersion,
            deviceName = deviceName,
            brand = brand,
            rows = rows
        )
    }
}
