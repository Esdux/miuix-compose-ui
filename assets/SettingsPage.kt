package com.example.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.app.ui.util.PageScaffold
import com.example.app.ui.util.nextCard
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 设置页的排班规则（三条，都是踩过坑的）：
 *
 * 1. **开关在上、跳转在中、危险操作置底且单独分组。** 用户扫设置页时先找「能开的开关」，
 *    再找「能进去的页面」，最后才是「会毁东西的按钮」。把卸载放在第一组，是设计事故。
 *
 * 2. **会重绘整个 App 的设置单独开一个 Activity。** 主题这种设置一改，页面当场重排，
 *    用户的手指还停在开关上，下面几行会从指头底下消失。放到二级页，回来时用
 *    onResume 让 settingsEpoch 自增重读（见 AppShell.kt）。
 *
 * 3. **每组不超过 6 行。** 超过就说明这一组其实混了两类事，拆开。
 *
 * **跳转必须指向真的注册过的 Activity。** 早先这里写 ThemeActivity::class.java，
 * 而那个类当时既没实现也没进 manifest，用户一点就 ActivityNotFoundException 崩进程。
 */
@Composable
fun SettingsPage(isBlurEnabled: Boolean, extraBottomPadding: Dp = 0.dp) {
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }

    PageScaffold(title = "设置", isBlurEnabled = isBlurEnabled, extraBottomPadding = extraBottomPadding) {
        item(key = "appearance") {
            Column {
                SmallTitle(text = "界面")
                Card(modifier = Modifier.nextCard()) {
                    // 会重绘 App 的设置 → 二级 Activity
                    ArrowPreference(
                        title = "主题",
                        summary = "深色、浅色、动态取色，以及底栏形态",
                        onClick = {
                            context.startActivity(Intent(context, ThemeActivity::class.java))
                        },
                    )
                    // 这里**故意没有**「背景模糊」开关。
                    //
                    // 它曾经存在过，而且是假的：checked 接的是一个 remember 出来的局部
                    // var，拨了不生效、退出页面就回默认。真正生效的那一个在主题二级页里，
                    // 写的是 AppSettings。
                    //
                    // 同一个设置出现两处入口，只要有一处是假的就是 bug —— 用户拨了没反应，
                    // 会以为整个应用的开关都坏了。开关只有一份真相，入口也只有一个。
                }

                SmallTitle(text = "数据")
                Card(modifier = Modifier.nextCard()) {
                    ArrowPreference(
                        title = "导入设置",
                        summary = "从 JSON 文件恢复",
                        // 换成 ActivityResultContracts.OpenDocument() 即可用；
                        // 模板不引 SAF 是为了让空工程零权限直接跑起来
                        onClick = { },
                    )
                    ArrowPreference(
                        title = "导出设置",
                        summary = "备份成 JSON 文件",
                        onClick = { },
                    )
                }

                SmallTitle(text = "危险操作")
                Card(modifier = Modifier.nextCard()) {
                    ArrowPreference(
                        title = "重置全部设置",
                        summary = "恢复出厂默认，不影响已保存的数据",
                        onClick = { confirmReset = true },
                    )
                }
            }
        }
    }

    // 危险操作必须二次确认：WindowDialog 不依赖 Scaffold 的 popupHost，
    // 在任何 Composition 里都能用。
    //
    // 按钮排布照 HyperMusicCover：**横排、等宽、中间 20dp 间隔、确认项用 primary 色**。
    // 竖排堆叠是 Material 的写法，在 HyperOS 里会显得没做完 —— 系统弹窗一律是两个按钮并排。
    // 顺序是「取消在左、确认在右」，与系统的对齐。
    WindowDialog(
        show = confirmReset,
        title = "重置全部设置？",
        summary = "此操作不可撤销",
        onDismissRequest = { confirmReset = false },
    ) {
        val dismiss = LocalDismissState.current
        Row(horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(
                modifier = Modifier.weight(1f),
                text = "取消",
                onClick = { dismiss?.invoke() },
            )
            // 12dp：与全工程的块间距同一个数。对话框里两个按钮的间距没有理由比卡片间距更大，
            // 早先写 20dp，看起来像两个不相关的按钮各占一边。
            Spacer(Modifier.width(12.dp))
            TextButton(
                modifier = Modifier.weight(1f),
                text = "重置",
                onClick = {
                    confirmReset = false
                    dismiss?.invoke()
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
