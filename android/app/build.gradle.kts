import java.net.URI
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

/**
 * The API key sent as `X-API-Key` on every backend request (ADR-0013).
 *
 * Resolved like the signing config — from `apiKey` in `local.properties` or the
 * `MFL_API_KEY` environment variable — so it is configurable per machine and in CI
 * without ever being hardcoded. Empty when unset: local development against a
 * backend with authentication disabled needs no key, and the interceptor simply
 * omits the header. A release build that forgets it warns (below), because a
 * secured production backend rejects unauthenticated requests with 401.
 *
 * The key is embedded in the APK, where it is extractable. That is the accepted
 * limitation of app-level (not user-level) auth for V1; see ADR-0013.
 */
fun resolveApiKey(): String {
    val localProperties = rootProject.file("local.properties").takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
    return (localProperties?.getProperty("apiKey") ?: System.getenv("MFL_API_KEY"))
        ?.trim()?.takeIf { it.isNotEmpty() }
        ?: ""
}

/**
 * Application version, read from the tracked `version.properties`.
 *
 * `versionName` is the only value anyone edits. `versionCode` is derived from
 * it — MAJOR * 10000 + MINOR * 100 + PATCH — so the two can never disagree and
 * nobody has to remember to bump a second number. Android rejects an upgrade
 * whose versionCode did not increase, and that failure looks like a broken
 * install rather than a forgotten edit, so deriving it removes an entire class
 * of release-day confusion.
 *
 * 1.0.0 becomes 10000, leaving room below it (the previous hand-set value was
 * 1) and 99 patch releases between minors.
 */
fun resolveVersion(): Pair<Int, String> {
    val file = rootProject.file("version.properties")
    require(file.exists()) { "version.properties is missing; see docs/RELEASE_CHECKLIST.md" }
    val name = Properties().apply { file.inputStream().use(::load) }
        .getProperty("versionName")?.trim().orEmpty()

    val parts = Regex("""^(\d+)\.(\d+)\.(\d+)$""").find(name)?.destructured
        ?: throw GradleException("versionName '$name' must be MAJOR.MINOR.PATCH")
    val (major, minor, patch) = parts.toList().map(String::toInt)
    require(minor < 100 && patch < 100) {
        "versionName '$name': MINOR and PATCH must each be below 100"
    }
    return (major * 10000 + minor * 100 + patch) to name
}

/**
 * Signing configuration, read from `local.properties` or the environment.
 *
 * Never from a tracked file: a committed keystore or password is compromised
 * permanently, and unlike most secrets this one cannot be rotated — the signing
 * key *is* the app's identity, and Android will not accept an update signed by
 * a different one.
 *
 * Returns null when nothing is configured, which is the ordinary case on a
 * machine that only builds debug. `assembleRelease` then still produces an
 * unsigned APK, exactly as before; it does not fail. The build warns instead,
 * because a release build silently coming out unsigned is precisely the
 * surprise TD-006 was about.
 */
fun resolveSigningCredentials(): Map<String, String>? {
    val localProperties = rootProject.file("local.properties").takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

    fun value(property: String, environment: String): String? =
        (localProperties?.getProperty(property) ?: System.getenv(environment))
            ?.trim()?.takeIf { it.isNotEmpty() }

    val storeFile = value("releaseKeystorePath", "MFL_KEYSTORE_PATH") ?: return null
    val storePassword = value("releaseKeystorePassword", "MFL_KEYSTORE_PASSWORD")
    val keyAlias = value("releaseKeyAlias", "MFL_KEY_ALIAS")
    val keyPassword = value("releaseKeyPassword", "MFL_KEY_PASSWORD") ?: storePassword

    if (storePassword == null || keyAlias == null || keyPassword == null) {
        throw GradleException(
            "releaseKeystorePath is set but the rest of the signing configuration is " +
                "not. Needed: releaseKeystorePassword, releaseKeyAlias " +
                "(releaseKeyPassword defaults to the store password). " +
                "See docs/RELEASE_CHECKLIST.md.",
        )
    }
    return mapOf(
        "storeFile" to storeFile,
        "storePassword" to storePassword,
        "keyAlias" to keyAlias,
        "keyPassword" to keyPassword,
    )
}

