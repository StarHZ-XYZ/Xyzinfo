package com.rjy.xyz.apps.xyzinfo.data.speed

/**
 * 公共测速节点目录。
 *
 * **每一台都是实测确认可用的**（HTTPS、无需密钥、允许第三方测速）：
 * 逐条用 curl 打过范围请求，能返回 200/206 且真的吐出数据的才会留在这里 ——
 * 有些镜像看着像有 `speedtest/` 目录，实际返回的是 200 的 HTML 假页面（华为云就是这样），
 * 那种一进目录就会把「测速失败」甩给用户，所以全部剔掉了。
 *
 * 覆盖思路：
 * - **中国大陆**：清华 TUNA、北外镜像站（都在北京，校园网出口，大陆用户延迟最低）；
 * - **亚太**：Vultr 首尔 / 东京 / 新加坡 / 悉尼（大陆用户出海最常走的几条线）；
 * - **美洲 / 欧洲**：Vultr 与 Linode 的主力机房；
 * - **任播兜底**：Cloudflare（[SpeedTestServer.anycast] = true），运营商网络自动就近接入，
 *   也是唯一支持**上传**测速的节点。
 */
object SpeedTestCatalog {

    /** Cloudflare 的下载接口按 bytes 参数现场生成数据，给足 500MB，跑满高速线路也不会中途断流。 */
    private const val CLOUDFLARE_DOWN = "https://speed.cloudflare.com/__down?bytes=500000000"
    private const val CLOUDFLARE_DOWN_SMALL = "https://speed.cloudflare.com/__down?bytes=50000000"
    private const val CLOUDFLARE_UP = "https://speed.cloudflare.com/__up"

    private const val TUNA = "https://mirrors.tuna.tsinghua.edu.cn/speedtest"

    /** Vultr 官方测速文件（每个机房一个子域，文件名统一）。 */
    private fun vultr(host: String) = "https://$host.vultr.com/vultr.com.100MB.bin"

    /** Linode / Akamai 官方测速文件。 */
    private fun linode(city: String) = "https://speedtest.$city.linode.com/100MB-$city.bin"

