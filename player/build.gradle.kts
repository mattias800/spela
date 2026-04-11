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
        // Do NOT set source.setFrom(...) globally. In detekt 2.0 this flat source
        // list bleeds into the per-compilation type-resolution tasks (e.g.
        // `detektMainDesktop`), making them see both commonMain and
        // desktopMain/androidMain as a single merged compilation — which then
        // trips "expect and corresponding actual are declared in the same module"
        // errors from detekt's internal Kotlin compiler. The auto-generated
        // per-source-set tasks (`detektCommonMainSourceSet` etc.) and per-
        // compilation tasks (`detektMainDesktop` etc.) already discover sources
        // from the KMP model.
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

    // Force KMP-aware compilation on every detekt task. Without this,
    // detekt's internal Kotlin compiler treats commonMain + platform
    // source sets as a single flat module and fails with
    // "expect and corresponding actual are declared in the same module"
    // errors — which in turn silently disables every type-resolution rule
    // (UnusedPrivateMember, CanBeNonNullable, etc.). The per-compilation
    // tasks don't auto-set this for KMP in detekt 2.0.0-alpha.2.
    tasks.withType(dev.detekt.gradle.Detekt::class.java).configureEach {
        multiPlatformEnabled.set(true)
    }
}
