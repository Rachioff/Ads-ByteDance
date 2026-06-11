# ═══════════════════════════════════════════════════════════════
# Ads-ByteDance ProGuard/R8 混淆规则
#
# 版本: v1.0
# 最后更新: 2026-06-11 (Day 11)
#
# 说明:
# - release 构建使用 proguard-android-optimize.txt（含优化）
# - 本文件补充应用级 keep 规则
# - 所有依赖库的规则基于官方文档 + 实测验证
# ═══════════════════════════════════════════════════════════════

# ── 通用规则 ──────────────────────────────────────────────────

# 保留行号信息（崩溃堆栈可读）
-keepattributes SourceFile,LineNumberTable

# 保留泛型签名（Retrofit / Kotlinx Serialization 需要）
-keepattributes Signature

# 保留注解（运行时反射可能用到）
-keepattributes *Annotation*

# 保留异常信息
-keepattributes Exceptions

# 保留 BuildConfig（Release 中也要用）
-keep class com.bytedance.ads_bytedance.BuildConfig { *; }

# 保留 R 类
-keepclassmembers class **.R$* { public static <fields>; }

# ── Kotlin ─────────────────────────────────────────────────────

# 保留 Kotlin 伴生对象
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ── Kotlinx Serialization ──────────────────────────────────────

# 保留 @Serializable 类及其伴生对象的 serializer()
# 不保留的话编译期生成的序列化器会被 R8 移除 → 运行时 crash
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-keep,includedescriptorclasses class com.bytedance.ads_bytedance.**$$serializer { *; }
-keepclassmembers class com.bytedance.ads_bytedance.** {
    *** Companion;
}
-keepclasseswithmembers class com.bytedance.ads_bytedance.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 保留所有数据模型（@Serializable 注解的类）
-keep @kotlinx.serialization.Serializable class com.bytedance.ads_bytedance.data.model.** { *; }
-keep @kotlinx.serialization.Serializable class com.bytedance.ads_bytedance.ai.model.** { *; }
-keep @kotlinx.serialization.Serializable class com.bytedance.ads_bytedance.ai.chat.model.** { *; }
-keep @kotlinx.serialization.Serializable class com.bytedance.ads_bytedance.behavior.model.** { *; }

# Kotlinx Serialization 核心
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Retrofit ────────────────────────────────────────────────────

# Retrofit 接口方法签名不能被混淆（动态代理调用）
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# 保留 API 接口
-keep,allowobfuscation,allowshrinking class com.bytedance.ads_bytedance.data.remote.AdApiService { *; }
-keep,allowobfuscation,allowshrinking class com.bytedance.ads_bytedance.ai.api.AiApiService { *; }
-keep,allowobfuscation,allowshrinking class com.bytedance.ads_bytedance.ai.api.ChatBotService { *; }

# ── OkHttp ──────────────────────────────────────────────────────

# OkHttp 使用 Okio，需保留
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ── Coil (3.x) ──────────────────────────────────────────────────

# Coil 3.x 使用 Kotlin 协程和 ImageLoader，混淆后可能失效
-keep class coil3.** { *; }
-dontwarn coil3.**

# ── Room ────────────────────────────────────────────────────────

# Room Entity 和 DAO 不能被混淆（运行时生成实现类）
-keep class com.bytedance.ads_bytedance.data.local.entity.** { *; }
-keep class com.bytedance.ads_bytedance.data.local.dao.** { *; }

# Room 生成的实现类
-keep class com.bytedance.ads_bytedance.data.local.AppDatabase_Impl { *; }

# ── Koin ────────────────────────────────────────────────────────

# Koin 依赖注入通过反射创建实例，模块声明不能被混淆
-keep class com.bytedance.ads_bytedance.di.** { *; }

# Koin ViewModel 声明
-keep class ** extends androidx.lifecycle.ViewModel { *; }

# ── ExoPlayer / Media3 ──────────────────────────────────────────

# Media3 ExoPlayer 使用反射加载渲染器和解码器
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── Kotlin Coroutines ───────────────────────────────────────────

# 协程内部类（如 SuspendLambda）不能被混淆
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ── Compose ─────────────────────────────────────────────────────

# Compose 运行时通过反射调用 Composable 函数
-keep class androidx.compose.** { *; }

# Compose compiler 生成的辅助类
-keep class androidx.compose.runtime.** { *; }

# Material3
-keep class androidx.compose.material3.** { *; }

# ── AndroidX ────────────────────────────────────────────────────

# Navigation Compose
-keep class androidx.navigation.** { *; }

# Lifecycle
-keep class androidx.lifecycle.** { *; }

# ── 数据类 ──────────────────────────────────────────────────────

# AdItem 密封类子类（序列化反序列化需要）
-keep class com.bytedance.ads_bytedance.data.model.AdItem { *; }
-keep class com.bytedance.ads_bytedance.data.model.AdItem$LargeImageAd { *; }
-keep class com.bytedance.ads_bytedance.data.model.AdItem$SmallImageAd { *; }
-keep class com.bytedance.ads_bytedance.data.model.AdItem$VideoAd { *; }

# 枚举（序列化使用 name() / valueOf()）
-keepclassmembers enum com.bytedance.ads_bytedance.data.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers enum com.bytedance.ads_bytedance.behavior.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── 其他 ────────────────────────────────────────────────────────

# 保留 Parcelable 实现
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
