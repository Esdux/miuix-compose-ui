# 依赖与版本

## 模块矩阵

依赖组为 `top.yukonga.miuix.kmp`，版本统一。

| 模块 | 用途 | 是否需单独添加 |
|---|---|---|
| `miuix-ui` | 核心组件（自动包含 `miuix-core`） | 必需 |
| `miuix-preference` | Preference 组件（SwitchPreference 等），依赖 `miuix-ui` | 按需 |
| `miuix-icons` | 扩展图标（`MiuixIcons.*`） | 按需 |
| `miuix-glass-icons` | Glass 图标（`MiuixIcons.Glass.*`） | 按需 |
| `miuix-nav` | 自包含导航运行时 | 按需 |
| `miuix-blur` | 背景模糊、混合、纹理 | 按需，Android 需 minSdk 33 |
| `miuix-squircle` | 连续曲率圆角（已由 `miuix-ui` 传递包含） | 直接使用时可显式添加 |
| `miuix-material` | 渲染原语与材质定义 | 按需 |
| `miuix-glass` | Glass 表面与组件 | 按需 |
| `miuix-window` | Desktop 窗口与标题栏样式（Desktop JVM） | 按需 |
| `androidx.compose.material:material-icons-extended` | 状态卡勾/感叹号等 Material 图标（非 miuix，但模板要用） | 模板必需 |

## Compose Multiplatform

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4")
            implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.4")
            implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.4")
            implementation("top.yukonga.miuix.kmp:miuix-nav:0.9.4")
        }
        desktopMain.dependencies {
            implementation("top.yukonga.miuix.kmp:miuix-window:0.9.4")
        }
    }
}
```

`mavenCentral()` 必须存在于 `settings.gradle.kts` 的 `dependencyResolutionManagement.repositories` 或根构建脚本中。

## 纯 Android Compose

| 通用坐标 | Android 变体 |
|---|---|
| `miuix-ui` | `miuix-ui-android` |
| `miuix-preference` | `miuix-preference-android` |
| `miuix-icons` | `miuix-icons-android` |
| `miuix-nav` | `miuix-nav-android` |
| `miuix-blur` | `miuix-blur-android` |
| `miuix-squircle` | `miuix-squircle-android` |

```kotlin
dependencies {
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
}
```

## 真实 Android 工程配置（vcut_lower/app）

2026-10 已验证组合：

```toml
# gradle/libs.versions.toml
[versions]
agp = "9.4.0"
kotlin = "2.4.10"
composeBom = "2026.08.00"
miuix = "0.9.4-rc01"

[libraries]
miuix-ui = { group = "top.yukonga.miuix.kmp", name = "miuix-ui-android", version.ref = "miuix" }
miuix-blur = { group = "top.yukonga.miuix.kmp", name = "miuix-blur-android", version.ref = "miuix" }
miuix-preference = { group = "top.yukonga.miuix.kmp", name = "miuix-preference-android", version.ref = "miuix" }
miuix-icons = { group = "top.yukonga.miuix.kmp", name = "miuix-icons-android", version.ref = "miuix" }
miuix-squircle = { group = "top.yukonga.miuix.kmp", name = "miuix-squircle-android", version.ref = "miuix" }
```

```kotlin
android {
    compileSdk = 37
    defaultConfig {
        minSdk = 35
        targetSdk = 37
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}
```

这是案例工程的验证组合，不要求新工程照抄版本；minSdk 35 是案例选择，Miuix 本身最低 24（blur 33）。

## 版本一致性

- 所有 `top.yukonga.miuix.kmp:*` 必须同版本。混用 `0.9.4-rc01` 与 `0.9.4` 可能触发 `NoSuchMethodError`。
- 升级时整体切换，并重新编译所有依赖 Miuix 的第三方库。
- 用 `./gradlew dependencyInsight --dependency miuix-ui` 查看实际解析版本。
- 案例工程锁定 `0.9.4-rc01`；正式 `0.9.4` 已发布，升级时按 0.9.4 的 Breaking Changes 逐项核对。

## 其他平台变体

按平台后缀选择：`-iosarm64`、`-iossimulatorarm64`、`-macosarm64`、`-desktop`、`-wasmjs`、`-js`。例如 `miuix-ui-desktop`。仅在非 Gradle KMP 工程中需要手工选变体。

## 版本查询

Maven Central 元数据是版本事实来源：

```bash
curl -s https://repo1.maven.org/maven2/top/yukonga/miuix/kmp/miuix-ui/maven-metadata.xml
```

查看 `<latest>`、`<release>` 与 `<versions>`。截至 2026-11，`latest`/`release` 为 `0.9.4`。

发布说明：`https://github.com/compose-miuix-ui/miuix/releases`。README 徽章展示的 Kotlin/CMP 版本可能领先于最新发布，构建基线以对应 Release 说明为准。

## 0.9.4 关键兼容事实

- Android 共用 `minSdk` 从 23 升到 24。
- `miuix-navigation3-ui` 被移除，替换为 `miuix-nav`；`NavKey`、`rememberNavBackStack`、`NavDisplay`、`NavController` 均为 Miuix 自己的 API，不是 `androidx.navigation3`。
- `RadioButtonPreference` 的文本颜色参数合并进 `colors`。
- `NavigationRail` 的可空 `state` 拆成两个重载。
- `NavigationBarItem` / `FloatingNavigationBarItem` 在 `enabled` 与 `badge` 之间新增 `colors`。
- `InputField` 在 `textStyle` 与 `leadingIcon` 之间新增 `color`；`Modifier.drawBackdrop` 在 `enabled` 前新增 `progressiveGradient`。
- `PullToRefresh` 的触发阈值与视觉进度语义变化，自定义指示器用 `RefreshState.ThresholdReached` 判断已就绪。
- 基于 0.9.3 或更早版本预编译的库应针对 0.9.4 重新编译。

## 依赖自检清单

- [ ] 所有 `top.yukonga.miuix.kmp` 依赖版本一致
- [ ] `mavenCentral()` 可用
- [ ] Android `minSdk >= 24`；使用 `miuix-blur` 时 `>= 33`
- [ ] Kotlin 与 Compose Multiplatform 版本满足该 Miuix 版本要求
- [ ] 没有残留 `miuix-navigation3-ui`
- [ ] 没有把 Material3 与 Miuix 的 `Scaffold`/主题混在同一页面