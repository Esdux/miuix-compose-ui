# HyperMusicCover 实战范式

来源：`zyl6932/HyperMusicCover`（包名 `com.os4.musiccover`，AGPL-3.0）与 `zyl6932/vcut_lower`
（包名 `com.github.zyl6932.vcutlower`）。两者是同一套界面骨架的两个实例，
本文只记录**从代码里读出来、且已经在这两个工程里跑起来**的范式。

---

## 1. 条件相关的设置项折叠（原工程叫 `FoldUnder`）

> **`FoldUnder.kt` 不在本 skill 的模板里。** 它是 vcut_lower / HyperMusicCover 自建的组件
> （不是 Miuix API）。模板曾带过一份，随「液态玻璃」开关一起删掉了 —— 删的是那个假开关，
> 不是这个技巧。**技巧本身仍然值得用**，需要时按下面的代码自己建一个 `ui/util/FoldUnder.kt`。

**问题**：一个开关控制下面几行是否有效。常见做法是把那几行置灰（`enabled = false`），
但置灰不说明原因，一屏灰行等于告诉用户「这里坏了」。

**做法**：关掉时把下面几行**折叠掉**，打开时展开。

```kotlin
@Composable
internal fun FoldUnder(
    visible: Boolean,
    settled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val state = remember(settled, if (settled) Unit else visible) { MutableTransitionState(visible) }
    state.targetState = visible
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        content()
    }
}
```

### `settled` 参数不能省

页面打开时先显示默认值，模块稍后通过广播回一个真实值。**如果那一刻也走动画**，
每次进这个页面都会看到一次「展开」，而且开关在它下面先关后开地跳一下。

`settled = false` 期间，`remember` 的 key 里带上 `visible`：
值一变就**新建一个 `MutableTransitionState`**，新状态从目标值开始 = 瞬间到位，不播动画。
`settled` 变成 `true` 的那一帧也重建一次（key 从 `visible` 变成 `Unit`），
之后 key 稳定，才开始真正动画。

用法：

```kotlin
// 主题页：某个子开关只在父开关开启时存在
FoldUnder(isFloatingNavbar) {
    SwitchPreference(
        title = "某个子选项",
        summary = "父开关关掉时这一行不存在",
        checked = childEnabled,
        onCheckedChange = onChildChange,
    )
}

// 需要等模块回值的场合
FoldUnder(module.coverFeature, settled = enabled) {
    // ...
}
```

### 为什么是折叠而不是隐藏

`AnimatedVisibility` + `expandVertically`/`shrinkVertically` 让卡片高度连续变化。
直接 `if (visible) { ... }` 会让下面所有行**瞬间跳位**，手指可能正好落在刚跳上来的那一行上。

**一个卡片里最多两层折叠。** 三层以上用户会失去位置感，那种情况该拆成二级页。

### 什么时候不要用它

如果那个子选项**拨了跟没拨一样**（比如只把模糊半径从 25 调到 32），
删掉整个开关，而不是给它加一层折叠。判据见 SKILL.md 4.1：
**一个设置项必须让用户在两秒内看出差别。**

---

## 2. 主题设置单独成页

### 为什么不在设置页里

KernelSU 的做法是设置页只留一行「主题」，点进去是完整的 appearance 区块。HMC 跟随它，理由写在自己的注释里：

> The list was long enough that the settings page read as a wall, and the switches that follow are
> not all visible at once - a conditional switch only exists when its parent is on, so on the
> settings page a row appeared or vanished underneath the finger that opened the dropdown above it.
> Here the dropdown is the last thing that moves.

翻译成规则：

1. **设置页超过 ~12 行** → 拆出主题二级页。
2. **有折叠项时更要拆**：折叠会让它下面的行上下移动，用户手指可能正悬在那里。
3. 二级页里把**会改变自身外观的那一项放到最上面**（主题模式），把**会引起折叠的开关放到最后**。
   这样展开/收起发生在页面底部，不推动上面的行。

