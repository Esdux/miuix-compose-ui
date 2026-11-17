# 页面配方

以下片段按 0.9.4 / 0.9.4-rc01 API 编写。`MiuixIcons.*` 图标需要按 `references/theme-and-icons.md` 单独 import，片段中省略图标 import。

## 0. 四标签应用壳（vcut_lower / HyperMusicCover 形态）

四个平级标签 + 顶部渐进模糊 + 三态底栏，是 vcut_lower 与 HyperMusicCover 共用的外壳。整份壳只建一层 backdrop、只持有一个 PagerState、只用一个 BackHandler，四个页面都是它的子节点。

### 0.1 四标签定义

| 索引 | 标签 | 放什么 | 图标 |
|---|---|---|---|
| 0 | 主页 | 状态卡（可点刷新）+ 少量入口 | `MiuixIcons.Home` |
| 1 | 功能 | 一功能一 `Card`，点击 `startActivity` 进独立 Activity | `MiuixIcons.Tune` |
| 2 | 设置 | `SmallTitle` 分组 + `Card` + `ArrowPreference` / `SwitchPreference` | `MiuixIcons.Settings` |
| 3 | 关于 | 版本、构建信息、开源声明 | `MiuixIcons.Info` |

四个标签之间没有返回栈关系：任意标签都能直接点过去，返回键从任何非主页标签回主页。

### 0.2 Pager 骨架

```kotlin
val pagerState = rememberPagerState(pageCount = { 4 })

HorizontalPager(
    state = pagerState,
    beyondViewportPageCount = 1,
    overscrollEffect = null,
    modifier = Modifier.fillMaxSize(),
) { page ->
    when (page) {
        0 -> HomePage(backdrop, blurActive)
        1 -> FeaturePage(backdrop, blurActive)
        2 -> SettingsPage(backdrop, blurActive)
        3 -> AboutPage(backdrop, blurActive)
    }
}
```

两个参数都不是可选项：

- `beyondViewportPageCount = 1`：默认 0 时相邻页要等滑到才组合，快速翻页会出现半屏空白或顶栏高度跳变。给 1 只多组合一页，滑动首帧就有内容；不要给更大值，四个页面全预组合等于常驻四份 `LazyColumn`。
- `overscrollEffect = null`：Pager 自带的 stretch 会和 Miuix 的阻尼回弹打架，并且它会抢走列表的越界手势，表现为「滑到头先整页拉伸、列表回弹失效」。所有越界反馈交给页内的 `overScrollVertical()`。

### 0.3 底栏三态 navBarMode

```kotlin
// 0 = 贴底；1 = 悬浮
val navBarMode = if (isFloatingNavbar) 1 else 0

when (navBarMode) {
    0 -> NavigationBar(color = barColor) { /* NavigationBarItem ×4 */ }
    1 -> FloatingNavigationBar(color = barColor) { /* FloatingNavigationBarItem ×4 */ }
    // 2 -> 曾用于液态玻璃（vcut_lower 的自建 539 行组件）。模板已删：那个开关是假的，
    //      打开只把模糊半径 25→32，用户看不出区别。详见 SKILL.md 第 4 节。
}
```

模板只保留两态：贴底与悬浮。**曾经有过第三种「液态玻璃」，已删除** ——
它作为悬浮的子选项存在（`isLiquidGlass = true` 而 `isFloatingNavbar = false` 时仍是贴底条），
但打开后只是模糊半径 25 → 32 加一道描边，用户看不出区别。
一个拨了跟没拨一样的开关，比没有这个开关更糟：它会让人怀疑其它开关也是假的。

三态共用的 `barColor`：

```kotlin
val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
```

点标签时同时更新 `selectedIndex`（立刻，供底栏高亮与 BackHandler 判定）和 Pager 位置（动画，见下节）：

```kotlin
val scope = rememberCoroutineScope()

NavigationBarItem(
    selected = selectedIndex == index,
    onClick = {
        selectedIndex = index
        scope.launch { pagerState.goToPage(index) }
    },
    icon = { Icon(icons[index], contentDescription = labels[index]) },
    label = labels[index],
)
```

