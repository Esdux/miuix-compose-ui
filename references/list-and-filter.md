# 候选列表与筛选列表规范

适用场景：一屏要列出 N 个同类可选项（应用、进程、文件、设备、字体、曲目、插件、订阅源……），
并且用户需要**缩小范围**才能找到目标。

参考实现：Cirno（`nep.timeline.cirno`）的 `AppPage.kt` / `AppListViewModel.kt` /
`AppItemCompose.kt` / `RunningProcessDialog.kt`，以及 vcut_lower / HyperMusicCover 的分组卡。

---

## 0. 三条不可协商的规则

| # | 规则 | 违反后的表现 |
|---|---|---|
| 1 | 列表项不是「一张张小卡」，而是**一整块卡被切成条** | 每项都是独立圆角卡 → 项间出现 12dp 空隙，视觉上像一堆碎片，HyperOS 里没有这种形态 |
| 2 | 筛选与搜索在 **ViewModel/数据层** 派生，不在 UI 里 `filter` | 每次重组都重跑一遍过滤，输入一个字符卡一次；且列表对象反复重建，滚动位置丢失 |
| 3 | 加载态是**整块替换**，不是列表里塞占位项 | 列表先空后跳、高度抖动、滚动条乱跳 |

---

## 1. 双流模型：全量流 + 过滤流

```kotlin
class AppListViewModel : ViewModel() {
    // 全量：只用于判断"有没有数据 / 要不要显示加载中"
    private val _filterApps = MutableStateFlow<List<AppItem>>(emptyList())
    // 过滤后：真正渲染的列表
    private val _cacheFilterApps = MutableStateFlow<List<AppItem>>(emptyList())

    private val _search = MutableStateFlow("")
    private val _type = MutableStateFlow(0)
    private val _updatedApps = MutableStateFlow(true)      // 加载中 = false

    val search: StateFlow<String> = _search
    val type: StateFlow<Int> = _type
    val filterApps: StateFlow<List<AppItem>> = _filterApps
    val cacheFilterApps: StateFlow<List<AppItem>> = _cacheFilterApps
    val updatedApps: StateFlow<Boolean> = _updatedApps

    private fun autoUpdateCacheFilterApps() {
        viewModelScope.launch {
            combine(_filterApps, _search, _type) { apps, search, type ->
                apps.asSequence()
                    .filter { /* 分类条件 */ }
                    .filter {
                        if (search.isNotEmpty())
                            it.appName.contains(search, ignoreCase = true) ||
                            it.packageName.contains(search, ignoreCase = true)
                        else true
                    }
                    .toList()
            }.collect { _cacheFilterApps.value = it }
        }
    }

    init { autoUpdateCacheFilterApps() }
}
```

要点：

- **两个流各有明确职责**。`filterApps` 回答「这一类到底有没有东西」，`cacheFilterApps` 回答「现在该画哪些」。合成一个流时，搜不到结果就分不清是「没数据」还是「被筛掉了」。
- **`combine` 而不是在 UI 里 `derivedStateOf { list.filter {} }`**。列表可能有上千项，放在组合里过滤会在每次重组时重跑；放在 ViewModel 里只随三个输入变化。
- **`.asSequence()`**：连续两个 `filter` 不产生中间 List。
- 搜索**同时匹配显示名与标识**（应用名 + 包名）。用户记得哪个就搜哪个，只匹配一个会让另一半人以为搜不到。
- `ignoreCase = true` 是默认要求，不是可选项。

### 何时用「首次加载」标记

分类之间数据来源不同、切换代价差很大时，才加 `hasLoadedMonitorOnce` 这类一次性标记：

```kotlin
private val _hasLoadedMonitorOnce = MutableStateFlow(false)
// getFilterApps 里：if (type == 2) _hasLoadedMonitorOnce.value = true
```

