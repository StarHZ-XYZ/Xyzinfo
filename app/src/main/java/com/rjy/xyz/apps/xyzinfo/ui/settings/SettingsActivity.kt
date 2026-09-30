package com.rjy.xyz.apps.xyzinfo.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import com.rjy.xyz.apps.xyzinfo.databinding.ActivitySettingsBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.Anim
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.UpdateControls
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow

/**
 * 设置页：外观开关（液态玻璃底栏 / 丝滑动画）、跑分模式、机型库更新与关于信息。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_SETTINGS)

        setupAppearance()
        setupBenchmark()
        setupData()
        setupAbout()
    }

    private fun setupAppearance() {
        binding.switchAnimations.isChecked = SettingsRepository.animationsEnabled(this)
        binding.switchAnimations.setOnCheckedChangeListener { _, checked ->
            SettingsRepository.setAnimationsEnabled(this, checked)
            // 粒子层是运行时挂上去的，改完重建页面让开关立刻生效
            recreate()
        }

        binding.switchParticles.isChecked = SettingsRepository.particleEffectEnabled(this)
        binding.switchParticles.setOnCheckedChangeListener { _, checked ->
            SettingsRepository.setParticleEffectEnabled(this, checked)
            recreate()
        }

        // 活体预览：就是首页用的那个底栏组件，点着能直接感受液体指示块
        binding.glassBarPreview.bind(GlassScaffold.tabs(), GlassScaffold.TAB_SETTINGS) { _, _ -> }
        // 预览也要真的磨砂：拿设置页自己的内容当取样源
        binding.glassBarPreview.attachBackdrop(binding.root)
    }

    private fun setupBenchmark() {
        binding.switchDeepBenchmark.isChecked = SettingsRepository.deepBenchmarkEnabled(this)
        binding.switchDeepBenchmark.setOnCheckedChangeListener { _, checked ->
            SettingsRepository.setDeepBenchmarkEnabled(this, checked)
        }

        binding.btnClearBenchmark.setOnClickListener {
            Anim.pressFeedback(it)
            SettingsRepository.clearBenchmark(this)
            Toast.makeText(this, "已清除本机跑分记录，排行榜里不再显示实测行", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupData() {
        UpdateControls.refreshStatus(this, binding.tvDataStatus)
        UpdateControls.attachUpdate(this, binding.btnUpdateData, binding.tvDataStatus)
        UpdateControls.attachReset(this, binding.btnResetData, binding.tvDataStatus)
        Anim.pressFeedback(binding.btnUpdateData, binding.btnResetData, binding.btnClearBenchmark)
    }

    private fun setupAbout() {
        binding.tvAbout.setInfoRow(
            "应用：XYZ-Devinfo 设备检测\n" +
                "版本：0.7\n" +
                "作者：星幻终（RJYZ）\n" +
                "排行榜数据：极客湾（Geekerwan）公开榜单\n" +
                "机型库：Google Play 认证设备清单 + 社区补充\n" +
                "开源协议：MIT"
        )
    }
}
