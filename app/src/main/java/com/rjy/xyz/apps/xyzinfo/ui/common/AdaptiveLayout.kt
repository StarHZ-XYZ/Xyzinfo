package com.rjy.xyz.apps.xyzinfo.ui.common

import android.content.Context
import android.view.ViewGroup
import com.rjy.xyz.apps.xyzinfo.data.DeviceFormDetector

/**
 * 按设备形态自适应排版。
 *
 * 现在做的是最稳的两件事：
 * 1. 宽屏（平板 / 展开态折叠屏）把左右内边距从 18dp 加到 44dp，
 *    避免文字行太长、卡片被拉得空荡荡；
 * 2. 宽屏把页面标题字号放大一档，视觉层级在平板上更清楚。
 *
 * 都只改运行时属性，不动布局 XML，所以 11 个页面统一生效。
 */
object AdaptiveLayout {

    fun apply(context: Context, host: ViewGroup) {
        val info = DeviceFormDetector.detect(context)
        if (!DeviceFormDetector.isWide(info)) return
        val density = context.resources.displayMetrics.density
        val horizontal = (44 * density).toInt()
        host.setPadding(horizontal, host.paddingTop, horizontal, host.paddingBottom)

        // 页面标题（20sp 以上的 TextView）在宽屏上放大 15%
        for (index in 0 until host.childCount) {
            val child = host.getChildAt(index)
            if (child is android.widget.TextView && child.textSize > 20f * density) {
                child.textSize = child.textSize / density * 1.15f
            }
        }
    }
}
