package com.example.app.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
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
 * 应用列表的数据模型 —— 照 Cirno 的 AppItem 精简。
 *
 * [icon] 是**已解码的 ImageBitmap**，不是 Drawable。
 * 图标解码要读 APK 里的资源，一次几十毫秒；放在组合里做，列表滚动就会一卡一卡。
 * 在 IO 线程解好再发出去，UI 只负责画。
 */
data class AppEntry(
    val packageName: String,
    val userId: Int,
    val appName: String,
    val isSystem: Boolean,
    val enabled: Boolean,
    val icon: ImageBitmap?,
) {
    /** LazyColumn 的 key：包名会重复（多用户），必须拼上 userId。 */
    val key: String get() = packageName + "#" + userId
}

/**
 * 应用列表的双流模型（照 Cirno 的 AppListViewModel）。
 *
 * - [allApps]：全量，只回答「有没有数据」。
 * - [visibleApps]：过滤后，真正渲染的。
 *
 * 合成一条流时，「搜不到」与「一个应用都没有」分不开，
 * 于是空状态只能写一句含糊的「暂无数据」，用户不知道该清搜索还是该去装应用。
 *
 * 过滤放在 ViewModel：几百个应用在组合里 filter，每次重组都重跑一遍。
 */
class AppListViewModel : ViewModel() {

    private val _allApps = MutableStateFlow<List<AppEntry>>(emptyList())
    private val _query = MutableStateFlow("")
    private val _filter = MutableStateFlow(Filter.ALL)
    private val _loaded = MutableStateFlow(false)
    private val _failed = MutableStateFlow(false)

    val query: StateFlow<String> = _query
    val filter: StateFlow<Filter> = _filter
    val loaded: StateFlow<Boolean> = _loaded
    val failed: StateFlow<Boolean> = _failed

    /** 全量，给空状态判断用 */
    val allApps: StateFlow<List<AppEntry>> = _allApps

    val visibleApps: StateFlow<List<AppEntry>> =
        combine(_allApps, _query, _filter) { all, q, f ->
            all.asSequence()
                .filter { app ->
                    when (f) {
                        Filter.ALL -> true
                        Filter.USER -> !app.isSystem
                        Filter.SYSTEM -> app.isSystem
                        Filter.DISABLED -> !app.enabled
                    }
                }
                .filter { app ->
                    if (q.isEmpty()) true
                    else app.appName.contains(q, ignoreCase = true) ||
                        app.packageName.contains(q, ignoreCase = true)
                }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    /** 返回 Job：调用方 join() 之后才收下拉指示器，否则数据没回来指示器就没了。 */
    fun refresh(): Job = viewModelScope.launch {
        _failed.value = false
        val result = withContext(Dispatchers.IO) { runCatching { loadApps() }.getOrNull() }
        if (result != null) {
            _allApps.value = result
            _loaded.value = true
        } else {
            _failed.value = true
            if (!_loaded.value) _loaded.value = true
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** 换筛选保留搜索词：用户通常是在同一批词里换范围。 */
    fun onFilterChange(value: Filter) {
        if (_filter.value == value) return
        _filter.value = value
    }

    /**
     * 读已安装应用。
     *
     * **必须走 queryIntentActivities，不能只信 getInstalledPackages。**
     * 实测（HyperOS 4 / API 37）：即使 manifest 里声明了 QUERY_ALL_PACKAGES 且
     * `dumpsys package` 显示 `granted=true`，getInstalledPackages() 与
     * getInstalledApplications() 仍只返回 **1 个（自己）**，
     * 而 queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER) 正常返回 **111 个**。
     *
     * 这不是 bug 而是厂商收紧：系统只保证「带启动器入口的应用」可被枚举。
     * 对「应用列表」这个页面来说这恰好是对的语义 —— 用户在这里找的就是能打开的应用；
     * 纯后台服务包（系统组件、驱动包）本来也不该出现在这个列表里。
     *
     * 先按启动器查，查不到（有些定制 ROM 连这个也收紧）再退回全量枚举。
     */
    private fun loadApps(): List<AppEntry> {
        val context = appContext ?: return emptyList()
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA

        val infos: List<android.content.pm.ApplicationInfo> = runCatching {
            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                0,
            ).mapNotNull { it.activityInfo?.applicationInfo }.distinctBy { it.packageName }
        }.getOrDefault(emptyList()).ifEmpty {
            // 兜底：老 ROM 或没有启动器的场景
            pm.getInstalledApplications(flags)
        }

        val out = ArrayList<AppEntry>(infos.size)
        for (app in infos) {
            val label = runCatching {
                pm.getApplicationLabel(app).toString()
            }.getOrDefault(app.packageName)
            // 图标解码在 IO 线程做一次，之后全是直接画
            val bitmap = runCatching {
                // 传 ApplicationInfo 而不是 PackageInfo：后者那个重载在 API 37 上已不可用，
                // 编译期报「None of the following candidates is applicable」。
                pm.getApplicationIcon(app).toBitmap(116, 116).asImageBitmap()
            }.getOrNull()
            out += AppEntry(
                packageName = app.packageName,
                userId = 0,
                appName = label,
                isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                enabled = app.enabled,
                icon = bitmap,
            )
        }
        return out.sortedBy { it.appName.lowercase() }
    }

    enum class Filter(val label: String) {
        ALL("全部"),
        USER("用户应用"),
        SYSTEM("系统应用"),
        DISABLED("已停用"),
    }

    companion object {
        /**
         * 应用上下文。ViewModel 拿不到 Context（它不该持有 Activity），
         * 由 Activity 在 onCreate 里塞一次。真实工程里换成 Application 单例。
         */
        var appContext: Context? = null
    }
}
