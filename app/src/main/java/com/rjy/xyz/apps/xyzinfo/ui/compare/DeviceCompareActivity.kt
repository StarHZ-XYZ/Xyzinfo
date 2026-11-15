package com.rjy.xyz.apps.xyzinfo.ui.compare

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.DeviceSnapshot
import com.rjy.xyz.apps.xyzinfo.data.DeviceCatalog
import com.rjy.xyz.apps.xyzinfo.data.DeviceSnapshotCollector
import com.rjy.xyz.apps.xyzinfo.data.DeviceSnapshotStore
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityDeviceCompareBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.ThemeColors
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import kotlin.math.roundToInt

/**
 * 设备横向对比（1.1.x 新增）。
 *
 * A 机 = 本机实时采集；B 机 = 内置规格库，或另一台设备导出的快照。
 * 快照路线复用项目已有的 FileProvider 分享与 org.json 解析，不新增依赖。
 */
class DeviceCompareActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceCompareBinding
    private lateinit var adapter: CompareAdapter
    private var deviceA: DeviceSnapshot? = null
    private var deviceB: DeviceSnapshot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceCompareBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_NONE)

        val appVersion = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.1.3"
        }.getOrDefault("1.1.3")
        deviceA = DeviceSnapshotCollector.collect(this, appVersion)

        binding.tvDeviceA.text = deviceA?.deviceName ?: "本机"
        binding.btnExportA.setOnClickListener {
            val a = deviceA ?: return@setOnClickListener
            val file = DeviceSnapshotStore.exportFile(this, a)
            DeviceSnapshotStore.shareFile(this, file)
            Toast.makeText(this, "已导出快照，可通过微信 / 蓝牙等发给另一台设备", Toast.LENGTH_LONG).show()
        }
        binding.btnImportFile.setOnClickListener { importFromFile() }
        binding.btnPasteText.setOnClickListener { pasteFromClipboardOrInput() }
        binding.btnBuiltInB.text = "从内置机型库选择（${DeviceCatalog.count(this)} 款）"
        binding.btnBuiltInB.setOnClickListener { showBuiltInPicker() }
        binding.btnCopyComparison.setOnClickListener { copyComparison() }
        binding.btnShareComparison.setOnClickListener { shareComparison() }

        adapter = CompareAdapter()
        binding.rvCompare.layoutManager = LinearLayoutManager(this)
        binding.rvCompare.adapter = adapter
        refresh()
    }

    private fun refresh() {
        adapter.submit(deviceA, deviceB)
        binding.tvDeviceB.text = deviceB?.deviceName ?: "未导入"
        binding.tvEmptyHint.visibility =
            if (deviceB != null) View.GONE else View.VISIBLE
        binding.btnCopyComparison.isEnabled = deviceB != null
        binding.btnShareComparison.isEnabled = deviceB != null
    }

    private fun appVersion(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.1.3"
    }.getOrDefault("1.1.3")

    private fun importFromFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        runCatching {
            startActivityForResult(Intent.createChooser(intent, "选择设备快照"), REQUEST_IMPORT)
        }.onFailure {
            Toast.makeText(this, "没有可用的文件选择器", Toast.LENGTH_SHORT).show()
        }
    }

    /** 本地搜索 160+ 款设备规格，选中即成为 B 机。 */
    private fun showBuiltInPicker() {
        val view = layoutInflater.inflate(R.layout.dialog_device_picker, null)
        val search = view.findViewById<EditText>(R.id.etDeviceSearch)
        val list = view.findViewById<RecyclerView>(R.id.rvDeviceCatalog)
        val count = view.findViewById<TextView>(R.id.tvDeviceCount)
        val empty = view.findViewById<TextView>(R.id.tvNoDevices)
        val brandRow = view.findViewById<LinearLayout>(R.id.layoutCatalogBrands)
        val dialog = AlertDialog.Builder(this)
            .setTitle("机型规格库")
            .setView(view)
            .setNegativeButton("取消", null)
            .create()

        var selectedBrand: String? = null
        val catalogAdapter = DeviceCatalogAdapter { selected ->
            selectBuiltInDevice(selected)
            dialog.dismiss()
            Toast.makeText(this, "已选择：${selected.name}", Toast.LENGTH_SHORT).show()
        }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = catalogAdapter

        fun refreshList() {
            val devices = DeviceCatalog.search(
                this,
                search.text?.toString().orEmpty(),
                selectedBrand
            )
            catalogAdapter.submit(devices)
            list.scrollToPosition(0)
            empty.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
            count.text = "共 ${devices.size} 款 · 按品牌和名称排序\n" +
                "数据来源：${DeviceCatalog.sourceLabel}"
        }

        fun buildBrandChips() {
            brandRow.removeAllViews()
            (listOf<String?>(null) + DeviceCatalog.brands(this)).forEach { brand ->
                val chip = TextView(this).apply {
                    text = brand ?: "全部"
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setPadding(dp(13f), dp(6f), dp(13f), dp(6f))
                    setTextColor(
                        if (selectedBrand == brand) ThemeColors.accent(this@DeviceCompareActivity)
                        else ContextCompat.getColor(this@DeviceCompareActivity, R.color.text_secondary)
                    )
                    background = ContextCompat.getDrawable(
                        this@DeviceCompareActivity,
                        R.drawable.bg_chip_filter
                    )
                    isSelected = selectedBrand == brand
                    isClickable = true
                    setOnClickListener {
                        if (selectedBrand != brand) {
                            selectedBrand = brand
                            buildBrandChips()
                            refreshList()
                        }
                    }
                }
                chip.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8f) }
                brandRow.addView(chip)
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshList()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        buildBrandChips()
        refreshList()
        dialog.setOnShowListener {
            val targetWidth = (resources.displayMetrics.widthPixels * 0.94f)
                .toInt()
                .coerceAtMost((560 * resources.displayMetrics.density).toInt())
            dialog.window?.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

    internal fun selectBuiltInDevice(device: DeviceCatalog.Device) {
        deviceB = device.toSnapshot(appVersion())
        refresh()
    }

    private fun copyComparison() {
        val text = comparisonText() ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            Toast.makeText(this, "系统剪贴板不可用", Toast.LENGTH_SHORT).show()
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("XYZ-Devinfo 设备对比", text))
        Toast.makeText(this, "对比结果已复制", Toast.LENGTH_SHORT).show()
    }

    private fun shareComparison() {
        val text = comparisonText() ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "XYZ-Devinfo 设备对比")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching {
            startActivity(Intent.createChooser(send, "分享设备对比"))
        }.onFailure {
            Toast.makeText(this, "没有可用的分享目标", Toast.LENGTH_SHORT).show()
        }
    }

    private fun comparisonText(): String? {
        val a = deviceA
        val b = deviceB
        if (a == null || b == null) {
            Toast.makeText(this, "请先选择对比机", Toast.LENGTH_SHORT).show()
            return null
        }
        return ComparisonReport.build(a, b)
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).roundToInt()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val text = runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "读取文件失败", Toast.LENGTH_SHORT).show()
            return
        }
        applyImported(text)
    }

    /** 优先直接读剪贴板里的快照；读不到再弹输入框让用户粘贴。 */
    private fun pasteFromClipboardOrInput() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (clip.isNotBlank() && DeviceSnapshotStore.fromJson(clip) != null) {
            applyImported(clip)
            return
        }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            maxLines = 10
            hint = "在此粘贴设备快照 JSON 文本"
        }
        AlertDialog.Builder(this)
            .setTitle("粘贴设备快照")
            .setView(input)
            .setPositiveButton("导入") { _, _ -> applyImported(input.text?.toString().orEmpty()) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun applyImported(text: String) {
        val snap = DeviceSnapshotStore.fromJson(text)
        if (snap == null) {
            Toast.makeText(this, "不是有效的设备快照 JSON", Toast.LENGTH_SHORT).show()
            return
        }
        deviceB = snap
        refresh()
        Toast.makeText(this, "已导入：${snap.deviceName}", Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val REQUEST_IMPORT = 9101
    }
}
