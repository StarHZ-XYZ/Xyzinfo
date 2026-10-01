package com.rjy.xyz.apps.xyzinfo.data.speed

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * 联网小工具：带超时、带 UA、只读文本。
 *
 * 三个细节是有意为之：
 * - `Accept-Encoding: identity`：不要压缩，测速时数到的字节要等于线路上真实跑的字节；
 * - 固定 UA：有些接口（Cloudflare 的部分端点）会直接拒绝陌生 UA；
 * - 限制读取长度：定位接口都是几百字节的 JSON，万一碰上返回一整个网页的，
 *   也不会把内存读爆。
 */
internal object SpeedHttp {

    const val USER_AGENT = "XyzInfo-Android"

    fun open(url: String, connectTimeoutMs: Int, readTimeoutMs: Int): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Cache-Control", "no-cache")
        }

    /** 读取一段文本；失败（超时 / 非 2xx / 解析异常）返回 null。 */
    fun readText(url: String, limitBytes: Int = 8 * 1024, timeoutMs: Int = 6000): String? =
        runCatching {
            val connection = open(url, timeoutMs, timeoutMs)
            try {
                if (connection.responseCode !in 200..299) return null
                connection.inputStream.use { readLimited(it, limitBytes) }
            } finally {
                runCatching { connection.disconnect() }
            }
        }.getOrNull()

    private fun readLimited(stream: InputStream, limit: Int): String {
        val buffer = ByteArray(1024)
        val out = StringBuilder()
        var total = 0
        while (total < limit) {
            val read = stream.read(buffer)
            if (read <= 0) break
            out.append(String(buffer, 0, read, Charsets.UTF_8))
            total += read
        }
        return out.toString()
    }
}

/**
 * 用户位置检测。
 *
 * 用**出口 IP 的归属地**而不是 GPS，原因是：选测速服务器要看的是「数据包走哪条路」，
 * 不是「人站在哪」。连着 Wi-Fi、走代理、用流量，出口 IP 才是真正决定该连哪台服务器的东西；
 * 而且 IP 定位不需要任何权限、不用等卫星、室内也能用。
 *
 * 依次尝试多个公开接口（都是 HTTPS、免密钥），任意一个成功就返回；
 * 全部失败时返回 null，界面会自动退回「全球候选」顺序，不影响测速。
 */
object GeoLocator {

    data class UserGeo(
        val ip: String,
        val city: String,
        val region: String,
        val country: String,
        val isp: String,
        val latitude: Double?,
        val longitude: Double?,
        val source: String
    ) {
        val hasCoordinates: Boolean get() = latitude != null && longitude != null

        /** 经纬度对；缺任意一项都返回 null（拿来直接做距离计算）。 */
        val coordinates: Pair<Double, Double>?
            get() {
                val lat = latitude ?: return null
                val lon = longitude ?: return null
                return lat to lon
            }

        /** 「韩国 · Chuncheon」这种归属地文案。 */
        val placeText: String
            get() = listOf(country, region, city)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(" · ")
    }

    private val providers: List<Pair<String, () -> UserGeo?>> = listOf(
        "ipwho.is" to { fromIpWhoIs() },
        "ip.sb" to { fromIpSb() },
        "ipinfo.io" to { fromIpInfo() },
        "cloudflare" to { fromCloudflareTrace() }
    )

    /** 阻塞式检测，**必须在后台线程调用**。 */
    fun detect(): UserGeo? {
        providers.forEach { (_, fetch) ->
            val geo = runCatching { fetch() }.getOrNull()
            if (geo != null) return geo
        }
        return null
    }

    private fun fromIpWhoIs(): UserGeo? {
        val json = JSONObject(SpeedHttp.readText("https://ipwho.is/") ?: return null)
        if (json.optBoolean("success", true).not()) return null
        val connection = json.optJSONObject("connection")
        return UserGeo(
            ip = json.optString("ip"),
            city = json.optString("city"),
            region = json.optString("region"),
            country = json.optString("country"),
            isp = connection?.optString("isp").orEmpty().ifBlank {
                connection?.optString("org").orEmpty()
            },
            latitude = json.optDoubleOrNull("latitude"),
            longitude = json.optDoubleOrNull("longitude"),
            source = "ipwho.is"
        )
    }

    private fun fromIpSb(): UserGeo? {
        val json = JSONObject(SpeedHttp.readText("https://api.ip.sb/geoip") ?: return null)
        return UserGeo(
            ip = json.optString("ip"),
            city = json.optString("city"),
            region = json.optString("region"),
            country = json.optString("country"),
            isp = json.optString("isp"),
            latitude = json.optDoubleOrNull("latitude"),
            longitude = json.optDoubleOrNull("longitude"),
            source = "ip.sb"
        )
    }

    private fun fromIpInfo(): UserGeo? {
        val json = JSONObject(SpeedHttp.readText("https://ipinfo.io/json") ?: return null)
        // loc 的格式是 "纬度,经度"，很可能整个字段都不存在
        val loc = json.optString("loc").split(',')
        val latitude = loc.getOrNull(0)?.trim()?.toDoubleOrNull()
        val longitude = loc.getOrNull(1)?.trim()?.toDoubleOrNull()
        return UserGeo(
            ip = json.optString("ip"),
            city = json.optString("city"),
            region = json.optString("region"),
            country = json.optString("country"),
            isp = json.optString("org"),
            latitude = latitude,
            longitude = longitude,
            source = "ipinfo.io"
        )
    }

    /**
     * Cloudflare 的 /cdn-cgi/trace：纯文本、只有国家和接入点代号，没有城市和经纬度。
     * 放在最后当兜底 —— 有国家名总比什么都没有强，坐标缺失时由界面退回候选顺序。
     */
    private fun fromCloudflareTrace(): UserGeo? {
        val text = SpeedHttp.readText("https://www.cloudflare.com/cdn-cgi/trace") ?: return null
        val fields = text.lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }
            .toMap()
        val country = fields["loc"].orEmpty()
        val ip = fields["ip"].orEmpty()
        if (country.isBlank() && ip.isBlank()) return null
        return UserGeo(
            ip = ip,
            city = "",
            region = "",
            country = country,
            isp = "",
            latitude = null,
            longitude = null,
            source = "cloudflare"
        )
    }

    /** JSON 里字段可能是字符串（"37.87"）也可能是数字（37.87），这里统一处理。 */
    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        val value = optDouble(name, Double.NaN)
        return if (value.isFinite()) value else null
    }
}
