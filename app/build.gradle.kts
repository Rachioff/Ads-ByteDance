import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)       // kotlin-compose 已内置 kotlin-android，不需要重复声明
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
}

// 读取 local.properties 中的敏感配置（API Key 等）
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(FileInputStream(file))
}

android {
    namespace = "com.bytedance.ads_bytedance"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.bytedance.ads_bytedance"
        minSdk = 26        // API 26 (Android 8.0)，符合 tech.md 要求
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // BuildConfig 数据源开关：mock → 本地 JSON；remote → 网络 API
        buildConfigField("String", "DATA_MODE", "\"mock\"")

        // AI API Key（从 local.properties 注入，不提交 Git）
        buildConfigField("String", "AI_API_KEY",
            "\"${localProperties.getProperty("ai.api.key", "")}\"")
        buildConfigField("String", "AI_API_BASE_URL",
            "\"${localProperties.getProperty("ai.api.base.url", "https://api.openai.com")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // JVM 目标版本通过 kotlin {} 顶层块设置（见文件末尾）

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // ── Compose (通过 BOM 统一管理版本) ──
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // ── AndroidX 核心 ──
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ── 导航 (Navigation Compose) ──
    implementation(libs.navigation.compose)

    // ── 网络 (OkHttp + Retrofit) ──
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)

    // ── 图片加载 (Coil 3.x) ──
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)

    // ── 视频播放 (Media3 ExoPlayer) ──
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // ── 本地存储 (Room + DataStore) ──
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    // ── 依赖注入 (Koin) ──
    implementation(libs.koin.android)

    // ── 协程 ──
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // ── 序列化 (Kotlinx Serialization) ──
    implementation(libs.kotlinx.serialization.json)

    // ── 测试 ──
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}