用于「这个分类第一次进入才去抓（要 root、要遍历 /proc），之后靠下拉刷新」。**不要**给所有分类都加，否则回到列表时看到的是旧数据且没有任何提示。

---

## 2. 页面状态的聚合

```kotlin
data class AppListScreenState(
    val filteredApps: List<AppItem>,
    val loadingApps: List<AppItem>,
    val searchValue: String,
    val type: Int,
    val updatedApps: Boolean,
)

@Composable
fun rememberAppListScreenState(viewModel: AppListViewModel): AppListScreenState {
    val filteredApps by viewModel.cacheFilterApps.collectAsStateWithLifecycle()
    val loadingApps by viewModel.filterApps.collectAsStateWithLifecycle()
    val searchValue by viewModel.search.collectAsStateWithLifecycle()
    val type by viewModel.type.collectAsStateWithLifecycle()
    val updatedApps by viewModel.updatedApps.collectAsStateWithLifecycle()
    // ... isActive 见下节
    return AppListScreenState(filteredApps, loadingApps, searchValue, type, updatedApps)
}
```

- `collectAsStateWithLifecycle()`，不是 `collectAsState()`。后者在页面不可见时仍然收集，后台空转。
- 页面只拿**一个** `data class`，不拿五个散装变量。散装变量一多，改签名时到处漏。

### 回前台重取一次（不是轮询）

```kotlin
val lifecycleOwner = LocalLifecycleOwner.current
val isActive = remember { mutableStateOf(false) }

DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> isActive.value = true
            Lifecycle.Event.ON_STOP -> isActive.value = false
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}

LaunchedEffect(isActive.value, type) { viewModel.getFilterApps() }
```

这里与「状态轮询」不同：**要的是每次回到前台取一次快照**，不是 while(true) + delay。
用 `repeatOnLifecycle(RESUMED) { while (true) { ... delay } }` 反而错——从别的页面返回会立刻再打一次数据源，列表闪一下。

**`LaunchedEffect` 的 key 里必须有 `type`**：切分类必须重取，否则切过去看到的是上一类的列表。

---

## 3. 分组卡：一块卡切成条

```kotlin
private val AppListTopShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val AppListBottomShape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)

itemsIndexed(
    items = appItems,
    key = { _, item -> item.packageName + "#" + item.userId },
) { i, item ->
    if (appCount == 1) {
        // 单项：完整 Card（四个角都圆）
        CirnoCard(modifier = Modifier.padding(horizontal = 12.dp)) {
            AppItemCompose(item)
        }
    } else {
        val isFirst = i == 0
        val isLast = i == appCount - 1
        val shape = when {
            isFirst -> AppListTopShape
            isLast -> AppListBottomShape
            else -> RectangleShape
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(shape)
                .cirnoCardBackground(shape, colorScheme.surfaceContainer),
        ) {
            AppItemCompose(item)
        }
    }
}

item { Spacer(modifier = Modifier.height(12.dp)) }
```

四条硬规则：

1. **`padding(horizontal = 12.dp)` 只在外层，块内相邻项之间不留间距**。留了就不是「一条」，是「一堆」。
2. **首项只圆上两角，末项只圆下两角，中间是 `RectangleShape`**。这一条决定了它读起来是「一个组」。
3. **只有一项时用完整圆角卡**（`CirnoCard`），不要用 TopShape 或 BottomShape——单项是孤例，不是组的首/末。
4. **末尾补一个 12dp 的 Spacer item**，否则最后一项会贴住底栏。

### key 必须复合

`key = { _, item -> item.packageName + "#" + item.userId }`

同一个包可能有多个用户（分身/工作资料），只用包名会让 Compose 认为是同一个 item，滚动、动画、状态全部错位。
**任何列表的 key 都要包含「可能重复的那个维度」**：包名+用户、文件名+路径、曲名+歌手、设备名+序列号。

---

## 4. 列表项内容排班

列表项只有一个信息模板，不要每项长得不一样。

