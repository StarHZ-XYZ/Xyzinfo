package com.rjy.xyz.apps.xyzinfo.data

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.util.SystemPropertiesReader
import java.util.Locale

/** 设备形态。 */
enum class DeviceForm(val label: String) {
    PHONE("手机"),
    TABLET("平板"),
    FOLDABLE("折叠屏"),
    CAR("车机"),
    WATCH("智能手表"),
    TV("电视"),
    OTHER("其他设备")
}

/** 形态识别结果。 */
data class DeviceFormInfo(
    val form: DeviceForm,
    val widthDp: Int,
    val heightDp: Int,
    val smallestWidthDp: Int,
    /** 折叠屏专有：当前是展开还是折叠（按屏宽推断）。 */
    val unfolded: Boolean?
) {
    /** 界面用的完整描述，例如「折叠屏（已展开）｜ 673×727dp」。 */
    val description: String
        get() = buildString {
            append(form.label)
            if (unfolded != null) append(if (unfolded) "（已展开）" else "（已折叠）")
            append("｜ $widthDp×$heightDp dp")
        }
}

/**
 * 识别设备是手机 / 平板 / 折叠屏 / 车机 / 手表 / 电视 / 其他设备。
 *
 * 判据（按可靠性排序）：
 * 1. 车机 / 手表 / 电视的系统模式与硬件特性优先匹配，避免按大屏误判为平板；
 * 2. 有铰链角度传感器、系统折叠特性或明确市场名 → 折叠屏；
 * 3. 桌面模式、家电、VR 头显等特殊 UI 模式 → 其他设备；
 * 4. `smallestScreenWidthDp >= 600` → 平板；
 * 5. 其余算手机。
 *
 * 很多折叠屏不会把 Android 11 的铰链传感器开放给普通应用；如果只看 `smallestWidthDp`，
 * 展开后一定会被误判成平板。因此后两层身份识别必须在尺寸判断之前完成。
 *
 * 折叠屏的「展开 / 折叠」不读传感器（读一次要注册监听、还要考虑权限与耗电），
 * 直接用当前屏宽推断：折叠态一般 < 600dp，展开态 >= 600dp。
 */
object DeviceFormDetector {

    @Volatile
    private var cachedFoldableHardware: Boolean? = null

    fun detect(context: Context): DeviceFormInfo {
        val configuration = context.resources.configuration
        val widthDp = configuration.screenWidthDp
        val heightDp = configuration.screenHeightDp
        val smallest = configuration.smallestScreenWidthDp
        val uiModeType = currentUiModeType(context, configuration)
        val hasAutomotiveFeature = hasFeature(context, PackageManager.FEATURE_AUTOMOTIVE)
        val hasWatchFeature = hasFeature(context, PackageManager.FEATURE_WATCH)
        val hasTvFeature = hasFeature(context, PackageManager.FEATURE_LEANBACK) ||
            hasFeature(context, PackageManager.FEATURE_LEANBACK_ONLY) ||
            hasFeature(context, PackageManager.FEATURE_TELEVISION)
        val hasOtherFeature = hasFeature(context, PackageManager.FEATURE_PC) ||
            hasFeature(context, PackageManager.FEATURE_EMBEDDED) ||
            hasFeature(context, PackageManager.FEATURE_VR_MODE)
        val specialForm = specialUiForm(
            uiModeType,
            hasAutomotiveFeature,
            hasWatchFeature,
            hasTvFeature
        )
        val foldable = specialForm == null && isFoldableHardware(context)
        val form = classify(
            FormSignals(
                uiModeType = uiModeType,
                hasAutomotiveFeature = hasAutomotiveFeature,
                hasWatchFeature = hasWatchFeature,
                hasTvFeature = hasTvFeature,
                hasOtherFeature = hasOtherFeature,
                isFoldableHardware = foldable,
                smallestWidthDp = smallest
            )
        )
        return DeviceFormInfo(
            form = form,
            widthDp = widthDp,
            heightDp = heightDp,
            smallestWidthDp = smallest,
            unfolded = if (form == DeviceForm.FOLDABLE) widthDp >= TABLET_MIN_WIDTH_DP else null
        )
    }

    /** 宽屏（平板 / 展开态折叠屏）——排版要按宽屏适配。 */
    fun isWide(info: DeviceFormInfo): Boolean =
        info.widthDp >= TABLET_MIN_WIDTH_DP || info.form == DeviceForm.TABLET

    internal data class FormSignals(
        val uiModeType: Int,
        val hasAutomotiveFeature: Boolean = false,
        val hasWatchFeature: Boolean = false,
        val hasTvFeature: Boolean = false,
        val hasOtherFeature: Boolean = false,
        val isFoldableHardware: Boolean = false,
        val smallestWidthDp: Int
    )

    /**
     * 形态判定的纯函数：特殊模式优先于尺寸，折叠屏优先于普通平板判定。
     */
    internal fun classify(signals: FormSignals): DeviceForm {
        specialUiForm(
            signals.uiModeType,
            signals.hasAutomotiveFeature,
            signals.hasWatchFeature,
            signals.hasTvFeature
        )?.let { return it }
        return when {
            signals.isFoldableHardware -> DeviceForm.FOLDABLE
            signals.hasOtherFeature || signals.uiModeType in OTHER_UI_MODE_TYPES -> DeviceForm.OTHER
            signals.smallestWidthDp >= TABLET_MIN_WIDTH_DP -> DeviceForm.TABLET
            else -> DeviceForm.PHONE
        }
    }

