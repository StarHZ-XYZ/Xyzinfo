package com.rjy.xyz.apps.xyzinfo.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.rjy.xyz.apps.xyzinfo.model.RootStatus
import com.rjy.xyz.apps.xyzinfo.model.SystemInfo
import com.rjy.xyz.apps.xyzinfo.model.TrebleStatus
import com.rjy.xyz.apps.xyzinfo.util.DeviceFacts
import com.rjy.xyz.apps.xyzinfo.util.Labels
import com.rjy.xyz.apps.xyzinfo.util.ProcFs
import java.util.Locale
import java.util.TimeZone

/**
 * 读取系统版本、设备标识、存储与运行环境信息。
 */
object SystemInfoProvider {

    private val ROOT_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/vendor/bin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su"
    )

    private const val TREBLE_CONFIG_PATH = "/system/etc/ld.config.txt"

    fun load(context: Context): SystemInfo {
        val androidRelease = Build.VERSION.RELEASE
        val apiLevel = Build.VERSION.SDK_INT
        val buildId = DeviceFacts.orUnknown(Build.ID)
        val securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            DeviceFacts.orUnknown(Build.VERSION.SECURITY_PATCH)
        } else {
            VERSION_TOO_OLD
        }
        val kernelVersionLine = DeviceFacts.kernelVersionLine()
        val bootloader = DeviceFacts.orUnknown(Build.BOOTLOADER)

        val brand = DeviceFacts.orUnknown(Build.BRAND)
        val manufacturer = DeviceFacts.orUnknown(Build.MANUFACTURER)
        val model = DeviceFacts.orUnknown(Build.MODEL)
        val device = DeviceFacts.orUnknown(Build.DEVICE)
        val product = DeviceFacts.orUnknown(Build.PRODUCT)
        val board = DeviceFacts.orUnknown(Build.BOARD)
        val hardware = DeviceFacts.orUnknown(Build.HARDWARE)
        val fingerprint = DeviceFacts.orUnknown(Build.FINGERPRINT)
        val abiList = Build.SUPPORTED_ABIS.joinToString(", ").ifBlank { Labels.UNKNOWN }
        val languageTag = Locale.getDefault().toLanguageTag()
        val timeZoneId = TimeZone.getDefault().id

        val memory = readMemory(context)
        val systemPartition = readPartition(Environment.getRootDirectory().absolutePath)
        val dataPartition = readPartition(Environment.getDataDirectory().absolutePath)
        val rootStatus = detectRootStatus()
        val trebleStatus = detectTrebleStatus()

        return SystemInfo(
            androidRelease = DeviceFacts.orUnknown(androidRelease),
            apiLevel = apiLevel,
            buildId = buildId,
            securityPatch = securityPatch,
            kernelVersionLine = kernelVersionLine,
            bootloader = bootloader,
            brand = brand,
            manufacturer = manufacturer,
            model = model,
            device = device,
            product = product,
            board = board,
            hardware = hardware,
            fingerprint = fingerprint,
            abiList = abiList,
            languageTag = languageTag,
            timeZoneId = timeZoneId,
            timestampMillis = System.currentTimeMillis(),
            ramTotalBytes = memory?.first,
            ramAvailableBytes = memory?.second,
            systemPartitionTotalBytes = systemPartition?.first,
            systemPartitionAvailableBytes = systemPartition?.second,
            dataPartitionTotalBytes = dataPartition?.first,
            dataPartitionAvailableBytes = dataPartition?.second,
            rootStatus = rootStatus,
            trebleStatus = trebleStatus,
            rawPreview = buildRawPreview(
                androidVersion = DeviceFacts.orUnknown(androidRelease),
                apiLevel = apiLevel,
                buildId = buildId,
                securityPatch = securityPatch,
                kernelVersion = kernelVersionLine,
                bootloader = bootloader,
                brand = brand,
                manufacturer = manufacturer,
                model = model,
                device = device,
                product = product,
                board = board,
                hardware = hardware,
                fingerprint = fingerprint,
                abi = abiList,
                language = languageTag,
                timeZone = timeZoneId,
                rootStatus = rootStatus,
                trebleStatus = trebleStatus
            )
        )
    }

    /** 返回 (总量, 可用量)，读取失败时为 null。 */
    private fun readMemory(context: Context): Pair<Long, Long>? = runCatching {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        info.totalMem to info.availMem
    }.getOrNull()

    private fun readPartition(path: String): Pair<Long, Long>? = runCatching {
        val stat = StatFs(path)
        stat.totalBytes to stat.availableBytes
    }.getOrNull()

    private fun detectRootStatus(): RootStatus =
        if (ROOT_PATHS.any { ProcFs.exists(it) }) RootStatus.LIKELY_ROOTED else RootStatus.CLEAN

    /**
     * Project Treble 自 Android 8 起强制要求，
     * 因此找不到配置文件时按系统版本推断。
     */
    private fun detectTrebleStatus(): TrebleStatus = when {
        ProcFs.readText(TREBLE_CONFIG_PATH).isNotBlank() -> TrebleStatus.CONFIG_PRESENT
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> TrebleStatus.LIKELY_SUPPORTED
        else -> TrebleStatus.UNKNOWN
    }

    private fun buildRawPreview(
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
        timeZone: String,
        rootStatus: RootStatus,
        trebleStatus: TrebleStatus
    ): String = """
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
        Timezone: $timeZone
        Root: ${Labels.rootStatus(rootStatus)}
        Treble: ${Labels.trebleStatus(trebleStatus)}
    """.trimIndent()

    private const val VERSION_TOO_OLD = "系统版本过低"
}
