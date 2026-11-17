# 主题与图标

## 应用主题

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val controller = remember { ThemeController(ColorSchemeMode.System) }
    MiuixTheme(controller = controller, content = content)
}
```

`MiuixTheme` 两种入口：

- `MiuixTheme(controller = ..., textStyles = ..., content = ...)`：由 Controller 计算配色。
- `MiuixTheme(colors = lightColorScheme() / darkColorScheme(), content = ...)`：直接指定 ColorScheme，此时 `MiuixTheme.colorSchemeMode` 为 `null`。

## ThemeController

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `colorSchemeMode` | `ColorSchemeMode` | `System` | 配色模式 |
| `lightColors` | `Colors` | `lightColorScheme()` | 自定义浅色配色 |
| `darkColors` | `Colors` | `darkColorScheme()` | 自定义深色配色 |
| `keyColor` | `Color?` | `null` | Monet 种子色；null 时尝试系统壁纸色 |
| `colorSpec` | `ThemeColorSpec` | `Spec2021` | 颜色规范版本 |
| `paletteStyle` | `ThemePaletteStyle` | `TonalSpot` | 动态取色调色板 |
| `isDark` | `Boolean?` | `null` | 强制深浅色；null 跟随系统 |

`ColorSchemeMode`：`System`、`Light`、`Dark`、`MonetSystem`、`MonetLight`、`MonetDark`。

`ThemePaletteStyle`：`TonalSpot`、`Neutral`、`Vibrant`、`Expressive`、`Rainbow`、`FruitSalad`、`Monochrome`、`Fidelity`、`Content`。

`ThemeColorSpec`：`Spec2021`、`Spec2025`。`Spec2025` 仅 `TonalSpot`、`Neutral`、`Vibrant`、`Expressive` 支持，其余自动降级为 `Spec2021`。

## 访问主题值

```kotlin
val colors = MiuixTheme.colorScheme
val textStyles = MiuixTheme.textStyles
val mode = MiuixTheme.colorSchemeMode
val dynamic = MiuixTheme.isDynamicColor
```

组件内不要用 Material3 的 `MaterialTheme.colorScheme` 或 `colorScheme` 常量；统一用 `MiuixTheme.colorScheme`。

## 常用颜色

完整表见 `https://compose-miuix-ui.github.io/miuix/zh_CN/guide/colors`。

| 名称 | 用途 | 浅色 |
|---|---|---|
| `primary` | 主色 | `#FF3482FF` |
| `onPrimary` | 主色上的文本 | `#FFFFFFFF` |
| `primaryVariant` | 主色变体 | `#FF3482FF` |
| `onPrimaryVariant` | 主色变体文本 | `#FFAECDFF` |
| `primaryContainer` | 主色容器 | `#FF5D9BFF` |
| `onPrimaryContainer` | 主色容器文本 | `#FFFFFFFF` |
| `secondary` | 次要色 | `#FFE6E6E6` |
| `onSecondary` | 次要色文本 | `#FFFFFFFF` |
| `secondaryVariant` | 次要色变体 | `#FFF0F0F0` |
| `onSecondaryVariant` | 次要色变体文本 | `#FF303030` |
| `tertiaryContainer` | 三级容器 | `#FFEAF2FF` |
| `onTertiaryContainer` | 三级容器文本 | `#FF3482FF` |
| `background` | 应用背景 | `#FFFFFFFF` |
| `onBackground` | 背景文本 | `#FF000000` |
| `onBackgroundVariant` | 背景次级文本 | `#FF8C93B0` |
| `surface` | 表面 | `#FFF7F7F7` |
| `onSurface` | 表面文本 | `#FF000000` |
| `surfaceVariant` | 表面变体 | `#FFFFFFFF` |
| `onSurfaceSecondary` | 表面次要文本 | `#CC000000` |
| `onSurfaceVariantSummary` | 摘要文本 | `#99000000` |
| `onSurfaceVariantActions` | 操作文本 | `#66000000` |
| `surfaceContainer` | 表面容器 | `#FFFFFFFF` |
| `onSurfaceContainer` | 表面容器文本 | `#FF000000` |
| `surfaceContainerHigh` | 高对比容器 | `#FFE8E8E8` |
| `surfaceContainerHighest` | 最高对比容器 | `#FFE8E8E8` |
| `outline` | 边框 | `#FFD9D9D9` |
| `dividerLine` | 分隔线 | `#FFE0E0E0` |
| `error` / `onError` | 错误色 | `#FFE94634` / `#FFFFFFFF` |
| `disabledPrimary` | 禁用主色 | `#FFC2D9FF` |
| `windowDimming` | 弹层遮罩 | `#4D000000` |

深色值不同，不要硬编码浅色值；通过 `MiuixTheme.colorScheme` 读取。

## 文本样式

完整表见 `https://compose-miuix-ui.github.io/miuix/zh_CN/guide/textstyles`。

| 样式 | 字号 | 字重 |
|---|---|---|
| `main` | 17sp | Normal |
| `paragraph` | 17sp | Normal，行高 1.2em |
| `body1` | 16sp | Normal |
| `body2` | 14sp | Normal |
| `button` | 17sp | Normal |
| `footnote1` | 13sp | Normal |
| `footnote2` | 11sp | Normal |
| `headline1` | 17sp | Normal |
| `headline2` | 16sp | Normal |
| `subtitle` | 14sp | Bold |
| `title1` | 32sp | Normal |
| `title2` | 24sp | Normal |
| `title3` | 20sp | Normal |
| `title4` | 18sp | Normal |

```kotlin
Text("页面标题", style = MiuixTheme.textStyles.title2)
Text("正文", style = MiuixTheme.textStyles.main)
```

