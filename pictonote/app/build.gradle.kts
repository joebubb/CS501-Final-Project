import java.util.Properties
import java.io.FileInputStream

val properties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    properties.load(FileInputStream(localPropertiesFile))
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.pictoteam.pictonote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pictoteam.pictonote"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        var apiKey: String? = properties.getProperty("GEMINI_API_KEY") ?: System.getenv("GEMINI_API_KEY")
        if (apiKey == null) {
            apiKey = "\"MISSING_API_KEY\""
        } else {
            apiKey = "\"$apiKey\""
        }
        buildConfigField("String", "GEMINI_API_KEY", apiKey)
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "11"
        // Add these compiler args to improve compatibility with Kotlin 2.0.0
        freeCompilerArgs += listOf(
            "-Xskip-prerelease-check",
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=true"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Updated composeOptions for compatibility with Kotlin 2.0.0
    composeOptions {
        // Updated to a version more compatible with Kotlin 2.0.0
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

// Modified resolution strategy with harmonized component versions
configurations.all {
    resolutionStrategy {
        // Force a consistent version of ALL lifecycle components - downgraded to 2.6.2
        force("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
        force("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
        force("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
        force("androidx.lifecycle:lifecycle-runtime:2.6.2")
        force("androidx.lifecycle:lifecycle-viewmodel:2.6.2")
        force("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.6.2")

        // Force a consistent version of navigation components - downgraded to match lifecycle
        force("androidx.navigation:navigation-compose:2.7.5")
        force("androidx.navigation:navigation-runtime-ktx:2.7.5")

        // Keep your exclusions for lifecycle libraries
        exclude(group = "androidx.lifecycle", module = "lifecycle-runtime-android")
        exclude(group = "androidx.lifecycle", module = "lifecycle-viewmodel-android")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    // Lifecycle with exclusions to prevent duplicate class errors
    implementation(libs.androidx.lifecycle.runtime.ktx) {
        exclude(group = "androidx.lifecycle", module = "lifecycle-runtime-android")
    }
    implementation(libs.androidx.lifecycle.viewmodel.ktx) {
        exclude(group = "androidx.lifecycle", module = "lifecycle-runtime-android")
        exclude(group = "androidx.lifecycle", module = "lifecycle-viewmodel-android")
    }

    // Explicitly add the lifecycle-viewmodel-compose with downgraded version
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2") {
        exclude(group = "androidx.lifecycle", module = "lifecycle-runtime-android")
        exclude(group = "androidx.lifecycle", module = "lifecycle-viewmodel-android")
    }

    // Add the savedstate handler explicitly
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.6.2") {
        exclude(group = "androidx.lifecycle", module = "lifecycle-runtime-android")
        exclude(group = "androidx.lifecycle", module = "lifecycle-viewmodel-android")
    }

    implementation(libs.androidx.activity.compose)

    // Updated to use the current BOM version format
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.runtime.livedata)

    // Navigation components with downgraded versions
    implementation("androidx.navigation:navigation-runtime-ktx:2.7.5")
    implementation("androidx.navigation:navigation-compose:2.7.5")

    implementation(libs.androidx.datastore.core.android)
    implementation(libs.androidx.datastore.preferences.core.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Updated to use the same BOM version for tests
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))

    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.auth)
    implementation(libs.play.services.auth)
    implementation(libs.google.firebase.auth.ktx)

    implementation(libs.retrofit)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.converter.moshi)
}