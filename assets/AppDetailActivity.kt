package com.example.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.app.ui.AppSettings
import com.example.app.ui.theme.MiuixAppTheme
import com.example.app.ui.util.DshIconSize
import com.example.app.ui.util.DshSpacing
import com.example.app.ui.util.PageScaffold
import com.example.app.ui.util.firstCard
import com.example.app.ui.util.nextCard
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 应用二级菜单 —— 点列表里某个应用后进来的页面。
 *
 * 排班顺序照 Cirno 的 AppPage：
 * 1. 身份（图标 + 名称 + 包名），不可操作，只回答「我在配哪个应用」；
 * 2. 这个应用**自己**的开关；
 * 3. 跳到系统页面的出口（应用信息 / 强行停止）；
 * 4. 危险操作（清掉本应用的全部配置）单独一组置底。
 *
 * 为什么身份区不是一张可点的 Card：它点了没有去处。可点的东西必须有去处，
 * 否则用户会反复点它、怀疑卡住了。
 */
class AppDetailActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_NAME = "name"

        fun intent(context: Context, app: AppEntry): Intent =
            Intent(context, AppDetailActivity::class.java)
                .putExtra(EXTRA_PKG, app.packageName)
                .putExtra(EXTRA_NAME, app.appName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: return finish()
        val name = intent.getStringExtra(EXTRA_NAME) ?: pkg

        setContent {
            // 图标在组合里用 remember 解一次 —— 见 AppEntry.icon 注释。
            // 不能写在 setContent 外面：remember 是 @Composable，只能在组合作用域里调。
            val icon: ImageBitmap? = remember(pkg) {
                runCatching {
                    packageManager.getApplicationIcon(pkg).toBitmap(144, 144).asImageBitmap()
                }.getOrNull()
            }
            val settings = remember { AppSettings.load(this) }
            val themeMode = remember(settings) {
                runCatching { ColorSchemeMode.valueOf(settings.themeMode) }
                    .getOrDefault(ColorSchemeMode.System)
            }
            MiuixAppTheme(themeMode = themeMode) {
                var enabled by remember { mutableStateOf(true) }
                var keepAlive by remember { mutableStateOf(false) }

                PageScaffold(
                    title = name,
                    isBlurEnabled = settings.isBlurEnabled,
                    onBack = { finish() },
                ) {
                    item(key = "identity") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // 水平 28dp 对齐 SmallTitle 的文字缩进，垂直 12dp 是块间距
                                .padding(horizontal = 28.dp, vertical = DshSpacing.Block),
                            // **图标中心 = 「名称 + 包名」文字块中心。**
                            //
                            // 用 Row 的 CenterVertically 一次达成，不要再给文字列限高：
                            // 限高（比如固定 72dp）在两行包名时会把文字挤出框外，
                            // 视觉上反而更歪。这里 72dp 的图标是行内最高的元素，
                            // 所以它自己贴着 Row 的上下边，顶边自然与上方大标题齐平，
                            // 文字块则被居中到图标中线。
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            icon?.let {
                                Image(
                                    bitmap = it,
                                    contentDescription = null,
                                    modifier = Modifier.size(DshIconSize.DetailIcon),
                                )
                                Spacer(Modifier.width(DshSpacing.CardInside))
                            }
                            Column {
                                MiuixText(
                                    text = name,
                                    style = MiuixTheme.textStyles.title3,
                                )
                                // 包名一定会很长（com.mfcloudcalculate.networkdisk 这类），
                                // **不截断，允许换行**：截断后用户认不出是哪个应用，
                                // 而这一行存在的唯一意义就是让人确认「就是这个包」。
                                // 折行导致文字块变高，靠外层 verticalAlignment = CenterVertically 消化。
                                MiuixText(
                                    text = pkg,
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }

                    item(key = "options") {
                        Column {
                            SmallTitle(text = "此应用")
                            Card(modifier = Modifier.firstCard()) {
                                SwitchPreference(
                                    title = "启用配置",
                                    summary = "关掉后这个应用回到系统默认行为",
                                    checked = enabled,
                                    onCheckedChange = { enabled = it },
                                )
                                SwitchPreference(
                                    title = "保持存活",
                                    summary = "代价是内存占用，只在必要时开",
                                    checked = keepAlive,
                                    enabled = enabled,
                                    onCheckedChange = { keepAlive = it },
                                )
                            }
                        }
                    }

                    item(key = "system") {
                        Column {
                            SmallTitle(text = "系统")
                            Card(modifier = Modifier.nextCard()) {
                                ArrowPreference(
                                    title = "应用信息",
                                    summary = "打开系统的权限、存储、通知页面",
                                    onClick = {
                                        val i = Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", pkg, null),
                                        )
                                        runCatching { startActivity(i) }
                                    },
                                )
                                ArrowPreference(
                                    title = "强行停止",
                                    summary = "系统会弹一次确认，这里不代劳",
                                    onClick = {
                                        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(Uri.fromParts("package", pkg, null))
                                        runCatching { startActivity(i) }.onFailure {
                                            Toast.makeText(this@AppDetailActivity, "无法打开", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                            }
                        }
                    }

                    item(key = "danger") {
                        Column {
                            SmallTitle(text = "危险操作")
                            Card(modifier = Modifier.nextCard()) {
                                ArrowPreference(
                                    title = "清除此应用的配置",
                                    summary = "只影响这个应用，不动其它数据",
                                    onClick = {
                                        Toast.makeText(this@AppDetailActivity, "已清除", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                            // 页尾不加额外间距：最后一块 Card 自己的 nextCard 12dp 就是收尾。
                            // 早先这里补了 24dp，结果这个二级页的底部留白比别的页多一倍。
                        }
                    }
                }
            }
        }
    }
}
