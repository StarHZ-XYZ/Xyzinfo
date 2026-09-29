package com.rjy.xyz.apps.xyzinfo.ui.common

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * 把状态栏 / 导航栏高度加进根布局的内边距。
 *
 * Android 15 起强制边到边显示：页面背景需要延伸到状态栏后面，
 * 但内容必须让开状态栏与导航栏，这里统一处理，避免每个页面各写一遍。
 */
fun View.applySystemBarPadding() {
    val initialTop = paddingTop
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(top = initialTop + bars.top, bottom = initialBottom + bars.bottom)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
