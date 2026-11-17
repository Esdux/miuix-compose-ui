package com.example.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.ui.util.DshIconSize
import com.example.app.ui.util.DshSpacing
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
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
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.window.WindowListPopup

// 分组卡的三种形状：提到文件顶层，避免每次重组 new 一个 RoundedCornerShape
private val GroupTopShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val GroupBottomShape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)

/**
 * 列表项的最小模型。换成你自己的类型，但要保证 [id] 唯一且稳定。
 *
 * [icon] 是**已经解码好的位图**，不是 Drawable：
 * Drawable 不能在组合里直接画，每次重组都 toBitmap 会掉帧；
 * 在 ViewModel 的 IO 线程里解好、放进 ListEntry，UI 只管画。
 * 不需要图标（纯文字列表）时留 null，左侧槽位自动收起，行高不会因此变矮。
 */
data class ListEntry(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val summary: String? = null,
    val tag: TagSpec? = null,
    val icon: ImageBitmap? = null,
)


/**
 * 分类筛选放在顶栏右上角，用 WindowListPopup + DropdownImpl。
 * 切换给一次 Confirm 触感，然后关弹层 —— 少了触感，切换是「无声的」，用户不确定点中了没。
 */
@Composable
private fun CategoryButton(category: Int, onSelected: (Int) -> Unit) {
    var show by remember { mutableStateOf(false) }
    var holdDown by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val options = listOf("全部", "已启用", "未启用")

    IconButton(
        onClick = { show = true; holdDown = true },
        holdDownState = holdDown,     // 不配这个，按下去图标没有回弹，像没点到
    ) {
        Icon(
            imageVector = MiuixIcons.More,
            contentDescription = "Filter",
            tint = colorScheme.onBackground,
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
                options.forEachIndexed { index, label ->
                    key(index) {
                        DropdownImpl(
                            text = label,
                            optionSize = options.size,      // 必须等于总数，它决定高亮条宽度
                            isSelected = category == index,
                            onSelectedIndexChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onSelected(it)
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

/** 空与失败要分开说：同一句文案覆盖两种状态，用户永远不知道该做什么。 */
@Composable
private fun EmptyHint(failed: Boolean, bottomPadding: androidx.compose.ui.unit.Dp, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MiuixText(
                text = if (failed) "读取失败，下拉重试" else "没有匹配的条目",
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

/**
 * 通用列表页 —— 「候选列表 / 搜索结果 / 带筛选的清单」都长这样。
 *
 * 形态与 [AppListPage] 同构，只差左侧图标：那边是应用图标，这边是可选的缩略图。
 * 用户眼里这两种列表是同一类东西，长得不一样反而是设计错误。
 *
 * 本函数只负责「搜索栏 + 列表」，外壳由调用方套 [com.example.app.ui.util.PageScaffoldColumn]：
 * 它内部要放一个自己会滚的 LazyColumn，塞进 PageScaffold 的 LazyColumn 会双层滚动打架。
 */
@Composable
fun ListPage(
    entries: List<ListEntry>,
    loaded: Boolean,
    failed: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    category: Int,
    onCategoryChange: (Int) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onEntryClick: (ListEntry) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(0f),
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = DshSpacing.Block),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchBar(
                modifier = Modifier.weight(1f),
                inputField = {
                    InputField(
                        query = query,
                        onQueryChange = onQueryChange,
                        onSearch = { keyboard?.hide() },   // 只收键盘，不再查一遍
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        label = "搜索",
                        leadingIcon = {
                            Icon(
                                modifier = Modifier
                                    .padding(start = DshSpacing.Block, end = DshSpacing.Compact)
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
            CategoryButton(category = category, onSelected = onCategoryChange)
        }

        // 标题带数量：改筛选后列表变短，用户需要知道是被筛掉了还是加载失败
        SmallTitle(text = "条目 · " + entries.size)

        Box(modifier = Modifier.weight(1f)) {
            ListBody(
                loaded = loaded,
                entries = entries,
                failed = failed,
                bottomPadding = bottomPadding,
                refreshing = refreshing,
                listState = listState,
                pullState = pullState,
                onRefresh = onRefresh,
                onEntryClick = onEntryClick,
            )
        }
    }
}

/**
 * 列表主体：加载态与列表是兄弟节点，交叉淡入淡出；不塞进 LazyColumn 的第一项。
 *
 * 单独抽出来还有一个编译上的理由：AnimatedVisibility 在 Column 里会被解析成
 * ColumnScope 的扩展重载，放在 Box 里没有那个隐式接收者就编译不过。
 */
@Composable
private fun ListBody(
    loaded: Boolean,
    entries: List<ListEntry>,
    failed: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    refreshing: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    pullState: top.yukonga.miuix.kmp.basic.PullToRefreshState,
    onRefresh: () -> Unit,
    onEntryClick: (ListEntry) -> Unit,
) {
    AnimatedVisibility(visible = loaded, enter = fadeIn(), exit = fadeOut()) {
        if (entries.isEmpty()) {
            EmptyHint(failed = failed, bottomPadding = bottomPadding, onRetry = onRefresh)
        } else {
            PullToRefresh(
                isRefreshing = refreshing,
                onRefresh = onRefresh,
                pullToRefreshState = pullState,
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = DshSpacing.Block, bottom = bottomPadding + DshSpacing.Block),
                ) {
                    itemsIndexed(
                        items = entries,
                        // key 必须稳定：没有 key 时增删一项会让整列位置错位、动画乱飞
                        key = { _, entry -> entry.id },
                    ) { index, entry ->
                        GroupedRow(
                            entry = entry,
                            isFirst = index == 0,
                            isLast = index == entries.lastIndex,
                            isOnly = entries.size == 1,
                            onClick = { onEntryClick(entry) },
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
 * 一整块卡切成条：外层只留水平 12dp，块内相邻项之间无缝。
 * 单项是孤例，用完整圆角卡；多项时首项圆上、末项圆下、中间直角。
 *
 * 为什么不用一列 Card：Card 之间有缝，看起来是「一堆独立的卡」；
 * 而 HyperOS 的设置页/列表页是「一整块被切成行」，缝只在块的外缘。
 */
@Composable
private fun GroupedRow(
    entry: ListEntry,
    isFirst: Boolean,
    isLast: Boolean,
    isOnly: Boolean,
    onClick: () -> Unit,
) {
    if (isOnly) {
        Card(
            modifier = Modifier.padding(horizontal = DshSpacing.Block),
            cornerRadius = 16.dp,
            colors = CardDefaults.defaultColors(color = colorScheme.surfaceContainer),
            pressFeedbackType = PressFeedbackType.Tilt,
            showIndication = true,
            onClick = onClick,
        ) {
            EntryContent(entry)
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
            .padding(horizontal = DshSpacing.Block)
            .clip(shape)
            .background(colorScheme.surfaceContainer, shape)
            .clickable(onClick = onClick),
    ) {
        EntryContent(entry)
    }
}

/**
 * 行内容：图标 → 标题 → 副标题 → 摘要，右侧最多一个标签。
 *
 * 三个文字槽位的分工是固定的，别混用：
 * - title：**是什么**（应用名、文件名、设置项名）
 * - subtitle：**怎么样**（状态短语，一句话）
 * - summary：**标识 / 唯一 id**（包名、路径），不是给人读的描述
 */
@Composable
private fun EntryContent(entry: ListEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = DshSpacing.Block, vertical = DshSpacing.Compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entry.icon?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(DshIconSize.ListIcon).padding(end = DshSpacing.CardInside),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            MiuixText(
                text = entry.title,
                style = MiuixTheme.textStyles.title4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.subtitle?.let {
                MiuixText(
                    text = it,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            entry.summary?.let {
                MiuixText(
                    text = it,
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        entry.tag?.let {
            Spacer(Modifier.width(DshSpacing.Block))
            StatusTag(it)
        }
    }
}