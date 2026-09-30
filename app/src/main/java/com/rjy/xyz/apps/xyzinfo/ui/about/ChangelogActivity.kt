package com.rjy.xyz.apps.xyzinfo.ui.about

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.BuildConfig
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityChangelogBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow

/**
 * 更新日志页：把所有版本的更新内容与下一版预告写清楚。
 *
 * 文案直接放在应用里（不依赖网络），随版本一起更新。
 */
class ChangelogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChangelogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangelogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_NONE)

        binding.tvChangelogSubtitle.setInfoRow(
            "当前版本：${BuildConfig.VERSION_NAME}（构建号 ${BuildConfig.BUILD_NUMBER}）"
        )
        binding.tvChangelog.text = CHANGELOG
    }

    private companion object {
        val CHANGELOG = """
【1.0 预告 · 开发中】
· 环境检测“可视化页面”：把 root / SELinux / 调试 / 模拟器 / Xposed / 分身 / 用户 CA 的分级清单与证据做成页面（检测内核已在 0.9 完成）
· 温度监控浮窗：实时显示各热区温度，并可长期记录温度曲线（1 小时 / 24 小时 / 7 天 / 30 天）
· DeepSeek 大肥鱼主题：蓝系配色 + 一条游动的大鱼装饰
· 安卓版本图标：给系统属性页的 2.2 ~ 17 每个版本配上对应的甜点图标
· 结构调整：单 Activity + 四个 Fragment，彻底消除切标签时的长帧

【0.9.0】
· App 图标全面重构：扁平化设计，X 为主体、齿轮点缀、青蓝渐变底
· 莫奈取色全局生效（布局与自绘部分统一走主题属性）；深色模式三档手动切换
· 信息行支持小图标；主页宫格默认开启；主页顶部信息从 9 行收敛到 5 行
· 子页面不再显示底栏，改为左上角圆形返回按钮
· 底栏：实时轻微高斯模糊、跟手滑动切换、去掉按下发灰与点击闪烁
· 硬件测试（新）：屏幕坏点/触摸/多点触控、扬声器现场合成旋律、麦克风电平、振动三档、摄像头（方向+自动对焦+多镜头切换）、NFC
· 杂项工具（新）：反应力测试、随机密码、手电筒
· 环境检测内核（新）：root 痕迹、SELinux、调试状态、模拟器特征、Xposed、分身应用、用户 CA 证书
· 更新日志页（本页）

【0.8.0】
· 必应每日壁纸：软件背景，可开关、可换往期图、蒙版浓度可调
· 首页品牌徽标：200+ 品牌目录 + 按官方配色生成徽章
· 设备形态识别：手机 / 平板 / 折叠屏，宽屏自动适配排版
· 四季氛围效果：雪花 / 枫叶 / 花瓣 / 阳光，默认开启可手动锁定
· 内存测试：顺序带宽 + 随机访问延迟 + 分配速率，与 CPU/GPU 同刻度计分
· 主页宫格样式（两列，等宽等高）
· 内置 17 张国家 / 区域地图，按定位自动切换
· 启动动画页（约 1.8 秒）；设置页新增 GitHub 仓库入口；版本号开始附带构建号

【0.7.0】
· 全应用丝滑动画：页面转场、区块从左往右入场、卡片按压、数字滚动、条形图生长
· 全新底栏（首页 / 跑分 / 排行 / 设置）：高斯模糊 + 等分标签 + 指示器
· 点击粒子效果；设置里新增动画总开关
· CPU / GPU 测试重写：按时间跑满 60 秒以上、8 项负载、稳定性与 1% low
· 排行榜重写：数据换成极客湾榜单，支持综合 / 单核 / 多核 / GPU 与品牌筛选，本机实测插入榜单
· GPS 定位新页面：实时数据、内置离线世界地图、卫星天顶图 + 指南针、收音机探测
· 修复首页「数据更新」按钮（补 INTERNET 权限 + 多镜像 + gzip 校验）

【0.6.0】
· 首页机型名中文化（2.3 万设备代号 + 2.5 万型号映射），并支持在线更新机型库
· 通信参数模块；首页机型名按品牌着色（40+ 品牌）

【0.5.0】
· 性能测试模块：CPU 四项与 GPU 跑分，并与内置参考机型对比

【0.4.0】
· 芯片库扩充到 120+ 条；新增系统 UI / ROM 识别（澎湃OS、MIUI、ColorOS、One UI、鸿蒙等）

【0.3.0】
· 界面重构 + 深色模式；RAM 标称容量与内存频率；芯片名中文化与品牌图标

【0.2.0】
· 按「界面 / 数据 / 模型 / 工具」四层重构代码

【0.1.0】
· 首个版本：设备基础信息检测
""".trimIndent()
    }
}
