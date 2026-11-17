# 性能、缓存与状态管理

适用 Miuix 0.9.4（`top.yukonga.miuix.kmp`，Android 用 `-android` 变体）。规则来源：`zyl6932/vcut_lower`（minSdk 35 / compileSdk 37 / Miuix 0.9.4-rc01）与 `zyl6932/HyperMusicCover`。逐条对照执行，违反哪条就在低端机上掉帧或耗电。

## 1. 重组与稳定性

### 1.1 状态类型必须稳定

- 页面状态一律 `data class`，字段全 `val`，成员只允许基本类型 / `String` / 枚举 / 其它 data class / `ImmutableList`。
- 禁止 `Map<String, Any>`、`List<Any>`、`Array<*>` 出现在 composable 参数里：编译器无法证明稳定性，每次重组都当作已变化，整棵子树重画。
- 反例 `class Status { var level = 0 }`：引用比较的 `equals` 让值变了也不触发重组，界面卡在旧值。

```kotlin
data class VcutStatus(
    val checked: Boolean = false, val level: Int = 0,
    val charging: Boolean = false, val temperature: Float = 0f,
)
```

### 1.2 `remember` 与 `derivedStateOf` 分工

- `remember(key)`：缓存**对象构造**（backdrop、Paint、格式化器、颜色表），key 变了才重建。
- `derivedStateOf`：缓存**从 State 读出的派生值**，只在派生结果变化时才让读取方重组。高频源 + 低频结果必须走它：

```kotlin
val pagerState = rememberPagerState(pageCount = { 4 })
// currentPage 随滑动 offset 高频变化，直接读会让 About 页每帧重组
val onAboutPage by remember { derivedStateOf { pagerState.currentPage == 3 } }
```

- 不要在 `derivedStateOf` 里做 IO、分配大对象、调系统服务；它跟着重组节奏跑。

### 1.3 禁止在 composable 里做 IO

- 禁止在 composable 体内（含 `if` 分支、`Row` 内联 lambda）读 SharedPreferences、读文件、查数据库、调 `Vcut.status()`、发网络请求；正确位置是 `LaunchedEffect` / `produceState` / repository 的 `StateFlow`。

```kotlin
// 错：每次重组都取 prefs + 主线程读盘
val mode = LocalContext.current.getSharedPreferences("cfg", 0).getInt("theme", 0)
// 对：仓库持 StateFlow，页面只订阅
val mode by repo.themeMode.collectAsStateWithLifecycle()
```

### 1.4 `@Stable` / `@Immutable`

- 全 `val` 且成员同样不可变 → `@Immutable`，编译器可跳过 `equals` 比较；内部持 `mutableStateOf` 的可变持有者 → `@Stable`（对外可观察，不依赖 `equals`）。
- 值对象漏标就是自愿多付比较成本；但**不要**给含可变集合的类标 `@Immutable` 骗编译器，结果是该重组时不重组。

```kotlin
@Immutable
data class BlurSpec(val radius: Float, val surfaceAlpha: Float)
```

### 1.5 `rememberUpdatedState` 与 lambda 稳定性

- 长生命周期 effect 里要用「最新」回调时先 `rememberUpdatedState` 再读，否则改回调会重启整个 effect（轮询重来、动画跳帧）。
- 传给子 composable 的 lambda 用方法引用或 `remember` 包一层，避免每次重组新建实例让子组件无法跳过。

```kotlin
@Composable
fun AutoRefresh(onTick: () -> Unit) {
    val latest by rememberUpdatedState(onTick)
    LaunchedEffect(Unit) { while (true) { latest(); delay(3_000) } }
}
```

### 1.6 LazyColumn 的 item key

- 必须给 `key`，取**稳定业务 id**（`it.id` / `it.packageName`），不能取整个对象。反例 `key = { it }`：内容一变 key 就变，item 被销毁重建，滚动位置、`animateItem()`、内部焦点全丢。
- key 在列表生命周期内必须唯一；重复 key 直接抛 `IllegalArgumentException`。

```kotlin
items(items = rows, key = { it.id }) { row -> RowItem(row) }
```

## 2. miuix-blur 的真实开销与降级

### 2.1 API 33 是硬门槛

- `miuix-blur` 依赖 `RuntimeShader`，Android 侧要 **API 33（TIRAMISU）**；低版本不要尝试模糊，直接回退纯色。能力检测必须在**构造 backdrop 之前**，不是使用时：

```kotlin
@Composable
fun rememberBlurBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported() || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop { drawRect(surfaceColor); drawContent() }
}

val backdrop = rememberBlurBackdrop()
val blurActive = isBlurEnabled && backdrop != null
val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
```

