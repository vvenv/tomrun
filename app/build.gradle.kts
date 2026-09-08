plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vvenv.tomrun"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vvenv.tomrun"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.0"
        buildConfigField(
            "String",
            "LEADERBOARD_API_BASE",
            "\"${project.findProperty("LEADERBOARD_API_BASE") ?: ""}\""
        )
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "LEADERBOARD_API_BASE",
                "\"${project.findProperty("LEADERBOARD_API_BASE") ?: "http://10.0.2.2:8787"}\""
            )
        }
        release {
            isMinifyEnabled = false
            // 用 debug 签名，方便直接 adb install 分发试玩
            signingConfig = signingConfigs.getByName("debug")
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