    private fun specialUiForm(
        uiModeType: Int,
        hasAutomotiveFeature: Boolean,
        hasWatchFeature: Boolean,
        hasTvFeature: Boolean
    ): DeviceForm? = when {
        hasAutomotiveFeature || uiModeType == Configuration.UI_MODE_TYPE_CAR -> DeviceForm.CAR
        hasWatchFeature || uiModeType == Configuration.UI_MODE_TYPE_WATCH -> DeviceForm.WATCH
        hasTvFeature || uiModeType == Configuration.UI_MODE_TYPE_TELEVISION -> DeviceForm.TV
        else -> null
    }

    private fun currentUiModeType(context: Context, configuration: Configuration): Int {
        val manager = ContextCompat.getSystemService(context, UiModeManager::class.java)
        return manager?.currentModeType
            ?: (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK)
    }

    private fun hasFeature(context: Context, feature: String): Boolean =
        runCatching { context.packageManager.hasSystemFeature(feature) }.getOrDefault(false)

    /**
     * 折叠硬件身份在一次进程内不会变化，缓存后避免每次页面排版都启动一次 getprop。
     */
    private fun isFoldableHardware(context: Context): Boolean =
        cachedFoldableHardware ?: synchronized(this) {
            cachedFoldableHardware ?: detectFoldableHardware(context).also {
                cachedFoldableHardware = it
            }
        }

    private fun detectFoldableHardware(context: Context): Boolean {
        if (hasHingeEvidence(context)) return true

        val properties = SystemPropertiesReader.readAll()
        val marketName = runCatching { DeviceNameRepository.lookup(context) }.getOrNull()
        val identity = buildList {
            add(Build.MANUFACTURER)
            add(Build.BRAND)
            add(Build.DEVICE)
            add(Build.MODEL)
            add(Build.PRODUCT)
            add(marketName.orEmpty())
            add(properties["ro.product.marketname"].orEmpty())
            add(properties["ro.product.vendor.marketname"].orEmpty())
            add(properties["ro.product.odm.marketname"].orEmpty())
        }.joinToString(" ")
        return looksLikeFoldable(identity, properties)
    }

    private fun hasHingeEvidence(context: Context): Boolean {
        val packageManager = context.packageManager
        if (FOLDABLE_FEATURES.any(packageManager::hasSystemFeature)) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return runCatching {
            val manager = ContextCompat.getSystemService(context, SensorManager::class.java)
                ?: return false
            manager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null
        }.getOrDefault(false)
    }

    /**
     * 纯身份判断，供系统能力和属性都读不到时兜底，也方便单测覆盖各家折叠屏命名。
     */
    internal fun looksLikeFoldable(
        identity: String,
        properties: Map<String, String> = emptyMap()
    ): Boolean {
        val text = (identity + " " + properties.values.joinToString(" "))
            .lowercase(Locale.ROOT)
        if (FOLDABLE_NAME_PATTERNS.any { it.containsMatchIn(text) }) return true

        return FOLDABLE_FLAG_KEYS.any { key ->
            val value = properties[key]?.trim()?.lowercase(Locale.ROOT).orEmpty()
            value.split(',').map(String::trim).any {
                it == "1" || it == "true" || it == "yes" || it == "foldable"
            }
        }
    }

    /** Android 对平板的官方定义：最小宽度 600dp。 */
    const val TABLET_MIN_WIDTH_DP = 600

    private val FOLDABLE_FEATURES = listOf(
        PackageManager.FEATURE_SENSOR_HINGE_ANGLE,
        "com.google.android.feature.FOLDABLE",
        "com.google.android.feature.PIXEL_FOLDABLE",
        "com.samsung.android.feature.FOLDABLE",
        "com.samsung.android.hardware.foldable",
        "com.huawei.hardware.foldable",
        "com.honor.hardware.foldable",
        "com.motorola.hardware.foldable"
    )

    /** 只匹配完整型号词，避免普通手机 / 平板因为普通英文单词误判。 */
    private val FOLDABLE_NAME_PATTERNS = listOf(
        Regex("""(?:^|[\s_-])(fold|flip|razr)(?:$|[\s_-]|\d)"""),
        Regex("""\bmagic\s+v(?:s\d*|\d+)?\b"""),
        Regex("""\bmate\s+x(?:t|\d+)?\b"""),
        Regex("""\b(?:huawei\s+)?pocket\s+\d+\b"""),
        Regex("""\bfind\s+n\d*\b"""),
        Regex("""\boneplus\s+open\b""")
    )

    private val FOLDABLE_FLAG_KEYS = setOf(
        "ro.build.characteristics",
        "ro.product.build.characteristics",
        "ro.vendor.product.build.characteristics",
        "ro.config.foldable",
        "ro.foldable",
        "persist.sys.foldable",
        "persist.sys.is_foldable",
        "ro.product.is_foldable",
        "ro.vendor.product.is_foldable",
        "ro.hardware.foldable"
    )

    private val OTHER_UI_MODE_TYPES = setOf(
        Configuration.UI_MODE_TYPE_DESK,
        Configuration.UI_MODE_TYPE_APPLIANCE,
        Configuration.UI_MODE_TYPE_VR_HEADSET
    )
}
