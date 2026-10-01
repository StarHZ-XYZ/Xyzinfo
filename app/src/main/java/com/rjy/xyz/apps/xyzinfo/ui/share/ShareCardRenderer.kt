package com.rjy.xyz.apps.xyzinfo.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.appcompat.content.res.AppCompatResources
import com.rjy.xyz.apps.xyzinfo.data.BatteryInfoProvider
import com.rjy.xyz.apps.xyzinfo.data.DeviceOverviewProvider
import com.rjy.xyz.apps.xyzinfo.data.RamInfoProvider
import com.rjy.xyz.apps.xyzinfo.data.ScreenInfoProvider
import com.rjy.xyz.apps.xyzinfo.data.SocInfoProvider
import com.rjy.xyz.apps.xyzinfo.util.Formats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** 分享卡片的配色主题。 */
enum class ShareTheme(val id: String, val label: String) {
    /** 深空：深蓝黑底 + 青色光，最耐看。 */
    DEPTH("depth", "深空"),

    /** 云白：浅底深字，适合发到白底的地方。 */
    CLOUD("cloud", "云白"),

    /** 品牌色：跟着设备品牌的主色走。 */
    BRAND("brand", "品牌色");

    companion object {
        fun of(id: String?): ShareTheme = values().firstOrNull { it.id == id } ?: DEPTH
    }
}

/**
 * 设备信息分享卡片（1.0.5 新增）。
 *
 * 把「这台机器到底是什么」浓缩成一张 1080×1620 的图：品牌 logo + 机型名 + 八项关键参数。
 * 纯 Canvas 绘制，不依赖任何图片素材，所以任何设备上都能生成，
 * 而且可以直接在单元测试里渲染出来比对（见 `ShareCardRendererTest`）。
 */
object ShareCardRenderer {

    /** 卡片尺寸：3:4.5，微信 / 微博 / QQ 都不会被裁。 */
    const val CARD_WIDTH = 1080
    const val CARD_HEIGHT = 1620

    /** 卡片上要显示的一行参数。 */
    data class Spec(val label: String, val value: String)

    /** 卡片内容。 */
    data class Content(
        val deviceName: String,
        val modelLine: String,
        val brandLabel: String,
        val logo: Drawable?,
        val specs: List<Spec>,
        val footerLeft: String,
        val footerRight: String
    )

    /** 按当前设备采集卡片内容。 */
    fun collect(context: Context, versionName: String): Content {
        val overview = runCatching { DeviceOverviewProvider.load() }.getOrNull()
        val soc = runCatching { SocInfoProvider.load(context) }.getOrNull()
        val ram = runCatching { RamInfoProvider.load(context) }.getOrNull()
        val screen = runCatching { ScreenInfoProvider.load(context) }.getOrNull()
        val battery = runCatching { BatteryInfoProvider.load(context) }.getOrNull()

        val deviceName = overview?.displayName ?: Build.MODEL
        val modelLine = buildString {
            append("型号 ").append(overview?.rawModel ?: Build.MODEL)
            val code = overview?.deviceCode ?: Build.DEVICE
            if (code.isNotBlank()) append(" ｜ 代号 ").append(code)
        }
        val brandLabel = overview?.manufacturer?.takeIf { it.isNotBlank() }
            ?: overview?.brand ?: Build.MANUFACTURER

        val specs = buildList {
            add(Spec("处理器", soc?.displayName ?: overview?.cpuArchitecture ?: "未知"))
            add(Spec("图形", soc?.gpuName?.takeIf { it.isNotBlank() } ?: "未知"))
            add(
                Spec(
                    "内存",
                    ram?.let { info ->
                        val total = Formats.bytes(info.measuredTotalBytes)
                        val type = info.typeName.takeIf { it.isNotBlank() && it != "未知" }
                        if (type != null) "$total · $type" else total
                    } ?: "未知"
                )
            )
            add(
                Spec(
                    "屏幕",
                    screen?.let { "${it.widthPx}×${it.heightPx} · ${it.currentRefreshRateHz.roundToInt()}Hz" }
                        ?: "未知"
                )
            )
            add(
                Spec(
                    "电池",
                    battery?.let { info ->
                        val capacity = info.designCapacityMah
                        if (capacity != null) "$capacity mAh" else "${info.percent}%"
                    } ?: "未知"
                )
            )
            add(Spec("系统", "Android ${overview?.androidRelease ?: Build.VERSION.RELEASE}（API ${overview?.apiLevel ?: Build.VERSION.SDK_INT}）"))
            add(Spec("架构", overview?.abiLabel ?: System.getProperty("os.arch").orEmpty()))
            add(Spec("系统 UI", overview?.romName?.takeIf { it.isNotBlank() } ?: "未知"))
            add(Spec("设备代号", overview?.deviceCode?.takeIf { it.isNotBlank() } ?: Build.DEVICE))
            add(Spec("内核", overview?.kernelRelease?.takeIf { it.isNotBlank() } ?: "未知"))
        }

        return Content(
            deviceName = deviceName,
            modelLine = modelLine,
            brandLabel = brandLabel,
            logo = null,
            specs = specs,
            footerLeft = "XYZ-Devinfo · 星幻终（RJYZ）",
            footerRight = "v$versionName · ${dateStamp()}"
        )
    }

