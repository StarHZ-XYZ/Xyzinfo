package com.rjy.xyz.apps.xyzinfo.ui.common

import android.content.Context
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.rjy.xyz.apps.xyzinfo.R

/**
 * 主题色取值。
 *
 * 之前自绘部分（底栏指示线、光晕、芯片颜色、排行榜条、卫星点……）直接读 `@color/accent`，
 * 所以开了莫奈取色也只有 Material 组件跟着变，自绘的还是青绿。
 * 现在统一从这里取：**优先读主题的 colorPrimary / colorPrimaryContainer**，
 * 而动态取色（DynamicColors）会把这两个属性替换成系统壁纸配色，
 * 于是整套自绘颜色也跟着系统走；没开动态取色时回落到应用自己的青绿。
 */
object ThemeColors {

    fun accent(context: Context): Int =
        resolve(context, androidx.appcompat.R.attr.colorPrimary, R.color.accent)

    fun accentSoft(context: Context): Int =
        resolve(
            context,
            com.google.android.material.R.attr.colorPrimaryContainer,
            R.color.accent_soft
        )

    fun accentDim(context: Context): Int =
        resolve(context, androidx.appcompat.R.attr.colorPrimary, R.color.accent_dim)

    private fun resolve(context: Context, attrRes: Int, fallbackRes: Int): Int {
        val fallback = ContextCompat.getColor(context, fallbackRes)
        return runCatching { MaterialColors.getColor(context, attrRes, fallback) }
            .getOrDefault(fallback)
    }
}
