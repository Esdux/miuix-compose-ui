package com.example.app.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.app.ui.AppSettings
import com.example.app.ui.theme.MiuixAppTheme
import com.example.app.ui.util.PageScaffoldColumn
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

/**
 * 应用列表独立成一个 Activity。
 *
 * 理由：它是一个**整屏到达**的页面（有搜索、有筛选、有下拉刷新），
 * 塞进「功能」标签里会让底栏跟着出现，用户在筛选应用时误触底栏跳走。
 * 二级页统一没有底栏，返回靠顶栏箭头与系统手势 —— 这是平台已经做好的三件事
 * （转场动画、返回处理、预测性返回），就地换页只能模仿。
 */
class AppListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ViewModel 拿不到 Activity Context，这里塞一次应用上下文
        AppListViewModel.appContext = applicationContext

        setContent {
            val settings = remember { AppSettings.load(this) }
            val themeMode = remember(settings) {
                runCatching { ColorSchemeMode.valueOf(settings.themeMode) }
                    .getOrDefault(ColorSchemeMode.System)
            }
            MiuixAppTheme(themeMode = themeMode) {
                // 用 Column 版骨架：本页内容是「自己会滚的列表」，
                // 塞进 PageScaffold 的 LazyColumn 里会出现外层滚不动、内层也滚不动。
                PageScaffoldColumn(
                    title = "应用列表",
                    isBlurEnabled = settings.isBlurEnabled,
                    onBack = { finish() },
                ) { innerPadding ->
                    AppListPage(
                        isBlurEnabled = settings.isBlurEnabled,
                        bottomPadding = innerPadding.calculateBottomPadding(),
                        onEntryClick = { app ->
                            startActivity(AppDetailActivity.intent(this, app))
                        },
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                    )
                }
            }
        }
    }
}
