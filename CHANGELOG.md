# 更新日志

## v0.4 — 芯片库全量补齐 + 系统识别

### 芯片型号库

- 规格库继续扩充到 120+ 条，覆盖各厂商各价位段的主流与老机型：
  - 骁龙：8 Gen 5 / 8 Gen 3 for Galaxy / 888+ / 7 Gen 4 / 7s Gen 4 / 6 Gen 4 / 6s Gen 3 /
    4s Gen 2 / 4 Gen 3 / 768G / 710 / 670 / 630 / 625 / 626 / 450 / 439 / 429 /
    821 / 820 / 810 / 801 等。
  - 天玑与 Helio：920 / 900 / 820 / 720 / 7025 / 8020 / 6300 / 6080 / 6020 / 1050 / 1300 /
    Helio G96 / G100 / G70 / G50 / G35 / P60 / P70 / P90 / P22 / P35 / A22 等。
  - 猎户座：1580 / 1480 / 1380 / 1280 / 1080 / 990 / 9825 / 9820 / 9810 / 9611 / 9610 / 8895 / 8890 / 7885 等。
  - 麒麟：990 5G / 990E / 710A / 710F / 655 / 659 / 650 等。
  - 紫光展锐：T310 / SC9820E / SC7731E / T9100 等。
- 匹配算法改进：完全匹配时按别名长度加权，共用代号（如 MT6877）会归到更具体的型号（天玑 920），
  而不是被营销名更长的条目抢走。
- 新增系列级兜底命名：即使某颗芯片还没收录，也会显示为「骁龙 8 系（SM8999）」
  「天玑 / Helio 平台（MT9999）」这类名称，而不是孤零零一个代号。

### 系统识别（新功能）

- 新增「系统 UI / ROM」识别，支持：
  **澎湃OS（HyperOS）/ MIUI**、**ColorOS**、**realme UI**、**OxygenOS**、**OriginOS / Funtouch OS**、
  **One UI**（含版本号换算）、**HarmonyOS（鸿蒙）/ EMUI**、**Flyme / Flyme AIOS**、**MagicOS**、
  **MyOS / ZUI / My UX / Nothing OS / Xperia / ZenUI**、**LineageOS / crDroid / Evolution X / Pixel 原生 / 类原生**。
- 澎湃OS 与 MIUI 的区分依据 `ro.miui.ui.version.name`：V816（HyperOS 1.0）起算澎湃OS，V14x 及更早算 MIUI；
  若系统提供独立的 `ro.mi.os.version.name` 则优先使用。
- 实现方式：普通应用拿不到隐藏的 `SystemProperties` API，因此调用 `/system/bin/getprop` 一次性读取全部属性后解析，无需 root。
- 系统信息页新增「系统 UI / ROM」卡片，展示系统类型、版本号、Build 显示 ID、构建类型，以及**识别依据**（读了哪个属性）；
  首页设备卡片也会显示一行系统 UI。

### 其他

- 版本号 0.3 → 0.4（versionCode 3 → 4）。
- 新增单元测试：`RomInfoProviderTest`（各厂商识别规则）、`SocCodeNamingTest`（兜底命名）、
  规格库的共用代号与新机型用例。

## v0.3 — 界面重构 + 深色模式 + 内存/芯片识别增强

### 界面

- 全套配色重做：去掉原蓝色渐变，改为「石墨灰 + 青绿」中性配色。
- **支持跟随系统深色模式**：颜色统一定义在 `values/colors.xml`，深色版本在 `values-night/colors.xml`，
  7 个页面全部改用 `@color/*` 引用，不再有硬编码色值；状态栏图标明暗用 `values-night/bools.xml` 控制。
- 7 个布局全部重写：统一页头（标题 + 副标题）、卡片分组（区块标题 + 说明 + 分组信息面板 + 分隔线）、
  原始信息用等宽字体代码块展示。
- 信息行改为「标签次要色 + 数值加粗」，一眼能分清字段与数值（`ui/common/InfoRow.kt`）。
- 首页模块卡片改为图标 + 标题 + 说明 + 箭头，图标为矢量绘制（`ic_module_*`）。
- 适配 Android 15+ 强制边到边显示：根布局统一加系统栏内边距（`ui/common/WindowInsets.kt`）。

### 内存（RAM）

- 容量改用 `/proc/meminfo` 的 `MemTotal` 作为主数据源，并新增「标称容量」推断：按最近的常见容量档位
  （2/3/4/6/8/12/16/18/24/32GB）吸附，同时保留实测值，还能看到「内核预留」差额。
  原来的档位阈值是顺序判断，16GB 机器实测 14.9GB 会被误判成 12GB，现已修正。
- **新增内存频率读取**：扫描 `/sys/class/devfreq` 下 DDR/DDR 总线相关节点（覆盖高通、联发科、三星、麒麟等），
  显示当前 / 最高 / 最低频率，并标注读取来源节点；扫不到时退回老平台固定节点列表。

### 芯片识别（SoC）

- 规格库从 44 条扩到 90+ 条：补齐骁龙 8 Elite Gen 5 / 8s Gen 4 / 7+ Gen 3 / 6 Gen 3 / 4 Gen 2 及中低端老型号，
  天玑 9500 / 8400 / 8100 / 1200 等，麒麟 9030 / 9020A / 985 / 820 / 960，紫光展锐 T730 / T612 / SC9832E，
  以及 **Google Tensor G1~G5 全系** 和 **小米玄戒 O1 / O3**。
- 芯片名中文化：`骁龙 8 Gen 3`、`天玑 9400`、`麒麟 9000S`、`紫光展锐 T820`（原来显示英文名或直接显示 SM8650）。
- 支持带后缀的型号（如 `SM8650-AB`、`SM8750-AC`）。
- 匹配算法增加保护：短别名（如玄戒的 `O1`）只接受精确匹配，避免误判。
- 新增品牌图标（骁龙 / 联发科 / 猎户座 / 麒麟 / 展锐 / Google Tensor / 玄戒 / 未知），
  颜色随深浅模式切换。注：图标为自绘的风格化芯片图形，非各厂商官方 logo。
- 真机实测补充：Xiaomi Civi（`SM7325`）原本只能显示 `SM7325`，现已正确识别为 **骁龙 778G / 778G+**
  （含 Adreno 642L、GPU 实测 608 MHz）；顺带补上 SM7350 / SM7475 / SM7450 / SM780G 等缺失型号。
- CPU 架构判定调整：明确写 `armv9` 才显示 ARMv9，否则按 ARMv8-A 处理（原来 arm64 一律显示 ARMv8-v9）。

### 其他

- 新增离线界面渲染自检：`LayoutRenderTest` 用 Robolectric 把 7 个页面在浅色 / 深色下渲染成 PNG
  （输出到 `app/build/render/`），不用真机即可检查配色与布局。
- 版本号 0.2 → 0.3（versionCode 2 → 3）。

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
