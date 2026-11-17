# 真实工程案例：vcut_lower/app 与 HyperMusicCover

两个真实工程：`vcut_lower` 的 `app/` 子目录（`https://github.com/zyl6932/vcut_lower`，Apache-2.0），和 HyperMusicCover（HyperOS 4 音乐锁屏模块）。前者是 KernelSU 模块管理界面，后者是锁屏音乐模块的配置与功能界面。业务毫无交集，界面骨架却逐字同构——这本身就是最有力的复用证据。下文只记 UI 工程做法，不涉及两个模块的内核、音频、电池业务。

Miuix 版本：vcut_lower 用 `0.9.4-rc01`，HyperMusicCover 用 `0.9.4`（Maven Central，2026-09-20）。本文涉及的 UI API 两版之间没有破坏性变化，代码两边都能编译。

## 1. 两个工程的位置与共同点

| 项 | vcut_lower | HyperMusicCover |
| 定位 | KernelSU 模块管理界面 | HyperOS 4 音乐锁屏模块 |
| 业务 | 内核参数、电池、充电策略 | 锁屏音乐卡片、歌词、封面 |
| 一级导航 | 四标签 Pager | 四标签 Pager |
| 二级页面 | `BatteryActivity` / `ChargeActivity` / `ThemeActivity` | 主题页（独立 `Activity`） |
| 骨架三件套 | `ui/util/PageScaffold.kt`、`ui/util/CardSpacing.kt`、`ui/theme/Theme.kt` | 同名同构，逐字相同 |

三个文件逐字相同，差异只有两处：包名（`package` 声明与 `R` 的导入路径），以及可见文字——骨架里每个字符串都走 `stringResource(R.string.xxx)`，换项目只换 `strings.xml`，Kotlin 文件一个字不动。

所以「骨架可跨项目搬」不是推测：PageScaffold / CardSpacing / Theme 约束的是结构（inset 放哪、间距怎么排、状态栏图标怎么跟），不是业务。两个业务完全无关的模块共享同一份骨架，说明它没被某个 App 的具体需求污染过。搬用时按三个文件的边界切：`ui/util/` 放与业务无关的布局辅助，`ui/theme/` 放主题与系统栏同步，页面文件只写 `LazyListScope` 内容。

## 2. 工程配置对照表

`gradle/libs.versions.toml`（两工程同结构，只列关键行）：

```toml
[versions]
agp = "9.4.0"
kotlin = "2.4.10"
composeBom = "2026.08.00"
miuix = "0.9.4-rc01"   # HyperMusicCover: "0.9.4"

[libraries]
miuix-ui = { group = "top.yukonga.miuix.kmp", name = "miuix-ui-android", version.ref = "miuix" }
miuix-blur = { group = "top.yukonga.miuix.kmp", name = "miuix-blur-android", version.ref = "miuix" }
miuix-preference = { group = "top.yukonga.miuix.kmp", name = "miuix-preference-android", version.ref = "miuix" }
miuix-icons = { group = "top.yukonga.miuix.kmp", name = "miuix-icons-android", version.ref = "miuix" }
```

| 字段 | vcut_lower/app | HyperMusicCover |
| namespace | 工程自有（随模块） | 工程自有（随模块） |
| compileSdk / minSdk / targetSdk | 37 / 35 / 37 | 37 / 35 / 37 |
| versionCode | 随模块发版 | 随模块发版 |
| Kotlin | 2.4.10 | 2.4.10 |
| AGP | 9.4.0 | 9.4.0 |
| Compose BOM | 2026.08.00 | 2026.08.00 |
| miuix | 0.9.4-rc01 | 0.9.4 |

右列除 miuix 版本外与左列同值，未逐项重新采集。`app/app/build.gradle.kts` 关键值：`compileSdk = 37`、`minSdk = 35`、`targetSdk = 37`、Java 17、`buildFeatures { compose = true; buildConfig = true }`。另引 `androidx.compose.material:material-icons-extended`，只用它的 `ImageVector`，没有引入 Material3 UI 组件。

## 3. 统一页面骨架

两个工程的每个二级页面都调同一个函数。合并两版参数后的完整签名：

```kotlin
@Composable
fun PageScaffold(
    title: String, isBlurEnabled: Boolean, extraBottomPadding: Dp = 0.dp,
    onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
)
```

