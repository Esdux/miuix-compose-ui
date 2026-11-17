package com.example.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.ui.util.DshIconSize
import com.example.app.ui.util.DshSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.window.WindowListPopup

// 分组卡的三种形状：提到文件顶层，避免每次重组 new 一个 RoundedCornerShape
private val GroupTopShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val GroupBottomShape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)

/**
 * 应用列表页 —— 照 Cirno 的 AppPage 形态：搜索 + 分类筛选 + 图标列表 + 下拉刷新。
 *
 * 与 [ListPage] 的差别只有一处：列表项左侧多一个**应用图标**。
 * 其余规则完全一致（一整块卡切成条、筛选在 ViewModel 派生、加载态整块替换），
 * 因为用户眼里这两种列表是同一类东西，长得不一样反而是设计错误。
 *
 * 图标为什么是 ImageBitmap 而不是 Drawable：见 [AppEntry.icon] 的注释。
 */
@Composable
fun AppListPage(
    isBlurEnabled: Boolean,
    bottomPadding: Dp,
    onEntryClick: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppListViewModel = viewModel(),
) {
    val apps by viewModel.visibleApps.collectAsStateWithLifecycle()
    val allApps by viewModel.allApps.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshing) {
        if (!refreshing) return@LaunchedEffect
        // join() 是必须的：不 join 指示器会在数据回来之前就收了，用户以为没刷新
        viewModel.refresh().join()
        refreshing = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 搜索栏在列表外面，不做 sticky item：sticky 项会跟着滚走
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchBar(
                modifier = Modifier.weight(1f),
                inputField = {
                    InputField(
                        query = query,
                        onQueryChange = viewModel::onQueryChange,
                        onSearch = { keyboard?.hide() },   // 只收键盘，不再查一遍
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        label = "搜索应用",
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
                expanded = searchExpanded,
                onExpandedChange = { searchExpanded = it },
            ) {}
            FilterButton(filter = filter, onSelected = viewModel::onFilterChange)
        }

        // 标题写数量：几百个应用里用户需要知道「筛完还剩几个」，
        // 否则改筛选后列表变短，他不知道是被筛掉了还是加载失败。
        SmallTitle(text = "应用 · " + apps.size)

        Box(modifier = Modifier.weight(1f)) {
            AppListBody(
                loaded = loaded,
                apps = apps,
                isEmptyOverall = allApps.isEmpty(),
                failed = failed,
                bottomPadding = bottomPadding,
                refreshing = refreshing,
                listState = listState,
                pullState = pullState,
                onRefresh = { refreshing = true },
                onEntryClick = onEntryClick,
            )
        }
    }
}

/**
 * 列表主体：加载态与列表是兄弟节点，交叉淡入淡出；不塞进 LazyColumn 的第一项。
 *
 * 单独抽出来是为了脱离外层 Column 的 ColumnScope —— AnimatedVisibility 在 Column 里
 * 会被解析成 ColumnScope 扩展重载，在 Box 里没有那个隐式接收者就编译不过。
 */
@Composable
private fun AppListBody(
    loaded: Boolean,
    apps: List<AppEntry>,
    isEmptyOverall: Boolean,
    failed: Boolean,
    bottomPadding: Dp,
    refreshing: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    pullState: top.yukonga.miuix.kmp.basic.PullToRefreshState,
    onRefresh: () -> Unit,
    onEntryClick: (AppEntry) -> Unit,
) {
    AnimatedVisibility(visible = loaded, enter = fadeIn(), exit = fadeOut()) {
        if (apps.isEmpty()) {
            AppEmptyHint(
                // 三种空要分开说：读失败 / 一个应用都没有 / 筛掉了。
                // 同一句「暂无数据」会让用户不知道该清搜索还是该去装应用。
                failed = failed,
                isEmptyOverall = isEmptyOverall,
                bottomPadding = bottomPadding,
                onRetry = onRefresh,
            )
        } else {
            PullToRefresh(
                isRefreshing = refreshing,
                onRefresh = onRefresh,
                pullToRefreshState = pullState,
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 12.dp, bottom = bottomPadding + 12.dp),
                ) {
                    itemsIndexed(
                        items = apps,
                        // key 必须复合：多用户下同一个包名会出现多次
                        key = { _, app -> app.key },
                    ) { index, app ->
                        AppGroupedRow(
                            app = app,
                            isFirst = index == 0,
                            isLast = index == apps.lastIndex,
                            isOnly = apps.size == 1,
                            onClick = { onEntryClick(app) },
                        )
                    }
                }
            }
        }
    }

    AnimatedVisibility(visible = !loaded, enter = fadeIn(), exit = fadeOut()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            InfiniteProgressIndicator()
        }
    }
}

/**
 * 一块卡切成条：外层只留水平 12dp，块内相邻项之间无缝。
 * 单项是孤例，用完整圆角卡；多项时首项圆上、末项圆下、中间直角。
 */
