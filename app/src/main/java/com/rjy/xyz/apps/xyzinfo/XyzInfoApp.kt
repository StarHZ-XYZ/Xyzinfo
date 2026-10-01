package com.rjy.xyz.apps.xyzinfo

import android.app.Application
import android.os.Build
import com.google.android.material.color.DynamicColors
import com.rjy.xyz.apps.xyzinfo.data.SecurityGuard
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository

/**
 * 应用入口。
 *
 * 只做一件事：按设置决定是否启用 **动态取色（Material You / 莫奈取色）**。
 * Android 12+ 上，系统会根据壁纸生成一套配色；`DynamicColors` 会在每个 Activity
 * 创建时把它套到 Material 组件（按钮、开关、滑块、水波纹、系统栏）上。
 *
 * 注意：本应用的强调色大量是自绘的（`@color/accent`），动态取色目前主要作用在
 * Material 组件与系统栏；自绘部分改用系统主色在后续版本继续推进。
 */
class XyzInfoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 逆向/二次打包防护：release 包里检测调试器与签名，不通过直接退出
        SecurityGuard.install(this)
        applyDarkMode()
        applyDynamicColors()
    }

    /** 深色模式：跟随系统 / 强制浅色 / 强制深色。 */
    private fun applyDarkMode() {
        val mode = when (SettingsRepository.darkMode(this)) {
            1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            2 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun applyDynamicColors() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (!SettingsRepository.followSystemColor(this)) return
        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    companion object {
        /**
         * 进程内是否已经走过一次启动流程。
         *
         * 用途：从桌面再次点开应用时，系统会在已有任务栈上**再启一个 SplashActivity**
         * （开屏页自己 finish 过，不是任务栈根），于是"明明进程没被杀，却整页重载了一遍"。
         * 有了这个标记，开屏页只在**冷启动**时播动画，热启动直接透明掠过，
         * 原来那套页面栈原封不动地恢复。
         */
        @Volatile
        var warmStarted = false
            private set

        fun markStarted() {
            warmStarted = true
        }
    }
}
