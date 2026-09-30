package com.rjy.xyz.apps.xyzinfo.ui.ram

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.data.RamInfoProvider
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityRamInfoBinding
import com.rjy.xyz.apps.xyzinfo.model.RamInfo
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import com.rjy.xyz.apps.xyzinfo.ui.common.setRawBlock
import com.rjy.xyz.apps.xyzinfo.util.Formats
import com.rjy.xyz.apps.xyzinfo.util.Labels

/**
 * 运行内存详情页。
 */
class RamInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRamInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRamInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_NONE)

        render(RamInfoProvider.load(this))
    }

    private fun render(info: RamInfo) = with(binding) {
        tvRamTitle.text = capacityTitle(info)
        tvRamSubTitle.text = "标称容量、内存类型与 DDR 频率总览"

        tvRamCapacityNominal.setInfoRow("标称容量：${gigaBytes(info.nominalTotalGigabytes)}")
        tvRamCapacityMeasured.setInfoRow("实测可用总量：${Formats.bytes(info.measuredTotalBytes)}")
        tvTotalRam.setInfoRow("内核预留：${reservedBytes(info)}")
        tvUsedRam.setInfoRow("当前已用：${Formats.bytes(info.usedBytes)}")
        tvAvailRam.setInfoRow("当前可用：${Formats.bytes(info.availableBytes)}")
        tvUsagePercent.setInfoRow("内存占用率：${info.usagePercent}%")
        tvLowMemory.setInfoRow("系统低内存状态：${if (info.lowMemory) "是" else "否"}")
        tvThreshold.setInfoRow("低内存阈值：${Formats.bytes(info.thresholdBytes)}")

        tvRamType.setInfoRow("RAM 类型：${info.typeName}")
        tvRamBrand.setInfoRow("RAM 品牌：${info.brandName ?: Labels.NOT_PUBLIC}")
        tvRamTypeInferred.setInfoRow(
            "内存世代（按芯片推断）：${info.inferredMemoryType ?: Labels.NOT_PUBLIC}"
        )
        tvFreqCurrent.setInfoRow("当前频率：${megaHertz(info.currentFrequencyMHz)}")
        tvFreqMax.setInfoRow("最高频率：${megaHertz(info.maxFrequencyMHz)}")
        tvFreqMin.setInfoRow("最低频率：${megaHertz(info.minFrequencyMHz)}")
        tvFreqSource.setInfoRow(frequencyDetail(info))

        tvJavaHeapMax.setInfoRow("Java 堆上限：${Formats.bytes(info.javaHeapMaxBytes)}")
        tvNativeHeapSize.setInfoRow("Native Heap 总大小：${Formats.bytes(info.nativeHeapTotalBytes)}")
        tvNativeHeapAllocated.setInfoRow(
            "Native Heap 已分配：${Formats.bytes(info.nativeHeapAllocatedBytes)}"
        )

        tvSwapTotal.setInfoRow("Swap / ZRAM 总量：${Formats.bytesOrUnknown(info.swapTotalBytes)}")
        tvSwapUsed.setInfoRow("Swap / ZRAM 已用：${Formats.bytesOrUnknown(info.swapUsedBytes)}")

        tvMemInfoRaw.setRawBlock(info.memInfoPreview)
    }

    /** 标题优先显示标称容量（例如 12GB RAM），推断不出来时退回实测值。 */
    private fun capacityTitle(info: RamInfo): String =
        info.nominalTotalGigabytes?.let { "${it}GB RAM" }
            ?: "${Formats.bytes(info.measuredTotalBytes)} RAM"

    private fun gigaBytes(nominalGigabytes: Int?): String =
        nominalGigabytes?.let { "$it GB" } ?: Labels.UNKNOWN

    /** 标称容量与实测可用量之差，即内核等预留的部分。 */
    private fun reservedBytes(info: RamInfo): String {
        val nominal = info.nominalTotalGigabytes ?: return Labels.NOT_PUBLIC
        val nominalBytes = nominal * 1024L * 1024L * 1024L
        val reserved = nominalBytes - info.measuredTotalBytes
        return if (reserved > 0) Formats.bytes(reserved) else Labels.NOT_PUBLIC
    }

    private fun megaHertz(value: Int?): String =
        value?.let { "$it MHz" } ?: Labels.NOT_PUBLIC

    /** 频率读到了就显示来源节点，读不到就说明原因（例如系统限制）。 */
    private fun frequencyDetail(info: RamInfo): String = when {
        info.frequencySource != null -> "读取节点：${info.frequencySource}"
        info.frequencyNote != null -> "频率说明：${info.frequencyNote}"
        else -> "频率说明：${Labels.NOT_PUBLIC}"
    }
}
