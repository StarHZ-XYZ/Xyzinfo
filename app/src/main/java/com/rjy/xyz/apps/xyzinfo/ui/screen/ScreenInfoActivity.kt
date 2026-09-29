package com.rjy.xyz.apps.xyzinfo.ui.screen

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.data.ScreenInfoProvider
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityScreenInfoBinding
import com.rjy.xyz.apps.xyzinfo.model.ScreenInfo
import com.rjy.xyz.apps.xyzinfo.util.Formats
import com.rjy.xyz.apps.xyzinfo.util.Labels

/**
 * 屏幕参数详情页。
 */
class ScreenInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScreenInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreenInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        render(ScreenInfoProvider.load(this))
    }

    private fun render(info: ScreenInfo) = with(binding) {
        tvScreenTitle.text = "${info.widthPx} × ${info.heightPx}"
        tvScreenSubTitle.text = "分辨率、刷新率与触控能力总览"

        tvResolution.text = "屏幕分辨率：${info.widthPx} × ${info.heightPx}"
        tvScreenSize.text = "屏幕尺寸：${sizeInches(info.diagonalInches)}"
        tvDensity.text =
            "屏幕密度：${info.densityDpi} dpi / ${Formats.decimal(info.density)}x / ${info.densityBucket}"
        tvOrientation.text = "当前方向：${if (info.landscape) "横屏" else "竖屏"}"

        tvCurrentRefreshRate.text = "当前刷新率：${Formats.hertz(info.currentRefreshRateHz)}"
        tvRefreshModes.text = "支持刷新率档位：${refreshModes(info.supportedRefreshRatesHz)}"
        tvTouchPoints.text = "最大触控点数：${touchPoints(info.touchPointsHint)}"
        tvTouchSampleRate.text = "触控采样率：${touchSampleRate(info.touchSampleRateHz)}"

        tvHdrSupport.text = "HDR 支持：${hdrSupport(info)}"
        tvHdrCurrentStatus.text = "HDR 当前状态：${hdrCurrentStatus(info)}"
        tvWideColor.text = "广色域支持：${wideColorGamut(info.wideColorGamut)}"
        tvBrightnessHint.text = "亮度提示：${brightnessHint(info.maxBrightnessNode)}"

        tvRawDisplayInfo.text = info.rawPreview
    }

    private fun sizeInches(diagonalInches: Double?): String =
        diagonalInches?.let { "${Formats.decimal(it)} 英寸" } ?: Labels.UNKNOWN

    private fun refreshModes(rates: List<Float>): String =
        if (rates.isEmpty()) Labels.UNKNOWN else rates.joinToString(" / ") { Formats.refreshRateTag(it) }

    private fun touchPoints(hint: Int?): String =
        hint?.let { "$it 点或以上" } ?: Labels.NOT_PUBLIC

    private fun touchSampleRate(rateHz: Int?): String =
        rateHz?.let { Formats.hertzFromInt(it) } ?: Labels.NOT_PUBLIC

    private fun hdrSupport(info: ScreenInfo): String = when {
        !info.hdrDetectable -> "系统版本过低"
        info.hdrSupportedTypes.isEmpty() -> "不支持或系统未公开"
        else -> info.hdrSupportedTypes.joinToString(" / ")
    }

    private fun hdrCurrentStatus(info: ScreenInfo): String = when {
        !info.hdrDetectable -> "系统版本过低"
        info.hdrSupportedTypes.isEmpty() -> "当前不可用"
        info.hdrMaxLuminance > 0f && info.hdrMinLuminance >= 0f -> "支持 HDR，系统未公开实时状态"
        else -> "支持 HDR，但系统未公开当前状态"
    }

    private fun wideColorGamut(supported: Boolean?): String = when (supported) {
        null -> "系统版本过低"
        true -> "支持"
        false -> "不支持"
    }

    private fun brightnessHint(maxBrightnessNode: String?): String =
        maxBrightnessNode?.let { "系统最大亮度节点值：$it" } ?: Labels.NOT_PUBLIC
}
