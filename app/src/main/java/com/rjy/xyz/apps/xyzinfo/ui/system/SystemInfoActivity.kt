package com.rjy.xyz.apps.xyzinfo.ui.system

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.data.SystemInfoProvider
import com.rjy.xyz.apps.xyzinfo.databinding.ActivitySystemInfoBinding
import com.rjy.xyz.apps.xyzinfo.model.SystemInfo
import com.rjy.xyz.apps.xyzinfo.util.Formats
import com.rjy.xyz.apps.xyzinfo.util.Labels
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 系统信息详情页。
 */
class SystemInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySystemInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySystemInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        render(SystemInfoProvider.load(this))
    }

    private fun render(info: SystemInfo) = with(binding) {
        tvSystemTitle.text = "Android ${info.androidRelease}"
        tvSystemSubTitle.text = "系统版本、设备标识、存储与环境总览"

        tvAndroidVersion.text = "Android 版本：${info.androidRelease}"
        tvApiLevel.text = "API 等级：${info.apiLevel}"
        tvBuildId.text = "Build ID：${info.buildId}"
        tvSecurityPatch.text = "安全补丁：${info.securityPatch}"
        tvKernelVersion.text = "Linux 内核：${info.kernelVersionLine}"
        tvBootloader.text = "Bootloader：${info.bootloader}"

        tvBrand.text = "品牌：${info.brand}"
        tvManufacturer.text = "制造商：${info.manufacturer}"
        tvModel.text = "型号：${info.model}"
        tvDevice.text = "设备代号：${info.device}"
        tvProduct.text = "Product：${info.product}"
        tvBoard.text = "Board：${info.board}"
        tvHardware.text = "Hardware：${info.hardware}"
        tvFingerprint.text = "Fingerprint：${info.fingerprint}"

        tvAbi.text = "ABI 列表：${info.abiList}"
        tvLanguage.text = "系统语言：${info.languageTag}"
        tvTimezone.text = "时区：${info.timeZoneId}"
        tvCurrentTime.text = "当前时间：${currentTime(info.timestampMillis)}"

        tvRamSummary.text = "运行内存概览：${memorySummary(info)}"
        tvInternalStorage.text = "系统分区存储：${partitionSummary(info.systemPartitionTotalBytes, info.systemPartitionAvailableBytes)}"
        tvDataStorage.text = "数据分区存储：${partitionSummary(info.dataPartitionTotalBytes, info.dataPartitionAvailableBytes)}"
        tvRootStatus.text = "Root 状态：${Labels.rootStatus(info.rootStatus)}"
        tvTrebleStatus.text = "Project Treble：${Labels.trebleStatus(info.trebleStatus)}"

        tvRawSystemInfo.text = info.rawPreview
    }

    private fun currentTime(timestampMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))

    private fun memorySummary(info: SystemInfo): String {
        val total = info.ramTotalBytes ?: return Labels.NOT_PUBLIC
        val available = info.ramAvailableBytes ?: return Labels.NOT_PUBLIC
        return "总计 ${Formats.bytes(total)}，可用 ${Formats.bytes(available)}"
    }

    private fun partitionSummary(total: Long?, available: Long?): String {
        if (total == null || available == null) return Labels.NOT_PUBLIC
        val used = (total - available).coerceAtLeast(0L)
        return "总计 ${Formats.bytes(total)}，已用 ${Formats.bytes(used)}，可用 ${Formats.bytes(available)}"
    }
}