/**
 * Generates the release network security configuration.
 *
 * Android forbids cleartext from API 28, and it is right to. But Version 1 is a
 * *local production release* (ROADMAP M12 / MILESTONE_12_PLAN §4, Option B):
 * the backend stays on the LAN over plain HTTP, and deploying TLS purely to
 * satisfy a release milestone was rejected as speculative infrastructure.
 *
 * So the release build needs an exemption, and the only question is how wide.
 * The debug config permits cleartext to *any* host, which is defensible there —
 * debug builds are never distributed. It is not defensible in a release build.
 *
 * This narrows it to exactly one host: whatever `apiBaseUrl` names. Nothing
 * else on the network becomes reachable in cleartext, so the exemption is as
 * small as the deployment actually requires. It is generated rather than
 * checked in because a resource file cannot read local.properties, and the LAN
 * address is machine-specific — the alternative is a tracked file containing
 * somebody's IP address, which is how it eventually gets committed.
 *
 * When the backend moves behind TLS, `apiBaseUrl` becomes an https URL and this
 * emits a config that permits no cleartext at all. The exemption removes itself
 * rather than needing to be remembered.
 */
abstract class GenerateNetworkSecurityConfig : DefaultTask() {
    @get:Input
    abstract val baseUrl: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val host = runCatching { URI(baseUrl.get()).host }.getOrNull()
        val cleartext = baseUrl.get().startsWith("http://") && !host.isNullOrBlank()

        val body = if (cleartext) {
            """
            |    <!-- Cleartext to the configured backend host only. -->
            |    <base-config cleartextTrafficPermitted="false" />
            |    <domain-config cleartextTrafficPermitted="true">
            |        <domain includeSubdomains="false">$host</domain>
            |    </domain-config>
            """.trimMargin()
        } else {
            """
            |    <!-- The backend is reached over TLS; no exemption is needed. -->
            |    <base-config cleartextTrafficPermitted="false" />
            """.trimMargin()
        }

        val xml = outputDirectory.get().asFile.resolve("xml").apply { mkdirs() }
            .resolve("network_security_config.xml")
        xml.writeText(
            """
            |<?xml version="1.0" encoding="utf-8"?>
            |<!-- GENERATED by :app:generateReleaseNetworkSecurityConfig. Do not edit. -->
            |<network-security-config>
            |$body
            |</network-security-config>
            |
            """.trimMargin(),
        )
        if (cleartext) {
            logger.lifecycle(
                "[release] cleartext permitted to '$host' only (local production release; " +
                    "set an https apiBaseUrl to remove this entirely).",
            )
        }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.test.retry)
}

val generateReleaseNetworkSecurityConfig =
    tasks.register<GenerateNetworkSecurityConfig>("generateReleaseNetworkSecurityConfig") {
        description = "Writes the release network security config from apiBaseUrl."
        baseUrl.set(resolveApiBaseUrl())
        outputDirectory.set(layout.buildDirectory.dir("generated/res/networkSecurityConfig"))
    }

