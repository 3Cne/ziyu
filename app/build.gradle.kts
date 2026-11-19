import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.compose.screenshot")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
fun signingValue(key: String, env: String): String =
    keystoreProperties.getProperty(key) ?: System.getenv(env) ?: ""

android {
    namespace = "com.moyu.writer"
    compileSdk = 37
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    defaultConfig {
        applicationId = "com.moyu.writer"
        minSdk = 26
        targetSdk = 36
        versionCode = 863
        versionName = "3.26.44"
        val updateRepo = (project.findProperty("updateRepo") as String?) ?: ""
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(
                keystoreProperties.getProperty("storeFile") ?: "moyu-release.keystore"
            )
            storePassword = signingValue("storePassword", "MOYU_STORE_PASSWORD")
            keyAlias = signingValue("keyAlias", "MOYU_KEY_ALIAS").ifBlank { "moyu" }
            keyPassword = signingValue("keyPassword", "MOYU_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui:1.9.5")
    implementation("androidx.compose.ui:ui-graphics:1.9.5")
    implementation("androidx.compose.foundation:foundation:1.9.5")
    implementation("androidx.compose.animation:animation:1.9.5")
    // Local copy of Haze 1.7.3, compiled against a newer Compose. It is used through
    // the small Java bridge below so the Kotlin compiler never has to read its metadata.
    implementation(files("libs/haze-1.7.3.aar"))
    // Floating tab bar glass: Kyant0/AndroidLiquidGlass, Apache-2.0.
    // 1.0.0 is the last release built against Compose 1.9.4 / Kotlin 2.2.21,
    // which matches this project without a build-chain upgrade.
    implementation("io.github.kyant0:backdrop:1.0.0")
    // @Preview annotations live in main sources next to the composables they render.
    // The library is compile-time only; release builds compile it but ship nothing extra.
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.5")
    debugImplementation("androidx.compose.ui:ui-tooling:1.9.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.9.5")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.74.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.74.0")
    // Layoutlib renderer for `updateDebugScreenshotTest`: renders the @Preview
    // functions in src/screenshotTest without a device or an IDE window.
    screenshotTestImplementation("androidx.compose.ui:ui-tooling:1.9.5")
    screenshotTestImplementation("androidx.compose.ui:ui-tooling-preview:1.9.5")
}

































































































