```kotlin
CustomBasicComponent(
    title = app.appName,
    subtitle = subtitle,                       // 状态短语，可为 null
    subtitleMaxLines = 1,
    subtitleOverflow = TextOverflow.Ellipsis,
    subtitleColor = subtitleColor,
    summary = app.packageName + if (app.userId != 0) "#" + app.userId else "",
    leftAction = {
        Image(
            painter = rememberDrawablePainter(drawable = app.appIcon),
            contentDescription = app.appName,
            modifier = Modifier.size(58.dp).padding(end = 16.dp),
        )
    },
    rightActions = { if (configured) StatusTag(label, backgroundColor, contentColor) },
    onClick = { AppContext.enterAppPage(app) },
    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
)
```

槽位定义（从左到右）：

| 槽位 | 内容 | 规格 |
|---|---|---|
| `leftAction` | 图标 / 头像 / 缩略图 | 58dp + `padding(end = 16.dp)`，实际占位 74dp |
| `title` | 主名称 | `MiuixTheme.textStyles.title4`（18sp） |
| `subtitle` | **状态短语**，一句话 | `textStyles.subtitle`（14sp Bold），颜色按状态 |
| `summary` | 标识 / 路径 / 唯一 id | `textStyles.footnote1`（13sp），`onSurfaceVariantSummary` |
| `rightActions` | 标签 / 开关 / 箭头 | 单个 `StatusTag` 或 `Switch` |

- `heightIn(min = 56.dp)`：由 `CustomBasicComponent` 内部保证，不要在外面再写死高度。
- **`subtitle` 是状态，`summary` 是标识**。写反了用户读起来别扭：「已冻结」应该在名字下面第一行，「com.xxx#10」应该在最后一行小字。
- `subtitle` 为 `null` 时整行不占位，不要把空字符串塞进去。

### 状态标签

```kotlin
@Composable
fun StatusTag(label: String, backgroundColor: Color, contentColor: Color) {
    Box(modifier = Modifier.background(backgroundColor, RoundedCornerShape(6.dp))) {
        Text(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            text = label,
            color = contentColor,
            fontSize = 9.sp,
            fontWeight = FontWeight(750),
            maxLines = 1,
            softWrap = false,
        )
    }
}
```

固定参数：圆角 6dp、内边距 4×2dp、字号 9sp、字重 750、单行不换行。
**颜色只承担语义，不承担装饰**：

| 语义 | 亮色 | 暗色 |
|---|---|---|
| 危险 / 黑名单 / 阻断 | `Color(0xFFD13636)` | 同左 |
| 白名单 / 放行 | `Color.Black` 底 + 白字 | `Color.White` 底 + 黑字 |
| 其它已配置 | `Color(60, 179, 113)` | 同左 |

一个列表项**最多一个** `StatusTag`。要表达两个状态就换成一个更准确的短语，不要并排两个标签。

### 状态短语优先于标签

```kotlin
if (configured) {
    if (app.backgroundLevel == 1) subtitle = stringResource(R.string.direct_app)
    else if (app.backgroundLevel == 2) subtitle = stringResource(R.string.foreground_service)
    else if (app.idle) subtitle = stringResource(R.string.battery_opt)
}
```

先判断「用户最想知道的那一条」，命中就停。串成 `if/else if` 链而不是拼字符串——拼出来的短语长度不可控，会挤掉 summary。

---

## 5. 搜索栏