### 页面签名：值从宿主传，页面不读不写

```kotlin
@Composable
fun ThemePageView(
    currentMode: ColorSchemeMode,
    onModeChange: (ColorSchemeMode) -> Unit,
    isFloatingNavbar: Boolean,
    onFloatingNavbarChange: (Boolean) -> Unit,
    isBlurEnabled: Boolean,
    onBlurEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
)
```

> Nothing is read or saved here: the values arrive from the screen that hosts this
> ([ThemeActivity]), which owns them because it owns the theme this page is drawn in.

**主题页不能自己读 `AppSettings`。** 它是被主题影响的那个页面，自己持有状态就会出现
「改完模式，这一页还是旧主题」的循环依赖。值从 Activity 传进来，Activity 才是所有者。

### 主题模式下拉

```kotlin
val modes = listOf(
    stringResource(R.string.theme_system),
    stringResource(R.string.theme_light),
    stringResource(R.string.theme_dark),
    stringResource(R.string.theme_monet_system),
    stringResource(R.string.theme_monet_light),
    stringResource(R.string.theme_monet_dark),
)
val modesEnum = listOf(
    ColorSchemeMode.System, ColorSchemeMode.Light, ColorSchemeMode.Dark,
    ColorSchemeMode.MonetSystem, ColorSchemeMode.MonetLight, ColorSchemeMode.MonetDark,
)
val currentIndex = modesEnum.indexOf(currentMode).takeIf { it >= 0 } ?: 0

WindowDropdownPreference(
    title = stringResource(R.string.theme_mode),
    summary = modes[currentIndex],          // 当前值显示在 summary
    items = modes,
    selectedIndex = currentIndex,
    onSelectedIndexChange = { onModeChange(modesEnum[it]) },
    onExpandedChange = { expanded = it },
)
```

- **两个平行 List**（显示名 / 枚举）按索引对齐，`indexOf(...).takeIf { it >= 0 } ?: 0` 兜底。
- 当前值放 `summary`，不放进 `title`。`title` 是「主题模式」这个标签，永远不变。
- `WindowDropdownPreference`（弹窗式）用于选项 ≥ 4 的场合；选项 2–3 个用 `SwitchPreference` 或 `RadioButtonPreference` 直接铺开。

---

## 3. 关于页

### 结构：身份区 + 三张卡

```
[Logo 100dp squircle + 应用名 35sp Bold + 版本号]   ← 浮动层，随滚动淡出
[Card] 更新检查 / 直接更新
[Card] 致谢（单独一张）
[Card] 源码 / Telegram / QQ 群 / 反馈
[Card] 许可证 / 依赖清单
```

- **致谢单独一张卡**，不塞进许可证卡。注释写得很清楚：它是这个页面里唯一「写人」而不是「写许可」的部分，
  放进许可证卡里会被当成又一条许可证。
- 所有链接都是 `ArrowPreference(title, summary, onClick)`，summary 写「点了会去哪」而不是重复 URL。
- 许可证与依赖清单是**两个独立条目**，各占一行，都跳二级页。

