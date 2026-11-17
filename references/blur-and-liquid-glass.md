# 模糊与玻璃效果

基于 `miuix-blur`（0.9.4-rc01 / 0.9.4 实测用法）。该模块处于实验阶段，参数可能变化；调用前先核对当前版本源码或 Dokka。

## 1. 依赖与平台门槛

```kotlin
implementation("top.yukonga.miuix.kmp:miuix-blur:0.9.4")
// Android 工程
implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4")
```

- Android 需要 API 33（`RuntimeShader`）。
- Desktop / iOS / macOS / Web 由 Skiko 提供支持。
- 低版本 Android 不应尝试模糊，直接回退纯色。

## 2. 能力检测与降级

```kotlin
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.material.isRenderEffectSupported

val canBlur = isRuntimeShaderSupported()   // 模糊/混色/噪点
val canBackdrop = isRenderEffectSupported() // 仅背景采样支架
```

真实工程的做法是把 backdrop 做成可空，能力不足或用户关闭模糊时返回 `null`，所有模糊分支都判空：

```kotlin
@Composable
fun rememberBlurBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported() || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}
```

调用方：

```kotlin
val backdrop = rememberBlurBackdrop()
val blurActive = isBlurEnabled && backdrop != null
val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
```

**不要**在 `backdrop == null` 时仍调用 `textureBlur(backdrop = null, ...)`。

## 3. 背景捕获

```kotlin
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

val backdrop = rememberLayerBackdrop {
    drawRect(MiuixTheme.colorScheme.surface)
    drawContent()
}

Box(
    modifier = Modifier
        .fillMaxSize()
        .layerBackdrop(backdrop)
) {
    // 页面内容
}
```

注意：

- `layerBackdrop` 只捕获它所在 composable 绘制的内容，不包含父级 `Scaffold` 的背景。
- 捕获内容若有透明区域，模糊会把颜色扩散到透明像素，产生色块；在 `onDraw` 里先 `drawRect(不透明背景)`。
- 同一页面只放一层 `layerBackdrop`，通常包住整个内容区；顶栏和底栏分别对同一个 backdrop 做模糊。

## 4. 普通模糊表面

```kotlin
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.textureBlur

Modifier.textureBlur(
    backdrop = backdrop,
    shape = RoundedCornerShape(24.dp),
    blurRadius = 25f,
    colors = BlurDefaults.blurColors(
        blendColors = listOf(
            BlendColorEntry(color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)),
        ),
    ),
    highlight = Highlight.GlassStrokeMiddleLight, // 深色主题用 GlassStrokeMiddleDark
)
```

用途：悬浮底栏、卡片、胶囊按钮等有明确边界的表面。

要点：

- 背景色设为 `Color.Transparent`，由模糊层绘制颜色；否则纯色会盖住模糊。
- `shape` 决定采样与裁剪形状，圆角要和组件圆角一致。
- `blendColors` 用来把主题色叠到模糊结果上，控制「栏有多实」。
- `highlight` 提供玻璃边缘高光，深浅主题选择不同预设。

## 5. 渐进模糊（顶栏）

```kotlin
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur

Box(
    modifier = Modifier
        .matchParentSize()
        .progressiveTextureBlur(
            backdrop = backdrop,
            shape = RectangleShape,
            gradient = ProgressiveBlur.Top.copy(curve = 10f),
            blurRadius = 15f,
            colors = BlurDefaults.blurColors(
                blendColors = listOf(
                    BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(alpha = 0.3f)),
                ),
            ),
        ),
)
```

- 方向预设：`ProgressiveBlur.Top` / `Bottom` / `Left` / `Right`。
- `curve` 控制从清晰端到模糊端的衰减；案例用 `10f` 让模糊快速衰减。
- 典型用法：透明 `TopAppBar` 后面铺一层渐进模糊，内容从下方滚过时产生「越靠近顶栏越模糊」的效果。
- 若要做「滚动后才渐显」，用 `graphicsLayer { alpha = ... }` 结合 `scrollBehavior.state.contentOffset`；`ScrollFadeDistance = 0.dp` 表示常驻完整模糊。

## 6. 玻璃高光与设备倾斜

`miuix-blur` 的 `highlight` 包提供可配置的玻璃边缘光：

```kotlin
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource

val specular = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.0.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f,
        ),
        dualPeak = true,
    ),
)
```

- 常用预设：`Highlight.GlassStrokeMiddleLight` / `Highlight.GlassStrokeMiddleDark`。
- `rememberDeviceTilt`（`top.yukonga.miuix.kmp.blur.sensor`）可把设备倾斜映射到高光位置，案例的液态玻璃导航栏使用；具体签名以当前版本源码为准。
- 高光属于视觉增强，低端设备可整体关闭，不影响功能。

## 7. 底层自定义 API

案例还使用了以下 API 实现液态玻璃：

| API | 用途 |
|---|---|
| `Modifier.drawBackdrop(backdrop, ...)` | 在绘制阶段读取背景，自定义绘制 |
| `BackdropEffectScope.blur(...)` | 在 backdrop 作用域内做模糊 |
| `Modifier.runtimeShaderEffect(...)` | 直接挂自定义 RuntimeShader |
| `Backdrop` / `LayerBackdrop` | 背景采样对象 |
| `colorControls(...)` | 调整模糊结果的颜色参数 |
| `asBrush()` | 把模糊结果转为 Brush |
| `squircleClip(...)` | 平滑圆角裁剪（`miuix-squircle`） |

这些是底层能力，参数较多且仍在演进。使用前请读 `miuix-blur` 源码或 Dokka，不要凭记忆写签名。普通页面优先用 `textureBlur` / `progressiveTextureBlur`，只有需要独特效果时才下探到底层。

## 8. 性能与生命周期

- 模糊是逐帧采样，成本高于纯色；只在可见页面启用。
- 多页 Pager 中，给非当前页传 `isCurrent = { false }` 或直接不挂模糊，避免后台页持续采样。
- 不要在高频重组路径里构造 `Highlight` / `BlurDefaults`，用 `remember`。
- 背景内容变化频繁时（视频、动画），模糊开销会上升；必要时降低 `blurRadius` 或关闭渐进层。
- 关闭模糊时保留纯色 surface 分支，确保对比度和可读性。

## 9. 常见错误

| 现象 | 原因 | 修复 |
|---|---|---|
| 模糊处出现色块 | backdrop 捕获到透明像素 | `drawRect(surfaceColor)` 后再 `drawContent()` |
| 模糊完全没效果 | 背景色不透明盖住了模糊层 | 模糊表面设 `Color.Transparent` |
| 低版本 Android 崩溃 | 未判断 API 33 / RuntimeShader | 先 `isRuntimeShaderSupported()`，backdrop 可空 |
| 顶栏模糊与内容脱节 | 列表 inset 放在 modifier，内容没滚到顶栏下 | inset 放 `contentPadding` |
| 模糊层闪烁/跳动 | 每帧重建 Highlight 或高频重组 | `remember` 配置，减少重组 |
| 玻璃导航栏在浅色下看不清 | 高光/混合色只按深色调 | 按 `isInDarkTheme()` 切换 Highlight 与混合色 |