```kotlin
SearchBar(
    modifier = Modifier.padding(bottom = 6.dp),
    inputField = {
        InputField(
            query = searchValue,
            onQueryChange = { viewModel.updateByQuery(it, type) },
            onSearch = { keyboardController?.hide() },
            expanded = expanded,
            onExpandedChange = { expanded = it },
            label = stringResource(R.string.search),
            leadingIcon = {
                Icon(
                    modifier = Modifier
                        .padding(start = 12.dp, end = 8.dp)
                        .size(20.dp)
                        .alpha(0.4f),
                    imageVector = MiuixIcons.Basic.Search,
                    tint = colorScheme.onSurfaceContainer,
                    contentDescription = "Search",
                )
            },
        )
    },
    outsideEndAction = {
        Text(
            modifier = Modifier.padding(end = 12.dp).clickable(
                interactionSource = null,
                indication = null,
            ) {
                expanded = false
                viewModel.updateByQuery("", type)
            },
            text = stringResource(R.string.cancel),
            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold),
            color = colorScheme.primary,
        )
    },
    expanded = expanded,
    onExpandedChange = { expanded = it },
) { /* 无内容 */ }
```

- `leadingIcon` 固定三件套：`padding(start = 12.dp, end = 8.dp)` + `size(20.dp)` + `alpha(0.4f)`。少一个都会显得图标浮在框外。
- `tint = colorScheme.onSurfaceContainer`，不是 `onSurface`——后者在浅底上太重。
- **`outsideEndAction` 是「取消」**，17sp Bold + `colorScheme.primary`，点了清空查询并收起。`interactionSource = null, indication = null` 是必须的，否则文字下面会有一圈水波纹。
- `onSearch` 只做 `keyboardController?.hide()`，不触发查询——查询已经在 `onQueryChange` 里做了，回车再查一遍会闪。
- `expanded` 用 `rememberSaveable`：转屏后搜索栏不该自己收起。
- 搜索栏**放在列表外面**（`Column` 里 LazyColumn 之上），不要做成 LazyColumn 的 sticky item。

---

## 6. 分类筛选：右上角弹层

```kotlin
@Composable
fun TopBarActions() {
    val showTopPopup = remember { mutableStateOf(false) }
    val topPopupHoldDown = remember { mutableStateOf(false) }
    val type by appListViewModel.type.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current

    IconButton(
        onClick = { showTopPopup.value = true; topPopupHoldDown.value = true },
        holdDownState = topPopupHoldDown.value,
    ) {
        Icon(
            imageVector = MiuixIcons.More,
            contentDescription = "WindowListPopup",
            tint = colorScheme.onBackground,
        )
    }

    WindowListPopup(
        show = showTopPopup.value,
        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = { showTopPopup.value = false },
        onDismissFinished = { topPopupHoldDown.value = false },
        content = {
            val state = LocalDismissState.current
            val items = stringArrayResource(R.array.dropdownOptions)
            ListPopupColumn {
                items.forEachIndexed { index, string ->
                    key(index) {
                        DropdownImpl(
                            text = string,
                            optionSize = items.size,
                            isSelected = type == index,
                            onSelectedIndexChange = { selectedIdx ->
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                appListViewModel.updateByQuery(type = selectedIdx)
                                state?.invoke()
                            },
                            index = index,
                        )
                    }
                }
            }
        },
    )
}
```

- 触发按钮：`MiuixIcons.More`，**必须配 `holdDownState`**，否则按下去图标没有回弹，像没点到。`onDismissFinished` 里复位。
- `popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider` + `Align.TopEnd`：弹层贴右上角展开，这是 HyperOS 的习惯位置。
- 选项用 `DropdownImpl(text, optionSize, isSelected, onSelectedIndexChange, index)`，**`optionSize` 必须等于选项总数**——它决定选中项的高亮条宽度。
- 切换分类**给一次 `HapticFeedbackType.Confirm`**，然后 `state?.invoke()` 关弹层。少了触感反馈，切换是「无声的」，用户不确定点中了没。
- 分类数 ≥ 3 且互斥时用这个弹层；只有 2 个分类用 `TabRow`；分类之间可以多选时用 `FilterChip` 排（miuix 没有 chip，用 `Button` 的 `secondary` 形态）。

### 分类切换要清空搜索吗

