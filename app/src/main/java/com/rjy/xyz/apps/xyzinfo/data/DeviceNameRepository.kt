package com.rjy.xyz.apps.xyzinfo.data

import android.content.Context
import android.os.Build
import java.io.File
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * 机型名映射库：把系统里的 Build.DEVICE（设备代号，如 zijin）
 * 与 Build.MODEL（型号，如 2109119BC）翻译成上市机型名（如「小米 Civi 1S」）。
 *
 * 数据来源：Google Play 公开的认证设备清单（supported_devices），
 * 已压缩打包在 assets/device_names.tsv.gz 与 assets/model_names.tsv.gz 中，
 * 覆盖 2.3 万个设备代号与 2.5 万个型号。
 */
object DeviceNameRepository {

    /** 常见品牌的官方中文名。 */
    private val brandNames = mapOf(
        "xiaomi" to "小米", "redmi" to "红米", "poco" to "POCO",
        "huawei" to "华为", "honor" to "荣耀", "samsung" to "三星",
        "google" to "谷歌", "oneplus" to "一加", "realme" to "真我",
        "meizu" to "魅族", "zte" to "中兴", "nubia" to "努比亚",
        "lenovo" to "联想", "sony" to "索尼", "asus" to "华硕",
        "nokia" to "诺基亚", "motorola" to "摩托罗拉", "sharp" to "夏普",
        "oppo" to "OPPO", "vivo" to "vivo", "iqoo" to "iQOO",
        "tecno" to "传音 Tecno", "infinix" to "传音 Infinix", "itel" to "传音 itel",
        "doogee" to "道格", "blackview" to "Blackview", "ulefone" to "Ulefone",
        "tcl" to "TCL", "alcatel" to "阿尔卡特", "vertu" to "威图",
        "smartisan" to "锤子", "gionee" to "金立", "coolpad" to "酷派",
        "hisense" to "海信", "cmcc" to "中国移动", "sony ericsson" to "索尼爱立信",
        "blackshark" to "黑鲨", "doov" to "朵唯", "leeco" to "乐视", "360" to "360"
    )

    @Volatile
    private var deviceNames: Map<String, String>? = null

    @Volatile
    private var modelNames: Map<String, String>? = null

    /** 更新后的数据存放目录（放在应用私有目录，卸载即清除）。 */
    fun updatedDir(context: Context): File = File(context.filesDir, "devicenames")

    /** 数据来源说明，用于界面展示。 */
    fun dataSource(context: Context): String =
        if (File(updatedDir(context), "$DEVICE_FILE.gz").exists()) "已更新（下载数据）" else "内置资源"

    fun reload(context: Context) {
        deviceNames = null
        modelNames = null
        load(context)
    }

    /** 加载映射库；耗时约百毫秒级，建议在后台线程调用。 */
    fun load(context: Context) {
        if (deviceNames == null) deviceNames = read(context, "device_names")
        if (modelNames == null) modelNames = read(context, "model_names")
    }

    /** 用当前系统里所有可用的设备标识查机型名。 */
    fun lookup(context: Context): String? =
        lookup(context, Build.DEVICE, Build.MODEL, Build.PRODUCT)

    /**
     * 同时查询系统型号、产品名和设备代号，返回信息最完整的「中文品牌 + 机型名」。
     *
     * 只按某一个字段优先会顾此失彼：小米 Civi 1S 的设备代号库比型号库更完整，
     * 荣耀 / iQOO 的部分设备则刚好相反。这里先汇总所有命中的别名，再取信息量最大的
     * 市场名；查询前还会去掉品牌前缀、空格和分隔符差异，兼容
     * `HONOR ELP-AN00` / `ELP-AN00` 这类不同写法。
     */
    fun lookup(
        context: Context,
        device: String?,
        model: String?,
        product: String? = null
    ): String? {
        load(context)
        val models = modelNames.orEmpty()
        val devices = deviceNames.orEmpty()
        return listOf(model, product, device)
            .flatMap { id ->
                listOfNotNull(
                    find(models, id),
                    find(devices, id)
                )
            }
            .map(::localizeMarketName)
            .distinct()
            .maxByOrNull(::marketNameScore)
    }

    /** 已收录条目数（代号库 + 型号库）。 */
    fun entryCount(): Int = (deviceNames?.size ?: 0) + (modelNames?.size ?: 0)

