package com.rjy.xyz.apps.xyzinfo.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.model.CellSnapshot
import com.rjy.xyz.apps.xyzinfo.model.SimSlotInfo
import com.rjy.xyz.apps.xyzinfo.model.TelephonyInfo
import com.rjy.xyz.apps.xyzinfo.util.Labels
import com.rjy.xyz.apps.xyzinfo.util.SystemPropertiesReader

/**
 * 读取通信参数：基带版本、运营商与 SIM、网络类型、信号强度、小区信息。
 *
 * 受系统限制说明：
 * - 基带版本、运营商名称：无需权限；
 * - 数据网络类型、信号强度、小区信息：需要 READ_PHONE_STATE，
 *   小区信息在 Android 10+ 还需要位置权限（系统用位置来保护基站隐私）；
 * - 手机号：多数运营商不写入 SIM，且 Android 10+ 起普通应用基本读不到，只能显示“系统未提供”。
 */
object TelephonyInfoProvider {

    /** 与调制解调器相关的属性，存在哪个就展示哪个。 */
    private val RADIO_PROPERTY_KEYS = listOf(
        "gsm.version.baseband",
        "ro.baseband",
        "ro.boot.baseband",
        "ro.boot.radio",
        "ro.boot.hardware",
        "persist.radio.multisim.config"
    )

