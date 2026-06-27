plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

val defaultAndroidNativeAbiFilters = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
val androidNativeAbiFilters = providers.gradleProperty("spela.android.abiFilters")
    .map { raw ->
        raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .also {
                require(it.isNotEmpty()) {
                    "spela.android.abiFilters must contain at least one ABI when provided"
                }
            }
    }
    .getOrElse(defaultAndroidNativeAbiFilters)

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.lifecycle.runtime)
                implementation(libs.koin.android)
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

android {
    namespace = "com.spela.player.android"
    compileSdk = 35

    // Pin the NDK explicitly instead of inheriting AGP's default. This is the
    // same version AGP 8.13.2 defaults to (27.0.12077973), so it's behaviour-
    // neutral — but pinning makes the version deterministic and, crucially,
    // lets CI pre-install this exact NDK so configuring :android (which the
    // desktop-only :shared:desktopTest + detekt jobs do) never triggers a
    // flaky on-the-fly NDK install. See #1281 and the CI pre-install step.
    ndkVersion = "27.0.12077973"

    sourceSets["main"].manifest.srcFile("src/main/AndroidManifest.xml")
    sourceSets["main"].java.srcDirs("src/main/java")
    sourceSets["main"].res.srcDirs("src/main/res")

    defaultConfig {
        applicationId = "com.spela.player"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        val appVersion = project.findProperty("appVersion")?.toString() ?: "1.0.0"
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Register FailureDiagnosticsListener so every test failure
        // captures screenshot + ui.xml + logcat + state.json + repro.txt
        // under /sdcard/spela-test-failures/. `run-e2e.sh` pulls them
        // to player/build/test-failures/ at the end of the run.
        testInstrumentationRunnerArguments["listener"] =
            "com.spela.player.android.FailureDiagnosticsListener"

        ndk {
            abiFilters += androidNativeAbiFilters
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use release keystore from env vars (CI), or fall back to debug signing
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    if (keystorePath != null) {
        signingConfigs.create("release") {
            storeFile = file(keystorePath)
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: ""
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("../native/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.8")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.8")
}

configurations.all {
    resolutionStrategy.force("androidx.tracing:tracing:1.3.0-alpha02")
}