- **同一维度切换**（全部 / 已冻结 / 系统应用）：**保留**搜索词，用户往往是在同一批词里换范围。
- **换数据源**（应用列表 → 进程列表）：**清空**，两个列表的字段不同，留着旧词只会得到空结果。

---

## 7. 三态：加载 / 空 / 错误

```kotlin
AnimatedVisibility(visible = isLoading.isNotEmpty() && updatedApps, enter = fadeIn(), exit = fadeOut()) {
    PullToRefresh(...) { /* 列表 */ }
}

AnimatedVisibility(visible = !updatedApps, enter = fadeIn(), exit = fadeOut()) {
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            InfiniteProgressIndicator(modifier = Modifier.align(Alignment.CenterVertically))
        }
    }
}
```

- 加载态与列表是**兄弟节点**，靠 `AnimatedVisibility` 交叉淡入淡出。不要把 loading 塞进 `LazyColumn` 的第一项。
- `updatedApps = false` 时**整块列表不渲染**，不是只盖一层遮罩。遮罩会让用户能滑到底下的空列表。
- 加载指示器用 `InfiniteProgressIndicator`，居中，不写字。写字反而让人盯着读。
- 列表为空**不是加载态**：`updatedApps == true` 且 `filteredApps.isEmpty()` 时显示空提示。

### 空与失败要分开说

```kotlin
text = stringResource(
    if (state.failed) R.string.running_process_load_failed
    else R.string.running_process_empty,
)
```

- 空：「没有匹配的应用」→ 用户该去改搜索词。
- 失败：「读取失败，下拉重试」→ 用户该下拉或检查权限。

**同一句文案覆盖两种状态，用户永远不知道该做什么。**

### 空态的两个层级

| 层级 | 场景 | 表现 |
|---|---|---|
| 整体空 | 这个分类本身没数据 | 整屏居中一行灰字 |
| 筛选后空 | 有数据但搜索/筛选筛没了 | **搜索框下方**居中一行灰字，搜索框保持可见 |

第二种千万别整屏替换——搜索框被换掉，用户就没法改查询了。

---

## 8. 下拉刷新

```kotlin
val pullToRefreshState = rememberPullToRefreshState()
var isRefreshing by remember { mutableStateOf(false) }

LaunchedEffect(isRefreshing) {
    if (!isRefreshing) return@LaunchedEffect
    viewModel.update().join()   // join：等真正取完再收指示器
    isRefreshing = false
}

PullToRefresh(
    isRefreshing = isRefreshing,
    onRefresh = { isRefreshing = true },
    pullToRefreshState = pullToRefreshState,
    topAppBarScrollBehavior = scrollBehavior,
    contentPadding = PaddingValues(top = innerPadding.calculateTopPadding() + 12.dp),
) { /* 列表 */ }
```

- **`join()` 是必须的**。`viewModel.update()` 返回 `Job`，不 join 的话指示器在数据回来之前就收了，用户以为没刷新。
- `PullToRefresh` 要拿到 `topAppBarScrollBehavior`，否则下拉时顶栏不跟着收。
- 刷新期间**不清空列表**（`updatedApps` 不动），只转指示器。清空会让整屏闪白。
- `contentPadding.top` 要加 `+ 12.dp`，给刷新指示器留出落点。

---

## 9. 滚动条

```kotlin
@file:OptIn(ExperimentalScrollBarApi::class)

Box(modifier = Modifier.weight(1f)) {
    LazyColumn(state = lazyListState, /* ... */) { /* ... */ }

    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(lazyListState),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        trackPadding = listContentPadding,
    )
}
```

- miuix **不会自动加滚动条**，必须手动挂 `VerticalScrollBar`。
- `trackPadding` **必须传列表的 `contentPadding`**。传默认值时滚动条会一直伸到顶栏和底栏底下，穿出卡片区域。
- `fillMaxHeight()` + `align(Alignment.CenterEnd)`，不要给宽度。
- 列表项数可能 ≤ 3 的页面（设置页、关于页）**不加滚动条**——短列表配滚动条反而显得空。

