package com.rjy.xyz.apps.xyzinfo.ui.misc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityMiscBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.Anim
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.ThemeColors
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import kotlin.random.Random

/** 杂项工具：反应力小游戏 + 随机密码 + 手电筒。 */
class MiscActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMiscBinding
    private val handler = Handler(Looper.getMainLooper())

    // 0 = 待机，1 = 等待变色，2 = 等待点击
    private var reactionState = 0
    private var readyAt = 0L
    private var bestMillis = 0L

    private var cameraId: String? = null
    private var torchOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMiscBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_NONE)

        setupReaction()
        setupPassword()
        setupTorch()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
        // 离开页面时把状态复位，避免回来时卡在"等待变色"
        if (reactionState == 1) {
            reactionState = 0
            binding.tvReactionResult.setInfoRow("已取消（离开页面）")
        }
    }

    // ---------- 反应力测试 ----------

    private fun setupReaction() {
        binding.tvReactionResult.setInfoRow("最佳成绩：—")
        binding.reactionArea.setOnClickListener {
            when (reactionState) {
                0 -> {
                    reactionState = 1
                    binding.reactionArea.setBackgroundColor(0xFF2A2F38.toInt())
                    binding.tvReactionResult.setInfoRow("状态：等待方块变色…（抢跑作废）")
                    val delay = Random.nextLong(1500L, 4000L)
                    handler.postDelayed({
                        if (reactionState != 1) return@postDelayed
                        reactionState = 2
                        readyAt = SystemClock.elapsedRealtime()
                        binding.reactionArea.setBackgroundColor(ThemeColors.accent(this))
                        binding.tvReactionResult.setInfoRow("状态：就是现在，快点！")
                    }, delay)
                }

                1 -> {
                    handler.removeCallbacksAndMessages(null)
                    reactionState = 0
                    binding.reactionArea.setBackgroundColor(0xFF2A2F38.toInt())
                    binding.tvReactionResult.setInfoRow("抢跑了，这次作废，再点一次重来")
                }

                else -> {
                    val millis = SystemClock.elapsedRealtime() - readyAt
                    reactionState = 0
                    binding.reactionArea.setBackgroundColor(0xFF2A2F38.toInt())
                    if (bestMillis == 0L || millis < bestMillis) bestMillis = millis
                    binding.tvReactionResult.setInfoRow(
                        "本次反应：$millis 毫秒 ｜ 最佳：$bestMillis 毫秒"
                    )
                }
            }
            Anim.pressFeedback(binding.reactionArea)
        }
    }

    // ---------- 随机密码 ----------

    private fun setupPassword() {
        binding.btnGeneratePassword.setOnClickListener {
            Anim.pressFeedback(it)
            val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
            val lower = "abcdefghijkmnopqrstuvwxyz"
            val digits = "23456789"
            val symbols = "!@#$%^&*-_=+"
            val all = upper + lower + digits + symbols
            val password = buildString {
                append(upper[Random.nextInt(upper.length)])
                append(lower[Random.nextInt(lower.length)])
                append(digits[Random.nextInt(digits.length)])
                append(symbols[Random.nextInt(symbols.length)])
                repeat(16) { append(all[Random.nextInt(all.length)]) }
            }.toCharArray().also { it.shuffle() }.concatToString()

            binding.tvPassword.text = password
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("password", password))
            Toast.makeText(this, "已生成并复制到剪贴板", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- 手电筒 ----------

    private fun setupTorch() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        cameraId = runCatching {
            manager?.cameraIdList?.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()

        if (cameraId == null) {
            binding.tvTorchState.text = "这台设备没有可用的闪光灯（或没有相机权限）"
            binding.btnTorch.isEnabled = false
            return
        }
        binding.tvTorchState.text = "闪光灯可用，点按钮开关"
        binding.btnTorch.setOnClickListener {
            Anim.pressFeedback(it)
            val target = !torchOn
            val ok = runCatching {
                manager?.setTorchMode(cameraId!!, target)
            }.isSuccess
            if (ok) {
                torchOn = target
                binding.btnTorch.text = if (torchOn) "关闭手电筒" else "打开手电筒"
                binding.tvTorchState.text = if (torchOn) "手电筒已打开" else "手电筒已关闭"
            } else {
                Toast.makeText(this, "切换失败（可能被其它应用占用）", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 离开时确保手电筒不会被留在打开状态
        if (torchOn) {
            runCatching {
                val manager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                cameraId?.let { manager?.setTorchMode(it, false) }
            }
        }
    }
}
