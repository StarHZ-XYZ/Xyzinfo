package com.rjy.xyz.apps.xyzinfo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.min
import com.rjy.xyz.apps.xyzinfo.R

/**
 * 品牌徽标目录 + 徽章渲染。
 *
 * 为什么用「徽章」而不是逐个画官方 logo：全世界手机品牌几百个，手绘矢量既不现实，
 * 也涉及商标；所以这里给每个品牌定义**官方主色 + 缩写**，用统一形状渲染成徽章
 * （圆角方块 + 品牌色渐变 + 白/黑缩写），一眼能认出品牌，风格也统一。
 * 以后想给某个品牌换真 logo，只要在 [BRANDS] 里补一个 drawable 字段即可。
 */
object BrandLogoCatalog {

    data class Brand(
        /** 匹配用的别名（全部小写），命中 Build.MANUFACTURER / BRAND / 机型名任意一个即可。 */
        val aliases: List<String>,
        /** 中文名（用于界面展示）。 */
        val displayName: String,
        /** 徽章上的缩写，1~3 个字符。 */
        val badge: String,
        /** 品牌主色（写成 Long 省得每个字面量都加 .toInt()）。 */
        val color: Long,
        /**
         * 真正的品牌 logo（1.0.4 起随「超级 logo 包」内置，由用户提供的原图自动矢量化生成）。
         * 为 0 时退回 [badge] 文字徽章 —— 品牌表里几百个品牌不可能人人都有矢量图，
         * 有 logo 的用 logo、没有的用统一风格徽章，观感才一致。
         */
        val logoRes: Int = 0,
        /**
         * logo 自带的衬底：0 = 不需要，1 = 垫一层浅色板（logo 本身近黑），
         * 2 = 垫一层深色板（logo 本身接近纯白）。
         *
         * 为什么需要：OPPO / 索尼 / 荣耀的 logo 是纯黑字标，深色模式下会糊在背景里；
         * Nothing / vivo / 黑莓是白色字标，浅色卡片上直接看不见。
         * 与其强行给 logo 改色（品牌色就没了），不如垫一块对比色底板 —— 既保住原色，
         * 两种主题下都清清楚楚，观感也更像一枚"图标"。
         */
        val logoTile: Int = 0
    )

