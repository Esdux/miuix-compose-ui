# 组件速查

包路径约定（0.9.4 / 0.9.4-rc01）：

| 包 | 内容 |
|---|---|
| `top.yukonga.miuix.kmp.basic` | 基础组件、`Scaffold`、部分 Overlay 组件、`DropdownEntry`/`DropdownItem`/`NavigationItem` |
| `top.yukonga.miuix.kmp.overlay` | `OverlayDialog`、`OverlayListPopup`、`OverlayCascadingListPopup`、`OverlayBottomSheet` |
| `top.yukonga.miuix.kmp.preference` | 所有 Preference 与 `Overlay*Preference` / `Window*Preference` |
| `top.yukonga.miuix.kmp.menu` | `Overlay*DropdownMenu` 系列 |
| `top.yukonga.miuix.kmp.window` | `WindowDialog`、`WindowListPopup`、`WindowDropdown*` 等 |
| `top.yukonga.miuix.kmp.theme` | `MiuixTheme`、`ThemeController`、`lightColorScheme`、`LocalDismissState` |
| `top.yukonga.miuix.kmp.icon` | `MiuixIcons` 与图标扩展属性 |
| `top.yukonga.miuix.kmp.utils` | `overScrollVertical`、`scrollEndHaptic`、`PressFeedbackType`、`platform` 等 |

> 标注「案例」的参数来自 `vcut_lower/app`（Miuix 0.9.4-rc01）的实际调用；文档页未列全时以源码为准。版本基线：Miuix 0.9.4（Maven Central 2026-09-20），vcut_lower 用 0.9.4-rc01，minSdk 35 / compileSdk 37 / Kotlin 2.4.10 / AGP 9.4.0 / Compose BOM 2026.08.00。

## 脚手架与容器

| 组件 | import | 要点 |
|---|---|---|
| `Scaffold` | `basic.Scaffold` | `topBar`、`bottomBar`、`snackbarHost`、`floatingActionButton`、`floatingToolbar`、`floatingToolbarPosition`、`popupHost`、`contentWindowInsets`、`content` |
| `Surface` | `basic.Surface` | `color`、`shape`、`shadowElevation`；有 `onClick` 重载 |
| `Card` | `basic.Card` | `cornerRadius`、`insideMargin`、`colors`；案例还用 `onClick`、`showIndication`、`pressFeedbackType = PressFeedbackType.Tilt` |
| `BasicComponent` | `basic.BasicComponent` | 通用设置行：`title`、`summary`、`enabled`、`onClick`、`endActions`（案例用于右对齐数值） |
| `HorizontalDivider` / `VerticalDivider` | `basic.HorizontalDivider` / `basic.VerticalDivider` | Miuix 没有名为 `Divider` 的组件 |
| `SmallTitle` | `basic.SmallTitle` | `text`、`textColor`；默认 `InsideMargin = PaddingValues(28.dp, 8.dp)`，文本样式 `textStyles.subtitle` |

### Scaffold 的两个关键参数

```kotlin
Scaffold(
    popupHost = { MiuixPopupHost() },                       // 默认值
    contentWindowInsets = WindowInsets.systemBars           // 默认值
        .union(WindowInsets.displayCutout),
) { padding -> /* content */ }
```

- `popupHost` 默认就是 `{ MiuixPopupHost() }`，提供 Overlay 弹层容器。`OverlayDialog`、`OverlayListPopup`、`OverlayBottomSheet`、`Overlay*Preference`、`Overlay*DropdownMenu` 都依赖它。
- `contentWindowInsets` 默认 `WindowInsets.systemBars.union(WindowInsets.displayCutout)`：系统栏与刘海都算进来，四边都扣。传 `WindowInsets(0)` 会连状态栏一起让内容顶到最上沿，只有自绘顶栏时才这么干。
- 如果该页面只用 `WindowDialog` / `WindowDropdownPreference`，可以 `popupHost = { }` 关闭默认容器，减少一层组合；但**关闭后就不要再使用 Overlay 组件**。
- `contentWindowInsets` 传给 content 的 PaddingValues。案例把它限制为水平方向：

```kotlin
contentWindowInsets = WindowInsets.systemBars
    .add(WindowInsets.displayCutout)
    .only(WindowInsetsSides.Horizontal)
```

