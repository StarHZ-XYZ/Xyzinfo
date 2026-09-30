package com.rjy.xyz.apps.xyzinfo.data

import android.content.Context

/**
 * 开屏预加载：把首页要用的数据在开屏动画期间全部读好。
 *
 * 之前首页是「先进页面、后台线程再查机型名」，于是会先闪一下「未知设备」再变。
 * 现在改成开屏期间把这些全部加载完，动画放完时首页直接是最终状态。
 *
 * 每一步都单独 try：某一项失败（比如机型库文件损坏）不能让开屏卡住。
 */
object AppPreloader {

    /** 顺序有讲究：机型库最重（解压 420KB 压缩数据），放最前面。 */
    fun warmUp(context: Context, onStep: ((String) -> Unit)? = null) {
        step(onStep, "正在载入机型库…") {
            DeviceNameRepository.load(context)
        }
        step(onStep, "正在识别机型名…") {
            DeviceNameRepository.lookup(context, android.os.Build.DEVICE, android.os.Build.MODEL)
        }
        step(onStep, "正在识别系统与芯片…") {
            DeviceOverviewProvider.load()
            RomInfoProvider.load()
            SocInfoProvider.findSpec()
        }
        step(onStep, "正在准备背景…") {
            if (SettingsRepository.bingWallpaperEnabled(context)) {
                BingWallpaperRepository.loadBitmap(context)
            }
        }
        onStep?.invoke("准备完成")
    }

    private fun step(onStep: ((String) -> Unit)?, name: String, block: () -> Unit) {
        onStep?.invoke(name)
        runCatching { block() }
    }
}