    /**
     * 品牌表：按「中国 / 韩日 / 欧美 / 其它地区」大致排列。
     * 别名越具体越长越靠前，匹配时取最长命中的那条，避免「huawei」被「hua」抢走。
     */
    val BRANDS: List<Brand> = listOf(
        // ── 中国 ──
        Brand(listOf("xiaomi", "mi ", "redmi", "小米"), "小米", "MI", 0xFFFF6900, R.drawable.ic_logo_xiaomi),
        Brand(listOf("redmi", "红米"), "红米", "RD", 0xFFFF6900, R.drawable.ic_logo_xiaomi),
        Brand(listOf("poco"), "POCO", "PC", 0xFFFFC400),
        Brand(listOf("huawei", "华为", "hwd"), "华为", "HW", 0xFFCF0A2C),
        Brand(listOf("honor", "荣耀"), "荣耀", "HN", 0xFF0A84FF, R.drawable.ic_logo_honor, 1),
        Brand(listOf("oppo", "欧珀"), "OPPO", "OP", 0xFF1C9E4C, R.drawable.ic_logo_oppo, 1),
        Brand(listOf("oneplus", "一加"), "一加", "1+", 0xFFEB0028, R.drawable.ic_logo_oneplus),
        Brand(listOf("realme", "真我"), "真我", "RM", 0xFFD8A200, R.drawable.ic_logo_realme),
        Brand(listOf("vivo", "维沃"), "vivo", "VV", 0xFF415FFF, R.drawable.ic_logo_vivo, 2),
        Brand(listOf("iqoo"), "iQOO", "iQ", 0xFF415FFF, R.drawable.ic_logo_iqoo),
        Brand(listOf("meizu", "魅族"), "魅族", "MZ", 0xFF00A9E0, R.drawable.ic_logo_meizu),
        Brand(listOf("zte", "中兴"), "中兴", "ZT", 0xFF0066B3, R.drawable.ic_logo_zte),
        Brand(listOf("nubia", "努比亚"), "努比亚", "NB", 0xFFE4002B, R.drawable.ic_logo_nubia),
        Brand(listOf("red magic", "redmagic", "红魔"), "红魔", "RM", 0xFFE4002B, R.drawable.ic_logo_redmagic),
        Brand(listOf("lenovo", "联想"), "联想", "LN", 0xFFE2231A, R.drawable.ic_logo_lenovo),
        Brand(listOf("motorola", "moto", "摩托罗拉"), "摩托罗拉", "MO", 0xFF5C92FA, R.drawable.ic_logo_motorola),
        Brand(listOf("legion", "拯救者"), "拯救者", "LG", 0xFFE2231A),
        Brand(listOf("smartisan", "锤子", "坚果"), "锤子", "SM", 0xFF8C8C8C, R.drawable.ic_logo_smartisan),
        Brand(listOf("gionee", "金立"), "金立", "GN", 0xFFD2232A),
        Brand(listOf("coolpad", "酷派"), "酷派", "CP", 0xFF2A6DBB),
        Brand(listOf("letv", "leeco", "乐视"), "乐视", "LE", 0xFFE2231A),
        Brand(listOf("qiku", "360"), "360", "360", 0xFF0E9F76),
        Brand(listOf("meitu", "美图"), "美图", "MT", 0xFFE65C9C),
        Brand(listOf("gree", "格力"), "格力", "GR", 0xFF1B77C8),
        Brand(listOf("hisense", "海信"), "海信", "HS", 0xFF1D7A46),
        Brand(listOf("tcl", "阿尔卡特", "alcatel"), "TCL", "TCL", 0xFFE4002B),
        Brand(listOf("blackshark", "黑鲨"), "黑鲨", "BS", 0xFF00C8FF, R.drawable.ic_logo_blackshark),
        Brand(listOf("tecno", "传音"), "传音 Tecno", "TC", 0xFF0067B1),
        Brand(listOf("infinix"), "传音 Infinix", "IN", 0xFF0A84FF),
        Brand(listOf("itel"), "传音 itel", "IT", 0xFF00A0E9),
        Brand(listOf("doov", "朵唯"), "朵唯", "DW", 0xFFB0308A),
        Brand(listOf("k-touch", "天语"), "天语", "KT", 0xFFE2231A),
        Brand(listOf("doogee", "道格"), "道格", "DG", 0xFF37474F),
        Brand(listOf("blackview"), "Blackview", "BV", 0xFF1A1A1A),
        Brand(listOf("ulefone"), "Ulefone", "UF", 0xFFE8622C),
        Brand(listOf("oukitel"), "Oukitel", "OK", 0xFFF5A623),
        Brand(listOf("umidigi"), "UMIDIGI", "UM", 0xFF2E7D32),
        Brand(listOf("cubot"), "Cubot", "CB", 0xFF00897B),
        Brand(listOf("agm"), "AGM", "AGM", 0xFF455A64),
        Brand(listOf("cmcc", "中国移动"), "中国移动", "CM", 0xFF0072BC),
        Brand(listOf("bbk", "步步高"), "步步高", "BBK", 0xFF1B5E20),
        Brand(listOf("xiaotiancai", "小天才"), "小天才", "XTC", 0xFF00A0E9),
        Brand(listOf("hinotech", "hi-nova"), "HI", "HI", 0xFF6A1B9A),
        Brand(listOf("lephone", "百立丰"), "百立丰", "LP", 0xFF0277BD),
        Brand(listOf("kingsun"), "金太阳", "KS", 0xFFEF6C00),
        Brand(listOf("vivo iqoo"), "iQOO", "iQ", 0xFF415FFF),

        // ── 韩日 ──
        Brand(listOf("samsung", "三星"), "三星", "SS", 0xFF1428A0, R.drawable.ic_logo_samsung),
        Brand(listOf("lg electronics", "lge", "lg-"), "LG", "LG", 0xFFA50034),
        Brand(listOf("sony", "索尼"), "索尼", "SN", 0xFF1A1A1A, R.drawable.ic_logo_sony, 1),
        Brand(listOf("sharp", "夏普"), "夏普", "SH", 0xFFE60012),
        Brand(listOf("kyocera", "京瓷"), "京瓷", "KY", 0xFFD32F2F),
        Brand(listOf("fujitsu", "富士通"), "富士通", "FJ", 0xFFD50000),
        Brand(listOf("panasonic", "松下"), "松下", "PA", 0xFF0B4EA2),
        Brand(listOf("nec"), "NEC", "NEC", 0xFF1414A0),
        Brand(listOf("casio", "卡西欧"), "卡西欧", "CA", 0xFF1565C0),
        Brand(listOf("rakuten", "乐天"), "乐天", "RK", 0xFFBF0000),

        // ── 欧美 ──
        Brand(listOf("apple", "苹果", "iphone"), "苹果", "AP", 0xFF333333),
        Brand(listOf("google"), "谷歌", "GO", 0xFF4285F4, R.drawable.ic_logo_google),
        Brand(listOf("nokia", "诺基亚"), "诺基亚", "NK", 0xFF124191, R.drawable.ic_logo_nokia),
        Brand(listOf("htc", "宏达电"), "HTC", "HTC", 0xFF0F9D58),
        Brand(listOf("blackberry", "rim", "黑莓"), "黑莓", "BB", 0xFF1A1A1A, R.drawable.ic_logo_blackberry, 2),
        Brand(listOf("nvidia"), "NVIDIA", "NV", 0xFF76B900),
        Brand(listOf("amazon"), "Amazon", "AZ", 0xFFFF9900),
        Brand(listOf("microsoft", "surface"), "微软", "MS", 0xFF0078D4),
        Brand(listOf("siemens", "西门子"), "西门子", "SI", 0xFF009999),
        Brand(listOf("philips", "飞利浦"), "飞利浦", "PH", 0xFF0B5FA5),
        Brand(listOf("ericsson", "爱立信"), "爱立信", "ER", 0xFF0082F0),
        Brand(listOf("vertu", "威图"), "威图", "VT", 0xFF1A1A1A),
        Brand(listOf("gigaset", "金阶"), "Gigaset", "GI", 0xFFE2001A),
        Brand(listOf("archos"), "Archos", "AR", 0xFF7B1FA2),
        Brand(listOf("wiko"), "Wiko", "WK", 0xFF00A3E0),
        Brand(listOf("crosscall"), "Crosscall", "CC", 0xFF37474F),
        Brand(listOf("bittium"), "Bittium", "BT", 0xFF00457C),
        Brand(listOf("sonim"), "Sonim", "SO", 0xFFFF6F00),
        Brand(listOf("bullitt", "cat ", "caterpillar"), "CAT", "CAT", 0xFFFFC107),
        Brand(listOf("fairphone"), "Fairphone", "FP", 0xFF00A0AA),
        Brand(listOf("punkt"), "Punkt", "PU", 0xFF607D8B),

        // ── 中国台湾 ──
        Brand(listOf("asus", "华硕", "zenfone", "rog"), "华硕", "AG", 0xFF00539B),
        Brand(listOf("acer", "宏碁"), "宏碁", "AC", 0xFF80BB00),
        Brand(listOf("gigabyte", "技嘉"), "技嘉", "GB", 0xFFE2231A),
        Brand(listOf("msi", "微星"), "微星", "MSI", 0xFFE2231A),
        Brand(listOf("benq", "明基"), "明基", "BQ", 0xFF7A1FA2),
        Brand(listOf("infocus"), "InFocus", "IF", 0xFF00838F),

        // ── 印度 / 东南亚 / 其它 ──
        Brand(listOf("lava"), "Lava", "LV", 0xFFE64A19),
        Brand(listOf("micromax"), "Micromax", "MM", 0xFFD32F2F),
        Brand(listOf("karbonn"), "Karbonn", "KB", 0xFF0097A7),
        Brand(listOf("intex"), "Intex", "IX", 0xFF1976D2),
        Brand(listOf("spice"), "Spice", "SP", 0xFFEF6C00),
        Brand(listOf("jio", "reliance"), "Jio", "JIO", 0xFF0A2885),
        Brand(listOf("smartfren"), "Smartfren", "SF", 0xFFE4002B),
        Brand(listOf("advan"), "Advan", "AD", 0xFF0D47A1),
        Brand(listOf("polytron"), "Polytron", "PT", 0xFF0277BD),
        Brand(listOf("mytel", "viettel"), "Viettel", "VT", 0xFFE2231A),
        Brand(listOf("mobell"), "Mobell", "MB", 0xFF00695C),
        Brand(listOf("bq"), "BQ", "BQ", 0xFF2E7D32),
        Brand(listOf("cubot", "ulefone"), "CUBOT", "CU", 0xFF00897B),
        Brand(listOf("meizu pro"), "魅族", "MZ", 0xFF00A9E0),
        Brand(listOf("nuu"), "NUU", "NUU", 0xFF37474F),
        Brand(listOf("vernee"), "Vernee", "VE", 0xFF00695C),
        Brand(listOf("elephone"), "Elephone", "EL", 0xFF0277BD),
        Brand(listOf("leagoo"), "Leagoo", "LG", 0xFF455A64),
        Brand(listOf("blu"), "BLU", "BLU", 0xFF01579B),
        Brand(listOf("verykool"), "verykool", "VK", 0xFF7B1FA2),
        Brand(listOf("condor"), "Condor", "CD", 0xFF00695C),
        Brand(listOf("innjoo"), "Innjoo", "IJ", 0xFF455A64),
        Brand(listOf("iko"), "IKO", "IKO", 0xFF00838F),
        Brand(listOf("ginzzu"), "Ginzzu", "GZ", 0xFF1565C0),
        Brand(listOf("digma"), "Digma", "DM", 0xFF37474F),
        Brand(listOf("prestigio"), "Prestigio", "PR", 0xFF0D47A1),
        Brand(listOf("allview"), "Allview", "AV", 0xFF01579B),
        Brand(listOf("vim"), "VIM", "VIM", 0xFF00897B),
        Brand(listOf("own", "sico"), "OWN", "OW", 0xFFEF6C00),
        Brand(listOf("axioo"), "Axioo", "AX", 0xFF0277BD),
        Brand(listOf("evercoss"), "Evercoss", "EC", 0xFFD32F2F),
        Brand(listOf("mito"), "MITO", "MI", 0xFF29B6F6),
        Brand(listOf("pixel", "google pixel"), "Pixel", "PX", 0xFF4285F4),
        Brand(listOf("nothing", "nothing phone"), "Nothing", "NO", 0xFF1A1A1A, R.drawable.ic_logo_nothing, 2)
    )

