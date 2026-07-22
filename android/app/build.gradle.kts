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

// ---------------------------------------------------------------------------
// Device safety guard
// ---------------------------------------------------------------------------
//
// Gradle's `connectedAndroidTest` installs an app + test APK and uninstalls both
// afterwards. On 2026-07-22 that cleanup ran against the developer's daily-use
// phone and deleted the app's Room database — two routines and four workout
// sessions. Nothing was permanently lost (it had all synced, and the backend is
// the source of truth per ADR-0003), but synchronization is one-way, so the
// phone could not get its history back.
//
// The rule that followed — "never point connectedAndroidTest at the daily-use
// device" — would otherwise have stayed a convention in a document. This makes
// it structural: device-lifecycle tasks refuse to run against anything that is
// not an emulator.
//
// Note what is *not* being prevented. The instrumented tests pass perfectly well
// on real hardware; all six do. It is the install/uninstall lifecycle that is
// unsafe on a device holding irreplaceable data, which is why the error points
// at `am instrument` — it runs the same tests and uninstalls nothing.
//
// Escape hatch: -PallowPhysicalDeviceTests=true, deliberately verbose so that
// using it is a decision rather than a reflex.
//
// Everything the check needs is resolved at configuration time into plain
// serializable values; the execution-time action closes over nothing but those.
// Capturing build-script functions here would break the configuration cache.

run {
    val adbPath = android.sdkDirectory.resolve("platform-tools/adb").absolutePath
    val overrideRequested =
        providers.gradleProperty("allowPhysicalDeviceTests").orNull == "true"
    val explicitSerial = providers.environmentVariable("ANDROID_SERIAL").orNull

    // connected*AndroidTest installs then uninstalls; uninstall* removes outright.
    val deviceLifecycleTask = Regex("^(connected.*AndroidTest|uninstall.*)$")

    tasks.matching { deviceLifecycleTask.matches(it.name) }.configureEach {
        val taskName = name
        doFirst {
            if (overrideRequested) {
                logger.warn(
                    "\n[device-guard] OVERRIDDEN for '$taskName'. This task can uninstall " +
                        "the app, which deletes its database. Be sure nothing on the target " +
                        "device is irreplaceable.\n",
                )
                return@doFirst
            }

            val adb = File(adbPath)
            if (!adb.canExecute()) {
                logger.warn("[device-guard] adb not found at $adbPath; skipping the check.")
                return@doFirst
            }

            fun adb(vararg args: String): String = try {
                val process = ProcessBuilder(listOf(adb.absolutePath) + args)
                    .redirectErrorStream(true)
                    .start()
                val text = process.inputStream.bufferedReader().readText()
                process.waitFor()
                text
            } catch (e: Exception) {
                ""
            }

            val targets = if (!explicitSerial.isNullOrBlank()) {
                listOf(explicitSerial)
            } else {
                adb("devices").lineSequence().drop(1).mapNotNull { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 2 && parts[1] == "device") parts[0] else null
                }.toList()
            }
            if (targets.isEmpty()) return@doFirst

            // The serial alone is not enough: a physical device reached over
            // wireless debugging also gets an adb-style serial, so the build
            // properties are the authority and the serial is only a fast path.
            val physical = targets.filterNot { serial ->
                serial.startsWith("emulator-") ||
                    adb("-s", serial, "shell", "getprop", "ro.kernel.qemu").trim() == "1" ||
                    adb("-s", serial, "shell", "getprop", "ro.build.characteristics")
                        .contains("emulator") ||
                    adb("-s", serial, "shell", "getprop", "ro.product.model").trim()
                        .startsWith("sdk_")
            }
            if (physical.isEmpty()) return@doFirst

            val described = physical.joinToString {
                val model = adb("-s", it, "shell", "getprop", "ro.product.model").trim()
                if (model.isEmpty()) it else "$it ($model)"
            }

            throw GradleException(
                """
                |
                |'$taskName' would run against a physical device: $described
                |
                |This task installs and uninstalls APKs, and an uninstall deletes the
                |app's database. On a daily-use phone that is real training data, and
                |because synchronization is one-way it cannot be restored from the
                |backend.
                |
                |Run it on the emulator instead:
                |    ANDROID_SERIAL=emulator-5554 ./gradlew $taskName
                |
                |To exercise instrumented tests on the phone without the uninstall step:
                |    adb install -r app/build/outputs/apk/debug/app-debug.apk
                |    adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
                |    adb shell am instrument -w com.myfitnesslog.test/com.myfitnesslog.HiltTestRunner
                |
                |Back up first, whatever you do:
                |    adb exec-out run-as com.myfitnesslog cat databases/myfitnesslog.db > backup.db
                |
                |If you are certain, re-run with -PallowPhysicalDeviceTests=true
                |
                """.trimMargin(),
            )
        }
    }
}
