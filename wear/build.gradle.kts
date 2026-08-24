plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Wextra")
    }
}

android {
    namespace = "rmjarvis.ultiobserver.wear"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "rmjarvis.ultiobserver"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.3.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.ui.tooling)
    implementation(libs.androidx.wear.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
