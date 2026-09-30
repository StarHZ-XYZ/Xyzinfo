package com.rjy.xyz.apps.xyzinfo.ui.hardware

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityScreenTestBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow

/**
 * 屏幕硬件测试页：坏点 + 触摸。
 *
 * 这一页刻意**不挂底栏**（硬件测试要整屏干净），也保持屏幕常亮。
 */
class ScreenTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScreenTestBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreenTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.screenTestView.onHint = { hint -> binding.tvScreenTestHint.setInfoRow(hint) }
        binding.btnDeadPixel.setOnClickListener {
            binding.screenTestView.mode = ScreenTestView.MODE_DEAD_PIXEL
            binding.tvScreenTestHint.setInfoRow("坏点测试：白（点一下换色）")
        }
        binding.btnTouchTest.setOnClickListener {
            binding.screenTestView.mode = ScreenTestView.MODE_TOUCH
            binding.tvScreenTestHint.setInfoRow("触摸测试：当前 0 点触控")
        }
        binding.btnScreenTestExit.setOnClickListener { finish() }
    }
}
