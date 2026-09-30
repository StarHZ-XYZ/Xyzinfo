package com.rjy.xyz.apps.xyzinfo.ui.system

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.data.SystemInfoProvider
import com.rjy.xyz.apps.xyzinfo.data.RomInfoProvider
import com.rjy.xyz.apps.xyzinfo.databinding.ActivitySystemInfoBinding
import com.rjy.xyz.apps.xyzinfo.model.RomInfo
import com.rjy.xyz.apps.xyzinfo.model.SystemInfo
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.AndroidVersionIcon
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import com.rjy.xyz.apps.xyzinfo.ui.common.setRawBlock
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
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_NONE)

        render(SystemInfoProvider.load(this))
        renderRom(RomInfoProvider.load())
    }

    private fun renderRom(rom: RomInfo) = with(binding) {
        tvRomName.setInfoRow("系统类型：${rom.name}")
        tvRomVersion.setInfoRow("系统版本号：${rom.version ?: Labels.NOT_PUBLIC}")
        tvDisplayId.setInfoRow("Build 显示 ID：${rom.displayId ?: Labels.NOT_PUBLIC}")
        tvBuildType.setInfoRow("构建类型：${rom.buildType ?: Labels.NOT_PUBLIC}")
        tvRomSource.setInfoRow("识别依据：${rom.source ?: Labels.NOT_PUBLIC}")
    }

    private fun render(info: SystemInfo) = with(binding) {
        tvSystemTitle.text = "Android ${info.androidRelease}"
        tvSystemSubTitle.text = "系统版本、设备标识、存储与环境总览"

        tvAndroidVersion.setInfoRow("Android 版本：${info.androidRelease}")
        // 给每个安卓版本配上对应的"甜点 / 吉祥物"图标（2.2 ~ 17 全覆盖）
        tvAndroidVersion.setCompoundDrawablesWithIntrinsicBounds(
            AndroidVersionIcon.drawable(
                this@SystemInfoActivity,
                android.os.Build.VERSION.SDK_INT,
                (16 * resources.displayMetrics.density).toInt()
            ),
            null,
            null,
            null
        )
        tvAndroidVersion.compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()
        tvApiLevel.setInfoRow("API 等级：${info.apiLevel}")
        tvBuildId.setInfoRow("Build ID：${info.buildId}")
        tvSecurityPatch.setInfoRow("安全补丁：${info.securityPatch}")
        tvKernelVersion.setInfoRow("Linux 内核：${info.kernelVersionLine}")
        tvBootloader.setInfoRow("Bootloader：${info.bootloader}")

        tvBrand.setInfoRow("品牌：${info.brand}")
        tvManufacturer.setInfoRow("制造商：${info.manufacturer}")
        tvModel.setInfoRow("型号：${info.model}")
        tvDevice.setInfoRow("设备代号：${info.device}")
        tvProduct.setInfoRow("Product：${info.product}")
        tvBoard.setInfoRow("Board：${info.board}")
        tvHardware.setInfoRow("Hardware：${info.hardware}")
        tvFingerprint.setInfoRow("Fingerprint：${info.fingerprint}")

        tvAbi.setInfoRow("ABI 列表：${info.abiList}")
        tvLanguage.setInfoRow("系统语言：${info.languageTag}")
        tvTimezone.setInfoRow("时区：${info.timeZoneId}")
        tvCurrentTime.setInfoRow("当前时间：${currentTime(info.timestampMillis)}")

        tvRamSummary.setInfoRow("运行内存概览：${memorySummary(info)}")
        tvInternalStorage.setInfoRow(
            "系统分区存储：${partitionSummary(info.systemPartitionTotalBytes, info.systemPartitionAvailableBytes)}"
        )
        tvDataStorage.setInfoRow(
            "数据分区存储：${partitionSummary(info.dataPartitionTotalBytes, info.dataPartitionAvailableBytes)}"
        )
        tvRootStatus.setInfoRow("Root 状态：${Labels.rootStatus(info.rootStatus)}")
        tvTrebleStatus.setInfoRow("Project Treble：${Labels.trebleStatus(info.trebleStatus)}")

        tvRawSystemInfo.setRawBlock(info.rawPreview)
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
