package com.rjy.xyz.apps.xyzinfo.ui.telephony

import android.Manifest
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.TelephonyInfoProvider
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityTelephonyInfoBinding
import com.rjy.xyz.apps.xyzinfo.databinding.ItemSimCardBinding
import com.rjy.xyz.apps.xyzinfo.model.CellSnapshot
import com.rjy.xyz.apps.xyzinfo.model.SimSlotInfo
import com.rjy.xyz.apps.xyzinfo.model.TelephonyInfo
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import com.rjy.xyz.apps.xyzinfo.ui.common.setRawBlock
import com.rjy.xyz.apps.xyzinfo.util.Labels
import com.rjy.xyz.apps.xyzinfo.ui.common.ThemeColors

/**
 * 通信参数页：基带、双卡信息、信号强度与小区信息。
 *
 * 双卡机型会按订阅（SubscriptionInfo）逐张卡读取运营商、信号与小区信息。
 */
class TelephonyInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTelephonyInfoBinding

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            render()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTelephonyInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_NONE)

        binding.btnGrantPermission.setOnClickListener { requestPermissions() }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) render()
    }

    private fun requestPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    private fun render() {
        val info = TelephonyInfoProvider.load(this)

        binding.tvBasebandVersion.setInfoRow("基带版本：${info.basebandVersion ?: Labels.NOT_PUBLIC}")
        binding.tvRadioProperties.setRawBlock(radioPropertiesText(info))

        binding.tvSlotSummary.setInfoRow(
            "卡槽：${info.phoneCount} 个 ｜ 已识别 SIM：${info.slots.size} 张 ｜ " +
                "SIM 状态：${info.simState} ｜ 电话类型：${info.phoneType}"
        )
        renderSimCards(info)

        binding.tvDeviceId.setInfoRow(
            "IMEI / MEID：${info.deviceId ?: "系统限制（Android 10+ 需特权应用）"}"
        )
        binding.tvSubscriberId.setInfoRow(
            "IMSI（订阅者 ID）：${info.subscriberId ?: "系统限制（Android 10+ 需特权应用）"}"
        )
        binding.tvSimSerial.setInfoRow(
            "SIM 序列号：${info.simSerialNumber ?: "系统限制（需特权应用）"}"
        )
        binding.tvVoiceMail.setInfoRow("语音信箱：${info.voiceMailNumber ?: Labels.NOT_PUBLIC}")
        binding.tvCallState.setInfoRow("通话状态：${info.callState}")
        binding.tvDataState.setInfoRow("数据连接状态：${info.dataState}")
        binding.tvCountryInfo.setInfoRow("国家代码：网络 ${info.networkCountryIso ?: "—"} ｜ SIM ${info.simCountryIso ?: "—"}")

        binding.tvNetworkType.setInfoRow("数据网络类型：${info.dataNetworkType ?: Labels.NOT_PUBLIC}")
        binding.tvSignalLevel.setInfoRow(
            "信号等级：${info.signalLevel?.let { "$it / 4" } ?: Labels.NOT_PUBLIC}"
        )
        binding.tvSignalDbm.setInfoRow(
            "信号强度：${info.signalDbm?.let { "$it dBm" } ?: Labels.NOT_PUBLIC}"
        )
        binding.tvCellList.setRawBlock(cellListText(info.cells, "默认卡"))
        binding.tvTelephonyNotes.setRawBlock(notesText(info))

        val needPermission = !info.phoneStateGranted || !info.locationGranted
        binding.btnGrantPermission.visibility = if (needPermission) View.VISIBLE else View.GONE
    }

    /** 每张 SIM 卡一张卡片：运营商、卡号、MCC/MNC、网络、信号与各自的小区。 */
    private fun renderSimCards(info: TelephonyInfo) {
        binding.layoutSimCards.removeAllViews()

        if (info.slots.isEmpty()) {
            val item = ItemSimCardBinding.inflate(layoutInflater, binding.layoutSimCards, false)
            item.tvSimCardTitle.text = "SIM 卡信息"
            item.tvSimCarrier.setInfoRow(
                "未读取到 SIM 订阅：${if (info.phoneStateGranted) "系统未返回，可能是副卡未激活" else "需要「电话状态」权限"}"
            )
            item.tvSimNumber.setInfoRow("卡号：${Labels.NOT_PUBLIC}")
            item.tvSimMccMnc.setInfoRow("MCC / MNC：${Labels.NOT_PUBLIC}")
            item.tvSimCountry.setInfoRow("国家 / 地区：${Labels.NOT_PUBLIC}")
            item.tvSimNetwork.setInfoRow("数据网络：${Labels.NOT_PUBLIC}")
            item.tvSimSignal.setInfoRow("信号：${Labels.NOT_PUBLIC}")
            item.tvSimRoaming.setInfoRow("漫游 / 数据开关：${Labels.NOT_PUBLIC}")
            item.tvSimCells.setRawBlock("小区信息：无法读取")
            binding.layoutSimCards.addView(item.root)
            return
        }

        val accentColor = ThemeColors.accent(this)
        info.slots.forEach { slot ->
            val item = ItemSimCardBinding.inflate(layoutInflater, binding.layoutSimCards, false)
            val cardType = if (slot.isEmbedded) "eSIM" else "实体卡"

            item.tvSimCardTitle.text = "SIM ${slot.slotIndex}（$cardType ｜ 订阅 ${slot.subscriptionId}）"
            item.tvSimCardTitle.setTextColor(accentColor)
            item.tvSimCarrier.setInfoRow("运营商：${slot.carrierName ?: slot.displayName ?: Labels.NOT_PUBLIC}")
            item.tvSimNumber.setInfoRow("卡号：${slot.number ?: "系统未提供"}")
            item.tvSimMccMnc.setInfoRow("MCC / MNC：${slot.mccMnc ?: Labels.NOT_PUBLIC}")
            item.tvSimCountry.setInfoRow("国家 / 地区：${slot.countryIso ?: Labels.NOT_PUBLIC}")
            item.tvSimNetwork.setInfoRow("数据网络：${slot.networkType ?: Labels.NOT_PUBLIC}")
            item.tvSimSignal.setInfoRow(
                "信号：${slot.signalDbm?.let { "$it dBm" } ?: Labels.NOT_PUBLIC}" +
                    (slot.signalLevel?.let { "（等级 $it / 4）" } ?: "")
            )
            item.tvSimRoaming.setInfoRow(
                "漫游：${slot.roaming?.let { if (it) "漫游中" else "未漫游" } ?: Labels.UNKNOWN}" +
                    " ｜ 数据开关：${slot.dataEnabled?.let { if (it) "已开启" else "已关闭" } ?: Labels.UNKNOWN}"
            )
            item.tvSimCells.setRawBlock(cellListText(slot.cells, "SIM ${slot.slotIndex}"))
            binding.layoutSimCards.addView(item.root)
        }
    }

    private fun radioPropertiesText(info: TelephonyInfo): String {
        if (info.radioProperties.isEmpty()) return "无线电属性：系统未公开"
        return "无线电属性：\n" + info.radioProperties.joinToString("\n") { (key, value) ->
            "$key = $value"
        }
    }

    private fun cellListText(cells: List<CellSnapshot>, label: String): String {
        if (cells.isEmpty()) {
            return "$label 小区信息：未读取到（需要位置权限，且系统定位开关开启）"
        }

        return "$label 小区信息：\n\n" + cells.joinToString("\n\n") { cell ->
            buildString {
                append(cell.type)
                append(if (cell.registered) "（已注册 / 服务小区）" else "（邻区）")
                append("\n小区 ID：").append(cell.cellId ?: Labels.UNKNOWN)
                append("\n物理小区：").append(cell.physicalCellId ?: "—")
                append("\nTAC / LAC：").append(cell.areaCode ?: "—")
                append("\n信道号：").append(cell.channel ?: "—")
                append("\n信号：").append(cell.signalDbm?.let { "$it dBm" } ?: Labels.UNKNOWN)
                append("（等级 ").append(cell.level ?: "—").append("）")
            }
        }
    }

    private fun notesText(info: TelephonyInfo): String =
        if (info.notes.isEmpty()) {
            "权限已授予，信息读取正常。\n" +
                "说明：双卡机型按订阅分别读取；小区信息受系统隐私保护，Android 10 起需要位置权限。"
        } else {
            info.notes.joinToString("\n")
        }
}