内部结构：

```kotlin
val scrollBehavior = MiuixScrollBehavior()
val backdrop = rememberBlurBackdrop()
val blurActive = isBlurEnabled && backdrop != null
val surfaceColor = MiuixTheme.colorScheme.surface
Scaffold(
    popupHost = { }, floatingActionButton = floatingActionButton,
    contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal),
    topBar = {
        BlurredBar(backdrop, blurActive, scrollBehavior) {
            TopAppBar(
                title = title, scrollBehavior = scrollBehavior,
                color = if (blurActive) Color.Transparent else surfaceColor,
                navigationIcon = { if (onBack != null) PageBackButton(onBack) },
                actions = { actions() },
            )
        }
    },
) { innerPadding ->
    Box(if (blurActive) Modifier.layerBackdrop(backdrop!!) else Modifier) {
        LazyColumn(
            overscrollEffect = null, content = content,
            modifier = Modifier.fillMaxSize().scrollEndHaptic()
                .overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + extraBottomPadding,
            ),
        )
    }
}
```

骨架固定五件事：整屏只放一个 `layerBackdrop`；顶栏透明/模糊与内容滚动绑同一个 `scrollBehavior`；`contentWindowInsets` 只留水平方向，内容能滚到状态栏下面；列表 `overscrollEffect = null`，回弹交给 `overScrollVertical()` 独占；顶部 inset 放 `contentPadding`，不放列表 modifier。

backdrop 写法固定为 `rememberLayerBackdrop { drawRect(surfaceColor); drawContent() }`：先铺底色再画内容。整屏只允许一层——嵌套第二个会让上层采样到下层已模糊过的像素，滚动时出现玻璃套玻璃的重影；先 `drawRect` 是让玻璃背后的底色由自己补，否则模糊采样到透明区会发灰。`rememberBlurBackdrop()` 能力不足时返回 null，调用方一律走不透明分支，判定写在函数首行：

```kotlin
if (!isRuntimeShaderSupported() || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
```

顶栏模糊参数集中一处，不散落到页面：

```kotlin
object TopBarBlurConfig {
    const val BlurRadius = 15f; const val GradientCurve = 10f
    const val SurfaceAlpha = 0.3f; val ScrollFadeDistance = 0.dp
}
```

`ScrollFadeDistance = 0.dp` 表示顶栏不随滚动渐隐，透明度只由模糊层与 `SurfaceAlpha` 决定；改观感只调这一个值，不要在页面里单独加 `background`。

教训：`PageScaffold.kt` 文件头原注释是「这段代码在成为函数之前被复制过六次」。六份副本的差异全在 inset 上——有的把顶部 inset 加在列表 modifier 上，列表被压到顶栏以下，内容滚不到顶栏后面，backdrop 在那儿没有内容可采样，顶栏模糊与别的页面不一致；有的漏了 `overscrollEffect = null`，Miuix 回弹与 Compose 默认回弹叠加，列表边缘抖一下。抽成函数后这类差异一次性消失。

宿主负责状态，页面不重复起轮询：`POLL_MS = 3000L`，`repeatOnLifecycle(Lifecycle.State.RESUMED)` 起协程，先读一次再 `delay`（否则冷启动要等三秒才有第一帧），进程内用 `Vcut.last` 缓存最后一次结果，切页直接读缓存。

## 4. 卡片间距

`ui/util/CardSpacing.kt`：

```kotlin
internal fun Modifier.firstCard(): Modifier = padding(12.dp)
internal fun Modifier.nextCard(): Modifier = padding(horizontal = 12.dp).padding(bottom = 12.dp)
```

12dp 是栅格单位：左右永远 12dp，卡与卡之间 12dp，分组标题自带与上一张卡的距离。页面排班 `SmallTitle("分组标题")` → `Card(Modifier.nextCard())` → `SmallTitle("下一个分组")` → `Card(Modifier.nextCard())`。每组第一张卡用 `firstCard()`（四周 12dp）负责与上方内容拉开；同组后续卡用 `nextCard()`（左右 12dp、下方 12dp），避免相邻卡上下间距叠成 24dp；`SmallTitle` 与卡片之间不再补间距。

