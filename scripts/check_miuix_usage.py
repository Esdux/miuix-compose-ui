#!/usr/bin/env python3
"""Static checks for projects that use the Miuix Compose UI library.

Usage:
    python check_miuix_usage.py --path <project-or-file>

Exit code:
    0: no errors (warnings allowed)
    1: at least one error
    2: invalid arguments / path
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

SKIP_DIRS = {"build", ".gradle", ".git", ".idea", "node_modules", "out"}

MIUIX_GROUP = "top.yukonga.miuix.kmp:"

MIUIX_PACKAGES = (
    "top.yukonga.miuix.kmp.basic",
    "top.yukonga.miuix.kmp.overlay",
    "top.yukonga.miuix.kmp.preference",
    "top.yukonga.miuix.kmp.menu",
    "top.yukonga.miuix.kmp.window",
    "top.yukonga.miuix.kmp.theme",
    "top.yukonga.miuix.kmp.icon",
    "top.yukonga.miuix.kmp.utils",
    "top.yukonga.miuix.kmp.nav",
)

OVERLAY_CALL = re.compile(
    r"\b(?:OverlayDialog|OverlayBottomSheet|OverlayListPopup|"
    r"OverlayCascadingListPopup|OverlayDropdownPreference|"
    r"OverlaySpinnerPreference|OverlayDropdownMenu|"
    r"OverlayIconDropdownMenu|OverlayIconCascadingDropdownMenu)\s*\("
)

ICON_USE = re.compile(r"\bMiuixIcons(?:\.[A-Za-z_]\w*)*\.([A-Za-z_]\w*)")

MIN_SDK_PATTERNS = (
    re.compile(r"\bminSdk\s*=\s*(\d+)"),
    re.compile(r"\bminSdk\s*\(\s*(\d+)\s*\)"),
    re.compile(r"\bminSdkVersion\s*\(?\s*(\d+)"),
)

BLUR_HINT = re.compile(r"miuix-(?:blur|glass)(?:-[\w-]+)?[:\"'\s]")


class Finding:
    def __init__(self, level: str, path: Path, line: int | None, message: str):
        self.level = level
        self.path = path
        self.line = line
        self.message = message

    def render(self) -> str:
        location = str(self.path)
        if self.line is not None:
            location = f"{location}:{self.line}"
        return f"[{self.level}] {location} {self.message}"


def iter_files(root: Path, suffix: str):
    if root.is_file():
        if root.suffix == suffix:
            yield root
        return
    for path in root.rglob(f"*{suffix}"):
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        if path.is_file():
            yield path


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return ""


def check_kotlin_files(root: Path, findings: list[Finding]) -> None:
    project_uses_overlay = False
    project_has_scaffold = False
    project_uses_theme = False
    project_uses_ui = False
    project_uses_blur = False
    project_has_blur_guard = False

    for path in iter_files(root, ".kt"):
        text = read(path)
        # Match against the comment-stripped copy: the templates document the
        # anti-patterns in comments, and matching those yields false positives.
        match_text = strip_comments(text)
        lines = text.splitlines()
        imports = [line.strip() for line in lines if line.strip().startswith("import ")]

        uses_miuix = any(pkg in text for pkg in MIUIX_PACKAGES) or any(
            "top.yukonga.miuix.kmp" in imp for imp in imports
        )
        if not uses_miuix:
            continue

        has_scaffold = re.search(r"\bScaffold\s*[\(\{]", text) is not None
        project_has_scaffold = project_has_scaffold or has_scaffold

        if "MiuixTheme" in text:
            project_uses_theme = True
        if any(pkg in text for pkg in MIUIX_PACKAGES[:5]):
            project_uses_ui = True
        if "top.yukonga.miuix.kmp.blur" in text:
            project_uses_blur = True
        if re.search(r"\b(?:isRuntimeShaderSupported|isRenderEffectSupported)\s*\(", text):
            project_has_blur_guard = True

        import_material3 = any("androidx.compose.material3" in imp for imp in imports)
        if import_material3:
            findings.append(
                Finding(
                    "WARNING",
                    path,
                    None,
                    "同一文件同时引入了 Material3 与 Miuix；同一页面应使用同一套 UI 组件。",
                )
            )

        overlay_match = OVERLAY_CALL.search(text)
        if overlay_match:
            project_uses_overlay = True
            if re.search(r"popupHost\s*=\s*\{\s*\}", text):
                findings.append(
                    Finding(
                        "ERROR",
                        path,
                        text.count("\n", 0, overlay_match.start()) + 1,
                        "Scaffold 的 popupHost 被置空，但同文件使用了 Overlay 组件；Overlay 将无法显示。改用 Window 级弹层或恢复默认 popupHost。",
                    )
                )
            if not has_scaffold:
                findings.append(
                    Finding(
                        "WARNING",
                        path,
                        text.count("\n", 0, overlay_match.start()) + 1,
                        "使用了 Overlay 组件，但本文件没有 Scaffold；请确认运行时位于某个 Scaffold 子树内。",
                    )
                )

        imported_icon_names = set()
        for imp in imports:
            if ".kmp.icon." in imp:
                imported_icon_names.add(imp.rsplit(".", 1)[-1])

        for match in ICON_USE.finditer(match_text):
            icon = match.group(1)
            if icon in {"Glass", "Light", "Normal", "Regular", "Medium", "Demibold"}:
                continue
            if icon not in imported_icon_names:
                findings.append(
                    Finding(
                        "WARNING",
                        path,
                        text.count("\n", 0, match.start()) + 1,
                        f"MiuixIcons.{icon} 未发现对应 import；图标扩展属性需逐个 import（如 top.yukonga.miuix.kmp.icon.extended.{icon}）。",
                    )
                )
                break

        if re.search(r"\bCheckbox\s*\(\s*checked\s*=", match_text):
            findings.append(
                Finding(
                    "WARNING",
                    path,
                    None,
                    "Miuix Checkbox 使用 state: ToggleableState，而不是 checked；请核对参数。",
                )
            )

        if re.search(r"\bInputField\s*\([^)]*\bvalue\s*=", text, re.S):
            findings.append(
                Finding(
                    "WARNING",
                    path,
                    None,
                    "Miuix InputField 使用 query/onQueryChange；value/onValueChange 属于 TextField。",
                )
            )

        uses_damped_overscroll = "overScrollVertical" in text or "overScrollHorizontal" in text
        has_lazy_list = re.search(r"\b(?:LazyColumn|LazyRow)\s*\(", text) is not None
        has_native_overscroll = re.search(r"overscrollEffect\s*=\s*null", text) is not None
        if uses_damped_overscroll and has_lazy_list and not has_native_overscroll:
            findings.append(
                Finding(
                    "WARNING",
                    path,
                    None,
                    "使用 overScrollVertical/overScrollHorizontal 时，应为同一列表设置 overscrollEffect = null，避免两套回弹叠加。",
                )
            )

    if project_uses_overlay and not project_has_scaffold:
        findings.append(
            Finding(
                "ERROR",
                root,
                None,
                "项目使用了 Overlay 组件，但全项目未发现 Scaffold；Overlay 弹层将无法正常显示。",
            )
        )

    if project_uses_ui and not project_uses_theme:
        findings.append(
            Finding(
                "WARNING",
                root,
                None,
                "使用了 Miuix UI 组件但未发现 MiuixTheme；页面可能拿不到 Miuix 配色与文本样式。",
            )
        )

    if project_uses_blur and not project_has_blur_guard:
        findings.append(
            Finding(
                "WARNING",
                root,
                None,
                "使用了 miuix-blur 但未发现 isRuntimeShaderSupported/isRenderEffectSupported 能力判断；低版本 Android 可能崩溃或无效果。",
            )
        )


def check_gradle_files(root: Path, findings: list[Finding]) -> None:
    gradle_files = list(iter_files(root, ".gradle.kts")) + list(iter_files(root, ".gradle"))
    if root.is_file() and root.suffix == ".kts":
        gradle_files = [root]
    if not gradle_files:
        return

    combined = "\n".join(read(p) for p in gradle_files)
    uses_miuix = MIUIX_GROUP in combined

    if not uses_miuix:
        return

    direct_versions = {
        m.group(1)
        for m in re.finditer(
            r"top\.yukonga\.miuix\.kmp:[A-Za-z0-9_.-]+:([A-Za-z0-9_.-]+)", combined
        )
        if not m.group(1).startswith("$")
    }
    toml_versions = {
        m.group(1)
        for m in re.finditer(
            r"^\s*miuix\w*\s*=\s*[\"']([0-9][^\"']*)[\"']", combined, re.MULTILINE
        )
    }
    versions = direct_versions | toml_versions
    if len(versions) > 1:
        findings.append(
            Finding(
                "ERROR",
                root,
                None,
                f"发现多个 Miuix 版本：{', '.join(sorted(versions))}；所有 top.yukonga.miuix.kmp:* 必须同版本。",
            )
        )

    if re.search(r"miuix[^\"'\s]*[:\"']\s*<version>", combined):
        findings.append(
            Finding("WARNING", root, None, "存在未替换的 <version> 占位符。")
        )

    if "mavenCentral()" not in combined:
        extra = []
        for name in ("settings.gradle.kts", "settings.gradle", "build.gradle.kts", "build.gradle"):
            candidate = (root / name) if root.is_dir() else root.parent / name
            if candidate.is_file():
                extra.append(read(candidate))
        if "mavenCentral()" not in "\n".join(extra):
            findings.append(
                Finding(
                    "WARNING",
                    root,
                    None,
                    "未发现 mavenCentral()；Miuix 构件发布在 Maven Central。",
                )
            )

    if "miuix-navigation3-ui" in combined:
        findings.append(
            Finding(
                "ERROR",
                root,
                None,
                "miuix-navigation3-ui 在 0.9.4 已被移除，请迁移到 miuix-nav。",
            )
        )

    needs_blur = BLUR_HINT.search(combined) is not None

    for path in gradle_files:
        text = read(path)
        for pattern in MIN_SDK_PATTERNS:
            for match in pattern.finditer(text):
                value = int(match.group(1))
                line = text.count("\n", 0, match.start()) + 1
                if value < 24:
                    findings.append(
                        Finding(
                            "ERROR",
                            path,
                            line,
                            f"minSdk={value}，Miuix 共用构件要求 minSdk >= 24。",
                        )
                    )
                elif needs_blur and value < 33:
                    findings.append(
                        Finding(
                            "ERROR",
                            path,
                            line,
                            f"minSdk={value}，但工程使用了 miuix-blur/glass，Android 需要 >= 33。",
                        )
                    )


# SKILL.md 2.0 的间距阶梯：2 纯视觉分离 / 4 组件内微调 / 8 组件内呼吸 / 12 块间距 / 16 卡内边距 / 28 SmallTitle 水平。
# 出现这六个数之外的 dp，就是有人又手调了一次。
DESIGN_TOKEN_SKIP = {2, 4, 8, 12, 16, 28}

# 这两个文件本身就是阶梯的定义处，不参与检查。
DESIGN_TOKEN_SELF = {"CardSpacing.kt", "DesignTokens.kt"}

# 一个文件里出现多个 rememberLayerBackdrop 通常是错的（自采样递归 → SIGSEGV），
# 但「同一份骨架的两种内容槽位」（PageScaffold / PageScaffoldColumn）各有一份是正确的：
# 它们永远不会在同一次组合里同时出现。这类文件按函数数而不是按文件数判定。
MULTI_BACKDROP_ALLOWED = {"PageScaffold.kt"}

BARE_FONT_SIZE = re.compile(r"\bfontSize\s*=\s*\d+(?:\.\d+)?\.sp\b")
PADDING_LITERAL = re.compile(r"\bpadding\s*\(([^)]*?\d+(?:\.\d+)?\.dp[^)]*)\)")
PAGER_PAGE_COUNT = re.compile(r"rememberPagerState\s*\(\s*pageCount\s*=\s*\{\s*(\d+)\s*\}")


# 行级豁免标记：写在命中行的上一行或同一行，该行不参与设计 token 检查。
# 只给「本来就不该跟主题走」的少数场合用 —— 标签 chip 的 9sp、StatusTag 的 4×2dp 内边距。
# 用行级而不是文件级：一个 StatusTag 不该让整份 ListPage.kt 失去栅格检查。
DESIGN_TOKEN_EXEMPT = "miuix-check: allow-design-tokens"
BRACKET_EXEMPT = "miuix-check: allow-brackets"
CYCLE_EXEMPT = "miuix-check: allow-option-cycle"


def _line_exempt(lines: list[str], lineno: int, marker: str = DESIGN_TOKEN_EXEMPT) -> bool:
    """lineno is 1-based; the marker may sit on that line or the one above it."""
    for candidate in (lineno, lineno - 1):
        if 1 <= candidate <= len(lines) and marker in lines[candidate - 1]:
            return True
    return False


def check_design_tokens(root: Path, findings: list[Finding]) -> None:
    """Checks for the spacing / typography / navigation rules in SKILL.md."""
    for path in iter_files(root, ".kt"):
        text = read(path)
        if "top.yukonga.miuix.kmp" not in text and "MiuixTheme" not in text:
            continue
        lines = text.split("\n")

        for match in BARE_FONT_SIZE.finditer(text):
            lineno = text.count("\n", 0, match.start()) + 1
            if _line_exempt(lines, lineno):
                continue
            findings.append(
                Finding(
                    "WARNING",
                    path,
                    lineno,
                    "写死了 fontSize = N.sp；字号必须取自 MiuixTheme.textStyles（main/body1/body2/title3/subtitle 等），否则与系统字体缩放脱节。",
                )
            )
            break

        # 早先这里是「用了 firstCard() 就整份文件跳过 padding 检查」——
        # 结果是只要有一张卡走了栅格，同文件里手调的 20dp / 24dp 全被放过。
        # 栅格不是「用了就对」，而是「每一处都得对」。
        if path.name not in DESIGN_TOKEN_SELF:
            for match in PADDING_LITERAL.finditer(text):
                values = re.findall(r"(\d+(?:\.\d+)?)\.dp", match.group(1))
                odd = [v for v in values if float(v) not in DESIGN_TOKEN_SKIP]
                lineno = text.count("\n", 0, match.start()) + 1
                if odd and not _line_exempt(lines, lineno):
                    findings.append(
                        Finding(
                            "WARNING",
                            path,
                            lineno,
                            f"卡片外的内边距出现 {odd[0]}.dp；卡片间距统一走 Modifier.firstCard() / nextCard()（12dp 栅格）。",
                        )
                    )
                    break

        if re.search(r"\banimateScrollToPage\s*\(", text):
            findings.append(
                Finding(
                    "WARNING",
                    path,
                    text.count("\n", 0, text.find("animateScrollToPage")) + 1,
                    "底栏切页用了 animateScrollToPage；vcut_lower/HyperMusicCover 用 pagerState.scroll(MutatePriority.UserInput) + animate(spring) 配合 PagerNavigationSpringSpec，才能做到可中断、可跟手。",
                )
            )

        backdrop_count = len(re.findall(r"\brememberLayerBackdrop\s*[({]", text))
        if backdrop_count > 1:
            findings.append(
                Finding(
                    "WARNING",
                    path,
                    None,
                    f"同一文件出现 {backdrop_count} 次 rememberLayerBackdrop；整屏只允许一层 backdrop，多层各自采样会成倍消耗 GPU 并出现递归模糊。",
                )
            )

        if re.search(r"\bpagerState\.currentPage\b", text) and "derivedStateOf" not in text:
            findings.append(
                Finding(
                    "WARNING",
                    path,
                    text.count("\n", 0, text.find("pagerState.currentPage")) + 1,
                    "直接读 pagerState.currentPage 会让读取方在每次滚动中重组；用 derivedStateOf 包一层，只在真正换页时触发。",
                )
            )

        for match in PAGER_PAGE_COUNT.finditer(text):
            count = int(match.group(1))
            if count < 3 or count > 5:
                findings.append(
                    Finding(
                        "WARNING",
                        path,
                        text.count("\n", 0, match.start()) + 1,
                        f"底栏标签页数量为 {count}；3–5 个是可发现性与信息密度的平衡区间，超出应改用二级页或分组。",
                    )
                )


# ---- 列表 / 弹窗 / 图标资源（对应 references/list-and-filter.md 与 hmc-patterns.md）----

# 系统 adaptive-icon 不是 VectorDrawable，painterResource 会抛
# "Only VectorDrawables and rasterized asset types are supported"
ADAPTIVE_ICON_REF = re.compile(
    r"painterResource\s*\(\s*(?:android\.R\.|R\.)(?:drawable\.)?(?:sym_def_app_icon|sym_action\w*|ic_menu_\w+)\b"
)

# LazyColumn items/itemsIndexed 的 key 只用了单一字段
LAZY_KEY_SINGLE = re.compile(
    r"\bkey\s*=\s*\{\s*(?:_\s*,\s*)?(?:item|it|entry|app|row)\.(\w+)\s*\}"
)

# 列表项里每项都 new 一个圆角/颜色
INLINE_SHAPE = re.compile(r"RoundedCornerShape\s*\([^)]*\)")

# 弹窗状态在 content lambda 外读
DIALOG_STATE_HOIST = re.compile(
    r"(?:val\s+(\w+)\s*=\s*state\.(?:snapshot|data|value|items|list))[\s\S]{0,400}?"
    r"(?:WindowDialog|OverlayDialog|WindowListPopup)\s*\("
)


BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.DOTALL)
LINE_COMMENT = re.compile(r"//[^\n]*")


def strip_comments(text: str) -> str:
    """Drop // and block comments while keeping every line number stable.

    Comment bodies become spaces (newlines are kept), so offsets and
    text.count(chr(10), 0, pos) still point at the original line.
    """

    def blank(match):
        return "".join(ch if ch == "\n" else " " for ch in match.group(0))

    return LINE_COMMENT.sub(blank, BLOCK_COMMENT.sub(blank, text))


def check_list_and_dialog(root: Path, findings: list[Finding]) -> None:
    """Checks for the list / dialog / icon-asset rules.

    Matching runs on a comment-stripped copy: the templates carry long doc
    comments that *describe* these anti-patterns, and matching those produced
    five false positives on the blank template.
    """
    for path in iter_files(root, ".kt"):
        raw = read(path)
        if "top.yukonga.miuix.kmp" not in raw and "MiuixTheme" not in raw:
            continue
        text = strip_comments(raw)

        for match in ADAPTIVE_ICON_REF.finditer(text):
            findings.append(
                Finding(
                    "ERROR",
                    path,
                    text.count("\n", 0, match.start()) + 1,
                    "painterResource 指向系统 adaptive-icon；Compose 只认 VectorDrawable 与位图，"
                    "运行到这里会抛 IllegalArgumentException 并崩进程。改成自己 res/drawable 下的 <vector>。",
                )
            )

        for match in LAZY_KEY_SINGLE.finditer(text):
            findings.append(
                Finding(
                    "WARNING",
                    path,
                    text.count("\n", 0, match.start()) + 1,
                    f"列表 key 只用了单一字段 {match.group(1)}；把可能重复的维度拼进去"
                    "（如 包名+"#"+用户id、路径、名称+序号），否则滚动位置与动画会串位。",
                )
            )

        if re.search(r"\bLazyColumn\s*\(", text) and re.search(r"\b(?:Card|CirnoCard)\s*\(", text):
            if "firstCard()" not in text and "nextCard()" not in text and "surfaceContainer" not in text:
                pass  # 交给 check_design_tokens 的栅格检查，避免重复报

        # 列表项内联创建 shape：提到文件顶层
        if re.search(r"\bitems(?:Indexed)?\s*\(", text):
            inline = len(INLINE_SHAPE.findall(text))
            if inline >= 3 and not re.search(r"^private val \w+Shape", text, re.MULTILINE):
                findings.append(
                    Finding(
                        "WARNING",
                        path,
                        None,
                        f"文件内出现 {inline} 处内联 RoundedCornerShape；分组卡的首/末形状提到文件顶层 private val，"
                        "否则每帧都会分配新对象。",
                    )
                )

        # 弹窗状态在 lambda 外读：Miuix 弹窗内容由祖先 Scaffold popup host 调用，
        # 外层捕获的只是常量，数据回来也不刷新
        for match in DIALOG_STATE_HOIST.finditer(text):
            findings.append(
                Finding(
                    "WARNING",
                    path,
                    text.count("\n", 0, match.start()) + 1,
                    f"弹窗内容读的 {match.group(1)} 在 content lambda 之外取值；"
                    "Miuix 的 Overlay/Window 弹窗由祖先 popup host 调用，外层捕获的是常量，"
                    "数据回来也不刷新（表现为弹窗一直转圈）。把读取移进 content lambda。",
                )
            )

        # layerBackdrop 自采样：一个文件里注册了多份 backdrop 才可能互相套。
        # 单个 layerBackdrop + 一个模糊调用是正常结构（PageScaffold 就是这样：
        # 注册层只包列表，BlurredBar 在 topBar 里，天然在注册层之外），所以不报。
        backdrop_regs = len(re.findall(r"\bModifier\.layerBackdrop\s*\(", text))
        # PageScaffold.kt 有 PageScaffold / PageScaffoldColumn 两个兄弟函数，各有一份 backdrop。
        # 它们是互斥的内容槽位，永远不会在同一次组合里同时出现，不是自采样。
        if backdrop_regs > 1 and path.name not in MULTI_BACKDROP_ALLOWED:
            findings.append(
                Finding(
                    "WARNING",
                    path,
                    None,
                    f"同一文件注册了 {backdrop_regs} 处 layerBackdrop；确认每一处注册层里都不包含"
                    "用它做模糊的组件（自采样 = 渲染树递归后 SIGSEGV）。顶栏与底栏的模糊要用两份独立 backdrop。",
                )
            )

        # 跳转目标：模板里常见但未注册的 Activity
        for match in re.finditer(r"Intent\s*\([^)]*?,\s*([A-Z]\w*Activity)::class\.java", text):
            name = match.group(1)
            if name not in _declared_activities(root):
                findings.append(
                    Finding(
                        "WARNING",
                        path,
                        text.count("\n", 0, match.start()) + 1,
                        f"跳转到 {name}，但工程里找不到这个类的定义；"
                        "未实现或未注册进 AndroidManifest.xml 会抛 ActivityNotFoundException。",
                    )
                )


_ACTIVITY_CACHE: dict[str, set[str]] = {}


def _declared_activities(root: Path) -> set[str]:
    """Every Activity class name declared somewhere under root (cached per root)."""
    key = str(root)
    if key in _ACTIVITY_CACHE:
        return _ACTIVITY_CACHE[key]
    names: set[str] = set()
    for path in iter_files(root, ".kt"):
        names.update(re.findall(r"\bclass\s+([A-Z]\w*Activity)\b", read(path)))
    _ACTIVITY_CACHE[key] = names
    return names




# ── 9.5 控件增删：重复定义与死引用 ──────────────────────────────────────────

TOP_LEVEL_COMPOSABLE = re.compile(
    r"^(?:@Composable\s*\n)?(?:private\s+|internal\s+|public\s+)?fun\s+([A-Z]\w*)\s*\(",
    re.MULTILINE,
)


def _package_of(path: Path) -> str:
    """读 Kotlin 文件首行的 package 声明；读不到就按目录路径推。"""
    for line in read(path).split("\n", 20):
        stripped = line.strip()
        if stripped.startswith("package "):
            return stripped[len("package "):].strip()
    return path.parent.as_posix()


def _defined_names(paths: list[Path]) -> dict[str, list[tuple[Path, int]]]:
    """Map top-level composable name -> [(file, line), ...] across the project."""
    table: dict[str, list[tuple[Path, int]]] = {}
    for path in paths:
        text = read(path)
        for match in TOP_LEVEL_COMPOSABLE.finditer(text):
            name = match.group(1)
            lineno = text.count("\n", 0, match.start()) + 1
            table.setdefault(name, []).append((path, lineno))
    return table


def check_references(root: Path, findings: list[Finding]) -> None:
    """SKILL.md 9.5.2 / 9.5.4：新增前查重、删后确认引用。

    两项检查都不看「有没有 import」—— import 错了编译器会说。
    这里抓的是编译器不说的那两类：

    1. **同包同名 composable**：Kotlin 直接 Conflicting overloads，
       但两份代码逐字相同时报错信息会指向后一份，让人以为是新文件有问题。
    2. **死引用**：Intent 指向的 Activity 不在 manifest 里。
       编译期完全合法，用户点下去才 ActivityNotFoundException 崩进程。
    """
    kt_files = list(iter_files(root, ".kt"))
    if not kt_files:
        return

    # 1. 同包同名 composable
    by_package: dict[str, dict[str, list[tuple[Path, int]]]] = {}
    for name, places in _defined_names(kt_files).items():
        for path, lineno in places:
            pkg = _package_of(path)
            by_package.setdefault(pkg, {}).setdefault(name, []).append((path, lineno))
    for pkg, names in by_package.items():
        for name, places in names.items():
            if len(places) < 2:
                continue
            where = ", ".join(f"{p.name}:{n}" for p, n in places)
            findings.append(
                Finding(
                    "ERROR",
                    places[0][0],
                    places[0][1],
                    f"包 {pkg} 里定义了 {len(places)} 个同名 composable `{name}`（{where}）；"
                    "同包同名会直接 Conflicting overloads。抽到独立文件，或改名。",
                )
            )

    # 2. Intent 指向的 Activity 是否注册
    declared = _declared_activities(root)
    for path in kt_files:
        raw = read(path)
        # 注释里的示例代码不是真引用（KDoc 里常写 `Intent(context, FooActivity::class.java)` 做说明）。
        # 先剥注释再匹配，行号仍然对得上：strip_comments 保留换行。
        text = strip_comments(raw)
        for match in re.finditer(r"Intent\s*\([^)]*?,\s*([A-Z]\w*Activity)::class\.java", text):
            name = match.group(1)
            if name in declared:
                continue
            lineno = text.count("\n", 0, match.start()) + 1
            findings.append(
                Finding(
                    "ERROR",
                    path,
                    lineno,
                    f"`{name}` 没有在 AndroidManifest.xml 注册；编译能过，但用户点下去会 "
                    "ActivityNotFoundException 崩进程。加 <activity android:name=\"...{name}\" />。",
                )
            )


def check_reuse(root: Path, findings: list[Finding]) -> None:
    """优先复用已有资源：自绘了库里有的图标、或重写了模板已有的布局，都提示。

    这两条不是「编译错误」，是「你在重复劳动且结果更差」：
      - 自绘 Material 图标一定画不如图形原版（包围盒、比例、端点圆角是一起设计的）；
      - 重写模板已有的页面布局，结果通常是「看着差不多但处处差一点」。
    允许自绘的只有品牌图形（应用 logo、启动图标），这些库里没有。
    """
    if not root.is_dir():
        return

    # --- 1. res/drawable 下疑似复刻 Material 图标的 vector ---
    # 允许清单：品牌图形与启动图标。其余 24dp viewport 的单色 vector 大概率是抄来的图标。
    ALLOWED_DRAWABLE = re.compile(
        r"^(ic_about_logo|ic_launcher_background|ic_launcher_foreground|ic_launcher_monochrome)$"
    )
    for path in root.rglob("res/drawable*/**/*.xml"):
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        if ALLOWED_DRAWABLE.match(path.stem):
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        if "<vector" not in text:
            continue
        # 24dp viewport 是 Material 图标的标准尺寸
        if re.search(r'android:viewportWidth="24(?:\.0)?"', text):
            findings.append(
                Finding(
                    "WARNING",
                    path,
                    None,
                    "这个 <vector> 是 24dp viewport，很像自绘的 Material 图标。通用操作走 "
                    "MiuixIcons.*，状态符号（勾、感叹号、警告）走 material-icons-extended 的 "
                    "Icons.Rounded.*，不要自绘 —— 包围盒与比例一定画不如图形原版。"
                    "只有品牌图形（应用 logo、启动图标）才需要自绘，并把名字加进本检查的允许清单。",
                )
            )

    # --- 2. 页面文件自己重写了模板已有的布局 ---
    # 只看页面（ui/screens/ 下或文件名含 Page/Activity/Screen），util/theme 是工具层不查。
    PAGE_FILE = re.compile(r"(Page|Activity|Screen)\.kt$")
    LAYOUT_SIGNATURES = (
        (r"\.offset\(\s*27\.dp\s*,\s*31\.dp\s*\)",
         "状态卡的三层 Box（图标出血 + 左下应用名 + 左上标题）",
         "assets/HomePage.kt"),
    )
    for path in root.rglob("*.kt"):
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        if not PAGE_FILE.search(path.name):
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        body = strip_comments(text)
        for pattern, label, template in LAYOUT_SIGNATURES:
            if not re.search(pattern, body):
                continue
            # 已经引了 PageScaffold 说明在模板体系内，不再追问
            if "PageScaffold" in body:
                continue
            findings.append(
                Finding(
                    "WARNING",
                    path,
                    None,
                    f"这里自己搭了{label}的结构，但整个文件没用 PageScaffold 骨架。"
                    f"先去 {template} 抄一份再改文案 —— 现成的在真机上跑过、修过坑。",
                )
            )
            break

def check_first_cards(root: Path, findings: list[Finding]) -> None:
    """运行卡片页的前两张卡上方不准有分组标题（SmallTitle）。

    判据：文件里存在状态卡（StatusCard 调用或 AppStatus 数据类）才算「运行卡片页」。
    在这些文件里，SmallTitle 若落在第 1 或第 2 个 item(...) 块内，就报 WARNING。

    为什么：SmallTitle 自带 PaddingValues(28.dp, 8.dp)，两个标题吃掉约 72dp ——
    正好是一张数据卡的高度，而前两张卡是首屏唯一一定会被看的东西。
    前两张卡各只有一张，没有可收拢的同类，标题只是把内容复述一遍。
    """
    STATE_MARKERS = ("StatusCard(", "AppStatus")
    ITEM_START = re.compile(r"\bitem\s*\(")
    TITLE = re.compile(r"\bSmallTitle\s*\(")

    for path in iter_files(root, ".kt"):
        raw = read(path)
        body = strip_comments(raw)
        if not any(marker in body for marker in STATE_MARKERS):
            continue

        item_starts = [m.start() for m in ITEM_START.finditer(body)]
        if len(item_starts) < 2:
            continue

        for match in TITLE.finditer(body):
            pos = match.start()
            block = -1
            for i, start in enumerate(item_starts):
                if pos > start:
                    block = i
                else:
                    break
            if block in (0, 1):
                findings.append(
                    Finding(
                        "WARNING",
                        path,
                        body.count("\n", 0, pos) + 1,
                        f"运行卡片页的第 {block + 1} 张卡上方出现了分组标题 SmallTitle。"
                        "前两张卡不准加标题 —— 它们各只有一张、没有可收拢的同类，标题只是复述内容；"
                        "SmallTitle 自带 28/8dp 内边距，两个标题吃掉约 72dp ≈ 一张数据卡的高度。"
                        "从第三张卡起才允许分组。见 SKILL.md 5.1。",
                    )
                )
                break


def check_ui_copy(root: Path, findings: list[Finding]) -> None:
    """界面文案不准用括号做补充说明。

    扫 UI 文案参数（title / summary / text / label / subtitle / contentDescription / placeholder）
    的字符串字面量：含全角括号，或含半角括号且带中文 —— 报 WARNING。

    为什么：括号里的字最先被折行、被截断，一对全角括号还占四个字符宽，
    等于把副标题预算花在标点上。补充信息交给 summary / subtitle，或拆成两行。

    例外：技术标识原样保留 —— 包名、版本号、路径、正则、全大写常量不做判定。
    行级豁免：同一行或上一行写 // miuix-check: allow-brackets 。
    """
    UI_ARG = re.compile(
        r'\b(?:title|summary|text|label|subtitle|contentDescription|placeholder)\s*=\s*"([^"]*)"'
    )

    for path in iter_files(root, ".kt"):
        raw = read(path)
        body = strip_comments(raw)
        if not body.strip():
            continue
        lines = raw.split("\n")

        for match in UI_ARG.finditer(body):
            val = match.group(1)
            if not val or "${" in val:
                continue
            lineno = body.count("\n", 0, match.start()) + 1
            if _line_exempt(lines, lineno, BRACKET_EXEMPT):
                continue
            has_full = "\uff08" in val or "\uff09" in val
            has_ascii = "(" in val or ")" in val
            has_cjk = any("\u4e00" <= ch <= "\u9fff" for ch in val)
            if not (has_full or (has_ascii and has_cjk)):
                continue
            # 技术标识白名单：括号里装的全是 ASCII（协议码 32、包名 com.xiaomi.mirror:cast、
            # 类名 miRyShareTransfer、单位 GB）不算补充说明，放行。
            inner = re.findall(r"[（(]([^）)]*)[）)]", val)
            if inner and not any(any("一" <= ch <= "鿿" for ch in g) for g in inner):
                continue
            msg = (
                "界面文案 " + repr(val) + " 里用了括号做补充说明。"
                "括号里的字最先被折行、被截断，一对全角括号还占四个字符宽。"
                "把它搬进 summary / subtitle，或拆成两行；单位写成 12.4 GB 而不是 12.4（GB）。"
                "只有包名、版本号、路径、正则、报错原文这类技术标识才保留括号。见 SKILL.md 9.6。"
            )
            findings.append(Finding("WARNING", path, lineno, msg))

def check_theme_block(root: Path, findings: list[Finding]) -> None:
    """主题设置板块必须完整保留（SKILL.md 6.1）。

    判据分三层，任何一层不过都是 ERROR：
      1. ThemeActivity 文件存在（或其等价主题二级页）；
      2. 页内三组控件都在：WindowDropdownPreference（六模式）+ 两个 SwitchPreference；
      3. AppSettings 里 themeMode / isFloatingNavbar / isBlurEnabled 三个字段都在。

    为什么用 ERROR 而不是 WARNING：主题是环境适配，删掉之后 App 自己拿不到取色与蒙版，
    用户只能靠系统强制深色补救；而 AppSettings 三字段、AppNightMode、状态栏同步、
    settingsEpoch 重读会同时变成死代码。这不是风格问题，是功能残缺。

    豁免：整份工程没有 MiuixTheme 时（纯资源工程、单测工程）不判定。
    """
    kt_files = list(iter_files(root, ".kt"))
    if not kt_files:
        return

    probe = ""
    for f in kt_files[:60]:
        probe += read(f)
        if len(probe) > 400000:
            break
    if "MiuixTheme" not in probe:
        return

    theme_files = [f for f in kt_files if f.name == "ThemeActivity.kt"]
    theme_pages = []
    for f in kt_files:
        src = read(f)
        if "WindowDropdownPreference" in src and "ColorSchemeMode" in src:
            theme_pages.append(f)
    target = theme_files or theme_pages
    if not target:
        findings.append(
            Finding(
                "ERROR",
                root,
                None,
                "找不到主题设置板块（ThemeActivity.kt，或含 WindowDropdownPreference 与 ColorSchemeMode 的页面）。"
                "主题设置必须完整保留，见 SKILL.md 6.1：它是唯一会重建 Activity 的设置，"
                "删掉后 AppSettings 三字段、AppNightMode、状态栏同步、settingsEpoch 重读全成死代码。",
            )
        )
        return

    page = target[0]
    src = strip_comments(read(page))
    checks = [
        ("WindowDropdownPreference", "六个主题模式的单选下拉"),
        ('"悬浮底栏"', "悬浮底栏开关"),
        ('"背景模糊"', "背景模糊开关"),
    ]
    for token, label in checks:
        if token not in src:
            findings.append(
                Finding(
                    "ERROR",
                    page,
                    None,
                    "主题设置板块缺少" + label + "（找不到 " + token + "）。"
                    "三项一项都不能少，见 SKILL.md 6.1：六模式 + 悬浮底栏 + 背景模糊。",
                )
            )

    settings_files = [f for f in kt_files if f.name == "AppSettings.kt"]
    if settings_files:
        ssrc = strip_comments(read(settings_files[0]))
        for field in ("themeMode", "isFloatingNavbar", "isBlurEnabled"):
            if field not in ssrc:
                findings.append(
                    Finding(
                        "ERROR",
                        settings_files[0],
                        None,
                        "AppSettings 缺少 " + field + " 字段。主题板块的三项设置必须能持久化，"
                        "删字段会让主题页读写到不存在的键，见 SKILL.md 6.1。",
                    )
                )


def check_option_picker(root: Path, findings: list[Finding]) -> None:
    """多选一必须用下拉，禁止点击循环切换（SKILL.md 9.7）。

    静态只能抓两种最典型的写法，扫描窗口限定在 onClick / onCheckedChange 的 lambda 里：
      1. 模运算轮转： (x + 1) % n
      2. 调用 next / cycle / rotate / advance 类函数

    Boolean 开关（SwitchPreference）是合法的，模运算只对 >=3 个选项才成立，所以这里只报
    WARNING 而不是 ERROR —— 轮播图索引、分页游标也可能用 % 运算，需要人看一眼。

    行级豁免：同一行或上一行写 // miuix-check: allow-option-cycle 。
    """
    CLICK = re.compile(r"\bon(?:Click|CheckedChange|SelectedIndexChange)?\s*=\s*\{")
    ROTATE = [
        (re.compile(r"\(\s*[\w.]+\s*\+\s*1\s*\)\s*%"), "模运算轮转 (x + 1) % n"),
        (re.compile(r"\b(?:next|cycle|rotate|advance)(?!Card)[A-Z]?\w*\s*\("), "调用 next/cycle/rotate 类函数"),
    ]

    for path in iter_files(root, ".kt"):
        raw = read(path)
        body = strip_comments(raw)
        if "DropDown" in body or "Dropdown" in body:
            # 文件里已经有下拉组件，仍然检查，但不因为「有下拉」就跳过
            pass
        lines = raw.split(chr(10))

        for click in CLICK.finditer(body):
            start = click.end()
            window = body[start:start + 400]
            for pattern, label in ROTATE:
                m = pattern.search(window)
                if not m:
                    continue
                hit_line = body.count(chr(10), 0, start + m.start()) + 1
                if _line_exempt(lines, hit_line, CYCLE_EXEMPT):
                    continue
                findings.append(
                    Finding(
                        "WARNING",
                        path,
                        hit_line,
                        "点击回调里出现" + label + "，看起来是「点一下切到下一个」的循环切换。"
                        "多选一（只要有选项，>=2 个互斥值）必须用 WindowDropdownPreference 弹下拉，见 SKILL.md 9.7："
                        "循环切换让用户猜不到下次变成什么、看不见全部选项、也没法回退。"
                        "若这里确实是轮播图索引或分页游标，加 // miuix-check: allow-option-cycle 并写明理由。",
                    )
                )
                break


def check_entry_unique(root: Path, findings: list[Finding]) -> None:
    """功能入口必须唯一：同一个配置不准从两个页面都能进（SKILL.md 9.8）。

    两种判据：
      1. 同一个目标 Activity 在 >=2 个文件里被 startActivity(Intent(...)) 跳转；
      2. 同一个 (Activity, title) 在 >=2 个文件里作为跳转参数出现。

    第 2 条同时支持两种调用风格：命名参数 title = "X" 与位置参数 intent(ctx, "X", ...)。
    同一个工厂函数被两种风格调用本身也是不一致，但这里只按「同一 title 多入口」报。
    """
    JUMP = re.compile(r"startActivity\s*\(\s*Intent\s*\([^)]*?([A-Z]\w*Activity)\s*::\s*class")
    INTENT_CALL = re.compile(r"([A-Z]\w*Activity)\.intent\s*\(")
    NAMED_TITLE = re.compile(r"title\s*=\s*\"([^\"]+)\"")
    POS_TITLE = re.compile(r",\s*\"([^\"]+)\"")

    targets: dict[str, list[str]] = {}
    titled: dict[tuple[str, str], list[str]] = {}

    for path in iter_files(root, ".kt"):
        body = strip_comments(read(path))
        for m in JUMP.finditer(body):
            targets.setdefault(m.group(1), []).append(path.name)
        for m in INTENT_CALL.finditer(body):
            activity = m.group(1)
            window = body[m.end():m.end() + 300]
            # 截到本调用结束，避免串到同文件下一个 intent(...) 的参数
            close = window.find(chr(41))
            if close >= 0:
                window = window[:close]
            tm = NAMED_TITLE.search(window) or POS_TITLE.search(window)
            if tm:
                titled.setdefault((activity, tm.group(1)), []).append(path.name)

    for activity, files in sorted(targets.items()):
        uniq = sorted(set(files))
        if len(uniq) < 2:
            continue
        findings.append(
            Finding(
                "WARNING",
                root,
                None,
                activity + " 有 " + str(len(uniq)) + " 个入口文件（" + " / ".join(uniq) + "）。"
                "同一个配置功能不准放多个入口，见 SKILL.md 9.8：用户会以为它们是两个功能，"
                "改完一处回到另一处发现同名入口还在，会怀疑没保存。只留一个最合理的入口。",
            )
        )

    for (activity, title), files in sorted(titled.items()):
        uniq = sorted(set(files))
        if len(uniq) < 2:
            continue
        findings.append(
            Finding(
                "WARNING",
                root,
                None,
                "功能「" + title + "」有 " + str(len(uniq)) + " 个入口（" + " / ".join(uniq) + "）。"
                "同一个配置功能只准一个入口，见 SKILL.md 9.8：两处都要维护、必有一处变旧版；"
                "用户改完一处回到另一处发现同名入口还在，会以为没保存。",
            )
        )

def check_orphan_activity(root: Path, findings: list[Finding]) -> None:
    """Activity 类必须在 AndroidManifest.xml 注册（SKILL.md 9.8）。

    未注册的 Activity 是死代码：编译能过、没人能进去，但它往往是一份「完整版」，
    而真正在用的那份是残缺版 —— 两套壳并存时最难查，因为代码看着都对。
    """
    manifest = None
    for cand in sorted(root.rglob("AndroidManifest.xml")):
        if any(part in SKIP_DIRS for part in cand.parts):
            continue
        manifest = cand
        break
    if manifest is None:
        return

    xml = read(manifest)
    declared = set(re.findall(r'android:name="([^' + chr(34) + ']+)"', xml))
    short = {d.rsplit(".", 1)[-1] for d in declared}

    ACT = re.compile(r"class\s+(\w+)\s*:\s*(?:ComponentActivity|Activity)\s*\(")

    for path in iter_files(root, ".kt"):
        body = strip_comments(read(path))
        for m in ACT.finditer(body):
            name = m.group(1)
            if name in short:
                continue
            findings.append(
                Finding(
                    "WARNING",
                    path,
                    body.count(chr(10), 0, m.start()) + 1,
                    name + " 继承了 Activity 但 AndroidManifest.xml 里没有注册。"
                    "未注册的 Activity 是死代码，进不去；更危险的是它常常是一份「完整版」，"
                    "而真正在用的那份是残缺版（缺 settingsEpoch / onResume 重读），"
                    "症状就是「设置里改了没反应」。要么注册它并删掉另一份，要么删掉它。见 SKILL.md 9.8。",
                )
            )

def check_settings_reload(root: Path, findings: list[Finding]) -> None:
    """应用壳必须重读设置：只 onCreate 读一次 = 开关拨了没反应（SKILL.md 6.2）。

    判据：某个 ComponentActivity 文件同时满足
      - 有 AppSettings.load( ，
      - 有 setContent（说明是界面壳），
      - 没有 onResume（没有重读时机），
      - 文件名不含 Theme（主题页自己写自己，不需要重读）
    → 报 WARNING。

    真实反例（小米妙播模块，2026-11-21）：MainActivity 在 onCreate 里
    val settings = AppSettings.load(this) 读一次，再传给 AppShellScreen；
    从主题页返回后这两个值永远是旧值，症状是「主题页只有主题模式有效」。
    """
    ACT = re.compile(r"class\s+(\w+)\s*:\s*ComponentActivity\s*\(")

    for path in iter_files(root, ".kt"):
        if "Theme" in path.name:
            continue
        body = strip_comments(read(path))
        if "AppSettings.load(" not in body or "AppShellScreen(" not in body:
            continue
        if "onResume" in body:
            continue
        m = ACT.search(body)
        if not m:
            continue
        msg = (
            m.group(1) + " 在 onCreate 里读一次 AppSettings.load 就再不重读（没有 onResume）。"
            "从主题页返回后，悬浮底栏、背景模糊这些值永远是进 App 时的旧值 —— "
            "症状是「主题页设置只有主题模式有效」。加 onResume { settingsEpoch++ } 并用 "
            "remember(settingsEpoch) { AppSettings.load(this) } 重读，见 SKILL.md 6.2。"
        )
        findings.append(Finding("WARNING", path, body.count(chr(10), 0, m.start()) + 1, msg))

def main() -> int:
    parser = argparse.ArgumentParser(
        description="Check Miuix usage for common integration mistakes."
    )
    parser.add_argument("--path", required=True, help="Project directory or a single file")
    args = parser.parse_args()

    root = Path(args.path).expanduser().resolve()
    if not root.exists():
        print(f"[ERROR] path does not exist: {root}")
        return 2

    findings: list[Finding] = []
    check_kotlin_files(root, findings)
    check_gradle_files(root, findings)
    check_design_tokens(root, findings)
    check_list_and_dialog(root, findings)
    check_references(root, findings)
    check_reuse(root, findings)
    check_first_cards(root, findings)
    check_ui_copy(root, findings)
    check_theme_block(root, findings)
    check_option_picker(root, findings)
    check_entry_unique(root, findings)
    check_orphan_activity(root, findings)
    check_settings_reload(root, findings)

    for finding in findings:
        print(finding.render())

    errors = sum(1 for f in findings if f.level == "ERROR")
    warnings = sum(1 for f in findings if f.level == "WARNING")
    print(f"Summary: {errors} error(s), {warnings} warning(s)")

    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())