### Logo 区：随滚动淡出的浮动层

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(top = logoPadding.calculateTopPadding() + 52.dp, /* start/end */)
        .onSizeChanged { size ->
            val measured = with(density) { size.height.toDp() }
            // 列表里有个 Spacer 预留这个高度，每次写入都会触发列表重新布局。
            // Compose 本身会丢掉同值写入；这里再丢掉「差不到 1 像素」的写入，
            // 因为除了 logo 真的变了尺寸，不可能有别的变化。
            if (abs(measured.value - logoHeightDp.value) >= 1f) logoHeightDp = measured
        },
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(100.dp)
            .graphicsLayer {
                val iconProgress = ((scrollProgressProvider() - 0.35f) / 0.15f).coerceIn(0f, 1f)
                clip = true
                alpha = 1 - iconProgress
                scaleX = 1 - (iconProgress * 0.05f)
                scaleY = 1 - (iconProgress * 0.05f)
            }
            .squircleClip(cornerRadius = 28.dp),
    ) {
        Image(
            modifier = Modifier.size(100.dp),
            painter = painterResource(R.drawable.ic_about_logo),
            contentDescription = null,
        )
    }
    MiuixText(
        modifier = Modifier.padding(top = 12.dp, bottom = 5.dp).graphicsLayer {
            val p = ((scrollProgressProvider() - 0.20f) / 0.15f).coerceIn(0f, 1f)
            alpha = 1 - p
            scaleX = 1 - (p * 0.05f)
            scaleY = 1 - (p * 0.05f)
        },
        text = appName,
        color = colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
        fontSize = 35.sp,
    )
    // 版本号：更早淡出（-0.05f）
}
```

要点：

1. **三层的淡出起点不同**：版本号 0.05、应用名 0.20、Logo 0.35，宽度都是 0.15。
   这样它们**依次**消失，不是一个整体忽然没了。
2. **`scrollProgressProvider()` 是 lambda，不是值**。传值的话每次滚动都会重组整个 logo 区；
   传 lambda 让 `graphicsLayer` 在绘制阶段读，只重绘不重组。
3. **`squircleClip(cornerRadius = 28.dp)`** 用在 100dp 图标上。28/100 是 HMC 的固定比例，
   对应 iOS 图标的圆角观感。miuix 的 squircle 比普通 `RoundedCornerShape` 更接近真实图标。
4. **`onSizeChanged` 里的 1px 容差**：列表里有一个 Spacer 预留这个高度，
   每次写都会重新布局列表。同值 Compose 会丢，但「差 0.4dp」不会，那 1px 容差是唯一可靠的判据。
5. **logo 区是浮在列表之上的独立 Column**，不是 `LazyColumn` 的 item。列表里只放一个等高 Spacer。
   这样滚动时 logo 可以独立做透明度动画，不会被 item 复用回收。

### 图标必须是 VectorDrawable 或位图

`painterResource(android.R.drawable.sym_def_app_icon)` 会崩：

```
java.lang.IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported
ex. PNG, JPG, WEBP
```

系统 adaptive-icon 是 `<adaptive-icon>` XML，Compose 的 `painterResource` 不认。
**只能指向自己 `res/drawable/` 下的 `<vector>` 或 png/jpg/webp。**
这个崩溃只在翻到关于页时触发，最容易漏测。

---

## 4. 弹窗

### 单按钮：撑满宽

```kotlin
WindowDialog(
    show = showMissing,
    title = stringResource(R.string.home_features_missing_dialog),
    summary = state.featuresMissing.map { featureName(it) }.joinToString("\n"),
    onDismissRequest = { showMissing = false },
) {
    val dismiss = LocalDismissState.current
    TextButton(
        modifier = Modifier.fillMaxWidth(),
        text = stringResource(R.string.home_features_missing_ok),
        onClick = { dismiss?.invoke() },
    )
}
```

### 双按钮：横排、等宽、取消在左、确认在右

```kotlin
WindowDialog(
    show = confirmRestart,
    title = stringResource(R.string.restart_stale),
    summary = stringResource(R.string.restart_confirm_summary),
    onDismissRequest = { confirmRestart = false },
) {
    val dismiss = LocalDismissState.current
    Row(horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.update_cancel),
            onClick = { dismiss?.invoke() },
        )
        Spacer(Modifier.width(20.dp))
        TextButton(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.restart_menu),
            onClick = { /* 执行 */ },
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}
```

硬规则：

| 项 | 值 | 违反后果 |
|---|---|---|
| 布局 | `Row` + `Arrangement.SpaceBetween` | `Column` 堆叠是 Material 写法，在 HyperOS 里显得没做完 |
| 每个按钮 | `Modifier.weight(1f)` | 不等宽，两按钮长度不一 |
| 间距 | `Spacer(Modifier.width(20.dp))` | 20dp 是固定值 |
| 顺序 | 取消左、确认右 | 反了会让误触 |
| 确认色 | `ButtonDefaults.textButtonColorsPrimary()` | 两个按钮同色，分不清主次 |
| 关闭 | `dismiss?.invoke()` | 直接改 `show = false` 会跳过退场动画 |

- `LocalDismissState.current` **必须在 content lambda 内读**，理由同「弹窗状态必须在 lambda 内读」。
- `onDismissRequest` 与按钮 `onClick` 是两条路径：前者是点外部/返回键，后者是点按钮。
  两个都要能关，且**关闭动作走 `dismiss?.invoke()`** 才会播退场动画。

### 不可提前关闭的告知弹窗

```kotlin
LaunchedEffect(showNotice) {
    if (!showNotice) return@LaunchedEffect
    countdown = PROVIDER_NOTICE_SECONDS
    while (countdown > 0) { delay(1_000); countdown-- }
}
WindowDialog(
    show = showNotice,
    title = title,
    summary = stringResource(R.string.lyrics_provider_missing),
    // Held for fifteen seconds, and held means held: an outside tap and the back gesture
    // both come through here, so there is no way out of it before the button unlocks.
    onDismissRequest = {
        if (countdown == 0) { showNotice = false; LyricsNotice.markSeen(context) }
    },
) {
    val dismiss = LocalDismissState.current
    TextButton(
        modifier = Modifier.fillMaxWidth(),
        text = if (countdown > 0) stringResource(R.string.lyrics_provider_got_it_wait, countdown)
               else stringResource(R.string.lyrics_provider_got_it),
        enabled = countdown == 0,
        onClick = { dismiss?.invoke() },
    )
}
```

- 倒计时期间 `onDismissRequest` **什么都不做**：点外部、返回手势都走这里，所以真的关不掉。
- 按钮文字**带倒计时数字**，而不是「确定」+ 一个灰按钮。用户知道还要等多久。
- 只在「必须让用户知道、且用户会本能划掉」的场合用（模块未安装、权限缺失）。
  普通确认不要用，强制停留会被当成 bug。

---

## 5. 状态卡：底纹图标出血

```kotlin
Card(
    colors = CardDefaults.defaultColors(color = statusColor),
    onClick = onRefresh,
    showIndication = true,
    pressFeedbackType = PressFeedbackType.Tilt,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 底纹图标：出血到右下角
        Box(
            modifier = Modifier.fillMaxSize().offset(27.dp, 31.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Icon(
                modifier = Modifier.size(110.dp),
                // 与 HMC 用的是同一个 ImageVector，不是自己画的
                imageVector = if (ok) Icons.Rounded.CheckCircleOutline
                else Icons.Rounded.ErrorOutline,
                tint = iconTint,
                contentDescription = null,
            )
        }
        // 正文是叠在图标之上的另一个 Box，不是与图标同层的 Column：
        // 用 Column 会把图标挤到标题下面去，出血就没了。
        Box(modifier = Modifier.fillMaxSize().padding(16.dp, 14.dp)) {
            Column {
                MiuixText(text = title, style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(1.dp))
                MiuixText(text = subtitle, style = MiuixTheme.textStyles.body2)
            }
        }
    }
}
```

- **图标 110dp、偏移 `(27.dp, 31.dp)`、右下对齐**。三个数值一起决定「出血」的量：
  图标大部分在卡外，只露出左上那一段弧。少一个都出不来这个效果。
- 图标 tint 用 `colorScheme.error.copy(alpha = 0.8f)` 或 `colorScheme.primary.copy(alpha = 0.8f)`，
  **不是实色**。实色底纹会盖过正文。
- 卡片颜色三档（`dynamicColor` / 暗色 / 亮色）各一套，不能只写一套：

```kotlin
val statusColor = if (dynamicColor) {
    if (ok) colorScheme.primaryContainer else colorScheme.errorContainer
} else if (darkTheme) {
    if (ok) Color(0xFF1A3825) else Color(0xFF3D1C1C)
} else {
    if (ok) Color(0xFFDFFAE4) else Color(0xFFFDE8E8)
}
```

- **副标题写下一步动作**，不是描述状态。例：「请在 KernelSU 管理器 → 超级用户 中允许本应用」，
  不是「未授权」。
- 整卡可点 = 刷新。`pressFeedbackType = PressFeedbackType.Tilt`（miuix 的倾斜反馈），
  `showIndication = true`。这两项在有 `onClick` 的 Card 上必须显式给，默认值是 `None` / `false`。

---

## 6. 顶栏渐进模糊的参数

```kotlin
object TopBarBlurConfig {
    const val BlurRadius: Float = 15f
    const val GradientCurve: Float = 10f
    const val SurfaceAlpha: Float = 0.3f
    val ScrollFadeDistance: Dp = 0.dp
}
```

- `BlurRadius = 15f`：顶栏只需要「内容从下面滑过时糊掉」，不需要磨砂玻璃。半径给大了，
  采样面积不变但每像素的核更大，帧率直接掉。
- `GradientCurve = 10f`：喂给 `ProgressiveBlur(angle, startFraction, endFraction, curve)` 的第四个参数。
  `curve > 1` 让模糊**集中在顶栏下半部分**，上半部分接近透明 —— 这正是 iOS/HyperOS 的观感。
  给 `1f`（线性）会显得整条栏都是糊的。
- `SurfaceAlpha = 0.3f`：叠加在模糊上的 surface 色透明度。低了顶栏上的文字在亮背景上看不清，
  高了模糊白做。0.3 是这两个约束的交点。
- `ScrollFadeDistance = 0.dp`：滚动时不额外淡出。顶栏常驻，不做「滚远了就消失」。

调用：

```kotlin
progressiveTextureBlur(
    backdrop = backdrop,
    shape = RectangleShape,
    gradient = ProgressiveBlur(
        angle = 90f,
        startFraction = 0f,
        endFraction = 1f,
        curve = TopBarBlurConfig.GradientCurve,
    ),
    blurRadius = TopBarBlurConfig.BlurRadius,
    colors = BlurDefaults.blurColors(
        blendColors = listOf(
            BlendColorEntry(color = surface.copy(alpha = TopBarBlurConfig.SurfaceAlpha)),
        ),
    ),
)
```

**注意 `progressiveTextureBlur` 没有 `curve` 参数**，`curve` 是 `ProgressiveBlur` 数据类的字段。
写成 `progressiveTextureBlur(..., curve = 10f)` 会编译失败：
`No parameter with name 'curve' found`。

---

## 7. 背景动效（OS2 / OS3）

HMC 的 `ui/component/effect/` 是一套**纯 Brush 绘制**的背景动效，不依赖任何图片资源：

| 文件 | 职责 |
|---|---|
| `BgEffectConfig.kt` | 参数：色板、速度、振幅、模糊量、设备类型适配 |
| `BgEffectPainter.kt` | 用 `Brush` + `kotlin.math.cos/sin` 画出流动光斑 |
| `BgEffectModifier.kt` | `Modifier.bgEffect(...)`，把绘制挂到任意容器上 |
| `BgEffectBackground.kt` | 组装：读配置 → 起动画时钟 → 调 painter |
| `OS2BgFrag.kt` / `OS3BgFrag.kt` | 两代系统的两套色板与运动曲线 |
| `DeviceType.kt` | 按屏幕尺寸/DPI 调参 |

关键约束：

1. **只用 `Brush` 和三角函数**，不用 `RuntimeShader`。这样在 API 33 以下也能跑，
   而且不吃 GPU 的 shader 编译时间。
2. **动画时钟是单一 `withFrameNanos` 循环**，不是多个 `animateFloatAsState`。
   多个独立动画各自请求帧，会互相打架。
3. **`DeviceType` 调参是必须的**：同样的振幅在 6.7 寸和 11 寸平板上观感完全不同。

什么时候用：**背景是「产品性格」的一部分**（音乐播放器、相册、天气）。
工具类 App（设置、文件管理、模块管理）不要加，纯属耗电。

---

## 8. 启动闪屏与夜间模式

### 冷启动白闪三件套

```kotlin
// 1) 窗口背景在 setContent 之前就设好
window.setBackgroundDrawable(ColorDrawable(AppSettings.surfaceArgb(this) ?: 0xFFFBFE))
// 2) 主题里的 windowSplashScreenBackground 与上面同色
// 3) 内容真正画出来再撤 splash
holdSplashUntilContentIsReady()
```

**`windowSplashScreenBackground` 是主题属性**，系统在主题解析时就定了，早于任何代码。
所以它必须写死在 `themes.xml` 里，且与代码里设的窗口背景同色。两处不一致就会闪一下。

### 夜间模式在 onStop 应用

```kotlin
override fun onStop() {
    super.onStop()
    AppSettings.applyNightMode(this)   // 会触发 configuration change → 重建 Activity
}
```

- 放在 `onStop` 而不是设置页的开关回调里：**别让用户看着 Activity 重建**。
- 只在值真的变化时才写（`AppSettings` 内部比对），否则每次切后台都重建一次。

`nightModeFor(themeMode)`：

| themeMode | UiModeManager |
|---|---|
| `Light` / `MonetLight` | `MODE_NIGHT_NO` |
| `Dark` / `MonetDark` | `MODE_NIGHT_YES` |
| 其它（`System` / `MonetSystem`） | `MODE_NIGHT_AUTO` |

### settingsEpoch：回到前台重读设置

```kotlin
override fun onResume() {
    super.onResume()
    settingsEpoch++    // 主题页是独立 Activity，在本页 stopped 期间保存
}
```

主题页是独立 Activity，它保存设置时本页是 stopped 状态。**不在 `onResume` 重读，就会停在旧主题上**。
用一个自增的 `epoch` 作为 `remember` 的 key，比在每个设置项上挂回调简单得多。

---

## 9. 图标策略

| 需求 | 做法 |
|---|---|
| 通用操作（返回、更多、搜索、删除） | `MiuixIcons.*`，100+ 个已内置 |
| 品牌图形（应用图标、关于页 logo） | **自己画 vector**，放 `res/drawable/` |
| 状态符号（勾、感叹号、警告） | `material-icons-extended` 的 `Icons.Rounded.*` |
| 桌面图标 | `res/mipmap-anydpi-v26/ic_launcher.xml` + foreground/background 两张 vector |

### 状态符号为什么用 material-icons-extended

早期试过自绘：把 `CheckCircleOutline` 的 `pathData` 抄进自己的 `<vector>`，
勾用 `strokeWidth="2.2"` + 圆头。**失败过两次**：

1. 勾的包围盒偏心 —— 圆头会让端点外扩 1.1，手算的中心与视觉中心差 0.2~0.3px，
   110dp 出血后肉眼可见；
2. 圆环与勾的比例不对 —— 原版圆环内径、勾的粗细、端点圆角是一起设计的，
   单独改一项就不协调。

实测代价（2026-11-17，同一个工程）：

| 状态 | debug APK | release APK（R8） |
|---|---|---|
| 自绘 vector | 32.2 MB | 5.95 MB |
| material-icons-extended | 64.4 MB | 6.51 MB |

**release 只多 0.56 MB** —— R8 把未引用的图标类全裁了，进包的只有实际用到的两个
`ImageVector`。debug 大 32MB 是没裁剪的正常现象，不要拿它判断「图标库太重」。

所以：**要精确复刻某个 Material 图标，就直接引库；debug 包大不是理由。**
只有品牌图形（应用 logo、关于页图标）才必须自绘，那些库里没有。

品牌图形必须自绘：HMC 的 `ic_about_logo.xml` 只能当**技法参考**
（108dp viewport、三色渐变、白色卡片 + 描边圆 + 圆心），不能直接复制到别的产品里。

### 桌面图标隐藏

```kotlin
private const val SECRET_CODE = "95993926"