    private fun dateStamp(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())

    /** 画一张卡片。[brandColor] 只在 [ShareTheme.BRAND] 下使用。 */
    fun render(
        context: Context,
        content: Content,
        theme: ShareTheme = ShareTheme.DEPTH,
        brandColor: Int = 0xFF3AA6A0.toInt()
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = paletteFor(theme, brandColor)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        drawBackground(canvas, paint, palette)
        drawHeader(canvas, paint, content, palette)
        drawSpecs(canvas, paint, content, palette)
        drawFooter(canvas, paint, content, palette)
        return bitmap
    }

    private class Palette(
        val top: Int,
        val bottom: Int,
        val glow: Int,
        val title: Int,
        val body: Int,
        val muted: Int,
        val card: Int,
        val stroke: Int,
        val tile: Int
    )

    private fun paletteFor(theme: ShareTheme, brandColor: Int): Palette = when (theme) {
        ShareTheme.DEPTH -> Palette(
            top = 0xFF11161F.toInt(),
            bottom = 0xFF0A0D13.toInt(),
            glow = 0x3AA5F3EF.toInt(),
            title = Color.WHITE,
            body = 0xFFE6ECF3.toInt(),
            muted = 0xFF8C96A6.toInt(),
            card = 0x14FFFFFF,
            stroke = 0x1FFFFFFF,
            tile = 0xFFF2F4F7.toInt()
        )

        ShareTheme.CLOUD -> Palette(
            top = 0xFFF7F9FC.toInt(),
            bottom = 0xFFE3EAF3.toInt(),
            glow = 0x33FFFFFF,
            title = 0xFF12161C.toInt(),
            body = 0xFF2A323D.toInt(),
            muted = 0xFF68727F.toInt(),
            card = 0xFFFFFFFF.toInt(),
            stroke = 0x1A0B1A2A,
            tile = 0xFFFFFFFF.toInt()
        )

        ShareTheme.BRAND -> {
            // 品牌色：主色做底、压暗一档做渐变，白字永远清楚
            val deep = darken(brandColor, 0.55f)
            Palette(
                top = brandColor,
                bottom = deep,
                glow = 0x33FFFFFF,
                title = Color.WHITE,
                body = 0xFFF3F7FA.toInt(),
                muted = 0xCCFFFFFF.toInt(),
                card = 0x1FFFFFFF,
                stroke = 0x33FFFFFF,
                tile = 0xFFF2F4F7.toInt()
            )
        }
    }

