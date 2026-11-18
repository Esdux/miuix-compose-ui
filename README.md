# miuix-compose-ui

给 AI 编码会话用的 Miuix（HyperOS 风格 Compose）界面 skill：内置 28 个真机验证过的模板 + 2 个脚本，
一键铺出四标签应用壳、状态卡、设置页、筛选列表、主题二级页，并按静态检查保证不跑偏。

## 用法

```bash
python scripts/scaffold_app.py --project path/to/app --dry-run   # 先看计划
python scripts/scaffold_app.py --project path/to/app             # 落盘
python scripts/check_miuix_usage.py --path path/to/app           # 要求 0 error 0 warning
```

## 结构

| 路径 | 内容 |
|---|---|
| `SKILL.md` | 主文件，13 节：一键流程、16 条硬性约束、间距字号表、各组件范式 |
| `assets/` | 28 个 `.kt` 模板 + 4 个资源文件 |
| `references/` | 12 份专题文档（信息架构、性能缓存、列表筛选、HMC 范式、组件速查） |
| `scripts/` | `scaffold_app.py` 铺模板、`check_miuix_usage.py` 静态检查 |
| `CHANGELOG.md` | 状态卡同步清单（给已按旧版铺过模板的工程） |

## 依赖

```kotlin
implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4")
implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.4")
implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.4")
implementation("top.yukonga.miuix.kmp:miuix-blur:0.9.4")   // minSdk 33
implementation("androidx.compose.material:material-icons-extended")
```

## 许可

Apache-2.0。模板提炼自 [vcut_lower](https://github.com/zyl6932/vcut_lower)、
[HyperMusicCover](https://github.com/zyl6932/HyperMusicCover) 与 Cirno 的界面结构，已重写为独立实现。