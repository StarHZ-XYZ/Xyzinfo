package com.rjy.xyz.apps.xyzinfo.ui.inspect

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.DeviceInspector
import com.rjy.xyz.apps.xyzinfo.data.EnvironmentCheck
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityInspectBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.Anim
import com.rjy.xyz.apps.xyzinfo.ui.common.countUpText
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import kotlin.math.roundToInt

/** 大肥鱼验机：本地规则引擎出一份验机报告（评分 + 逐条证据）。 */
class DeviceInspectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInspectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInspectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_NONE)

        binding.tvInspectNote.setInfoRow(
            "验机规则全部在本机运行，不会上传任何信息。\n"
                + "接 DeepSeek 做「AI 解读」的能力已预留（需要在设置里配置 API Key 后启用），"
                + "未配置时只显示本地结论。"
        )
        binding.btnRerunInspect.setOnClickListener {
            Anim.pressFeedback(it)
            runInspect()
        }
        runInspect()
    }

    private fun runInspect() {
        binding.tvInspectVerdict.setInfoRow("正在验机…")
        binding.tvInspectScore.text = "—"
        binding.layoutInspectItems.removeAllViews()
        Thread({
            val report = runCatching { DeviceInspector.inspect(this) }.getOrNull()
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (report == null) {
                    binding.tvInspectVerdict.setInfoRow("验机失败，请重试")
                    return@runOnUiThread
                }
                Anim.countUpText(binding.tvInspectScore, report.score)
                binding.tvInspectVerdict.setInfoRow(
                    report.verdict + "\n风险 " + report.riskCount + " 项 ｜ 注意 " +
                        report.noticeCount + " 项 ｜ 共 " + report.findings.size + " 项检查"
                )
                // 结论库：命中的每条都按序号列出来（没命中时是通用结论）
                binding.tvInspectSummary.text = "【总结】\n" + report.summaries
                    .mapIndexed { index, line -> "${index + 1}. $line" }
                    .joinToString("\n\n")
                render(report.findings)
            }
        }, "xyzinfo-inspect").start()
    }

    private fun render(findings: List<DeviceInspector.Finding>) {
        val density = resources.displayMetrics.density
        findings.forEachIndexed { index, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, (10 * density).roundToInt(), 0, (10 * density).roundToInt())
            }
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            head.addView(
                TextView(this).apply {
                    text = when (item.level) {
                        EnvironmentCheck.Level.RISK -> "风险"
                        EnvironmentCheck.Level.NOTICE -> "注意"
                        EnvironmentCheck.Level.SAFE -> "正常"
                    }
                    textSize = 11f
                    setPadding(
                        (8 * density).roundToInt(), (3 * density).roundToInt(),
                        (8 * density).roundToInt(), (3 * density).roundToInt()
                    )
                    setTextColor(ContextCompat.getColor(this@DeviceInspectActivity, R.color.surface))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 8 * density
                        setColor(
                            ContextCompat.getColor(
                                this@DeviceInspectActivity,
                                when (item.level) {
                                    EnvironmentCheck.Level.RISK -> R.color.status_danger
                                    EnvironmentCheck.Level.NOTICE -> R.color.status_warning
                                    EnvironmentCheck.Level.SAFE -> R.color.accent
                                }
                            )
                        )
                    }
                }
            )
            head.addView(
                TextView(this).apply {
                    text = item.title
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(this@DeviceInspectActivity, R.color.text_primary))
                    setPadding((10 * density).roundToInt(), 0, 0, 0)
                }
            )
            row.addView(head)
            row.addView(
                TextView(this).apply {
                    text = item.detail
                    textSize = 12.5f
                    setTextColor(ContextCompat.getColor(this@DeviceInspectActivity, R.color.text_secondary))
                    setPadding(0, (4 * density).roundToInt(), 0, 0)
                }
            )
            binding.layoutInspectItems.addView(row)
            if (index != findings.lastIndex) {
                binding.layoutInspectItems.addView(
                    View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1
                        )
                        setBackgroundColor(
                            ContextCompat.getColor(this@DeviceInspectActivity, R.color.divider)
                        )
                    }
                )
            }
        }
        val visible = (0 until minOf(binding.layoutInspectItems.childCount, 10))
            .map { binding.layoutInspectItems.getChildAt(it) }
        Anim.staggerIn(visible, step = 30L, travelDp = 10f, duration = 300L)
    }
}
