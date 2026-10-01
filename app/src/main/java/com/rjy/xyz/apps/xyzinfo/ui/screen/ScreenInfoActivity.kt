package com.rjy.xyz.apps.xyzinfo.ui.screen

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.data.ScreenInfoProvider
import com.rjy.xyz.apps.xyzinfo.data.ResolutionTier
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityScreenInfoBinding
import com.rjy.xyz.apps.xyzinfo.model.ScreenInfo
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import com.rjy.xyz.apps.xyzinfo.ui.common.setRawBlock
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
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_NONE)

        render(ScreenInfoProvider.load(this))
    }

    private fun render(info: ScreenInfo) = with(binding) {
        tvScreenTitle.text = "${info.widthPx} × ${info.heightPx}"
        tvScreenSubTitle.text = "分辨率、刷新率与触控能力总览"

        tvResolution.setInfoRow("屏幕分辨率：${info.widthPx} × ${info.heightPx}")
        tvResolutionTier.setInfoRow("分辨率档位：${ResolutionTier.describe(info.widthPx, info.heightPx)}")
        tvScreenSize.setInfoRow("屏幕尺寸：${sizeInches(info.diagonalInches)}")
        tvDensity.setInfoRow(
            "屏幕密度：${info.densityDpi} dpi / ${Formats.decimal(info.density.toDouble())}x / ${info.densityBucket}"
        )
        tvOrientation.setInfoRow("当前方向：${if (info.landscape) "横屏" else "竖屏"}")

        tvCurrentRefreshRate.setInfoRow("当前刷新率：${Formats.hertz(info.currentRefreshRateHz)}")
        tvRefreshModes.setInfoRow("支持刷新率档位：${refreshModes(info.supportedRefreshRatesHz)}")
        tvTouchPoints.setInfoRow("最大触控点数：${touchPoints(info.touchPointsHint)}")
        tvTouchSampleRate.setInfoRow("触控采样率：${touchSampleRate(info.touchSampleRateHz)}")

        tvHdrSupport.setInfoRow("HDR 支持：${hdrSupport(info)}")
        tvHdrCurrentStatus.setInfoRow("HDR 当前状态：${hdrCurrentStatus(info)}")
        tvWideColor.setInfoRow("广色域支持：${wideColorGamut(info.wideColorGamut)}")
        tvBrightnessHint.setInfoRow("亮度提示：${brightnessHint(info.maxBrightnessNode)}")

        tvRawDisplayInfo.setRawBlock(info.rawPreview)

        buildGraySteps()
        tvHdrTestResult.text = hdrTestNote(info)
    }

    /**
     * 黑位 / 高光裁切测试：11 格灰度（0% ~ 100%）。
     *
     * 好屏能分出左边几格；如果 0% 与 10% 糊成一块，说明黑位被压死了。
     * 这里是**真的屏幕能力测试**（和上面两张参考图的观感对比互补）。
     */
    private fun buildGraySteps() = with(binding) {
        layoutGraySteps.removeAllViews()
        for (step in 0..10) {
            val value = (step * 25.5).toInt().coerceIn(0, 255)
            layoutGraySteps.addView(
                android.view.View(this@ScreenInfoActivity).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        0,
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        1f
                    )
                    setBackgroundColor(android.graphics.Color.rgb(value, value, value))
                }
            )
        }
    }

    /** HDR 测试的结论行：把系统的 HDR 报告情况说清楚。 */
    private fun hdrTestNote(info: ScreenInfo): String {
        val supported = info.hdrSupportedTypes.isNotEmpty()
        return if (supported) {
            "本机报告支持：${info.hdrSupportedTypes.joinToString(" / ")}。" +
                "两张图差别越明显，说明屏幕的对比度与 HDR 处理越好。"
        } else {
            "系统没有报告 HDR 类型（部分机型在应用层拿不到）。" +
                "两张图的差别仍然能反映屏幕对比度：差别很小说明黑位与高光都被压缩了。"
        }
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