- `backdrop == null` 时**不得**继续调 `textureBlur(backdrop = null, ...)`，也不要把 alpha 调成半透明假装模糊。

### 2.2 backdrop 采样成本

- backdrop 的本质是把整块区域重绘到离屏层再采样：该层每帧渲染两遍，分辨率越高、面积越大越贵。成本 ≈ 采样面积 × 层数 × 帧率。三条推论：

1. **只允许一层 `layerBackdrop`。** 嵌套两层就是串行重采样两遍，成本翻倍，表面色还叠加两次导致对比度失真。
2. **顶栏用 `progressiveTextureBlur`，不要全屏 blur。** 顶栏只有几十 dp 高，成本与顶栏高度成正比，而不是整屏面积。
3. **不要放进 LazyColumn item。** 每个 item 一层 backdrop，滚动时几十层同时重采样，帧率直接崩。

### 2.3 `TopBarBlurConfig` 的取值理由

```kotlin
val topBarBlur = TopBarBlurConfig(
    blurRadius = 15f,          // 15dp 半径
    gradientCurve = 10f,       // 渐变曲线强度
    surfaceAlpha = 0.3f,       // 叠 30% 表面色
    scrollFadeDistance = 0.dp, // 不做滚动淡出
)
```

- `blurRadius = 15f`：15 之后视觉收益快速递减，但采样核半径继续线性上涨，成本白付。
- `gradientCurve = 10f`：控制模糊强度沿高度的过渡长度，曲线越陡过渡越窄；10f 能在顶栏高度内收干净，不留可见硬边。
- `surfaceAlpha = 0.3f`：模糊后底图亮度不可控，叠一层 30% 表面色把文字对比度锁在可读区间；调到 0 会在浅色壁纸上糊成一片。
- `scrollFadeDistance = 0.dp`：非 0 意味着滚动时每帧改 alpha，触发额外合成层重绘；顶栏本来就随滚动折叠，再叠淡出既贵又抖。模糊开关要持久化，关闭时 `blurActive = false`、backdrop 返回 `null`，不能「关了但还在采样」。

## 3. 列表与 Pager 性能

### 3.1 `beyondViewportPageCount = 1`

```kotlin
HorizontalPager(
    state = pagerState,
    beyondViewportPageCount = 1,   // 前后各预组合一页
    overscrollEffect = null,       // 回弹交给 Miuix
) { page -> PageContent(page) }
```

- 0 会让相邻页在滑动第一帧才开始组合，重页面必掉帧；1 是内存与手感的平衡点。页面很重（大图、图表、地图）时降回 0，用「滑动开始再组合」的代价换内存。

### 3.2 `overscrollEffect = null` 的原因

- 统一写法：`LazyColumn(overscrollEffect = null)` + `Modifier.overScrollVertical()` + `scrollEndHaptic()` + `nestedScroll(scrollBehavior.nestedScrollConnection)`。
- 必须置 null：Compose 自带 stretch 与 Miuix `overScrollVertical()` 会**叠两层位移**，回弹距离翻倍，且两层都在改绘制矩阵。二者只能留一个，留 Miuix 的（回弹曲线与 hdc 反馈和系统一致）。
- `nestedScroll` 负责把滚动量喂给顶栏折叠，缺了它顶栏永远不收起。

```kotlin
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .overScrollVertical()
        .nestedScroll(scrollBehavior.nestedScrollConnection)
        .scrollEndHaptic(),
    overscrollEffect = null,
) { /* items */ }
```

### 3.3 item 内不要新建对象

- 禁止在 item 体内：`listOf(...)`、现算 `Color(0xFF...)`、`Dp` 运算链、`String.format`、`SimpleDateFormat()`。颜色/尺寸/格式化结果用 `remember` 或文件顶层 `private val`：

```kotlin
private val SubTextColor = Color(0xFF6B6B6B)

@Composable
fun RowItem(row: Row) {
    Text(remember(row.timestamp) { formatTime(row.timestamp) }, color = SubTextColor)
}
```

- 卡片间距用现成扩展，不要手算 padding 制造新 `Modifier` 链：`Modifier.firstCard() = padding(12.dp)`、`Modifier.nextCard() = padding(horizontal = 12.dp).padding(bottom = 12.dp)`。

### 3.4 图标按需引入 + R8

- 只 import 实际用到的图标，禁止 `import androidx.compose.material.icons.filled.*` 通配，禁止把整组图标塞进 `when` 默认分支。
- `material-icons-extended` 有几十 MB 生成代码，只有 `isMinifyEnabled = true` + `isShrinkResources = true` 才能裁掉未引用部分；debug 包大是正常的，不要因此判定「图标库太重」。
- 长列表不要用 `Column` + `verticalScroll`：全部 item 一次性组合，滚动必然掉帧。

