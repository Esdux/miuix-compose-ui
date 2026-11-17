package com.example.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 候选列表的双流模型。
 *
 * - [allEntries]：全量，只用来判断「有没有数据 / 要不要显示加载中」。
 * - [visibleEntries]：过滤后，真正渲染的列表。
 *
 * 合成一个流时，搜不到结果就分不清是「没数据」还是「被筛掉了」。
 *
 * 过滤在 ViewModel 里做，不在 UI 里 derivedStateOf { list.filter {} }：
 * 列表可能有上千项，放在组合里过滤会随每次重组重跑。
 */
class ListViewModel : ViewModel() {

    private val _allEntries = MutableStateFlow<List<ListEntry>>(emptyList())
    private val _query = MutableStateFlow("")
    private val _category = MutableStateFlow(0)
    private val _loaded = MutableStateFlow(false)
    private val _failed = MutableStateFlow(false)

    val query: StateFlow<String> = _query
    val category: StateFlow<Int> = _category
    val loaded: StateFlow<Boolean> = _loaded
    val failed: StateFlow<Boolean> = _failed

    val visibleEntries: StateFlow<List<ListEntry>> =
        combine(_allEntries, _query, _category) { all, q, cat ->
            all.asSequence()
                .filter { entry -> matchesCategory(entry, cat) }
                .filter { entry ->
                    if (q.isEmpty()) true
                    else entry.title.contains(q, ignoreCase = true) ||
                        entry.id.contains(q, ignoreCase = true)
                }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    /** 返回 Job，调用方 join() 之后才收下拉指示器。 */
    fun refresh(): Job = viewModelScope.launch {
        _failed.value = false
        val result = withContext(Dispatchers.IO) {
            runCatching { loadEntries() }.getOrNull()
        }
        if (result != null) {
            _allEntries.value = result
            _loaded.value = true
        } else {
            _failed.value = true
            if (!_loaded.value) _loaded.value = true   // 别让加载态永远转下去
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /**
     * 同一维度切换保留搜索词（用户往往是在同一批词里换范围）；
     * 换数据源时才清空。这里都是同一维度，所以保留。
     */
    fun onCategoryChange(value: Int) {
        if (_category.value == value) return
        _category.value = value
        refresh()
    }

    private fun matchesCategory(entry: ListEntry, category: Int): Boolean = when (category) {
        1 -> entry.tag != null
        2 -> entry.tag == null
        else -> true
    }

    /** 换成你的真实数据源。IO 线程，别在组合里调。 */
    private fun loadEntries(): List<ListEntry> = emptyList()
}
