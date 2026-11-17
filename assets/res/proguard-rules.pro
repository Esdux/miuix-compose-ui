# Miuix + Compose 的 release 保留规则。
#
# 为什么需要它：vcut_lower 的 app/build.gradle.kts 注释写得很直接 ——
# 「material-icons-extended 有几十 MB 生成代码，不开 R8 体积会很大」。
# 但开了 R8 之后，反射与序列化路径会静默失效（界面不崩，只是某些行不显示），
# 所以下面几组必须显式保留。

# Compose runtime 通过反射查找 @Composable 的 lambda 与 state 类
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable <methods>;
}
-keep class androidx.compose.runtime.** { *; }

# kotlinx.serialization：AppSettings / SettingsBackup 的 JSON 靠它
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.example.app.**$$serializer { *; }
-keepclassmembers class com.example.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Miuix 图标是扩展属性，名字被 R8 改掉后 MiuixIcons.X 找不到
-keep class top.yukonga.miuix.kmp.icon.** { *; }

# RuntimeShader 的 AGSL 通过字符串索引 uniform，字段名不能混淆
-keepclassmembers class top.yukonga.miuix.kmp.blur.** {
    <fields>;
}

# 只保留行号，方便看崩溃栈；不保留源文件名
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
