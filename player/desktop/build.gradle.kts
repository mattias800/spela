import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

val nativeBuildDir = project.layout.buildDirectory.dir("native")

val buildNativeLibrary by tasks.registering(Exec::class) {
    group = "native"
    description = "Build the spela-libretro native library for desktop"

    val nativeDir = nativeBuildDir.get().asFile
    val nativeSrc = project.file("../native")

    inputs.dir(nativeSrc.resolve("src"))
    inputs.file(nativeSrc.resolve("CMakeLists.txt"))
    outputs.dir(nativeDir)

    doFirst {
        nativeDir.mkdirs()
    }

    workingDir = nativeDir
    // Find cmake: system PATH, then Android SDK, then common Homebrew locations
    commandLine("sh", "-c", """
        CMAKE=${'$'}(command -v cmake 2>/dev/null)
        if [ -z "${'$'}CMAKE" ]; then
            for dir in "${'$'}HOME/Library/Android/sdk/cmake"/*/bin; do
                if [ -x "${'$'}dir/cmake" ]; then CMAKE="${'$'}dir/cmake"; break; fi
            done
        fi
        if [ -z "${'$'}CMAKE" ]; then
            for dir in /opt/homebrew/bin /usr/local/bin; do
                if [ -x "${'$'}dir/cmake" ]; then CMAKE="${'$'}dir/cmake"; break; fi
            done
        fi
        if [ -z "${'$'}CMAKE" ]; then echo "ERROR: cmake not found" >&2; exit 1; fi
        # Find JAVA_HOME for JNI headers
        JHOME=${'$'}([ -n "${'$'}JAVA_HOME" ] && echo "${'$'}JAVA_HOME" || /usr/libexec/java_home 2>/dev/null || echo "")
        echo "Using cmake: ${'$'}CMAKE"
        echo "Using JAVA_HOME: ${'$'}JHOME"
        "${'$'}CMAKE" -DJAVA_HOME="${'$'}JHOME" "${nativeSrc.absolutePath}" && make -j${'$'}(sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null || echo 4)
    """.trimIndent())
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.koin.core)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ktor.client.mock)
                implementation(libs.sqldelight.jvm.driver)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.spela.player.desktop.MainKt"

        jvmArgs += "-Djava.library.path=${nativeBuildDir.get().asFile.absolutePath}"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Spela"
            packageVersion = "1.0.0"

            macOS {
                bundleID = "com.spela.player"
            }

            windows {
                menuGroup = "Spela"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }

            linux {
                packageName = "spela-player"
            }
        }
    }
}

// Parallel test execution — each test class gets its own SpelaTestHarness with
// in-memory SQLite and isolated Compose/Skia surface, so they are safe to fork.
tasks.withType<Test> {
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(2)
    jvmArgs("-Xmx1024m")
}

// Make the run task depend on building the native library.
tasks.matching { it.name == "run" || it.name == "desktopRun" }.configureEach {
    dependsOn(buildNativeLibrary)
}