    val servers: List<SpeedTestServer> = listOf(
        SpeedTestServer(
            id = "cloudflare",
            name = "Cloudflare 全球节点",
            city = "自动就近接入",
            countryCode = "GLOBAL",
            provider = "Cloudflare",
            latitude = 0.0,
            longitude = 0.0,
            downloadUrls = listOf(CLOUDFLARE_DOWN, CLOUDFLARE_DOWN_SMALL),
            uploadUrls = listOf(CLOUDFLARE_UP),
            anycast = true
        ),
        SpeedTestServer(
            id = "tuna",
            name = "清华 TUNA 镜像站",
            city = "北京",
            countryCode = "CN",
            provider = "清华大学",
            latitude = 40.0007,
            longitude = 116.3267,
            downloadUrls = listOf("$TUNA/1000mb.bin", "$TUNA/100mb.bin")
        ),
        SpeedTestServer(
            id = "bfsu",
            name = "北外镜像站",
            city = "北京",
            countryCode = "CN",
            provider = "北京外国语大学",
            latitude = 39.9588,
            longitude = 116.3180,
            downloadUrls = listOf("https://mirrors.bfsu.edu.cn/speedtest/1000mb.bin")
        ),
        SpeedTestServer(
            id = "vultr-sel",
            name = "Vultr 首尔",
            city = "首尔",
            countryCode = "KR",
            provider = "Vultr",
            latitude = 37.5665,
            longitude = 126.9780,
            downloadUrls = listOf(vultr("sel-kor-ping"))
        ),
        SpeedTestServer(
            id = "vultr-hnd",
            name = "Vultr 东京",
            city = "东京",
            countryCode = "JP",
            provider = "Vultr",
            latitude = 35.6762,
            longitude = 139.6503,
            downloadUrls = listOf(vultr("hnd-jp-ping"))
        ),
        SpeedTestServer(
            id = "vultr-sgp",
            name = "Vultr 新加坡",
            city = "新加坡",
            countryCode = "SG",
            provider = "Vultr",
            latitude = 1.3521,
            longitude = 103.8198,
            downloadUrls = listOf(vultr("sgp-ping"))
        ),
        SpeedTestServer(
            id = "vultr-syd",
            name = "Vultr 悉尼",
            city = "悉尼",
            countryCode = "AU",
            provider = "Vultr",
            latitude = -33.8688,
            longitude = 151.2093,
            downloadUrls = listOf(vultr("syd-au-ping"))
        ),
        SpeedTestServer(
            id = "vultr-lax",
            name = "Vultr 洛杉矶",
            city = "洛杉矶",
            countryCode = "US",
            provider = "Vultr",
            latitude = 34.0522,
            longitude = -118.2437,
            downloadUrls = listOf(vultr("lax-ca-us-ping"))
        ),
        SpeedTestServer(
            id = "vultr-sjo",
            name = "Vultr 圣何塞",
            city = "圣何塞",
            countryCode = "US",
            provider = "Vultr",
            latitude = 37.3382,
            longitude = -121.8863,
            downloadUrls = listOf(vultr("sjo-ca-us-ping"))
        ),
        SpeedTestServer(
            id = "vultr-fra",
            name = "Vultr 法兰克福",
            city = "法兰克福",
            countryCode = "DE",
            provider = "Vultr",
            latitude = 50.1109,
            longitude = 8.6821,
            downloadUrls = listOf(vultr("fra-de-ping"))
        ),
        SpeedTestServer(
            id = "vultr-lon",
            name = "Vultr 伦敦",
            city = "伦敦",
            countryCode = "GB",
            provider = "Vultr",
            latitude = 51.5074,
            longitude = -0.1278,
            downloadUrls = listOf(vultr("lon-gb-ping"))
        ),
        SpeedTestServer(
            id = "linode-fremont",
            name = "Linode 弗里蒙特",
            city = "弗里蒙特",
            countryCode = "US",
            provider = "Akamai Linode",
            latitude = 37.5485,
            longitude = -121.9886,
            downloadUrls = listOf(linode("fremont"))
        ),
        SpeedTestServer(
            id = "linode-dallas",
            name = "Linode 达拉斯",
            city = "达拉斯",
            countryCode = "US",
            provider = "Akamai Linode",
            latitude = 32.7767,
            longitude = -96.7970,
            downloadUrls = listOf(linode("dallas"))
        ),
        SpeedTestServer(
            id = "linode-seattle",
            name = "Linode 西雅图",
            city = "西雅图",
            countryCode = "US",
            provider = "Akamai Linode",
            latitude = 47.6062,
            longitude = -122.3321,
            downloadUrls = listOf(linode("seattle"))
        ),
        SpeedTestServer(
            id = "linode-atlanta",
            name = "Linode 亚特兰大",
            city = "亚特兰大",
            countryCode = "US",
            provider = "Akamai Linode",
            latitude = 33.7490,
            longitude = -84.3880,
            downloadUrls = listOf(linode("atlanta"))
        ),
        SpeedTestServer(
            id = "linode-newark",
            name = "Linode 纽瓦克",
            city = "纽瓦克",
            countryCode = "US",
            provider = "Akamai Linode",
            latitude = 40.7357,
            longitude = -74.1724,
            downloadUrls = listOf(linode("newark"))
        ),
        SpeedTestServer(
            id = "linode-london",
            name = "Linode 伦敦",
            city = "伦敦",
            countryCode = "GB",
            provider = "Akamai Linode",
            latitude = 51.5074,
            longitude = -0.1278,
            downloadUrls = listOf(linode("london"))
        ),
        SpeedTestServer(
            id = "linode-paris",
            name = "Linode 巴黎",
            city = "巴黎",
            countryCode = "FR",
            provider = "Akamai Linode",
            latitude = 48.8566,
            longitude = 2.3522,
            downloadUrls = listOf(linode("paris"))
        ),
        SpeedTestServer(
            id = "linode-frankfurt",
            name = "Linode 法兰克福",
            city = "法兰克福",
            countryCode = "DE",
            provider = "Akamai Linode",
            latitude = 50.1109,
            longitude = 8.6821,
            downloadUrls = listOf(linode("frankfurt"))
        ),
        SpeedTestServer(
            id = "linode-sydney",
            name = "Linode 悉尼",
            city = "悉尼",
            countryCode = "AU",
            provider = "Akamai Linode",
            latitude = -33.8688,
            longitude = 151.2093,
            downloadUrls = listOf(linode("sydney"))
        )
    )

    /**
     * 按地理位置挑出最近的 [limit] 台。
     *
     * 任播节点（Cloudflare）排序时按 0 公里处理，所以它一定在候选里 ——
     * 它没有固定机房，"最近"这件事只有运营商的路由表知道，交给实测延迟去判断。
     */
    fun nearest(latitude: Double, longitude: Double, limit: Int): List<SpeedTestServer> =
        servers
            .sortedBy { it.distanceKm(latitude, longitude) }
            .take(limit.coerceAtLeast(1))

    /**
     * 拿不到位置时的兜底顺序：任播优先，后面按「大陆 → 亚太 → 其他」排。
     * 这样即使定位接口全挂了，测速也还能正常跑。
     */
    fun defaultOrder(limit: Int): List<SpeedTestServer> {
        val preferred = listOf("cloudflare", "tuna", "bfsu", "vultr-sel", "vultr-hnd", "vultr-sgp")
        val head = preferred.mapNotNull { id -> servers.firstOrNull { it.id == id } }
        return (head + servers.filterNot { it in head }).take(limit.coerceAtLeast(1))
    }
}