这样内容可以滚到状态栏/导航栏下面，顶部与底部由页面自己通过 `contentPadding` 控制。

### Card 完整参数

```kotlin
@Composable
fun Card(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CardDefaults.CornerRadius,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    colors: CardColors = CardDefaults.defaultColors(),
    border: BorderStroke? = null,
    pressFeedbackType: PressFeedbackType = PressFeedbackType.Sink,
    showIndication: Boolean = true,
    holdDownState: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
)
```

| 参数 | 默认值 | 何时改 |
|---|---|---|
| `cornerRadius` | `CardDefaults.CornerRadius` | 需要玻璃/胶囊外观时按容器高度调；常规设置卡不动 |
| `insideMargin` | `CardDefaults.InsideMargin` | 卡片内首尾元素要贴边（如整宽分隔线）时改为 `PaddingValues(0.dp)`，再在内容里自己补 |
| `colors` | `CardDefaults.defaultColors()` | 只传 `color =` 覆盖底色：`CardDefaults.defaultColors(color = statusColor)`；状态语义靠底色承载，不要另加色块 |
| `border` | `null` | 需要描边区分层级时给 `BorderStroke(1.dp, color)` |
| `pressFeedbackType` | `PressFeedbackType.Sink` | 见下方选择规则 |
| `showIndication` | `true` | 只有纯展示、不响应点击的卡才设 `false`；设了 `onClick` 却关掉指示，用户点下去没有任何反馈 |
| `holdDownState` | `false` | 需要在按下期间由外部读取「是否按住」时设 `true`（按下态保持到抬手，而不是立即回弹）；只为了视觉反馈不要开 |
| `onClick` | `null` | 整卡可点时给；为 null 时卡片不可点，子项各自响应 |
| `onLongPress` | `null` | 整卡长按出菜单时给；给了它 `pressFeedbackType` 仍按普通点击处理 |

**PressFeedbackType 何时用 Tilt**：

- `PressFeedbackType.Sink`（默认）：按下整体下沉 + 轻微缩放。适合常规设置卡、列表卡——面积小、下沉不会露出背景。
- `PressFeedbackType.Tilt`：按下倾斜。适合大面积的整卡点击，例如主页状态卡：卡片几乎占满宽度，Sink 会让边缘缩进去、露出下面一截背景，视觉上像卡片「掉下去」；Tilt 只改变倾斜角度，轮廓稳定。
- 只展示不可点的卡不要设 `pressFeedbackType`，它配合 `onClick` 才有意义。

```kotlin
Card(
    modifier = Modifier.firstCard().fillMaxWidth(),
    colors = CardDefaults.defaultColors(color = statusColor),
    onClick = onRefresh,
    showIndication = true,
    pressFeedbackType = PressFeedbackType.Tilt,
) {
    // 内容
}
```

### SmallTitle

```kotlin
@Composable
fun SmallTitle(
    text: String,
    modifier: Modifier = Modifier,
    insideMargin: PaddingValues = PaddingValues(28.dp, 8.dp),
    textColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    textStyles: TextStyle = MiuixTheme.textStyles.subtitle,
)
```

- 默认 `InsideMargin = PaddingValues(28.dp, 8.dp)`：左右 28dp 与卡片内文字对齐，上下 8dp 就是它与上方卡片、下方卡片之间的全部间距——所以分组标题前后不要再补 `Spacer`。
- 文本样式固定 `textStyles.subtitle`，不要改成 `title3`：分组标题比设置项标题弱一级，同级别会让人误以为它是可点的卡头。
- 一个 `SmallTitle` 只带一组 `Card`；两个分组之间是「标题 → 卡 → 标题 → 卡」，不是「标题 → 卡 → 卡」。

### BasicComponent 与 Card 的分工

| 场景 | 用什么 |
|---|---|
| 一个设置项（标题 + 说明 + 尾部控件） | `ArrowPreference` / `SwitchPreference` 等 `*Preference`，外面套 `Card` |
| 一组设置项的容器（含分隔线） | `Card(Modifier.nextCard()) { ... }` |
| 卡片里需要一行「标题 + 右侧数值」但没有任何 Preference 语义 | `BasicComponent`，尾部用 `endActions` |
| 卡片里放非列表内容（图表、说明段落、按钮组） | `Card` + 自己排 `Column` |
| 单个可点的整块（状态卡、功能入口） | `Card(onClick = ...)`，内部再放 `ArrowPreference` 或自定义内容 |