    private fun drawBackground(canvas: Canvas, paint: Paint, palette: Palette) {
        paint.shader = LinearGradient(
            0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(),
            palette.top, palette.bottom, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), paint)
        paint.shader = RadialGradient(
            CARD_WIDTH * 0.86f, CARD_HEIGHT * 0.08f, CARD_WIDTH * 0.75f,
            intArrayOf(palette.glow, 0x00000000),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), paint)
        paint.shader = null
    }

    private fun drawHeader(canvas: Canvas, paint: Paint, content: Content, palette: Palette) {
        val left = 72f
        var top = 96f

        // 品牌 logo：白色圆角板 + 按比例居中的 logo
        val tileSize = 150f
        val tileRect = RectF(left, top, left + tileSize, top + tileSize)
        paint.color = palette.tile
        canvas.drawRoundRect(tileRect, 34f, 34f, paint)
        content.logo?.let { logo ->
            val inner = tileSize - 52f
            val intrinsicWidth = logo.intrinsicWidth.takeIf { it > 0 } ?: 1
            val intrinsicHeight = logo.intrinsicHeight.takeIf { it > 0 } ?: 1
            val scale = minOf(inner / intrinsicWidth, inner / intrinsicHeight)
            val drawWidth = (intrinsicWidth * scale).roundToInt()
            val drawHeight = (intrinsicHeight * scale).roundToInt()
            val dx = (tileRect.left + (tileSize - drawWidth) / 2f).roundToInt()
            val dy = (tileRect.top + (tileSize - drawHeight) / 2f).roundToInt()
            logo.setBounds(dx, dy, dx + drawWidth, dy + drawHeight)
            logo.draw(canvas)
        }

        // 右侧：品牌标签 + 机型名
        val textLeft = left + tileSize + 36f
        val textWidth = CARD_WIDTH - textLeft - 72f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = palette.muted
        paint.textSize = 30f
        canvas.drawText(fit(content.brandLabel.uppercase(Locale.ROOT), paint, textWidth), textLeft, top + 44f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = palette.title
        paint.textSize = 62f
        canvas.drawText(fit(content.deviceName, paint, textWidth), textLeft, top + 118f, paint)

        // 型号 / 代号
        top += tileSize + 56f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = palette.body
        paint.textSize = 32f
        canvas.drawText(fit(content.modelLine, paint, CARD_WIDTH - left * 2), left, top, paint)
    }

    private fun drawSpecs(canvas: Canvas, paint: Paint, content: Content, palette: Palette) {
        val left = 72f
        val right = CARD_WIDTH - 72f
        val gap = 24f
        val cellWidth = (right - left - gap) / 2f
        val cellHeight = 158f
        val rowGap = 26f
        val firstTop = 392f

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        content.specs.forEachIndexed { index, spec ->
            val column = index % 2
            val row = index / 2
            val cellLeft = left + column * (cellWidth + gap)
            val cellTop = firstTop + row * (cellHeight + rowGap)
            val cellRect = RectF(cellLeft, cellTop, cellLeft + cellWidth, cellTop + cellHeight)

            paint.shader = null
            paint.color = palette.card
            canvas.drawRoundRect(cellRect, 26f, 26f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.4f
            paint.color = palette.stroke
            canvas.drawRoundRect(cellRect, 26f, 26f, paint)
            paint.style = Paint.Style.FILL

            paint.color = palette.muted
            paint.textSize = 27f
            canvas.drawText(spec.label, cellLeft + 30f, cellTop + 54f, paint)

            paint.color = palette.body
            paint.textSize = 36f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(
                fit(spec.value, paint, cellWidth - 60f),
                cellLeft + 30f,
                cellTop + 116f,
                paint
            )
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
    }

    private fun drawFooter(canvas: Canvas, paint: Paint, content: Content, palette: Palette) {
        val left = 72f
        val bottom = CARD_HEIGHT - 84f
        paint.color = palette.stroke
        canvas.drawRect(left, bottom - 74f, CARD_WIDTH - left, bottom - 72.6f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = palette.body
        paint.textSize = 30f
        canvas.drawText(content.footerLeft, left, bottom, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = palette.muted
        paint.textSize = 26f
        val textWidth = paint.measureText(content.footerRight)
        canvas.drawText(content.footerRight, CARD_WIDTH - left - textWidth, bottom, paint)
    }

    /** 量一下宽度，超了就截断加省略号 —— Canvas 不会自己折行。 */
    private fun fit(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    /** 压暗一个颜色（品牌色做渐变底用）。 */
    private fun darken(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).roundToInt().coerceIn(0, 255),
        (Color.green(color) * factor).roundToInt().coerceIn(0, 255),
        (Color.blue(color) * factor).roundToInt().coerceIn(0, 255)
    )

    /** 把矢量 drawable 画成一张位图（测试和分享都用得上）。 */
    fun drawableToBitmap(context: Context, resId: Int, sizePx: Int): Bitmap? {
        val drawable = AppCompatResources.getDrawable(context, resId) ?: return null
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }

    /** 分享卡片里的品牌 logo（没有对应 logo 时返回 null，卡片会用品牌标签顶上）。 */
    fun brandLogo(context: Context, brand: com.rjy.xyz.apps.xyzinfo.data.BrandLogoCatalog.Brand?, sizePx: Int): Drawable? =
        brand?.let { com.rjy.xyz.apps.xyzinfo.data.BrandLogoCatalog.drawable(context, it, sizePx) }

}
