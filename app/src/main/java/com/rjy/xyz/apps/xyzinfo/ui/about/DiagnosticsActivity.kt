package com.rjy.xyz.apps.xyzinfo.ui.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.data.CrashRecovery
import com.rjy.xyz.apps.xyzinfo.data.DiagnosticCenter
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityDiagnosticsBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.Anim
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding

/** 隐藏诊断中心：复制健康报告、开启轻量模式、清理可再生成的缓存。 */
class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiagnosticsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_NONE)

        binding.btnCopyDiagnostics.setOnClickListener {
            Anim.pressFeedback(it)
            copyReport()
        }
        val formButtons = mapOf(
            "auto" to binding.btnFormAuto,
            "foldable" to binding.btnFormFoldable,
            "tablet" to binding.btnFormTablet,
            "phone" to binding.btnFormPhone
        )
        binding.groupFormOverride.check(
            formButtons[SettingsRepository.deviceFormOverride(this)]?.id ?: binding.btnFormAuto.id
        )
        binding.groupFormOverride.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val value = formButtons.entries.firstOrNull { it.value.id == checkedId }?.key ?: "auto"
            SettingsRepository.setDeviceFormOverride(this, value)
            render()
        }
        binding.btnToggleLightweight.setOnClickListener {
            Anim.pressFeedback(it)
            val enabled = !SettingsRepository.lightweightMode(this)
            SettingsRepository.setLightweightMode(this, enabled)
            Toast.makeText(
                this,
                if (enabled) "轻量稳定模式已开启" else "轻量稳定模式已关闭",
                Toast.LENGTH_SHORT
            ).show()
            render()
        }
        binding.btnRepairTransient.setOnClickListener {
            Anim.pressFeedback(it)
            confirmRepair()
        }
        binding.btnClearCrashReports.setOnClickListener {
            Anim.pressFeedback(it)
            CrashRecovery.clear(this)
            render()
            Toast.makeText(this, "崩溃记录已清除", Toast.LENGTH_SHORT).show()
        }
        render()
    }

    private fun render() {
        binding.tvDiagnosticsReport.text = DiagnosticCenter.report(this)
        binding.btnToggleLightweight.text =
            if (SettingsRepository.lightweightMode(this)) {
                "关闭轻量稳定模式"
            } else {
                "开启轻量稳定模式（关闭重特效与实时模糊）"
            }
    }

    private fun copyReport() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("XYZ-Devinfo 诊断报告", DiagnosticCenter.report(this)))
        Toast.makeText(this, "诊断报告已复制", Toast.LENGTH_SHORT).show()
    }

    private fun confirmRepair() {
        AlertDialog.Builder(this)
            .setTitle("清理临时缓存")
            .setMessage("只删除可重新生成的缓存和排行榜渲染缓存，不会删除设置、电池记录或收藏数据。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清理") { _, _ ->
                binding.btnRepairTransient.isEnabled = false
                Thread({
                    val result = DiagnosticCenter.repairTransient(this)
                    runOnUiThread {
                        if (isFinishing) return@runOnUiThread
                        binding.btnRepairTransient.isEnabled = true
                        render()
                        Toast.makeText(
                            this,
                            "已清理 ${result.removedFiles} 个缓存项，释放 ${result.sizeLabel}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }, "xyzinfo-diagnostics-repair").start()
            }
            .show()
    }
}
