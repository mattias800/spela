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

val buildNativeLibrary by tasks.registering {
    group = "native"
    description = "Build the spela-libretro native library for desktop"

    val nativeDir = nativeBuildDir.get().asFile
    val nativeSrc = project.file("../native")

    inputs.dir(nativeSrc.resolve("src"))
    inputs.file(nativeSrc.resolve("CMakeLists.txt"))
    outputs.dir(nativeDir)

    doLast {
        nativeDir.mkdirs()

        // Find cmake
        val cmakePath = findCmake()
            ?: error("cmake not found. Install CMake and ensure it is on PATH.")

        val javaHome = (System.getenv("JAVA_HOME")
            ?: org.gradle.internal.jvm.Jvm.current().javaHome.absolutePath)
            .replace('\\', '/')  // CMake FindJNI chokes on Windows backslashes

        logger.lifecycle("Using cmake: $cmakePath")
        logger.lifecycle("Using JAVA_HOME: $javaHome")

        // Configure
        val configureArgs = mutableListOf(
            cmakePath,
            "-DJAVA_HOME=$javaHome",
            nativeSrc.absolutePath.replace('\\', '/'),
        )
        // Use Ninja on Windows if available (avoids heavy Visual Studio generator)
        if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
            if (findExecutable("ninja") != null) {
                configureArgs.add(1, "-G")
                configureArgs.add(2, "Ninja")
            }
        }

        exec {
            workingDir = nativeDir
            commandLine(configureArgs)
        }

        // Build
        exec {
            workingDir = nativeDir
            commandLine(cmakePath, "--build", ".", "--parallel")
        }
    }
}

fun findCmake(): String? {
    findExecutable("cmake")?.let { return it }
    // Fallback: Android SDK cmake
    val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    if (androidHome != null) {
        file("$androidHome/cmake").listFiles()?.flatMap { ver ->
            listOf(File(ver, "bin/cmake"), File(ver, "bin/cmake.exe"))
        }?.firstOrNull { it.canExecute() }?.let { return it.absolutePath }
    }
    // Fallback: common Homebrew locations
    listOf("/opt/homebrew/bin/cmake", "/usr/local/bin/cmake")
        .map { File(it) }
        .firstOrNull { it.canExecute() }
        ?.let { return it.absolutePath }
    return null
}

fun findExecutable(name: String): String? {
    val pathDirs = System.getenv("PATH")?.split(File.pathSeparator) ?: return null
    val extensions = if (org.gradle.internal.os.OperatingSystem.current().isWindows)
        listOf(".exe", ".cmd", ".bat", "") else listOf("")
    for (dir in pathDirs) {
        for (ext in extensions) {
            val candidate = File(dir, "$name$ext")
            if (candidate.canExecute()) return candidate.absolutePath
        }
    }
    return null
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
                implementation(libs.ktor.client.cio)
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

        // Use forward slashes so the path isn't mangled by escape-character
        // interpretation in the jpackage .cfg file on Windows.
        jvmArgs += "-Djava.library.path=${nativeBuildDir.get().asFile.absolutePath.replace('\\', '/')}"

        // JBR custom title bar doesn't need --add-opens; no FFM fallback.

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Spela"
            val appVersion = project.findProperty("appVersion")?.toString() ?: "1.0.0"
            // macOS DMG requires MAJOR > 0; bump 0.x.y → 1.x.y for native packaging metadata
            packageVersion = if (appVersion.startsWith("0.")) "1" + appVersion.substring(1) else appVersion

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

            // Include required Java modules for the bundled JRE.
            // java.sql is needed by SQLDelight's JDBC SQLite driver.
            modules(
                "java.base",
                "java.desktop",
                "java.logging",
                "java.prefs",
                "java.sql",           // Required for SQLDelight JDBC driver
                "jdk.unsupported"     // Required for some Compose/Skia internals
            )
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
    // Gradle-level timeout as a backstop for the entire test task. Sized
    // to the real total runtime: 98+ Compose UI test classes × ~30s each
    // at maxParallelForks = procs/2 gives ~5m on an unloaded 12-core
    // machine but can blow past 5m under dev-machine contention
    // (playwright, IDE, concurrent gradle). The per-test 30s guard and
    // per-class 120s guard still catch real waitForIdle hangs; this
    // outer cap just keeps a truly stuck Gradle daemon from lasting
    // forever.
    timeout.set(Duration.ofMinutes(15))
    testLogging {
        events("failed")
    }
}

