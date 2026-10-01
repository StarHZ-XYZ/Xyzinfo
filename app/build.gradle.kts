import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.rjy.xyz.apps.xyzinfo"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.rjy.xyz.apps.xyzinfo"
        minSdk = 24
        targetSdk = 36
        versionCode = 12
        versionName = "1.0.2"

        // 构建号：形如 20260930.0412（每次构建都不同，便于区分同一版本的不同构建）
        val buildStamp = SimpleDateFormat("yyyyMMdd.HHmm").format(Date())
        buildConfigField("String", "BUILD_NUMBER", "\"$buildStamp\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            /*
             * 安全加固：release 打开 R8 混淆 + 资源压缩。
             *
             * 混淆后类名/方法名/字段名全部被重命名，并统一收敛到一个包（-repackageclasses），
             * 反编译出来只剩 a.a(a) 这种没有语义的骨架；同时去掉行号与源文件名，
             * 崩溃堆栈也不再泄露原始代码结构。
             *
             * 注意：发布包用和调试包同一把密钥签名（本项目发布的一直是可以直接安装的包），
             * SecurityGuard 里的签名指纹就是这把密钥，换了密钥要同步改指纹。
             */
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // 界面渲染自检需要读取 res 资源
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
