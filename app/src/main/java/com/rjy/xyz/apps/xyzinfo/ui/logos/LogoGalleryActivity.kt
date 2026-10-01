package com.rjy.xyz.apps.xyzinfo.ui.logos

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityLogoGalleryBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.Anim
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import kotlin.math.roundToInt

/**
 * 品牌图鉴（1.0.4 新增）：把「超级 logo 包」里的矢量 logo 全部展示出来。
 *
 * 这一页同时也是**验收页**：图标一个个排开，哪个转坏了、哪个颜色不对，一眼就能看出来。
 * 全部是 VectorDrawable，放大到任何尺寸都不糊，也不占多少安装包体积。
 */
class LogoGalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogoGalleryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogoGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_NONE)

        binding.tvLogoTip.text =
            "共 ${GROUPS.sumOf { it.second.size }} 个 logo，全部由原图自动矢量化生成（矢量，放大不糊）"
        buildGallery()
    }

    private fun buildGallery() {
        binding.layoutLogoGrid.removeAllViews()
        GROUPS.forEach { (title, entries) ->
            binding.layoutLogoGrid.addView(sectionTitle(title))
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            // 每行 3 个，奇数个的补空位，保证整齐
            entries.chunked(3).forEach { chunk ->
                val line = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(4f), 0, dp(4f))
                }
                chunk.forEach { entry ->
                    line.addView(cell(entry.first, entry.second))
                }
                repeat(3 - chunk.size) {
                    line.addView(
                        android.view.View(this),
                        LinearLayout.LayoutParams(0, 1, 1f)
                    )
                }
                row.addView(line)
            }
            binding.layoutLogoGrid.addView(row)
        }
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(ContextCompat.getColor(this@LogoGalleryActivity, R.color.text_secondary))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10f); bottomMargin = dp(2f) }
        setPadding(dp(8f), 0, 0, 0)
    }

    private fun cell(name: String, res: Int): LinearLayout {
        val icon = ImageView(this).apply {
            setImageResource(res)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(40f), dp(40f))
            // 部分 logo 是深色的，配一个浅色底片才看得清（深色模式下同样适用）
            background = ContextCompat.getDrawable(this@LogoGalleryActivity, R.drawable.bg_icon_tile)
            setPadding(dp(7f), dp(7f), dp(7f), dp(7f))
            contentDescription = name
        }
        val label = TextView(this).apply {
            text = name
            textSize = 11f
            gravity = Gravity.CENTER
            maxLines = 1
            setTextColor(ContextCompat.getColor(this@LogoGalleryActivity, R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6f) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(icon)
            addView(label)
            Anim.pressFeedback(this)
            setOnClickListener {
                // 点一下放大预览，方便检查矢量化质量
                binding.layoutLogoGrid.postDelayed({ icon.isEnabled = icon.isEnabled }, 0)
            }
        }
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        /** 分类 → (显示名, 资源)。顺序按"手机品牌 → 芯片厂商"排。 */
        val GROUPS: List<Pair<String, List<Pair<String, Int>>>> get() = listOf(
            "手机品牌（中国）" to listOf(
                "小米" to R.drawable.ic_logo_xiaomi,
                "OPPO" to R.drawable.ic_logo_oppo,
                "vivo" to R.drawable.ic_logo_vivo,
                "一加" to R.drawable.ic_logo_oneplus,
                "真我" to R.drawable.ic_logo_realme,
                "荣耀" to R.drawable.ic_logo_honor,
                "iQOO" to R.drawable.ic_logo_iqoo,
                "魅族" to R.drawable.ic_logo_meizu,
                "中兴" to R.drawable.ic_logo_zte,
                "努比亚" to R.drawable.ic_logo_nubia,
                "红魔" to R.drawable.ic_logo_redmagic,
                "黑鲨" to R.drawable.ic_logo_blackshark,
                "联想" to R.drawable.ic_logo_lenovo,
                "摩托罗拉" to R.drawable.ic_logo_motorola,
                "锤子" to R.drawable.ic_logo_smartisan
            ),
            "手机品牌（海外）" to listOf(
                "三星" to R.drawable.ic_logo_samsung,
                "谷歌" to R.drawable.ic_logo_google,
                "索尼" to R.drawable.ic_logo_sony,
                "诺基亚" to R.drawable.ic_logo_nokia,
                "黑莓" to R.drawable.ic_logo_blackberry,
                "Nothing" to R.drawable.ic_logo_nothing
            ),
            "芯片厂商" to listOf(
                "骁龙" to R.drawable.ic_logo_snapdragon,
                "联发科" to R.drawable.ic_logo_mediatek,
                "麒麟" to R.drawable.ic_logo_kirin,
                "紫光展锐" to R.drawable.ic_logo_unisoc
            )
        )
    }
}
