package com.example.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.app.ui.util.PageScaffold
import com.example.app.ui.util.firstCard
import com.example.app.ui.util.nextCard
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference

/**
 * 功能页 = 一个功能一张卡 + 一行 ArrowPreference，点进去是独立 Activity。
 *
 * 为什么是 Activity 而不是就地换页：整屏到达的转场是平台自带的动画，
 * 它自带返回处理，Android 13+ 还有预测性返回动画；就地换页只能模仿这三件事，
 * 且永远只是近似。
 *
 * 应急操作（「现在马上别关机」这种）反过来：一行 ArrowPreference，点一下当场执行，
 * Toast 回执，不跳页、不弹确认 —— 它本来就该是一步到位。
 *
 * **这里的跳转全部走 FeatureDetailActivity + Intent extra。** 早先模板里写的是
 * context.startActivity(Intent(context, FeatureOneActivity::class.java))，
 * 而那些类既没注册进 manifest 也不是可用的页面，用户一点就 ActivityNotFoundException
 * 崩进程 —— 模板里的跳转必须真的能跳，否则第一次运行就崩在「功能」页。
 */
@Composable
fun FeaturesPage(isBlurEnabled: Boolean, extraBottomPadding: Dp = 0.dp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    PageScaffold(title = "功能", isBlurEnabled = isBlurEnabled, extraBottomPadding = extraBottomPadding) {
        item(key = "features") {
            Column {
                Card(modifier = Modifier.firstCard()) {
                    ArrowPreference(
                        title = "功能一",
                        summary = "一句话说清它做什么、代价是什么",
                        onClick = {
                            context.startActivity(
                                FeatureDetailActivity.intent(
                                    context,
                                    title = "功能一",
                                    summary = "一句话说清它做什么、代价是什么",
                                ),
                            )
                        },
                    )
                }
                Card(modifier = Modifier.nextCard()) {
                    ArrowPreference(
                        title = "功能二",
                        summary = "同上",
                        onClick = {
                            context.startActivity(
                                FeatureDetailActivity.intent(
                                    context,
                                    title = "功能二",
                                    summary = "同上",
                                ),
                            )
                        },
                    )
                }

                Card(modifier = Modifier.nextCard()) {
                    ArrowPreference(
                        title = "应用列表",
                        summary = "搜索、按用户/系统筛选，点进去是应用二级菜单",
                        onClick = {
                            context.startActivity(Intent(context, AppListActivity::class.java))
                        },
                    )
                }

                SmallTitle(text = "应急")
                Card(modifier = Modifier.nextCard()) {
                    ArrowPreference(
                        title = "立即执行的操作",
                        summary = "说明它只影响这一轮，其余交回系统判定",
                        onClick = {
                            scope.launch {
                                val ok = runCatching { doEmergencyAction() }.getOrDefault(false)
                                Toast.makeText(
                                    context,
                                    if (ok) "已执行" else "失败：原因",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 真实项目替换成自己的实现 */
private suspend fun doEmergencyAction(): Boolean = true
