---
name: miuix-compose-ui
description: |
  用 Miuix（Xiaomi HyperOS 风格的 Compose Multiplatform UI 库）从零搭建或改造 Android / CMP 应用界面。
  内置一套经过 vcut_lower、HyperMusicCover、Cirno 三个真实工程验证的「四标签应用模板」：统一页面骨架、12dp 卡片间距、
  两态底栏（贴底/悬浮）、顶栏渐进模糊、状态卡、设置页排班、主题二级页、条件项折叠、
  候选列表与筛选列表（分组条 + 双流模型 + 搜索 + 分类弹层 + 三态 + 下拉刷新）、性能与缓存规则、功能排班规则。
  当用户要求「用 Miuix 写界面」「做成 HyperOS 风格」「照着 vcut_lower / HyperMusicCover 的样子做一个」
  「把现有 Compose 界面改成小米风格」，或查询 Miuix 组件/主题/图标/导航/模糊用法、
  要做「可选列表 / 应用列表 / 搜索结果 / 带筛选的设置页」、排查 Miuix 依赖与集成问题时使用。
  触发词：miuix、Miuix、HyperOS 风格 UI、小米风格界面、澎湃 OS 风格、MiuixTheme、MiuixIcons、Compose Multiplatform UI 库、
  Miuix 模糊、vcut_lower、HyperMusicCover、Cirno、候选列表、筛选列表、分组卡片列表。
---

# Miuix Compose UI

Miuix 是 `compose-miuix-ui/miuix` 维护的 Compose Multiplatform UI 库，提供 Xiaomi HyperOS 设计风格的组件，Apache-2.0。
本 skill 不止是 API 速查：它把 `zyl6932/vcut_lower` 与 `zyl6932/HyperMusicCover` 两个已上架工程的界面骨架、间距、
底栏形态、模糊参数、状态轮询、设置排班全部固化成了模板与规则，用来**一键产出同款界面**。

---

## ⚠️ 开工前必读：资源导航（先找，再画）

> **本文件第 11 节「资源导航」是开工第一件事，不是参考资料。**
> 在写任何一行 UI 代码之前，先按第 11 节的表把已有资源过一遍。
>
> **本 skill 自带 28 个可直接复制的 `.kt` 模板、4 个资源文件、2 个脚本。**
> 你要画的界面里，十有八九已经有一份写好的、在真机上跑过、修过坑的版本。

### 三条硬性要求

1. **先查后画。** 需要状态卡、设置页、列表页、应用列表、主题页、详情页、关于页、底栏、顶栏、模糊、
   间距常量、图标尺寸 —— 全部先去第 11 节找对应文件，`cp` 过来改文案，**不要自己重画**。
2. **只有两类东西允许新画：**
   - **品牌图形**（应用 logo、关于页图标、启动图标）—— 库里没有，必须自绘；
   - **模板确实没有的新布局**（如相机预览、地图、双栏对照）—— 且要说明为什么现成的不能用。
3. **非应用图标一律不自绘。** 通用操作走 `MiuixIcons.*`（100+ 个内置），
   状态符号（勾、感叹号、警告）走 `material-icons-extended` 的 `Icons.Rounded.*`。
   自绘 Material 图标是重复劳动，而且**一定画不如图形原版** —— 见 `references/hmc-patterns.md` 第 9 节的两次失败记录。

### 开工检查（三问）

| 问 | 答不上来就别动手 |
|---|---|
| 我要画的这个东西，第 11 节里有对应文件吗？ | 有 → 抄它，改文案 |
| 是品牌图形吗？ | 不是 → 用 `MiuixIcons.*` 或 `Icons.Rounded.*`，不自绘 |
| 是模板没有的新布局吗？ | 不是 → 回去抄模板；是 → 说明为什么现成的不能用 |

---

## 0. 一键生成流程（主路径）

用户说「照着 vcut_lower 做一个 XX App 的界面」时，按这 8 步走，不要跳步、不要即兴发挥。

### 步骤 1：读工程现状（不改任何文件）

必读三个文件，确认形态与版本：

| 文件 | 要确认什么 |
|---|---|
| `settings.gradle.kts` | 有没有 `pluginManagement` 的仓库声明、是不是 CMP 多平台工程 |
| `gradle/libs.versions.toml` | Kotlin / AGP / Compose BOM / miuix 版本，minSdk |
| `app/build.gradle.kts` | `compileSdk`、`minSdk`、`buildFeatures.compose`、现有依赖 |

工程里已经有 miuix 版本号时**沿用**，不要擅自升级 —— 跨版本预编译库存在二进制不兼容。

### 步骤 2：加依赖

纯 Android 工程（最常见）：

```toml
# gradle/libs.versions.toml
[versions]
miuix = "0.9.4"          # 以工程已有为准；新工程用最新稳定版

[libraries]
miuix-core       = { group = "top.yukonga.miuix.kmp", name = "miuix-core-android",       version.ref = "miuix" }
miuix-ui         = { group = "top.yukonga.miuix.kmp", name = "miuix-ui-android",         version.ref = "miuix" }
miuix-blur       = { group = "top.yukonga.miuix.kmp", name = "miuix-blur-android",       version.ref = "miuix" }
miuix-preference = { group = "top.yukonga.miuix.kmp", name = "miuix-preference-android", version.ref = "miuix" }
miuix-icons      = { group = "top.yukonga.miuix.kmp", name = "miuix-icons-android",      version.ref = "miuix" }
miuix-squircle   = { group = "top.yukonga.miuix.kmp", name = "miuix-squircle-android",   version.ref = "miuix" }
```

**只加真正用到的模块。** 需要模糊才加 `miuix-blur`（要求 API 33+），需要 Preference 行才加 `miuix-preference`。
所有 `top.yukonga.miuix.kmp:*` 必须同版本。CMP 工程去掉 `-android` 后缀写在 `commonMain.dependencies`。

### 步骤 3：抄模板文件（assets/）

**优先用脚本一次铺完**（自动读包名、改 package 声明、已存在的文件不覆盖）：

```bash
python scripts/scaffold_app.py --project <工程根目录> --dry-run   # 先看计划
python scripts/scaffold_app.py --project <工程根目录>             # 再落盘
python scripts/scaffold_app.py --project <工程根目录> --tabs home,discover,mine
```

脚本跑完会直接打印 `AndroidManifest.xml` 该加的 activity 段和 `AppShellScreen` 的 `AppTab` 列表。
它**只铺文件、不改 build 文件**，依赖缺失会在输出里列成 `[TODO]`。
铺完是 28 个 `.kt` + 3 个 drawable + 1 个 `themes.xml` + 1 个 `proguard-rules.pro`。
2026-11-17 实测：在全新 namespace `com.demo.fresh` 的空工程上跑一次脚本，`assembleDebug` 直接成功，
无需手改任何一行 —— 这就是「一键」的验收标准。

手工抄（脚本不适用，或想逐文件核对）时按顺序复制，改包名，不要重写：

| 目标路径（按你的包名改） | 来源 | 作用 |
|---|---|---|
| `ui/theme/Theme.kt` | `assets/MiuixAppTheme.kt` | MiuixTheme 壳 + 系统栏图标同步 |
| `ui/util/PageScaffold.kt` | `assets/PageScaffold.kt` | 每页统一骨架（顶栏模糊 + insets + 列表） |
| `ui/util/CardSpacing.kt` | `assets/CardSpacing.kt` | `firstCard()` / `nextCard()` 12dp 间距 |
| `ui/util/BlurUtils.kt` | `assets/BlurUtils.kt` | `rememberBlurBackdrop`、`BlurredBar`、`isInDarkTheme` |
| `ui/util/PagerNavigationSpring.kt` | `assets/PagerNavigationSpring.kt` | 底栏翻页弹簧 |
| `ui/AppShellActivity.kt` | `assets/AppShell.kt` | 四标签壳 + 两态底栏 |
| `ui/AppSettings.kt` | `assets/AppSettings.kt` | 外观设置持久化 + 夜间模式 |
| `ui/screens/HomePage.kt` | `assets/HomePage.kt` | 状态卡 + 数据卡 + 设备卡 |
| `ui/screens/FeaturesPage.kt` | `assets/FeaturesPage.kt` | 一功能一卡，点进 Activity |
| `ui/screens/SettingsPage.kt` | `assets/SettingsPage.kt` | 分组设置 + 危险操作二次确认 |
| `ui/screens/AboutPage.kt` | `assets/AboutPage.kt` | 身份卡 + 链接 + 许可 |
| `ui/screens/ListPage.kt` | `assets/ListPage.kt` | 候选列表：分组条 + 搜索 + 分类弹层 + 三态 |
| `ui/screens/ListViewModel.kt` | `assets/ListViewModel.kt` | 双流模型（全量流 + 过滤流） |
| `ui/screens/StatusTag.kt` | `assets/StatusTag.kt` | 状态 chip（两个列表页共用，不能各写一份） |
| `ui/screens/AppListPage.kt` | `assets/AppListPage.kt` | 应用列表（真实读取已安装应用） |
| `ui/screens/AppListViewModel.kt` | `assets/AppListViewModel.kt` | 应用列表双流 + 图标解码 |
| `ui/screens/AppListActivity.kt` | `assets/AppListActivity.kt` | 应用列表外壳（`PageScaffoldColumn`） |
| `ui/screens/AppDetailActivity.kt` | `assets/AppDetailActivity.kt` | 应用二级菜单 |
| `ui/util/DesignTokens.kt` | `assets/DesignTokens.kt` | 间距与图标尺寸阶梯 |
| `ui/screens/ThemeActivity.kt` | `assets/ThemeActivity.kt` | 主题二级页：六个模式 + 三个外观开关 |
| `ui/screens/FeatureDetailActivity.kt` | `assets/FeatureDetailActivity.kt` | 所有「点进去」共用的详情页，靠 Intent extra 区分 |
| `ui/component/effect/` 下 7 个 | `assets/effect/` | 背景动效（RuntimeShader 绘制，OS2/OS3 两种） |
| `res/drawable/` 下 3 个 | `assets/res/drawable/` | 关于页 logo、启动图标前景/背景（状态卡图标来自 `material-icons-extended`，不在 res 里） |
| `res/values/themes.xml` | `assets/res/values/themes.xml` | 窗口底色（**必须不透明**）与系统栏 |

