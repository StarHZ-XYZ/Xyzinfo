package com.rjy.xyz.apps.xyzinfo.ui.share

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.BrandLogoCatalog
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityShareCardBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.Anim
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.ThemeColors
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import java.io.File
import java.io.FileOutputStream

/**
 * 设备信息分享卡片（1.0.5 新增）。
 *
 * 首页「当前设备」卡片右上角的分享按钮进来：看一眼卡片、换个配色、保存或分享。
 * 卡片是本地画的，不走网络，也不会把原始 Build 字段之外的任何东西带出去。
 */
class ShareCardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareCardBinding
    private var theme: ShareTheme = ShareTheme.DEPTH
    private var card: Bitmap? = null
    private var content: ShareCardRenderer.Content? = null
    private var pendingSaveAfterPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareCardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_NONE)

        theme = ShareTheme.DEPTH
        binding.btnSaveCard.setOnClickListener {
            Anim.pressFeedback(it)
            saveToGallery()
        }
        binding.btnShareCard.setOnClickListener {
            Anim.pressFeedback(it)
            shareCard()
        }
        binding.btnCopyCard.setOnClickListener {
            Anim.pressFeedback(it)
            copyAsText()
        }
        buildThemeChips()
        generate()
    }

    private fun generate() {
        val content = ShareCardRenderer.collect(this, appVersion())
        // 品牌 logo 直接塞进卡片内容里（矢量，画到卡片画布上依然清晰）
        val brand = BrandLogoCatalog.find(
            Build.MANUFACTURER,
            Build.BRAND,
            content.deviceName,
            Build.DEVICE,
            Build.MODEL
        )
        val withLogo = content.copy(
            logo = ShareCardRenderer.brandLogo(this, brand, LOGO_PX)
        )
        this.content = withLogo
        val bitmap = ShareCardRenderer.render(this, withLogo, theme, brandColor(brand))
        card?.recycle()
        card = bitmap
        binding.ivCardPreview.setImageDrawable(BitmapDrawable(resources, bitmap))
        binding.tvCardStatus.text =
            "卡片尺寸 ${ShareCardRenderer.CARD_WIDTH}×${ShareCardRenderer.CARD_HEIGHT} · " +
                "${ShareCardRenderer.CARD_WIDTH}px 宽（发到任何平台都够清晰）"
    }

    private fun buildThemeChips() {
        binding.layoutThemeChips.removeAllViews()
        ShareTheme.values().forEach { item ->
            val selected = item == theme
            val chip = TextView(this).apply {
                text = item.label
                textSize = 12.5f
                gravity = Gravity.CENTER
                setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
                setTextColor(
                    if (selected) ThemeColors.accent(this@ShareCardActivity)
                    else ContextCompat.getColor(this@ShareCardActivity, R.color.text_secondary)
                )
                background = ContextCompat.getDrawable(this@ShareCardActivity, R.drawable.bg_chip_filter)
                isSelected = selected
                isClickable = true
                setOnClickListener {
                    if (theme == item) return@setOnClickListener
                    theme = item
                    buildThemeChips()
                    generate()
                }
            }
            chip.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8f) }
            binding.layoutThemeChips.addView(chip)
        }
    }

    /** 品牌色：品牌图鉴里有就用品牌官方色，没有就用应用主色。 */
    private fun brandColor(brand: BrandLogoCatalog.Brand?): Int =
        brand?.color?.toInt() ?: ThemeColors.accent(this)

    private fun appVersion(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.5"
    }.getOrDefault("1.0.5")

    // ---------- 保存 ----------

    private fun saveToGallery() {
        val bitmap = card ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Android 9 及以下要写外部存储的权限，先申请再继续
            pendingSaveAfterPermission = true
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_WRITE
            )
            return
        }
        val name = "XyzInfo-${System.currentTimeMillis()}.png"
        val result = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/XyzInfo")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("无法创建相册条目")
                contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: error("无法写入相册")
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "XyzInfo"
                )
                if (!dir.exists() && !dir.mkdirs()) error("无法创建目录")
                File(dir, name).outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        }
        result.onSuccess {
            Toast.makeText(this, "已保存到相册 · Pictures/XyzInfo", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, "保存失败：${it.message ?: "未知原因"}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_WRITE || !pendingSaveAfterPermission) return
        pendingSaveAfterPermission = false
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            saveToGallery()
        } else {
            Toast.makeText(this, "没有存储权限，没法写入相册", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- 分享 ----------

    private fun shareCard() {
        val bitmap = card ?: return
        val result = runCatching {
            val dir = File(cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "xyzinfo-device-card.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        }
        result.onSuccess { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "我的设备信息 · XYZ-Devinfo 星幻终")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                startActivity(Intent.createChooser(intent, "分享设备卡片"))
            }.onFailure {
                Toast.makeText(this, "没有可用的分享目标", Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(this, "生成分享文件失败：${it.message ?: "未知原因"}", Toast.LENGTH_LONG).show()
        }
    }

    /** 复制成纯文本：发帖 / 让别人帮忙看问题时最省事。 */
    private fun copyAsText() {
        val text = content?.let { ShareCardRenderer.asPlainText(it) } ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            ?: return
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("XYZ-Devinfo 设备信息", text))
        Toast.makeText(this, "设备信息已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        card?.recycle()
        card = null
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQUEST_WRITE = 8801
        const val LOGO_PX = 320
    }
}