## 4. 状态轮询与生命周期

### 4.1 只在 RESUMED 轮询

```kotlin
@Composable
fun rememberVcutStatus(pollMs: Long = 3_000): VcutStatus {
    var status by remember { mutableStateOf(Vcut.last ?: Vcut.Status()) }
    var checked by remember { mutableStateOf(Vcut.last != null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { status = Vcut.status(); checked = true; delay(pollMs) }
        }
    }
    return status
}
```

- 必须是 `RESUMED`，不是 `STARTED`：`STARTED` 在锁屏、多任务预览、被半透明页盖住时仍在跑，白耗电。
- `repeatOnLifecycle` 在离开 RESUMED 时**取消整个块**：切后台自动停，回前台自动重启并立刻读一次，不需要额外启停代码。
- 循环内**先读再 delay**，保证进页面立即有值，而不是先空 3 秒。禁止 `Handler.postDelayed` 无限递归或 `Application` 级定时器：它们不随页面生命周期停。

### 4.2 为什么是 3 秒

- 单次 `Vcut.status()` 是轻量系统读取，成本低但不是零；3 秒时用户手动操作（插拔、切档）的反馈延迟已在感知阈值附近。低于 1 秒没有信息增量，只增加与 UI 帧竞争的机会，并让 Binder 调用密度上去。
- 需要更快反馈的场景用手动 refresh（下拉刷新、按钮点击立即读一次），不要全局把间隔压到 500ms。

### 4.3 进程内缓存 = 首屏不为空

- `Vcut.last ?: Vcut.Status()`：进程级静态字段存上次结果，新页面直接用缓存值渲染，不必从空状态等第一次读完。
- 缓存只在内存，进程被杀自然失效，**不需要** TTL、版本号、失效策略。写入点只有一个：每次轮询读完立刻写回 `last`，手动 refresh 也写回，下次轮询自然对齐。

## 5. 缓存分层

| 层 | 载体 | 存什么 | 存多久 |
| --- | --- | --- | --- |
| 内存 | 进程级 `@Volatile var last` | 瞬时状态、上一次读数 | 进程存活期 |
| 磁盘 | SharedPreferences / JSON 文件 | 用户意图：主题、模糊开关、轮询间隔 | 直到用户改 |
| 跨进程 | 广播 / Binder / ContentProvider | Activity 与 Service 共享的配置与状态 | 按需 |

- 原则：**瞬时状态只进内存，用户意图才落盘。** 把电量、温度每秒写盘是最常见的耗电来源；落盘要 debounce，连续变更合并成一次 `apply()`，不要每个 tick 都 `commit()`。

### 5.1 「两份真相」反例

```kotlin
// 错：两个写入点，各自维护一份「当前主题」
object ThemeGlobal { var mode: Int = 0 }   // MainActivity 读它
prefs.edit().putInt("theme", 1).apply()    // 设置页只改了盘，没改 ThemeGlobal.mode
```

- 症状：设置页显示「深色」，返回主界面还是浅色；杀掉进程重进又对了。正确做法是**单一写入点 + 单向数据流**：仓库持 `StateFlow`，UI 只读它，写盘是副产物而不是第二真相。

```kotlin
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(prefs.getInt(KEY_THEME, 0))
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()
    fun setThemeMode(mode: Int) {
        _themeMode.value = mode                        // 内存先变，UI 立即响应
        prefs.edit().putInt(KEY_THEME, mode).apply()   // 盘是副产物
    }
}
```

- 检查方法：全局搜 `getSharedPreferences`，只应出现在 repository 里；UI 层出现第二次就是两份真相。

### 5.2 SettingsBackup：导入 / 导出

- 格式用带版本号的 JSON，未知字段忽略，缺字段用默认值；导出走 SAF `CreateDocument`，导入走 `GetContent`，不需要任何存储权限。
- 导入后必须 `recreate()`：主题在 `AppTheme` 顶层读取，静态仓库值变了不会让已存在的 Compose 树重新取值，只有重建 Activity 才会重新读。

