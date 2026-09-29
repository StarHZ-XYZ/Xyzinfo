package com.rjy.xyz.apps.xyzinfo.model

/** 单个小区的快照（来自 TelephonyManager.getAllCellInfo）。 */
data class CellSnapshot(
    /** NR / LTE / WCDMA / GSM … */
    val type: String,
    val registered: Boolean,
    val cellId: String?,
    val physicalCellId: String?,
    /** TAC（LTE/NR）或 LAC（2G/3G） */
    val areaCode: String?,
    /** ARFCN 信道号：nrarfcn / earfcn / uarfcn / arfcn */
    val channel: String?,
    val signalDbm: Int?,
    val level: Int?
)

/** 单张 SIM 卡（订阅）的快照。 */
data class SimSlotInfo(
    /** 卡槽序号，从 1 开始，便于界面上显示「SIM 1」。 */
    val slotIndex: Int,
    val subscriptionId: Int,
    val carrierName: String?,
    val displayName: String?,
    val number: String?,
    val countryIso: String?,
    val mccMnc: String?,
    val roaming: Boolean?,
    /** 是否 eSIM。 */
    val isEmbedded: Boolean,
    val networkType: String?,
    val signalLevel: Int?,
    val signalDbm: Int?,
    val dataEnabled: Boolean?,
    val cells: List<CellSnapshot>
)

/** 通信参数快照。 */
data class TelephonyInfo(
    val basebandVersion: String?,
    /** 与调制解调器相关的系统属性键值对，例如 ro.baseband。 */
    val radioProperties: List<Pair<String, String>>,
    val phoneCount: Int,
    val activeModemCount: Int,
    val simState: String,
    val phoneType: String,
    val callState: String,
    val dataState: String,
    /** IMEI / MEID，Android 10+ 普通应用通常读不到。 */
    val deviceId: String?,
    /** IMSI（订阅者 ID），Android 10+ 普通应用通常读不到。 */
    val subscriberId: String?,
    val simSerialNumber: String?,
    val voiceMailNumber: String?,
    val networkCountryIso: String?,
    val simCountryIso: String?,
    /** 每张已激活 SIM 卡的详细信息。 */
    val slots: List<SimSlotInfo>,
    val dataNetworkType: String?,
    val signalLevel: Int?,
    val signalDbm: Int?,
    /** 默认卡（订阅列表读不到时的兜底）的小区列表。 */
    val cells: List<CellSnapshot>,
    val phoneStateGranted: Boolean,
    val locationGranted: Boolean,
    /** 受限说明，例如“未授予权限”“系统不向普通应用提供”等。 */
    val notes: List<String>
)
