package com.rjy.xyz.apps.xyzinfo.ui.common

import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.data.DeviceNameRepository
import com.rjy.xyz.apps.xyzinfo.data.DeviceNameUpdater

/**
 * 机型库更新的界面逻辑：首页与设置页共用同一套（状态展示 / 更新 / 恢复内置）。
 */
object UpdateControls {

    /** 刷新「数据来源 / 版本 / 收录条数」这一行。 */
    fun refreshStatus(activity: AppCompatActivity, status: TextView) {
        Thread({
            DeviceNameRepository.load(activity)
            val source = DeviceNameRepository.dataSource(activity)
            val version = DeviceNameUpdater.localVersion(activity)
            val entries = DeviceNameRepository.entryCount()
            val online = DeviceNameUpdater.hasNetwork(activity)
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                status.setInfoRow(
                    "数据来源：$source\n版本：$version ｜ 已收录：$entries 条\n网络：${if (online) "可用" else "不可用"}"
                )
            }
        }, "xyzinfo-data-status").start()
    }

    /**
     * 绑定「更新」按钮。
     *
     * @param onUpdated 更新成功后回调，用于重新查一次机型名。
     */
    fun attachUpdate(
        activity: AppCompatActivity,
        button: View,
        status: TextView,
        onUpdated: (() -> Unit)? = null
    ) {
        button.setOnClickListener {
            button.isEnabled = false
            button.alpha = 0.6f
            status.setInfoRow("更新状态：正在连接更新服务器…")
            Anim.pressFeedback(button)
            Thread({
                val result = DeviceNameUpdater.update(activity)
                activity.runOnUiThread {
                    if (activity.isFinishing) return@runOnUiThread
                    button.isEnabled = true
                    button.animate().alpha(1f).setDuration(Anim.DURATION_SHORT).start()
                    status.setInfoRow("更新状态：${result.message}")
                    if (result.success) onUpdated?.invoke()
                }
                if (result.success) refreshStatus(activity, status)
            }, "xyzinfo-data-update").start()
        }
    }

    /** 绑定「恢复内置机型库」按钮。 */
    fun attachReset(
        activity: AppCompatActivity,
        button: View,
        status: TextView,
        onUpdated: (() -> Unit)? = null
    ) {
        button.setOnClickListener {
            button.isEnabled = false
            val removed = DeviceNameUpdater.resetToBuiltIn(activity)
            button.isEnabled = true
            status.setInfoRow(
                "更新状态：" + if (removed) "已删除下载数据，恢复使用内置机型库" else "当前本来就在用内置机型库"
            )
            onUpdated?.invoke()
            refreshStatus(activity, status)
        }
    }
}
