package com.rjy.xyz.apps.xyzinfo.ui.common

import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R

/**
 * 信息行统一渲染：把「标签：值」拆成两种样式。
 *
 * 标签用次要色 + 常规字重，值用主要色 + 中等字重，
 * 这样一行里能一眼看出哪个是字段、哪个是数值。
 */
fun TextView.setInfoRow(content: String) {
    val separatorIndex = content.indexOf('：')
    if (separatorIndex <= 0 || separatorIndex == content.length - 1) {
        text = content
        return
    }

    val label = content.substring(0, separatorIndex + 1)
    val value = content.substring(separatorIndex + 1)
    val labelColor = ContextCompat.getColor(context, R.color.text_secondary)

    text = SpannableStringBuilder().apply {
        append(SpannableString(label).apply {
            setSpan(
                ForegroundColorSpan(labelColor),
                0,
                label.length,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        })
        append(SpannableString(value).apply {
            setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                value.length,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        })
    }
}

/** 原始信息块：直接展示多行文本，不做标签着色。 */
fun TextView.setRawBlock(content: String) {
    text = content
}
