package com.rjy.xyz.apps.xyzinfo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 必应每日壁纸。
 *
 * 数据来自必应官方的图片归档接口（HPImageArchive），不需要任何 key：
 *   https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=zh-CN
 * 返回里 images[0].url 是当天的 1920×1080 大图，copyright 是版权文案。
 *
 * 图片下载到应用私有目录，作为页面底层背景使用；界面上的可读性由
 * 「渐变蒙版 + 卡片不透明」两层保证，见 GlassScaffold。
 */
object BingWallpaperRepository {

    data class Wallpaper(
        val file: File,
        val copyright: String,
        val date: String
    )

    /** idx 用来取「往前第几天」的图，0 = 今天，1 = 昨天……最多 7。 */
    private const val API = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=%d&n=1&mkt=%s"
    private const val BASE = "https://www.bing.com"

    private fun dir(context: Context): File = File(context.filesDir, "bing").apply { mkdirs() }

    private fun imageFile(context: Context): File = File(dir(context), "wallpaper.jpg")

    private fun metaFile(context: Context): File = File(dir(context), "meta.txt")

    /** 当前已下载的壁纸；没有就返回 null。 */
    fun current(context: Context): Wallpaper? {
        val file = imageFile(context)
        if (!file.exists() || file.length() < 20_000) return null
        val meta = runCatching { metaFile(context).readLines() }.getOrDefault(emptyList())
        return Wallpaper(
            file = file,
            copyright = meta.getOrNull(0).orEmpty(),
            date = meta.getOrNull(1).orEmpty()
        )
    }

    /** 今天是否已经下过（避免每次进页面都请求一次）。 */
    fun isUpToDate(context: Context): Boolean {
        val wallpaper = current(context) ?: return false
        return wallpaper.date == today() && wallpaper.file.length() > 20_000
    }

    data class Result(val success: Boolean, val message: String)

    /**
     * 下载必应当天（或往前第 [index] 天）的壁纸，成功后覆盖本地文件。
     *
     * @param index 0 = 今天，1 = 昨天，依此类推，用于「换一张」。
     */
    fun download(context: Context, index: Int = 0, market: String = "zh-CN"): Result {
        val json = runCatching { httpGet(String.format(API, index.coerceIn(0, 7), market)) }.getOrNull()
            ?: return Result(false, "连不上必应（检查网络后重试）")
        val image = runCatching {
            JSONObject(json.decodeToString()).getJSONArray("images").getJSONObject(0)
        }.getOrNull() ?: return Result(false, "必应返回的数据格式变了")

        val url = BASE + image.optString("url")
        val copyright = image.optString("copyright")
        val date = image.optString("startdate").ifBlank { today() }
        val bytes = runCatching { httpGet(url) }.getOrNull()
            ?: return Result(false, "壁纸图片下载失败")
        if (bytes.size < 20_000) return Result(false, "壁纸图片不完整（${bytes.size} 字节）")

        // 校验确实是能解码的图片，避免把 404 页面存成壁纸
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return Result(false, "下载到的不是有效图片")
        }

        runCatching {
            imageFile(context).writeBytes(bytes)
            metaFile(context).writeText("$copyright\n$date")
        }.onFailure { return Result(false, "写入本地失败：${it.message}") }
        return Result(true, "已更新必应壁纸：$date")
    }

    /** 读取当前壁纸，供界面做背景。 */
    fun loadBitmap(context: Context): Bitmap? {
        val file = current(context)?.file ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        // 屏幕宽度 1080 级别，采样到 1440 以内足够，省内存也够清晰
        var sample = 1
        while (bounds.outWidth / sample > 1440) sample *= 2
        return runCatching {
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        }.getOrNull()
    }

    fun clear(context: Context) {
        dir(context).deleteRecursively()
    }

    private fun today(): String =
        java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())

    private fun httpGet(url: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) XyzInfo")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
