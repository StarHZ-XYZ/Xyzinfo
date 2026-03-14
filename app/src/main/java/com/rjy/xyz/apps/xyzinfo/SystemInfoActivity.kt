package com.rjy.xyz.apps.xyzinfo

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SystemInfoActivity : AppCompatActivity() {

    private lateinit var tvSystemTitle: TextView
    private lateinit var tvSystemSubTitle: TextView

    private lateinit var tvAndroidVersion: TextView
    private lateinit var tvApiLevel: TextView
    private lateinit var tvBuildId: TextView
    private lateinit var tvSecurityPatch: TextView
    private lateinit var tvKernelVersion: TextView
    private lateinit var tvBootloader: TextView

    private lateinit var tvBrand: TextView
    private lateinit var tvManufacturer: TextView
    private lateinit var tvModel: TextView
    private lateinit var tvDevice: TextView
    private lateinit var tvProduct: TextView
    private lateinit var tvBoard: TextView
    private lateinit var tvHardware: TextView
    private lateinit var tvFingerprint: TextView

    private lateinit var tvAbi: TextView
    private lateinit var tvLanguage: TextView
    private lateinit var tvTimezone: TextView
    private lateinit var tvCurrentTime: TextView

    private lateinit var tvRamSummary: TextView
    private lateinit var tvInternalStorage: TextView
    private lateinit var tvDataStorage: TextView
    private lateinit var tvRootStatus: TextView
    private lateinit var tvTrebleStatus: TextView

    private lateinit var tvRawSystemInfo: TextView

    private lateinit var cardHeader: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system_info)

        initViews()
        loadSystemInfo()
    }

    private fun initViews() {
        tvSystemTitle = findViewById(R.id.tvSystemTitle)
        tvSystemSubTitle = findViewById(R.id.tvSystemSubTitle)

        tvAndroidVersion = findViewById(R.id.tvAndroidVersion)
        tvApiLevel = findViewById(R.id.tvApiLevel)
        tvBuildId = findViewById(R.id.tvBuildId)
        tvSecurityPatch = findViewById(R.id.tvSecurityPatch)
        tvKernelVersion = findViewById(R.id.tvKernelVersion)
        tvBootloader = findViewById(R.id.tvBootloader)

        tvBrand = findViewById(R.id.tvBrand)
        tvManufacturer = findViewById(R.id.tvManufacturer)
        tvModel = findViewById(R.id.tvModel)
        tvDevice = findViewById(R.id.tvDevice)
        tvProduct = findViewById(R.id.tvProduct)
        tvBoard = findViewById(R.id.tvBoard)
        tvHardware = findViewById(R.id.tvHardware)
        tvFingerprint = findViewById(R.id.tvFingerprint)

        tvAbi = findViewById(R.id.tvAbi)
        tvLanguage = findViewById(R.id.tvLanguage)
        tvTimezone = findViewById(R.id.tvTimezone)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)

        tvRamSummary = findViewById(R.id.tvRamSummary)
        tvInternalStorage = findViewById(R.id.tvInternalStorage)
        tvDataStorage = findViewById(R.id.tvDataStorage)
        tvRootStatus = findViewById(R.id.tvRootStatus)
        tvTrebleStatus = findViewById(R.id.tvTrebleStatus)

        tvRawSystemInfo = findViewById(R.id.tvRawSystemInfo)

        cardHeader = findViewById(R.id.cardHeader)
    }

    private fun loadSystemInfo() {
        val androidVersion = Build.VERSION.RELEASE ?: "未知"
        val apiLevel = Build.VERSION.SDK_INT
        val buildId = safe(Build.ID)
        val securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            safe(Build.VERSION.SECURITY_PATCH)
        } else {
            "系统版本过低"
        }
        val kernelVersion = readKernelVersion()
        val bootloader = safe(Build.BOOTLOADER)

        val brand = safe(Build.BRAND)
        val manufacturer = safe(Build.MANUFACTURER)
        val model = safe(Build.MODEL)
        val device = safe(Build.DEVICE)
        val product = safe(Build.PRODUCT)
        val board = safe(Build.BOARD)
        val hardware = safe(Build.HARDWARE)
        val fingerprint = safe(Build.FINGERPRINT)

        val abi = Build.SUPPORTED_ABIS.joinToString(", ").ifBlank { "未知" }
        val language = Locale.getDefault().toLanguageTag()
        val timezone = java.util.TimeZone.getDefault().id
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val ramSummary = buildRamSummary()
        val internalStorage = buildStorageSummary(Environment.getRootDirectory().absolutePath)
        val dataStorage = buildStorageSummary(Environment.getDataDirectory().absolutePath)
        val rootStatus = detectRootStatus()
        val trebleStatus = detectTrebleStatus()

        tvSystemTitle.text = "Android $androidVersion"
        tvSystemSubTitle.text = "系统版本、设备标识、存储与环境总览"

        tvAndroidVersion.text = "Android 版本：$androidVersion"
        tvApiLevel.text = "API 等级：$apiLevel"
        tvBuildId.text = "Build ID：$buildId"
        tvSecurityPatch.text = "安全补丁：$securityPatch"
        tvKernelVersion.text = "Linux 内核：$kernelVersion"
        tvBootloader.text = "Bootloader：$bootloader"

        tvBrand.text = "品牌：$brand"
        tvManufacturer.text = "制造商：$manufacturer"
        tvModel.text = "型号：$model"
        tvDevice.text = "设备代号：$device"
        tvProduct.text = "Product：$product"
        tvBoard.text = "Board：$board"
        tvHardware.text = "Hardware：$hardware"
        tvFingerprint.text = "Fingerprint：$fingerprint"

        tvAbi.text = "ABI 列表：$abi"
        tvLanguage.text = "系统语言：$language"
        tvTimezone.text = "时区：$timezone"
        tvCurrentTime.text = "当前时间：$currentTime"

        tvRamSummary.text = "运行内存概览：$ramSummary"
        tvInternalStorage.text = "系统分区存储：$internalStorage"
        tvDataStorage.text = "数据分区存储：$dataStorage"
        tvRootStatus.text = "Root 状态：$rootStatus"
        tvTrebleStatus.text = "Project Treble：$trebleStatus"

        tvRawSystemInfo.text = buildRawSystemInfo(
            androidVersion = androidVersion,
            apiLevel = apiLevel,
            buildId = buildId,
            securityPatch = securityPatch,
            kernelVersion = kernelVersion,
            bootloader = bootloader,
            brand = brand,
            manufacturer = manufacturer,
            model = model,
            device = device,
            product = product,
            board = board,
            hardware = hardware,
            fingerprint = fingerprint,
            abi = abi,
            language = language,
            timezone = timezone,
            currentTime = currentTime,
            rootStatus = rootStatus,
            trebleStatus = trebleStatus
        )
    }

    private fun buildRamSummary(): String {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val total = Formatter.formatFileSize(this, info.totalMem)
            val avail = Formatter.formatFileSize(this, info.availMem)
            "总计 $total，可用 $avail"
        } catch (_: Exception) {
            "系统未公开"
        }
    }

    private fun buildStorageSummary(path: String): String {
        return try {
            val stat = StatFs(path)
            val total = stat.totalBytes
            val avail = stat.availableBytes
            val used = total - avail
            "总计 ${Formatter.formatFileSize(this, total)}，已用 ${Formatter.formatFileSize(this, used)}，可用 ${Formatter.formatFileSize(this, avail)}"
        } catch (_: Exception) {
            "系统未公开"
        }
    }

    private fun detectRootStatus(): String {
        val rootPaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/vendor/bin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su"
        )

        return try {
            val exists = rootPaths.any { File(it).exists() }
            if (exists) "疑似已 Root / 存在 su" else "未检测到明显 Root 痕迹"
        } catch (_: Exception) {
            "未知"
        }
    }

    private fun detectTrebleStatus(): String {
        return try {
            val value = readFileText("/system/etc/ld.config.txt")
            if (value.isNotBlank()) "已支持或系统包含 Treble 相关配置" else "系统未公开"
        } catch (_: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) "大概率支持" else "系统版本过低"
        }
    }

    private fun readKernelVersion(): String {
        val procVersion = readFileText("/proc/version").trim()
        if (procVersion.isNotBlank()) return procVersion

        return System.getProperty("os.version") ?: "未知"
    }

    private fun buildRawSystemInfo(
        androidVersion: String,
        apiLevel: Int,
        buildId: String,
        securityPatch: String,
        kernelVersion: String,
        bootloader: String,
        brand: String,
        manufacturer: String,
        model: String,
        device: String,
        product: String,
        board: String,
        hardware: String,
        fingerprint: String,
        abi: String,
        language: String,
        timezone: String,
        currentTime: String,
        rootStatus: String,
        trebleStatus: String
    ): String {
        return """
            原始系统信息预览：
            Android: $androidVersion
            API: $apiLevel
            Build ID: $buildId
            Security Patch: $securityPatch
            Kernel: $kernelVersion
            Bootloader: $bootloader
            Brand: $brand
            Manufacturer: $manufacturer
            Model: $model
            Device: $device
            Product: $product
            Board: $board
            Hardware: $hardware
            Fingerprint: $fingerprint
            ABI: $abi
            Language: $language
            Timezone: $timezone
            CurrentTime: $currentTime
            Root: $rootStatus
            Treble: $trebleStatus
        """.trimIndent()
    }

    private fun readFileText(path: String): String {
        return try {
            File(path).readText()
        } catch (_: Exception) {
            ""
        }
    }

    private fun safe(value: String?): String {
        return if (value.isNullOrBlank()) "未知" else value
    }
}