fun setLauncherVisible(context: Context, visible: Boolean) {
    val component = ComponentName(context, MainActivity::class.java)
    context.packageManager.setComponentEnabledSetting(
        component,
        if (visible) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP,
    )
}
```

- `DONT_KILL_APP` **必须给**，否则改完自己就被杀了。
- 隐藏后靠拨号盘/其他入口输 `SECRET_CODE` 恢复。适合模块类 App（入口在 LSPosed 里）。
- 普通 App 不要隐藏图标——用户找不到会以为没装上。

---

## 10. 宽屏适配

```kotlin
fun shouldShowSplitPane(): Boolean {
    val widthDp = LocalWindowInfo.current.containerSize.width.toDp()
    val ratio = widthDp / LocalWindowInfo.current.containerSize.height.toDp()
    return widthDp >= 840.dp || (widthDp >= 600.dp && ratio < 1.2f)
}
```

- `>= 840.dp`：平板横屏，直接双栏。
- `>= 600.dp 且宽高比 < 1.2`：折叠屏展开态（接近方形），也走双栏。
- 手机横屏（宽高比 > 1.2）**不**走双栏——高度不够，双栏反而难用。

宽屏下顶栏用 `SmallTopAppBar`（50dp 高），手机用 `TopAppBar`（52dp 高 + 大标题）。

---

## 11. 内容内边距的统一算法

```kotlin
@Composable
fun pageContentPadding(
    innerPadding: PaddingValues,
    outerPadding: PaddingValues,
    isWideScreen: Boolean,
    extraTop: Dp = 0.dp,
    extraStart: Dp = 0.dp,
    extraEnd: Dp = 0.dp,
): PaddingValues {
    val topPadding = innerPadding.calculateTopPadding() + extraTop
    val bottomPadding = if (isWideScreen) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
            outerPadding.calculateBottomPadding()
    } else {
        outerPadding.calculateBottomPadding()
    }
    return remember(topPadding, bottomPadding, extraStart, extraEnd) {
        PaddingValues(top = topPadding, start = extraStart, end = extraEnd, bottom = bottomPadding)
    }
}
```

- 手机：底部只留 `outerPadding`（底栏高度）。导航栏 inset 已经包含在底栏里了。
- 宽屏：底栏变成侧边栏，导航栏 inset **要额外加**，否则内容会被手势条盖住。
- `remember` 的 key 是四个算出来的值，不是入参——入参对象每次重组都是新实例。

---

## 12. 一页纸结论

1. **条件相关的行用 `FoldUnder` 折叠**，不置灰；`settled` 参数防止进页面时误播动画。
2. **设置页 > 12 行就拆主题二级页**；会改变外观的项放最上，引起折叠的开关放最下。
3. **主题页的值从宿主传，页面不读不写**。
4. **关于页 = 身份区 + 更新卡 + 致谢卡 + 链接卡 + 许可卡**；logo 用 100dp squircle，三层依次淡出。
5. **`painterResource` 只认 VectorDrawable 与位图**，系统 adaptive-icon 会崩。
6. **弹窗按钮横排等宽，取消左确认右**，关闭走 `dismiss?.invoke()`。
7. **状态卡底纹图标 110dp + offset(27,31) + 右下对齐**，tint 带 0.8 alpha。
8. **顶栏模糊 `BlurRadius=15` / `curve=10` / `SurfaceAlpha=0.3`**，`curve` 在 `ProgressiveBlur` 里。
9. **背景动效只用 Brush + 三角函数**，单一 `withFrameNanos` 时钟。
10. **夜间模式在 `onStop` 应用**，`settingsEpoch` 在 `onResume` 自增。
11. **状态符号引 material-icons-extended（release 只多 0.56MB），品牌图形自绘**。
12. **宽屏判据 840dp 或 (600dp 且比例 < 1.2)**。
