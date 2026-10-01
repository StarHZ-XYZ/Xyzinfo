package com.rjy.xyz.apps.xyzinfo

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.data.DeviceOverviewProvider
import com.rjy.xyz.apps.xyzinfo.data.BrandLogoCatalog
import com.rjy.xyz.apps.xyzinfo.data.DeviceFormDetector
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import com.rjy.xyz.apps.xyzinfo.data.DeviceNameRepository
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityMainBinding
import android.view.View
import com.rjy.xyz.apps.xyzinfo.model.DeviceOverview
import com.rjy.xyz.apps.xyzinfo.ui.battery.BatteryInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.benchmark.BenchmarkActivity
import com.rjy.xyz.apps.xyzinfo.ui.benchmark.RankingActivity
import com.rjy.xyz.apps.xyzinfo.ui.gps.GpsInfoActivity
import com.rjy.xyz.apps.xyzinfo.ui.hardware.ScreenTestActivity
import com.rjy.xyz.apps.xyzinfo.ui.hardware.HardwareMoreActivity
import com.rjy.xyz.apps.xyzinfo.ui.misc.MiscActivity
import com.rjy.xyz.apps.xyzinfo.ui.env.EnvironmentCheckActivity
import com.rjy.xyz.apps.xyzinfo.ui.inspect.DeviceInspectActivity
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
import com.rjy.xyz.apps.xyzinfo.ui.common.ThemeColors
import com.rjy.xyz.apps.xyzinfo.util.Labels
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R

