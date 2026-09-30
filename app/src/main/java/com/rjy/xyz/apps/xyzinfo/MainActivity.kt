package com.rjy.xyz.apps.xyzinfo

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.data.DeviceOverviewProvider
import com.rjy.xyz.apps.xyzinfo.data.BrandLogoCatalog
import com.rjy.xyz.apps.xyzinfo.data.DeviceFormDetector
import com.rjy.xyz.apps.xyzinfo.data.DeviceNameRepository
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityMainBinding
import android.view.View
import com.rjy.xyz.apps.xyzinfo.model.DeviceOverview
import com.rjy.xyz.apps.xyzinfo.ui.battery.BatteryInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.benchmark.BenchmarkActivity
import com.rjy.xyz.apps.xyzinfo.ui.benchmark.RankingActivity
import com.rjy.xyz.apps.xyzinfo.ui.gps.GpsInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.settings.SettingsActivity
import com.rjy.xyz.apps.xyzinfo.ui.ram.RamInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.screen.ScreenInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.sensor.SensorInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.soc.SocInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.system.SystemInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.telephony.TelephonyInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.Anim
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.UpdateControls
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import com.rjy.xyz.apps.xyzinfo.util.Labels
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R

/**
 * 首页：展示设备概要，并作为各检测页面的入口。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var firstResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_HOME)

        renderOverview(DeviceOverviewProvider.load())
        setupNavigation()
        loadDeviceName()
        setupUpdate()
        setupCardFeedback()
    }

    /** 从其它页面返回时重新查一次机型名（机型库可能刚更新过）。 */
    override fun onResume() {
        super.onResume()
        if (firstResume) {
            firstResume = false
            return
        }
        loadDeviceName()
    }

    /** 卡片按压反馈（入场动画由 GlassScaffold 统一负责）。 */
    private fun setupCardFeedback() {
        Anim.pressFeedback(
            binding.cardCpu, binding.cardMemory, binding.cardScreen, binding.cardBattery,
            binding.cardSensor, binding.cardTelephony, binding.cardSystem, binding.cardGps,
            binding.btnUpdateDeviceNames
        )
    }

    /** 机型库更新入口：状态展示 + 更新 + 恢复内置（与设置页共用同一套逻辑）。 */
    private fun setupUpdate() {
        UpdateControls.refreshStatus(this, binding.tvUpdateStatus)
        UpdateControls.attachUpdate(this, binding.btnUpdateDeviceNames, binding.tvUpdateStatus) {
            // 更新成功后立刻用新库重查一次机型名
            loadDeviceName()
        }
    }

    /**
     * 用内置机型库把设备代号 / 型号翻译成上市机型名（如「小米 Civi 1S」）。
     *
     * 映射库约 420KB 压缩数据，解析放在后台线程，读完再刷新标题。
     */
    private fun loadDeviceName() {
        Thread({
            val name = DeviceNameRepository.lookup(this, Build.DEVICE, Build.MODEL)
            // 品牌徽标：不依赖机型库是否命中，用 Build 字段也能认出来
            val brand = BrandLogoCatalog.find(
                Build.MANUFACTURER, Build.BRAND, name, Build.DEVICE, Build.MODEL
            )
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (name != null) {
                    binding.tvDeviceName.text = name
                    binding.tvDeviceName.setTextColor(brandColor(name))
                }
                applyBrandBadge(brand)
            }
        }, "device-name-lookup").start()
    }

    /** 把品牌徽章贴在机型名左边（compound drawable，不额外占一行）。 */
    private fun applyBrandBadge(brand: BrandLogoCatalog.Brand?) {
        if (brand == null) {
            binding.tvDeviceName.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            return
        }
        val density = resources.displayMetrics.density
        val size = (22 * density).toInt()
        binding.tvDeviceName.setCompoundDrawablesWithIntrinsicBounds(
            BrandLogoCatalog.drawable(this, brand, size), null, null, null
        )
        binding.tvDeviceName.compoundDrawablePadding = (10 * density).toInt()
    }

    /** 主流品牌官方色（中英文都覆盖），让首页机型名一眼可辨品牌。 */
    private fun brandColor(displayName: String): Int {
        val brand = displayName.substringBefore(' ').trim()
        val color = BRAND_COLORS[brand] ?: BRAND_COLORS[brand.lowercase()]
        return color ?: ContextCompat.getColor(this, R.color.text_primary)
    }

    private companion object {
        val BRAND_COLORS = mapOf(
            // 中文品牌名（机型库本地化后的写法）
            "小米" to 0xFFFF6900.toInt(), "红米" to 0xFFFF6900.toInt(),
            "三星" to 0xFF1428A0.toInt(), "华为" to 0xFFCF0A2C.toInt(),
            "荣耀" to 0xFF0A84FF.toInt(), "一加" to 0xFFEB0028.toInt(),
            "真我" to 0xFFD8A200.toInt(), "谷歌" to 0xFF4285F4.toInt(),
            "魅族" to 0xFF00A9E0.toInt(), "中兴" to 0xFF0066B3.toInt(),
            "努比亚" to 0xFFE4002B.toInt(), "联想" to 0xFFE2231A.toInt(),
            "索尼" to 0xFF6A6A6A.toInt(), "摩托罗拉" to 0xFF5C92FA.toInt(),
            "诺基亚" to 0xFF124191.toInt(), "华硕" to 0xFF00539B.toInt(),
            "黑鲨" to 0xFF00C8FF.toInt(), "锤子" to 0xFF8C8C8C.toInt(),
            "夏普" to 0xFFE60012.toInt(), "海信" to 0xFF1D7A46.toInt(),
            // 英文原名兜底
            "Xiaomi" to 0xFFFF6900.toInt(), "Redmi" to 0xFFFF6900.toInt(),
            "Samsung" to 0xFF1428A0.toInt(), "HUAWEI" to 0xFFCF0A2C.toInt(),
            "HONOR" to 0xFF0A84FF.toInt(), "OnePlus" to 0xFFEB0028.toInt(),
            "realme" to 0xFFD8A200.toInt(), "Google" to 0xFF4285F4.toInt(),
            "Meizu" to 0xFF00A9E0.toInt(), "ZTE" to 0xFF0066B3.toInt(),
            "nubia" to 0xFFE4002B.toInt(), "Lenovo" to 0xFFE2231A.toInt(),
            "Sony" to 0xFF6A6A6A.toInt(), "Motorola" to 0xFF5C92FA.toInt(),
            "Nokia" to 0xFF124191.toInt(), "ASUS" to 0xFF00539B.toInt(),
            "POCO" to 0xFFFFC400.toInt(), "OPPO" to 0xFF1C9E4C.toInt(),
            "vivo" to 0xFF415FFF.toInt(), "iQOO" to 0xFF415FFF.toInt()
        )
    }

    private fun renderOverview(overview: DeviceOverview) = with(binding) {
        tvDeviceName.text = overview.displayName
        tvDeviceRawModel.text =
            "原始型号：${overview.rawModel} ｜ 设备代号：${overview.deviceCode}"
        tvAndroidVersion.setInfoRow("Android 版本：${overview.androidRelease}（API ${overview.apiLevel}）")
        tvRomName.setInfoRow("系统 UI：${overview.romName ?: Labels.NOT_PUBLIC}")
        tvKernelVersion.setInfoRow("Linux 内核：${overview.kernelRelease}")
        tvBrandManufacturer.setInfoRow("品牌：${overview.brand} / ${overview.manufacturer}")
        tvDeviceCode.setInfoRow("设备代号：${overview.deviceCode} / ${overview.product}")
        // 一并显示设备形态：手机 / 平板 / 折叠屏（含展开折叠状态）
        val form = DeviceFormDetector.detect(this@MainActivity)
        tvSystemAbi.setInfoRow("系统架构：${overview.abiLabel} ｜ 设备形态：${form.description}")
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
        cardGps.setOnClickListener { open(GpsInfoActivity::class.java) }
    }

    private fun open(screen: Class<out Activity>) {
        startActivity(Intent(this, screen))
    }
}