### 0.4 弹簧翻页 goToPage

```kotlin
private val PagerNavigationSpringSpec = spring<Float>(
    dampingRatio = 0.86f,
    stiffness = 380f,
    visibilityThreshold = 1f,
)

private suspend fun PagerState.goToPage(target: Int) {
    if (target == currentPage) return
    val scrollPixels = (target - currentPage) * pageSize.toFloat() +
        currentPageOffsetFraction * pageSize.toFloat()

    scroll(MutatePriority.UserInput) {
        var previous = 0f
        animate(0f, scrollPixels, PagerNavigationSpringSpec) { value, _ ->
            previous += scrollBy(value - previous)
        }
    }
}
```

拆开看：

- `MutatePriority.UserInput`：让这次程序化滚动和手指滑动同级。用默认优先级时，用户中途上手滑会被这次动画压住；用 `UserInput` 则是新的一次滑动直接接管，动画自然让位。
- `animate(0f, scrollPixels, PagerNavigationSpringSpec) { value, _ -> ... }`：弹簧驱动的是「本次需要移动的像素总量」，不是页码，所以中途被接管时不会跳回。
- `previous += scrollBy(value - previous)`：`scrollBy` 会消费实际可滚动的量并把消费值返回，累加进 `previous` 才能保证下一次只滚增量。直接 `scrollBy(value)` 会按帧重复累加，翻两页时冲过头。
- `0.86f / 380f / 1f`：阻尼偏大、刚度中等，落点不回弹、起步不拖沓；`visibilityThreshold = 1f` 让 1 像素内直接收敛，避免长时间在亚像素上抖。

不要用 `animateScrollToPage`：它走 Pager 内置的 `AnimationSpec`，既不接受 `MutatePriority`，也无法把「用户手指优先级」传下去，动画期间列表手势会被吞。

### 0.5 BackHandler 契约

```kotlin
BackHandler(enabled = selectedIndex != 0) {
    selectedIndex = 0
    scope.launch { pagerState.goToPage(0) }
}
```

- 只有非主页才拦截；在主页按返回交还给系统，由系统决定退出/回桌面，应用不弹二次确认。
- 判定用 `selectedIndex` 而不是 `pagerState.currentPage`：`currentPage` 在弹簧动画中途才更新，连按两次返回时第二次仍会命中「非 0」，出现返回一次没退、再按一次才回到主页的错位。
- 拦截后同时改 `selectedIndex`，底栏高亮跟着回主页，不会出现「停在主页但高亮还在设置」。
- 标签页内部不再各自声明 BackHandler，否则每页一个、行为互相覆盖。

### 0.6 backdrop 与 blurActive 的共享

整屏只允许一层 backdrop，建立在 Scaffold 之上、Pager 之下，四个页面共用同一个实例：

```kotlin
val backdrop = rememberLayerBackdrop {
    drawRect(surfaceColor)
    drawContent()
}
```

```kotlin
val blurSupported = rememberBlurBackdrop() != null
val blurActive = isBlurEnabled && blurSupported
```

- `blurActive` 是「设置里开着模糊」与「设备真的支持运行时着色器」的合取，不是单个开关值。向下只传这个结果，页面里不要再各自判断 `isBlurEnabled`。
- 页面接收 `backdrop` 与 `blurActive` 两个参数，转手交给自己的 `PageScaffold(isBlurEnabled = blurActive)`；页内需要模糊的容器写 `Box(modifier = if (blurActive) Modifier.layerBackdrop(backdrop) else Modifier)`。
- 每页不能再调 `rememberLayerBackdrop`：第二层会把第一层的内容当成背景再采样一次，表现为切页后旧页残影、模糊里混着上一页的列表。
- `backdrop` 为 null（不支持模糊）时 `blurActive` 必为 false，此时所有 `layerBackdrop` 分支都不生效，顶栏回退纯色，不需要额外兜底代码。

### 0.7 rememberBlurBackdrop 与支持判定

