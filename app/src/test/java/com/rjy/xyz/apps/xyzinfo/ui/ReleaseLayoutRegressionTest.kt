package com.rjy.xyz.apps.xyzinfo.ui

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

    private fun locationInWindow(view: View): IntArray =
        IntArray(2).also(view::getLocationInWindow)
}