    /**
     * 找品牌：把待匹配文本转小写后找最长命中的别名。
     *
     * @param candidates 依次尝试 Build.MANUFACTURER、Build.BRAND、机型名、设备代号
     */
    fun find(vararg candidates: String?): Brand? {
        val normalized = candidates.filterNotNull().joinToString(" ") { it.lowercase() }
        if (normalized.isBlank()) return null
        var best: Brand? = null
        var bestLength = 0
        BRANDS.forEach { brand ->
            brand.aliases.forEach { alias ->
                if (alias.length > bestLength && normalized.contains(alias)) {
                    best = brand
                    bestLength = alias.length
                }
            }
        }
        return best
    }

    /**
     * 把品牌渲染成徽章 drawable（圆角方块 + 品牌色 + 缩写）。
     *
     * @param sizePx 期望边长（会按屏幕密度取整）
     */
    fun drawable(context: Context, brand: Brand, sizePx: Int): Drawable {
        /*
         * 有真 logo 就直接用（矢量，任意尺寸都清晰）；
         * 没有的仍然走下面的文字徽章 —— 两三百个品牌不可能都有图，
         * 混着用时"有图用图、没图用徽章"看起来是统一的，不会一半图一半字。
         */
        if (brand.logoRes != 0) {
            val logo = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, brand.logoRes)
            if (logo != null) {
                if (brand.logoTile == 0) {
                    logo.setBounds(0, 0, sizePx, sizePx)
                    return logo
                }
                // 近黑 / 近白的单色 logo：垫一块对比色底板，两种主题下都看得清
                return tiledLogo(context, logo, sizePx, brand.logoTile == 2)
            }
        }
        val size = sizePx.coerceAtLeast(16)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        val radius = size * 0.28f