### 步骤 4：定标签页（先定结构，再写内容）

默认四标签：**主页 / 功能 / 设置 / 关于**，图标 `MiuixIcons.Home / Tune / Settings / Info`。
产品类型不同就换变体（内容型 `首页/发现/我的`、媒体型 `播放/列表/我的`）。
**3–5 个，超过 5 个说明层级没设计好。** 完整判定见 `references/information-architecture.md`。

### 步骤 5：填每页内容（按黄金顺序）

每页从上到下：**状态 → 主操作 → 数据/内容 → 次要信息 → 元信息**。
每张 `Card` 只装一类信息，首屏不超过 3 张卡。

三种内容形态按需选：

| 页面要装什么 | 用哪个模板 | 关键约束 |
|---|---|---|
| 少量固定卡片（状态、数据、设备） | `assets/HomePage.kt` | 首屏 ≤ 3 卡，卡间 `firstCard()`/`nextCard()` |
| 一屏列 N 个同类可选项 | `assets/ListPage.kt` + `assets/ListViewModel.kt` | 分组条、双流模型、三态，见 `references/list-and-filter.md` |
| 开关控制下面几行是否有效 | 自己写 `AnimatedVisibility` + `expandVertically` | **折叠而不是置灰**；等外部回值时用 `MutableTransitionState` 防误播，见 `references/hmc-patterns.md` 第 1 节 |

列表页的三条硬规则（违反即返工）：

1. **一块卡切成条**，不是每项一张小卡：外层 12dp 水平内边距，块内无缝，首项圆上、末项圆下、中间直角。
2. **筛选与搜索在 ViewModel 里派生**（`combine` + `stateIn`），不在 UI 里 `filter`。
3. **加载态整块替换列表**，不往 `LazyColumn` 里塞占位项；空与失败要分开说。

### 步骤 6：接状态源

把 `HomePage` 里的 `AppStatus.sample` 换成真实状态。轮询必须走 `repeatOnLifecycle(RESUMED)`，
进程内缓存上次答案，见 `references/performance-and-caching.md` 第 4 节。

### 步骤 7：校验

```bash
python scripts/check_miuix_usage.py --path <工程目录>   # 修 ERROR 与新增的规范 WARNING
./gradlew :app:assembleDebug                            # 编译
```

检查脚本覆盖：Material3 混用、Overlay/popupHost 不匹配、图标缺 import、Checkbox/InputField 参数错、
回弹叠加、minSdk 与 blur 冲突、miuix 多版本、写死 `fontSize = N.sp`、卡片外手写非 12dp 栅格、
`animateScrollToPage`、多层 `rememberLayerBackdrop`、裸读 `pagerState.currentPage`、标签页数量越界、
`painterResource` 指向 adaptive-icon、列表 `key` 非复合、弹窗状态在 lambda 外读。

**另外两条是「你在重复劳动」而不是「你写错了」（`check_reuse`）：**

| 检查 | 触发条件 | 该怎么做 |
|---|---|---|
| 自绘了 Material 图标 | `res/drawable*/` 下非白名单的 `<vector>` 且 `viewportWidth="24"` | 通用操作走 `MiuixIcons.*`，状态符号走 `Icons.Rounded.*`；品牌图形才自绘，并把名字加进 `ALLOWED_DRAWABLE` 白名单 |
| 自己重写了模板布局 | 页面文件（`*Page.kt` / `*Activity.kt` / `*Screen.kt`）出现 `.offset(27.dp, 31.dp)` 却没用 `PageScaffold` | 去 `assets/HomePage.kt` 抄状态卡，改文案 |

两条都只是 WARNING（不阻塞编译），但它们正是「看着差不多、处处差一点」的来源。

检查脚本的豁免机制：极少数「本来就不该跟主题走」的尺寸（如列表项末尾状态 chip 的 9sp / 4×2dp）
可以在命中行的上一行或同一行写 `// miuix-check: allow-design-tokens`，该行不参与 token 检查。
**用行级而不是文件级** —— 一个 chip 不该让整份文件失去栅格检查。豁免旁边必须写清理由，否则下一个人不敢动它。

脚本查不出来的两类，必须人工核：

1. **`layerBackdrop` 注册层里有没有包含用它做模糊的组件** —— 有就是自采样，渲染树递归后 SIGSEGV。
   这条只能看代码结构，见第 3 节。
2. **跳转目标是否真的存在并注册进 manifest** —— 模板里任何 `Intent(context, XxxActivity::class.java)`
   都要能在 `AndroidManifest.xml` 找到，否则点下去是 `ActivityNotFoundException`。

### 步骤 8：报告

编译过了就报「已编译通过」，没过就贴报错原文并说明未验证项。**不要声称没跑过的验证已通过。**

---

## 1. 硬性约束（违反即返工）

| # | 规则 | 为什么 |
|---|---|---|
| 1 | **不混用 Material3 UI 组件**（`androidx.compose.material3.Button/Scaffold/Text/...`） | 两套主题、指示器、弹层机制。`material.icons` 的 `ImageVector` 可以混用，UI 组件不行 |
| 2 | **Overlay 与 `popupHost` 必须匹配** | `OverlayDialog`/`OverlayListPopup`/`Overlay*Preference` 依赖 `Scaffold` 的 `popupHost`。把 `popupHost = {}` 置空，它们静默不显示 |
| 3 | **Window 级弹层不依赖 Scaffold** | `WindowDialog`/`WindowDropdownPreference` 在任何 Composition 里都能用，用 `LocalDismissState.current?.invoke()` 关闭 |
| 4 | **图标要单独 import 扩展属性** | `MiuixIcons.Settings` 需 `import top.yukonga.miuix.kmp.icon.extended.Settings`，只 import `MiuixIcons` 会编译失败 |
| 5 | **越界回弹不叠加** | 用 `Modifier.overScrollVertical()` 时列表必须 `overscrollEffect = null`，否则阻尼回弹与 Compose stretch 在两端打架 |
| 6 | **顶部 inset 放 `contentPadding`** | 放到列表自身 `modifier.padding(top=)` 会让内容无法滚到透明顶栏下，模糊背景无内容可采样 |
| 7 | **`layerBackdrop` 的注册层里不能包含用该 backdrop 做模糊的组件** | 注册层把采样者自己圈进去 = 采样源指向自己 = 渲染树无限递归，装机后 native SIGSEGV（实测 `SkiaDisplayList::prepareListAndChildren` → `RenderNode::prepareTreeImpl` 递归 500+ 层 → `signal=11`）。**不是「只能有一个 backdrop 对象」**——顶栏与底栏各用一份独立 backdrop 才是正确结构 |
| 8 | **同版本** | 不混用 `0.9.4-rc01` 与 `0.9.4` |
| 9 | **API 以源码/文档为准** | Miuix 处于实验阶段，0.9.4 有多组签名不兼容变更（导航、Preference、Dialog、PullToRefresh、InputField）。不要引用旧博客或记忆里的旧签名 |
| 10 | **不伪造 API** | `IosLiquidGlassNavigationBar`、`BgEffectBackground` 是这两个工程自建的组件，**不是 Miuix API**，不要当库函数用 |
| 11 | **新增前查重** | 同名 composable 同包定义 → `Conflicting overloads` 直接编译失败；同一设置两处入口 → 必有一处是假的，用户拨了没反应会怀疑整个 App。三条 grep 见 9.5.2 |
| 12 | **删前确认引用** | 删 composable / Activity / 文件前 grep 类名 + 查 manifest + 查 `Intent(...::class.java)`。共用的文件（`StatusTag.kt`）误删会让另一个页面 `Unresolved reference`。见 9.5.4 |
| 13 | **模板内与本次构建无关的控件要删** | 留着不报错，但会让后来者以为它是业务的一部分。删的时候连带删入口、manifest 注册、独占辅助文件。见 9.5.1 |
| 14 | **长文本按「截断后认不认得出」决定** | 包名截成 `com.mfcloudcalculate.ne…` 就认不出是哪个应用 → 让它换行；列表标题前几字就够辨认 → `maxLines = 1` + `Ellipsis` |
| 15 | **图标与文字块对齐的是中线** | `Row(verticalAlignment = Alignment.CenterVertically)`。给文字列限高会在多行时把文字挤出框外，反而更歪 |
| 16 | **优先复用已有资源，不自绘、不重排** | 第 11 节列了 28 个模板。状态卡、设置页、列表页、底栏、顶栏、间距常量都已写好并在真机验证过；自己重画的结果是「看着差不多但处处差一点」。只有品牌图形（logo、启动图标）与模板确实没有的新布局才允许新写 |
| 17 | **运行卡片页前两张卡不加分组标题** | `SmallTitle` 自带 28/8dp 内边距，两个标题吃掉 72dp ≈ 一张卡的高度；前两张卡各只有一张，没有可收拢的同类，标题只是复述内容。从第三张卡起、或一个标题下确实有两张以上同类卡时才加。见 5.1 |
| 18 | **界面文案不用括号补充说明** | 括号里的字最先被折行、被截断（`保持存活（会增加内存…`）；一对全角括号占四个字符宽，等于把副标题预算花在标点上。补充信息交给 `summary` / `subtitle`，或拆成两行。包名、版本号、路径、正则、报错原文等技术标识照原样保留。见 9.6 |
| 19 | **主题设置板块完整保留** | `ThemeActivity` 的六个模式 + 悬浮底栏 + 背景模糊三组一项不能少，也不进 9.5.1 可删清单。删掉后 `AppSettings` 三个字段、`AppNightMode`、状态栏同步、`settingsEpoch` 重读全成死代码；且它是唯一会重建 Activity 的设置，删了没法靠系统深色补救。见 6.1 |
| 20 | **多选一必须下拉，禁止点击循环切换** | 点一下切到下一个（`(x + 1) % n`）用户永远猜不到下次变成什么、看不见全部选项、没法回退。≥3 个选项一律 `WindowDropdownPreference`；2 个选项且各需一句说明才平铺；布尔用 `SwitchPreference`。见 9.7 |
| 21 | **功能入口唯一，同一配置不准两处入口** | 主页放一个、设置页又放一个是典型违反。两处都要维护、必有一处变旧版；用户改完一处回到另一处发现同名入口还在，会以为没保存。只留一个最合理的入口，其余删掉。概览与详情、同一 Activity 多内容不算重复。见 9.8 |
| 22 | **应用壳必须重读设置** | 调 `AppShellScreen` 的 Activity 必须有 `onResume { settingsEpoch++ }` + `remember(settingsEpoch) { AppSettings.load(this) }`。只 onCreate 读一次的话，从主题页返回后悬浮底栏与背景模糊永远是旧值，症状就是「开关拨了没反应、只有主题模式有效」。见 6.2 |