教训：`CardSpacing.kt` 头注释记着「五个页面五种写法」。五种写法单看每页都好看，并排放进 Pager 就露馅——左右边距差 4dp、卡间距差 8dp，滑动时同一张卡的位置会跳。统一成两个函数后，间距从「每页自己决定」变成「栅格决定」。

状态卡是这套栅格里的一个 Card，结构固定：

```kotlin
Card(
    modifier = Modifier.firstCard(),
    colors = CardDefaults.defaultColors(color = statusColor),
    onClick = onRefresh, showIndication = true,
    pressFeedbackType = PressFeedbackType.Tilt,
) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().offset(27.dp, 31.dp), contentAlignment = Alignment.BottomEnd) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(110.dp))
        }
        Column(Modifier.padding(16.dp, 14.dp)) {
            Text(title, style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(1.dp))
            Text(body, style = MiuixTheme.textStyles.body2)
        }
    }
}
```

要点：`pressFeedbackType = PressFeedbackType.Tilt` 让整卡可点时有倾斜反馈，`showIndication = true` 才有水波纹；底纹图标 `offset(27.dp, 31.dp)` + `size(110.dp)` 溢出到右下角，超出部分由 Card 圆角裁掉；正文 `padding(16.dp, 14.dp)` 横向比纵向大，文字不顶圆角；标题 `title3` + SemiBold、正文 `body2`，中间只隔 `1.dp`，分层靠字号与字重而不是间距。

设置页同栅格：`SmallTitle(text)` → `Card(Modifier.nextCard())` → 组内若干 `ArrowPreference` / `SwitchPreference`。危险操作单独一张卡，点击先把确认文案写进 `confirm: String?` 状态，由 `WindowDialog` 消费，不在 `onClick` 里直接执行。功能页排班是「一功能一卡」，点击 `startActivity` 进独立 Activity：

```kotlin
Card(Modifier.firstCard()) {
    ArrowPreference(
        title = stringResource(R.string.feature_battery),
        summary = stringResource(R.string.feature_battery_summary),
        onClick = { context.startActivity(Intent(context, BatteryActivity::class.java)) },
    )
}
```

## 5. 底栏三态

两个设置项决定三种底栏，判定写成一行，不写嵌套 if：

```kotlin
// 这是 vcut_lower 原工程的写法（三态）。模板已删掉第三态，现在只保留 0 / 1，
// 删除理由见 SKILL.md 4.1：那个开关在真机上拨了跟没拨一样。
val navBarMode = if (!isFloatingNavbar) 0 else if (!isLiquidGlass) 1 else 2
```

| navBarMode | 语义 | 实现 |
| 0 | 贴底 | `NavigationBar(color = surface)` + `NavigationBarItem`，占满宽度 |
| 1 | 悬浮 | `FloatingNavigationBar` + `FloatingNavigationBarItem`，透明背景 + `textureBlur(shape = RoundedCornerShape(FloatingToolbarDefaults.CornerRadius))` |
| 2 | 液态玻璃 | 项目自建 `IosLiquidGlassNavigationBar`（539 行）。**模板已删此态** |

四标签固定：0 主页 `MiuixIcons.Home`、1 功能 `MiuixIcons.Tune`、2 设置 `MiuixIcons.Settings`、3 关于 `MiuixIcons.Info`。

要点：`NavigationBarItem` 有 `enabled` 参数，禁用状态交给它，别只在 `onClick` 里 `return`；液态玻璃版不是 Miuix 组件，是自建的 539 行文件，内部用 `blur` / `drawBackdrop` / `Highlight` 叠玻璃层，`NavigationItem(label, icon)` 只是数据载体，渲染全在自己手里，注释必须标「自建」，不要当库 API 用；三态共用同一份 `selectedIndex`，切模式只换渲染，不能丢选中项。

## 6. 弹层选择

两个工程都不用 `OverlayDialog`，统一 `WindowDialog`：

```kotlin
WindowDialog(
    show = pending != null, title = pending?.title.orEmpty(),
    summary = pending?.summary.orEmpty(), onDismissRequest = { pending = null },
) {
    val dismiss = LocalDismissState.current
    Row {
        TextButton(text = "取消", onClick = { dismiss?.invoke() }, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(20.dp))
        TextButton(text = "确定", onClick = { pending = null },
            modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
    }
}
```