颜色由 `MiuixTheme.colorScheme.onBackground` 在运行时统一提供；需要强调色时显式传 `color`。

自定义文本样式：

```kotlin
val styles = defaultTextStyles(
    title1 = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold)
)
MiuixTheme(controller = controller, textStyles = styles) { /* ... */ }
```

## 图标

`miuix-ui` 只带少量核心图标。扩展图标在 `miuix-icons`，Glass 图标在 `miuix-glass-icons`。

```kotlin
implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.4")
implementation("top.yukonga.miuix.kmp:miuix-glass-icons:0.9.4")
```

### 使用规则

1. `import top.yukonga.miuix.kmp.icon.MiuixIcons`
2. 为每个用到的图标单独 import 扩展属性，例如：
   ```kotlin
   import top.yukonga.miuix.kmp.icon.extended.Settings
   import top.yukonga.miuix.kmp.icon.extended.Ok
   ```
3. 使用时：`MiuixIcons.Settings`、`MiuixIcons.Ok`。

**常见错误**：只 import `MiuixIcons` 而没有 import 具体图标，会得到 `Unresolved reference: Settings`。

### 粗细

五种粗细：`Light`、`Normal`、`Regular`、`Medium`、`Demibold`。

```kotlin
Icon(imageVector = MiuixIcons.Settings, contentDescription = "设置")
Icon(imageVector = MiuixIcons.Light.Settings, contentDescription = "设置（细）")
```

默认粗细为 Regular。

### Glass 图标

```kotlin
import top.yukonga.miuix.kmp.icon.glass.Search

Icon(
    imageVector = MiuixIcons.Glass.Search,
    contentDescription = "搜索",
    modifier = Modifier.size(28.dp)
)
```

完整图标清单（含可复制 import）见 `https://compose-miuix-ui.github.io/miuix/zh_CN/guide/icons`。图标很多，按需查找，不要凭名称猜大小写。

## 动态取色示例

```kotlin
val controller = remember {
    ThemeController(
        ColorSchemeMode.MonetSystem,
        keyColor = Color(0xFF3482FF),
        paletteStyle = ThemePaletteStyle.Vibrant,
        colorSpec = ThemeColorSpec.Spec2025,
    )
}
MiuixTheme(controller = controller) { App() }
```

## 主题持久化与恢复

把 `ColorSchemeMode.name` 存进偏好，读取时容错：

```kotlin
val saved = prefs.getString("theme_mode", "System") ?: "System"
val mode = try {
    ColorSchemeMode.valueOf(saved)
} catch (_: Exception) {
    ColorSchemeMode.System
}
val controller = remember(mode) { ThemeController(mode) }
```

- 用 `remember(mode)`，模式变化时重建 Controller。
- `valueOf` 必须捕获异常；旧版本写入的字符串、手工改配置都可能不匹配。
- 主题页自身要在 `MiuixTheme` 内承载状态，改动立即可见；只在返回后才生效的写法会让用户「盲改」。
- 如果工程用 `UiModeManager.setApplicationNightMode(...)` 让启动页的 `-night` 资源跟随应用主题，该调用会重建 Activity，只在值变化时执行。

## 判断当前是否深色

不要用系统配置猜，直接按 Miuix 的 `surface` 亮度判断：

```kotlin
@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean {
    val surface = MiuixTheme.colorScheme.surface
    val luminance = 0.2126f * surface.red + 0.7152f * surface.green + 0.0722f * surface.blue
    return luminance < 0.5f
}
```

这样在 `Light` / `Dark` / `Monet*` 模式下结果都与实际配色一致。

## 同步系统栏图标

`enableEdgeToEdge()` 的系统栏样式只跟随系统深浅模式。应用内强制 Light/Dark 时，状态栏/导航栏图标颜色不会自动改变，需要按上面的 `isInDarkTheme()` 同步：

```kotlin
val isDark = isInDarkTheme()
val view = LocalView.current
if (!view.isInEditMode) {
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }
}
```

完整模板见 `assets/MiuixAppTheme.kt`。

## 图标与 Material Icons 共存

Miuix 的 `Icon` 接收 `ImageVector`。`androidx.compose.material:material-icons-extended` 提供的 `Icons.Rounded.*` 也是 `ImageVector`，可以直接传入：

```kotlin
Icon(
    imageVector = Icons.Rounded.CheckCircleOutline,
    contentDescription = null,
    tint = MiuixTheme.colorScheme.primary,
)
```

这属于**图标资源**混用，不是 UI 组件混用。仍然不要引入 `androidx.compose.material3.Button/Scaffold/Text` 等组件。

**体积实测（2026-11-17）**：同一工程只差图标来源，debug 从 32.2MB 涨到 64.4MB，
但开了 `isMinifyEnabled` + `isShrinkResources` 的 release 只从 5.95MB 涨到 6.51MB ——
R8 把未引用的图标类全裁了，进包的只有实际用到的 `ImageVector`。
**不要拿 debug 体积判断图标库太重。**

反过来，自绘 vector 复刻 Material 图标是有代价的：勾的包围盒、圆环与勾的比例
是一起设计的，单独抄 pathData 再调 strokeWidth 怎么都差一点（110dp 出血后肉眼可见）。
**要精确复刻就直接引库；只有品牌图形（应用 logo）才必须自绘。**

## 图标依赖的取舍

如果只用到少量 Miuix 图标，不必为了一个图标引入整个扩展库；但一旦使用 `MiuixIcons.X`，就必须加 `miuix-icons` 依赖并逐个 import。两者取其一，不要既引入大图标库又在代码里混用多套图标风格。