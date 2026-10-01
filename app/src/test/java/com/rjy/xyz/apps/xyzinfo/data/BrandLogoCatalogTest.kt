package com.rjy.xyz.apps.xyzinfo.data

import android.graphics.drawable.LayerDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 品牌 logo 的可读性规则。
 *
 * 这一块是 1.0.4 的真实问题：OPPO / 索尼 / 荣耀是纯黑字标，深色模式下会糊进背景；
 * Nothing / vivo / 黑莓是白色字标，浅色卡片上直接看不见。修法是给这几家垫一块对比色底板，
 * 而不是强行改 logo 的颜色（改色品牌辨识度就没了）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrandLogoCatalogTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun brand(name: String) =
        BrandLogoCatalog.BRANDS.first { it.displayName == name }

    @Test
    fun `近黑logo垫浅色板_近白logo垫深色板`() {
        // 纯黑字标 → 需要浅色底板（深色模式下才看得见）
        listOf("OPPO", "索尼", "荣耀").forEach { name ->
            assertEquals("$name 应该垫浅色板", 1, brand(name).logoTile)
        }
        // 白色字标 → 需要深色底板（浅色卡片上才看得见）
        listOf("Nothing", "vivo", "黑莓").forEach { name ->
            assertEquals("$name 应该垫深色板", 2, brand(name).logoTile)
        }
    }

    @Test
    fun `彩色logo不垫底板`() {
        // 这些是自带品牌色的手机 logo，白底深底都看得清，不需要底板
        listOf("小米", "三星", "一加", "黑鲨").forEach { name ->
            assertEquals("$name 是彩色的，不该垫底板", 0, brand(name).logoTile)
        }
    }

    @Test
    fun `芯片厂商logo也有内置资源`() {
        val chipBrands = listOf(
            com.rjy.xyz.apps.xyzinfo.model.SocBrand.SNAPDRAGON,
            com.rjy.xyz.apps.xyzinfo.model.SocBrand.MEDIATEK,
            com.rjy.xyz.apps.xyzinfo.model.SocBrand.KIRIN,
            com.rjy.xyz.apps.xyzinfo.model.SocBrand.UNISOC
        )
        chipBrands.forEach { soc ->
            assertTrue(
                "${ChipLogoCatalog.displayName(soc)} 应该有内置 logo",
                ChipLogoCatalog.logoOf(soc) != 0
            )
            assertNotNull(
                androidx.appcompat.content.res.AppCompatResources.getDrawable(
                    context, ChipLogoCatalog.logoOf(soc)
                )
            )
        }
    }

    @Test
    fun `需要底板的logo渲染成两层_不需要的只有logo本身`() {
        val oppo = BrandLogoCatalog.drawable(context, brand("OPPO"), 78)
        assertTrue("OPPO 应该是「底板 + logo」两层", oppo is LayerDrawable)
        assertEquals(2, (oppo as LayerDrawable).numberOfLayers)

        val xiaomi = BrandLogoCatalog.drawable(context, brand("小米"), 78)
        assertFalse("小米是彩色 logo，不该多一层底板", xiaomi is LayerDrawable)
    }

    @Test
    fun `所有内置logo资源都能取到`() {
        BrandLogoCatalog.BRANDS.filter { it.logoRes != 0 }.forEach { item ->
            assertNotNull("${item.displayName} 的 logo 取不到", BrandLogoCatalog.drawable(context, item, 64))
        }
        assertTrue("内置 logo 数量偏少", BrandLogoCatalog.BRANDS.count { it.logoRes != 0 } >= 20)
    }
}