| | `WindowDialog` | `OverlayDialog` |
| 依赖 | 无，任意 Composition 可弹 | 需要 Scaffold 的 `popupHost` |
| 场景 | 独立 Activity、跨页面确认 | 单页面内浮层 |

`WindowDialog` 走 Window 层，`ThemeActivity`、`BatteryActivity` 里照样能弹，不需要每个 Activity 都套 Scaffold；所以 `PageScaffold` 里的 `Scaffold` 直接把 `popupHost = { }` 置空，全局只保留一种弹层容器，少一套 Overlay 生命周期要维护。`LocalDismissState` 提供与退出动画同步的关闭动作，直接改 `show = false` 会跳帧，`dismiss()` 走完整动画；`onDismissRequest = { }` 能让「正在进行中」的确认弹层无法被返回键或点外部关闭。

改用 `OverlayDialog` 就必须把 `popupHost` 恢复成 `{ MiuixPopupHost() }`（或等价容器）。两者二选一，混用会出现「点了没反应」——弹层挂在另一个宿主里。

## 7. 主开关与从属项

`AppSettings` 默认值：

```kotlin
data class AppSettings(
    val themeMode: String = "System",
    val isFloatingNavbar: Boolean = false,   // 悬浮底栏
    val isLiquidGlass: Boolean = false,      // 液态玻璃底栏（vcut 有；模板已删）
    val isBlurEnabled: Boolean = true,       // 顶栏模糊
)
```

`PREFS_NAME = "app_settings"`。**模板已删除 `isLiquidGlass`**（见 SKILL.md 4.1）。
原工程里它是 `isFloatingNavbar` 的子选项而非平级项——液态玻璃只在悬浮底栏上成立，
贴底底栏没有玻璃层。这类「子选项」的 UI 表达方式仍值得学：

```kotlin
SwitchPreference(title = "悬浮底栏", checked = isFloatingNavbar, onCheckedChange = { ... })
FoldUnder(visible = isFloatingNavbar) {
    SwitchPreference(title = "液态玻璃", checked = isLiquidGlass, onCheckedChange = { ... })
}
```

`FoldUnder` 是自建组件（非 Miuix API），用 `AnimatedVisibility` 折叠：`visibleState = remember { MutableTransitionState(visible) }.apply { targetState = visible }`，进 `fadeIn() + expandVertically()`，出 `fadeOut() + shrinkVertically()`。

理由：从属项不适用时折叠而不是置灰，一屏灰开关说明不了「为什么不能开」；`settled` 参数用于首帧状态还没从 `SharedPreferences` 读出来时直接落位，避免页面进入时播一次「展开→收起」的假动画；关掉主开关时从属值保留（属于用户偏好而非临时状态），下次打开还是上次的玻璃设置。依赖关系也要写进文档：`navBarMode` 的判定顺序是「先看是否悬浮，再看是否玻璃」，颠倒会把贴底 + 玻璃判成玻璃态。

## 8. 主题持久化与系统栏

`Theme.kt` 里的 `ThemeController` 负责读、写、容错：

```kotlin
val themeMode = try {
    ColorSchemeMode.valueOf(saved.themeMode)
} catch (_: Exception) {
    ColorSchemeMode.System
}
```

存的是 `ColorSchemeMode.name`，枚举值改名就等于用户设置失效，所以读取必须容错、失败落回 `System`。`ColorSchemeMode` 共 6 个值，`AppNightMode.nightModeFor` 只区分四类：`Light` / `MonetLight` → `MODE_NIGHT_NO`，`Dark` / `MonetDark` → `MODE_NIGHT_YES`，`else`（含 `System`）→ `MODE_NIGHT_AUTO`。枚举新增值会自然落到 `AUTO`，不用同步改映射。

系统栏图标按 `surface` 实际亮度算，不看 `isSystemInDarkTheme()`：

```kotlin
@Composable
fun isInDarkTheme(): Boolean {
    val s = MiuixTheme.colorScheme.surface
    return 0.2126f * s.red + 0.7152f * s.green + 0.0722f * s.blue < 0.5f
}
val isDark = isInDarkTheme()
SideEffect {
    val window = (view.context as? Activity)?.window ?: return@SideEffect
    WindowCompat.getInsetsController(window, view).run {
        isAppearanceLightStatusBars = !isDark; isAppearanceLightNavigationBars = !isDark
    }
}
```

