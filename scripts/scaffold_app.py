#!/usr/bin/env python3
"""Scaffold a four-tab Miuix app shell into an existing Android project.

Usage:
    python scaffold_app.py --project <android-project-root> [--package com.example.app]
                           [--tabs home,features,settings,about] [--force] [--dry-run]

Steps:
    1. Locate the app module and read its namespace / applicationId.
    2. Copy the verified template .kt files from assets/ into the module source tree,
       rewriting the package declaration to the target package.
    3. Print the remaining manual steps.

It never edits build files and never overwrites an existing file unless --force is given.
Exit code: 0 ok, 1 something needs attention, 2 bad arguments.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

TEMPLATE_PACKAGE = "com.example.app"

ASSETS = Path(__file__).resolve().parent.parent / "assets"

LAYOUT = {
    "AppShell.kt": "ui/AppShell.kt",
    "PageScaffold.kt": "ui/util/PageScaffold.kt",
    "CardSpacing.kt": "ui/util/CardSpacing.kt",
    "DesignTokens.kt": "ui/util/DesignTokens.kt",
    "BlurUtils.kt": "ui/util/BlurUtils.kt",
    "PagerNavigationSpring.kt": "ui/util/PagerNavigationSpring.kt",
    "MiuixAppTheme.kt": "ui/theme/MiuixAppTheme.kt",
    "AppSettings.kt": "ui/AppSettings.kt",
    "HomePage.kt": "ui/screens/HomePage.kt",
    "FeaturesPage.kt": "ui/screens/FeaturesPage.kt",
    "SettingsPage.kt": "ui/screens/SettingsPage.kt",
    "AboutPage.kt": "ui/screens/AboutPage.kt",
    "ListPage.kt": "ui/screens/ListPage.kt",
    "ListViewModel.kt": "ui/screens/ListViewModel.kt",
    "StatusTag.kt": "ui/screens/StatusTag.kt",
    "AppListViewModel.kt": "ui/screens/AppListViewModel.kt",
    "AppListPage.kt": "ui/screens/AppListPage.kt",
    "AppListActivity.kt": "ui/screens/AppListActivity.kt",
    "AppDetailActivity.kt": "ui/screens/AppDetailActivity.kt",
    "FeatureDetailActivity.kt": "ui/screens/FeatureDetailActivity.kt",
    "ThemeActivity.kt": "ui/screens/ThemeActivity.kt",
    "effect/BgEffectBackground.kt": "ui/component/effect/BgEffectBackground.kt",
    "effect/BgEffectConfig.kt": "ui/component/effect/BgEffectConfig.kt",
    "effect/BgEffectModifier.kt": "ui/component/effect/BgEffectModifier.kt",
    "effect/BgEffectPainter.kt": "ui/component/effect/BgEffectPainter.kt",
    "effect/DeviceType.kt": "ui/component/effect/DeviceType.kt",
    "effect/OS2BgFrag.kt": "ui/component/effect/OS2BgFrag.kt",
    "effect/OS3BgFrag.kt": "ui/component/effect/OS3BgFrag.kt",
}

# Drawable / values 资源另走一份表：它们落到 res/ 而不是源码树。
RES_LAYOUT = {
    "res/drawable/ic_about_logo.xml": "res/drawable/ic_about_logo.xml",
    "res/drawable/ic_launcher_background.xml": "res/drawable/ic_launcher_background.xml",
    "res/drawable/ic_launcher_foreground.xml": "res/drawable/ic_launcher_foreground.xml",
    "res/values/themes.xml": "res/values/themes.xml",
}

# 落到 module 根（与 app/build.gradle.kts 同级）而不是 src/main 下的文件。
MODULE_LAYOUT = {
    "res/proguard-rules.pro": "proguard-rules.pro",
}

DEFAULT_TABS = ["home", "features", "settings", "about"]

TAB_LABELS = {"home": "主页", "features": "功能", "settings": "设置", "about": "关于"}
TAB_ICONS = {"home": "Home", "features": "Tune", "settings": "Settings", "about": "Info"}
TAB_PAGES = {
    "home": "HomePage",
    "features": "FeaturesPage",
    "settings": "SettingsPage",
    "about": "AboutPage",
}

TAB_EXTRA_ARGS = {
    "home": "extraBottomPadding = pad",
    "features": "extraBottomPadding = pad",
    "settings": "extraBottomPadding = pad",
    "about": "extraBottomPadding = pad",
}

REQUIRED_LIBS = (
    "miuix-core-android",
    "miuix-ui-android",
    "miuix-preference-android",
    "miuix-icons-android",
    # 状态卡的勾/感叹号图标直接取自 material-icons-extended 的 Rounded 版本 ——
    # 与 HyperMusicCover 是同一个 ImageVector。自绘 vector 怎么调都不如原版：
    # 勾的包围盒偏心、描边比例不对，圆头与圆环的间距也不对。
    "material-icons-extended",
)
OPTIONAL_LIBS = ("miuix-blur-android", "miuix-squircle-android")

NAMESPACE_PATTERNS = (
    re.compile(r"namespace\s*=\s*[\"']([\w.]+)[\"']"),
    re.compile(r"namespace\s+[\"']([\w.]+)[\"']"),
)
MANIFEST_PACKAGE = re.compile(r"package\s*=\s*[\"']([\w.]+)[\"']")


def find_module(project: Path) -> Path | None:
    for candidate in (project / "app", project):
        if (candidate / "build.gradle.kts").is_file() or (candidate / "build.gradle").is_file():
            return candidate
    return None


def read_namespace(module: Path) -> str | None:
    for name in ("build.gradle.kts", "build.gradle"):
        path = module / name
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        for pattern in NAMESPACE_PATTERNS:
            match = pattern.search(text)
            if match:
                return match.group(1)
    manifest = module / "src" / "main" / "AndroidManifest.xml"
    if manifest.is_file():
        match = MANIFEST_PACKAGE.search(manifest.read_text(encoding="utf-8", errors="ignore"))
        if match:
            return match.group(1)
    return None


def source_root(module: Path, package: str) -> Path:
    for base in ("src/main/java", "src/main/kotlin"):
        candidate = module / base
        if candidate.is_dir():
            return candidate / Path(*package.split("."))
    return module / "src" / "main" / "java" / Path(*package.split("."))


def check_dependencies(module: Path) -> tuple[list[str], list[str]]:
    blobs = []
    for path in (module / "build.gradle.kts", module / "build.gradle"):
        if path.is_file():
            blobs.append(path.read_text(encoding="utf-8", errors="ignore"))
    toml = module.parent / "gradle" / "libs.versions.toml"
    if toml.is_file():
        blobs.append(toml.read_text(encoding="utf-8", errors="ignore"))
    combined = "\n".join(blobs)
    missing = [lib for lib in REQUIRED_LIBS if lib not in combined]
    absent_optional = [lib for lib in OPTIONAL_LIBS if lib not in combined]
    return missing, absent_optional


def build_tab_block(tabs: list[str]) -> str:
    """Emit the defaultTabs() body matching the AppTab signature in assets/AppShell.kt."""
    lines = []
    for tab in tabs:
        label = TAB_LABELS[tab]
        icon = TAB_ICONS[tab]
        page = TAB_PAGES[tab]
        extra = TAB_EXTRA_ARGS[tab]
        # AppTab.content 的签名是 (isBlurEnabled: Boolean, bottomPadding: Dp) -> Unit。
        # 这里刻意不传 backdrop：每页自己 rememberBlurBackdrop()，共用一份会自采样递归。
        if extra:
            call = "%s(isBlurEnabled = blur, %s)" % (page, extra)
        else:
            call = "%s(isBlurEnabled = blur)" % page
        lines.append(
            '    AppTab("%s", "%s", MiuixIcons.%s) { blur, pad -> %s },'
            % (tab, label, icon, call)
        )
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description="Scaffold a Miuix four-tab app shell.")
    parser.add_argument("--project", required=True, help="Android project root")
    parser.add_argument("--package", help="Target package; read from Gradle when omitted")
    parser.add_argument("--tabs", default=",".join(DEFAULT_TABS),
                        help="Comma separated tab list, 3-5 items")
    parser.add_argument("--force", action="store_true", help="Overwrite existing files")
    parser.add_argument("--dry-run", action="store_true", help="Print the plan only")
    args = parser.parse_args()

    project = Path(args.project).expanduser().resolve()
    if not project.is_dir():
        print("[ERROR] project directory not found: %s" % project)
        return 2

    module = find_module(project)
    if module is None:
        print("[ERROR] no app module with build.gradle(.kts) found")
        return 2

    package = args.package or read_namespace(module)
    if not package:
        print("[ERROR] cannot determine the package name; pass --package")
        return 2

    tabs = [t.strip().lower() for t in args.tabs.split(",") if t.strip()]
    unknown = [t for t in tabs if t not in DEFAULT_TABS]
    if unknown:
        print("[ERROR] unknown tab(s): %s" % ", ".join(unknown))
        return 2
    if not 3 <= len(tabs) <= 5:
        print("[ERROR] tab count is %d; the template requires 3-5 tabs" % len(tabs))
        return 1
    if tabs[0] != "home":
        print("[ERROR] the first tab must be home; the back-key contract assumes it")
        return 1

    print("module     : %s" % module)
    print("package    : %s" % package)
    print("tabs       : %s" % ", ".join(tabs))
    print("source root: %s" % source_root(module, package))
    print("")

    missing, absent_optional = check_dependencies(module)
    if missing:
        print("[TODO] add these dependencies before compiling:")
        for lib in missing:
            print("        top.yukonga.miuix.kmp:%s" % lib)
    if absent_optional:
        print("[NOTE] not declared (add only if used):")
        for lib in absent_optional:
            print("        top.yukonga.miuix.kmp:%s" % lib)
    if missing or absent_optional:
        print("")

    if not ASSETS.is_dir():
        print("[ERROR] template directory missing: %s" % ASSETS)
        return 2

    target_root = source_root(module, package)
    skipped = []

    for template_name, relative in LAYOUT.items():
        source = ASSETS / template_name
        if not source.is_file():
            print("[MISS] template not found: %s" % template_name)
            continue
        destination = target_root / relative
        if destination.exists() and not args.force:
            skipped.append(str(destination))
            continue
        text = source.read_text(encoding="utf-8")
        text = text.replace(TEMPLATE_PACKAGE, package)
        if args.dry_run:
            print("[PLAN] %s" % relative)
        else:
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(text, encoding="utf-8")
            print("[OK]   %s" % relative)

    # 资源（drawable / values）落到 module/src/main/res，不走源码包路径。
    res_root = module / "src" / "main"
    for template_name, relative in RES_LAYOUT.items():
        source = ASSETS / template_name
        if not source.is_file():
            print("[MISS] template not found: %s" % template_name)
            continue
        destination = res_root / relative
        if destination.exists() and not args.force:
            skipped.append(str(destination))
            continue
        if args.dry_run:
            print("[PLAN] %s" % relative)
        else:
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(source.read_bytes())
            print("[OK]   %s" % relative)

    for template_name, relative in MODULE_LAYOUT.items():
        source = ASSETS / template_name
        if not source.is_file():
            print("[MISS] template not found: %s" % template_name)
            continue
        destination = module / relative
        if destination.exists() and not args.force:
            skipped.append(str(destination))
            continue
        text = source.read_text(encoding="utf-8").replace(TEMPLATE_PACKAGE, package)
        if args.dry_run:
            print("[PLAN] %s" % relative)
        else:
            destination.write_text(text, encoding="utf-8")
            print("[OK]   %s" % relative)

    if skipped:
        print("")
        print("[SKIP] already present (use --force to overwrite):")
        for path in skipped:
            print("       %s" % path)

    print("")
    print("Next steps:")
    print("  1. Register the shell activity and the two detail activities in AndroidManifest.xml:")
    print('       <activity android:name=".ui.AppShellActivity"')
    print('                 android:exported="true"')
    print('                 android:icon="@drawable/ic_about_logo">')
    print("         <intent-filter>")
    print('           <action android:name="android.intent.action.MAIN" />')
    print('           <category android:name="android.intent.category.LAUNCHER" />')
    print("         </intent-filter>")
    print("       </activity>")
    print('       <activity android:name=".ui.screens.ThemeActivity" android:exported="false" />')
    print('       <activity android:name=".ui.screens.FeatureDetailActivity" android:exported="false" />')
    print("     Both detail activities must exist and be registered, or the first tap on")
    print("     Settings > 主题 / Features crashes with ActivityNotFoundException.")
    print("  2. Wrap the activity content in MiuixAppTheme(themeMode) { AppShellScreen(...) }.")
    print("     The theme composable is MiuixAppTheme, not AppTheme.")
    print("  3. Replace the sample data in HomePage.kt (AppStatus.sample) with the real source.")
    print("  4. Replace doEmergencyAction() in FeaturesPage.kt with the real action.")
    print("  5. Turn on R8 in app/build.gradle.kts. This is not optional:")
    print("       debug build of this template is 31.8 MB, release with R8 is 5.9 MB.")
    print("       The whole 26 MB gap is unused androidx.compose + miuix classes that")
    print("       debug keeps. proguard-rules.pro is already scaffolded for you.")
    print("         buildTypes { release {")
    print("             isMinifyEnabled = true")
    print("             isShrinkResources = true")
    print('             proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")')
    print('             ndk { abiFilters += "arm64-v8a" }')
    print("         } }")
    print("  6. Check res/values/themes.xml: android:windowBackground must be an opaque")
    print("     color (e.g. #FFFBFE). Transparent leaves the window black until Compose")
    print("     draws, which reads as a broken launch on a dark device.")
    print("  7. Run scripts/check_miuix_usage.py --path <project> and clear every finding.")
    print("")
    print("Tab block for AppShellScreen:")
    print(build_tab_block(tabs))

    return 0


if __name__ == "__main__":
    sys.exit(main())