/**
 * 首页：展示设备概要，并作为各检测页面的入口。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var firstResume = true
    /** 代码生成的硬件测试入口卡片，宫格排版时要一起排。 */
    /** 代码生成的入口卡片列表（硬件测试、杂项），宫格排版要一起排。 */
    private val extraCards = mutableListOf<android.view.View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_HOME)

        renderOverview(DeviceOverviewProvider.load())
        setupNavigation()
        addExtraEntries()
        applyHomeLayoutStyle()
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
        tvAndroidVersion.setInfoRow(
            "Android 版本：${overview.androidRelease}（API ${overview.apiLevel}）",
            iconRes = R.drawable.ic_module_system
        )
        // 一并显示设备形态：手机 / 平板 / 折叠屏（含展开折叠状态）
        val form = DeviceFormDetector.detect(this@MainActivity)
        tvRomName.setInfoRow(
            "系统 UI：${overview.romName ?: Labels.NOT_PUBLIC} ｜ 形态：${form.form.label}"
        )
        tvKernelVersion.setInfoRow(
            "内核：${overview.kernelRelease} ｜ 架构：${overview.abiLabel}",
            iconRes = R.drawable.ic_module_cpu
        )
        tvBrandManufacturer.setInfoRow(
            "品牌：${overview.brand}" +
                if (overview.manufacturer.isNotBlank() && overview.manufacturer != overview.brand) {
                    " / ${overview.manufacturer}"
                } else {
                    ""
                },
            iconRes = R.drawable.ic_module_telephony
        )
        // 顶部信息收敛：设备代号已并入「原始型号」那一行，CPU 架构并入内核那一行，
        // 这两行不再单独占位，避免首页一上来就是一大串等宽字段。
        tvDeviceCode.visibility = android.view.View.GONE
        tvSystemAbi.visibility = android.view.View.GONE
        tvCpuArch.visibility = android.view.View.GONE
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

    /**
     * 用代码生成「硬件测试」入口卡片（避免再复制一大段布局 XML）。
     * 它在 applyHomeLayoutStyle 之前插入，所以宫格模式下也会一起进两列排布。
     */
    /** 新增两个入口：硬件测试、杂项工具。 */
    private fun addExtraEntries() {
        // 硬件测试合并成一个入口：屏幕 + 音频 + 传感器，都在同一个页面里
        addEntryCard(
            "硬件测试",
            "屏幕坏点 / 触摸 / 扬声器 / 麦克风 / 振动 / 摄像头 / NFC",
            R.drawable.ic_module_screen
        ) {
            open(HardwareMoreActivity::class.java)
        }
        addEntryCard("杂项工具", "反应力测试、随机密码、手电筒", R.drawable.ic_module_benchmark) {
            open(MiscActivity::class.java)
        }
        addEntryCard(
            "环境检测",
            "root 与风险环境痕迹（分级 + 证据）",
            R.drawable.ic_module_system
        ) {
            open(EnvironmentCheckActivity::class.java)
        }
        addEntryCard(
            "大肥鱼验机",
            "汇总全部检测项，一条结论 + 逐条证据",
            R.drawable.ic_deepseek_fish
        ) {
            open(DeviceInspectActivity::class.java)
        }
        addEntryCard(
            "温度监控",
            "各热区实时温度 + 长期记录与导出",
            R.drawable.ic_module_sensor
        ) {
            open(com.rjy.xyz.apps.xyzinfo.ui.thermal.ThermalActivity::class.java)
        }
        addEntryCard(
            "网络测速",
            "多台公共节点，自动就近 + 并行多线程，单位可切 Mbps / MB/s",
            R.drawable.ic_module_network
        ) {
            open(com.rjy.xyz.apps.xyzinfo.ui.network.NetworkSpeedActivity::class.java)
        }
    }

    /**
     * 生成一张与 XML 里**完全同款**的入口卡片：
     * 42dp 图标底板（bg_icon_tile）+ 标题（主要色加粗）+ 说明（次要色）+ 右箭头。
     */
    private fun addEntryCard(
        cardTitle: String,
        cardSubtitle: String,
        iconRes: Int,
        onClick: () -> Unit
    ) {
        val parent = binding.cardGps.parent as? android.view.ViewGroup ?: return
        val density = resources.displayMetrics.density
        val title = android.widget.TextView(this).apply {
            text = cardTitle
            setTextColor(
                androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.text_primary)
            )
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val subtitle = android.widget.TextView(this).apply {
            text = cardSubtitle
            setTextColor(
                androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.text_secondary)
            )
            textSize = 12f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (3 * density).toInt() }
        }
        val texts = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(title)
            addView(subtitle)
        }
        val icon = android.widget.ImageView(this).apply {
            setImageResource(iconRes)
            /*
             * 矢量图标（24dp 内建尺寸）用 CENTER，和 XML 里那些卡片保持一致；
             * 位图图标（比如大肥鱼那张 512px 的 PNG）必须用 FIT_CENTER，
             * 否则会按原始像素尺寸居中绘制，在 42dp 底板里被裁得只剩一块。
             *
             * 大肥鱼（474:349 的宽扁图）单独处理：给它一个 32×24dp 的绘制区，
             * 让它**高度和其它图标一样**、整体居中，看起来才是对齐的。
             */
            val wideFish = iconRes == R.drawable.ic_deepseek_fish
            scaleType = if (wideFish || drawable is android.graphics.drawable.BitmapDrawable) {
                android.widget.ImageView.ScaleType.FIT_CENTER
            } else {
                android.widget.ImageView.ScaleType.CENTER
            }
            if (wideFish) {
                setPadding(
                    (4.8 * density).toInt(), (9 * density).toInt(),
                    (4.8 * density).toInt(), (9 * density).toInt()
                )
            }
            background = androidx.core.content.ContextCompat.getDrawable(
                this@MainActivity, R.drawable.bg_icon_tile
            )
            layoutParams = android.widget.LinearLayout.LayoutParams(
                (42 * density).toInt(), (42 * density).toInt()
            )
        }
        val chevron = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron_right)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                (20 * density).toInt(), (20 * density).toInt()
            )
        }
        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(
                (16 * density).toInt(), (16 * density).toInt(),
                (16 * density).toInt(), (16 * density).toInt()
            )
            addView(icon)
            addView(
                texts,
                android.widget.LinearLayout.LayoutParams(
                    0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ).apply { marginStart = (14 * density).toInt() }
            )
            addView(chevron)
        }
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * density).toInt() }
            radius = 18f * density
            setCardBackgroundColor(
                androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.surface)
            )
            strokeColor = androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.stroke)
            strokeWidth = (1 * density).toInt()
            cardElevation = 0f
            addView(row)
            isClickable = true
            setOnClickListener { onClick() }
        }
        Anim.pressFeedback(card)
        extraCards += card
        parent.addView(card, (parent.indexOfChild(binding.cardGps) + 1).coerceAtMost(parent.childCount))
    }

    /**
     * 主页排版样式：默认一列列表，可选两列宫格。
     *
     * 做法是在运行时把 8 张功能卡片从原来的竖排容器里摘出来，塞进一个 2 列 GridLayout，
     * 再插回原来的位置——不用维护两套布局 XML，卡片本身也不用改。
     */
    private fun applyHomeLayoutStyle() {
        if (!SettingsRepository.homeGridStyle(this)) return
        val cards = buildList {
            add(binding.cardCpu); add(binding.cardMemory); add(binding.cardScreen); add(binding.cardBattery)
            add(binding.cardSensor); add(binding.cardTelephony); add(binding.cardSystem); add(binding.cardGps)
            extraCards.forEach { add(it) }
        }
        val parent = cards.first().parent as? android.view.ViewGroup ?: return
        val anchorIndex = parent.indexOfChild(cards.first())
        cards.forEach { parent.removeView(it) }

        val density = resources.displayMetrics.density
        val gap = (10 * density).toInt()
        val grid = android.widget.GridLayout(this).apply {
            columnCount = 2
            // 关掉系统默认边距，否则不同行列的间距会不一致
            useDefaultMargins = false
            alignmentMode = android.widget.GridLayout.ALIGN_BOUNDS
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        cards.forEachIndexed { index, card ->
            val row = index / 2
            val column = index % 2
            card.layoutParams = android.widget.GridLayout.LayoutParams().apply {
                /*
                 * 列：宽度给 0 + 权重（外层宽度是 match_parent，有界，权重才生效）。
                 * 行：**不能**给权重！GridLayout 在 WRAP_CONTENT 高度的容器里
                 * 无法分配加权行，会把行高算成 0，整块宫格直接消失。
                 * 这里改成 FILL 对齐 + MATCH_PARENT：同一行的卡片自动拉伸到该行最高的一张，
                 * 8 张卡片结构一样，行与行的高度自然也就一致了。
                 */
                width = 0
                height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                rowSpec = android.widget.GridLayout.spec(row, android.widget.GridLayout.FILL)
                columnSpec = android.widget.GridLayout.spec(column, 1f)
                // 卡片之间统一留 gap，外侧不留边（相邻卡片各出一半，正好等于 gap）
                setMargins(
                    if (column == 0) 0 else gap / 2,
                    if (row == 0) 0 else gap / 2,
                    if (column == 1) 0 else gap / 2,
                    if (row == 3) 0 else gap / 2
                )
            }
            // 统一最小高度：8 张卡片内容差不多，加上这个下限后每一行的高度完全一致
            card.minimumHeight = (112 * density).toInt()
            grid.addView(card)
        }
        /*
         * 注意：插入下标必须是「搬走 8 张卡片之后」的合法值。
         * 之前直接用搬走之前记下的 anchorIndex，容器已经变短，下标越界 →
         * 一开启宫格就抛 IndexOutOfBoundsException，整个应用直接退出。
         */
        parent.addView(grid, anchorIndex.coerceIn(0, parent.childCount))
    }

    private fun open(screen: Class<out Activity>) {
        startActivity(Intent(this, screen))
    }
}
