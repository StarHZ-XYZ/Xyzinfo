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
private const val KEY_DEEPSEEK_THEME = "deepseek_theme"
    private const val KEY_SEASON_EFFECT = "season_effect"
    private const val KEY_SEASON_MODE = "season_mode"
    private const val KEY_SEASON_GRAVITY = "season_gravity"
    private const val KEY_HOLIDAY_EFFECT = "holiday_effect"
    private const val KEY_HOME_GRID = "home_grid_style"
    private const val KEY_DEEPSEEK_KEY = "deepseek_api_key"
    private const val KEY_AI_MODE = "ai_mode"
    private const val KEY_DARK_MODE = "dark_mode"

    private const val KEY_LAST_SINGLE = "last_single"
    private const val KEY_LAST_MULTI = "last_multi"
    private const val KEY_LAST_GPU = "last_gpu"
    private const val KEY_LAST_AT = "last_at"
    private const val KEY_LAST_DETAIL = "last_detail"
    private const val KEY_LAST_STABILITY = "last_stability"

    private const val KEY_SPEED_UNIT = "speed_unit"
    private const val KEY_SPEED_CONNECTIONS = "speed_connections"
    private const val KEY_SPEED_DOWN = "speed_last_down"
    private const val KEY_SPEED_UP = "speed_last_up"
    private const val KEY_SPEED_PING = "speed_last_ping"
    private const val KEY_SPEED_JITTER = "speed_last_jitter"
    private const val KEY_SPEED_NODE = "speed_last_node"
    private const val KEY_SPEED_AT = "speed_last_at"
    private const val KEY_SPEED_USE_LOCATION = "speed_use_location"
    private const val KEY_SPEED_HISTORY = "speed_history"

    /** 测速记录最多保留多少条（新的在前）。 */
    private const val SPEED_HISTORY_LIMIT = 20

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

    // ---------- 大肥鱼主题（角落装饰） ----------

    /**
     * 是否开启「大肥鱼主题」。
     *
     * 注意：这个主题**不改任何配色**（用户明确要求），只是在每个页面的角落
     * 摆一条可爱的大肥鱼当装饰（轻微浮动，不挡内容、不吃点击）。
     * 默认关。
     */
    fun deepSeekTheme(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DEEPSEEK_THEME, false)

    fun setDeepSeekTheme(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEEPSEEK_THEME, enabled).apply()
    }

    // ---------- 四季氛围效果 ----------

    /** 是否显示四季氛围（雪花 / 枫叶 / 花瓣 / 阳光）。默认**开启**。 */
    fun seasonEffectEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SEASON_EFFECT, true)

    fun setSeasonEffectEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SEASON_EFFECT, enabled).apply()
    }

    /**
     * 季节模式：`auto` 按月份自动，其它是 `spring` / `summer` / `autumn` / `winter`
     * 手动锁定（用户想一年四季随时看某个季节就用这个）。
     */
    fun seasonMode(context: Context): String =
        prefs(context).getString(KEY_SEASON_MODE, "auto") ?: "auto"

    fun setSeasonMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_SEASON_MODE, mode).apply()
    }

    /**
     * 四季氛围是否跟随重力：开启后雪花 / 枫叶 / 花瓣会**顺着设备倾斜的方向斜着落**。
     * 默认开（这是它最好玩的地方），关掉就还是直上直下地掉。
     */
    fun seasonGravity(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SEASON_GRAVITY, true)

    fun setSeasonGravity(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SEASON_GRAVITY, enabled).apply()
    }

    // ---------- 节日彩蛋（1.0.4）----------

    /**
     * 节日当天是否把四季氛围换成节日特效，并在进场时弹一条节日祝福。
     *
     * 默认**开启**：春节 / 中秋 / 国庆 / 元旦 / 圣诞 / 情人节 / 建军节 / 七夕
     * 这些日子打开软件才有彩蛋，平日的界面完全不受影响。
     */
    fun holidayEffectEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HOLIDAY_EFFECT, true)

    fun setHolidayEffectEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HOLIDAY_EFFECT, enabled).apply()
    }

    // ---------- 主页排版样式 ----------

    /** 主页功能模块是否用宫格（两列）排列；**默认开启宫格**。 */
    fun homeGridStyle(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HOME_GRID, true)

    fun setHomeGridStyle(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HOME_GRID, enabled).apply()
    }

    // ---------- 大肥鱼（AI 验机解读）----------

    /**
     * DeepSeek API Key；为空表示还没配置。
     *
     * 安全加固：库里存的是 AndroidKeyStore 加密后的密文（`v1:...`）。
     * 老版本直接存的明文会在第一次读取时自动迁移成密文，用户无感。
     */
    fun deepSeekApiKey(context: Context): String {
        val raw = prefs(context).getString(KEY_DEEPSEEK_KEY, "").orEmpty()
        if (raw.isEmpty()) return ""
        if (SecureStore.isEncrypted(raw)) return SecureStore.decrypt(raw).orEmpty()
        /*
         * 旧明文 → 密文迁移。
         *
         * 覆盖之前先做一次「加密 → 解密」自检：确认密钥库在本机可用、而且真的能还原，
         * 才会把明文替换掉。否则万一某些机型密钥库异常，用户辛苦填的 Key 就找不回来了
         * ——宁可暂时留着明文，也不能丢数据。
         */
        val blob = SecureStore.encrypt(raw)
        if (blob != null && SecureStore.decrypt(blob) == raw) {
            prefs(context).edit().putString(KEY_DEEPSEEK_KEY, blob).apply()
        }
        return raw
    }

    fun setDeepSeekApiKey(context: Context, key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            prefs(context).edit().remove(KEY_DEEPSEEK_KEY).apply()
            return
        }
        val blob = SecureStore.encrypt(trimmed)
        if (blob != null && SecureStore.decrypt(blob) == trimmed) {
            prefs(context).edit().putString(KEY_DEEPSEEK_KEY, blob).apply()
        } else {
            // 极端情况下密钥库不可用 / 还原不了：宁可不存，也不把 Key 明文落盘
            prefs(context).edit().remove(KEY_DEEPSEEK_KEY).apply()
        }
    }

    /**
     * AI 模式：`api` 用自己填的 API Key 直接调用；`web` 走官方免费版（需要登录，跳浏览器/App）。
     */
    fun aiMode(context: Context): String = prefs(context).getString(KEY_AI_MODE, "web") ?: "web"

    fun setAiMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_AI_MODE, mode).apply()
    }

    // ---------- 深色模式 ----------

    /** 0 = 跟随系统，1 = 浅色，2 = 深色。 */
    fun darkMode(context: Context): Int = prefs(context).getInt(KEY_DARK_MODE, 0)

    fun setDarkMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_DARK_MODE, mode.coerceIn(0, 2)).apply()
    }

    // ---------- 最近一次跑分成绩 ----------

    /** 最近一次跑分（用于在排行榜里插入「本机实测」那一行）。 */
    data class SavedBenchmark(
        val single: Int,
        val multi: Int,
        val gpu: Int?,
        val timestamp: Long,
        /** 上次各阶段的原始速率明细（换机器标定、或用户想回看时很有用）。 */
        val detail: String? = null,
        val stabilityPercent: Int = 0
    )

    fun saveBenchmark(
        context: Context,
        single: Int,
        multi: Int,
        gpu: Int?,
        detail: String? = null,
        stabilityPercent: Int = 0
    ) {
        prefs(context).edit()
            .putInt(KEY_LAST_SINGLE, single)
            .putInt(KEY_LAST_MULTI, multi)
            .putInt(KEY_LAST_GPU, gpu ?: -1)
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .putString(KEY_LAST_DETAIL, detail)
            .putInt(KEY_LAST_STABILITY, stabilityPercent)
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
            timestamp = p.getLong(KEY_LAST_AT, 0L),
            detail = p.getString(KEY_LAST_DETAIL, null),
            stabilityPercent = p.getInt(KEY_LAST_STABILITY, 0)
        )
    }

    fun clearBenchmark(context: Context) {
        prefs(context).edit()
            .remove(KEY_LAST_SINGLE)
            .remove(KEY_LAST_MULTI)
            .remove(KEY_LAST_GPU)
            .remove(KEY_LAST_AT)
            .remove(KEY_LAST_DETAIL)
            .remove(KEY_LAST_STABILITY)
            .apply()
    }

    // ---------- 网络测速 ----------

    /** 测速单位：`MBPS`（默认，运营商口径）或 `MBS`（下载器口径）。 */
    fun speedUnit(context: Context): String =
        prefs(context).getString(KEY_SPEED_UNIT, "MBPS") ?: "MBPS"

    fun setSpeedUnit(context: Context, name: String) {
        prefs(context).edit().putString(KEY_SPEED_UNIT, name).apply()
    }

    /** 并行连接数（1 / 4 / 8 / 16），默认 4 条。 */
    fun speedConnections(context: Context): Int =
        prefs(context).getInt(KEY_SPEED_CONNECTIONS, 4).coerceIn(1, 16)

    fun setSpeedConnections(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_SPEED_CONNECTIONS, value.coerceIn(1, 16)).apply()
    }

    /**
     * 测速前是否先按出口 IP 定位、自动就近选点。**默认开启**。
     *
     * 关掉之后直接用手里的候选节点（就近顺序 + 实测延迟），不再发定位请求 ——
     * 有些用户不想暴露位置，或者身处代理环境时定位反而会挑错节点。
     */
    fun speedUseLocation(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SPEED_USE_LOCATION, true)

    fun setSpeedUseLocation(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SPEED_USE_LOCATION, enabled).apply()
    }

    /** 一条历史测速记录（速度统一存字节/秒，显示时再按单位换算）。 */
    data class SpeedRecord(
        val timestamp: Long,
        val serverName: String,
        val downloadBytesPerSecond: Double,
        val uploadBytesPerSecond: Double?,
        val pingMs: Double?,
        val jitterMs: Double?,
        val connections: Int
    )

    /**
     * 追加一条测速记录，最多保留 [SPEED_HISTORY_LIMIT] 条（新的在最前面）。
     *
     * 记录只有"什么时候、连哪台、跑出多少、延迟多少"这几项，不含任何标识信息。
     */
    fun addSpeedRecord(context: Context, record: SpeedRecord) {
        val records = speedRecords(context).toMutableList()
        records.add(0, record)
        while (records.size > SPEED_HISTORY_LIMIT) records.removeAt(records.size - 1)
        val array = org.json.JSONArray()
        records.forEach { item ->
            array.put(
                org.json.JSONObject().apply {
                    put("t", item.timestamp)
                    put("node", item.serverName)
                    put("down", item.downloadBytesPerSecond)
                    put("up", item.uploadBytesPerSecond ?: -1.0)
                    put("ping", item.pingMs ?: -1.0)
                    put("jitter", item.jitterMs ?: -1.0)
                    put("conn", item.connections)
                }
            )
        }
        prefs(context).edit().putString(KEY_SPEED_HISTORY, array.toString()).apply()
    }

    fun speedRecords(context: Context): List<SpeedRecord> {
        val raw = prefs(context).getString(KEY_SPEED_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = org.json.JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                SpeedRecord(
                    timestamp = item.optLong("t"),
                    serverName = item.optString("node"),
                    downloadBytesPerSecond = item.optDouble("down", 0.0),
                    uploadBytesPerSecond = item.optDouble("up", -1.0).takeIf { it >= 0 },
                    pingMs = item.optDouble("ping", -1.0).takeIf { it >= 0 },
                    jitterMs = item.optDouble("jitter", -1.0).takeIf { it >= 0 },
                    connections = item.optInt("conn")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun clearSpeedRecords(context: Context) {
        prefs(context).edit().remove(KEY_SPEED_HISTORY).apply()
    }

    /** 最近一次测速结果（速度统一存字节/秒，显示时再按单位换算）。 */
    data class SavedSpeedResult(
        val downloadBytesPerSecond: Double,
        val uploadBytesPerSecond: Double?,
        val pingMs: Double?,
        val jitterMs: Double?,
        val serverName: String,
        val timestamp: Long
    )

    fun saveSpeedResult(
        context: Context,
        downloadBytesPerSecond: Double,
        uploadBytesPerSecond: Double?,
        pingMs: Double?,
        jitterMs: Double?,
        serverName: String
    ) {
        prefs(context).edit()
            .putString(KEY_SPEED_DOWN, downloadBytesPerSecond.toString())
            .putString(KEY_SPEED_UP, uploadBytesPerSecond?.toString() ?: "")
            .putString(KEY_SPEED_PING, pingMs?.toString() ?: "")
            .putString(KEY_SPEED_JITTER, jitterMs?.toString() ?: "")
            .putString(KEY_SPEED_NODE, serverName)
            .putLong(KEY_SPEED_AT, System.currentTimeMillis())
            .apply()
    }

    fun lastSpeedResult(context: Context): SavedSpeedResult? {
        val p = prefs(context)
        val download = p.getString(KEY_SPEED_DOWN, "")?.toDoubleOrNull() ?: return null
        return SavedSpeedResult(
            downloadBytesPerSecond = download,
            uploadBytesPerSecond = p.getString(KEY_SPEED_UP, "")?.toDoubleOrNull(),
            pingMs = p.getString(KEY_SPEED_PING, "")?.toDoubleOrNull(),
            jitterMs = p.getString(KEY_SPEED_JITTER, "")?.toDoubleOrNull(),
            serverName = p.getString(KEY_SPEED_NODE, "").orEmpty(),
            timestamp = p.getLong(KEY_SPEED_AT, 0L)
        )
    }
}