    fun load(context: Context): TelephonyInfo {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val properties = SystemPropertiesReader.readAll()
        val notes = mutableListOf<String>()

        val phoneStateGranted = hasPermission(context, Manifest.permission.READ_PHONE_STATE)
        val locationGranted = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

        val radioProperties = RADIO_PROPERTY_KEYS.mapNotNull { key ->
            properties[key]?.takeIf { it.isNotBlank() }?.let { key to it }
        }

        if (telephony == null) {
            return TelephonyInfo(
                basebandVersion = Build.getRadioVersion(),
                radioProperties = radioProperties,
                phoneCount = 0,
                activeModemCount = 0,
                simState = Labels.UNKNOWN,
                phoneType = Labels.UNKNOWN,
                callState = Labels.UNKNOWN,
                dataState = Labels.UNKNOWN,
                deviceId = null,
                subscriberId = null,
                simSerialNumber = null,
                voiceMailNumber = null,
                networkCountryIso = null,
                simCountryIso = null,
                slots = emptyList(),
                dataNetworkType = null,
                signalLevel = null,
                signalDbm = null,
                cells = emptyList(),
                phoneStateGranted = phoneStateGranted,
                locationGranted = locationGranted,
                notes = listOf("该设备没有电话服务（可能是平板 / Wi-Fi 版）")
            )
        }

        if (!phoneStateGranted) {
            notes += "未授予「电话状态」权限：SIM 卡列表、网络类型、信号强度、小区信息都不可读"
        }
        if (!locationGranted) {
            notes += "未授予「位置信息」权限：Android 10 起读取小区信息需要位置权限"
        }

        // 已激活的订阅（双卡会返回两张）
        val subscriptions = runCatching {
            val manager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            manager?.activeSubscriptionInfoList.orEmpty()
        }.getOrNull().orEmpty()

        if (subscriptions.isEmpty() && phoneStateGranted) {
            notes += "系统未返回订阅列表：可能只识别到一张卡，或副卡未激活"
        }

        val slots = subscriptions
            .sortedBy { it.simSlotIndex }
            .map { subscription -> readSlot(telephony, subscription, locationGranted, notes) }

        val defaultSignal = readSignalStrength(telephony)

        return TelephonyInfo(
            basebandVersion = Build.getRadioVersion()?.takeIf { it.isNotBlank() },
            radioProperties = radioProperties,
            phoneCount = phoneCount(telephony),
            activeModemCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { telephony.activeModemCount }.getOrDefault(0)
            } else {
                0
            },
            simState = mapSimState(telephony.simState),
            phoneType = mapPhoneType(runCatching { telephony.phoneType }.getOrDefault(-1)),
            callState = mapCallState(runCatching { telephony.callState }.getOrDefault(-1)),
            dataState = mapDataState(runCatching { telephony.dataState }.getOrDefault(-1)),
            deviceId = runCatching { telephony.deviceId }.getOrNull()?.takeIf { it.isNotBlank() },
            subscriberId = readSubscriberId(telephony),
            simSerialNumber = runCatching { telephony.simSerialNumber }.getOrNull()?.takeIf { it.isNotBlank() },
            voiceMailNumber = runCatching { telephony.voiceMailNumber }.getOrNull()?.takeIf { it.isNotBlank() },
            networkCountryIso = runCatching { telephony.networkCountryIso }.getOrNull(),
            simCountryIso = runCatching { telephony.simCountryIso }.getOrNull(),
            slots = slots,
            dataNetworkType = readDataNetworkType(telephony),
            signalLevel = defaultSignal.level,
            signalDbm = defaultSignal.dbm,
            cells = readCells(telephony, locationGranted, notes),
            phoneStateGranted = phoneStateGranted,
            locationGranted = locationGranted,
            notes = notes.distinct()
        )
    }

    /** 读取单张 SIM 卡（一个订阅）的详细信息。 */
    private fun readSlot(
        telephony: TelephonyManager,
        subscription: SubscriptionInfo,
        locationGranted: Boolean,
        notes: MutableList<String>
    ): SimSlotInfo {
        // 用订阅 ID 拿到「这张卡自己的」TelephonyManager，才能读到各自的信号与小区
        val perSim = runCatching { telephony.createForSubscriptionId(subscription.subscriptionId) }
            .getOrNull()

        val signal = perSim?.let { readSignalStrength(it) } ?: SignalReading(null, null)
        val number = subscription.number?.takeIf { it.isNotBlank() }
        if (number == null) {
            notes += "SIM 卡号：运营商一般不写入卡内，Android 10+ 起普通应用也读不到"
        }

        return SimSlotInfo(
            slotIndex = subscription.simSlotIndex + 1,
            subscriptionId = subscription.subscriptionId,
            carrierName = subscription.carrierName?.toString()?.takeIf { it.isNotBlank() },
            displayName = subscription.displayName?.toString()?.takeIf { it.isNotBlank() },
            number = number,
            countryIso = subscription.countryIso,
            mccMnc = formatMccMnc(subscription.mcc, subscription.mnc),
            roaming = perSim?.let { runCatching { it.isNetworkRoaming }.getOrNull() },
            isEmbedded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { subscription.isEmbedded }.getOrDefault(false)
            } else {
                false
            },
            networkType = perSim?.let { readDataNetworkType(it) },
            signalLevel = signal.level,
            signalDbm = signal.dbm,
            dataEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                perSim?.let { runCatching { it.isDataEnabled }.getOrNull() }
            } else {
                null
            },
            cells = perSim?.let { readCells(it, locationGranted, notes) }.orEmpty()
        )
    }

    private fun formatMccMnc(mcc: Int, mnc: Int): String? =
        if (mcc > 0 && mnc >= 0) "$mcc$mnc" else null

    private fun phoneCount(telephony: TelephonyManager): Int = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) telephony.activeModemCount else telephony.phoneCount
    }.getOrDefault(0)

    private fun readSubscriberId(telephony: TelephonyManager): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            telephony.subscriberId
        } else {
            @Suppress("DEPRECATION")
            telephony.subscriberId
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun readDataNetworkType(telephony: TelephonyManager): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkTypeName(telephony.dataNetworkType)
        } else {
            @Suppress("DEPRECATION")
            networkTypeName(telephony.networkType)
        }
    }.getOrNull()

    /** 主卡的信号强度（dBm + 0~4 等级）。 */
    private fun readSignalStrength(telephony: TelephonyManager): SignalReading = runCatching {
        // CellSignalStrength 按制式区分（cellSignalStrengths）从 Android 10 才有，
        // 更低版本上普通应用拿不到可靠数值，直接显示未公开
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return SignalReading(null, null)
        val strength = telephony.signalStrength ?: return SignalReading(null, null)
        val first = strength.cellSignalStrengths.firstOrNull()
        SignalReading(first?.level, first?.dbm?.takeIf { it != Int.MAX_VALUE })
    }.getOrDefault(SignalReading(null, null))

    /** 读取小区信息（需要位置权限，Android 10+ 未授权时系统会直接抛异常）。 */
    private fun readCells(
        telephony: TelephonyManager,
        locationGranted: Boolean,
        notes: MutableList<String>
    ): List<CellSnapshot> {
        if (!locationGranted) return emptyList()

        val result = runCatching { telephony.allCellInfo }.getOrNull()
        if (result == null) {
            notes += "小区信息读取失败：可能未开启定位开关，或系统未授予权限"
            return emptyList()
        }

        return result.mapNotNull { snapshot(it) }
            .sortedByDescending { it.registered }
            .take(MAX_CELLS)
    }

    private fun snapshot(info: CellInfo): CellSnapshot? = runCatching {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr -> {
                val identity = info.cellIdentity as? android.telephony.CellIdentityNr
                CellSnapshot(
                    type = "NR（5G）",
                    registered = info.isRegistered,
                    cellId = identity?.nci?.toString(),
                    physicalCellId = identity?.pci?.toString(),
                    areaCode = identity?.tac?.toString(),
                    channel = identity?.nrarfcn?.toString(),
                    signalDbm = info.cellSignalStrength?.dbm?.takeIf { it != Int.MAX_VALUE },
                    level = info.cellSignalStrength?.level
                )
            }

            info is CellInfoLte -> CellSnapshot(
                type = "LTE（4G）",
                registered = info.isRegistered,
                cellId = info.cellIdentity.ci.toString(),
                physicalCellId = info.cellIdentity.pci.toString(),
                areaCode = info.cellIdentity.tac.toString(),
                channel = info.cellIdentity.earfcn.toString(),
                signalDbm = info.cellSignalStrength.dbm.takeIf { it != Int.MAX_VALUE },
                level = info.cellSignalStrength.level
            )

            info is CellInfoWcdma -> CellSnapshot(
                type = "WCDMA（3G）",
                registered = info.isRegistered,
                cellId = info.cellIdentity.cid.toString(),
                physicalCellId = info.cellIdentity.psc.toString(),
                areaCode = info.cellIdentity.lac.toString(),
                channel = info.cellIdentity.uarfcn.toString(),
                signalDbm = info.cellSignalStrength.dbm.takeIf { it != Int.MAX_VALUE },
                level = info.cellSignalStrength.level
            )

            info is CellInfoGsm -> CellSnapshot(
                type = "GSM（2G）",
                registered = info.isRegistered,
                cellId = info.cellIdentity.cid.toString(),
                physicalCellId = null,
                areaCode = info.cellIdentity.lac.toString(),
                channel = info.cellIdentity.arfcn.toString(),
                signalDbm = info.cellSignalStrength.dbm.takeIf { it != Int.MAX_VALUE },
                level = info.cellSignalStrength.level
            )

            else -> null
        }
    }.getOrNull()

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun mapSimState(state: Int): String = when (state) {
        TelephonyManager.SIM_STATE_READY -> "已就绪"
        TelephonyManager.SIM_STATE_ABSENT -> "无 SIM 卡"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "需要 PIN 解锁"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "需要 PUK 解锁"
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "网络锁定"
        TelephonyManager.SIM_STATE_NOT_READY -> "未就绪"
        TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "卡 IO 错误"
        else -> Labels.UNKNOWN
    }

    private fun mapPhoneType(type: Int): String = when (type) {
        TelephonyManager.PHONE_TYPE_GSM -> "GSM"
        TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
        TelephonyManager.PHONE_TYPE_SIP -> "SIP"
        TelephonyManager.PHONE_TYPE_NONE -> "无"
        else -> Labels.UNKNOWN
    }

    private fun mapCallState(state: Int): String = when (state) {
        TelephonyManager.CALL_STATE_IDLE -> "空闲"
        TelephonyManager.CALL_STATE_RINGING -> "响铃中"
        TelephonyManager.CALL_STATE_OFFHOOK -> "通话中"
        else -> Labels.UNKNOWN
    }

    private fun mapDataState(state: Int): String = when (state) {
        TelephonyManager.DATA_CONNECTED -> "已连接"
        TelephonyManager.DATA_DISCONNECTED -> "已断开"
        TelephonyManager.DATA_CONNECTING -> "连接中"
        TelephonyManager.DATA_SUSPENDED -> "已挂起"
        else -> Labels.UNKNOWN
    }

    private fun networkTypeName(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_NR -> "NR（5G）"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE（4G）"
        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_UMTS -> "WCDMA / HSPA（3G）"
        TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_GSM -> "GSM（2G）"
        TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A -> "CDMA"
        TelephonyManager.NETWORK_TYPE_IWLAN -> "Wi-Fi 通话"
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> Labels.UNKNOWN
        else -> "类型 ID: $type"
    }

    private const val MAX_CELLS = 12

    private data class SignalReading(val level: Int?, val dbm: Int?)
}