- `BasicComponent` 是「行」，`Card` 是「容器」：不要用 `Card` 去做一行，也不要把 `BasicComponent` 当卡片外壳。
- `ArrowPreference` 自带右箭头与点击语义，比 `BasicComponent` 更明确；只有当确实需要右对齐一段数值、且不需要箭头与点击时，才用 `BasicComponent(endActions = { ... })`。
- 两者都在 `Card` 内部承担「一行」，同一张卡里不要混排两种行样式，否则行高和左右边距会对不上。

## 顶部与导航

| 组件 | import | 要点 |
|---|---|---|
| `SmallTopAppBar` | `basic.SmallTopAppBar` | `title`、`navigationIcon`、`actions`、`scrollBehavior`；案例还用 `color`、`titleColor`、`defaultWindowInsetsPadding` |
| `TopAppBar` | `basic.TopAppBar` | `title`、`largeTitle`、`navigationIcon`、`actions`、`scrollBehavior`、`color` |
| `BlurTopAppBar` | `basic.BlurTopAppBar` | 需要模糊能力；`largeTitleBlurRadius` 为必填 |
| `MiuixScrollBehavior` / `rememberTopAppBarState` | `basic.*` | `MiuixScrollBehavior()` 可直接构造；与 `Modifier.nestedScroll(...)` 配合 |
| `NavigationBar` | `basic.NavigationBar` | 底部导航容器；案例用 `color = barColor` 控制透明/纯色 |
| `NavigationBarItem` | `basic.NavigationBarItem` | `selected`、`onClick`、`icon`、`label`、`enabled`、`badge`、`colors` |
| `FloatingNavigationBar` / `FloatingNavigationBarItem` | `basic.*` | 悬浮底部导航；`color` 可设 `Color.Transparent` 配合模糊 |
| `NavigationItem` | `basic.NavigationItem` | `NavigationItem(label, icon)` 数据类，供自建导航栏渲染 |
| `NavigationRail` | `basic.NavigationRail` | 固定布局：`NavigationRail {}` 或 `NavigationRail(expanded = true) {}`；可展开：`NavigationRail(state = rememberNavigationRailState())` |
| `NavigationRailItem` | `basic.NavigationRailItem` | `selected`、`onClick`、`icon`、`label` |
| `TabRow` / `TabRowWithContour` | `basic.TabRow` / `basic.TabRowWithContour` | `tabs`、`selectedTabIndex`、`onTabSelected`、`modifier` |
| `BreadcrumbBar` | `basic.BreadcrumbBar` | `items: List<BreadcrumbItem>`、`onItemClick`、`highlightIndex`；配合 `joinToPath()` |

### TopAppBarDefaults 常量

| 常量 | 值 | 含义 |
|---|---|---|
| `TitlePadding` | `26.dp` | 标题相对导航图标一侧的水平缩进 |
| `NavigationIconPadding` | `16.dp` | 导航图标与屏幕左缘的距离 |
| `ActionIconPadding` | `16.dp` | 动作图标与屏幕右缘的距离 |
| `CollapsedHeight` | `52.dp` | 大标题栏折叠后的高度 |
| `SmallTopAppBarCenterHeight` | `50.dp` | `SmallTopAppBar` 内容区居中时的可用高度 |

```kotlin
TopAppBarDefaults.TitlePadding            // 26.dp
TopAppBarDefaults.NavigationIconPadding   // 16.dp
TopAppBarDefaults.ActionIconPadding       // 16.dp
TopAppBarDefaults.CollapsedHeight         // 52.dp
TopAppBarDefaults.SmallTopAppBarCenterHeight // 50.dp
```

- 自绘顶栏要与官方栏对齐时直接引这五个常量，不要写 `26.dp`/`16.dp` 字面量：它们随后续版本调整的可能性大于零，写死就会出现「标题比别人的多偏 2dp」。
- `CollapsedHeight` 是 `TopAppBar`（大标题）折叠后的高度，不是 `SmallTopAppBar` 的高度；后者由 `SmallTopAppBarCenterHeight` 决定内容居中区。
- 顶栏透明 + 模糊时改的是 `color`，不动这五个 padding；padding 变了文字位置会整体漂移。

