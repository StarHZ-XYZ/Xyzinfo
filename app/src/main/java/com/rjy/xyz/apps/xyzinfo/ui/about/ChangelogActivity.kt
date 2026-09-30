package com.rjy.xyz.apps.xyzinfo.ui.about

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.BuildConfig
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityChangelogBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow

/**
 * 更新日志页：把所有版本的更新内容写清楚，末尾一句「后续版本敬请期待」。
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
【1.0.0】
· 环境检测页（新）：root 痕迹 / SELinux / 调试状态 / 模拟器特征 / Xposed / 分身应用 / 用户 CA，分级清单 + 逐条证据
· 温度监控（新）：大字实时温度 + 环形温度表，1 小时 / 24 小时 / 7 天 / 30 天曲线；
  温度浮窗前台服务，每分钟落盘一条 CSV，退出应用也继续记录，浮窗可拖动、可一键关掉
· 大肥鱼验机（新）：13 类检查（芯片 / 机型名 / 内存差额 / 传感器 / 系统签名 / 环境 / 存储 /
  刷新率 / 无线硬件 / 摄像头 / 架构 / 系统版本 / 形态）+ 几十条结论库，最后给一段总结
· 大肥鱼（AI，新）：底栏正中间的 AI 标签；填 API Key 直连，或在软件内用 WebView 登录
  DeepSeek 免费版（登录态保存在本应用，进页面自动把验机报告复制到剪贴板）
· 大肥鱼图标：由桌面原图自动矢量化生成（保持 474:349 原比例），底栏 / 首页 / 验机页统一；
  描边改成贴轮廓的细渐变描边（蓝 → 紫 → 粉），不再是放大叠色块
· 大肥鱼主题（新）：不改任何配色，只在每个页面右下角摆一条可爱的大肥鱼，轻轻浮动摆尾，
  不挡内容、不吃点击，设置里可开关
· 底栏：手指辉光跟随手指**渐变**变色（滑到大肥鱼是蓝紫、离开渐变回主题色）；
  修掉 AI 配色 alpha=0 导致光晕/描边一直"看不见"的老 bug
· 硬件测试增加一键测试；流畅度优化：温度取值、记录落盘、验机报告复制等 I/O 全部挪到后台线程
· 更新日志页（本页）

1.+ / 2.+ 版本敬请期待

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
