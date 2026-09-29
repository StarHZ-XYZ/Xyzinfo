package com.rjy.xyz.apps.xyzinfo.data

import android.content.Context
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

    /** 加载映射库；耗时约百毫秒级，建议在后台线程调用。 */
    fun load(context: Context) {
        if (deviceNames == null) deviceNames = read(context, "device_names")
        if (modelNames == null) modelNames = read(context, "model_names")
    }

    /** 先按设备代号查，再按系统型号查，返回「中文品牌 + 机型名」。 */
    fun lookup(context: Context, device: String?, model: String?): String? {
        load(context)
        val byDevice = device?.trim()?.lowercase()?.let { deviceNames?.get(it) }
        val byModel = model?.trim()?.lowercase()?.let { modelNames?.get(it) }
        return (byDevice ?: byModel)?.let(::localizeBrand)
    }

    /** 已收录条目数（代号库 + 型号库）。 */
    fun entryCount(): Int = (deviceNames?.size ?: 0) + (modelNames?.size ?: 0)

    /** 把机型名开头的英文品牌换成中文，例如 “Xiaomi Civi 1S” → “小米 Civi 1S”。 */
    private fun localizeBrand(name: String): String {
        val firstSpace = name.indexOf(' ')
        if (firstSpace <= 0) return name
        val brand = name.substring(0, firstSpace)
        val chinese = brandNames[brand.lowercase()] ?: return name
        return chinese + name.substring(firstSpace)
    }

    /**
     * 读取映射文件。
     *
     * 注意：AAPT 会把 `.gz` 资源自动解压并去掉扩展名，因此这里先按 `.tsv` 读，
     * 读不到再退回 `.tsv.gz`（两种打包结果都能兼容）。
     */
    private fun read(context: Context, baseName: String): Map<String, String> = runCatching {
        val map = HashMap<String, String>(32768)
        val plainStream = runCatching { context.assets.open("$baseName.tsv") }.getOrNull()
        val raw = plainStream ?: context.assets.open("$baseName.tsv.gz")
        val reader = if (plainStream != null) raw.bufferedReader() else GZIPInputStream(raw).bufferedReader()

        reader.useLines { lines ->
                lines.forEach { line ->
                    val separator = line.indexOf('\t')
                    if (separator > 0) {
                        map[line.substring(0, separator)] = line.substring(separator + 1)
                    }
                }
            }
        raw.close()
        map
    }.getOrDefault(emptyMap())
}