```kotlin
TopAppBar(
    title = title,
    color = barColor,
    scrollBehavior = scrollBehavior,
    modifier = Modifier.heightIn(min = TopAppBarDefaults.CollapsedHeight),
)
```

## 基础交互

| 组件 | import | 关键签名/要点 |
|---|---|---|
| `Button` | `basic.Button` | `onClick` + `RowScope` 内容；`colors = ButtonDefaults.buttonColorsPrimary()` |
| `TextButton` | `basic.TextButton` | `text: String`、`onClick`、`modifier`；主色用 `ButtonDefaults.textButtonColorsPrimary()` |
| `IconButton` | `basic.IconButton` | `onClick`、`enabled` + 内容 |
| `Text` | `basic.Text` | `text`、`style = MiuixTheme.textStyles.*`；案例直接传 `fontSize`、`fontWeight`、`color` |
| `Icon` | `basic.Icon` | `imageVector`、`contentDescription`、`tint` |
| `TextField` | `basic.TextField` | `value`、`onValueChange`、`label` |
| `InputField` | `basic.InputField` | `query`、`onQueryChange`、`onSearch`、`expanded`、`onExpandedChange`、`leadingIcon`、`color` |
| `Switch` | `basic.Switch` | `checked`、`onCheckedChange` |
| `Checkbox` | `basic.Checkbox` | `state: ToggleableState`、`onClick` |
| `RadioButton` | `basic.RadioButton` | `selected`、`onClick` |
| `Slider` / `VerticalSlider` / `RangeSlider` | `basic.*` | `value`、`onValueChange`、`valueRange`、`enabled`、`onValueChangeFinished`；案例还用 `hapticEffect`、`keyPoints`、`magnetThreshold` |
| `SliderDefaults` | `basic.SliderDefaults` | `SliderHapticEffect.Step`、`DefaultHapticEffect` |
| `NumberPicker` | `basic.NumberPicker` | `value`、`onValueChange`、`range: IntRange` |
| `LinearProgressIndicator` / `CircularProgressIndicator` / `InfiniteProgressIndicator` | `basic.*` | 线性：`progress`；`InfiniteProgressIndicator()` 用于无法计算进度的等待 |
| `BadgedBox` / `Badge` / `BadgeDefaults` | `basic.*` | `BadgedBox(badge = { Badge() }) { ... }` |
| `FloatingActionButton` | `basic.FloatingActionButton` | `onClick`、`containerColor` |
| `FloatingToolbar` / `FloatingToolbarDefaults` / `ToolbarPosition` | `basic.*` | 放 `Scaffold(floatingToolbar = { ... })`；`FloatingToolbarDefaults.CornerRadius` 可用于模糊 shape |

## 搜索、刷新与滚动

| 组件 | import | 要点 |
|---|---|---|
| `SearchBar` | `basic.SearchBar` | `inputField` 槽里放 `InputField`；`expanded` + `onExpandedChange`；内容槽是搜索结果 |
| `PullToRefresh` | `basic.PullToRefresh` | `isRefreshing`、`onRefresh`、`pullToRefreshState = rememberPullToRefreshState()`；通常包 `LazyColumn` |
| `overScrollVertical` / `overScrollHorizontal` | `utils.overScrollVertical` / `utils.overScrollHorizontal` | Miuix 阻尼回弹；使用时给列表 `overscrollEffect = null` |
| `scrollEndHaptic` | `utils.scrollEndHaptic` | 滚动到边界时的触感反馈 |
| `MiuixOverscrollFactory` | 主题内置 | `MiuixTheme` 默认提供越界回弹，无需额外 Modifier |

## 偏好设置（需 `miuix-preference`）

