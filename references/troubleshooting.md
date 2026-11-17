# 故障排查

## 编译期

### `Unresolved reference: Settings` / `MiuixIcons.Xxx`

原因：`MiuixIcons` 的图标是扩展属性，必须单独 import。

修复：

```kotlin
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
```

并确认已添加 `miuix-icons` 依赖。Glass 图标需 `miuix-glass-icons`，import 为 `top.yukonga.miuix.kmp.icon.glass.Settings`（按实际图标名）。

### `Unresolved reference: MiuixTheme` / `Scaffold` 解析到 Material3

原因：包没导入，或工程同时有 Material3 与 Miuix 的同名组件。

修复：显式导入 Miuix 包：

```kotlin
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme
```

同一页面只使用一套 UI 组件。`androidx.compose.material3.*` 的 `Scaffold`、`Button`、`Text` 不会自动套用 Miuix 主题。

### `ClassNotFound` / `NoSuchMethodError` / `NoClassDefFoundError`

原因：多个 Miuix 构件版本不一致，或第三方库针对旧版 Miuix 预编译。0.9.4 修改了导航、Preference、Dialog、PullToRefresh、InputField 等公开签名。

修复：

1. 统一所有 `top.yukonga.miuix.kmp:*` 版本。
2. 对依赖 Miuix 的第三方库重新编译。
3. 用 `./gradlew dependencyInsight --dependency miuix-ui` 检查实际解析版本。

### `minSdk` 相关错误

- 共用构件要求 `minSdk >= 24`。
- `miuix-blur` 要求 `minSdk >= 33`。

修复：Android 模块 `defaultConfig.minSdk = 24`（或更高）；使用 blur 时使用 33，或在低版本走非模糊分支。

### `Cannot find a parameter with this name: xxx`

原因：使用了旧版参数名，或把 Compose Material 的参数名套到 Miuix 上。

高频差异：

| 组件 | 正确参数 |
|---|---|
| `TextButton` | `text = "..."`，不是内容 lambda |
| `InputField` | `query` / `onQueryChange`，不是 `value` / `onValueChange` |
| `TextField` | `value` / `onValueChange` |
| `Checkbox` | `state = ToggleableState.On/Off/Indeterminate`，不是 `checked` |
| `RadioButton` | `selected`，不是 `checked` |
| `Button` 主色 | `colors = ButtonDefaults.buttonColorsPrimary()` |
| `NumberPicker` | `value` + `range = 0..10` |

修复：以组件页参数表或源码为准；不要凭记忆改。

### `miuix-navigation3-ui` 依赖消失

原因：0.9.4 已移除该模块。

修复：改为 `miuix-nav`，并迁移 `NavKey`、`NavDisplay`、`rememberNavBackStack` 的 import 与返回栈代码。见 `references/navigation.md`。

## 运行/显示期

### OverlayDialog / OverlayListPopup / OverlayBottomSheet 不显示

原因通常是两者之一：

1. 组件不在 `Scaffold` 的作用域内。Overlay 系列依赖 `Scaffold` 的弹出层容器。
2. `Scaffold` 把 `popupHost` 置空了，例如 `Scaffold(popupHost = { })`。此时默认的 `MiuixPopupHost` 不存在，Overlay 弹层无处渲染。

修复：

- 把 Overlay 组件放进某个 `Scaffold` 的 content 中，并保留默认 `popupHost = { MiuixPopupHost() }`。
- 如果确实想把 `popupHost` 置空（常见于统一改用 Window 弹层），就把该页面的 `OverlayDialog` 等全部换成 `WindowDialog`、`WindowDropdownPreference`、`WindowListPopup`。
- 多个嵌套 `Scaffold` 各自管理弹层状态；确认弹层与它依赖的 `Scaffold` 是同一棵树上的关系。

`Window*` 系列（`WindowDialog`、`WindowListPopup`、`WindowDropdownMenu` 等）是窗口级实现，不要求 `Scaffold`，也不受 `popupHost` 影响。

### 弹层位置错误 / 被内容遮挡

- 检查是否同时在父子 `Scaffold` 中放置弹层，导致锚点错误。
- `OverlayListPopup` 的锚点由调用位置决定，放在触发按钮所在的 `Box` 内。
- 弹层内容需要滚动时用 `ListPopupColumn` 或显式滚动容器。

### 顶栏不随列表折叠 / 滚动冲突

原因：`scrollBehavior` 没有绑定，或重复绑定。

修复：

```kotlin
val behavior = MiuixScrollBehavior()
SmallTopAppBar(title = "标题", scrollBehavior = behavior)

LazyColumn(
    overscrollEffect = null,
    modifier = Modifier
        .scrollEndHaptic()
        .overScrollVertical(onOffsetChanged = { behavior.state.overscrollOffset = it })
        .nestedScroll(behavior.nestedScrollConnection),
)
```