// After jpackage creates the distributable, patch the .cfg file to replace the
// build-machine's absolute native library path with $APPDIR so the packaged app
// finds its native libs at runtime on end-user machines.
tasks.matching { it.name == "createDistributable" || it.name == "createReleaseDistributable" }.configureEach {
    doLast {
        val nativeAbsPath = nativeBuildDir.get().asFile.absolutePath.replace('\\', '/')
        val appImageDir = project.layout.buildDirectory.dir("compose/binaries/main/app").get().asFile
        appImageDir.walkTopDown()
            .filter { it.name.endsWith(".cfg") }
            .forEach { cfg ->
                val original = cfg.readText()
                val patched = original.replace(nativeAbsPath, "\$APPDIR")
                if (patched != original) {
                    cfg.writeText(patched)
                    logger.lifecycle("Patched native library path in ${cfg.name}")
                }
            }

        // Copy native library to the app's lib directory so it can be found at runtime.
        // $APPDIR resolves to lib/app/ (Linux/Windows) or Contents/app/ (macOS).
        val nativeDir = nativeBuildDir.get().asFile
        val possibleLibDirs = listOf(
            appImageDir.resolve("Spela/lib/app"),           // Linux
            appImageDir.resolve("Spela/app"),               // Windows
            appImageDir.resolve("Spela.app/Contents/app"),  // macOS
        )
        val appLibDir = possibleLibDirs.firstOrNull { it.exists() }
        if (appLibDir != null) {
            nativeDir.listFiles()?.filter { it.extension in listOf("so", "dylib", "dll") }?.forEach { lib ->
                val dest = appLibDir.resolve(lib.name)
                lib.copyTo(dest, overwrite = true)
                logger.lifecycle("Copied native library ${lib.name} to ${dest.relativeTo(appImageDir)}")
            }
        } else {
            logger.warn("Could not find app lib directory to copy native libraries")
        }
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

// Linux signal-chaining setup: libretro cores with JIT fastmem (Dolphin, PPSSPP)
// install their own SIGSEGV handlers, replacing HotSpot's. Dolphin's handler
// re-raises unrecognized faults via raise(), which strips si_addr — the JVM then
// aborts on its own (normal) safepoint/null-check faults. Preloading HotSpot's
// libjsig.so makes the core's sigaction() call register as a *chained* handler
// instead: the JVM sees every fault first with intact siginfo and forwards
// genuine fastmem faults to the core. Same mechanism as Android's libsigchain,
// which is why this only ever broke on desktop Linux (Windows VEH chains by
// design; macOS uses Mach exception ports). See player/native/CORE_HOST.md.
if (org.gradle.internal.os.OperatingSystem.current().isLinux) {
    val libjsig = org.gradle.internal.jvm.Jvm.current().javaHome.resolve("lib/libjsig.so")
    if (libjsig.exists()) {
        tasks.withType<JavaExec>().configureEach {
            environment("LD_PRELOAD", libjsig.absolutePath)
        }
    }

    // Wrap the packaged launcher the same way: rename the jpackage binary to
    // Spela-bin (it locates its .cfg by its own basename, so the cfg is
    // duplicated) and install a shell wrapper at bin/Spela that preloads the
    // bundled runtime's libjsig.so. AppImage and Flatpak launch scripts both
    // exec bin/Spela, so all Linux artifacts inherit the preload.
    tasks.matching { it.name == "createDistributable" || it.name == "createReleaseDistributable" }.configureEach {
        doLast {
            val appImageDir = project.layout.buildDirectory.dir("compose/binaries/main/app").get().asFile
            val binDir = appImageDir.resolve("Spela/bin")
            val launcher = binDir.resolve("Spela")
            val realBinary = binDir.resolve("Spela-bin")
            // Idempotent: skip if the launcher is already the shell wrapper.
            val alreadyWrapped = launcher.exists() && launcher.inputStream().use {
                val magic = ByteArray(2)
                it.read(magic) == 2 && magic[0] == '#'.code.toByte() && magic[1] == '!'.code.toByte()
            }
            if (launcher.exists() && !alreadyWrapped) {
                val cfg = appImageDir.resolve("Spela/lib/app/Spela.cfg")
                cfg.copyTo(appImageDir.resolve("Spela/lib/app/Spela-bin.cfg"), overwrite = true)
                launcher.renameTo(realBinary)
                launcher.writeText(
                    """
                    #!/bin/sh
                    # Preload HotSpot's libjsig so libretro cores' JIT-fastmem SIGSEGV
                    # handlers chain with the JVM instead of replacing its handlers.
                    # See player/native/CORE_HOST.md (Dolphin SIGSEGV on Linux).
                    HERE="${'$'}(cd "${'$'}(dirname "${'$'}0")" && pwd)"
                    export LD_PRELOAD="${'$'}HERE/../lib/runtime/lib/libjsig.so${'$'}{LD_PRELOAD:+:${'$'}LD_PRELOAD}"
                    exec "${'$'}HERE/Spela-bin" "${'$'}@"
                    """.trimIndent() + "\n"
                )
                launcher.setExecutable(true)
                logger.lifecycle("Wrapped Linux launcher with libjsig preload (bin/Spela -> bin/Spela-bin)")
            }
        }
    }
}

// Forward JVM stdout/stderr so emulation logs, native printf, and debug output
// are visible in the terminal. Without this, Gradle's forked JVM process swallows all output.
tasks.withType<JavaExec>().configureEach {
    standardOutput = System.out
    errorOutput = System.err
}

// Pass native library path to Compose Hot Reload tasks.
tasks.withType<JavaExec>().matching { it.name.startsWith("hotRun") || it.name.startsWith("hotDev") }.configureEach {
    jvmArgs("-Djava.library.path=${nativeBuildDir.get().asFile.absolutePath}")
}

// Headless netplay test runner — used by run-netplay-e2e.sh to launch two processes.
val runNetplayTestRunner by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Run the headless netplay test runner (pass args via --args)"
    dependsOn(buildNativeLibrary, "desktopJar")

    mainClass.set("com.spela.player.desktop.NetplayTestRunnerKt")
    classpath = files(
        tasks.named("desktopJar").map { it.outputs.files },
        configurations.named("desktopRuntimeClasspath"),
    )
    jvmArgs("-Djava.library.path=${nativeBuildDir.get().asFile.absolutePath}")
}

// Print the runtime classpath for the shell script to use when launching JVM directly.
tasks.register("printRuntimeClasspath") {
    group = "help"
    description = "Print the desktop runtime classpath (used by run-netplay-e2e.sh)"
    doLast {
        val desktopJar = tasks.named("desktopJar").get().outputs.files.singleFile
        val runtimeCp = configurations.named("desktopRuntimeClasspath").get().resolve()
        val allJars = listOf(desktopJar) + runtimeCp
        println(allJars.joinToString(File.pathSeparator) { it.absolutePath })
    }
}
