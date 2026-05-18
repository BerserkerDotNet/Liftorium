// Root build file. Plugin versions are pinned in gradle/libs.versions.toml.
// Modules apply plugins explicitly; declaring them here with apply false
// keeps a single version source of truth across all modules.
//
// Kover is the EXCEPTION: it MUST be applied to the root project (not
// `apply false`) so the aggregation tasks (`koverHtmlReport`, `koverXmlReport`,
// `koverVerify`) are created here and pull merged report data from the modules
// listed under `dependencies { kover(...) }`. See docs/decisions.md
// (2026-05-: Coverage gate at 95%).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.paparazzi) apply false
    alias(libs.plugins.kover)
}

// Aggregate Kover reports from the modules whose code is subject to the
// 95% coverage gate (domain + data only). The :app module is intentionally
// excluded — UI / framework glue / Compose previews / Paparazzi snapshots
// live there and would otherwise dilute the gate. See the Exclusion Contract
// in docs/decisions.md.
dependencies {
    kover(project(":core"))
    kover(project(":data"))
    kover(project(":domain"))
}

kover {
    reports {
        filters {
            excludes {
                // Framework glue and generated code. Counter mapping is
                // platform-native, not semantically equivalent to vitest —
                // see .github/skills/test-design/SKILL.md Android appendix.
                classes(
                    // Android entry points
                    "*.*Application",
                    "*.*Activity",
                    // Compose entry/preview functions are emitted into
                    // synthetic <File>Kt classes. The blanket "*ComposeKt"
                    // exclusion would also catch hand-written non-Compose
                    // logic, so we limit it to known UI entry files (none
                    // in :core/:data/:domain today; pattern kept for the
                    // future when MainActivityKt-style entries appear).
                    "*.*ComposeKt",
                    // Hilt / Dagger generated classes
                    "*.Hilt_*",
                    "*.*_Factory",
                    "*.*_HiltModules*",
                    "*.*_MembersInjector",
                    // Kotlinx serialization generated companions
                    "*.*$\$serializer",
                    // Room generated DAOs/databases
                    "*.*_Impl",
                    "*.*Database_Impl",
                    // Compose previews
                    "*.*Preview*",
                    "*.ComposableSingletons*",
                )
                // Behaviorless-model classes (data/value/sealed-result/DTO/
                // Room entities) should be excluded once they exist. The
                // Phase 1 codebase has none. Phase 4+ will add either a
                // class-name pattern (e.g. "*.model.*") or a repo-local
                // `@KoverIgnore` annotation declared in :core and referenced
                // here via `annotatedBy("dev.liftorium.core.KoverIgnore")`.
                // See the Exclusion Contract in docs/decisions.md.
            }
        }
        verify {
            // Per-counter rules. Kover 0.8.3 supports three counters:
            //   LINE        — source lines (≈ vitest "lines")
            //   BRANCH      — control-flow branches (≈ vitest "branches")
            //   INSTRUCTION — JVM bytecode instructions (no vitest equivalent;
            //                 functionally catches uncovered methods because
            //                 a method with no test invocations contributes
            //                 ALL its instructions to the missed pool)
            //
            // Kover has NO METHOD counter, so vitest's "functions" metric is
            // approximated by INSTRUCTION. This divergence is documented in
            // the test-design skill Android appendix and docs/decisions.md
            // (Exclusion Contract / Counter mapping).
            rule("Liftorium domain+data line coverage >= 95%") {
                bound {
                    minValue = 95
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                    aggregationForGroup = kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
                }
            }
            rule("Liftorium domain+data branch coverage >= 95%") {
                bound {
                    minValue = 95
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH
                    aggregationForGroup = kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
                }
            }
            rule("Liftorium domain+data instruction coverage >= 95%") {
                bound {
                    minValue = 95
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.INSTRUCTION
                    aggregationForGroup = kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
                }
            }
        }
    }
}
