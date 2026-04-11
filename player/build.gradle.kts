plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.detekt)
}

// Apply detekt to every subproject except :detekt-rules itself.
// The custom Spela rule set only targets the main source modules; the rules
// module is a pure Kotlin JVM build that produces the rule JAR.
subprojects {
    if (project.name == "detekt-rules") return@subprojects

    apply(plugin = "dev.detekt")

    detekt {
        config.setFrom("$rootDir/detekt.yml")
        buildUponDefaultConfig = false
        // Default rule sets are loaded but each rule is off by default —
        // detekt.yml explicitly activates only the specific rules we want
        // (currently :spela:ComponentOuterSpacingRule + style:UnusedImport).
        disableDefaultRuleSets = false
        parallel = true
        autoCorrect = false
        // Kotlin Multiplatform puts sources under src/commonMain/kotlin, etc.
        // detekt's default source.setFrom of src/main/kotlin doesn't cover this.
        source.setFrom(
            "src/commonMain/kotlin",
            "src/androidMain/kotlin",
            "src/desktopMain/kotlin",
            "src/main/kotlin",
        )
        // Per-subproject baseline file. detekt will only fail on NEW issues
        // beyond those captured in the baseline. Baseline entries are tracked
        // in IMPROVEMENTS.md and cleaned up in follow-up PRs.
        val baselineFile = file("$projectDir/detekt-baseline.xml")
        if (baselineFile.exists()) {
            baseline = baselineFile
        }
    }

    afterEvaluate {
        dependencies.add("detektPlugins", project(":detekt-rules"))
        // Upstream detekt-rules-style: we pick individual rules from it
        // (currently only `UnusedImport`) via detekt.yml.
        dependencies.add("detektPlugins", "dev.detekt:detekt-rules-style:2.0.0-alpha.2")
    }
}
