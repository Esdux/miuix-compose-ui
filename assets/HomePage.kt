package com.example.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.app.ui.theme.isInDarkTheme
import com.example.app.ui.util.PageScaffold
import com.example.app.ui.util.firstCard
import com.example.app.ui.util.nextCard
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 主页 = 状态卡 → 数据卡 → 元信息卡。这个顺序不是审美，是排班规则：
 * 状态 → 主操作 → 数据/内容 → 次要信息 → 元信息。
 * 详见 references/information-architecture.md。
 *
 * **状态卡的布局与图标逐字照搬 HyperMusicCover 的 HomePageView**（`ui/screen/home/HomePage.kt`
 * 第 194–350 行），只改文案与状态来源。这是刻意的：那张卡是三个 Box 叠出来的，
 * 不是三行 Column —— 标题与版本贴左上、工作模式钉左下、超大图标从右下角出血，
 * 模式与图标斜向平衡，中间留白才不显得挤。自己「重绘」成 Column 会挤成一团。
 *
 * 四个要点：
 * 1. 大色块表达成败，不是只写一行红字；
 * 2. 副标题给出**下一步动作**，不是复述错误；
 * 3. 整张卡可点 = 手动刷新，图标用大尺寸半透明铺在右下角做底纹，不额外占高度；
 * 4. **前两张卡上方不加分组标题**（SKILL.md 5.1）。下面三个 item 直接从卡片起 ——
 *    若有人给「运行中」加个 `SmallTitle("运行状态")`、给数据卡加个 `SmallTitle("应用信息")`，
 *    那两个标题要吃掉约 72dp（SmallTitle 自带 28/8dp 内边距），正好是一张数据卡的高度，
 *    而标题只是把卡片里的字复述一遍。检查脚本会拦（check_first_cards）。
 *    从第三张卡起、或一个标题下确实有两张以上同类卡时才加。
 */
@Composable
fun HomePage(
    isBlurEnabled: Boolean,
    extraBottomPadding: Dp = 0.dp,
    status: AppStatus = AppStatus.sample,
    onRefresh: () -> Unit = {},
) {
    PageScaffold(
        title = "主页",
        isBlurEnabled = isBlurEnabled,
        extraBottomPadding = extraBottomPadding,
    ) {
        item(key = "status") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .firstCard()
            ) {
                StatusCard(status = status, onRefresh = onRefresh)
            }
        }

        item(key = "data") {
            Card(modifier = Modifier.nextCard()) {
                Column {
                    BasicComponent(title = "可用空间", summary = status.freeSpace)
                    BasicComponent(title = "当前版本", summary = status.version)
                    BasicComponent(title = "运行状态", summary = status.runtime)
                }
            }
        }

        item(key = "device") {
            Card(modifier = Modifier.nextCard()) {
                Column {
                    BasicComponent(title = "设备名称", summary = status.deviceName)
                    BasicComponent(title = "设备型号", summary = status.deviceModel)
                    BasicComponent(title = "系统版本", summary = status.systemVersion)
                }
            }
        }
    }
}

/**
 * 状态卡本体 —— 结构与 HyperMusicCover 的 `item(key = "status")` 完全一致。
 *
 * 高度来自 `Row(IntrinsicSize.Min)`：里面三个 Box 都是 fillMaxSize、本身不贡献高度，
 * 没有这层 IntrinsicSize 卡片会塌成文字那么高。
 */
@Composable
private fun StatusCard(status: AppStatus, onRefresh: () -> Unit) {
    val ok = status.ok
    val darkTheme = isInDarkTheme()
    val dynamicColor = MiuixTheme.isDynamicColor

    // 颜色三档与 HMC 逐字相同：动态取色 → 手写深色 → 手写浅色。
    // 不直接用 errorContainer / secondaryContainer：深色 Monet 下对比度不够。
    val statusColor = if (ok) {
        when {
            dynamicColor -> MiuixTheme.colorScheme.secondaryContainer
            darkTheme -> Color(0xFF1A3825)
            else -> Color(0xFFDFFAE4)
        }
    } else {
        when {
            dynamicColor -> MiuixTheme.colorScheme.errorContainer
            darkTheme -> Color(0xFF3D1C1C)
            else -> Color(0xFFFDE8E8)
        }
    }
    val iconTint = if (ok) {
        if (dynamicColor) MiuixTheme.colorScheme.primary.copy(alpha = 0.8f) else Color(0xFF36D167)
    } else {
        if (dynamicColor) MiuixTheme.colorScheme.error.copy(alpha = 0.8f) else Color(0xFFDC3545)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(color = statusColor),
            onClick = onRefresh,
            showIndication = true,
            // Tilt：按压时卡片整体轻微倾斜，与 HyperOS 系统卡片一致；不要用 Ripple
            pressFeedbackType = PressFeedbackType.Tilt
        ) {
            Box {
                // ① 底纹图标：offset(27, 31) 让它故意出血，只露右下角一角
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(27.dp, 31.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Icon(
                        modifier = Modifier.size(110.dp),
                        // 用 material-icons-extended 的 Rounded 版本，与 HMC 是同一个图标。
                        // 早先为省体积自绘了 ic_check_circle.xml / ic_error_circle.xml，
                        // 结果勾的包围盒偏心、描边比例不对，怎么调都不如原版 —— 直接用它。
                        imageVector = if (ok) Icons.Rounded.CheckCircleOutline
                        else Icons.Rounded.ErrorOutline,
                        tint = iconTint,
                        contentDescription = null
                    )
                }
                // ② 左下角应用名：状态卡同时也是「我是谁」的位置
                if (status.appName.isNotEmpty()) {
                    Box(
                        // 16 / 10 与 HMC 一致，不要改成 DshSpacing.Compact ——
                        // 应用名要贴底但比卡内边距更靠下一点，8dp 会显得浮起来。
                        modifier = Modifier
                            .fillMaxSize()
                            // miuix-check: allow-design-tokens
                            .padding(16.dp, 10.dp),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        MiuixText(
                            text = status.appName,
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                // ③ 左上角标题 + 第二行
                Box(
                    // 16 / 14 与 HMC 一致。这两行是卡的正文，比应用名多 4dp 才与
                    // 下方 icon 的视觉重心对得上；换成 12dp 标题会往上顶。
                    modifier = Modifier
                        .fillMaxSize()
                        // miuix-check: allow-design-tokens
                        .padding(16.dp, 14.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Column {
                        MiuixText(
                            text = status.title,
                            style = MiuixTheme.textStyles.title3,
                            fontWeight = FontWeight.SemiBold
                        )
                        // 1dp 与 HMC 一致：这里只做视觉分离，不用行距。
                        Spacer(Modifier.height(1.dp))
                        MiuixText(
                            text = status.subtitle,
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                }
            }
        }
    }
}

/** 页面数据模型：真实项目换成自己的状态源；这里只是让模板能独立编译 */
data class AppStatus(
    val ok: Boolean,
    val title: String,
    val subtitle: String,
    val appName: String,
    val freeSpace: String,
    val version: String,
    val runtime: String,
    val deviceName: String,
    val deviceModel: String,
    val systemVersion: String,
) {
    companion object {
        val sample = AppStatus(
            ok = true,
            title = "运行中",
            subtitle = "一切正常",
            appName = "Example",
            freeSpace = "12.4 GB",
            version = "v1.0",
            runtime = "已加载",
            deviceName = "My Phone",
            deviceModel = "Xiaomi 15",
            systemVersion = "OS4.0.0.35.XPBCNXM",
        )
    }
}