```kotlin
@Composable
fun rememberBlurBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported() || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return null
    }
    val surface = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
}
```

支持判定必须在建 backdrop 之前，且两项都要判：运行时着色器能力（`isRuntimeShaderSupported()`）决定能不能编译模糊着色器，`SDK_INT < TIRAMISU` 决定系统有没有对应的 `RenderEffect` 通路。只在其中一项上放行，会在另一项缺失的设备上拿到空模糊甚至黑块。

### 0.8 TopBarBlurConfig

顶栏模糊的四个常量集中在一处，页面不要散写：

| 常量 | 值 | 作用 |
|---|---|---|
| `BlurRadius` | `15f` | 模糊半径；再大会把标题也糊掉 |
| `GradientCurve` | `10f` | 顶栏下缘渐变过渡的曲率 |
| `SurfaceAlpha` | `0.3f` | 模糊层上叠加的 surface 透明度，保证标题可读 |
| `ScrollFadeDistance` | `0.dp` | 滚动淡出距离；0 表示不做滚动淡出，模糊常驻 |

```kotlin
object TopBarBlurConfig {
    const val BlurRadius = 15f
    const val GradientCurve = 10f
    const val SurfaceAlpha = 0.3f
    val ScrollFadeDistance = 0.dp
}
```

```kotlin
BlurredBar(backdrop = backdrop, blurActive = blurActive, scrollBehavior = scrollBehavior) {
    TopAppBar(title = title, color = barColor, scrollBehavior = scrollBehavior)
}
```

### 0.9 isInDarkTheme

状态栏图标明暗不能只看 `ColorSchemeMode`：应用内强制浅色时系统仍是深色。按 surface 的相对亮度判定：

```kotlin
fun Color.isInDarkTheme(): Boolean {
    val luminance = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    return luminance < 0.5f
}
```

```kotlin
val dark = MiuixTheme.colorScheme.surface.isInDarkTheme()
```

0.5 是分界：低于它按深色处理（状态栏走浅色图标），高于它按浅色处理。

### 0.10 AppSettings 与 AppNightMode

应用设置是一个不可变数据类，字段与 `app_settings` 里的键一一对应：

```kotlin
data class AppSettings(
    val themeMode: String = "System",
    val isFloatingNavbar: Boolean = false,
    val isBlurEnabled: Boolean = true,
)

const val PREFS_NAME = "app_settings"
```

```kotlin
val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
val settings = AppSettings(
    themeMode = prefs.getString("theme_mode", "System") ?: "System",
    isFloatingNavbar = prefs.getBoolean("floating_navbar", false),
    isBlurEnabled = prefs.getBoolean("blur_enabled", true),
)
```

夜间模式写进另一个文件 `launch_background`，并且只在目标值变化时写：

```kotlin
object AppNightMode {
    const val PREFS_NAME = "launch_background"
    const val KEY_APPLIED = "night_mode"

    fun nightModeFor(mode: String): Int = when (mode) {
        "Light", "MonetLight" -> AppCompatDelegate.MODE_NIGHT_NO
        "Dark", "MonetDark" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_AUTO
    }

    fun apply(activity: Activity, mode: String) {
        val target = nightModeFor(mode)
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val applied = prefs.getInt(KEY_APPLIED, Int.MIN_VALUE)
        if (applied == target) return
        prefs.edit().putInt(KEY_APPLIED, target).apply()
        AppCompatDelegate.setDefaultNightMode(target)
    }
}
```

```kotlin
val mode = when (settings.themeMode) {
    "Light", "MonetLight" -> ColorSchemeMode.Light
    "Dark", "MonetDark" -> ColorSchemeMode.Dark
    else -> ColorSchemeMode.System
}
```

- `nightModeFor` 只做映射，不读不写；四个显式模式各自映射到 NO/YES，其余（含 `System`、`MonetSystem` 与未知值）统一 AUTO。
- 只在 `applied != target` 时才写：`setDefaultNightMode` 每次调用都会触发 `uiMode` 配置变化，无条件调用会在每次前台切换时重建 Activity，滚动位置和 Pager 位置全部丢失。