原因：`enableEdgeToEdge()` 的系统栏样式只跟随系统深浅模式。应用内强制 `Light` / `Dark`，或 `MonetDark` 派生出深色表面，系统都不知道；按 `surface` 亮度算才与真实背景一致。`SideEffect` 每次重组同步一次，主题切换立刻生效。

`AppNightMode` 处理启动页，和 Compose 主题是两件事：

```kotlin
object AppNightMode {
    private const val PREFS_NAME = "launch_background"
    private const val KEY_APPLIED = "night_mode"
    fun apply(context: Context, mode: ColorSchemeMode) {
        val target = nightModeFor(mode)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_APPLIED, Int.MIN_VALUE) == target) return
        context.getSystemService(UiModeManager::class.java).setApplicationNightMode(target)
        prefs.edit().putInt(KEY_APPLIED, target).apply()
    }
}
```

启动页窗口背景与 `launch_background` 由系统按应用夜间模式解析，`MiuixTheme` 管不到；系统深色 + 应用浅色时启动页是黑底直接跳浅色界面，闪一下。`setApplicationNightMode` 会触发 configuration change 重启 Activity，所以只在值变化时写（`KEY_APPLIED` 比对），否则每次冷启动都重建一次。单用 `launch_background` 这个 prefs 文件，与 `app_settings` 分开，清理设置时不会连带清掉已应用的夜间模式。

## 9. 页面切换与返回

四页 Pager：`rememberPagerState(pageCount = { 4 })` + `HorizontalPager(state = pagerState, beyondViewportPageCount = 1, overscrollEffect = null, modifier = Modifier.fillMaxSize())`。

`goToPage` 用弹簧动画驱动，不用 `animateScrollToPage`：

```kotlin
val PagerNavigationSpringSpec = spring<Float>(
    dampingRatio = 0.86f, stiffness = 380f, visibilityThreshold = 1f,
)
fun goToPage(index: Int) {
    if (index == selectedIndex) return
    selectedIndex = index
    scope.launch {
        val pageSize = pagerState.layoutInfo.pageSize + pagerState.layoutInfo.pageSpacing
        val scrollPixels =
            (index - pagerState.currentPage - pagerState.currentPageOffsetFraction) * pageSize
        var previous = 0f
        pagerState.scroll(MutatePriority.UserInput) {
            animate(0f, scrollPixels, PagerNavigationSpringSpec) { v, _ ->
                previous += scrollBy(v - previous)
            }
        }
        if (pagerState.currentPage != index) pagerState.scrollToPage(index)
    }
}
BackHandler(enabled = selectedIndex != 0) { goToPage(0) }
```

- `MutatePriority.UserInput` 让程序驱动的滚动与手指滑动同优先级，动画中途接管不会互相打断。
- `animate` 给的是绝对位移，`scrollBy` 吃的是增量，必须用 `previous` 累积换算，否则位移被平方放大。
- `dampingRatio = 0.86f` 略欠阻尼，落点有一次很小的回弹，接近系统 Pager 手感；`stiffness = 380f` 比默认快，四页之间不拖沓；`visibilityThreshold = 1f`（像素）让动画在亚像素处停下，尾部不空转。收尾 `scrollToPage` 兜底：动画被别的手势打断时由它对齐页面。
- `BackHandler` 的 `enabled` 用 `selectedIndex` 而不是 `pagerState.currentPage`，避免滑动过程中两者不一致导致返回键失灵；返回键先回第一页，在第一页时才交给系统退出。`beyondViewportPageCount = 1` 预组合相邻页，滑动首帧不白；`overscrollEffect = null` 避免与 Miuix 回弹冲突。

HyperMusicCover 的 `MainActivity` 有三个骨架之外的时序设计：

1. `settingsEpoch` 在 `onResume` 自增。主题页是独立 Activity，返回时主界面不会重组；用 `var settingsEpoch by mutableIntStateOf(0)`，`onResume` 里 `settingsEpoch++`，主界面 `remember(settingsEpoch) { readSettings() }` 重读 `SharedPreferences`。不需要广播、不需要 Flow、不引入全局单例。
2. 夜间模式在 `onStop` 应用。`setApplicationNightMode` 会触发 configuration change 重建 Activity；放 `onStop` 时界面已不可见，用户看不到中途闪断；放 `onResume` 会形成「重建 → 再 onResume → 再写」的循环。
3. 冷启动白闪三件套：`window.setBackgroundDrawable(...)` 在 `setContentView` 之前按已存主题设窗口背景；`holdSplashUntilContentIsReady()` 让 splash 撑到首帧内容就绪再撤；`AppNightMode.apply(...)` 让 `launch_background` 与窗口背景共用同一套判定。三者缺一，深色系统 + 浅色应用仍会白闪一帧。