---

## 2. 间距、圆角与字号（唯一取值表）

这三个数值是界面「看着像 HyperOS」的全部秘密，全工程统一，不允许每页各写一套。

### 2.0 一条铁律：间距只允许出现阶梯里的数

```kotlin
// ui/util/DesignTokens.kt —— 全工程唯一来源
object DshSpacing {
    val Hairline = 2.dp    // 纯视觉分离（标题与紧邻副标题），不承担间距语义
    val Tight    = 4.dp    // 组件内微调（chip 内边距、图标贴边）
    val Compact  = 8.dp    // 组件内呼吸（图标与文字之间）
    val Block    = 12.dp   // 块间距：卡片之间、卡片与屏幕边缘、对话框按钮之间
    val CardInside = 16.dp // 卡内边距（= miuix BasicComponentDefaults.InsideMargin）
}

object DshIconSize {
    val ListIcon      = 58.dp    // 列表行图标（Cirno AppItemCompose 钉死）
    val DetailIcon    = 72.dp    // 二级页身份区图标
    val HeroIcon      = 100.dp   // 关于页应用图标
    val WatermarkIcon = 110.dp   // 状态卡底纹图标（故意出血）
}
```

`28.dp` 也允许，但只在两种情况下：`SmallTitle` 的水平内边距（miuix 自带，不要手填），
以及身份区这类需要与 `SmallTitle` 文字缩进对齐的区块。

**规则：padding 里出现阶梯之外的数，就是有人又手调了一次。**
能写 `firstCard()` / `nextCard()` 就不要手写 `12.dp`。

> 反例（真实踩过）：五个页面出现过五种写法（只加 top、只加 vertical、只加 bottom……），
> 结果卡与卡之间的缝每页都不一样。后来又审计出五处独立的手调值：
> 对话框按钮间距 20dp、页尾留白 24dp、关于页图标上方 8dp、标题与副标题之间 5dp、
> 状态卡内部 1dp。每一处单看都说得通，放在一起就是「这个 App 每个页面的手感都不一样」——
> 用户说不出哪里不对，但会觉得不精致。

### 2.1 块间距：12dp 栅格

```kotlin
// ui/util/CardSpacing.kt —— 块间距的唯一入口
internal fun Modifier.firstCard(): Modifier = padding(12.dp)
internal fun Modifier.nextCard(): Modifier = padding(horizontal = 12.dp).padding(bottom = 12.dp)
```

- 第一个块：四周 12dp。
- 之后每个块：左右 12dp + 下 12dp。
- `SmallTitle` 自带上方间距（`PaddingValues(28.dp, 8.dp)`），**不要再额外加 top**。
  加了会让第二组比第一组低 8dp，整页分组看起来不齐（`FeatureDetailActivity.kt` 踩过）。
- 页面结尾不加额外间距，最后一块自己的 12dp 就是收尾。补 24dp 会让这个二级页的底部留白比别的页多一倍。

### 2.2 其它间距的归属

| 场景 | 值 | 用哪个 |
|---|---|---|
| 卡片之间 / 卡片与屏幕边缘 | 12dp | `firstCard()` / `nextCard()` |
| 对话框里两个并排按钮之间 | 12dp | `DshSpacing.Block`（与块间距同值，没有理由更大） |
| 列表行内边距 | 12dp 水平 / 8dp 垂直 | `DshSpacing.Block` / `DshSpacing.Compact` |
| 图标与右侧文字之间 | 16dp | `DshSpacing.CardInside` |
| 卡内边距 | 16dp | `CardDefaults.InsideMargin`（不要手填） |
| 标题与紧邻副标题之间 | 2dp | `DshSpacing.Hairline`（1dp 在低密度下会被抹成 0） |
| chip / 徽标内边距 | 4×2dp | `DshSpacing.Tight` + `Hairline` |
| 搜索栏与屏幕边缘 | 12dp | `DshSpacing.Block` |
| 身份区（图标 + 名称 + 包名） | 28dp 水平 / 12dp 垂直 | 水平对齐 `SmallTitle` 缩进 |

### 2.3 图标尺寸阶梯（四个值，不要加第五个）

| 值 | 用途 | 出处 |
|---|---|---|
| 58dp | 列表行左侧图标 | Cirno `AppItemCompose` 钉死这个数 |
| 72dp | 二级页身份区图标 | 比列表大一号，表示「进到里面了」 |
| 100dp | 关于页应用图标 | HyperMusicCover 关于页同尺寸 |
| 110dp | 状态卡底纹图标 | 故意出血，只露一角（配 `offset(27.dp, 31.dp)`） |

`20dp` 只给 `InputField` 的 `leadingIcon`（搜索放大镜），那是组件内部尺寸，不算阶梯。

### 2.4 组件默认值（来自 miuix 源码，不要手填）

| 组件 | 默认值 | 出处 |
|---|---|---|
| `Card` 圆角 | `CardDefaults.CornerRadius` | `basic/Card.kt` |
| `Card` 内边距 | `CardDefaults.InsideMargin` | 同上 |
| `BasicComponent` 内边距 | `PaddingValues(16.dp)` | `basic/Component.kt` |
| `SmallTitle` 内边距 | `PaddingValues(28.dp, 8.dp)` | `basic/SmallTitle.kt` |
| `TopAppBar` 折叠高度 | `52.dp` | `basic/TopAppBar.kt` |
| `TopAppBar` 标题内边距 | `26.dp` | 同上 |
| `TopAppBar` 图标内边距 | `16.dp` | 同上 |

### 2.5 字号（`MiuixTheme.textStyles`，源码实测）

| 样式 | 字号 | 用途 |
|---|---|---|
| `title1` | 32sp | 超大标题（关于页应用名） |
| `title2` | 24sp | 大标题 |
| `title3` | 20sp | 页面主标题、状态卡标题 |
| `title4` | 18sp | 列表项标题、次标题 |
| `main` / `paragraph` / `button` / `headline1` | 17sp | 正文主字号（`paragraph` 带 1.2em 行高） |
| `body1` / `headline2` | 16sp | 正文 |
| `body2` / `subtitle` | 14sp | 副标题、摘要（`subtitle` 加粗） |
| `footnote1` | 13sp | 脚注、列表 summary（包名、路径） |
| `footnote2` | 11sp | 极小字 |

**规则：不要写裸 `fontSize = 14.sp`。** 一律 `style = MiuixTheme.textStyles.xxx`，
只在需要强调时叠 `fontWeight`。这样换主题、换字号时全工程跟着变。

唯一豁免是 `StatusTag`（9sp / 750 字重 / 6dp 圆角 / 4×2dp）：它是列表项末尾的状态 chip，
尺寸由 Cirno 原版钉死 —— 9sp 正好比 `footnote2` 再小一档，塞进单行不撑高行高。
这类豁免要在代码里写 `// miuix-check: allow-design-tokens` 并注明理由，检查脚本才放行。

---
## 3. 页面骨架（PageScaffold 为什么必须存在）

每个页面都从同一个 `PageScaffold` 出发。它固定了五件事：

1. `backdrop` 与顶栏是否透明；
2. `Scaffold` 与它的 window insets；
3. `BlurredBar` + `TopAppBar` 这一对；
4. 内容上唯一的一层 `layerBackdrop`；
5. `LazyColumn` 的 `overscrollEffect = null` 与顶部 inset 的位置。

调用方只负责「列表里放什么」。

```kotlin
PageScaffold(
    title = "主页",
    backdrop = backdrop,              // 由应用壳传入，页面不要自己建
    extraBottomPadding = navBarHeight,
) {
    item(key = "status") { /* 第一张卡 */ }
    item(key = "data")   { /* 第二张卡 */ }
}
```

### backdrop 的份数与归属（这条写错会崩进程）

**`PageScaffold` 自己持有一份 backdrop，签名收的是 `isBlurEnabled: Boolean`，不是 `LayerBackdrop`。**
它把 `layerBackdrop` 只注册在**包住列表的那个 Box** 上，顶栏 `BlurredBar` 在注册层之外：

```kotlin
val backdrop = rememberBlurBackdrop()          // 内部是 rememberLayerBackdrop { drawRect(surface); drawContent() }
val blurActive = isBlurEnabled && backdrop != null

Scaffold(
    topBar = { BlurredBar(backdrop, blurActive, scrollBehavior) { TopAppBar(...) } },
) { innerPadding ->
    Box(modifier = if (blurActive) Modifier.layerBackdrop(backdrop) else Modifier) {
        LazyColumn(...)                        // ← 只有列表在注册层里
    }
}
```

底栏那层在应用壳里独立存在，包住 pager：

```kotlin
val backdrop = rememberLayerBackdrop {
    drawRect(MiuixTheme.colorScheme.surface)   // 没有内容时也有底色，不会透出黑
    drawContent()
}
// 底栏在 Scaffold(bottomBar = ...) 里，天然在注册层之外
Box(modifier = if (isBlurEnabled) Modifier.layerBackdrop(backdrop) else Modifier) { pager }
```

**所以「两份 backdrop」是正确的，「一份共用」是错的。** 早期版本把应用壳的 backdrop
作为参数传给 PageScaffold 共用，结果注册层同时圈住了 `BlurredBar` 自己 ——
采样源指向自己，渲染树递归 500+ 层，装机后直接 native SIGSEGV：

```
#510 pc /system/lib64/libhwui.so (SkiaDisplayList::prepareListAndChildren)
#511 pc /system/lib64/libhwui.so (RenderNode::prepareTreeImpl)
NativeCrashListener: nativeCrash pkg=... signal=11
```

