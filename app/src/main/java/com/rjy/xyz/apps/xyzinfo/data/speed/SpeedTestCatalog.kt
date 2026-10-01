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
 * - **中国大陆**：清华 TUNA、北外（北京）、中科大（合肥）、南科大（深圳）、南大（南京）、
 *   华为云（贵阳）、教育网联通线路 —— 7 台，大陆用户延迟最低、带宽最大；
 * - **亚太**：Vultr 首尔 / 东京 / 新加坡 / 悉尼 / 孟买 / 德里 / 班加罗尔 / 墨尔本，
 *   Linode 东京 / 新加坡 / 孟买（大陆用户出海常走的几条线）；
 * - **美洲 / 欧洲 / 中东**：Vultr 与 Linode 的主力机房（洛杉矶、圣何塞、墨西哥城、圣保罗、
 *   法兰克福、伦敦、巴黎、阿姆斯特丹、马德里、华沙、斯德哥尔摩、特拉维夫……）；
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

    /**
     * Linode / Akamai 的测速站（跑的是 LibreSpeed 内核）。
     *
     * 两个端点都实测可用：
     * - `100MB-<city>.bin`：官方测速大文件，下载用；
     * - `garbage.php?ckSize=100`：LibreSpeed 的随机数据接口，作为下载备用地址；
     * - `empty.php`：**上传端点**（POST 任意长度的二进制即可，返回 200）。
     */
    private fun linodeDownload(city: String) = listOf(
        "https://speedtest.$city.linode.com/100MB-$city.bin",
        "https://speedtest.$city.linode.com/garbage.php?ckSize=100"
    )

    private fun linodeUpload(city: String) =
        listOf("https://speedtest.$city.linode.com/empty.php")

    /**
     * 高校 / 云厂商镜像站的公开大文件。
     *
     * 试过的镜像站里只有清华和北外有专门的 `speedtest/` 目录，其余几家的 `speedtest/`
     * 路径要么 404、要么返回 200 的假页面。但它们都提供 HTTPS 的 Ubuntu 发行版 ISO ——
     * 几 GB 的公开静态文件，拿来测下载带宽完全够用，而且**大陆用户连它们比连境外节点快得多**
     * （实测中科大 750KB/s，同时对境外节点只有 100KB/s 上下）。
     * 每个镜像留两条版本路径，某个版本下架了还有另一条顶上。
     */
    private fun isoMirror(base: String) = listOf(
        "$base/ubuntu-releases/22.04/ubuntu-22.04.5-desktop-amd64.iso",
        "$base/ubuntu-releases/22.04/ubuntu-22.04.5-live-server-amd64.iso"
    )

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
            id = "ustc",
            name = "中科大镜像站",
            city = "合肥",
            countryCode = "CN",
            provider = "中国科学技术大学",
            latitude = 31.8395,
            longitude = 117.2663,
            downloadUrls = isoMirror("https://mirrors.ustc.edu.cn")
        ),
        SpeedTestServer(
            id = "sustech",
            name = "南科大镜像站",
            city = "深圳",
            countryCode = "CN",
            provider = "南方科技大学",
            latitude = 22.6019,
            longitude = 113.9949,
            downloadUrls = isoMirror("https://mirrors.sustech.edu.cn")
        ),
        SpeedTestServer(
            id = "nju",
            name = "南大镜像站",
            city = "南京",
            countryCode = "CN",
            provider = "南京大学",
            latitude = 32.0567,
            longitude = 118.7784,
            downloadUrls = isoMirror("https://mirrors.nju.edu.cn")
        ),
        SpeedTestServer(
            id = "huawei",
            name = "华为云镜像站",
            city = "贵阳",
            countryCode = "CN",
            provider = "华为云",
            latitude = 26.6470,
            longitude = 106.6302,
            downloadUrls = isoMirror("https://mirrors.huaweicloud.com")
        ),
        SpeedTestServer(
            id = "cernet",
            name = "教育网联通线路",
            city = "合肥",
            countryCode = "CN",
            provider = "CERNET",
            latitude = 31.8395,
            longitude = 117.2663,
            downloadUrls = isoMirror("https://mirrors.cernet.edu.cn")
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
            id = "vultr-ams",
            name = "Vultr 阿姆斯特丹",
            city = "阿姆斯特丹",
            countryCode = "NL",
            provider = "Vultr",
            latitude = 52.3676,
            longitude = 4.9041,
            downloadUrls = listOf(vultr("ams-nl-ping"))
        ),
        SpeedTestServer(
            id = "vultr-par",
            name = "Vultr 巴黎",
            city = "巴黎",
            countryCode = "FR",
            provider = "Vultr",
            latitude = 48.8566,
            longitude = 2.3522,
            downloadUrls = listOf(vultr("par-fr-ping"))
        ),
        SpeedTestServer(
            id = "vultr-mad",
            name = "Vultr 马德里",
            city = "马德里",
            countryCode = "ES",
            provider = "Vultr",
            latitude = 40.4168,
            longitude = -3.7038,
            downloadUrls = listOf(vultr("mad-es-ping"))
        ),
        SpeedTestServer(
            id = "vultr-waw",
            name = "Vultr 华沙",
            city = "华沙",
            countryCode = "PL",
            provider = "Vultr",
            latitude = 52.2297,
            longitude = 21.0122,
            downloadUrls = listOf(vultr("waw-pl-ping"))
        ),
        SpeedTestServer(
            id = "vultr-sto",
            name = "Vultr 斯德哥尔摩",
            city = "斯德哥尔摩",
            countryCode = "SE",
            provider = "Vultr",
            latitude = 59.3293,
            longitude = 18.0686,
            downloadUrls = listOf(vultr("sto-se-ping"))
        ),
        SpeedTestServer(
            id = "vultr-tlv",
            name = "Vultr 特拉维夫",
            city = "特拉维夫",
            countryCode = "IL",
            provider = "Vultr",
            latitude = 32.0853,
            longitude = 34.7818,
            downloadUrls = listOf(vultr("tlv-il-ping"))
        ),
        SpeedTestServer(
            id = "vultr-bom",
            name = "Vultr 孟买",
            city = "孟买",
            countryCode = "IN",
            provider = "Vultr",
            latitude = 19.0760,
            longitude = 72.8777,
            downloadUrls = listOf(vultr("bom-in-ping"))
        ),
        SpeedTestServer(
            id = "vultr-del",
            name = "Vultr 德里",
            city = "德里",
            countryCode = "IN",
            provider = "Vultr",
            latitude = 28.6139,
            longitude = 77.2090,
            downloadUrls = listOf(vultr("del-in-ping"))
        ),
        SpeedTestServer(
            id = "vultr-blr",
            name = "Vultr 班加罗尔",
            city = "班加罗尔",
            countryCode = "IN",
            provider = "Vultr",
            latitude = 12.9716,
            longitude = 77.5946,
            downloadUrls = listOf(vultr("blr-in-ping"))
        ),
        SpeedTestServer(
            id = "vultr-mel",
            name = "Vultr 墨尔本",
            city = "墨尔本",
            countryCode = "AU",
            provider = "Vultr",
            latitude = -37.8136,
            longitude = 144.9631,
            downloadUrls = listOf(vultr("mel-au-ping"))
        ),
        SpeedTestServer(
            id = "vultr-mex",
            name = "Vultr 墨西哥城",
            city = "墨西哥城",
            countryCode = "MX",
            provider = "Vultr",
            latitude = 19.4326,
            longitude = -99.1332,
            downloadUrls = listOf(vultr("mex-mx-ping"))
        ),
        SpeedTestServer(
            id = "vultr-sao",
            name = "Vultr 圣保罗",
            city = "圣保罗",
            countryCode = "BR",
            provider = "Vultr",
            latitude = -23.5505,
            longitude = -46.6333,
            downloadUrls = listOf(vultr("sao-br-ping"))
        ),
        SpeedTestServer(
            id = "linode-fremont",
            name = "Linode 弗里蒙特",
            city = "弗里蒙特",
            countryCode = "US",
            provider = "Akamai Linode",
            latitude = 37.5485,
            longitude = -121.9886,
            downloadUrls = linodeDownload("fremont"),
            uploadUrls = linodeUpload("fremont")
        ),
        SpeedTestServer(
            id = "linode-dallas",
            name = "Linode 达拉斯",
            city = "达拉斯",
            countryCode = "US",
            provider = "Akamai Linode",
            latitude = 32.7767,
            longitude = -96.7970,
            downloadUrls = linodeDownload("dallas"),
            uploadUrls = linodeUpload("dallas")
        ),
        SpeedTestServer(
            id = "linode-seattle",
            name = "Linode 西雅图",
            city = "西雅图",
            countryCode = "US",
            provider = "Akamai Linode",
            latitude = 47.6062,
            longitude = -122.3321,
            downloadUrls = linodeDownload("seattle"),
            uploadUrls = linodeUpload("seattle")
        ),
        SpeedTestServer(
            id = "linode-atlanta",
            name = "Linode 亚特兰大",
            city = "亚特兰大",
            countryCode = "US",
            provider = "Akamai Linode",
            latitude = 33.7490,
            longitude = -84.3880,
            downloadUrls = linodeDownload("atlanta"),
            uploadUrls = linodeUpload("atlanta")
        ),
        SpeedTestServer(
            id = "linode-newark",
            name = "Linode 纽瓦克",
            city = "纽瓦克",
            countryCode = "US",
            provider = "Akamai Linode",
            latitude = 40.7357,
            longitude = -74.1724,
            downloadUrls = linodeDownload("newark"),
            uploadUrls = linodeUpload("newark")
        ),
        SpeedTestServer(
            id = "linode-london",
            name = "Linode 伦敦",
            city = "伦敦",
            countryCode = "GB",
            provider = "Akamai Linode",
            latitude = 51.5074,
            longitude = -0.1278,
            downloadUrls = linodeDownload("london"),
            uploadUrls = linodeUpload("london")
        ),
        SpeedTestServer(
            id = "linode-paris",
            name = "Linode 巴黎",
            city = "巴黎",
            countryCode = "FR",
            provider = "Akamai Linode",
            latitude = 48.8566,
            longitude = 2.3522,
            downloadUrls = linodeDownload("paris"),
            uploadUrls = linodeUpload("paris")
        ),
        SpeedTestServer(
            id = "linode-frankfurt",
            name = "Linode 法兰克福",
            city = "法兰克福",
            countryCode = "DE",
            provider = "Akamai Linode",
            latitude = 50.1109,
            longitude = 8.6821,
            downloadUrls = linodeDownload("frankfurt"),
            uploadUrls = linodeUpload("frankfurt")
        ),
        SpeedTestServer(
            id = "linode-sydney",
            name = "Linode 悉尼",
            city = "悉尼",
            countryCode = "AU",
            provider = "Akamai Linode",
            latitude = -33.8688,
            longitude = 151.2093,
            downloadUrls = linodeDownload("sydney"),
            uploadUrls = linodeUpload("sydney")
        ),
        SpeedTestServer(
            id = "linode-tokyo2",
            name = "Linode 东京",
            city = "东京",
            countryCode = "JP",
            provider = "Akamai Linode",
            latitude = 35.6762,
            longitude = 139.6503,
            downloadUrls = linodeDownload("tokyo2"),
            uploadUrls = linodeUpload("tokyo2")
        ),
        SpeedTestServer(
            id = "linode-singapore",
            name = "Linode 新加坡",
            city = "新加坡",
            countryCode = "SG",
            provider = "Akamai Linode",
            latitude = 1.3521,
            longitude = 103.8198,
            downloadUrls = linodeDownload("singapore"),
            uploadUrls = linodeUpload("singapore")
        ),
        SpeedTestServer(
            id = "linode-mumbai1",
            name = "Linode 孟买",
            city = "孟买",
            countryCode = "IN",
            provider = "Akamai Linode",
            latitude = 19.0760,
            longitude = 72.8777,
            downloadUrls = linodeDownload("mumbai1"),
            uploadUrls = linodeUpload("mumbai1")
        ),
        SpeedTestServer(
            id = "linode-toronto1",
            name = "Linode 多伦多",
            city = "多伦多",
            countryCode = "CA",
            provider = "Akamai Linode",
            latitude = 43.6532,
            longitude = -79.3832,
            downloadUrls = linodeDownload("toronto1"),
            uploadUrls = linodeUpload("toronto1")
        ),
        SpeedTestServer(
            id = "linode-osaka",
            name = "Linode 大阪",
            city = "大阪",
            countryCode = "JP",
            provider = "Akamai Linode",
            latitude = 34.6937,
            longitude = 135.5023,
            downloadUrls = linodeDownload("osaka"),
            uploadUrls = linodeUpload("osaka")
        ),
        SpeedTestServer(
            id = "linode-jakarta",
            name = "Linode 雅加达",
            city = "雅加达",
            countryCode = "ID",
            provider = "Akamai Linode",
            latitude = -6.2088,
            longitude = 106.8456,
            downloadUrls = linodeDownload("jakarta"),
            uploadUrls = linodeUpload("jakarta")
        ),
        SpeedTestServer(
            id = "linode-madrid",
            name = "Linode 马德里",
            city = "马德里",
            countryCode = "ES",
            provider = "Akamai Linode",
            latitude = 40.4168,
            longitude = -3.7038,
            downloadUrls = linodeDownload("madrid"),
            uploadUrls = linodeUpload("madrid")
        ),
        SpeedTestServer(
            id = "linode-milan",
            name = "Linode 米兰",
            city = "米兰",
            countryCode = "IT",
            provider = "Akamai Linode",
            latitude = 45.4642,
            longitude = 9.1900,
            downloadUrls = linodeDownload("milan"),
            uploadUrls = linodeUpload("milan")
        ),
        SpeedTestServer(
            id = "linode-stockholm",
            name = "Linode 斯德哥尔摩",
            city = "斯德哥尔摩",
            countryCode = "SE",
            provider = "Akamai Linode",
            latitude = 59.3293,
            longitude = 18.0686,
            downloadUrls = linodeDownload("stockholm"),
            uploadUrls = linodeUpload("stockholm")
        ),
        SpeedTestServer(
            id = "linode-chennai",
            name = "Linode 金奈",
            city = "金奈",
            countryCode = "IN",
            provider = "Akamai Linode",
            latitude = 13.0827,
            longitude = 80.2707,
            downloadUrls = linodeDownload("chennai"),
            uploadUrls = linodeUpload("chennai")
        ),
        SpeedTestServer(
            id = "vultr-mxp",
            name = "Vultr 米兰",
            city = "米兰",
            countryCode = "IT",
            provider = "Vultr",
            latitude = 45.4642,
            longitude = 9.1900,
            downloadUrls = listOf(vultr("mxp-it-ping"))
        ),
        SpeedTestServer(
            id = "vultr-jnb",
            name = "Vultr 约翰内斯堡",
            city = "约翰内斯堡",
            countryCode = "ZA",
            provider = "Vultr",
            latitude = -26.2041,
            longitude = 28.0473,
            downloadUrls = listOf(vultr("jnb-za-ping"))
        ),
        SpeedTestServer(
            id = "vultr-scl",
            name = "Vultr 圣地亚哥",
            city = "圣地亚哥",
            countryCode = "CL",
            provider = "Vultr",
            latitude = -33.4489,
            longitude = -70.6693,
            downloadUrls = listOf(vultr("scl-cl-ping"))
        ),
        SpeedTestServer(
            id = "vultr-hon",
            name = "Vultr 檀香山",
            city = "檀香山",
            countryCode = "US",
            provider = "Vultr",
            latitude = 21.3069,
            longitude = -157.8583,
            downloadUrls = listOf(vultr("hon-hi-us-ping"))
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
        // 定位失败时也要能测出个像样的结果：先任播，再大陆几台快镜像，最后亚太
        val preferred = listOf(
            "cloudflare", "ustc", "tuna", "bfsu", "nju", "sustech", "huawei", "cernet",
            "vultr-sel", "vultr-hnd", "vultr-sgp"
        )
        val head = preferred.mapNotNull { id -> servers.firstOrNull { it.id == id } }
        return (head + servers.filterNot { it in head }).take(limit.coerceAtLeast(1))
    }

    /**
     * 挑一台**支持上传测速**的节点。
     *
     * 为什么要专门挑：下载节点满地都是（镜像站、Vultr 都只给下载），但能接收上传的很少 ——
     * 目前是 Cloudflare 与全部 Linode 节点（跑 LibreSpeed 内核，`empty.php` 接收 POST）。
     * 测速时如果用户选的节点不支持上传，就自动换成最近的一台能上传的，
     * 界面会注明"上行用的是哪台"，不再直接显示"该节点只提供下载"。
     */
    fun nearestUploadCapable(latitude: Double?, longitude: Double?): SpeedTestServer? {
        val candidates = servers.filter { it.supportsUpload }
        if (candidates.isEmpty()) return null
        if (latitude == null || longitude == null) return candidates.first()
        return candidates.minByOrNull { it.distanceKm(latitude, longitude) }
    }
}
