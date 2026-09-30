package com.rjy.xyz.apps.xyzinfo.ui.env

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.EnvironmentCheck
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityEnvCheckBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.Anim
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import kotlin.math.roundToInt

/**
 * 环境检测页：把 [EnvironmentCheck] 的结果按等级展示出来。
 *
 * 三级配色：风险（红）/ 注意（黄）/ 安全（青绿）。每一条都给证据，不吓唬人。
 */
class EnvironmentCheckActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEnvCheckBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEnvCheckBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_NONE)

        binding.btnRerunEnv.setOnClickListener {
            Anim.pressFeedback(it)
            runCheck()
        }
        runCheck()
    }

    private fun runCheck() {
        binding.tvEnvSummary.setInfoRow("正在检测…")
        binding.layoutEnvItems.removeAllViews()
        Thread({
            val report = runCatching { EnvironmentCheck.run(this) }.getOrNull()
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (report == null) {
                    binding.tvEnvSummary.setInfoRow("检测失败，请重试")
                    return@runOnUiThread
                }
                binding.tvEnvSummary.setInfoRow(
                    report.conclusion + "\n风险 " + report.riskCount +
                        " 项 ｜ 注意 " + report.noticeCount + " 项 ｜ 共 " +
                        report.items.size + " 项检测"
                )
                renderItems(report.items)
            }
        }, "xyzinfo-envcheck").start()
    }

    private fun renderItems(items: List<EnvironmentCheck.Item>) {
        val density = resources.displayMetrics.density
        // 风险排前面，其次注意，最后安全 —— 一眼看到最要紧的
        val ordered = items.sortedBy { it.level.ordinal * -1 }
        ordered.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, (10 * density).roundToInt(), 0, (10 * density).roundToInt())
            }
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val tag = TextView(this).apply {
                text = when (item.level) {
                    EnvironmentCheck.Level.RISK -> "风险"
                    EnvironmentCheck.Level.NOTICE -> "注意"
                    EnvironmentCheck.Level.SAFE -> "安全"
                }
                textSize = 11f
                setPadding(
                    (8 * density).roundToInt(), (3 * density).roundToInt(),
                    (8 * density).roundToInt(), (3 * density).roundToInt()
                )
                setTextColor(ContextCompat.getColor(this@EnvironmentCheckActivity, R.color.surface))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 8 * density
                    setColor(
                        ContextCompat.getColor(
                            this@EnvironmentCheckActivity,
                            when (item.level) {
                                EnvironmentCheck.Level.RISK -> R.color.status_danger
                                EnvironmentCheck.Level.NOTICE -> R.color.status_warning
                                EnvironmentCheck.Level.SAFE -> R.color.accent
                            }
                        )
                    )
                }
            }
            val title = TextView(this).apply {
                text = item.title
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@EnvironmentCheckActivity, R.color.text_primary))
                setPadding((10 * density).roundToInt(), 0, 0, 0)
            }
            head.addView(tag)
            head.addView(title)
            val evidence = TextView(this).apply {
                text = item.evidence
                textSize = 12.5f
                setTextColor(ContextCompat.getColor(this@EnvironmentCheckActivity, R.color.text_secondary))
                setPadding(0, (4 * density).roundToInt(), 0, 0)
            }
            row.addView(head)
            row.addView(evidence)
            binding.layoutEnvItems.addView(row)
            if (item !== ordered.last()) {
                binding.layoutEnvItems.addView(
                    View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1
                        )
                        setBackgroundColor(
                            ContextCompat.getColor(this@EnvironmentCheckActivity, R.color.divider)
                        )
                    }
                )
            }
        }
        val visible = (0 until minOf(binding.layoutEnvItems.childCount, 10))
            .map { binding.layoutEnvItems.getChildAt(it) }
        Anim.staggerIn(visible, step = 30L, travelDp = 10f, duration = 300L)
    }
}