判断标准只有一句：**`layerBackdrop` 那一层的子树里，不能出现任何用同一个 backdrop 做模糊的组件。**
「只创建一个对象」和「只注册一次」是两件事，前者省内存，后者才是防崩。

**关键细节（照抄，别改）：**

```kotlin
contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
    .only(WindowInsetsSides.Horizontal),
```

只取左右两个方向的 inset —— 上下由 `TopAppBar` 和 `bottomBar` 自己处理，
否则内容会被顶栏再挤一次，出现双倍留白。

**它存在的理由**（原注释）：这段代码在成为函数之前被复制过六次，副本之间漂移了 ——
有一页把顶部 inset 放在列表自己的 `modifier` 上，列表被压到栏下面，什么也滚不过去，
backdrop 在上面采不到东西，那页的栏就跟别的页不一样了。这个骨架的作用是让这种失败模式
**不可能发生**，而不只是「不太可能」。

---

## 4. 两态底栏

标签是**数据**，不是散在 `when(page)` 里的分支。`assets/AppShell.kt` 里定义了：

```kotlin
data class AppTab(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val content: @Composable (isBlurEnabled: Boolean, bottomPadding: Dp) -> Unit,
)
```

换产品形态时只改 `defaultTabs()` 这一个列表（三标签内容型、三标签媒体型），
`AppShellScreen` 的逻辑一行都不用动。散着写时改一次标签要动五处，还容易漏掉
底栏图标与返回键的对应关系。

`navBarMode`：`0` 贴底实心 / `1` 悬浮。两种共用底栏那一份 `backdrop`（它们互斥，同一时刻只有一个在渲染）。

| 形态 | 组件 | 关键参数 |
|---|---|---|
| 贴底 | `NavigationBar` + `NavigationBarItem` | 模糊时条透明，模糊由 `textureBlur(shape = RectangleShape, blurRadius = 25f)` 提供，混合色 `surface.copy(0.5f)` |
| 悬浮 | `FloatingNavigationBar` + `FloatingNavigationBarItem` | `shape = RoundedCornerShape(FloatingToolbarDefaults.CornerRadius)`，混合色 `surfaceContainer.copy(0.4f)`，`highlight` 按深浅色选 `GlassStrokeMiddleDark/Light` |

### 4.1 为什么删掉了第三种「液态玻璃」

vcut_lower 有一份 539 行的自建 `LiquidGlassNavigationBar`（折射、边缘流光、跟手形变），
模板曾照它的观感做了一个轻量版，作为悬浮底栏的子选项。**已删除。**

原因不是实现不了，而是**那个开关是假的**：

- 打开后只是模糊半径 `25f → 32f`、混合色 `0.4 → 0.3`、加一道 `GlassStrokeMiddle*` 描边；
- 在真机上看不出区别（2026-11-17 实测，深浅两种主题都对比过）；
- 却要多背一个设置项、一条 `FoldUnder` 折叠逻辑、一个 `isLiquidGlass` 持久化字段。

**一个拨了跟没拨一样的开关，比没有这个开关更糟** —— 用户一旦发现某个开关是摆设，
就会怀疑其它开关也是，于是每个开关都要试一遍才敢信。这比少一个功能贵得多。

判定标准（可复用）：**一个设置项必须让用户在两秒内看出差别。** 看不出来就不该存在。
真要做真液态玻璃（折射、跟手形变），它是另一个视觉方案，不是「悬浮的一个开关」，
应该整体替换底栏实现，而不是叠在悬浮上再给个开关。

> 注意区分：`references/blur-and-liquid-glass.md` 仍保留 vcut_lower 液态玻璃的**原理与 API 说明**
> （`rememberDeviceTilt`、`drawBackdrop`、`Highlight` 怎么组合）。那是读代码的参考，
> 不代表模板里有这个开关。

**翻页必须用弹簧，不能用 `animateScrollToPage`：**

```kotlin
pagerState.scroll(MutatePriority.UserInput) {
    val info = pagerState.layoutInfo
    val pageSize = info.pageSize + info.pageSpacing
    val distanceInPages = index - pagerState.currentPage - pagerState.currentPageOffsetFraction
    var previous = 0f
    animate(0f, distanceInPages * pageSize, PagerNavigationSpringSpec) { v, _ ->
        previous += scrollBy(v - previous)
    }
}
```

`MutatePriority.UserInput` 不能省：否则手指同时按住时滚动会被手势抢走。

---

## 5. 状态卡范式

主页第一张卡承载「当前成不成」，它是整个界面的心脏。三条规则：

1. **大色块表达成败**，不是只写一行红字。成功用绿（`secondaryContainer` / `#DFFAE4` / `#1A3825`），
   失败用红（`errorContainer` / `#FDE8E8` / `#3D1C1C`），按「动态取色 → 深色 → 浅色」三档取值。
2. **副标题给出下一步动作**：「请在 KernelSU 中允许本应用」，而不是复述错误「权限不足」。
3. **整张卡可点 = 手动刷新**，`pressFeedbackType = PressFeedbackType.Tilt`（不要 Ripple）。
   底纹图标用 `offset(27.dp, 31.dp)` + `size(110.dp)` 故意出血，只露一角，不额外占高度。

**布局照搬 HMC，不要自己重排。** 卡内是**三个 Box 叠在一起**，不是三行 Column：

| 层 | 对齐 | padding | 内容 |
|---|---|---|---|
| 1 底纹图标 | `BottomEnd` + `offset(27.dp, 31.dp)` | 无 | `size(110.dp)`，`Icons.Rounded.CheckCircleOutline` / `ErrorOutline` |
| 2 应用名 | `BottomStart` | `(16.dp, 10.dp)` | `body1` + Medium |
| 3 标题 + 副标题 | `TopStart` | `(16.dp, 14.dp)` | `title3` SemiBold + `Spacer(1.dp)` + `body2` |

外层 `Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min))`：里面三个 Box 都是
`fillMaxSize`、本身不贡献高度，没有这层 `IntrinsicSize.Min` 卡片会塌成文字那么高。

三个 Box 叠放的理由：模式与图标**斜向平衡**，中间留白才不显得挤。
换成三行 Column 会把它们挤成一团在顶部，图标也出不了血。

`16/10` 与 `16/14` 不是随手写的：应用名要贴底但比卡内边距更靠下一点（8dp 会显得浮起来），
正文比应用名多 4dp 才与下方图标的视觉重心对得上（12dp 标题会往上顶）。
这两处已加 `// miuix-check: allow-design-tokens` 豁免，不要按 12dp 栅格改掉。

图标直接用 `material-icons-extended` 的 `Icons.Rounded.*`，不要自绘：
自绘的勾怎么调都不如原版（包围盒偏心、圆环与勾比例不对），
而 release 包只多 0.56MB（R8 裁掉未引用的图标类）。详见 `references/hmc-patterns.md` 第 9 节。

**三态都要有：** 加载中（`正在检查…`）、成功、失败。加载中不要清空上一条答案 ——
那会闪一下红卡再变绿，用户会以为出事了。

### 5.1 前两张卡不加分组标题（硬规范）

**运行卡片页的前两张卡，上面不准出现 `SmallTitle`（或任何形式的分组标题）。**

运行卡片页指以「当前在不在跑」开头的主页类页面：第一张是状态卡（运行中 / 未运行），
第二张是数据卡（可用空间、当前版本、运行状态）。

```kotlin
// 错：给状态卡加标题「运行状态」，给数据卡加标题「应用信息」
item(key = "status_title") { SmallTitle("运行状态") }   // ← 删掉
item(key = "status") { StatusCard(...) }
item(key = "data_title")   { SmallTitle("应用信息") }   // ← 删掉
item(key = "data") { Card { ... } }

// 对：前两张直接起，第三张开始才允许分组
item(key = "status") { StatusCard(...) }
item(key = "data")   { Card { ... } }
item(key = "device_title") { SmallTitle("设备信息") }    // 从第三张卡起才允许
item(key = "device") { Card { ... } }
```

**为什么：**

1. **分组标题的作用是把同类卡收拢。** 前两张卡各只有一张，没有可收拢的同类 ——
   「运行中」上面写「运行状态」，「可用空间」上面写「应用信息」，标题只是把内容复述一遍。
2. **首屏纵向空间最贵。** `SmallTitle` 自带 `PaddingValues(28.dp, 8.dp)`，两个标题吃掉 72dp，
   正好是一张数据卡的高度。手机首屏就那么大，前两张卡是用户唯一一定会看的东西。
3. **状态卡自己已经回答了「这是什么」。** 大色块 + 标题 + 副标题 + 应用名，四层信息；
   再加一个灰色小标题只会稀释它。

**允许加分组标题的位置：** 第三张卡起，以及任何「有两张以上同类卡需要收拢」的地方
（例如设备卡与网络卡都叫「设备相关」）。判据是**标题与卡片数量比**：
一个标题下至少两张卡，否则就是多余的。

检查脚本已带这条（`check_first_cards`，WARNING）。

---

## 6. 主题与夜间模式

```kotlin
val controller = remember(themeMode) { ThemeController(themeMode) }
MiuixTheme(controller = controller) { /* ... */ }
```

可用模式：`System / Light / Dark / MonetSystem / MonetLight / MonetDark`。

**三个必须做的事：**

1. **系统栏图标跟随应用主题**（不是系统主题）。`enableEdgeToEdge()` 的 `SystemBarStyle.auto`
   只跟随系统深浅色；应用内强制浅色时状态栏图标会是白的，看不见。
   在 `MiuixTheme` 里用 `SideEffect` + `WindowCompat.getInsetsController` 按 surface 亮度设置。
2. **主题切换在 `onStop` 应用**，不在设置改变时。它是一次 configuration change，会重建 Activity；
   当场应用用户会看到开关跳一下。挪到 `onStop`，结果一样但没人在看。
3. **`onResume` 让 `settingsEpoch` 自增**。主题页是独立 Activity，在本页 stopped 期间保存改动；
   不重读就停在你离开时的主题上，直到下次冷启动。

### 6.1 主题设置板块硬性完整保留（不准删、不准减项）

**`ThemeActivity` 这个二级页整块保留，三组内容一项都不能少。** 它不进入 9.5.1 的可删清单。

