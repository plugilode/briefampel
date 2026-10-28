plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.android.compose.screenshot")
}

android {
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
    namespace = "de.briefampel.app"
    compileSdk = 36

    defaultConfig {
        // Paket-ID vor dem ersten Upload festlegen – danach nicht mehr änderbar.
        applicationId = "de.briefampel.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
        buildConfigField("String", "AI_MODEL", "\"${providers.gradleProperty("briefampel.model").getOrElse("gemini-3.8-flash")}\"")
        buildConfigField("String", "AI_REGION", "\"${providers.gradleProperty("briefampel.region").getOrElse("eu")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    "screenshotTestImplementation"("com.android.tools.screenshot:screenshot-validation-api:0.0.1-alpha16")
    "screenshotTestImplementation"(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20250517")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
