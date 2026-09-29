package com.rjy.xyz.apps.xyzinfo.ui.soc

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.data.SocInfoProvider
import com.rjy.xyz.apps.xyzinfo.databinding.ActivitySocInfoBinding
import com.rjy.xyz.apps.xyzinfo.model.SocInfo
import com.rjy.xyz.apps.xyzinfo.util.Formats
import com.rjy.xyz.apps.xyzinfo.util.Labels

/**
 * SoC（处理器 / GPU）详情页。
 */
class SocInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySocInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySocInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        render(SocInfoProvider.load(this))
    }

    private fun render(info: SocInfo) = with(binding) {
        tvSocModelTitle.text = info.displayName
        tvSocBrandSub.text = "SoC 品牌：${info.brandName}"
        tvBrandBadge.text = info.badge
        tvSocLevelHint.text = Labels.socLevelHint(info.performanceLevel)

        tvCpuArch.text = "CPU架构：${info.cpuArchitecture}"
        tvAbi.text = "ABI列表：${info.abiList}"
        tvCoreCount.text = "CPU核心数：${info.coreCount} 核"
        tvCluster.text = "CPU集群 / 大小核：${info.clusters}"
        tvSocManufacturer.text = "SoC制造商：${info.manufacturer}"
        tvSocModelCode.text = "SoC代号：${info.modelCode}"
        tvHardware.text = "硬件代号：${info.hardware}"

        tvGpuName.text = "GPU型号：${info.gpuName}"
        tvGpuCores.text = "GPU核心 / 计算单元：${info.gpuCores}"
        tvGpuMinFreq.text = "GPU最小频率：${megaHertz(info.gpuMinFreqMHz)}"
        tvGpuMaxFreq.text = "GPU最大频率：${gpuMaxFrequency(info)}"
        tvGraphicsApi.text =
            "图形接口与版本：${graphicsApi(info)}\n${rendererBackend(info)}"

        tvPerCoreFreq.text = perCoreFrequency(info.perCoreMaxFreqKHz)
        tvCpuInfoRaw.text = info.cpuInfoPreview

        applyBrandStyle(info.badge)
    }

    private fun megaHertz(value: Int?): String = value?.let { "$it MHz" } ?: Labels.UNKNOWN

    /** 优先使用设备实测频率，读不到时退回规格库中的名义频率。 */
    private fun gpuMaxFrequency(info: SocInfo): String = when {
        info.gpuMaxFreqMHz != null -> "${info.gpuMaxFreqMHz} MHz"
        info.gpuMaxFreqFromProfileMHz != null -> "${info.gpuMaxFreqFromProfileMHz} MHz（数据库）"
        else -> Labels.UNKNOWN
    }

    private fun graphicsApi(info: SocInfo): String {
        val api = if (info.vulkanSupported) {
            "OpenGL ES ${info.glEsVersion} / Vulkan"
        } else {
            "OpenGL ES ${info.glEsVersion}"
        }
        return "$api ｜ 数据库：${info.graphicsApiFromProfile}"
    }

    private fun rendererBackend(info: SocInfo): String =
        if (info.vulkanSupported) {
            "当前渲染后端：系统可能优先使用 Vulkan（同时支持 OpenGL ES ${info.glEsVersion}）"
        } else {
            "当前渲染后端：更可能使用 OpenGL ES ${info.glEsVersion}"
        }

    private fun perCoreFrequency(frequencies: List<Int?>): String = buildString {
        append("每核心最大频率：\n")
        frequencies.forEachIndexed { index, kiloHertz ->
            val value = kiloHertz?.let { Formats.kiloHertzAsGigaHertz(it) } ?: Labels.UNKNOWN
            append("CPU").append(index).append("：").append(value).append("\n")
        }
    }.trim()

    /** 依据芯片品牌调整头部卡片与徽标配色。 */
    private fun applyBrandStyle(badge: String) = with(binding) {
        val backgroundColor = when (badge) {
            "骁龙" -> "#FFF2E8"
            "联发科" -> "#EAF3FF"
            "猎户座" -> "#F1EEFF"
            "麒麟" -> "#ECFFF2"
            "展锐" -> "#FFF0F6"
            else -> "#F3F5F8"
        }
        val badgeColor = when (badge) {
            "骁龙" -> "#FF6A00"
            "联发科" -> "#247DFF"
            "猎户座" -> "#7253FF"
            "麒麟" -> "#18A957"
            "展锐" -> "#FF4F87"
            else -> "#6C7788"
        }

        cardBrand.setCardBackgroundColor(Color.parseColor(backgroundColor))

        val shape = GradientDrawable().apply {
            cornerRadius = 999f
            setColor(Color.parseColor(badgeColor))
        }
        tvBrandBadge.background = shape
        tvBrandBadge.setTextColor(Color.WHITE)
    }
}
