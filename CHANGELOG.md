# 状态卡变更（照搬 HyperMusicCover 原版）

> 给已经按旧版 skill 铺过模板的工程。**只涉及 `ui/screens/HomePage.kt` 的 `StatusCard`**，
> 数据类 `AppStatus` 与页面其余部分不变。

## 一、图标来源：自绘 vector → material-icons-extended

旧版为省体积自绘了 `res/drawable/ic_check_circle.xml` 与 `ic_error_circle.xml`。
**这两份删掉**，改用 HMC 用的同一个 `ImageVector`。

### 1. 加依赖

`gradle/libs.versions.toml`：

```toml
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
```

`app/build.gradle.kts`：

```kotlin
implementation(libs.androidx.compose.material.icons.extended)
```

> 离线工程只能吃本地缓存时写死 `version = "1.7.8"`（缓存里就这一版）。

### 2. 改 import

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
```

删掉 `import androidx.compose.ui.res.painterResource`（若没有别处用到）。

### 3. 改 Icon 调用

旧：

```kotlin
painter = painterResource(
    if (ok) R.drawable.ic_check_circle else R.drawable.ic_error_circle,
),
```

新：

```kotlin
imageVector = if (ok) Icons.Rounded.CheckCircleOutline
else Icons.Rounded.ErrorOutline,
```

### 4. 删两个 drawable

```sh
rm app/src/main/res/drawable/ic_check_circle.xml
rm app/src/main/res/drawable/ic_error_circle.xml
```

### 为什么值得引库

自绘的勾怎么调都不如原版：

- 圆头让端点外扩 1.1，手算的中心与视觉中心差 0.2~0.3px，110dp 出血后肉眼可见；
- 圆环内径、勾的粗细、端点圆角是一起设计的，单独改一项就不协调。

### 体积代价（实测，同一工程）

| 状态 | debug APK | release APK（R8） |
|---|---|---|
| 自绘 vector | 32.2 MB | 5.95 MB |
| material-icons-extended | 64.4 MB | **6.51 MB** |

release 只多 0.56MB —— R8 把未引用的图标类全裁了，进包的只有实际用到的两个 `ImageVector`。
**debug 大 32MB 是没裁剪的正常现象，不要拿它判断「图标库太重」。**

---

## 二、布局：三行 Column → 三个 Box 叠放

旧版把标题、应用名当成 Column 里的两行。HMC 原版是**三个 Box 叠在同一张卡里**，
各自对齐到不同角，中间留白。

| 层 | 对齐 | padding | 内容 |
|---|---|---|---|
| ① 底纹图标 | `BottomEnd` + `offset(27.dp, 31.dp)` | — | `size(110.dp)` |
| ② 应用名 | `BottomStart` | `(16.dp, 10.dp)` | `body1` + `FontWeight.Medium` |
| ③ 标题 + 副标题 | `TopStart` | `(16.dp, 14.dp)` | `title3` SemiBold + `Spacer(1.dp)` + `body2` |

完整结构（照抄即可）：

```kotlin
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
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Box {
            // ① 底纹图标：offset 让它故意出血，只露右下角一角
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(27.dp, 31.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    modifier = Modifier.size(110.dp),
                    imageVector = if (ok) Icons.Rounded.CheckCircleOutline
                    else Icons.Rounded.ErrorOutline,
                    tint = iconTint,
                    contentDescription = null,
                )
            }
            // ② 左下角应用名
            if (status.appName.isNotEmpty()) {
                Box(
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
                        fontWeight = FontWeight.SemiBold,
                    )
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
```

### 两个不能省的点

**外层 `Row(height(IntrinsicSize.Min))` 必须留。** 里面三个 Box 都是 `fillMaxSize`、
本身不贡献高度，没有这层卡片会塌成文字那么高。

**三个 Box 叠放而不是 Column 的理由：** 模式与图标**斜向平衡**，中间留白才不显得挤。
换成 Column 会把它们挤成一团在顶部，图标也出不了血。

---

## 三、两个 padding 值不要按 12dp 栅格改

`16/10` 与 `16/14` 是 HMC 原值：

- 应用名要贴底但比卡内边距更靠下一点 —— 改成 `8.dp`（`DshSpacing.Compact`）会显得浮起来；
- 正文比应用名多 4dp 才与下方图标的视觉重心对得上 —— 改成 `12.dp` 标题会往上顶。

两处都要加行级豁免，否则检查脚本会报「卡片外的内边距出现 10.dp」：

```kotlin
.fillMaxSize()
// miuix-check: allow-design-tokens
.padding(16.dp, 10.dp),
```

> 豁免标记必须紧贴被豁免那一行（或它的上一行），隔一行不生效。

---

## 四、验证

```sh
python scripts/check_miuix_usage.py --path app
```

应为 `0 error(s), 0 warning(s)`。

真机看两态：

- `ok = true` → 绿卡（`secondaryContainer` / `#DFFAE4` / `#1A3825`），勾在圆内居中；
- `ok = false` → 红卡（`errorContainer` / `#FDE8E8` / `#3D1C1C`），感叹号居中。

对照点：勾与圆环的间距、圆环粗细、图标出血量（只露右下角一角）。

---

## 五、若你用 `scripts/scaffold_app.py` 铺模板

| 位置 | 改动 |
|---|---|
| `REQUIRED_LIBS` | 加 `"material-icons-extended"`（不属于 miuix，但模板必需） |
| `RES_LAYOUT` | 删 `ic_check_circle.xml` 与 `ic_error_circle.xml` 两行 |
| 提示语 | `[TODO] add these Miuix modules` → `[TODO] add these dependencies` |