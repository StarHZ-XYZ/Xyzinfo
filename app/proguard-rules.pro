# ============================================================
#  XyzInfo 安全加固 / R8 混淆规则
# ============================================================

# 类名、方法名全部重命名后统一收敛到同一个包，反编译出来是 a.a(a,b) 这种没有语义的骨架
-repackageclasses 'o'
-allowaccessmodification
-dontpreverify

# 不保留源码文件名与行号：崩溃堆栈不再暴露原始代码结构
-renamesourcefileattribute Source
-keepattributes !SourceFile,!LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable

# ------------------------------------------------------------
#  必须保留：这些是"按名字反射实例化"的
# ------------------------------------------------------------

# 布局 XML 里用全限定类名实例化的自定义 View 由 AGP 自动生成 keep 规则
# （构建中间产物 aapt_rules.txt 里能看到 GlassBottomBar / ThermalChartView /
#  SatelliteSkyView / OfflineMapView / ScreenTestView / TemperatureGaugeView …
#  的 <init>(Context, AttributeSet)）。
#
# 所以这里**故意不再写** "-keep public class * extends android.view.View"：
# 那条规则会把所有自定义控件（包括代码里 new 出来的 SeasonOverlay、ParticleOverlay、
# GlassBackdrop 等）全部保住，等于白白放弃一大片混淆。

# 四大组件与 Application（系统按清单里的名字启动）
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ViewBinding 生成的绑定类
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** inflate(...);
    public static *** bind(...);
    public *** getRoot();
}

# 资源索引里的 R 字段（反射访问资源 ID 用得到）
-keepclassmembers class **.R$* {
    public static <fields>;
}

# 清单里用到的 Application 子类的静态入口
-keep class com.rjy.xyz.apps.xyzinfo.XyzInfoApp { *; }

# ------------------------------------------------------------
#  其他
# ------------------------------------------------------------

# WebView 的 JavaScript 接口（当前没有 @JavascriptInterface，留着以免以后加接口被混淆）
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 枚举的 values()/valueOf()（有的地方用 name() 做持久化）
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-dontwarn org.jetbrains.annotations.**
-dontwarn kotlin.**
