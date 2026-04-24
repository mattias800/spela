package com.spela.player.android

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.runner.Description
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * JUnit `RunListener` that snapshots everything we'd want to inspect
 * to understand WHY an assertion missed. Registered globally via the
 * `listener` instrumentation arg in `android/build.gradle.kts` so it
 * applies to every test class without per-class wiring.
 *
 * Per-failure layout under `/sdcard/spela-test-failures/<class>.<method>/`:
 *
 *   screenshot.png   — UiAutomation.takeScreenshot of display 0.
 *   ui.xml           — `uiautomator dump` of the accessibility tree.
 *   logcat.txt       — last 2000 lines of logcat from app + instrumentation.
 *   state.json       — focused window/activity, current package, display
 *                       ids, accessibility services enabled, timestamp.
 *                       Mirrors what [StartupDiagnostics] checks but at
 *                       the moment of failure rather than at start-up.
 *   failure.txt      — exception class + message + stack trace.
 *   repro.txt        — single-class gradle invocation to reproduce.
 *
 * `run-e2e.sh` `adb pull`s the whole directory to
 * `player/build/test-failures/` after the test run and prints the host
 * paths so the next iteration starts from concrete evidence rather
 * than reconstructed guesses.
 */
class FailureDiagnosticsListener : RunListener() {

    private val baseDir = "/sdcard/spela-test-failures"

    override fun testFailure(failure: Failure) {
        try {
            val description = failure.description
            val dir = File(
                baseDir,
                "${description.className}.${description.methodName}",
            )
            dir.mkdirs()

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

            captureScreenshot(device, File(dir, "screenshot.png"))
            captureUiDump(File(dir, "ui.xml"))
            captureLogcat(File(dir, "logcat.txt"))
            captureState(context, device, File(dir, "state.json"))
            captureFailure(failure.exception, File(dir, "failure.txt"))
            captureRepro(description, File(dir, "repro.txt"))
        } catch (capture: Throwable) {
            // Last-resort: don't let a diagnostics bug mask the real
            // failure. The original throwable is what JUnit reports;
            // we just leave a logcat trail.
            android.util.Log.e(
                "FailureDiagnostics",
                "Diagnostics capture failed for ${failure.description.displayName}",
                capture,
            )
        }
    }

    private fun captureScreenshot(device: UiDevice, out: File) {
        if (!device.takeScreenshot(out)) {
            try {
                val bitmap: Bitmap? = InstrumentationRegistry
                    .getInstrumentation()
                    .uiAutomation
                    ?.takeScreenshot()
                if (bitmap != null) {
                    FileOutputStream(out).use { fos ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                }
            } catch (_: Throwable) { /* give up */ }
        }
    }

    private fun captureUiDump(out: File) {
        val tmp = File("/sdcard/__diag_dump.xml")
        try {
            ProcessBuilder("uiautomator", "dump", tmp.absolutePath)
                .redirectErrorStream(true)
                .start()
                .waitFor()
        } catch (_: Throwable) { /* tool may be absent on some images */ }
        if (tmp.exists()) {
            tmp.copyTo(out, overwrite = true)
            tmp.delete()
        }
    }

    private fun captureLogcat(out: File) {
        try {
            val proc = ProcessBuilder("logcat", "-d", "-t", "2000")
                .redirectErrorStream(true)
                .start()
            proc.inputStream.copyTo(FileOutputStream(out))
            proc.waitFor()
        } catch (e: Throwable) {
            out.writeText("logcat capture failed: ${e.message}\n")
        }
    }

    private fun captureState(
        context: Context,
        device: UiDevice,
        out: File,
    ) {
        val sb = StringBuilder("{\n")
        fun line(k: String, v: String) {
            sb.append("  \"").append(k).append("\": \"").append(v.escape()).append("\",\n")
        }
        val ts = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date())
        line("timestamp", ts)
        line("deviceModel", Build.MODEL)
        line("androidVersion", Build.VERSION.RELEASE)
        line("currentPackage", device.currentPackageName ?: "<null>")
        line(
            "enabledA11yServices",
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty(),
        )
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        line(
            "displaysOn",
            dm.displays.filter { it.state == android.view.Display.STATE_ON }
                .joinToString(",") { it.displayId.toString() },
        )
        if (sb.endsWith(",\n")) sb.setLength(sb.length - 2)
        sb.append("\n}\n")
        out.writeText(sb.toString())
    }

    private fun captureFailure(e: Throwable, out: File) {
        out.bufferedWriter().use { w ->
            w.write(e::class.qualifiedName ?: "Throwable")
            w.write(": ")
            w.write(e.message ?: "")
            w.write("\n\n")
            e.stackTrace.forEach {
                w.write("  at $it\n")
            }
            var cause: Throwable? = e.cause
            while (cause != null) {
                w.write("\nCaused by ")
                w.write(cause::class.qualifiedName ?: "Throwable")
                w.write(": ")
                w.write(cause.message ?: "")
                w.write("\n")
                cause.stackTrace.forEach {
                    w.write("  at $it\n")
                }
                cause = cause.cause
            }
        }
    }

    private fun captureRepro(description: Description, out: File) {
        val cls = description.className
        val method = description.methodName
        out.writeText(
            buildString {
                appendLine("# Reproduce just this failure:")
                appendLine("ANDROID_SERIAL=\$ADB_SERIAL ./gradlew :android:connectedDebugAndroidTest \\")
                appendLine("  -Pandroid.testInstrumentationRunnerArguments.class=$cls#$method")
                appendLine()
                appendLine("# Or with the full test run setup (a11y services, port forwarding):")
                appendLine("./run-e2e.sh $cls#$method")
            },
        )
    }

    private fun String.escape(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