```kotlin
@Serializable
data class SettingsBackup(
    val version: Int = 1, val themeMode: Int = 0,
    val blurEnabled: Boolean = true, val pollIntervalMs: Long = 3_000,
)

val exportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/json")
) { uri -> uri ?: return@rememberLauncherForActivityResult
    context.contentResolver.openOutputStream(uri)?.use { out ->
        out.write(Json.encodeToString(repo.snapshot()).toByteArray())
    }
}

val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    uri ?: return@rememberLauncherForActivityResult
    val text = context.contentResolver.openInputStream(uri)
        ?.bufferedReader()?.use { it.readText() } ?: return@rememberLauncherForActivityResult
    val backup = runCatching { Json.decodeFromString<SettingsBackup>(text) }.getOrNull()
        ?: return@rememberLauncherForActivityResult
    repo.restore(backup); activity.recreate()   // 让 AppTheme 重新读取主题
}
```

## 6. 构建期优化

```kotlin
android {
    defaultConfig {
        ndk { abiFilters += "arm64-v8a" }              // 只出 arm64
        androidResources { localeFilters += "zh" }      // 只打中文资源
    }
    buildTypes { release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    } }
    packaging { resources.excludes += setOf(
        "META-INF/LICENSE", "META-INF/*.kotlin_module", "DebugProbesKt.bin") }
    buildFeatures { compose = true; buildConfig = true }
}
```

- `isMinifyEnabled`：R8 裁掉未引用的 Compose 生成代码与图标；不开的话 `material-icons-extended` 会整个进包。`isShrinkResources` 删掉未被引用的资源，依赖 minify 才生效。
- `abiFilters arm64-v8a`：现代设备只装 arm64，省掉整份 armeabi-v7a native 库。`localeFilters zh`：只保留中文资源，去掉几十种语言。
- `packaging.resources.excludes`：`META-INF/LICENSE`、`META-INF/*.kotlin_module`、`DebugProbesKt.bin` 三类纯噪音，体积与安装校验时间都省。
- `buildConfig = true` 只在代码里读 `BuildConfig.DEBUG` / 版本号时开，否则关掉减少生成类。
- baselineProfile：给冷启动 + Pager 首屏录一份（`dependencies { baselineProfile(project(":baselineprofile")) }`），AOT 编译热点路径后首帧明显提前；profile 要随界面改版重录，过期无收益。
- 混淆注意：参与 JSON 序列化的 data class 需 `@Keep` 或 serialization 自带 keep 规则，否则 R8 删字段导致反序列化后全是默认值。

## 7. 性能自查清单

| 检查项 | 判定标准 | 违反后果 |
| --- | --- | --- |
| 状态类型 | 全 `data class` + 全 `val`，无 `Map<String, Any>` / `List<Any>` 传参 | 编译器判定不稳定，每次重组重画整棵子树 |
| 派生值 | 高频源（`currentPage`、滚动偏移）经 `derivedStateOf` 收敛 | 每帧重组，逐帧动画与列表同时抖动 |
| composable 内 IO | 体内无 prefs / 文件 / 数据库 / 系统状态读取 | 主线程 IO，滚动卡顿甚至 ANR |
| lambda 传递 | 用方法引用或 `remember` 包裹 | 子组件每次重组都失效，跳过优化全废 |
| LazyColumn key | 用稳定业务 id，不用整个对象 | item 重建，滚动位置与动画丢失 |
| backdrop 层数 | 全树只有一层 `layerBackdrop` | 每帧串行重采样多次，帧率腰斩 |
| blur 降级 | 构造前判 `isRuntimeShaderSupported()` 与 API 33 | 低版本崩溃或黑屏 |
| blur 位置 | 顶栏用 `progressiveTextureBlur`，item 内无 blur | 成本随屏幕面积 / 列表项数线性上涨 |
| blur 开关 | 关闭时 backdrop 返回 `null` 且走纯色 | 关了仍在采样，白付全部开销 |
| Pager 预组合 | `beyondViewportPageCount = 1`（重页面可回 0） | 滑动首帧才组合，必然掉帧 |
| 回弹效果 | `overscrollEffect = null` 且只留 Miuix `overScrollVertical()` | 两层回弹叠加，位移翻倍 |
| 轮询生命周期 | `repeatOnLifecycle(RESUMED)` | 后台持续轮询，待机耗电 |
| 轮询间隔 | ≥ 3000ms，更快反馈走手动 refresh | 与 UI 帧竞争，无信息增量 |
| 首屏状态 | 命中进程内缓存，不从空状态起 | 每次进页面先闪一下空态 |
| 落盘内容 | 只存用户意图，瞬时状态留内存 | 每秒写盘，发热与耗电 |
| 真相数量 | 全局只有一处 `getSharedPreferences` | 设置不生效，需重启进程 |
| release 构建 | minify + shrinkResources + abiFilters + localeFilters + packaging excludes 全开 | 包体多出几十 MB，安装与冷启动变慢 |