功能页点进独立 Activity，拿到系统转场、返回手势与预测性返回动画，页面内不再嵌二级导航。

## 10. 可复用清单

| 文件 | 作用 | 搬用时必须改什么 |
| `ui/util/PageScaffold.kt` | 二级页统一骨架：Scaffold + 模糊顶栏 + layerBackdrop + LazyColumn | 包名；`stringResource` 指向的资源；`isBlurEnabled` 的取值来源 |
| `ui/util/CardSpacing.kt` | `firstCard` / `nextCard` 的 12dp 栅格 | 无，原样复制 |
| `ui/theme/Theme.kt` | `ThemeController`、`AppTheme`、`isInDarkTheme`、系统栏 `SideEffect` | 包名；增删 `ColorSchemeMode` 时检查 `nightModeFor` 的 `else` 分支 |
| `ui/theme/AppNightMode.kt` | 启动页夜间资源与 `launch_background` | 无，`PREFS_NAME` / `KEY_APPLIED` 保持常量避免撞名 |
| `ui/util/FoldUnder.kt` | 从属项折叠容器（自建，非 Miuix API） | 包名；注释保留「自建」标注 |
| `ui/component/IosLiquidGlassNavigationBar.kt` | 液态玻璃底栏（539 行，自建） | 包名；`NavigationItem` 列表与图标；保留「自建」标注 |
| `MainActivity` | 四标签 Pager、`navBarMode`、`settingsEpoch`、返回键 | 四个页面与图标；`settingsEpoch` 的重读键 |
| `AppSettings` + prefs | 主题与底栏三态持久化 | `PREFS_NAME` 不要与宿主其它模块重名 |

搬用顺序：

```bash
mkdir -p app/src/main/java/APP_PKG/ui/util app/src/main/java/APP_PKG/ui/theme
cp PageScaffold.kt CardSpacing.kt app/src/main/java/APP_PKG/ui/util/
# FoldUnder.kt 是原工程自建组件，模板未内置；需要时按 hmc-patterns.md 第 1 节自己建
cp Theme.kt AppNightMode.kt app/src/main/java/APP_PKG/ui/theme/
grep -rn "stringResource(R.string" app/src/main/java/APP_PKG/ui/util/
```

检查项：页面统一走 `PageScaffold`，不存在第二份复制的骨架；列表用 `overscrollEffect = null` + `scrollEndHaptic()` + `overScrollVertical()` + `nestedScroll()`；顶部 inset 在 `contentPadding`；卡片间距只用 `firstCard` / `nextCard`；弹层只用 `WindowDialog` 且 `popupHost = { }`；`navBarMode` 只有 0/1 两态（原工程的第三态已从模板删除，见 SKILL.md 4.1）；主题模式持久化并容错、状态栏图标按 `surface` 亮度同步；模糊前先判 `isRuntimeShaderSupported()` 与 `SDK_INT >= TIRAMISU`；`layerBackdrop` 的注册层里不含用同一 backdrop 做模糊的组件（自采样会让渲染树递归后 SIGSEGV）；夜间模式只在值变化时写且应用点在 `onStop`；vcut_lower 的自建组件（`FoldUnder`、液态玻璃底栏）在原文里标注为自建 —— 模板已不含这两者。

> **关于「整屏只有一个 backdrop」的更正**：这条早期表述被误读成「只创建一个 `LayerBackdrop` 对象」，
> 导致把 AppShell 的 backdrop 传给 PageScaffold 共用，装机后 native SIGSEGV。真实约束是
> **`layerBackdrop` 注册的那层内容里不能包含用该 backdrop 做模糊的组件**。
> vcut_lower 与 HyperMusicCover 都是两份独立 backdrop：外层包 pager 给底栏采样（底栏在
> `Scaffold(bottomBar=)` 里，天然在注册层之外），PageScaffold 自己那份只包列表给顶栏采样。
> 实测崩溃日志：`SkiaDisplayList::prepareListAndChildren` → `RenderNode::prepareTreeImpl` 递归 500+ 层 → `signal=11`。

