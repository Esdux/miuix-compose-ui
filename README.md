# miuix-compose-ui

用 [Miuix](https://github.com/compose-miuix-ui/miuix)（Xiaomi HyperOS 风格的 Compose Multiplatform UI 库）
从零搭建或改造 Android / CMP 应用界面的 **AI skill**。

内置一套经过 `zyl6932/vcut_lower`、`zyl6932/HyperMusicCover`、`Adkimsm/Cirno` 三个真实工程验证的
**四标签应用模板**：统一页面骨架、12dp 卡片间距、两态底栏、顶栏渐进模糊、状态卡、设置页排班、
主题二级页、候选列表与筛选列表、性能与缓存规则、功能排班规则。

给 AI 编码会话用：让它照着模板一键产出同款界面，而不是每次从零手写 Compose。

## 目录

```
├── SKILL.md                  # 主文件（899 行，13 节）：一键流程 + 硬性约束 + 间距字号表 + 各组件范式
├── CHANGELOG.md              # 状态卡同步清单（给已按旧版铺过模板的工程）
├── assets/                   # 28 个可直接复制的 .kt 模板 + 4 个资源文件
│   ├── AppShell.kt           #   四标签应用壳 + 两态底栏 + 弹簧翻页
│   ├── PageScaffold.kt       #   每页统一骨架（顶栏模糊 + insets + 列表）
│   ├── HomePage.kt           #   状态卡（照搬 HyperMusicCover）+ 数据卡 + 设备卡
│   ├── ListPage.kt           #   候选列表：分组条 + 搜索 + 分类弹层 + 三态 + 下拉刷新
│   ├── AppListPage.kt        #   应用列表：真实读取已安装应用（含包可见性坑）
│   ├── ThemeActivity.kt      #   主题二级页：六模式下拉 + 两态底栏 + 背景模糊
│   ├── effect/               #   背景动效（RuntimeShader，OS2/OS3 两种）
│   └── res/                  #   drawable + themes.xml + proguard-rules.pro
├── references/               # 12 份专题文档
│   ├── information-architecture.md   # 标签页数量/顺序/二级页判定/自查表
│   ├── performance-and-caching.md    # 重组/模糊开销/轮询/缓存分层/构建优化
│   ├── list-and-filter.md            # 双流模型/分组条/搜索/分类弹层/三态
│   ├── hmc-patterns.md               # HyperMusicCover 实战范式
│   ├── component-catalog.md          # Miuix 组件速查（签名与默认值）
│   └── ...
└── scripts/
    ├── scaffold_app.py       # 一键铺模板：读包名 → 铺 33 个文件 → 打印 manifest 段
    └── check_miuix_usage.py  # 静态检查：18 类常见错误 + 复用提醒
```

## 用法

### 作为 AI skill

把整个目录放进你的 skill 目录（DSH 是 `~/.dsh/skills/`），会话里说「用 Miuix 写界面」即会加载。

### 一键铺模板到现有工程

```bash
python scripts/scaffold_app.py --project path/to/app --dry-run   # 先看计划
python scripts/scaffold_app.py --project path/to/app             # 再落盘
python scripts/scaffold_app.py --project path/to/app --tabs home,discover,mine
```

脚本只铺文件、不改 build 文件；依赖缺失会在输出里列成 `[TODO]`。
2026-11-17 实测：在全新 namespace 的空工程上跑一次，`assembleDebug` 直接成功，无需手改任何一行。

### 静态检查

```bash
python scripts/check_miuix_usage.py --path path/to/app
```

要求输出 `0 error(s), 0 warning(s)`。检查项包括 Material3 混用、Overlay/`popupHost` 不匹配、
图标缺 import、回弹叠加、minSdk 与 blur 冲突、写死字号、卡片外手写非 12dp 栅格、
多层 `rememberLayerBackdrop`、标签页数量越界，以及「自绘了库里有的图标 / 重写了模板已有布局」的复用提醒。

## 依赖

```toml
[versions]
miuix = "0.9.4"

[libraries]
miuix-core       = { group = "top.yukonga.miuix.kmp", name = "miuix-core-android",       version.ref = "miuix" }
miuix-ui         = { group = "top.yukonga.miuix.kmp", name = "miuix-ui-android",         version.ref = "miuix" }
miuix-preference = { group = "top.yukonga.miuix.kmp", name = "miuix-preference-android", version.ref = "miuix" }
miuix-icons      = { group = "top.yukonga.miuix.kmp", name = "miuix-icons-android",      version.ref = "miuix" }
miuix-blur       = { group = "top.yukonga.miuix.kmp", name = "miuix-blur-android",       version.ref = "miuix" }  # 需 minSdk 33
miuix-squircle   = { group = "top.yukonga.miuix.kmp", name = "miuix-squircle-android",   version.ref = "miuix" }
```

另需 `androidx.compose.material:material-icons-extended`（状态卡的勾/感叹号图标）。
**debug 会因此涨到 64MB，但 release 开 R8 只多 0.56MB** —— 详见 `references/hmc-patterns.md` 第 9 节。

## 关键约束

| # | 规则 |
|---|---|
| 1 | 不混用 Material3 UI 组件（`material.icons` 的 `ImageVector` 可以） |
| 2 | `layerBackdrop` 的注册层里不能包含用该 backdrop 做模糊的组件（自采样 = native SIGSEGV） |
| 3 | 顶部 inset 放 `contentPadding`，不放列表 `modifier` |
| 4 | 间距只用 `DshSpacing` 五档（2/4/8/12/16），28dp 只在 `SmallTitle` 对齐场景 |
| 5 | 字号取自 `MiuixTheme.textStyles`，不写裸 `fontSize` |
| 6 | 优先复用 `assets/` 里的模板，不自绘库里有的图标、不重排已有布局 |

完整 16 条见 SKILL.md 第 1 节。

## 实测数据

| 项目 | 值 |
|---|---|
| Miuix 版本 | `0.9.4`（Maven Central，2026-09-20） |
| 模板自测 | 2026-11-17，空工程 `assembleDebug` 通过 |
| debug APK | 31.8 MB（自绘图标）/ 64.4 MB（material-icons-extended） |
| release APK（R8） | 5.95 MB / 6.51 MB |

## 许可

Apache-2.0。模板代码提炼自 `zyl6932/vcut_lower`（GPL-3.0）、`zyl6932/HyperMusicCover`（AGPL-3.0）
与 `Adkimsm/Cirno` 的界面结构，已重写为独立实现；引用处均在注释中注明来源。
Miuix 本身是 Apache-2.0。