| 必须保留 | 控件 | 为什么不能删 |
|---|---|---|
| **六个主题模式** | `WindowDropdownPreference` 单选（不是六行 `ArrowPreference`，也不是只有浅色/深色两档） | 跟随系统 / 浅色 / 深色 × 普通 / Monet，六种组合覆盖「跟系统走」与「固定一种」两类诉求。下拉文案固定为 `跟随系统` `浅色` `深色` `Monet 跟随系统` `Monet 浅色` `Monet 深色` —— 用 **Monet** 而不是「动态取色」，与系统叫法对齐，也让六个短标签前两字就能分组。砍成两档就丢了 Monet 取色与「跟随系统」 |
| **悬浮底栏开关** | `SwitchPreference` | 底栏形态（贴底 / 悬浮）是这套模板的辨识度来源，见第 4 节 |
| **背景模糊开关** | `SwitchPreference` | 低端机 / 省电场景要能关；关掉后界面退化成纯色是设计好的降级，不是 bug |

配套的**四件基础设施同属这个板块**，删页面就等于把它们变成死代码：

1. `AppSettings` 的 `themeMode` / `isFloatingNavbar` / `isBlurEnabled` 三个字段与 `save` / `load`；
2. `AppNightMode`（`launch_background` 里的夜间启动底色，避免冷启动白闪）；
3. `MiuixAppTheme` 里按 surface 亮度同步状态栏图标的 `SideEffect`；
4. `AppShell` 的 `onResume` 重读与 `settingsEpoch` 自增。

**为什么是硬规范而不是「建议保留」：**

- **主题是环境适配，不是可选功能。** 系统进深色、用户开 Monet，App 还停在浅色蓝，这不叫精简，叫没适配。
- **它是唯一会重建 Activity 的设置。** 删掉后 `onStop` 应用主题、`settingsEpoch` 重读、`AppNightMode` 夜间启动色这条链路整体失效，剩下的代码全是死的。
- **二级页形式本身就是排班结论。** 设置页超过约 12 行才拆出主题页（`references/hmc-patterns.md` 第 2 节）；拆都拆了又删，等于同时违反「不要过长设置页」和「不要空壳二级页」两条。
- **删了没法反悔。** 用户装到没有主题页的版本，只能靠系统强制深色，App 自身的取色与蒙版体系全部拿不到。

**允许的改动边界：**

| 允许 | 不允许 |
|---|---|
| 换主题页的入口位置（设置页第一行的 `ArrowPreference`） | 删掉入口 |
| 在三个开关之外**增加**新开关（如「圆角强度」） | 减掉表中任一项，或把六个模式砍成两档 |
| 调主题页内部的分组标题、行顺序 | 把主题选择搬回设置页就地展开（会触发整页重建） |
| 换 `WindowDropdownPreference` 的文案 | 用 `Overlay*` 弹层替代 `Window*`（依赖 `popupHost`，主题页里没有 Scaffold） |

检查脚本已带这条（`check_theme_block`，ERROR）。

---

### 6.2 应用壳必须重读设置（硬约束）

**应用壳（调 `AppShellScreen` 的那个 Activity）必须走「onResume 自增 + remember 重读」这条链。**

```kotlin
// 对：每次回到前台都重读
private var settingsEpoch by mutableStateOf(0)

override fun onResume() {
    super.onResume()
    settingsEpoch++
}

setContent {
    val settings = remember(settingsEpoch) { AppSettings.load(this) }
    AppShellScreen(isFloatingNavbar = settings.isFloatingNavbar, isBlurEnabled = settings.isBlurEnabled)
}

// 错：onCreate 读一次，整个进程生命周期都不再读
val settings = AppSettings.load(this)          // ← 快照
setContent { AppShellScreen(isFloatingNavbar = settings.isFloatingNavbar) }
```

**为什么这是硬约束：**

主题页是独立 Activity，它在自己的生命周期里 `AppSettings.save`，壳那边完全不知道。
没有 `onResume` 重读，从主题页返回后壳里的 `isFloatingNavbar` / `isBlurEnabled` 还是进 App 时的旧值 ——
**用户看到的就是「开关拨了没反应」**。

主题模式之所以常常「看起来有效」，是因为它用 `remember { mutableStateOf(...) }` 包着，
下一次 Activity 重建时会重新执行 —— 这是**重建时机凑巧**，不是机制正确。
一旦某次返回没有触发重建，连主题模式也一起失效。

检查脚本已带这条（`check_settings_reload`，WARNING）：壳文件里有 `AppSettings.load(`
和 `AppShellScreen(` 却没有 `onResume` 就报。二级页（FeatureDetail / AppList 等）不判定。
---

## 7. 模糊

```kotlin
@Composable
fun rememberBlurBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported() || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}
```

- **API 33 硬门槛**：RuntimeShader 从 T 开始有。低于 33 返回 `null`，所有 `blurActive` 自动为 false，
  界面退化成纯色 —— 这是设计好的降级，不是 bug。
- **顶栏用 `progressiveTextureBlur`，不是全屏 blur**：只模糊顶栏那一块，并按渐变衰减到清晰。
  参数集中在 `TopBarBlurConfig`：`BlurRadius = 15f`、`GradientCurve = 10f`、`SurfaceAlpha = 0.3f`、
  `ScrollFadeDistance = 0.dp`（0 = 常驻完整模糊，各页顶部即可见模糊）。
- **底栏用 `textureBlur`**，`blurRadius = 25f`，形状随形态（矩形 / 圆角）。
- 详细原理与性能代价见 `references/blur-and-liquid-glass.md`（该文件也记录了 vcut_lower
  自建液态玻璃底栏的 API 组合，那是读代码的参考，模板里没有这个开关 —— 见 4.1）。

---

## 8. 性能与缓存（要点摘录）

完整版见 `references/performance-and-caching.md`。写界面时逐条对照：

> **体积实测（2026-11-17，`com.demo.fresh` 空工程）**：debug `app-debug.apk` = **31.8 MB**，
> 同一份代码 release 开 R8 = **5.95 MB**（dex 5.55 MB）。
> 拆包看：debug 的 26 MB 差值全在 dex —— `androidx.compose` 7664 个类占 16 MB，
> `top.yukonga`（miuix）1770 个类 + coroutines + materialkolor 占 13.6 MB，
> 而 `res` 只有 122 KB、`lib` 只有 44 KB。**「什么功能都没有却 31 MB」不是模板的问题，是 debug 不裁剪。**
> 所以 `proguard-rules.pro` 是交付物的一部分（`assets/res/proguard-rules.pro`），不是可选优化。

| 规则 | 做法 | 违反后果 |
|---|---|---|
| 状态类型稳定 | 页面状态一律 `data class`，全 `val`，只含基本类型/String/枚举/data class | 编译器无法证明稳定性，整棵子树每次重组 |
| 高频源 → 低频结果 | `derivedStateOf { pagerState.currentPage == 3 }` | 逐帧动画在非当前页也跑，翻页掉帧 |
| composable 内禁止 IO | 读 prefs / 查库 / 系统服务放 `LaunchedEffect`、`produceState` 或 repository | 每次重组都读盘，主线程卡顿 |
| 系统信息只问一次 | `remember { Settings.Global.getString(...) }` | `Settings.Global` 是跨进程调用进 system_server，每次重组都打一次 |
| 轮询跟生命周期 | `repeatOnLifecycle(Lifecycle.State.RESUMED)` | 后台继续轮询，耗电 |
| 进程内缓存上次状态 | `Vcut.last ?: Status()` | 新页面从空状态等第一次读完，闪红卡 |
| Pager 预载 | `beyondViewportPageCount = 1` | 默认 0 翻页时白屏 |
| 列表回弹 | `overscrollEffect = null` + `overScrollVertical()` | 两套回弹打架 |
| 构建瘦身 | `isMinifyEnabled` + `isShrinkResources` + `proguardFiles` | debug 31.8 MB → release 5.9 MB（实测）；不开 R8 时 26 MB 全是没用到的 compose/miuix 类 |
| ABI 收窄 | `ndk { abiFilters += "arm64-v8a" }` | 四个 ABI 各带一份 native 库，包体翻倍（纯 Compose 模板 lib 只有 44 KB，真实工程带 so 时差距才明显） |

### 缓存分层（三层，别混）

| 层 | 载体 | 存什么 | 失效时机 |
|---|---|---|---|
| 内存 | 进程级 `val last: Status?` | 上次查询结果 | 进程死亡 |
| 磁盘 | `SharedPreferences` / JSON 文件 | 外观设置、用户偏好 | 用户清除数据 |
| 跨进程 | 广播 / Binder / AIDL | 与 hook、SystemUI 等其它进程交换的状态 | 由对端决定 |

**「两份真相」反例**：模块自身的设置（封面偏移、时钟缩放）属于跑在 SystemUI 里的 hook，
由 hook 自己持久化、界面通过桥去问。在 App 里再存一份，只会得到两份互相矛盾的真相。
**判定标准是「谁写」**：只有 App 写的放 App，只有 hook 写的放 hook，两边都要写的必须定一个所有者。

---

## 9. 功能排班（信息架构）

完整版见 `references/information-architecture.md`。四条最硬的：

1. **标签 3–5 个，且必须并列。** 每个标签要能回答「为什么和别的并列」。
   反例：`主页 / 功能 / 设置 / 主题设置` —— 主题设置是设置的子集，并列等于让用户猜。
2. **顺序 = 使用频率 × 首次理解成本。** 主页永远第一，设置倒数第二，关于永远最后。
3. **单页黄金顺序**：状态 → 主操作 → 数据/内容 → 次要信息 → 元信息。每卡一类，首屏 ≤3 张卡。
4. **二级页 vs 就地展开 vs 弹窗**：

| 场景 | 用什么 | 理由 |
|---|---|---|
| 完整流程、需要返回栈 | 独立 Activity | 转场、返回手势、Android 13+ 预测性返回动画都是平台自带的，就地换页只能模仿 |
| 确认 / 一次性输入 | `WindowDialog` | 不依赖 `popupHost`，任何 Composition 都能用 |
| 多选项 / 轻量编辑 | `WindowBottomSheet` / `OverlayBottomSheet` | 半屏，可滑动关闭 |
| 1–2 行内容 | 就地展开 | 跳页成本高于收益 |

