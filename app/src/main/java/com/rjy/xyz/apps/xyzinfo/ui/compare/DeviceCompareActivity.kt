package com.rjy.xyz.apps.xyzinfo.ui.compare

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.rjy.xyz.apps.xyzinfo.data.DeviceSnapshot
import com.rjy.xyz.apps.xyzinfo.data.DeviceSnapshotCollector
import com.rjy.xyz.apps.xyzinfo.data.DeviceSnapshotStore
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityDeviceCompareBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding

/**
 * 设备横向对比（1.1.x 新增）。
 *
 * A 机 = 本机实时采集；B 机 = 从另一台设备导出的快照（文件导入或文本粘贴）。
 * 第二台设备的数据来源走「本机导出 JSON → 另一台导入 / 粘贴」的路线，
 * 复用项目已有的 FileProvider 分享与 org.json 解析，不新增任何依赖。
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
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.1.0"
        }.getOrDefault("1.1.0")
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

        adapter = CompareAdapter()
        binding.rvCompare.layoutManager = LinearLayoutManager(this)
        binding.rvCompare.adapter = adapter
        refresh()
    }

    private fun refresh() {
        adapter.submit(deviceA, deviceB)
        binding.tvDeviceB.text = deviceB?.deviceName ?: "未导入"
        binding.tvEmptyHint.visibility =
            if (deviceB != null) android.view.View.GONE else android.view.View.VISIBLE
    }

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