## 11. 第三个案例：Cirno（候选列表与筛选）

`Adkimsm/Cirno`（包名 `nep.timeline.cirno`）是应用冻结与进程管理工具。它不共享前两个工程的
四标签骨架，价值在另一处：**一屏要列几百个可选项时的做法**。完整规范见 `references/list-and-filter.md`。

### 双流模型（`ui/viewModel/AppListViewModel.kt`）

```kotlin
private val _filterApps = MutableStateFlow<List<AppItem>>(emptyList())      // 全量
private val _cacheFilterApps = MutableStateFlow<List<AppItem>>(emptyList()) // 过滤后
private val _search = MutableStateFlow("")
private val _type = MutableStateFlow(0)
private val _updatedApps = MutableStateFlow(true)                            // 加载中 = false

private fun autoUpdateCacheFilterApps() {
    viewModelScope.launch {
        combine(_filterApps, _search, _type) { apps, search, type ->
            apps.asSequence()
                .filter { /* 分类 */ }
                .filter {
                    if (search.isNotEmpty())
                        it.appName.contains(search, ignoreCase = true) ||
                        it.packageName.contains(search, ignoreCase = true)
                    else true
                }.toList()
        }.collect { _cacheFilterApps.value = it }
    }
}
```

两个流各有职责：`filterApps` 回答「这一类到底有没有东西」，`cacheFilterApps` 回答「现在该画哪些」。
合成一个流时，搜不到结果就分不清是「没数据」还是「被筛掉了」。

### 分组条（`ui/page/AppPage.kt`）

```kotlin
private val AppListTopShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val AppListBottomShape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)

itemsIndexed(items = appItems, key = { _, item -> item.packageName + "#" + item.userId }) { i, item ->
    if (appCount == 1) {
        CirnoCard(modifier = Modifier.padding(horizontal = 12.dp)) { AppItemCompose(item) }
    } else {
        val shape = when {
            i == 0 -> AppListTopShape
            i == appCount - 1 -> AppListBottomShape
            else -> RectangleShape
        }
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                .clip(shape).cirnoCardBackground(shape, colorScheme.surfaceContainer)) {
            AppItemCompose(item)
        }
    }
}
```

一块卡切成条，不是每项一张小卡。key 用 `packageName + "#" + userId`：同一个包可能有多个用户
（分身/工作资料），只用包名会让 Compose 认为是同一个 item。

### 列表项四槽位（`ui/viewModel/AppItemCompose.kt`）

图标 58dp + `padding(end = 16.dp)` → `title`（`title4` 18sp）→ `subtitle`（状态短语，`subtitle` 14sp Bold）
→ `summary`（标识，`footnote1` 13sp）→ 右侧最多一个 `StatusTag`（9sp、750 字重、6dp 圆角）。

**`subtitle` 是状态，`summary` 是标识**，写反了读起来别扭。状态短语走 `if/else if` 链而非拼字符串，
拼出来的长度不可控会挤掉 summary。

### 弹窗内列表（`ui/dialog/RunningProcessDialog.kt`）

```kotlin
// Fields must be observable and every read must happen inside the dialog body lambda.
// The Miuix dialog content is invoked by an ancestor Scaffold popup host, so a value hoisted
// outside the lambda is only a captured constant for that subtree and never refreshes there.
private class RunningProcessState {
    var snapshot: RunningProcessSnapshot? by mutableStateOf(null)
    var loaded: Boolean by mutableStateOf(false)
}
```

**这是 Miuix 弹窗最容易踩的坑**：`OverlayDialog` 的内容由祖先 `Scaffold` 的 popup host 调用，
不在当前组合位置。把 `val snapshot = state.snapshot` 写在 `OverlayDialog(...)` 之前，
那个子树拿到的是捕获的常量，数据回来了也不刷新——表现为弹窗一直转圈。

弹窗列表用 `Column + verticalScroll`（不是 `LazyColumn`），`heightIn(max = 320.dp)`，
长标识（进程名、包名）用 `horizontalScroll` + `maxLines = 1, softWrap = false` 让它能横滑读完，
描述行才用 `Ellipsis`。