    /** 把机型名里的英文品牌与子品牌整理成国内常见写法。 */
    internal fun localizeMarketName(rawName: String): String {
        var name = rawName.trim().replace(Regex("\\s+"), " ")
        name = name.replaceFirst(
            Regex("^Huawei\\s+Honor\\s*", RegexOption.IGNORE_CASE),
            "荣耀 "
        )
        name = name.replaceFirst(
            Regex("^Huawei\\s+荣耀\\s*", RegexOption.IGNORE_CASE),
            "荣耀"
        )
        name = name.replaceFirst(
            Regex("^Honor\\s+荣耀\\s*", RegexOption.IGNORE_CASE),
            "荣耀"
        )
        name = name.replaceFirst(
            Regex("^Honor(?=\\S)", RegexOption.IGNORE_CASE),
            "荣耀"
        )
        name = name.replaceFirst(
            Regex("^Honor\\s+", RegexOption.IGNORE_CASE),
            "荣耀 "
        )
        name = name.replaceFirst(
            Regex("^Vivo\\s+iQOO\\s*", RegexOption.IGNORE_CASE),
            "iQOO "
        )

        val firstSpace = name.indexOf(' ')
        if (firstSpace <= 0) return name.trim()
        val brand = name.substring(0, firstSpace)
        val chinese = brandNames[brand.lowercase(Locale.ROOT)] ?: return name
        return (chinese + name.substring(firstSpace)).trim()
    }

    private fun find(map: Map<String, String>, raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return lookupKeys(raw).firstNotNullOfOrNull { key -> map[key] }
    }

    /** 同等可信时选最具体的市场名，例如 Civi 1S 优先于 Civi、200 Pro 优先于 200。 */
    private fun marketNameScore(name: String): Int =
        name.count { !it.isWhitespace() }

    /**
     * 把设备标识整理成若干可比对的键。
     *
     * 例如 `HONOR ELP-AN00` 同时生成 `honor-elp-an00`、`elp-an00` 与无分隔符形式，
     * 这样同一台机器无论 ROM 返回的是完整展示名还是内部型号都能命中。
     */
    private fun lookupKeys(raw: String): List<String> {
        val base = raw.trim().lowercase(Locale.ROOT)
        val normalized = base
            .replace(Regex("[\\s_]+"), "-")
            .replace(Regex("-+"), "-")
        val stripped = BRAND_PREFIXES.firstOrNull { normalized.startsWith(it) }
            ?.let { normalized.removePrefix(it) }
            .orEmpty()
        return listOf(normalized, stripped)
            .filter { it.isNotBlank() }
            .flatMap { value -> listOf(value, value.filter { it.isLetterOrDigit() }) }
            .distinct()
    }

    /**
     * 读取映射文件。
     *
     * 注意：AAPT 会把 `.gz` 资源自动解压并去掉扩展名，因此这里先按 `.tsv` 读，
     * 读不到再退回 `.tsv.gz`（两种打包结果都能兼容）。
     */
    private fun read(context: Context, baseName: String): Map<String, String> {
        val updated = File(updatedDir(context), "$baseName.tsv.gz")
        if (updated.exists()) {
            val repaired = runCatching {
                updated.inputStream().use { input ->
                    GZIPInputStream(input).bufferedReader().use(::parse)
                }
            }.getOrDefault(emptyMap())
            if (repaired.isNotEmpty()) return repaired
            // 下载缓存损坏时直接丢弃并回载内置资源，不能让型号库永久变成空表。
            runCatching { updated.delete() }
        }
        return runCatching {
            // AAPT 会把 .gz 资源自动解压并去掉扩展名，先按 .tsv 读，读不到再退回 .tsv.gz。
            val plain = runCatching { context.assets.open("$baseName.tsv") }.getOrNull()
            if (plain != null) {
                plain.bufferedReader().use(::parse)
            } else {
                GZIPInputStream(context.assets.open("$baseName.tsv.gz"))
                    .bufferedReader()
                    .use(::parse)
            }
        }.getOrDefault(emptyMap())
    }

    private fun parse(reader: java.io.BufferedReader): Map<String, String> {
        val map = HashMap<String, String>(32768)
        reader.lineSequence().forEach { line ->
            val separator = line.indexOf('\t')
            if (separator > 0) {
                val value = line.substring(separator + 1)
                lookupKeys(line.substring(0, separator)).forEach { key ->
                    map.putIfAbsent(key, value)
                }
            }
        }
        return map
    }

    const val DEVICE_FILE = "device_names"
    const val MODEL_FILE = "model_names"

    private val BRAND_PREFIXES = listOf(
        "huawei-honor-",
        "vivo-iqoo-",
        "honor-",
        "iqoo-",
        "huawei-",
        "vivo-"
    )
}