**设置项排班**：开关类在上 → 跳转类在中 → 危险操作单独分组置底 + 二次确认。每组 ≤6 行。

**返回键契约**：非首标签按返回回首页（`BackHandler(enabled = selectedIndex != 0) { goToPage(0) }`），
首页交给系统退出，二级 Activity 用系统返回。预测性返回不要自己实现。

---

## 9.5 控件增删与排版（改模板时唯一流程）

模板不是一次性产物。接入真实业务时一定会加控件、删控件、挪控件，
**这一步是模板类工程最容易崩的地方** —— 不是因为难，是因为它看起来太简单：
复制一段、改个名、能跑就算完，三个月后没人知道这行是干嘛的。

下面四步是固定顺序，不跳步。

### 9.5.1 第一步：先判定「模板内与本次构建无关」的控件

拿到需求后，先把模板里**这次用不到**的东西列出来，而不是先加新的。
判据只有一条：**它是否服务于这次要交付的那个功能？**

> **例外：主题设置板块不参与这个判定。** `ThemeActivity` 整块硬性保留（见 6.1），
> 无论本次构建有没有「主题要能切」这个需求 —— 它是环境适配，不是可选功能。

| 模板里的东西 | 什么时候删 | 什么时候留 |
|---|---|---|
| `HomePage.kt` 的「设备」卡（设备名/型号/系统版本） | 产品不是设备管理类工具 | 是系统级模块（vcut / HyperMusicCover 这类） |
| `HomePage.kt` 的「数据」卡（可用空间/版本/状态） | 没有本地数据要看 | 有本地存储或运行时状态 |
| `FeaturesPage.kt` 的「应急」组 | 没有「当场执行、不跳页」的动作 | 有重启/重载/卸载这类动作 |
| `SettingsPage.kt` 的「导入/导出设置」 | 设置项少于 3 个，备份没意义 | 设置项多、或用户会换机 |
| `SettingsPage.kt` 的「危险操作」组 | 没有不可逆操作 | 有重置/清除/卸载 |
| `AboutPage.kt` 的「开源许可」 | 不分发、无第三方依赖 | 要上架或开源 |
| `ListPage.kt` + `ListViewModel.kt` | 没有「一屏列 N 个同类项」 | 有候选列表/搜索结果 |
| `AppListPage.kt` + `AppListActivity.kt` + `AppDetailActivity.kt` | 不处理已安装应用 | 是模块类 App（Xposed/LSPosed/Shizuku） |
| ~~`ThemeActivity.kt`~~ | **不列入本清单 —— 主题设置板块硬性完整保留，见 6.1** | 始终保留 |
| `effect/` 下 7 个文件 | 不需要背景特效 | 要做 HyperOS 风格背景（vcut 的做法） |

**删的时候连带删三样**，漏一样就编译不过或运行时崩：

1. **入口**：`FeaturesPage` 里指向它的 `ArrowPreference`、`AppShell` 里的 `AppTab`。
2. **manifest 注册**：`<activity android:name=".ui.screens.XxxActivity" />`。
   不删不报错，但会留下一个永远打不开的 Activity。
3. **独占的辅助文件**：只被它用的 `data class`、`ViewModel`、`drawable`。
   反过来，**共用的一定不要删** —— `StatusTag.kt` 被两个列表页共用，
   删了另一个页面直接 `Unresolved reference`。删前先 grep 类名。

```bash
# 删任何文件前先跑这个：确认没人引用
grep -rn "StatusTag\|ListEntry\|AppListViewModel" --include="*.kt" app/src/main/java/
```

### 9.5.2 第二步：新增前查重（三种重复，代价递增）

**新增控件前必须先查模板里有没有同类东西。** 三种重复：

| 类型 | 表现 | 代价 |
|---|---|---|
| 完全重复 | 同一个 composable 在两个文件里各写一份 | 编译报 `Conflicting overloads`（同包）或行为分叉（不同包） |
| 参数重复 | 自己写 `padding(12.dp)` 而不是 `nextCard()` | 改栅格时漏改，两页间距不一致 |
| 语义重复 | 同一个设置在两处都有入口，一处真一处假 | **最贵**：用户拨了没反应，会怀疑整个 App 坏了 |

查重命令（三条，按顺序跑）：

```bash
# 1. 同名 composable / data class
grep -rn "fun YourName\|class YourName\|data class YourName" --include="*.kt" app/src/main/java/

# 2. 同名字符串（语义重复最常见的形态：同一个设置项出现两次）
grep -rn "背景模糊\|主题模式" --include="*.kt" app/src/main/java/

# 3. 同一个数（手调值重复）
grep -rn "padding(" --include="*.kt" app/src/main/java/ | grep -v "firstCard\|nextCard\|DshSpacing"
```

**语义重复的真实案例**：`SettingsPage` 曾有「背景模糊」开关，`checked` 绑的是
`remember { mutableStateOf(true) }` 局部变量 —— 拨了不生效、退出页面回默认。
真正生效的那个在 `ThemeActivity` 里写 `AppSettings`。
用户看到两个同名开关，其中一个永远骗他。**同一设置只能有一个入口。**

### 9.5.3 第三步：排版遵循间距栅格

新增的每一处 `padding` / `Spacer` 都必须落在 `DshSpacing` 五档里（2/4/8/12/16，见第 2 节）；
`28.dp` 只在两处出现，且都不是手填 —— `SmallTitle` 自带、以及与之对齐的身份区。
写之前先问三个问题：

1. **这是块间距吗？**（卡片之间、卡片与屏幕边缘、对话框按钮之间）→ `DshSpacing.Block`，
   或直接用 `Modifier.firstCard()` / `nextCard()`。
2. **这是卡内边距吗？** → 不写，交给 `CardDefaults.InsideMargin`。
3. **这是组件内部的呼吸吗？** → `Compact`(8dp) 或 `Tight`(4dp)。

三个问题都答不上来的数，**不要写**。写了就是下一个 20dp。

排版还有三条不属于间距但同属「整齐」的规则：

- **一行内最多一个可点区域。** 整行可点时，行内的箭头/开关就不再单独响应点击。
- **文本块与图标对齐，对齐的是「中心」不是「顶边」。** 图标 72dp、文字两行时，
  用 `Row(verticalAlignment = Alignment.CenterVertically)` 让两者中线重合；
  给文字列限高会把多行文字挤出框外，反而更歪。
- **长文本不截断，除非截断后仍能认出是什么。** 包名截成 `com.mfcloudcalculate.ne…`
  用户认不出是哪个应用，那这一行就没有存在意义 —— 让它换行。
  反过来，列表标题截断是对的（`maxLines = 1` + `Ellipsis`），因为标题前几个字就够辨认。

### 9.5.4 第四步：删前确认引用

删除任何 composable / 参数 / 文件前，跑一遍：

```bash
# 全项目找引用（含 assets 与 fresh-test 两个副本）
grep -rn "被删的名字" --include="*.kt" .

# 找 manifest 里的注册
grep -n "被删的类名" app/src/main/AndroidManifest.xml
```

**三个必查的隐式引用**（grep 名字找不到，但删了会崩）：

| 隐式引用 | 表现 | 怎么查 |
|---|---|---|
| `Intent(context, XxxActivity::class.java)` | 编译能过，点了才 `ActivityNotFoundException` | `grep -rn "::class.java" ` |
| `AppTab(...)` 的闭包 | 删了页面函数，`AppShell` 里编译不过 | `grep -n "AppTab(" AppShell.kt` |
| 检查脚本的豁免标记 | 删了带 `miuix-check: allow-design-tokens` 的代码，标记变成孤儿 | `grep -rn "allow-design-tokens"` |

### 9.5.5 改完必须过的自检清单

31 项，逐项打勾。**任何一项不过就不要交付。**

**编译与静态检查**

- [ ] 1. `assembleDebug` 通过，0 error
- [ ] 2. `python scripts/check_miuix_usage.py --path <工程>` 输出 `0 error(s), 0 warning(s)`
- [ ] 3. 没有 `Unresolved reference` / `Conflicting overloads` / `@Composable invocations can only happen...`
- [ ] 4. 新增的 `Activity` 已在 `AndroidManifest.xml` 注册且 `exported="false"`（除入口）

**删除完整性**

- [ ] 5. 删掉的页面，入口（`ArrowPreference` / `AppTab`）一并删了
- [ ] 6. 删掉的 Activity，manifest 注册一并删了
- [ ] 7. 删掉的独占辅助文件（ViewModel / drawable / data class）一并删了
- [ ] 8. 共用的文件**没有**被误删（`grep` 过类名）

**新增正确性**

- [ ] 9. 新增 composable 前 `grep` 过同名，无重复定义
- [ ] 10. 新增字符串前 `grep` 过，同一设置只有一个入口
- [ ] 11. 新增的 `padding` 全部落在 `DshSpacing` 五档（2/4/8/12/16），28dp 只在 `SmallTitle` 对齐场景出现
- [ ] 12. 卡片间距用的是 `firstCard()` / `nextCard()`，不是手写 12dp
- [ ] 13. 字号取自 `MiuixTheme.textStyles`，没有裸 `fontSize = N.sp`（`StatusTag` 除外，且带豁免标记）
- [ ] 14. 新增的 `Icon` 用了 `MiuixIcons` 里真实存在的名字（不是猜的）

**交互与排班**

- [ ] 15. 一行内最多一个可点区域
- [ ] 16. 可点的东西都有去处（点了不做事的不做 Card）
- [ ] 17. 开关类在上、跳转类在中、危险操作单独分组置底
- [ ] 18. 每组 ≤6 行
- [ ] 19. 会重绘整个 App 的设置（主题、语言）放在二级 Activity，不就地改
- [ ] 20. 二级页没有底栏，返回靠顶栏箭头 + 系统手势

**对齐与长文本**

