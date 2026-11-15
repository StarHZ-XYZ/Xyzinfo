package com.rjy.xyz.apps.xyzinfo.ui.about

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.BuildConfig
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityChangelogBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow

/** 更新日志：保留主要功能与修复，不再记录每个实现细节。 */
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
【1.1.3】
· 重排主页信息层级，统一卡片标题、说明、行距与间距
· 缩短模块描述，优化两列宫格中的换行和截断
· 当前设备区改为“设备概览”，辅助信息文字更清晰
· 新增一键复制设备摘要：系统、形态、型号、内核与架构

【1.1.2】
· 内置 163 款机型库，支持品牌、机型、芯片搜索和品牌筛选
· 修复选择机型后右侧对比表不刷新
· 新增对比结果一键复制和文本分享
· 启动页 Logo 固定为圆角方形，不再随系统显示为不同轮廓
· 分享卡片底部改为 2×2 等宽按钮，修复“保存到相册”错位和显示异常
· 更新日志精简：本页与仓库日志都只保留主要变化

【1.1.1】
· 五个顶部标签改为单 Activity 壳层，页面原地切换并保留状态
· 修复切页重载感和底栏绿色光标错位
· 新增设备快照导出 / 导入与横向参数对比
· 修复荣耀首页 Logo 过大、过扁
· 增强折叠屏 OEM 特征识别，并加入隐藏诊断中的形态校正

【1.1.0】
· CPU 跑分改为独立 XYZ Index v2，修正跨芯片虚高
· 排行榜加入骁龙 8 Elite Gen 6 / Extreme Gen 6 与发布日期
· 修复电池能量单位、荣耀 / iQOO 型号和荣耀 Logo
· 四季特效支持横屏重力
· 新增折叠屏、车机、手表、电视和其他设备形态
· 新增隐藏诊断、崩溃记录、轻量模式和更新数据校验

【1.0.x】
· 完成分享卡片、温度监控、HDR 与分辨率检测、定位地图、节日彩蛋、
  电池记录、网络测速、跑分重写和安全加固

【0.x】
· 从基础设备信息页逐步扩展为芯片、内存、屏幕、电池、传感器、
  通信、GPS、性能测试和硬件测试工具

数据说明：设备规格来自公开智能手机数据集；CPU 排行榜参考极客湾公开榜单。
"""
    }
}
