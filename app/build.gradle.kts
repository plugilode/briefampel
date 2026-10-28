import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.android.compose.screenshot")
}

// Firebase wird nur eingebunden, wenn die eigene google-services.json vorliegt (nicht in Git).
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

// Persönliche Testeinstellungen aus local.properties (nicht in Git).
val lokal = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun einstellung(name: String, standard: String): String =
    providers.gradleProperty(name).orNull ?: lokal.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() } ?: standard

android {
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
    namespace = "de.briefampel.app"
    compileSdk = 36

    defaultConfig {
        // Paket-ID vor dem ersten Upload festlegen – danach nicht mehr änderbar.
        applicationId = "de.briefampel.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0"
        buildConfigField("String", "AI_MODEL", "\"${einstellung("briefampel.model", "gemini-3.5-flash")}\"")
        buildConfigField("String", "AI_MODEL_FALLBACK", "\"${einstellung("briefampel.fallbackModel", "gemini-3.5-flash-lite")}\"")
        // Adresse des eigenen Servers (Firebase Functions), z. B. https://europe-west3-PROJEKT.cloudfunctions.net/api
        buildConfigField("String", "SERVER_URL", "\"${einstellung("briefampel.server", "")}\"")
        buildConfigField("String", "AI_REGION", "\"${einstellung("briefampel.region", "eu")}\"")
    }

    buildTypes {
        debug {
            // Nur für die eigene Test-APK: ein Schlüssel aus local.properties wird beim ersten Start übernommen.
            buildConfigField("String", "TEST_GEMINI_KEY", "\"${einstellung("briefampel.geminiKey", "")}\"")
        }
        release {
            // Niemals einen Schlüssel in eine Version für andere Nutzer einbauen.
            buildConfigField("String", "TEST_GEMINI_KEY", "\"\"")
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
    implementation("androidx.biometric:biometric:1.1.0")
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")
    implementation("com.android.billingclient:billing-ktx:9.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
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
