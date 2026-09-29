# 更新日志

## v0.2 — 结构重构

本次不改动检测能力，重点是把「一个页面一个巨型 Activity」拆成分层结构，并消除重复代码。

### 结构调整

- 包结构从「全部平铺在 `xyzinfo` 下」拆成 `ui` / `data` / `model` / `util` 四层。
- 六个检测页面移入 `ui/<功能>/`，Manifest 里的 Activity 路径同步更新。
- 原 `SocSpecRepository.kt` 移入 `data/soc/`，其中的 `SocSpec` 数据类拆成独立文件。

### 消除重复

- 原先 7 个文件各自实现了 `readFileText` / `readIntSafely` / `safe` / `formatBytes` 等工具方法，现统一收敛到 `util/ProcFs.kt`、`util/Formats.kt`、`util/DeviceFacts.kt`、`util/Labels.kt`。
- 系统枚举（电池状态 / 健康、供电来源、热区大类、Root、Treble）改为枚举类型，中文文案集中在 `Labels`，不再散落在各页面的 `when` 里。
- 移除 `build.gradle.kts` 中重复声明的 material 依赖，改为统一走版本目录（`libs.versions.toml`，1.12.0）。

### 界面层

- 启用 ViewBinding，去掉各 Activity 中大量 `lateinit var` + `findViewById`。
- Activity 只负责「取数据 → 填文本」，读取系统节点、解析数值、生成原始信息预览全部移入 `data` 层 Provider。
- 删除了未使用的成员（如电池页从未被使用的 `cardHeader`）。

### 行为一致性说明

- 首页与 SoC 页的 CPU 架构文案原本不一致（`ARMv8-A` / `ARMv8-v9`），现统一为 `AArch64 / ARMv8-v9`。
- 系统信息页的存储大小改用统一的字节格式化，输出风格与其它页面一致。
- Project Treble 判定：无配置文件时按系统版本给出「大概率支持」（Android 8 起为强制要求）。
- 电池 / 温度 / 频率等展示文案与单位保持原样。

### 新增

- JVM 单元测试：`FormatsTest`、`SocSpecRepositoryTest`、`ThermalKindTest`。
- 版本号 0.1 → 0.2（versionCode 1 → 2）。
- `gradle.properties` 增加 `android.overridePathCheck=true`：Windows 上工程路径含中文时 AGP 会默认中断构建，本项目目录即含中文，故显式放行（换到纯英文路径后可删除）。