---

## 10. 列表滚动的修饰器顺序

```kotlin
fun Modifier.pageScrollModifiers(
    enableScrollEndHaptic: Boolean,
    showTopAppBar: Boolean,
    topAppBarScrollBehavior: ScrollBehavior,
): Modifier = this
    .then(if (enableScrollEndHaptic) Modifier.scrollEndHaptic() else Modifier)
    .overScrollVertical()
    .then(if (showTopAppBar) Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection) else Modifier)
    .fillMaxHeight()
```

顺序不能换：

1. `scrollEndHaptic()` —— 滑到边界给一次震动。
2. `overScrollVertical()` —— miuix 的过度滚动效果。**开了它就必须同时给 `LazyColumn` 设 `overscrollEffect = null`**，否则两套回弹叠加，滑到底会「抖两下」。
3. `nestedScroll(...)` —— 把滚动量喂给顶栏，顶栏才会随滚动收起/展开。
4. `fillMaxHeight()` —— 最后。

---

## 11. 弹窗里的列表

```kotlin
@Composable
fun MiuixRunningProcessDialog(app: AppItem, onDismissFinished: () -> Unit) {
    val state = rememberRunningProcesses(app)
    // 调用方在 onDismissFinished 里卸载我们，动画期间必须保持 composed，
    // 所以 show 由本地驱动，不从上往下传。
    var show by remember { mutableStateOf(true) }

    OverlayDialog(
        show = show,
        title = app.appName,
        onDismissRequest = { show = false },
        onDismissFinished = onDismissFinished,
        content = {
            val snapshot = state.snapshot          // ← 必须在 lambda 内读
            val processes = snapshot?.processes.orEmpty()
            // ...
        },
    )
}
```

### 最重要的一条：状态必须在 content lambda 内读

```kotlin
// Fields must be observable and every read must happen inside the dialog body lambda.
// The Miuix dialog content is invoked by an ancestor Scaffold popup host, so a value hoisted
// outside the lambda is only a captured constant for that subtree and never refreshes there.
private class RunningProcessState {
    var snapshot: RunningProcessSnapshot? by mutableStateOf(null)
    var loaded: Boolean by mutableStateOf(false)
    var failed: Boolean by mutableStateOf(false)
}
```

Miuix 的 `OverlayDialog` 内容由**祖先 Scaffold 的 popup host** 调用，不在当前组合位置。
把 `val snapshot = state.snapshot` 写在 `OverlayDialog(...)` 之前，那个子树拿到的是**捕获的常量**，
数据回来了也不刷新，表现为「弹窗一直转圈」。

正确做法：状态对象用 `mutableStateOf` 字段（`by` 委托），**每次读都在 content lambda 里面**。

### 弹窗列表的结构

```kotlin
Column(modifier = Modifier.fillMaxWidth()) {
    // 1) 顶部元信息：一行两段，SpaceBetween，body2
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MiuixText(uidText, fontSize = MiuixTheme.textStyles.body2.fontSize,
                  color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        MiuixText(summaryText, /* 同上 */)
    }

    // 2) 列表：高度上限 + 普通 Column 滚动
    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(horizontal = 24.dp)) {
        when {
            !state.loaded -> /* InfiniteProgressIndicator，vertical 32.dp 居中 */
            processes.isEmpty() -> /* 一行灰字，vertical 32.dp 居中 */
            else -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                processes.forEach { process ->
                    key(process.pid) { MiuixProcessRow(process) }
                }
            }
        }
    }

    // 3) 底部单个主按钮，撑满宽
    MiuixTextButton(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        text = stringResource(R.string.close),
        colors = ButtonDefaults.textButtonColorsPrimary(),
        onClick = { show = false },
    )
}
```

