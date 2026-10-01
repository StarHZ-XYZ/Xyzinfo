package com.rjy.xyz.apps.xyzinfo.data.speed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 测速节点目录的结构检查。
 *
 * 这里不联网（单元测试跑在 CI / 无网环境），只保证目录本身自洽：
 * 地址合法、ID 唯一、就近排序符合预期。真正的「这台还能不能连」靠发布前手工验过一遍。
 */
class SpeedTestCatalogTest {

    private val servers = SpeedTestCatalog.servers

    @Test
    fun `节点数量够多且ID唯一`() {
        assertTrue("公共节点太少，至少要有 10 台", servers.size >= 10)
        val ids = servers.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `所有地址都是HTTPS`() {
        // 清单里 usesCleartextTraffic=false，只要有一个 http:// 这台节点在真机上必然连不上
        servers.forEach { server ->
            assertTrue("${server.name} 没有下载地址", server.downloadUrls.isNotEmpty())
            (server.downloadUrls + server.uploadUrls).forEach { url ->
                assertTrue("${server.name} 的地址不是 HTTPS：$url", url.startsWith("https://"))
            }
        }
    }

    @Test
    fun `经纬度在合法范围内`() {
        servers.filterNot { it.anycast }.forEach { server ->
            assertTrue(server.name, server.latitude in -90.0..90.0)
            assertTrue(server.name, server.longitude in -180.0..180.0)
        }
    }

    @Test
    fun `上传只出现在明确支持的节点上`() {
        servers.filter { it.supportsUpload }.forEach { server ->
            assertTrue("支持上传的节点必须有上传地址", server.uploadUrls.isNotEmpty())
        }
        // 校园镜像只提供下载，不该被标成支持上传（否则界面会显示一个假的上传成绩）
        val tuna = servers.first { it.id == "tuna" }
        assertFalse(tuna.supportsUpload)
    }

    @Test
    fun `按北京定位_最近的两台都是北京镜像`() {
        val nearest = SpeedTestCatalog.nearest(39.9042, 116.4074, 8)
        assertEquals(8, nearest.size)
        // 任播节点固定排在最前（它自动就近，用实测延迟定胜负）
        assertTrue(nearest.first().anycast)
        /*
         * 北京城内最近的两台固定节点就是清华 TUNA 与北外两座镜像站（都在 20 公里内）。
         * 不写死具体谁排第一：站在中关村还是站在魏公村，两台谁更近是会变的，
         * 这种微小差异不该让测试变脆。
         */
        val fixedClosest = nearest.filterNot { it.anycast }.take(2).map { it.id }.toSet()
        assertEquals(setOf("tuna", "bfsu"), fixedClosest)
        val firstFixed = nearest.first { !it.anycast }
        assertTrue(
            "最近的固定节点应在 20 公里内，实际 ${firstFixed.distanceKm(39.9042, 116.4074)}",
            firstFixed.distanceKm(39.9042, 116.4074) < 20.0
        )
    }

    @Test
    fun `按法兰克福定位_最近的是欧洲节点`() {
        val nearest = SpeedTestCatalog.nearest(50.1109, 8.6821, 3)
        val ids = nearest.map { it.id }
        assertTrue(ids.any { it == "linode-frankfurt" || it == "vultr-fra" })
        // 大陆校园镜像不该出现在欧洲用户的最近列表里
        assertFalse(ids.contains("tuna"))
    }

    @Test
    fun `拿不到定位时的兜底顺序可用来测速`() {
        val fallback = SpeedTestCatalog.defaultOrder(4)
        assertEquals(4, fallback.size)
        assertTrue(fallback.first().anycast)
        assertEquals("tuna", fallback[1].id)
    }

    @Test
    fun `距离换算与文案`() {
        // 北京 → 上海 约 1060~1100 公里
        val beijingToShanghai = Geodesy.distanceKm(39.9042, 116.4074, 31.2304, 121.4737)
        assertTrue("实际算得 $beijingToShanghai", beijingToShanghai in 1000.0..1150.0)
        assertEquals(0.0, Geodesy.distanceKm(39.9042, 116.4074, 39.9042, 116.4074), 1e-6)

        assertEquals("本机附近", Geodesy.distanceText(0.4))
        assertEquals("36 公里", Geodesy.distanceText(36.4))
        assertTrue(Geodesy.distanceText(1850.0).endsWith("公里"))
    }
}
