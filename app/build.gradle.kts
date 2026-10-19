import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 正式签名读仓库根目录的 keystore.properties（已 gitignore）；没有这个文件时 release 退回 debug 签名，
// 只能自己装着试，scripts/release-website.sh 会拒绝上传这种包。
val releaseKeystore = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
    Properties().apply { f.inputStream().use { load(it) } }
}

android {
    namespace = "com.vvenv.tomrun"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vvenv.tomrun"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "1.2"
        buildConfigField(
            "String",
            "LEADERBOARD_API_BASE",
            "\"${project.findProperty("LEADERBOARD_API_BASE") ?: ""}\""
        )
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore.getProperty("storeFile"))
                storePassword = releaseKeystore.getProperty("storePassword")
                keyAlias = releaseKeystore.getProperty("keyAlias")
                keyPassword = releaseKeystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 单独的属性名：gradle.properties 里的 LEADERBOARD_API_BASE 是线上地址，
            // 共用一个名字会让调试包把测试成绩传进正式榜
            buildConfigField(
                "String",
                "LEADERBOARD_API_BASE",
                "\"${project.findProperty("LEADERBOARD_API_BASE_DEBUG") ?: "http://10.0.2.2:8787"}\""
            )
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
