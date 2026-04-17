// Generated-code module. Holds the Kotlin client produced by
// openapi-generator from web/src/generated/openapi.json (the huma-emitted
// OpenAPI 3.1 spec). Do not hand-edit files under src/commonMain — run
// scripts/regen-kotlin-api.sh to refresh.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}

kotlin {
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                // ktor client + kotlinx serialization are the runtime
                // surface the generated ApiClient needs.
                api(libs.ktor.client.core)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.serialization.json)
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.datetime)
            }
        }
    }
}

android {
    namespace = "com.spela.client"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
