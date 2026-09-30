package com.rjy.xyz.apps.xyzinfo.ui.soc

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.SocInfoProvider
import com.rjy.xyz.apps.xyzinfo.databinding.ActivitySocInfoBinding
import com.rjy.xyz.apps.xyzinfo.model.SocInfo
import com.rjy.xyz.apps.xyzinfo.model.SocBrand
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import com.rjy.xyz.apps.xyzinfo.ui.common.setRawBlock
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
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_NONE)

        render(SocInfoProvider.load(this))
    }

    private fun render(info: SocInfo) = with(binding) {
        tvSocModelTitle.text = info.displayName
        tvSocBrandSub.setInfoRow("SoC 品牌：${info.brandName}")
        tvBrandBadge.text = info.badge
        tvSocLevelHint.text = Labels.socLevelHint(info.performanceLevel)

        tvCpuArch.setInfoRow("CPU架构：${info.cpuArchitecture}")
        tvAbi.setInfoRow("ABI列表：${info.abiList}")
        tvCoreCount.setInfoRow("CPU核心数：${info.coreCount} 核")
        tvCluster.setInfoRow("CPU集群 / 大小核：${info.clusters}")
        tvSocManufacturer.setInfoRow("SoC制造商：${info.manufacturer}")
        tvSocModelCode.setInfoRow("SoC代号：${info.modelCode}")
        tvHardware.setInfoRow("硬件代号：${info.hardware}")

        tvGpuName.setInfoRow("GPU型号：${info.gpuName}")
        tvGpuCores.setInfoRow("GPU核心 / 计算单元：${info.gpuCores}")
        tvGpuMinFreq.setInfoRow("GPU最小频率：${megaHertz(info.gpuMinFreqMHz)}")
        tvGpuMaxFreq.setInfoRow("GPU最大频率：${gpuMaxFrequency(info)}")
        tvGraphicsApi.setInfoRow("图形接口与版本：${graphicsApi(info)}\n${rendererBackend(info)}")

        tvPerCoreFreq.setRawBlock(perCoreFrequency(info.perCoreMaxFreqKHz))
        tvCpuInfoRaw.setRawBlock(info.cpuInfoPreview)

        applyBrandStyle(info.brand)
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

    /**
     * 依据芯片品牌显示对应图标与配色。
     *
     * 颜色都取自 @color/brand_*，浅色 / 深色模式下会自动切换。
     */
    private fun applyBrandStyle(brand: SocBrand) = with(binding) {
        val foreground = ContextCompat.getColor(this@SocInfoActivity, brand.foregroundColorRes())
        val background = ContextCompat.getColor(this@SocInfoActivity, brand.backgroundColorRes())

        ivBrandIcon.setImageResource(brand.iconRes())
        ivBrandIcon.backgroundTintList = ColorStateList.valueOf(background)
        tvBrandBadge.backgroundTintList = ColorStateList.valueOf(background)
        tvBrandBadge.setTextColor(foreground)
    }

    private fun SocBrand.iconRes(): Int = when (this) {
        SocBrand.SNAPDRAGON -> R.drawable.ic_brand_snapdragon
        SocBrand.MEDIATEK -> R.drawable.ic_brand_mediatek
        SocBrand.EXYNOS -> R.drawable.ic_brand_exynos
        SocBrand.KIRIN -> R.drawable.ic_brand_kirin
        SocBrand.UNISOC -> R.drawable.ic_brand_unisoc
        SocBrand.TENSOR -> R.drawable.ic_brand_tensor
        SocBrand.XRING -> R.drawable.ic_brand_xring
        SocBrand.UNKNOWN -> R.drawable.ic_brand_unknown
    }

    private fun SocBrand.foregroundColorRes(): Int = when (this) {
        SocBrand.SNAPDRAGON -> R.color.brand_snapdragon_fg
        SocBrand.MEDIATEK -> R.color.brand_mediatek_fg
        SocBrand.EXYNOS -> R.color.brand_exynos_fg
        SocBrand.KIRIN -> R.color.brand_kirin_fg
        SocBrand.UNISOC -> R.color.brand_unisoc_fg
        SocBrand.TENSOR -> R.color.brand_tensor_fg
        SocBrand.XRING -> R.color.brand_xring_fg
        SocBrand.UNKNOWN -> R.color.brand_unknown_fg
    }

    private fun SocBrand.backgroundColorRes(): Int = when (this) {
        SocBrand.SNAPDRAGON -> R.color.brand_snapdragon_bg
        SocBrand.MEDIATEK -> R.color.brand_mediatek_bg
        SocBrand.EXYNOS -> R.color.brand_exynos_bg
        SocBrand.KIRIN -> R.color.brand_kirin_bg
        SocBrand.UNISOC -> R.color.brand_unisoc_bg
        SocBrand.TENSOR -> R.color.brand_tensor_bg
        SocBrand.XRING -> R.color.brand_xring_bg
        SocBrand.UNKNOWN -> R.color.brand_unknown_bg
    }
}
