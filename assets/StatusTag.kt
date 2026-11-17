package com.example.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/** 右侧状态标签。颜色只承担语义，不承担装饰。 */
data class TagSpec(val label: String, val background: Color, val contentColor: Color)

/**
 * 固定参数：6dp 圆角、4×2dp 内边距、9sp、750 字重、单行不换行。
 *
 * 这里**故意**不跟 MiuixTheme.textStyles 与 12dp 栅格走：它是列表项末尾的状态 chip，
 * 尺寸由 Cirno 原版钉死（9sp 正好比 footnote2 的 11sp 再小一档，塞进单行不撑高行高）。
 * 跟正文样式走会让标签比标题还显眼，跟 12dp 栅格走会让 chip 胖成一坨。
 * 下面两行的豁免标记就是给这种情况用的，别的组件不要照抄。
 *
 * **必须单独一个文件。** 早先它同时定义在 ListPage.kt 与 AppListPage.kt 里，
 * 两份实现逐字相同，但同一个包里同名函数会直接 `Conflicting overloads` 编译失败。
 * 两个列表页共用它，就抽出来；复制一份看起来省事，代价是永远编译不过。
 */
@Composable
fun StatusTag(spec: TagSpec) {
    Box(modifier = Modifier.background(spec.background, RoundedCornerShape(6.dp))) {
        MiuixText(
            // miuix-check: allow-design-tokens —— 见上方注释，chip 尺寸固定
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            text = spec.label,
            color = spec.contentColor,
            // miuix-check: allow-design-tokens —— chip 固定 9sp
            fontSize = 9.sp,
            fontWeight = FontWeight(750),
            maxLines = 1,
            softWrap = false,
        )
    }
}
