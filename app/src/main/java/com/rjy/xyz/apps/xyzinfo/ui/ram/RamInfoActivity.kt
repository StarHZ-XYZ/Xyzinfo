package com.rjy.xyz.apps.xyzinfo.ui.ram

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.data.RamInfoProvider
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityRamInfoBinding
import com.rjy.xyz.apps.xyzinfo.model.RamInfo
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

        render(RamInfoProvider.load(this))
    }

    private fun render(info: RamInfo) = with(binding) {
        tvRamTitle.text = nominalRamLabel(info.totalBytes)
        tvRamSubTitle.text = "内存状态、堆信息与交换分区总览"

        tvRamType.text = "RAM 类型：${info.typeName}"
        tvRamBrand.text = "RAM 品牌：${info.brandName ?: Labels.NOT_PUBLIC}"
        tvRamFreq.text = "RAM 标称频率：${megaHertz(info.nominalFrequencyMHz)}"
        tvRamCurrentFreq.text = "RAM 当前频率：${megaHertz(info.currentFrequencyMHz)}"

        tvTotalRam.text = "总内存：${Formats.bytes(info.totalBytes)}"
        tvUsedRam.text = "当前已用：${Formats.bytes(info.usedBytes)}"
        tvAvailRam.text = "当前可用：${Formats.bytes(info.availableBytes)}"
        tvUsagePercent.text = "内存占用率：${info.usagePercent}%"
        tvLowMemory.text = "系统低内存状态：${if (info.lowMemory) "是" else "否"}"
        tvThreshold.text = "低内存阈值：${Formats.bytes(info.thresholdBytes)}"

        tvJavaHeapMax.text = "Java 堆上限：${Formats.bytes(info.javaHeapMaxBytes)}"
        tvNativeHeapSize.text = "Native Heap 总大小：${Formats.bytes(info.nativeHeapTotalBytes)}"
        tvNativeHeapAllocated.text =
            "Native Heap 已分配：${Formats.bytes(info.nativeHeapAllocatedBytes)}"

        tvSwapTotal.text = "Swap / ZRAM 总量：${Formats.bytesOrUnknown(info.swapTotalBytes)}"
        tvSwapUsed.text = "Swap / ZRAM 已用：${Formats.bytesOrUnknown(info.swapUsedBytes)}"

        tvMemInfoRaw.text = info.memInfoPreview
    }

    /** 系统给出的总内存往往略小于标称容量，这里吸附到常见容量档位。 */
    private fun nominalRamLabel(totalBytes: Long): String {
        val gigaBytes = totalBytes / 1024.0 / 1024.0 / 1024.0
        return when {
            gigaBytes >= 23 -> "24GB RAM"
            gigaBytes >= 17 -> "18GB RAM"
            gigaBytes >= 15 -> "16GB RAM"
            gigaBytes >= 11 -> "12GB RAM"
            gigaBytes >= 7 -> "8GB RAM"
            gigaBytes >= 5 -> "6GB RAM"
            gigaBytes >= 3 -> "4GB RAM"
            gigaBytes >= 2 -> "3GB RAM"
            else -> "${Formats.decimal(gigaBytes, 1)}GB RAM"
        }
    }

    private fun megaHertz(value: Int?): String =
        value?.let { "$it MHz" } ?: Labels.NOT_PUBLIC
}
