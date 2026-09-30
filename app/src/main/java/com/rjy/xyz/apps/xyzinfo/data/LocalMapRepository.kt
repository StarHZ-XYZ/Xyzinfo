package com.rjy.xyz.apps.xyzinfo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 内置本地地图包。
 *
 * 每张地图都带自己的**经纬度边界（bbox）**，投影按各自的 bbox 算，
 * 所以显示某个国家/区域时不会再把全世界硬拉伸过去，也不存在「标记跑到别的大洲」。
 *
 * 图放在 `assets/maps/<code>.png`，由 `_tools\make_region_maps.ps1` 从世界地图按 bbox 裁出；
 * 以后要更高精度，把同样 bbox 的高清图按同名丢进那个目录即可，代码不用改。
 */
object LocalMapRepository {

    data class MapPack(
        val code: String,
        val label: String,
        val left: Double,
        val top: Double,
        val right: Double,
        val bottom: Double,
        val asset: String
    ) {
        val spanLon: Double get() = right - left
        val spanLat: Double get() = top - bottom
        val area: Double get() = spanLon * spanLat

        fun contains(lat: Double, lon: Double): Boolean =
            lon in left..right && lat in bottom..top
    }

    /** 全世界地图：任何位置都兜底命中，保证不会白屏。 */
    val WORLD = MapPack("world", "世界", -180.0, 90.0, 180.0, -90.0, "world_map.png")

    /** 国家 / 区域包（命中多个时取面积最小的，所以「中国」优先于「东亚」）。 */
    val PACKS: List<MapPack> = listOf(
        MapPack("cn", "中国", 73.0, 53.0, 136.0, 18.0, "maps/cn.png"),
        MapPack("jp", "日本", 122.0, 46.0, 150.0, 24.0, "maps/jp.png"),
        MapPack("kr", "韩国与朝鲜", 124.0, 44.0, 132.0, 33.0, "maps/kr.png"),
        MapPack("in", "印度", 68.0, 36.0, 98.0, 6.0, "maps/in.png"),
        MapPack("us", "美国本土", -125.0, 50.0, -66.0, 24.0, "maps/us.png"),
        MapPack("br", "巴西", -74.0, 6.0, -34.0, -34.0, "maps/br.png"),
        MapPack("au", "澳大利亚", 112.0, -10.0, 154.0, -44.0, "maps/au.png"),
        MapPack("ru", "俄罗斯", 20.0, 82.0, 180.0, 41.0, "maps/ru.png"),
        MapPack("ea", "东亚", 70.0, 55.0, 150.0, 5.0, "maps/ea.png"),
        MapPack("sea", "东南亚", 90.0, 25.0, 145.0, -12.0, "maps/sea.png"),
        MapPack("sa", "南亚", 60.0, 40.0, 95.0, 5.0, "maps/sa.png"),
        MapPack("me", "中东", 25.0, 45.0, 65.0, 12.0, "maps/me.png"),
        MapPack("eu", "欧洲", -12.0, 72.0, 45.0, 34.0, "maps/eu.png"),
        MapPack("af", "非洲", -20.0, 38.0, 55.0, -38.0, "maps/af.png"),
        MapPack("na", "北美洲", -170.0, 75.0, -50.0, 5.0, "maps/na.png"),
        MapPack("sa2", "南美洲", -85.0, 15.0, -30.0, -58.0, "maps/sa2.png"),
        MapPack("oc", "大洋洲", 110.0, 0.0, 180.0, -50.0, "maps/oc.png")
    )

    /**
     * 按当前位置挑地图：命中多个取面积最小的（国家 > 区域 > 世界）。
     * 没有定位就返回世界地图。
     */
    fun findFor(lat: Double?, lon: Double?): MapPack {
        if (lat == null || lon == null) return WORLD
        return PACKS.filter { it.contains(lat, lon) }.minByOrNull { it.area } ?: WORLD
    }

    fun load(context: Context, pack: MapPack): Bitmap? = runCatching {
        context.assets.open(pack.asset).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}
