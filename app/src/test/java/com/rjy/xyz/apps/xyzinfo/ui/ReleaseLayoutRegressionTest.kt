package com.rjy.xyz.apps.xyzinfo.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.LayerDrawable
import android.view.View
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.DeviceCatalog
import com.rjy.xyz.apps.xyzinfo.ui.compare.DeviceCatalogAdapter
import com.rjy.xyz.apps.xyzinfo.ui.compare.DeviceCompareActivity
import com.rjy.xyz.apps.xyzinfo.ui.share.ShareCardActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class ReleaseLayoutRegressionTest {

    @Test
    fun `启动 Logo 固定为圆角方形图层`() {
        val context = RuntimeEnvironment.getApplication()
        assertTrue(ContextCompat.getDrawable(context, R.drawable.ic_splash_logo) is LayerDrawable)
    }

    @Test
    fun `分享卡片底部按钮为两行等宽布局`() {
        val activity = Robolectric.buildActivity(ShareCardActivity::class.java).setup().get()
        val root = activity.window.decorView
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 1080, 2400)
        val save = activity.findViewById<View>(R.id.btnSaveCard)
        val share = activity.findViewById<View>(R.id.btnShareCard)
        val copy = activity.findViewById<View>(R.id.btnCopyCard)
        val compare = activity.findViewById<View>(R.id.btnCompare)
        val saveLocation = locationInWindow(save)
        val shareLocation = locationInWindow(share)
        val copyLocation = locationInWindow(copy)
        val compareLocation = locationInWindow(compare)
        assertEquals(saveLocation[1], shareLocation[1])
        assertTrue(
            "第二行应在第一行下方：save=${saveLocation[1] + save.height}, " +
                "copy=${copyLocation[1]}",
            copyLocation[1] > saveLocation[1] + save.height
        )
        assertEquals(copyLocation[1], compareLocation[1])
        assertTrue("保存按钮宽度异常", save.width > 300)
        assertEquals(save.height, share.height)
        activity.finish()
    }

    @Test
    fun `内置机型入口在窄屏上完整显示`() {
        val activity = Robolectric.buildActivity(DeviceCompareActivity::class.java).setup().get()
        val root = activity.window.decorView
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 1080, 2400)
        val button = activity.findViewById<android.widget.TextView>(R.id.btnBuiltInB)
        val location = locationInWindow(button)
        assertTrue("内置机型入口宽度异常", button.width > 800)
        assertTrue("内置机型入口超出屏幕", location[0] + button.width <= root.width)
        assertTrue(
            "内置机型入口文字被截断",
            button.paint.measureText(button.text.toString()) <=
                button.width - button.totalPaddingLeft - button.totalPaddingRight
        )
        activity.finish()
    }

    @Test
    fun `选择内置机型后右侧对比表立即刷新`() {
        val activity = Robolectric.buildActivity(DeviceCompareActivity::class.java).setup().get()
        val selected = DeviceCatalog.load(activity).first()
        activity.selectBuiltInDevice(selected)
        assertEquals(selected.name, activity.findViewById<android.widget.TextView>(R.id.tvDeviceB).text)
        val list = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvCompare)
        assertTrue("选择机型后对比表为空", (list.adapter?.itemCount ?: 0) > 1)
        assertTrue(activity.findViewById<View>(R.id.btnCopyComparison).isEnabled)
        assertTrue(activity.findViewById<View>(R.id.btnShareComparison).isEnabled)
        activity.finish()
    }

    @Test
    fun `机型库弹窗按品牌和关键词筛选`() {
        val activity = Robolectric.buildActivity(DeviceCompareActivity::class.java).setup().get()
        activity.findViewById<View>(R.id.btnBuiltInB).performClick()
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("机型库弹窗没有显示", dialog)
        val list = dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(
            R.id.rvDeviceCatalog
        )
        assertEquals(DeviceCatalog.count(activity), list.adapter?.itemCount)
        val brandRow = dialog.findViewById<android.widget.LinearLayout>(R.id.layoutCatalogBrands)
        assertEquals(DeviceCatalog.brands(activity).size + 1, brandRow.childCount)

        dialog.findViewById<android.widget.EditText>(R.id.etDeviceSearch).setText("荣耀")
        val honor = DeviceCatalog.search(activity, "荣耀")
        assertEquals(honor.size, list.adapter?.itemCount)
        val catalogAdapter = list.adapter as DeviceCatalogAdapter
        val holder = catalogAdapter.onCreateViewHolder(list, 0)
        catalogAdapter.onBindViewHolder(holder, 0)
        holder.itemView.performClick()
        assertEquals(
            honor.first().name,
            activity.findViewById<android.widget.TextView>(R.id.tvDeviceB).text
        )
        assertFalse("选中机型后弹窗应关闭", dialog.isShowing)
        activity.finish()
    }

    @Test
    fun `主页设备摘要可一键复制`() {
        val activity = Robolectric.buildActivity(
            com.rjy.xyz.apps.xyzinfo.MainActivity::class.java
        ).setup().get()
        assertEquals("设备检测", activity.findViewById<android.widget.TextView>(R.id.tvTitle).text)
        activity.findViewById<View>(R.id.btnCopySummary).performClick()
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(activity)?.toString().orEmpty()
        assertTrue(text.startsWith("XYZ-Devinfo 设备摘要"))
        assertTrue(text.contains("Android："))
        assertTrue(text.contains("设备形态："))
        activity.finish()
    }

    @Test
    fun `主页卡片标题与说明保持垂直排版`() {
        val activity = Robolectric.buildActivity(
            com.rjy.xyz.apps.xyzinfo.MainActivity::class.java
        ).setup().get()
        val root = activity.window.decorView
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 1080, 2400)
        listOf(
            R.id.cardCpu,
            R.id.cardMemory,
            R.id.cardScreen,
            R.id.cardBattery,
            R.id.cardSensor,
            R.id.cardTelephony,
            R.id.cardSystem,
            R.id.cardGps
        ).forEach { cardId ->
            val card = activity.findViewById<View>(cardId)
            val texts = textViewsIn(card)
            assertTrue("卡片 $cardId 缺少标题与说明", texts.size >= 2)
            texts.forEach {
                assertTrue("卡片文字尺寸为 0", it.width > 0 && it.height > 0)
                assertTrue("卡片文字超过两行", it.lineCount <= 2)
            }
            assertTrue("卡片说明与标题重叠", texts[1].top >= texts[0].bottom)
        }
        val copy = activity.findViewById<View>(R.id.btnCopySummary)
        val share = activity.findViewById<View>(R.id.btnShareCard)
        val copyLocation = locationInWindow(copy)
        val shareLocation = locationInWindow(share)
        assertTrue("摘要与分享按钮重叠", copyLocation[0] + copy.width <= shareLocation[0])
        activity.finish()
    }

    private fun textViewsIn(view: View): List<android.widget.TextView> = buildList {
        if (view is android.widget.TextView) add(view)
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) addAll(textViewsIn(view.getChildAt(index)))
        }
    }

    private fun locationInWindow(view: View): IntArray =
        IntArray(2).also(view::getLocationInWindow)
}