`.overScrollVertical(...)` 应在 `.nestedScroll(...)` 之前。列表已绑定滚动行为时，不要把同一个 behavior 再传给外层 `PullToRefresh`。

### 内容不滚到透明顶栏下面 / 顶栏模糊采样为空

原因：把顶部 inset 加到了列表自身的 `modifier.padding(top = ...)`，列表被整体压到顶栏以下。

修复：把顶部 inset 放进 `contentPadding`，并让 `Scaffold` 只保留水平系统 insets：

```kotlin
Scaffold(
    contentWindowInsets = WindowInsets.systemBars
        .add(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal),
) { innerPadding ->
    LazyColumn(
        contentPadding = PaddingValues(top = innerPadding.calculateTopPadding(), bottom = ...)
    ) { ... }
}
```

这样内容可以滚到状态栏/顶栏后面，`layerBackdrop` 才有内容可采样。

### 模糊出现色块 / 模糊层完全没效果

- backdrop 捕获到透明像素：`rememberLayerBackdrop { drawRect(surfaceColor); drawContent() }`。
- 模糊表面背景不透明：设为 `Color.Transparent`，否则纯色盖住模糊。
- `backdrop == null` 仍调用 `textureBlur`：先判空，回退纯色。
- 低版本 Android：先 `isRuntimeShaderSupported()`，API 33 以下不要启用。

### 主题切换后页面状态丢失 / 反复重建

- `UiModeManager.setApplicationNightMode(...)` 会重建 Activity；只在模式值真正变化时调用。
- `ThemeController` 用 `remember(themeMode)`，避免每次重组都新建。
- 强制 Light/Dark 时状态栏图标不会自动同步，需按 `surface` 亮度设置 `isAppearanceLightStatusBars`。
- 主题页自身应在 `MiuixTheme` 内更新状态，否则用户看不到改动效果。

### 下拉刷新与越界回弹互相抢手势

- 使用显式 `overScrollVertical()` 时，给列表设 `overscrollEffect = null`。
- `MiuixTheme` 默认已提供越界回弹工厂；通常不需要再手动加修饰符。
- 自定义指示器用 `refreshState == RefreshState.ThresholdReached` 判断是否已触发，不要用 `pullProgress == 1f`。

### 深色模式不生效 / 颜色不对

- 检查是否用 `MiuixTheme` 包裹；Material3 的 `MaterialTheme` 不控制 Miuix 组件。
- `ThemeController(ColorSchemeMode.System)` 才会跟随系统；`Light`/`Dark` 会强制。
- 直接传 `colors` 时 `MiuixTheme.colorSchemeMode` 为 `null`，不要依赖它做分支。
- 不要硬编码浅色值；从 `MiuixTheme.colorScheme` 读取。

### 模糊效果在 Android 崩溃或无效

- `miuix-blur` 在 Android 需要 API 33（`RuntimeShader`）。
- 运行前用 `isRuntimeShaderSupported()` 判断；仅背景采样可用 `isRenderEffectSupported()`（API 32+）。
- `layerBackdrop` 不捕获父级背景；透明区域会产生色块，需在 `onDraw` 中先画不透明背景。

### 平滑圆角在低版本异常

`miuix-squircle` 的 shader modifier 在 Android 低于 33 时自动回退为圆弧圆角，不会崩溃。若视觉不一致，检查是否混用了 `RoundedCornerShape` 与 squircle。

## 工程/环境

### 依赖下载失败

- 确认 `mavenCentral()` 在仓库列表中。
- 确认使用的是 `top.yukonga.miuix.kmp` 坐标，而不是 `miuix` 或旧组织名。
- 内网环境检查镜像是否同步了该 group。

### 示例无法在 Desktop/iOS 启动

- 确认对应平台变体存在（`-desktop`、`-iosarm64` 等）。
- `miuix-window` 只适用于 Desktop JVM。
- iOS/macOS 需要在对应平台构建，不要在 JVM 目标中引用 Desktop 专属 API。

## 核对 API 的正确顺序

1. 先看当前版本组件文档页（中文）：`https://compose-miuix-ui.github.io/miuix/zh_CN/components/...`
2. 再看 Dokka API：`https://compose-miuix-ui.github.io/miuix/dokka/index.html`
3. 仍不确定时读源码：`https://raw.githubusercontent.com/compose-miuix-ui/miuix/main/<module>/src/commonMain/kotlin/top/yukonga/miuix/kmp/<pkg>/<File>.kt`
4. 最后查看 Release 说明的 Breaking Changes。

不要用旧版博客、旧 issue 片段或记忆中的签名直接产出代码。