- [ ] 21. 图标与文字块中线对齐（`Row(verticalAlignment = CenterVertically)`）
- [ ] 22. 长文本按「截断后还认不认得出」决定换行还是省略号
- [ ] 23. 列表项 `key` 唯一且稳定（复合 key，如 `packageName + "#" + userId`）
- [ ] 24. 深色与浅色两种主题下都看过截图（不能只看一种）
- [ ] 25. 运行卡片页的前两张卡上方没有 `SmallTitle` 分组标题（见 5.1）
- [ ] 26. 界面文案里没有用括号做补充说明；该进 `summary` 的都进了（见 9.6）
- [ ] 27. 主题设置板块完整：六模式下拉 + 悬浮底栏 + 背景模糊三组都在，入口可点进去（见 6.1）
- [ ] 28. 没有任何「点一下切到下一个」的多选一；≥3 个选项都走 `WindowDropdownPreference`（见 9.7）
- [ ] 29. 同一个配置功能只有一个入口，没有「主页一个、设置页又一个」（见 9.8）
- [ ] 30. 应用壳有 `onResume { settingsEpoch++ }` 与 `remember(settingsEpoch)` 重读（见 6.2）
- [ ] 31. 入口标题与目标页标题同名；同一工厂函数调用风格统一（全用命名参数）（见 9.8）

### 9.6 界面文案：不用括号补充说明

**界面文案里不准用括号做补充说明。** 要补充就交给副标题，或者拆成两行。

| 场景 | 错 | 对 |
|---|---|---|
| 开关的代价 | `title = "保持存活（会增加内存占用）"` | `title = "保持存活"` + `summary = "会增加内存占用"` |
| 操作的后果 | `title = "清空配置（不可恢复）"` | `title = "清空配置"` + `summary = "不可恢复"` |
| 状态的补充 | `summary = "已加载（共 3 个模块）"` | `summary = "已加载，共 3 个模块"` |
| 单位 | `"12.4（GB）"` | `"12.4 GB"` |
| 可选标记 | `title = "备注（可选）"` | `title = "备注"` + `summary = "可不填"` |

**为什么：**

1. **括号最先被折行、被截断。** 窄屏上一行装不下时，`（会增加内存占用）` 整段会掉到第二行，
   或者被 `Ellipsis` 吃掉一半 —— 用户看到的是 `保持存活（会增加内存…`，最关键的代价反而没了。
2. **括号是零信息量的字符。** 中文全角括号占两个字符宽，一对就是四个字符，
   等于把副标题的字数预算花在标点上。
3. **副标题本来就是这个用途。** `SwitchPreference` 的 `summary`、`ArrowPreference` 的 `summary`
   就是给「打开后会发生什么」用的 —— 把括号里的内容搬过去，位置更好、层级也更清楚。

**例外：技术标识原样保留。** 包名（`com.foo.bar`）、版本号（`v1.0`）、文件路径、正则、
代码片段、系统返回的错误原文 —— 这些是数据不是文案，括号照留（`getInstalledPackages(GET_META_DATA)` 不要改）。

**脚本判定规则：括号里的内容全是 ASCII 就放行。** `P2P(32)`、`投屏（com.xiaomi.mirror:cast）`、
`互传（miLyraShareTransfer）`、`12.4（GB）` 都不报；只有括号里出现中文（`保持存活（会增加内存占用）`）才报。
这条是 2026-11-21 跑小米妙播工程时补的 —— 初版对上面四个技术标识全部误报。

**判定问句：把括号连内容一起删掉，这句话还能读吗？**
能读 → 括号里的是补充，搬去副标题或删掉；不能读 → 说明它是正文的一部分，用逗号或拆行，不要括起来。

检查脚本已带这条（`check_ui_copy`，WARNING）。

### 9.7 多选一必须用下拉，禁止点击循环切换（硬约束）

**两个禁令：**

1. **不准「点一下切到下一个」** —— 一行点一次在 N 个选项里轮转（`(x + 1) % n`、
   `when (x) { 0 -> 1; 1 -> 2; else -> 0 }`、`mode = nextMode(mode)`）。
2. **多选一（≥3 个选项）必须用 `WindowDropdownPreference` 弹出下拉框。**

| 选项数 | 用什么 | 例子 |
|---|---|---|
| 2 个，且每项要一句说明 | 两行平铺（各自 `title` + `summary`） | 「浅色 / 深色」二选一 |
| **≥3 个** | **`WindowDropdownPreference` 下拉** | 主题六模式、刷新间隔、排序方式、语言 |
| 布尔（开 / 关） | `SwitchPreference` | 悬浮底栏、背景模糊 |

**为什么不准循环切换：**

1. **点几下才对？** 三个选项平均要点 1.5 次、六次要 2.5 次，而且点过头就得再绕一整圈。
   用户永远猜不到下一次点击会把值变成什么 —— 这不是选择，是抽奖。
2. **看不见全部选项。** 循环切换的界面上没有任何地方告诉用户「一共有 6 种」，
   他只能靠反复点去发现边界。下拉点开就列全，用户一眼知道范围。
3. **没法回退。** 下拉里点回原来那项就行；循环切换里要算「再点几下」。
4. **误触代价不同。** 下拉要先点开再选，中间隔一层确认；循环切换一点就变，且不可撤销。

**为什么 ≥3 就要求下拉、不留在平铺：** 三个选项平铺占三行，加上分组标题要吃掉首屏四行；
而下拉只占一行（行尾显示当前值）。同一信息，一种写法占一行，另一种占四行。

`WindowDropdownPreference` 是 Window 级组件，不需要 `Scaffold` 的 `popupHost`
（见硬约束第 2、3 条）——所以主题页这种没有 Scaffold 的二级页里也能直接用。

```kotlin
// 对：下拉，行尾显示当前值，点开列全
WindowDropdownPreference(
    items = labels,
    selectedIndex = currentIndex,
    title = "主题模式",
    summary = labels[currentIndex],      // 收起状态下用户唯一需要知道的
    onSelectedIndexChange = { settings = settings.copy(themeMode = keys[it]) },
)

// 错：点击循环切换
ArrowPreference(
    title = "主题模式",
    summary = labels[currentIndex],
    onClick = { index = (index + 1) % labels.size },   // ← 禁止
)
```

检查脚本已带这条（`check_option_picker`，WARNING）—— 静态只能抓「模运算轮转」与
「onClick 里调用 next/cycle/rotate 类函数」两种最常见写法，其它形态靠 9.5.5 人工核。

---

### 9.8 功能入口唯一：同一配置不准放两处入口（硬约束）

**同一个配置功能，全工程只能有一个入口。** 主页放一个、设置页又放一个，是最常见的违反形态。

| 错 | 对 |
|---|---|
| 主页状态卡点进去是「主题设置」，设置页第一行也是「主题设置」 | 只在设置页留入口；主页状态卡点进去是**状态详情**或什么都不做 |
| 功能页有「应用列表」，设置页「高级」里也有「应用列表」 | 只留一个；另一个位置放**不同**的东西 |
| 关于页有「问题反馈」，设置页底部也有「问题反馈」 | 只留关于页 |

**为什么这是硬约束：**

1. **两处都要维护。** 改了一处的文案、加了一处的跳转，另一处就悄悄变成旧版；三个月后没人说得清哪个是对的。
2. **用户会以为自己没保存。** 在主页点进去把主题改了，回到设置页发现同名入口还在，
   第一反应不是「有两个入口」，而是「刚才的改动撤销了」。
3. **它让「功能」看起来比实际多。** 界面上两个入口 = 用户认为有两个功能，
   点进去发现是同一个，信任感下降一次。
4. **真正的重复往往藏着一个假的。** 常见形态是其中一处 `checked` 绑的是
   `remember { mutableStateOf(...) }` 局部变量（见 9.5.2 的真实案例），
   拨了不生效 —— 用户从此不相信任何开关。

**允许的相邻形态（不算重复）：**

- 概览 + 详情：主页状态卡显示「运行中」，点进去是**状态详情**（日志、耗时），
  设置页的「运行状态」是另一件事的开关 —— 两者标题不同、目标不同，不算重复。
- 同一 Activity 承载多种内容（如 `FeatureDetailActivity` 靠 Intent extra 区分）——
  一个入口类、多个内容，不违反。
- 快捷方式 / 通知跳转：不是界面上的第二个入口，是另一个入口**渠道**。

**判定问句：这两个入口点进去，改的是不是同一份设置？**
是 → 删掉一个；不是 → 它们本来就不是同一个功能，标题要写得能区分。

**两条附加要求（同属入口规范）：**

1. **入口标题必须与目标页标题同名。** 入口写「音频延迟设置」、点进去标题写「音频延迟」是不行的 ——
   用户会以为进了另一个地方。同一个功能从头到尾只有一个名字。
2. **同一工厂函数的所有调用风格必须统一。** `FeatureDetailActivity.intent(ctx, title = "X", summary = "...")`
   与 `FeatureDetailActivity.intent(ctx, "X", "...")` 混用时，改参数顺序的人一定会漏掉一半。
   全用命名参数（`title = ` / `summary = `），位置参数只在参数只有一个时用。

检查脚本已带这两条（`check_entry_unique` / `check_orphan_activity`，WARNING）：

1. **同一目标 Activity 在 ≥2 个文件被 `startActivity(Intent(...))` 跳转** → 报；
2. **同一 `(Activity, title)` 在 ≥2 个文件作为跳转参数出现** → 报。这一条专门抓
   「一个通用详情页承载多种内容」的工程 —— 形态是 `FeatureDetailActivity.intent(ctx, "音频延迟", ...)`
   出现在三个页面里。命名参数 `title = "X"` 与位置参数都要认（同一工厂被两种风格调用，
   本身也是不一致）。
3. **Activity 继承了 `ComponentActivity` / `Activity` 却没在 manifest 注册** → 报。
   未注册的 Activity 是死代码，但它往往是一份「完整版」（有 `settingsEpoch`、`onResume` 重读），
   而真正在用的那份是残缺版 —— 两套壳并存时最难查，因为代码看着都对。

> **真实反例（小米妙播模块，2026-11-21 实测）**：`AppShell.kt` 里有一个功能完整的
> `AppShellActivity`（`settingsEpoch` + `onResume` 重读 + `applyNightMode` + `surfaceArgb`），
> 但 manifest 只注册了 `MainActivity` —— 它在 `onCreate` 里 `val settings = AppSettings.load(this)`
> 读一次就再也不读。结果：从主题页返回后，「悬浮底栏」「背景模糊」两个开关拨了没反应，
> 只有主题模式因为 `runCatching { ColorSchemeMode.valueOf(...) }` 在下一次重建时重读，看起来有效。
> 用户的原话是「主题页设置只有主题模式有效」。

