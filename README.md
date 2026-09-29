# XyzInfo（星幻终 设备信息）

一个基于 Android Studio 开发的 Android 手机硬件参数检测工具。

## 当前版本

v0.2（重构版，详见 [CHANGELOG.md](CHANGELOG.md)）

## 已实现功能

- SoC 信息：型号 / 品牌 / 大小核结构 / 每核心频率 / GPU / 图形接口
- RAM 信息：容量 / 占用率 / 堆信息 / Swap·ZRAM / 内存类型与实时频率
- 屏幕信息：分辨率 / 尺寸 / 密度 / 刷新率档位 / 触控 / HDR / 广色域
- 电池信息：电量 / 容量 / 电流 / 电压 / 温度 / 循环次数（每秒刷新）
- 传感器信息：热区温度 + 硬件传感器清单
- 系统信息：版本 / 安全补丁 / Build 标识 / 存储 / Root / Treble

## 项目结构

重构后按「界面 / 数据 / 模型 / 工具」四层拆分，每个检测页面只负责展示：

```
app/src/main/java/com/rjy/xyz/apps/xyzinfo/
├── MainActivity.kt              首页：设备概要 + 各页面入口
├── ui/                          界面层（Activity）
│   ├── soc/SocInfoActivity.kt
│   ├── ram/RamInfoActivity.kt
│   ├── screen/ScreenInfoActivity.kt
│   ├── battery/BatteryInfoActivity.kt
│   ├── sensor/SensorInfoActivity.kt
│   └── system/SystemInfoActivity.kt
├── data/                        数据层：读取系统节点并组装模型
│   ├── DeviceOverviewProvider.kt
│   ├── SocInfoProvider.kt
│   ├── RamInfoProvider.kt
│   ├── ScreenInfoProvider.kt
│   ├── BatteryInfoProvider.kt
│   ├── SensorInfoProvider.kt
│   ├── SystemInfoProvider.kt
│   └── soc/SocSpecRepository.kt + soc/SocSpec.kt   SoC 规格库与匹配算法
├── model/                       模型层：不可变数据类与枚举
│   ├── DeviceOverview.kt / SocInfo.kt / RamInfo.kt / ScreenInfo.kt
│   └── BatteryInfo.kt / SensorInfo.kt / SystemInfo.kt
└── util/                        工具层
    ├── ProcFs.kt                /proc、/sys 节点读取，统一容错
    ├── Formats.kt               字节 / 频率 / 温度 / 电流等格式化
    ├── DeviceFacts.kt           Build 与 /proc/cpuinfo 字段整理
    └── Labels.kt                枚举值 → 中文文案
```

数据流单向：`Provider 读取系统 → Model 承载数据 → Activity 渲染文案`。
界面文案与单位换算全部集中在 `util`，新增机型适配只需要改 `data`。

## 开发环境

- Android Studio
- Kotlin（Java 11 兼容级别）
- minSdk 24 / targetSdk 36
- 已启用 ViewBinding，无额外依赖

## 编译

用 Android Studio 打开工程直接运行，或执行：

```
./gradlew assembleDebug
```

## 测试

纯逻辑（格式化、SoC 匹配算法、热区归类）有 JVM 单元测试：

```
./gradlew testDebugUnitTest
```

## 项目目标

做一个界面清晰、能持续迭代的手机参数检测工具，并逐步完善硬件识别能力。

## 后续计划

- 布局中的硬编码中文文案下沉到 `strings.xml`
- SoC 规格库补充更多机型，并把规格数据改成可更新的资源文件
- 为 `data` 层的解析逻辑补充单元测试

## 截图

后续补充。

## 作者

星幻终(xyz)

## 开源协议

MIT