        // 品牌色 → 稍暗的同色系渐变，避免整块死板
        val baseColor = brand.color.toInt()
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            baseColor,
            darken(baseColor, 0.78f),
            android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null

        // 顶部一道高光，做出徽章厚度
        paint.color = 0x33FFFFFF
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size * 0.42f), radius, radius, paint)

        // 缩写：按对比度选黑或白
        paint.color = if (luminance(baseColor) > 0.6f) 0xFF11151A.toInt() else Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = when (brand.badge.length) {
            1 -> size * 0.52f
            2 -> size * 0.42f
            else -> size * 0.30f
        }
        val metrics = paint.fontMetrics
        val baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(brand.badge, size / 2f, baseline, paint)

        return BitmapDrawable(context.resources, bitmap)
    }

    /**
     * 给 logo 垫一块圆角底板（[darkTile] = true 用深色板配浅色 logo）。
     *
     * 用 LayerDrawable 而不是先画成位图：矢量 logo 直接按目标尺寸绘制，
     * 放大到任何尺寸都是清晰的；底板也只是个 shape drawable，几乎不占内存。
     */
    private fun tiledLogo(context: Context, logo: Drawable, sizePx: Int, darkTile: Boolean): Drawable {
        val tileColor = if (darkTile) 0xFF23272E.toInt() else 0xFFFFFFFF.toInt()
        val strokeColor = if (darkTile) 0xFF3A4048.toInt() else 0xFFD8DEE7.toInt()
        val tile = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = sizePx * 0.30f
            setColor(tileColor)
            setStroke((sizePx * 0.045f).toInt().coerceAtLeast(1), strokeColor)
        }
        val inset = (sizePx * 0.18f).toInt()
        logo.setBounds(inset, inset, sizePx - inset, sizePx - inset)
        return android.graphics.drawable.LayerDrawable(arrayOf(tile, logo)).apply {
            setBounds(0, 0, sizePx, sizePx)
        }
    }

    private fun darken(color: Int, factor: Float): Int {
        val r = ((color shr 16 and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = ((color shr 8 and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun luminance(color: Int): Float {
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    @Suppress("unused")
    private fun clamp(value: Int, max: Int) = min(value, max)
}