- `heightIn(max = 320.dp)`：弹窗高度上限。不给上限，项一多弹窗就顶到状态栏。
- **弹窗内用 `Column + verticalScroll`，不用 `LazyColumn`**。项数有限（一个包的进程通常 < 20），Lazy 的复用机制反而是负担，且弹窗里嵌套 Lazy 容易和弹窗自身的测量打架。
- `key(process.pid)` 照旧要给。
- 底部只有一个按钮时**撑满宽**（`fillMaxWidth`），不要缩在右边。两个按钮时才横排（见下节）。

### 行内布局

```kotlin
Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(modifier = Modifier.weight(1f)) {
        MiuixText(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            text = process.name,
            style = MiuixTheme.textStyles.title4,
            maxLines = 1,
            softWrap = false,
        )
        MiuixText(
            text = processDetailText(process),      // "pid 1234 · 45MB · 2.10%"
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    MiuixText(
        modifier = Modifier.padding(start = 12.dp),
        text = if (process.frozen) "已冻结" else "运行中",
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = if (process.frozen) FrozenAccent else MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}
```

- **长名字用 `horizontalScroll` + `maxLines = 1, softWrap = false`**，不要 `Ellipsis`。进程名、包名、路径这类标识被截断就没用了，让它能横滑读完。
- 反过来，**描述行（detail）要 Ellipsis**，它是补充信息，截断可以接受。
- 右侧状态文字用颜色区分，不用标签——弹窗里标签太重。

---

## 12. 性能清单

| 检查项 | 做法 | 违反后果 |
|---|---|---|
| 过滤在数据层 | `combine` 派生 | 每次重组重跑过滤 |
| key 复合 | `包名#用户` / `路径` | 滚动位置错乱、动画串位 |
| 列表项内不 new 对象 | 颜色、shape 提到文件顶层 `private val` | 每帧分配 |
| 分组 shape 常量 | `AppListTopShape` 等提到顶层 | 同上 |
| `collectAsStateWithLifecycle` | 不用 `collectAsState` | 后台空转 |
| 弹窗状态在 lambda 内读 | `by mutableStateOf` | 弹窗永不刷新 |
| `overscrollEffect = null` | 配 `overScrollVertical()` | 双重回弹抖动 |
| `trackPadding` | 传列表 contentPadding | 滚动条穿出卡片 |
| 刷新 `join()` | 等 Job 完成 | 指示器早收 |
| 弹窗用 `Column` | 不用 `LazyColumn` | 测量冲突 |
| 长标识横滑 | `horizontalScroll` | 截断后无法辨认 |
| 分类切换重取 | `LaunchedEffect` key 含 `type` | 显示上一类数据 |

---

## 13. 一页纸结论

1. **两个流**：全量流判断有没有数据，过滤流决定画哪些；`combine` 在 ViewModel 里派生。
2. **一块卡切成条**：外层 12dp 水平内边距，块内无缝；首项圆上、末项圆下、中间直角；单项用完整圆角卡。
3. **key 复合**：把可能重复的维度拼进去。
4. **列表项四槽位**：图标 58dp → 名称 → 状态短语 → 标识，右侧最多一个标签。
5. **标签只表语义**：红=危险、白/黑=放行、绿=已配置；9sp、750 字重、6dp 圆角。
6. **搜索栏在外**：图标 20dp + 0.4 alpha，`outsideEndAction` 是取消，回车只收键盘。
7. **分类在右上角**：`WindowListPopup` + `DropdownImpl`，切换给 Confirm 触感并关弹层。
8. **三态是兄弟节点**：加载整块替换列表；空与失败分开说；筛空只替换列表不替换搜索框。
9. **下拉刷新要 join**；刷新不清空列表。
10. **滚动条手动挂**，`trackPadding` 传列表 contentPadding。
11. **弹窗内状态必须在 content lambda 里读**，列表用 `Column + verticalScroll`，高度上限 320dp。
12. **长标识横滑，描述行省略**。
