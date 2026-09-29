package com.rjy.xyz.apps.xyzinfo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.data.DeviceOverviewProvider
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityMainBinding
import com.rjy.xyz.apps.xyzinfo.model.DeviceOverview
import com.rjy.xyz.apps.xyzinfo.ui.battery.BatteryInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.benchmark.BenchmarkActivity
import com.rjy.xyz.apps.xyzinfo.ui.ram.RamInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.screen.ScreenInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.sensor.SensorInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.soc.SocInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.system.SystemInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.telephony.TelephonyInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import com.rjy.xyz.apps.xyzinfo.util.Labels

/**
 * 首页：展示设备概要，并作为各检测页面的入口。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()

        renderOverview(DeviceOverviewProvider.load())
        setupNavigation()
    }

    private fun renderOverview(overview: DeviceOverview) = with(binding) {
        tvDeviceName.text = overview.displayName
        tvAndroidVersion.setInfoRow("Android 版本：${overview.androidRelease}（API ${overview.apiLevel}）")
        tvRomName.setInfoRow("系统 UI：${overview.romName ?: Labels.NOT_PUBLIC}")
        tvKernelVersion.setInfoRow("Linux 内核：${overview.kernelRelease}")
        tvBrandManufacturer.setInfoRow("品牌：${overview.brand} / ${overview.manufacturer}")
        tvDeviceCode.setInfoRow("设备代号：${overview.deviceCode} / ${overview.product}")
        tvSystemAbi.setInfoRow("系统架构：${overview.abiLabel}")
        tvCpuArch.setInfoRow("CPU架构：${overview.cpuArchitecture}")
    }

    private fun setupNavigation() = with(binding) {
        cardCpu.setOnClickListener { open(SocInfoActivity::class.java) }
        cardMemory.setOnClickListener { open(RamInfoActivity::class.java) }
        cardScreen.setOnClickListener { open(ScreenInfoActivity::class.java) }
        cardBattery.setOnClickListener { open(BatteryInfoActivity::class.java) }
        cardSensor.setOnClickListener { open(SensorInfoActivity::class.java) }
        cardTelephony.setOnClickListener { open(TelephonyInfoActivity::class.java) }
        cardSystem.setOnClickListener { open(SystemInfoActivity::class.java) }
        cardBenchmark.setOnClickListener { open(BenchmarkActivity::class.java) }
    }

    private fun open(screen: Class<out Activity>) {
        startActivity(Intent(this, screen))
    }
}