### 0.11 MainActivity 的三个设计

**一、settingsEpoch 在 onResume 自增。** 主题页是独立 Activity，返回时本进程的设置快照已过期：

```kotlin
private var settingsEpoch by mutableIntStateOf(0)

override fun onResume() {
    super.onResume()
    settingsEpoch++
}
```

```kotlin
setContent {
    val epoch = settingsEpoch
    val settings = remember(epoch) { readSettings(this) }
    MiuixTheme(controller = remember(settings.themeMode) { ThemeController(modeOf(settings.themeMode)) }) {
        AppShell(settings = settings)
    }
}
```

`remember(epoch)` 让每次回到前台都重读一次 `SharedPreferences`，改完主题/底栏形态返回即时生效；不依赖 Activity 重建，也不会因为不动就永远读旧值。

**二、夜间模式在 onStop 应用。** 改 `setDefaultNightMode` 会触发配置变化、重建 Activity；在 `onResume` 里应用会和「刚恢复就被重建」撞在一起，表现为闪一下白再回来。

```kotlin
override fun onStop() {
    super.onStop()
    AppNightMode.apply(this, currentSettings.themeMode)
}
```

**三、冷启动白闪三件套。** 顺序固定，缺一张就会在启动瞬间看到白屏：

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // 1) 窗口底色先跟上当前主题，避免 splash 背后是默认白
    window.setBackgroundDrawable(ColorDrawable(MiuixTheme.colorScheme.surface.toArgb()))

    // 2) splash 挂到内容绘制完成
    holdSplashUntilContentIsReady()

    // 3) 夜间模式在内容前落到 delegate
    AppNightMode.apply(this, readSettings(this).themeMode)

    setContent { /* ... */ }
}
```

第 1 步要早于 `setContent`，否则第一帧仍是系统默认背景；第 2 步让 splash 退场时机跟首帧绑定，而不是固定延时；第 3 步必须在内容组合前调用 `AppNightMode.apply`，否则首帧按旧模式取色，随后重建又闪一次。

### 0.12 状态轮询

```kotlin
private const val POLL_MS = 3000L

LaunchedEffect(Unit) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while (true) {
            refresh()        // 先读
            delay(POLL_MS)   // 再等
        }
    }
}
```

- 先读再 delay：进入 RESUMED 立刻拿到一次数据，不用空等 3 秒；切后台时 `repeatOnLifecycle` 取消协程，最后一次读取之后不再有定时器。
- 3000ms 是唯一周期常量 `POLL_MS`，不要在多处写 3000。
- 进程内用 `Vcut.last` 缓存上一轮结果：切页、切后台再回来时先用缓存渲染，本轮结果到了再覆盖，避免每页都闪一次空态。
- 轮询只放在主页状态卡所在层，功能页/设置页不各自起轮询。

### 0.13 状态卡

```kotlin
Card(
    modifier = Modifier.firstCard().fillMaxWidth(),
    colors = CardDefaults.defaultColors(color = statusColor),
    onClick = onRefresh,
    showIndication = true,
    pressFeedbackType = PressFeedbackType.Tilt,
) {
    Box(Modifier.fillMaxSize()) {
        // 底纹图标：故意溢出卡片右下角，制造「被裁掉的角标」观感
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(27.dp, 31.dp),
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                modifier = Modifier.size(110.dp),
            )
        }
        Column(Modifier.padding(16.dp, 14.dp)) {
            Text(
                text = statusTitle,
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(1.dp))
            Text(text = statusBody, style = MiuixTheme.textStyles.body2)
        }
    }
}
```

- `colors = CardDefaults.defaultColors(color = statusColor)`：整卡底色承载状态语义（正常/异常/未知），不另加色块。
- `onClick = onRefresh` + `showIndication = true` + `pressFeedbackType = PressFeedbackType.Tilt`：整卡可点刷新，用倾斜反馈而不是下沉，因为卡片面积大、下沉会露出下方背景。
- 底纹图标用 `Box(fillMaxSize()) + align(BottomEnd) + offset(27.dp, 31.dp) + size(110.dp)`，靠 Card 自身裁剪形成切角；不要用 `Modifier.clip` 自己裁，会连内容一起裁掉。
- 正文 `padding(16.dp, 14.dp)`，标题 `title3` + `SemiBold`，标题与正文之间只留 `Spacer(1.dp)`，不要再加 4/8dp。

### 0.14 设置页与功能页的排布

设置页：分组标题 → 卡片 → 设置行，危险操作单独走 Window 弹层。

```kotlin
item {
    SmallTitle("外观")
    Card(Modifier.nextCard()) {
        ArrowPreference(title = "主题", summary = settings.themeMode, onClick = { openTheme() })
        HorizontalDivider()
        SwitchPreference(
            title = "悬浮底栏",
            summary = "底栏改为悬浮样式",
            checked = settings.isFloatingNavbar,
            onCheckedChange = { setFloatingNavbar(it) },
        )
        HorizontalDivider()
        SwitchPreference(
            title = "背景模糊",
            summary = "关闭可省电，低端机建议关",
            checked = settings.isBlurEnabled,
            onCheckedChange = { setBlurEnabled(it) },
        )
    }
}
```

```kotlin
var confirm by remember { mutableStateOf<String?>(null) }

