plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.localfm.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.localfm.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 19
        versionName = "1.3.5"
    }

    signingConfigs {
        create("release") {
            val rootKs = rootProject.file("release.jks")
            storeFile = if (rootKs.exists()) rootKs else file("release.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "B4nu25gNrVzwsVSSe-N25bVP"
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "release"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "B4nu25gNrVzwsVSSe-N25bVP"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Bộ icon vector Material chính thức (đơn sắc) — không dùng emoji
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // Máy chủ HTTP nhúng để chia sẻ file trong LAN
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    // Tạo mã QR cho địa chỉ Web Share
    implementation("com.google.zxing:core:3.5.3")
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
