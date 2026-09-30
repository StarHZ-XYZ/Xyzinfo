package com.rjy.xyz.apps.xyzinfo.data

import android.content.Context

/**
 * 应用设置（SharedPreferences）。
 *
 * 目前只有三个开关 + 一份最近一次的跑分成绩：
 * - 液态玻璃底栏：关掉后底栏完全不创建，页面回到纯内容布局。
 * - 丝滑动画：关掉后所有入场 / 按压 / 数值动效直接跳到终态（省电、无障碍友好）。
 * - 深度跑分：把跑分时长从约 1 分钟拉长到约 3 分钟，测持续性能与温度墙。
 */
object SettingsRepository {

    private const val PREFS = "xyzinfo_settings"

    private const val KEY_GLASS_BAR = "glass_bottom_bar"
    private const val KEY_ANIMATIONS = "smooth_animations"
    private const val KEY_DEEP_BENCHMARK = "deep_benchmark"
    private const val KEY_PARTICLES = "touch_particles"
    private const val KEY_BING_WALLPAPER = "bing_wallpaper"
    private const val KEY_WALLPAPER_SCRIM = "wallpaper_scrim"
    private const val KEY_FOLLOW_SYSTEM_COLOR = "follow_system_color"
    private const val KEY_SEASON_EFFECT = "season_effect"

    private const val KEY_LAST_SINGLE = "last_single"
    private const val KEY_LAST_MULTI = "last_multi"
    private const val KEY_LAST_GPU = "last_gpu"
    private const val KEY_LAST_AT = "last_at"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- 液态玻璃底栏 ----------

    fun glassBottomBarEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GLASS_BAR, true)

    fun setGlassBottomBarEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_GLASS_BAR, enabled).apply()
    }

    // ---------- 丝滑动画 ----------

    fun animationsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ANIMATIONS, true)

    fun setAnimationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ANIMATIONS, enabled).apply()
    }

    // ---------- 深度跑分 ----------

    fun deepBenchmarkEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DEEP_BENCHMARK, false)

    fun setDeepBenchmarkEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEEP_BENCHMARK, enabled).apply()
    }

    // ---------- 点击粒子效果 ----------

    fun particleEffectEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PARTICLES, true)

    fun setParticleEffectEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PARTICLES, enabled).apply()
    }

    // ---------- 必应每日壁纸 ----------

    fun bingWallpaperEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BING_WALLPAPER, false)

    fun setBingWallpaperEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BING_WALLPAPER, enabled).apply()
    }

    /**
     * 蒙版浓度（0~100）。
     *
     * 壁纸之上会压一层半透明蒙版，数字越大越"糊"、文字越清楚。
     * 默认 72：既看得出是壁纸，又能保证标题这类直接压在背景上的文字可读。
     */
    fun wallpaperScrim(context: Context): Int =
        prefs(context).getInt(KEY_WALLPAPER_SCRIM, 72)

    fun setWallpaperScrim(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_WALLPAPER_SCRIM, value.coerceIn(45, 92)).apply()
    }

    // ---------- 莫奈取色 / 跟随系统主题色 ----------

    /** 是否跟随系统主题色（Android 12+ 的动态取色）。默认关，保持应用自己的青绿配色。 */
    fun followSystemColor(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FOLLOW_SYSTEM_COLOR, false)

    fun setFollowSystemColor(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FOLLOW_SYSTEM_COLOR, enabled).apply()
    }

    // ---------- 四季氛围效果 ----------

    /** 是否显示四季氛围（雪花 / 枫叶 / 花瓣 / 阳光）。默认关，避免影响老机器流畅度。 */
    fun seasonEffectEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SEASON_EFFECT, false)

    fun setSeasonEffectEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SEASON_EFFECT, enabled).apply()
    }

    // ---------- 最近一次跑分成绩 ----------

    /** 最近一次跑分（用于在排行榜里插入「本机实测」那一行）。 */
    data class SavedBenchmark(
        val single: Int,
        val multi: Int,
        val gpu: Int?,
        val timestamp: Long
    )

    fun saveBenchmark(context: Context, single: Int, multi: Int, gpu: Int?) {
        prefs(context).edit()
            .putInt(KEY_LAST_SINGLE, single)
            .putInt(KEY_LAST_MULTI, multi)
            .putInt(KEY_LAST_GPU, gpu ?: -1)
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .apply()
    }

    fun lastBenchmark(context: Context): SavedBenchmark? {
        val p = prefs(context)
        val single = p.getInt(KEY_LAST_SINGLE, -1)
        val multi = p.getInt(KEY_LAST_MULTI, -1)
        if (single <= 0 || multi <= 0) return null
        val gpu = p.getInt(KEY_LAST_GPU, -1)
        return SavedBenchmark(
            single = single,
            multi = multi,
            gpu = gpu.takeIf { it > 0 },
            timestamp = p.getLong(KEY_LAST_AT, 0L)
        )
    }

    fun clearBenchmark(context: Context) {
        prefs(context).edit()
            .remove(KEY_LAST_SINGLE)
            .remove(KEY_LAST_MULTI)
            .remove(KEY_LAST_GPU)
            .remove(KEY_LAST_AT)
            .apply()
    }
}
