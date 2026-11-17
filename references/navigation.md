# 导航（miuix-nav）

`miuix-nav` 是 0.9.4 引入的自包含导航运行时，**零依赖** `androidx.navigation3`。不要继续使用 `miuix-navigation3-ui`，也不要把它和 `androidx.navigation3` 的路由类型混用。

## 依赖

```kotlin
implementation("top.yukonga.miuix.kmp:miuix-nav:0.9.4")
```

路由需要序列化，工程应启用 Kotlin serialization 插件并添加 `kotlinx-serialization` 运行时（按工程已有配置）。

## 路由定义

```kotlin
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
sealed interface Route : NavKey {
    @Serializable data object Home : Route
    @Serializable data class Detail(val id: String) : Route
}
```

`NavKey` 来自 `top.yukonga.miuix.kmp.nav.core`，不是 `androidx.navigation3.runtime.NavKey`。

## 返回栈与 NavDisplay

```kotlin
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack

@Composable
fun App() {
    val backStack = rememberNavBackStack<Route>(Route.Home)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() }
    ) {
        entry<Route.Home> {
            HomeScreen(onOpen = { id -> backStack.add(Route.Detail(id)) })
        }
        entry<Route.Detail> { route ->
            DetailScreen(route.id, onBack = { backStack.removeLastOrNull() })
        }
    }
}
```

### 必须显式写父类型

`rememberNavBackStack` 是 `inline fun <reified T : NavKey>`。写成 `rememberNavBackStack(Route.Home)` 会把 `T` 推断为 `Route.Home`，之后 push 其他子类型时保存/恢复会序列化失败。正确写法是 `rememberNavBackStack<Route>(Route.Home)`。

## 返回栈操作

`rememberNavBackStack` 返回 `NavBackStack`（`SnapshotStateList<NavKey>`），可直接 `add` / `removeLastOrNull` / `removeAt`。

需要更语义化的操作时用 `NavController` 包装，提供 `push` / `pop` / `replace` / `popUntil`。`NavController` 的包路径请在该版本源码或 Dokka 中核对后再 import。

连续多步操作会作为一段连续运动播放：

```kotlin
backStack.add(Route.Detail("1"))
backStack.add(Route.Detail("2"))
backStack.add(Route.Detail("3"))

while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
```

## 转场

内置预设 `NavTransitions`：

| 预设 | 效果 |
|---|---|
| `MiuixDefault` | 全宽滑动 + 四分之一宽视差 + 被覆盖层轻微透明衰减（默认） |
| `Modal` | 上滑，下层保留可见 |
| `None` | 无动画 |

```kotlin
import top.yukonga.miuix.kmp.nav.transition.NavTransitions

NavDisplay(
    backStack = backStack,
    transition = NavTransitions.MiuixDefault
) {
    entry<Route.Home> { HomeScreen() }
    entry<Route.Detail>(transition = NavTransitions.Modal) { DetailScreen(it.id) }
}
```

## 滑动关闭

`swipeDismiss` 默认 `None`，内置预设也不会自动开启。按路由显式开启：

```kotlin
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection

entry<Route.Home>(swipeDismiss = NavSwipeDirection.LeftToRight) { HomeScreen() }
entry<Route.Sheet>(
    transition = NavTransitions.Modal,
    swipeDismiss = NavSwipeDirection.TopToBottom
) { SheetScreen() }
```

`NavSwipeDirection` 是物理手指方向，不随 RTL 镜像：LTR 返回用 `LeftToRight`，RTL 用 `RightToLeft`，底部 Modal 用 `TopToBottom`。

## 自定义转场

```kotlin
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition

val slideAndScale = navGraphicsTransition { scope ->
    val d = scope.relativeDepth
    translationX = -d * scope.layoutSize.width.toFloat()
    scaleX = 1f - 0.1f * d.coerceIn(0f, 1f)
    scaleY = scaleX
}
```

`NavTransitionScope` 提供 `relativeDepth`、`role`、`change`、`gesture`、`isRunning`、`settle`、`layoutSize`、`layoutDirection`、`density`。在 `graphicsLayer` 等延迟读取区读取 `relativeDepth`，避免每帧重组。

在 entry 内读取实时导航状态用 `LocalNavTransitionScope.current`；它只能在 `NavDisplay` 的 entry 内容中使用。

## 正交效果

`NavDisplayEffects` 控制转场之外的横切效果：

| 属性 | 默认值 | 说明 |
|---|---|---|
| `enableCornerClip` | `true` | 顶层 entry 运动时做平滑圆角裁剪 |
| `cornerClipRadius` | `0.dp` | 裁剪半径；可用 `rememberNavSystemCornerRadius()` |
| `cornerClipMode` | `Leading` | `Leading` 或 `All` |
| `dimAmount` | `0.5f` | 下层遮罩最大 alpha，`0f` 关闭 |

## 状态恢复与集成

- entry 会继承 SavedState 创建参数与 ViewModel 工厂，`SavedStateHandle`、Hilt 等集成可用。
- 返回事件支持跨独立 Android 窗口桥接。
- 0.9.4 起导航公开签名有变，旧版本预编译的库需重新编译。

## 迁移自 miuix-navigation3-ui

| 旧 | 新 |
|---|---|
| `top.yukonga.miuix.kmp:miuix-navigation3-ui:0.9.3` | `top.yukonga.miuix.kmp:miuix-nav:0.9.4` |
| `androidx.navigation3.*` 的 `NavKey` / `NavDisplay` / 返回栈 | `top.yukonga.miuix.kmp.nav.core.*` |
| 内置转场自动支持滑动关闭 | 需 `entry(swipeDismiss = ...)` 显式开启 |

不要做"只改依赖坐标"的迁移，导入与返回栈类型也要改。