---
## 10. 事实基线（2026-11）

| 项目 | 值 |
|---|---|
| Miuix 最新发布 | `0.9.4`（Maven Central，2026-09-20） |
| Miuix 构建基线 | Kotlin 2.4.20、Compose Multiplatform 1.12.0、AGP 9.4.1、Gradle 9.7.1 |
| Android minSdk | `24`；`miuix-blur` 需要 `33` |
| 平台 | Android、iOS、macOS、Desktop(JVM)、WasmJs、Js |
| 文档 | https://compose-miuix-ui.github.io/miuix/zh_CN/ |
| 真实案例 A | `zyl6932/vcut_lower`：Miuix `0.9.4-rc01`，minSdk 35、compileSdk 37、Kotlin 2.4.10、AGP 9.4.0、Compose BOM 2026.08.00 |
| 真实案例 B | `zyl6932/HyperMusicCover`：同版本组合，HyperOS 4 音乐锁屏模块 |
| 真实案例 C | `Adkimsm/Cirno`：候选列表与筛选（双流模型、分组条、StatusTag） |
| 模板自测 | 2026-11-17 在 `com.demo.fresh` 空工程上 `assembleDebug` 通过，APK 31.8 MB |
| 模板自测 2 | 2026-11-17 用 `scaffold_app.py` 在 `com.demo.fresh2` 全新铺 33 个文件（28 个 `.kt` + 4 个 res + 1 个 proguard）后 `assembleDebug` 直接通过，检查脚本 0 error 0 warning |
| 间距基线 | 2026-11-17 审计 5 处手调值（20/24/8/5/1dp）后统一到 `DshSpacing` 五档，四页实机复核一致 |
| 查重基线 | 2026-11-17 检查脚本新增两项后立刻抓出两个真实重复定义：`GroupedRow` 与 `EmptyHint` 在 `AppListPage.kt` / `ListPage.kt` 同包各一份。已改名为 `AppGroupedRow` / `AppEmptyHint` |

**这些不是永久事实。** 接入前用 `references/setup-and-versions.md` 的 Maven 元数据方法确认：

```bash
curl -s https://repo1.maven.org/maven2/top/yukonga/miuix/kmp/miuix-ui/maven-metadata.xml
```

### 10.1 包可见性（应用列表必读）

实测 HyperOS 4 / API 37：**即使 manifest 声明了 `QUERY_ALL_PACKAGES` 且
`dumpsys package` 显示 `granted=true`**，下面两个 API 仍只返回 **1 个（自己）**：

```kotlin
pm.getInstalledPackages(GET_META_DATA)   // -> 1
pm.getInstalledApplications(GET_META_DATA) // -> 1
```

而下面这个正常返回全部（实测 111 个）：

```kotlin
pm.queryIntentActivities(
    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
)  // -> 111
```

这不是 bug 而是厂商收紧：系统只保证「带启动器入口的应用」可被枚举。
对「应用列表」页面来说这恰好是对的语义 —— 用户在这里找的就是能打开的应用。
正确写法是先按启动器查，`.ifEmpty { pm.getInstalledApplications(flags) }` 兜底。

> 症状：列表只显示一条自己，看起来像列表组件坏了，其实是权限/枚举方式。

模块清单：`miuix-core`、`miuix-ui`、`miuix-blur`、`miuix-preference`、`miuix-icons`、`miuix-squircle`、
`miuix-shader`、`miuix-nav`、`miuix-window`、`miuix-material`、`miuix-glass`、`miuix-glass-icons`。
Android 用 `-android` 变体。

---

## 11. 资源导航

| 资源 | 路径 | 用途 |
|---|---|---|
| **应用壳模板** | `assets/AppShell.kt` | 四标签 + 两态底栏 + 弹簧翻页 + 主题重读 |
| 页面骨架 | `assets/PageScaffold.kt` | 顶栏模糊 + insets + 列表 |
| 间距 | `assets/CardSpacing.kt` | `firstCard()` / `nextCard()` |
| 模糊工具 | `assets/BlurUtils.kt` | backdrop、`BlurredBar`、`isInDarkTheme`、`TopBarBlurConfig` |
| 主题壳 | `assets/MiuixAppTheme.kt` | `MiuixTheme` + 系统栏同步 |
| 设置存储 | `assets/AppSettings.kt` | 外观设置 + 夜间模式 + 启动底色 |
| 翻页弹簧 | `assets/PagerNavigationSpring.kt` | `PagerNavigationSpringSpec` |
| 主页 | `assets/HomePage.kt` | 状态卡 + 数据卡 + 设备卡 |
| 功能页 | `assets/FeaturesPage.kt` | 一功能一卡 + 应急行 |
| 设置页 | `assets/SettingsPage.kt` | 分组 + 危险操作确认 |
| 关于页 | `assets/AboutPage.kt` | 身份卡 + 链接 + 许可 |
| **候选列表** | `assets/ListPage.kt` | 分组条 + 搜索栏 + 分类弹层 + 三态 + 下拉刷新 |
| 列表 ViewModel | `assets/ListViewModel.kt` | 双流模型：全量流判断有无数据，过滤流决定画哪些 |
| 状态标签 | `assets/StatusTag.kt` | 列表项末尾的 chip（9sp / 750 / 6dp 圆角，带设计 token 豁免标记） |
| **应用列表** | `assets/AppListPage.kt` + `assets/AppListViewModel.kt` | 真实读取已安装应用：图标解码在 IO 线程、包可见性坑、launcher query 兜底 |
| 应用列表外壳 | `assets/AppListActivity.kt` | 演示 `PageScaffoldColumn` 的用法（内容是「自己会滚的列表」） |
| 应用二级菜单 | `assets/AppDetailActivity.kt` | 身份区 + 本应用开关 + 系统出口 + 危险操作置底 |
| **间距阶梯** | `assets/DesignTokens.kt` | `DshSpacing` 五档（2/4/8/12/16）+ `DshIconSize` 四档（58/72/100/110），全工程唯一来源 |
| **状态卡同步清单** | `CHANGELOG.md` | 已按旧版铺过模板的工程，把 `StatusCard` 改成 HMC 原版的逐步骤说明 |
| **功能排班** | `references/information-architecture.md` | 标签数量/并列性/顺序/二级页判定/自查表 |
| **性能与缓存** | `references/performance-and-caching.md` | 重组/模糊开销/列表/轮询/缓存分层/构建优化/自查表 |
| **候选与筛选列表** | `references/list-and-filter.md` | 双流模型/分组条/列表项四槽位/标签/搜索/分类弹层/三态/刷新/滚动条/弹窗列表 |
| **HMC 实战范式** | `references/hmc-patterns.md` | 条件折叠/主题二级页/关于页 logo/弹窗按钮/状态卡/模糊参数/背景动效/闪屏/宽屏 |
| 组件速查 | `references/component-catalog.md` | 组件 import、关键参数、所属模块 |
| 主题与图标 | `references/theme-and-icons.md` | 颜色、文本样式、MiuixIcons 清单、系统栏 |
| 页面配方 | `references/page-patterns.md` | 列表、设置、表单、搜索、弹层组合 |
| 真实案例 | `references/real-world-case-study.md` | 两个工程的架构与决策记录 |
| 模糊与玻璃 | `references/blur-and-liquid-glass.md` | layerBackdrop、textureBlur、渐进模糊、液态玻璃 |
| 导航 | `references/navigation.md` | miuix-nav 路由、转场、手势 |
| 依赖与版本 | `references/setup-and-versions.md` | 模块矩阵、坐标、版本兼容、升级迁移 |
| 故障排查 | `references/troubleshooting.md` | 常见编译/运行/显示问题 |
| **主题二级页** | `assets/ThemeActivity.kt` | 六模式下拉 + 悬浮底栏 + 背景模糊（**硬性完整保留，见 6.1**） |
| 功能详情页 | `assets/FeatureDetailActivity.kt` | 一个 Activity 承载所有「点进去」，`intent()` 工厂 |
| 背景动效 | `assets/effect/` | `BgEffectBackground` + Painter/Modifier/Config + OS2/OS3 + DeviceType |
| 图标与窗口 | `assets/res/` | `ic_about_logo` / 启动图标 / `themes.xml`（状态卡勾与感叹号来自 `material-icons-extended`，不自绘） |
| **一键铺模板** | `scripts/scaffold_app.py` | 读包名 → 铺 28 个 .kt + 4 个资源 + 1 个 proguard → 打印 manifest 段与 AppTab 列表 |
| 静态检查 | `scripts/check_miuix_usage.py` | 混用、弹层、图标、minSdk、版本一致性、栅格、字号、弹簧、backdrop、**自绘图标/重写布局的复用提醒** |

---

## 12. API 核对方式

已知文档页 URL 模式（中文优先）：

- 快速开始：`https://compose-miuix-ui.github.io/miuix/zh_CN/guide/getting-started`
- 组件页：`https://compose-miuix-ui.github.io/miuix/zh_CN/components/basic/<组件小写名>`（扩展组件在 `components/extended/`）
- API 索引：`https://compose-miuix-ui.github.io/miuix/dokka/index.html`
- 源码：`https://raw.githubusercontent.com/compose-miuix-ui/miuix/main/<模块>/src/commonMain/kotlin/top/yukonga/miuix/kmp/<包>/<文件>.kt`

**源码优先于文档。** 0.9.4 里文档滞后于代码的地方不止一处（`PullToRefresh`、`InputField` 的签名）。
拿不准时拉源码 grep 函数签名，不要猜。

无法访问网络时，按工程内已引入版本与本地依赖源码核对，并**明确标注不确定项**，不要含糊带过。

---

## 13. 真实案例的定位

`references/real-world-case-study.md` 记录的是 `vcut_lower/app` 与 `HyperMusicCover` 在 2026-10/11 的做法，
用来补齐文档没有强调的工程细节。它们是**已验证的参考实现**，不是唯一正确写法；
新项目仍应按自身页面数量、平台和交互复杂度选择架构。

模板里的注释保留了「为什么这么做」的原话（例如「这段代码在成为函数之前被复制过六次」），
那些注释比代码本身更值钱 —— **抄模板时不要删注释**，否则下一个人会把坑重新踩一遍。