| 组件 | import | 关键参数 |
|---|---|---|
| `ArrowPreference` | `preference.ArrowPreference` | `title`、`summary`、`enabled`、`onClick` |
| `SwitchPreference` | `preference.SwitchPreference` | `title`、`summary`、`checked`、`enabled`、`onCheckedChange` |
| `CheckboxPreference` | `preference.CheckboxPreference` | `title`、`summary`、`checked`、`enabled`、`onCheckedChange`；位置枚举 `CheckboxLocation` |
| `RadioButtonPreference` | `preference.RadioButtonPreference` | `title`、`summary`、`selected`、`onClick`、`colors`（0.9.4 起文本色在 `colors`） |
| `SliderPreference` | `preference.SliderPreference` | `title`、`summary`、`value`、`onValueChange` |
| `RangeSliderPreference` | `preference.RangeSliderPreference` | `title`、`summary`、`value: ClosedFloatingPointRange<Float>`、`onValueChange` |
| `OverlayDropdownPreference` | `preference.OverlayDropdownPreference` | 需 Scaffold + popupHost；`title`、`items: List<String>`、`selectedIndex`、`onSelectedIndexChange` |
| `OverlaySpinnerPreference` | `preference.OverlaySpinnerPreference` | 需 Scaffold + popupHost；`items: List<DropdownItem>`、`selectedIndex`、`onSelectedIndexChange` |
| `WindowDropdownPreference` | `preference.WindowDropdownPreference` | 不依赖 Scaffold；`title`、`summary`、`items: List<String>`、`selectedIndex`、`enabled`、`onSelectedIndexChange`、`onExpandedChange`（案例） |
| `WindowSpinnerPreference` | `preference.WindowSpinnerPreference` | 不依赖 Scaffold；`items: List<DropdownItem>`、`selectedIndex`、`onSelectedIndexChange` |

### ArrowPreference 完整参数

```kotlin
@Composable
fun ArrowPreference(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    holdDownState: Boolean = false,
    endActions: (@Composable RowScope.() -> Unit)? = null,
    insideMargin: PaddingValues = ArrowPreferenceDefaults.InsideMargin,
    colors: PreferenceColors = ArrowPreferenceDefaults.defaultColors(),
)
```

| 参数 | 默认值 | 说明 |
|---|---|---|
| `title` | 必填 | 主标题，单行截断 |
| `summary` | `null` | 副标题；为 null 时行高变矮，同一张卡里要么全有要么全无 |
| `enabled` | `true` | `false` 时整行置灰且不响应点击；用于「依赖上一项开关」的项 |
| `onClick` | `null` | 为 null 时不显示右箭头，退化为纯展示行 |
| `holdDownState` | `false` | 按下态保持到抬手；整行点击进新页面时可开，视觉更「实」 |
| `endActions` | `null` | 箭头左侧的尾部槽，可放 `Text`（当前值）或 `Switch` |
| `insideMargin` | `ArrowPreferenceDefaults.InsideMargin` | 行内边距；在 `Card` 里保持默认，不要与 `Card.insideMargin` 叠加调整 |
| `colors` | `ArrowPreferenceDefaults.defaultColors()` | 0.9.4 起文本色从这里传，不再有独立的 `titleColor` |

```kotlin
ArrowPreference(
    title = "主题",
    summary = settings.themeMode,
    onClick = { openThemeActivity() },
)
```

### SwitchPreference 完整参数

```kotlin
@Composable
fun SwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    holdDownState: Boolean = false,
    endActions: (@Composable RowScope.() -> Unit)? = null,
    insideMargin: PaddingValues = SwitchPreferenceDefaults.InsideMargin,
    colors: SwitchColors = SwitchPreferenceDefaults.defaultColors(),
)
```

| 参数 | 默认值 | 说明 |
|---|---|---|
| `title` | 必填 | 主标题 |
| `checked` | 必填 | 受控状态，必须来自 `remember`/设置快照，不能行内临时变量 |
| `onCheckedChange` | 必填 | 为 null 时开关不可交互（只读展示） |
| `summary` | `null` | 副标题；建议写「打开后会发生什么」，不要重复标题 |
| `enabled` | `true` | 依赖项未开启时置灰：如「保持存活」依赖「启用配置」 |
| `holdDownState` | `false` | 一般不开；开关自身已有移动反馈 |
| `endActions` | `null` | 放在开关左侧的附加内容 |
| `insideMargin` | `SwitchPreferenceDefaults.InsideMargin` | 与 `ArrowPreference` 默认值一致，混排才对齐 |
| `colors` | `SwitchPreferenceDefaults.defaultColors()` | 需要强调色时传 |

