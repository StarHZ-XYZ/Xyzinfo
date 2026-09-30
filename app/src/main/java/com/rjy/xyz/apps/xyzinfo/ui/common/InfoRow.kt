package com.rjy.xyz.apps.xyzinfo.ui.common

import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.ui.common.ThemeColors

/**
 * 信息行统一渲染：把「标签：值」拆成两种样式，可选在前面加一个小图标。
 *
 * - 标签：次要色 + 常规字重；值：主要色 + 加粗，一眼能分清字段与数值；
 * - [iconRes] 不为 0 时在行首插入一个 15dp 的矢量图标（自动染成强调色），
 *   图标用 ImageSpan 实现，会跟着文字基线走，不额外占高度。
 *
 * 注意：这个方法**整体替换** TextView 的文本（重复调用不会越叠越长），
 * 需要「在原有内容后面追加」的场景请用 [appendInfoRow]。
 */
fun TextView.setInfoRow(content: String, iconRes: Int = 0) {
    text = buildInfoRow("", content, iconRes)
}

/** 在已有内容后面追加一行（同样支持图标）。 */
fun TextView.appendInfoRow(content: String, iconRes: Int = 0) {
    text = buildInfoRow(text ?: "", content, iconRes)
}

private fun TextView.buildInfoRow(base: CharSequence, content: String, iconRes: Int): CharSequence {
    val builder = SpannableStringBuilder(base)
    if (iconRes != 0) {
        val icon = ContextCompat.getDrawable(context, iconRes)?.mutate()
        if (icon != null) {
            val size = (15 * resources.displayMetrics.density).toInt()
            icon.setBounds(0, 0, size, size)
            runCatching { icon.setTint(ThemeColors.accentDim(context)) }
            builder.append(
                SpannableString(" ").apply {
                    setSpan(
                        // ALIGN_CENTER（API 29+）让图标与文字垂直居中；老系统退回底部对齐
                        ImageSpan(
                            icon,
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                ImageSpan.ALIGN_CENTER
                            } else {
                                ImageSpan.ALIGN_BOTTOM
                            }
                        ),
                        0,
                        1,
                        SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            )
            builder.append("  ")
        }
    }

    val separatorIndex = content.indexOf('：')
    if (separatorIndex <= 0 || separatorIndex == content.length - 1) {
        builder.append(content)
        return builder
    }

    val label = content.substring(0, separatorIndex + 1)
    val value = content.substring(separatorIndex + 1)
    val labelColor = ContextCompat.getColor(context, R.color.text_secondary)
    builder.append(
        SpannableString(label).apply {
            setSpan(
                ForegroundColorSpan(labelColor),
                0,
                label.length,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    )
    builder.append(
        SpannableString(value).apply {
            setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                value.length,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    )
    return builder
}

/** 原始信息块：直接展示多行文本，不做标签着色。 */
fun TextView.setRawBlock(content: String) {
    text = content
}
