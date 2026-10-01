package com.rjy.xyz.apps.xyzinfo.data.speed

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 一台公共测速服务器（节点）。
 *
 * 目录里的每一台都是**实测能连通的公开节点**（HTTPS、不需要密钥、不限制第三方测速），
 * 具体清单见 [SpeedTestCatalog]。字段说明：
 *
 * @param downloadUrls 依次尝试的下载地址（有的镜像会调整文件名，留多个候选更抗变化）；
 * @param uploadUrls 上传测速地址，为空表示该节点不支持上传测速（大部分校园镜像不接收上传）。
 * @param anycast 任播节点（如 Cloudflare）：不绑定单一机房，运营商网络会自动把请求送到最近的
 *   接入点，所以它没有「距离」可言，进入候选列表时按 0 公里处理，最终由实测延迟定胜负。
 */
data class SpeedTestServer(
    val id: String,
    val name: String,
    val city: String,
    val countryCode: String,
    val provider: String,
    val latitude: Double,
    val longitude: Double,
    val downloadUrls: List<String>,
    val uploadUrls: List<String> = emptyList(),
    val anycast: Boolean = false
) {

    /** 能不能测上传。 */
    val supportsUpload: Boolean get() = uploadUrls.isNotEmpty()

    /** 归属地一行字，例如「北京 · 清华大学 TUNA」。 */
    val placeText: String
        get() = listOf(city, provider).filter { it.isNotBlank() }.distinct().joinToString(" · ")

    /** 到某个坐标的大圆距离（公里）；任播节点没有固定机房，返回 0。 */
    fun distanceKm(latitude: Double, longitude: Double): Double =
        if (anycast) 0.0 else Geodesy.distanceKm(latitude, longitude, this.latitude, this.longitude)
}

/**
 * 球面距离计算（Haversine）。
 *
 * 选服务器只需要「谁离我近」这个量级判断，Haversine 的误差（<0.5%）完全可以忽略，
 * 不需要引入更复杂的椭球模型。
 */
object Geodesy {

    private const val EARTH_RADIUS_KM = 6371.0088

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(a)))
    }

    /** 距离文案：近处精确到公里，远处取整到十公里，读起来才不啰嗦。 */
    fun distanceText(km: Double): String = when {
        km < 1.0 -> "本机附近"
        km < 1000.0 -> "${km.roundToInt()} 公里"
        else -> String.format(Locale.getDefault(), "%,.0f 公里", km)
    }
}
