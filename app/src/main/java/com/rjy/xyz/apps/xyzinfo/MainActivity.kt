package com.rjy.xyz.apps.xyzinfo

import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import android.content.Intent

class MainActivity : AppCompatActivity() {

    private lateinit var tvDeviceName: TextView
    private lateinit var tvAndroidVersion: TextView
    private lateinit var tvKernelVersion: TextView
    private lateinit var tvBrandManufacturer: TextView
    private lateinit var tvDeviceCode: TextView
    private lateinit var tvSystemAbi: TextView
    private lateinit var tvCpuArch: TextView

    private lateinit var cardCpu: MaterialCardView
    private lateinit var cardMemory: MaterialCardView
    private lateinit var cardScreen: MaterialCardView
    private lateinit var cardBattery: MaterialCardView
    private lateinit var cardSensor: MaterialCardView
    private lateinit var cardSystem: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initDeviceInfo()
        initClicks()
    }

    private fun initViews() {
        tvDeviceName = findViewById(R.id.tvDeviceName)
        tvAndroidVersion = findViewById(R.id.tvAndroidVersion)
        tvKernelVersion = findViewById(R.id.tvKernelVersion)
        tvBrandManufacturer = findViewById(R.id.tvBrandManufacturer)
        tvDeviceCode = findViewById(R.id.tvDeviceCode)
        tvSystemAbi = findViewById(R.id.tvSystemAbi)
        tvCpuArch = findViewById(R.id.tvCpuArch)

        cardCpu = findViewById(R.id.cardCpu)
        cardMemory = findViewById(R.id.cardMemory)
        cardScreen = findViewById(R.id.cardScreen)
        cardBattery = findViewById(R.id.cardBattery)
        cardSensor = findViewById(R.id.cardSensor)
        cardSystem = findViewById(R.id.cardSystem)
    }

    private fun initDeviceInfo() {
        tvDeviceName.text = getFriendlyDeviceName()
        tvAndroidVersion.text = "Android ${Build.VERSION.RELEASE}  (API ${Build.VERSION.SDK_INT})"
        tvKernelVersion.text = "Linux 内核：${getKernelVersion()}"
        tvBrandManufacturer.text =
            "品牌：${safeValue(Build.BRAND)}    制造商：${safeValue(Build.MANUFACTURER)}"
        tvDeviceCode.text =
            "设备代号：${safeValue(Build.DEVICE)}    产品：${safeValue(Build.PRODUCT)}"
        tvSystemAbi.text = "系统架构：${getPrimaryAbiFriendly()}"
        tvCpuArch.text = "CPU架构：${getCpuArchitectureFriendly()}"
    }

    private fun initClicks() {
        cardCpu.setOnClickListener {
            startActivity(Intent(this, SocInfoActivity::class.java))
        }
        cardMemory.setOnClickListener {
            startActivity(Intent(this, RamInfoActivity::class.java))
        }
        cardScreen.setOnClickListener {
            startActivity(Intent(this, ScreenInfoActivity::class.java))
        }
        cardBattery.setOnClickListener {
            startActivity(Intent(this, BatteryInfoActivity::class.java))
        }
        cardSensor.setOnClickListener {
            startActivity(Intent(this, SensorInfoActivity::class.java))
        }
        cardSystem.setOnClickListener {
            startActivity(Intent(this, SystemInfoActivity::class.java))
        }
    }

    private fun getFriendlyDeviceName(): String {
        val brand = safeValue(Build.BRAND).trim()
        val manufacturer = safeValue(Build.MANUFACTURER).trim()
        val model = safeValue(Build.MODEL).trim()

        if (model.startsWith(brand, ignoreCase = true)) {
            return beautifyDeviceName(model)
        }

        if (model.startsWith(manufacturer, ignoreCase = true)) {
            return beautifyDeviceName(model)
        }

        val mainBrand = when {
            brand.equals("redmi", true) -> "Redmi"
            brand.equals("poco", true) -> "POCO"
            brand.equals("xiaomi", true) -> "Xiaomi"
            brand.equals("meizu", true) -> "Meizu"
            brand.equals("realme", true) -> "realme"
            brand.equals("oneplus", true) -> "OnePlus"
            brand.equals("huawei", true) -> "HUAWEI"
            brand.equals("honor", true) -> "HONOR"
            brand.equals("samsung", true) -> "Samsung"
            brand.equals("oppo", true) -> "OPPO"
            brand.equals("vivo", true) -> "vivo"
            manufacturer.isNotBlank() && manufacturer != "未知" ->
                manufacturer.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase() else it.toString()
                }
            else ->
                brand.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase() else it.toString()
                }
        }

        return beautifyDeviceName("$mainBrand $model")
    }

    private fun beautifyDeviceName(raw: String): String {
        return raw
            .replace("_", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun getKernelVersion(): String {
        try {
            val file = File("/proc/version")
            if (file.exists()) {
                BufferedReader(FileReader(file)).use { reader ->
                    val line = reader.readLine()
                    if (!line.isNullOrBlank()) {
                        val match = Regex("Linux version\\s+([^\\s]+)").find(line)
                        if (match != null) {
                            return match.groupValues[1]
                        }
                        return line
                    }
                }
            }
        } catch (_: Exception) {
        }

        return safeValue(System.getProperty("os.version"))
    }

    private fun getPrimaryAbiFriendly(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull()
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Build.SUPPORTED_32_BIT_ABIS.firstOrNull() ?: Build.SUPPORTED_64_BIT_ABIS.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                Build.CPU_ABI
            }

        return when (abi?.lowercase()) {
            "arm64-v8a" -> "arm64-v8a (64位 ARM)"
            "armeabi-v7a" -> "armeabi-v7a (32位 ARM)"
            "armeabi" -> "armeabi (32位 ARM)"
            "x86_64" -> "x86_64 (64位 x86)"
            "x86" -> "x86 (32位 x86)"
            "riscv64" -> "riscv64 (64位 RISC-V)"
            null, "" -> "未知"
            else -> abi
        }
    }

    private fun getCpuArchitectureFriendly(): String {
        val cpuInfoText = readProcCpuInfo().lowercase()

        when {
            "aarch64" in cpuInfoText -> return "AArch64 / ARMv8-A"
            "armv8" in cpuInfoText -> return "ARMv8-A"
            "armv7" in cpuInfoText -> return "ARMv7"
            "armv6" in cpuInfoText -> return "ARMv6"
            "x86_64" in cpuInfoText -> return "x86_64"
            "intel" in cpuInfoText -> return "x86 / Intel"
            "amd" in cpuInfoText -> return "x86 / AMD"
            "riscv" in cpuInfoText -> return "RISC-V"
        }

        val abi = Build.SUPPORTED_ABIS.firstOrNull()?.lowercase().orEmpty()
        return when {
            "arm64" in abi -> "AArch64 / ARMv8-A"
            "armeabi-v7a" in abi -> "ARMv7"
            "armeabi" in abi -> "ARM"
            "x86_64" in abi -> "x86_64"
            abi == "x86" -> "x86"
            "riscv64" in abi -> "RISC-V 64"
            else -> "未知"
        }
    }

    private fun readProcCpuInfo(): String {
        return try {
            val file = File("/proc/cpuinfo")
            if (!file.exists()) return ""

            file.readText()
        } catch (_: Exception) {
            ""
        }
    }

    private fun safeValue(value: String?): String {
        return if (value.isNullOrBlank()) "未知" else value
    }
}