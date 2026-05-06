package com.spela.player.android

/**
 * Marks a test class as needing a physical Android device (e.g., AYN
 * Thor) — typically because it exercises libretro emulation, gamepad
 * input, or save-state-bound flows that don't behave reliably on the
 * `sdk_gphone64_arm64` AVD used in CI.
 *
 * `BaseE2ETest.baseSetUp()` checks this annotation and calls
 * `Assume.assumeFalse(...)` on the emulator, marking the test SKIPPED
 * rather than failing. Annotated tests still run normally on a physical
 * device.
 *
 * Use sparingly. The CI gate is most valuable when it covers the most
 * code; tag a test only when it's been demonstrated to be unreliable on
 * the AVD AND the bug is in test infra / emulator interaction, not real
 * product code.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresPhysicalDevice(val reason: String)