```kotlin
SwitchPreference(
    title = "保持存活",
    summary = "代价是内存占用，只在必要时开",
    checked = keepAlive,
    enabled = enabled,               // 依赖上一条「启用配置」
    onCheckedChange = { keepAlive = it },
)
```

- 一组设置项之间用 `HorizontalDivider()` 分隔，最后一项后面不加分隔线。
- 依赖关系用 `enabled` 表达，不要隐藏开关：隐藏会让用户找不到上次打开过的项。

## 弹层与菜单

| 组件 | import | 要点 |
|---|---|---|
| `OverlayDialog` | `overlay.OverlayDialog` | 需 Scaffold + popupHost；`show`、`title`、`summary`、`onDismissRequest`、`backgroundColor`、`outsideMargin`、`insideMargin`；0.9.4 增加 `maxWidth`、`largeScreen`、`cornerRadius` |
| `OverlayBottomSheet` | `overlay.OverlayBottomSheet` | 需 Scaffold + popupHost；`show`、`title`、`onDismissRequest`、`backgroundColor`、`dragHandleColor` |
| `OverlayListPopup` | `overlay.OverlayListPopup` | 需 Scaffold + popupHost；`show`、`alignment = PopupPositionProvider.Align.Start`；内容用 `ListPopupColumn` + `DropdownImpl` |
| `OverlayCascadingListPopup` | `overlay.OverlayCascadingListPopup` | 需 Scaffold + popupHost；`entries: List<DropdownEntry>`，二级项用 `DropdownItem.children` |
| `OverlayDropdownMenu` / `OverlayIconDropdownMenu` / `OverlayIconCascadingDropdownMenu` | `menu.*` | 需 Scaffold + popupHost；`entry`/`entries`、`title`、`collapseOnSelection` |
| `WindowDialog` | `window.WindowDialog` | 不依赖 Scaffold；`show`、`title`、`summary`、`onDismissRequest`；内容用 `LocalDismissState.current?.invoke()` 关闭 |
| `WindowListPopup` / `WindowDropdownMenu` / `WindowIconDropdownMenu` 等 | `window.*` / `menu.*` | 窗口级弹层，不依赖 Scaffold |
| `SnackbarHost` / `SnackbarHostState` / `Snackbar` / `SnackbarDuration` / `SnackbarResult` | `basic.*` | 放 `Scaffold(snackbarHost = { SnackbarHost(state) })`；`snackbarHostState.showSnackbar(...)` 是 suspend |

### WindowDialog 的关闭方式

```kotlin
var show by remember { mutableStateOf(false) }

WindowDialog(
    show = show,
    title = "确认",
    summary = "这一步不可撤销。",
    onDismissRequest = { show = false },
) {
    val dismiss = LocalDismissState.current
    Row {
        TextButton(text = "取消", onClick = { dismiss?.invoke() }, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(20.dp))
        TextButton(text = "确定", onClick = { show = false }, modifier = Modifier.weight(1f))
    }
}
```

- `dismiss?.invoke()` 会走弹层退出动画；直接 `show = false` 也可以，但动画可能被跳过。
- `onDismissRequest = { }` 可禁止返回键/点外部关闭，适合「正在执行，不可中断」的弹层。
- `WindowDialog` 在独立 Activity 中同样可用，不要求外层有 `Scaffold`。
- 危险操作把「待确认的动作」存成 `var confirm by remember { mutableStateOf<String?>(null) }`，多个入口共用一个弹层；布尔量分不清是哪一个动作。

## DropdownEntry / DropdownItem

`basic.DropdownEntry(items = listOf(DropdownItem(...)))`。

`DropdownItem(text, selected, onClick, summary, icon, children, enabled)`。`children` 用于二级级联菜单。

## 新增或改版组件的核对

组件页 URL 形如 `https://compose-miuix-ui.github.io/miuix/zh_CN/components/basic/<name>`。若页面 404，尝试 `components/extended/<name>` 或 `components/<name>`。源码路径：

```
miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/<Component>.kt
miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/overlay/<Component>.kt
miuix-preference/src/commonMain/kotlin/top/yukonga/miuix/kmp/preference/<Component>.kt
```

参数表与真实工程冲突时，以当前版本源码为准；本文件的案例参数只用于提示存在性，不作为类型定义。
