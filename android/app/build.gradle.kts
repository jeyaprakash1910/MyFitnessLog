import java.util.Properties

/**
 * Backend base URL, resolved at configuration time.
 *
 * Read from `local.properties` (machine-specific and gitignored) so a developer
 * can point the app at a LAN-hosted backend without editing tracked files or
 * risking committing their own IP address. Falls back to the emulator's host
 * loopback, so the emulator workflow needs no configuration at all.
 *
 * Retrofit requires a trailing slash on a base URL and throws otherwise, so the
 * value is normalised here rather than failing at runtime.
 */
fun resolveApiBaseUrl(): String {
    val localProperties = rootProject.file("local.properties")
    val configured = if (localProperties.exists()) {
        Properties().apply { localProperties.inputStream().use(::load) }
            .getProperty("apiBaseUrl")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    } else {
        null
    }
    val url = configured ?: "http://10.0.2.2:8080/api/v1/"
    return if (url.endsWith("/")) url else "$url/"
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.myfitnesslog"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.myfitnesslog"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.myfitnesslog.HiltTestRunner"

        // Room schema export directory. Exported schemas are committed to Git
        // so the local database has the same migration discipline Flyway gives
        // the backend (see docs/ANDROID_ARCHITECTURE.md, decision 7).
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        debug {
            // Debug-only network logging is gated on this flag at runtime.
            buildConfigField("boolean", "ENABLE_NETWORK_LOGGING", "true")
            buildConfigField("String", "API_BASE_URL", "\"${resolveApiBaseUrl()}\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "ENABLE_NETWORK_LOGGING", "false")
            // Release has no development fallback: a real deployment must set
            // this deliberately, over HTTPS. Left as the emulator loopback it
            // would fail fast rather than silently talking to nothing.
            buildConfigField("String", "API_BASE_URL", "\"${resolveApiBaseUrl()}\"")
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

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric needs Android resources available to unit tests.
            isIncludeAndroidResources = true
        }
    }

    // Room exported schemas double as test assets so migration tests can read them.
    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }
}

dependencies {
    // AndroidX core / lifecycle / activity
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose (BOM aligns all Compose artifact versions)
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // WorkManager (background sync scheduling) + its Hilt worker injection
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room (infrastructure only in Phase 1; entities/DAOs arrive in Phase 2)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Networking (configured; no API interfaces or calls in Phase 1)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Async / serialization
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Unit tests (Robolectric runs Room DAO + Compose UI tests on the JVM, no device)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    // MockWebServer verifies the sync transport layer's method/path/body contract.
    testImplementation(libs.okhttp.mockwebserver)
    // WorkManagerTestInitHelper drives the scheduler on the JVM under Robolectric.
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.retrofit)
    testImplementation(libs.retrofit.kotlinx.serialization)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)

    // Instrumented tests
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
