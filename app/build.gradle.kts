import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoReport
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
    alias(libs.plugins.kotlin.serialization)
    jacoco
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Wextra")
    }
}

android {
    namespace = "rmjarvis.ultiobserver"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "rmjarvis.ultiobserver"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.0alpha"

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
                "proguard-rules.pro"
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
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.colorpicker.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

val filteredCoverageExclusions = listOf(
    // Android/resource/generated support.
    "**/R.class",
    "**/R\$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    // Kotlin/Compose compiler scaffolding that is not user-actionable app behavior.
    "**/*\$WhenMappings.*",
    "**/ComposableSingletons*.*",
    // IDE previews are design-time helpers rather than app behavior.
    "**/*Preview*.*",
    // Static theme definitions are visual constants, not interactive behavior.
    "**/rmjarvis/ultiobserver/ui/theme/**",
)

tasks.register<JacocoReport>("filteredCoverageReport") {
    group = "verification"
    description = "Generates a JaCoCo report excluding previews, generated scaffolding, and static theme code."

    dependsOn("testDebugUnitTest", "connectedDebugAndroidTest")

    reports {
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/filtered/html"))
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/filtered/filteredCoverageReport.xml"))
        csv.required.set(false)
    }

    classDirectories.setFrom(
        files(
            fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
                exclude(filteredCoverageExclusions)
            },
            fileTree(layout.buildDirectory.dir("intermediates/javac/debug/classes")) {
                exclude(filteredCoverageExclusions)
            },
        )
    )
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                "outputs/code_coverage/debugAndroidTest/connected/**/*.ec",
            )
        }
    )
}

tasks.register<JavaExec>("estimateBackupSize") {
    group = "verification"
    description = "Serializes a high-activity representative game and reports Android backup JSON sizes."

    val unitTestTask = tasks.named<Test>("testDebugUnitTest")
    dependsOn("compileDebugUnitTestKotlin")

    mainClass.set("rmjarvis.ultiobserver.BackupSizeEstimateToolKt")
    classpath = unitTestTask.get().classpath
    workingDir = rootProject.projectDir
}