android {
    namespace = "com.myfitnesslog"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.myfitnesslog"
        minSdk = 26
        targetSdk = 35
        // Both derived from version.properties; see resolveVersion().
        versionCode = resolveVersion().first
        versionName = resolveVersion().second

        testInstrumentationRunner = "com.myfitnesslog.HiltTestRunner"

        // Room schema export directory. Exported schemas are committed to Git
        // so the local database has the same migration discipline Flyway gives
        // the backend (see docs/ANDROID_ARCHITECTURE.md, decision 7).
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        val credentials = resolveSigningCredentials()
        if (credentials != null) {
            create("release") {
                storeFile = file(credentials.getValue("storeFile"))
                storePassword = credentials.getValue("storePassword")
                keyAlias = credentials.getValue("keyAlias")
                keyPassword = credentials.getValue("keyPassword")

                // v2/v3 are what the platform verifies. v1 (JAR signing) is only
                // needed below API 24 and minSdk is 26, so AGP skips it and
                // `apksigner verify` reports "v1: false, v2: true, v3: true" —
                // that is correct, not a partial signature. Stated explicitly so
                // the schemes are a decision rather than a default.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Debug-only network logging is gated on this flag at runtime.
            buildConfigField("boolean", "ENABLE_NETWORK_LOGGING", "true")
            buildConfigField("String", "API_BASE_URL", "\"${resolveApiBaseUrl()}\"")
            buildConfigField("String", "API_KEY", "\"${resolveApiKey()}\"")
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            if (signingConfig == null) {
                logger.warn(
                    "\n[release] No signing configuration found — assembleRelease will " +
                        "produce an UNSIGNED apk, which installs nowhere. Configure " +
                        "releaseKeystorePath in local.properties; see " +
                        "docs/RELEASE_CHECKLIST.md.\n",
                )
            }

            // R8 stays off for V1. Room, Hilt, Retrofit and kotlinx.serialization
            // all depend on generated code or reflection, so enabling shrinking
            // is a real risk; the app is small enough that it buys nothing worth
            // that risk. proguardFiles stays configured so turning it on later is
            // a one-line change made deliberately (MILESTONE_12_PLAN §5).
            isMinifyEnabled = false
            buildConfigField("boolean", "ENABLE_NETWORK_LOGGING", "false")
            // Release has no development fallback: a real deployment must set
            // this deliberately, over HTTPS. Left as the emulator loopback it
            // would fail fast rather than silently talking to nothing.
            buildConfigField("String", "API_BASE_URL", "\"${resolveApiBaseUrl()}\"")
            buildConfigField("String", "API_KEY", "\"${resolveApiKey()}\"")
            if (resolveApiKey().isEmpty()) {
                logger.warn(
                    "\n[release] No apiKey configured — requests will OMIT X-API-Key, and a " +
                        "secured backend rejects them with 401. Set apiKey in local.properties " +
                        "or the MFL_API_KEY environment variable; see ADR-0013 / DEPLOYMENT.md.\n",
                )
            }
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
        // AGP's legacy sourceSet container does not carry a task dependency from
        // a provider, so the directory is declared here and the generator is
        // hooked onto preReleaseBuild below — resource merging happens after
        // preBuild, so the file is always present by the time aapt looks.
        getByName("release") {
            res.srcDir(generateReleaseNetworkSecurityConfig.map { it.outputDirectory })
        }
    }
}

// See the sourceSets note above: this is what actually guarantees the generated
// network security config exists before resources are merged.
// `matching` rather than `named`: AGP creates these tasks lazily during its own
// evaluation, so they do not exist yet at this point in the script.
tasks.matching { it.name == "preReleaseBuild" || it.name == "mergeReleaseResources" }
    .configureEach { dependsOn(generateReleaseNetworkSecurityConfig) }

/**
 * Retry a failed test once, but only on CI (TD-015).
 *
 * WorkoutViewModelTest, and whichever class happens to run after it, fail roughly
 * one run in four while the code is correct: a leaked coroutine is still using
 * `Dispatchers.Main` when a test class installs or resets it. Two attempts to fix
 * the cause were measured and reverted, one of which made it four times worse.
 * docs/TECH_DEBT.md TD-015 has the detail and the dead ends.
 *
 * This is containment, and the part that matters is that it hides nothing. A test
 * that fails and then passes is reported as **FLAKY**, not as green, so the
 * problem stays visible and countable rather than turning into a habit of
 * re-running red builds. `maxFailures` still fails the build outright when the
 * suite is broadly broken rather than merely flaky, so a real regression cannot
 * hide behind a retry.
 *
 * Local runs are deliberately untouched: `./gradlew test` on a developer machine
 * shows the truth, flake included, because that is where the fix will be worked
 * on.
 */
tasks.withType<Test>().configureEach {
    retry {
        maxRetries.set(if (System.getenv("CI") != null) 1 else 0)
        // A suite failing this widely is not flaky, it is broken. Stop retrying
        // and report it.
        maxFailures.set(5)
        // A retried pass is a pass for the build, and a FLAKY entry in the report.
        failOnPassedAfterRetry.set(false)
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

    // Room (local persistence: entities, DAOs, and migrations)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore (app settings)
    implementation(libs.androidx.datastore.preferences)

    // Networking (Retrofit + OkHttp for backend synchronization)
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
