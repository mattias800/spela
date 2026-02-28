import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

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
    // Fail individual tests that hang instead of blocking the entire suite.
    // Per-test timeout: 30 seconds. Per-class (suite) timeout: 120 seconds.
    systemProperty("junit.jupiter.execution.timeout.default", "30s")
    systemProperty("junit.jupiter.execution.timeout.testable.method.default", "30s")
    systemProperty("junit.jupiter.execution.timeout.lifecycle.method.default", "15s")
    // Gradle-level timeout as a backstop for the entire test task.
    timeout.set(Duration.ofMinutes(5))
    testLogging {
        events("failed")
    }
}

// Make run, packaging, and distribution tasks depend on building the native library.
tasks.matching {
    it.name == "run" || it.name == "desktopRun" || it.name == "hotRunDesktop" ||
        it.name == "createDistributable" || it.name == "packageDmg" ||
        it.name == "packageDeb" || it.name == "packageMsi"
}.configureEach {
    dependsOn(buildNativeLibrary)
}

// macOS Vulkan setup: Make libretro cores (e.g. Dolphin) that dlopen("libvulkan.dylib")
// load MoltenVK directly instead of the Vulkan loader. The Vulkan loader requires
// VK_KHR_portability_enumeration which cores like Dolphin don't enable, causing
// VK_ERROR_INCOMPATIBLE_DRIVER. MoltenVK loaded directly has no such restriction.
if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
    val icdCandidates = listOf(
        "/opt/homebrew/etc/vulkan/icd.d/MoltenVK_icd.json",  // Homebrew ARM
        "/usr/local/etc/vulkan/icd.d/MoltenVK_icd.json",     // Homebrew Intel
    )
    val icdPath = icdCandidates.firstOrNull { file(it).exists() }
    if (icdPath != null) {
        tasks.withType<JavaExec>().configureEach {
            environment("VK_ICD_FILENAMES", icdPath)
        }
    }

    // Create a shim directory where libvulkan.dylib symlinks to libMoltenVK.dylib.
    // By placing this shim dir FIRST in DYLD_FALLBACK_LIBRARY_PATH, cores that
    // dlopen("libvulkan.dylib") will find MoltenVK instead of the Vulkan loader.
    // This bypasses the loader's VK_KHR_portability_enumeration requirement.
    // (DYLD_LIBRARY_PATH won't work here — macOS SIP strips it for hardened JVMs.)
    val moltenvkCandidates = listOf(
        "/opt/homebrew/lib/libMoltenVK.dylib",  // Homebrew ARM
        "/usr/local/lib/libMoltenVK.dylib",     // Homebrew Intel
    )
    val moltenvkPath = moltenvkCandidates.firstOrNull { file(it).exists() }
    val homebrewLib = listOf("/opt/homebrew/lib", "/usr/local/lib")
        .filter { file(it).isDirectory }
        .joinToString(":")

    if (moltenvkPath != null) {
        val shimDir = layout.buildDirectory.dir("vulkan-shim").get().asFile
        shimDir.mkdirs()
        // Create symlinks for both unversioned and versioned library names.
        // Dolphin tries libvulkan.1.dylib first, then libvulkan.dylib.
        for (linkName in listOf("libvulkan.dylib", "libvulkan.1.dylib")) {
            val shimLink = File(shimDir, linkName)
            if (!shimLink.exists()) {
                Files.createSymbolicLink(
                    shimLink.toPath(),
                    Path.of(moltenvkPath)
                )
            }
        }
        // Shim dir first so libvulkan.dylib resolves to MoltenVK,
        // then Homebrew lib for other libraries cores may need.
        val fallbackPath = listOf(shimDir.absolutePath, homebrewLib)
            .filter { it.isNotEmpty() }
            .joinToString(":")
        tasks.withType<JavaExec>().configureEach {
            environment("DYLD_FALLBACK_LIBRARY_PATH", fallbackPath)
            // Dolphin checks LIBVULKAN_PATH first before trying system paths.
            environment("LIBVULKAN_PATH", moltenvkPath)
        }
    } else if (homebrewLib.isNotEmpty()) {
        tasks.withType<JavaExec>().configureEach {
            environment("DYLD_FALLBACK_LIBRARY_PATH", homebrewLib)
        }
    }
}

// Pass native library path to Compose Hot Reload tasks.
tasks.withType<JavaExec>().matching { it.name.startsWith("hotRun") || it.name.startsWith("hotDev") }.configureEach {
    jvmArgs("-Djava.library.path=${nativeBuildDir.get().asFile.absolutePath}")
}