ArrowPreference(
    title = "清除全部缓存",
    summary = "不可撤销",
    onClick = { confirm = "cache" },
)

confirm?.let { action ->
    WindowDialog(
        show = true,
        title = "确认清除",
        summary = "删除后无法恢复。",
        onDismissRequest = { confirm = null },
    ) {
        val dismiss = LocalDismissState.current
        Row {
            TextButton(text = "取消", onClick = { dismiss?.invoke() }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(20.dp))
            TextButton(
                text = "清除",
                onClick = { doClear(action); confirm = null },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
```

危险操作用 `confirm: String?` 承载「待确认的动作标识」，不用布尔：一个页面里可能有多处危险操作，布尔分不清是哪一个，字符串关掉即回到 null。

功能页：一功能一卡，点击进独立 Activity。

```kotlin
item {
    Card(Modifier.firstCard()) {
        ArrowPreference(
            title = "裁剪视频",
            summary = "打开裁剪工作台",
            onClick = { context.startActivity(Intent(context, TrimActivity::class.java)) },
        )
    }
}
item {
    Card(Modifier.nextCard()) {
        ArrowPreference(
            title = "提取音轨",
            summary = "导出为音频文件",
            onClick = { context.startActivity(Intent(context, ExtractAudioActivity::class.java)) },
        )
    }
}
```

功能不进标签页内部做二级导航：每个功能是独立 Activity，返回即回功能列表，Pager 位置不变。

### 0.15 版本与构建矩阵

| 项 | 值 |
|---|---|
| Miuix（Maven Central） | 0.9.4，2026-09-20 |
| Miuix（vcut_lower 实际使用） | 0.9.4-rc01 |
| minSdk | 35 |
| compileSdk | 37 |
| Kotlin | 2.4.10 |
| AGP | 9.4.0 |
| Compose BOM | 2026.08.00 |

```toml
[versions]
miuix = "0.9.4-rc01"
composeBom = "2026.08.00"
kotlin = "2.4.10"
agp = "9.4.0"

[libraries]
miuix = { module = "top.yukonga.miuix.kmp:miuix", version.ref = "miuix" }
miuix-preference = { module = "top.yukonga.miuix.kmp:miuix-preference", version.ref = "miuix" }
miuix-icons = { module = "top.yukonga.miuix.kmp:miuix-icons", version.ref = "miuix" }
```

```bash
./gradlew :app:assembleDebug
```

0.9.4 与 0.9.4-rc01 的 API 差异集中在个别新增参数，本文片段在两版上都可用；落到具体工程时以工程里锁定的版本为准。

## 1. 统一页面骨架（推荐）

每个页面都从同一个 `PageScaffold` 出发，避免间距、insets、顶栏滚动行为各写一套。完整实现见 `assets/PageScaffold.kt`，来源说明见 `references/real-world-case-study.md`。

签名：

```kotlin
@Composable
fun PageScaffold(
    title: String,
    isBlurEnabled: Boolean,
    extraBottomPadding: Dp = 0.dp,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
)
```

它固定以下结构：

```kotlin
val scrollBehavior = MiuixScrollBehavior()
val backdrop = rememberBlurBackdrop()          // 可能为 null
val blurActive = isBlurEnabled && backdrop != null
val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

Scaffold(
    popupHost = { },                           // 只用 Window 弹层时关闭 Overlay 容器
    topBar = {
        BlurredBar(backdrop, blurActive, scrollBehavior) {
            TopAppBar(
                title = title,
                color = barColor,
                scrollBehavior = scrollBehavior,
                navigationIcon = { if (onBack != null) PageBackButton(onBack) },
                actions = actions,
            )
        }
    },
    contentWindowInsets = WindowInsets.systemBars
        .add(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal),
) { innerPadding ->
    Box(modifier = if (blurActive) Modifier.layerBackdrop(backdrop) else Modifier) {
        LazyColumn(
            overscrollEffect = null,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + extraBottomPadding,
            ),
            content = content,
        )
    }
}
```

要点：

- 顶部 inset 必须放 `contentPadding`，不能加在列表 `modifier` 上。
- `overscrollEffect = null` 与 `overScrollVertical()` 必须成对出现。
- `popupHost = { }` 只适用于全程使用 Window 级弹层的页面；用 Overlay 组件时保留默认 `popupHost`。
- `backdrop` 为 null 时不要调用模糊，回退纯色顶栏。
- 在四标签壳里，`backdrop` 由壳统一建好并下传，页面内的 `PageScaffold` 接收现成的 `blurActive`，不再各建一层，见第 0.6 节。

## 2. 应用壳

```kotlin
@Composable
fun App() {
    val controller = remember { ThemeController(ColorSchemeMode.System) }
    MiuixTheme(controller = controller) {
        Scaffold {
            HomeContent()
        }
    }
}
```

`MiuixTheme` 必须在 `Scaffold` 外层，`Scaffold` 必须在 `MiuixTheme` 内层。Overlay 组件放在 `Scaffold` 内容中。

四标签形态的壳不是这个最小例子，见第 0 节。

## 3. 设置页

```kotlin
@Composable
fun SettingsScreen() {
    var wifi by remember { mutableStateOf(true) }
    var language by remember { mutableIntStateOf(0) }

    Scaffold(topBar = { SmallTopAppBar(title = "设置") }) { padding ->
        LazyColumn(
            overscrollEffect = null,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(/* ScrollBehavior */),
            contentPadding = padding,
        ) {
            item {
                Column {
                    SmallTitle("网络")
                    Card(Modifier.nextCard()) {
                        SwitchPreference(
                            title = "Wi-Fi",
                            summary = "已连接到 HOME-5G",
                            checked = wifi,
                            onCheckedChange = { wifi = it }
                        )
                        HorizontalDivider()
                        WindowDropdownPreference(
                            title = "语言",
                            summary = languages[language],
                            items = languages,
                            selectedIndex = language,
                            onSelectedIndexChange = { language = it }
                        )
                    }
                    SmallTitle("关于")
                    Card(Modifier.nextCard()) {
                        ArrowPreference(title = "版本", summary = "1.0.0", onClick = { })
                    }
                }
            }
        }
    }
}
```

**间距规范**（案例 `CardSpacing.kt`）：

```kotlin
fun Modifier.firstCard() = padding(12.dp)
fun Modifier.nextCard() = padding(horizontal = 12.dp).padding(bottom = 12.dp)
```

- 第一张卡 `firstCard()`，后续每张 `nextCard()`。
- `SmallTitle` 自带与上方内容的间距，不要再补。
- 不要混用 top-only / vertical / bottom-only 写法，否则页与页之间卡片间距会漂移。

`OverlayDropdownPreference` 需要 Scaffold 的 popupHost；只想避免这个依赖时用 `WindowDropdownPreference`。

## 4. 列表页 + 顶栏滚动

```kotlin
val behavior = MiuixScrollBehavior()
LazyColumn(
    overscrollEffect = null,
    modifier = Modifier
        .fillMaxSize()
        .scrollEndHaptic()
        .overScrollVertical(onOffsetChanged = { behavior.state.overscrollOffset = it })
        .nestedScroll(behavior.nestedScrollConnection),
    contentPadding = padding,
) {
    items(items) { item ->
        ArrowPreference(title = item, onClick = { })
        HorizontalDivider()
    }
}
```

要点：

- `overScrollVertical` 放在 `nestedScroll` 之前。
- 必须设 `overscrollEffect = null`；Miuix 的阻尼回弹会替代 Compose 自带 stretch，二者同时存在会在边界打架。
- 列表已绑定顶栏时，不要再把同一个滚动行为传给外层 `PullToRefresh`。
- 下拉刷新仍可用 `PullToRefresh`，但内部列表保持 `overscrollEffect = null`。

## 5. 表单页

```kotlin
Scaffold(topBar = { SmallTopAppBar(title = "新建联系人") }) { padding ->
    Column(
        Modifier.padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextField(value = name, onValueChange = { name = it }, label = "姓名")
        TextField(value = email, onValueChange = { email = it }, label = "邮箱")
        Button(
            onClick = { },
            colors = ButtonDefaults.buttonColorsPrimary(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存")
        }
    }
}
```

## 6. 底部导航

```kotlin
Scaffold(
    bottomBar = {
        NavigationBar(color = barColor) {
            labels.forEachIndexed { i, label ->
                NavigationBarItem(
                    selected = index == i,
                    onClick = { index = i },
                    icon = icons[i],
                    label = label,
                    enabled = true,
                )
            }
        }
    }
) { padding -> /* content */ }
```

- `NavigationBar(color = ...)` 可配合透明背景 + `textureBlur` 做模糊底栏。
- 悬浮样式把组件换成 `FloatingNavigationBar(color = ...)` / `FloatingNavigationBarItem(...)`。
- 需要完全自定义外观时，`NavigationItem(label, icon)` 只是数据载体，渲染自己实现。
- 平级标签页用 `HorizontalPager` + 底栏即可；切换动画用第 0.4 节的弹簧 `goToPage`，不要用 `animateScrollToPage`（它不接受 `MutatePriority`，动画期间会吞掉列表手势）。需要返回栈/深链再用 `miuix-nav`。
- 大屏侧边导航：`Row { NavigationRail { ... }; Box(Modifier.weight(1f)) { ... } }`。

## 7. 对话框

### Overlay 版本（需要 Scaffold + popupHost）

```kotlin
Scaffold {
    TextButton(text = "删除", onClick = { show = true })

    OverlayDialog(
        title = "确认删除",
        summary = "删除后无法恢复。",
        show = show,
        onDismissRequest = { show = false }
    ) {
        TextButton(text = "取消", onClick = { show = false }, modifier = Modifier.fillMaxWidth())
        TextButton(text = "删除", onClick = { show = false }, modifier = Modifier.fillMaxWidth())
    }
}
```

### Window 版本（不依赖 Scaffold）

```kotlin
WindowDialog(
    show = show,
    title = "确认删除",
    summary = "删除后无法恢复。",
    onDismissRequest = { show = false },
) {
    val dismiss = LocalDismissState.current
    Row {
        TextButton(text = "取消", onClick = { dismiss?.invoke() }, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(20.dp))
        TextButton(
            text = "删除",
            onClick = { show = false },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}
```

选择规则：

- 页面已经在 `Scaffold` 内，且喜欢 Overlay 的动效 → 用 `OverlayDialog`，不要动 `popupHost`。
- 页面没有 `Scaffold`、独立 Activity、或已在 `Scaffold` 里把 `popupHost = { }` 关掉 → 用 `WindowDialog`。
- `onDismissRequest = { }` 可禁止中断式关闭。
- 生成中/执行中的弹层可用 `InfiniteProgressIndicator()` 表示不可计算进度。

## 8. 下拉选择与动作菜单

```kotlin
var index by remember { mutableIntStateOf(0) }
val options = listOf("按名称", "按日期", "按大小")

Scaffold(topBar = {
    SmallTopAppBar(
        title = "文件",
        actions = {
            OverlayIconDropdownMenu(
                entry = DropdownEntry(
                    items = options.mapIndexed { i, text ->
                        DropdownItem(text = text, selected = index == i, onClick = { index = i })
                    }
                )
            ) {
                Icon(MiuixIcons.More, contentDescription = "更多")
            }
        }
    )
}) { /* ... */ }
```

- 二级菜单用 `DropdownItem(children = ...)`；`OverlayIconCascadingDropdownMenu` 与 `OverlayCascadingListPopup` 都支持。
- `OverlayIconDropdownMenu` 需要 Scaffold + popupHost；不想依赖时用 `WindowIconDropdownMenu`。
- `WindowDropdownPreference` 支持 `enabled` 与 `onExpandedChange`，适合设置项中的下拉。

## 9. 搜索页

```kotlin
SearchBar(
    inputField = {
        InputField(
            query = query,
            onQueryChange = { query = it },
            onSearch = { expanded = false },
            expanded = expanded,
            onExpandedChange = { expanded = it },
            leadingIcon = {
                Icon(MiuixIcons.Search, contentDescription = "搜索")
            }
        )
    },
    expanded = expanded,
    onExpandedChange = { expanded = it }
) {
    Column { /* 搜索建议/结果 */ }
}
```

`InputField` 用 `query`/`onQueryChange`，普通 `TextField` 才是 `value`/`onValueChange`。

## 10. Snackbar

```kotlin
val hostState = remember { SnackbarHostState() }
val scope = rememberCoroutineScope()

Scaffold(snackbarHost = { SnackbarHost(hostState) }) {
    TextButton(text = "提示", onClick = {
        scope.launch { hostState.showSnackbar("已保存") }
    })
}
```

## 11. 主题持久化与系统栏

```kotlin
enum class ThemeMode { System, Light, Dark, MonetSystem, MonetLight, MonetDark }

val saved = prefs.getString("theme_mode", "System") ?: "System"
val mode = try { ColorSchemeMode.valueOf(saved) } catch (_: Exception) { ColorSchemeMode.System }

val controller = remember(mode) { ThemeController(mode) }
MiuixTheme(controller = controller) {
    SyncSystemBarsToMiuixTheme()   // 按 surface 亮度设置状态栏图标
    AppContent()
}
```

- 主题模式存 `ColorSchemeMode.name`，读取时容错回退 `System`。
- `enableEdgeToEdge()` 的系统栏样式只跟随系统；应用内强制深浅色时，需要按 Miuix `surface` 亮度同步状态栏图标，判定用第 0.9 节的 `isInDarkTheme()`。
- 主题页做成独立 Activity 时，返回主界面要靠第 0.11 节的 `settingsEpoch` 触发重读，不要指望 `remember` 自己刷新。
- 具体实现见 `references/theme-and-icons.md` 与 `assets/MiuixAppTheme.kt`。

## 12. 布局顺序检查

1. `MiuixTheme` 在最外层。
2. `Scaffold` 在其内，`topBar`/`bottomBar` 用 Miuix 组件。
3. 弹层与 `popupHost` 匹配：Overlay 保留默认，Window 可关闭。
4. 页面内容处理 `paddingValues`，顶部 inset 放 `contentPadding`。
5. 列表滚动与顶栏滚动行为绑定一次；`overScrollVertical()` 与 `overscrollEffect = null` 成对。
6. 卡片间距统一 `firstCard` / `nextCard`。
7. 四标签形态下：backdrop 只有一层、BackHandler 只有一个、Pager 的 `overscrollEffect = null` 不漏。
