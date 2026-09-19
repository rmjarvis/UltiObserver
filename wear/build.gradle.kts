import java.util.Properties

val releaseSigningPropertiesFile = rootProject.file("release-signing.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile.inputStream().use { load(it) }
    }
}
val releaseSigningPropertyNames = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val missingReleaseSigningProperties = releaseSigningPropertyNames.filter { name ->
    releaseSigningPropertiesFile.isFile && releaseSigningProperties.getProperty(name).isNullOrBlank()
}
require(missingReleaseSigningProperties.isEmpty()) {
    "Missing release signing properties in ${releaseSigningPropertiesFile.name}: " +
        missingReleaseSigningProperties.joinToString(", ")
}
val hasReleaseSigningProperties = releaseSigningPropertiesFile.isFile

fun releaseSigningProperty(name: String): String {
    return releaseSigningProperties.getProperty(name)
        ?: error("Missing release signing property: $name")
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Wextra")
    }
}

android {
    namespace = "rmjarvis.ultiobserver"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "rmjarvis.ultiobserver"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "1.5.0alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigningProperties) {
            create("localRelease") {
                storeFile = rootProject.file(releaseSigningProperty("storeFile"))
                storePassword = releaseSigningProperty("storePassword")
                keyAlias = releaseSigningProperty("keyAlias")
                keyPassword = releaseSigningProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigningProperties) {
                signingConfig = signingConfigs.getByName("localRelease")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        if (providers.gradleProperty("includeReleaseScreenshotTools").isPresent) {
            getByName("androidTest").kotlin.directories.add(
                "../tools/release-screenshots/wear"
            )
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":wear-protocol"))
    constraints {
        implementation(libs.androidx.fragment) {
            because("Play Services otherwise packages the obsolete Fragment 1.1.0")
        }
    }
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.ui.tooling)
    implementation(libs.androidx.wear.tooling.preview)
    implementation(libs.androidx.wear.ongoing)
    implementation(libs.firebase.crashlytics)
    implementation(libs.play.services.wearable)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.mockito.android)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