@Composable
private fun AppGroupedRow(
    app: AppEntry,
    isFirst: Boolean,
    isLast: Boolean,
    isOnly: Boolean,
    onClick: () -> Unit,
) {
    if (isOnly) {
        Card(
            modifier = Modifier.padding(horizontal = 12.dp),
            cornerRadius = 16.dp,
            colors = CardDefaults.defaultColors(color = colorScheme.surfaceContainer),
            pressFeedbackType = PressFeedbackType.Tilt,
            showIndication = true,
            onClick = onClick,
        ) {
            AppRowContent(app)
        }
        return
    }

    val shape = when {
        isFirst -> GroupTopShape
        isLast -> GroupBottomShape
        else -> RectangleShape
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(shape)
            .background(colorScheme.surfaceContainer, shape)
            .clickable(onClick = onClick),
    ) {
        AppRowContent(app)
    }
}

/**
 * 行内容四槽位：图标 → 名称 → 状态短语 → 标识，右侧最多一个标签。
 *
 * 58dp 图标 + 16dp 间距是 Cirno 的 AppItemCompose 钉死的两个数：
 * 58 是 HyperOS 图标在列表里的标准边长，16 是它与文字之间的呼吸。
 */
@Composable
private fun AppRowContent(app: AppEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        app.icon?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(DshIconSize.ListIcon).padding(end = DshSpacing.CardInside),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            MiuixText(
                text = app.appName,
                style = MiuixTheme.textStyles.title4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // summary 是「标识 / 唯一 id」，不是给人读的描述。
            //
            // **包名不截断，允许换行。** 这里踩过一次：为了保住单行整齐，加了
            // maxLines = 1 + Ellipsis，结果 com.mfcloudcalculate.networkdisk 变成
            // com.mfcloudcalculate.ne… —— 用户看不出这是哪个应用，而这正是 summary
            // 唯一的作用。多一行高度换来「看得全」，比整齐重要。
            MiuixText(
                text = app.packageName + if (app.userId != 0) "#" + app.userId else "",
                style = MiuixTheme.textStyles.footnote1,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
        if (app.isSystem) {
            Spacer(Modifier.width(12.dp))
            StatusTag(
                TagSpec(
                    label = "系统",
                    background = Color(60, 179, 113),
                    contentColor = Color.White,
                ),
            )
        }
        if (!app.enabled) {
            Spacer(Modifier.width(12.dp))
            StatusTag(
                TagSpec(
                    label = "已停用",
                    background = Color(0xFFD13636),
                    contentColor = Color.White,
                ),
            )
        }
    }
}


/**
 * 分类筛选放在搜索栏右侧，用 WindowListPopup + DropdownImpl。
 * 切换给一次 Confirm 触感，然后关弹层 —— 少了触感，切换是「无声的」，用户不确定点中了没。
 */
@Composable
private fun FilterButton(
    filter: AppListViewModel.Filter,
    onSelected: (AppListViewModel.Filter) -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var holdDown by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    // values() 而不是 entries：后者要 Kotlin 1.9+ 的 enum entries 支持，
    // 模板要能在更老的 AGP/Kotlin 组合下也编得过。
    val options = AppListViewModel.Filter.values().toList()

    IconButton(
        onClick = { show = true; holdDown = true },
        holdDownState = holdDown,     // 不配这个，按下去图标没有回弹，像没点到
    ) {
        Icon(
            imageVector = MiuixIcons.Filter,
            contentDescription = "筛选：" + filter.label,
            // 筛选生效时染主色：用户一眼知道「现在看到的不是全部」
            tint = if (filter == AppListViewModel.Filter.ALL) colorScheme.onBackground else colorScheme.primary,
        )
    }

    WindowListPopup(
        show = show,
        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = { show = false },
        onDismissFinished = { holdDown = false },
        content = {
            val dismiss = LocalDismissState.current
            ListPopupColumn {
                options.forEachIndexed { index, option ->
                    key(index) {
                        DropdownImpl(
                            text = option.label,
                            optionSize = options.size,      // 必须等于总数，它决定高亮条宽度
                            isSelected = filter == option,
                            onSelectedIndexChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onSelected(option)
                                dismiss?.invoke()
                            },
                            index = index,
                        )
                    }
                }
            }
        },
    )
}

/** 三种空要分开说，否则用户永远不知道该做什么。 */
@Composable
private fun AppEmptyHint(
    failed: Boolean,
    isEmptyOverall: Boolean,
    bottomPadding: Dp,
    onRetry: () -> Unit,
) {
    val text = when {
        failed -> "读取失败，下拉重试"
        isEmptyOverall -> "没有读到任何应用"
        else -> "没有匹配的应用"
    }
    Box(
        modifier = Modifier.fillMaxSize().padding(bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MiuixText(
                text = text,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = colorScheme.onSurfaceVariantSummary,
            )
            if (failed) {
                Spacer(Modifier.height(12.dp))
                MiuixText(
                    modifier = Modifier.clickable(onClick = onRetry),
                    text = "重试",
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = colorScheme.primary,
                )
            }
        }
    }
}
