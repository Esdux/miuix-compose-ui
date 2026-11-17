package com.example.app.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.app.ui.AppSettings
import com.example.app.ui.theme.MiuixAppTheme
import com.example.app.ui.util.PageScaffold
import com.example.app.ui.util.firstCard
import com.example.app.ui.util.nextCard
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

/**
 * 功能详情二级页 —— 模板里所有「点进去」的功能都复用这一个 Activity。
 *
 * 为什么是 Activity 而不是同一个 Activity 里换 Compose 内容：
 * 整屏到达的转场动画、返回键处理、Android 13+ 的预测性返回，这三件事平台已经做好了，
 * 就地换页只能模仿，且永远只是近似。HyperMusicCover 用注释钉死了这条结论。
 *
 * 一个 Activity 承载所有功能详情、靠 Intent extra 区分，是权衡后的选择：
 * 每个功能一个 Activity 会让 manifest 无限膨胀，而这里的页面结构完全同构。
 * 当某个功能需要自己的生命周期（相机、前台服务、深链）时，再给它独立 Activity。
 */
class FeatureDetailActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SUMMARY = "summary"

        fun intent(context: Context, title: String, summary: String): Intent =
            Intent(context, FeatureDetailActivity::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_SUMMARY, summary)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "功能"
        val summary = intent.getStringExtra(EXTRA_SUMMARY) ?: ""

        setContent {
            val settings = remember { AppSettings.load(this) }
            val themeMode = remember(settings) {
                runCatching { ColorSchemeMode.valueOf(settings.themeMode) }
                    .getOrDefault(ColorSchemeMode.System)
            }
            MiuixAppTheme(themeMode = themeMode) {
                var enabled by remember { mutableStateOf(true) }
                PageScaffold(
                    title = title,
                    isBlurEnabled = settings.isBlurEnabled,
                    // 二级页没有底栏，返回键由顶栏的返回按钮与系统手势共同承担
                    onBack = { finish() },
                ) {
                    // **不要在这里放「说明」卡。**
                    //
                    // 二级页的顶栏标题已经回答了「我在哪」；再补一张「说明」卡解释这个功能，
                    // 就是把文档搬进了界面 —— 用户点进来是要用，不是要读。
                    // 说明的正确位置是**具体控件自己的 summary**：一行，紧跟在这个开关下面，
                    // 讲清「它做什么、代价是什么」。用户读到的是他正要拨的那个开关的后果，
                    // 而不是一段需要先读完再往下找的概述。
                    item(key = "options") {
                        Column {
                            SmallTitle(text = "选项")
                            Card(modifier = Modifier.firstCard()) {
                                SwitchPreference(
                                    title = "示例开关",
                                    // summary 写「做什么 + 代价」，不是「这是什么」
                                    summary = summary.ifBlank { "打开后会发生什么，代价是什么" },
                                    checked = enabled,
                                    onCheckedChange = { enabled = it },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
