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
                    "**Application**",
                    "**Activity**",
                    // Compose entry/preview functions are emitted into
                    // synthetic <File>Kt classes. The blanket "**ComposeKt"
                    // exclusion would also catch hand-written non-Compose
                    // logic, so we limit it to known UI entry files (none
                    // in :core/:data/:domain today; pattern kept for the
                    // future when MainActivityKt-style entries appear).
                    "**ComposeKt**",
                    // Hilt / Dagger generated classes
                    "**Hilt_**",
                    "**_Factory**",
                    "**_HiltModules**",
                    "**_MembersInjector**",
                    // Kotlinx serialization generated classes. `$Companion`
                    // and `$$serializer` are emitted alongside every
                    // @Serializable type and carry compiler-generated
                    // dispatch with no hand-written behavior to test.
                    // `$WhenMappings` is emitted for `when` over enums.
                    // Note: this also excludes hand-written `companion
                    // object` factory wrappers (e.g. TimeSource.system()).
                    // That's an acceptable trade — factory wrappers are
                    // structural and the underlying implementation
                    // (JvmTimeSource) carries the real behavior coverage.
                    "**\$\$serializer**",
                    "**\$Companion**",
                    "**\$WhenMappings**",
                    // Room generated DAOs/databases. Kover wildcards in
                    // 0.8.3 are inconsistent — `**_Impl**` matches
                    // `LiftoriumDatabase_Impl` but does NOT match
                    // `LoadedProgramVersionDao_Impl`, even though both
                    // FQNs contain `_Impl` as a substring. To reproduce:
                    // remove the explicit `**Dao_Impl` / `**Database_Impl`
                    // lines below and run `:data:koverVerifyDebug`; the
                    // generated `LoadedProgramVersionDao_Impl` class will
                    // re-appear in coverage with ~0% covered lines from
                    // its synthesized `Callable` inner classes.
                    //
                    // The pair `**Dao_Impl` + `**Dao_Impl$*` is not
                    // asymmetric: the first matches the outer generated
                    // class (FQN ends with `Dao_Impl`), the second
                    // matches its nested `Callable` classes (FQN ends
                    // with `Dao_Impl$<anything>`). Same for
                    // `**Database_Impl` + `**Database_Impl$*`. Both
                    // outer-and-nested pairs are required because kover
                    // does not treat `$` as a path wildcard.
                    "**_Impl**",
                    "**Dao_Impl",
                    "**Dao_Impl\$*",
                    "**Database_Impl",
                    "**Database_Impl\$*",
                    // Compose previews
                    "**Preview**",
                    "**ComposableSingletons**",
                )
                // Behaviorless-model classes (data/value/sealed-result/DTO/
                // Room entities) opt out via the repo-local @KoverIgnore
                // annotation declared in :core. The annotation policy is
                // documented in dev.liftorium.core.KoverIgnore and the
                // Exclusion Contract in docs/decisions.md.
                annotatedBy("dev.liftorium.core.KoverIgnore")
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

// ---------------------------------------------------------------------------
// Architecture fitness function: module dependency graph
// ---------------------------------------------------------------------------
//
// Asserts that the Gradle project-dependency graph matches the architecture's
// allowed module edges. This is intentionally ORTHOGONAL to the ArchUnit
// bytecode rules in each module's test source: ArchUnit catches forbidden
// references (does any class USE a forbidden type?), while this task catches
// forbidden classpath presence (can any class even SEE a forbidden module?).
//
// Per ADR `docs/decisions.md` 2026-05-16 "Android module layout" and
// `docs/architecture.md` ("Planned repository layout" + module map):
//
//   :app    -> :domain, :data, :core
//   :data   -> :domain, :core
//   :domain -> :core
//   :core   -> none
//
// Adding a new module or new edge requires updating the map below AND
// landing a corresponding ADR. The map is the single source of truth for
// allowed cross-module dependencies in non-test configurations.
//
// Test configurations (testImplementation, androidTestImplementation,
// kover, kapt-for-tests, etc.) are exempt because test code legitimately
// reaches across modules to consume test fixtures, kover instruments
// everything, and the Room migration test consumes data schema assets.
val allowedModuleEdges: Map<String, Set<String>> = mapOf(
    ":app" to setOf(":domain", ":data", ":core"),
    ":data" to setOf(":domain", ":core"),
    ":domain" to setOf(":core"),
    ":core" to emptySet(),
)

val moduleGraphProductionConfigurationSuffixes: Set<String> = setOf(
    "Api",
    "Implementation",
    "CompileOnly",
    "RuntimeOnly",
    "Kapt",
    "Ksp",
)

val moduleGraphProductionExactNames: Set<String> = setOf(
    "api",
    "implementation",
    "compileOnly",
    "runtimeOnly",
    "kapt",
    "ksp",
)

// A configuration is treated as "production" (its ProjectDependency entries
// affect the published architecture) iff its name does NOT identify it as a
// test/kover/lint/etc. and its name corresponds to a base Gradle/AGP
// dependency configuration or a variant-suffixed flavor of one
// (e.g. `releaseImplementation`, `debugApi`, `flavorDebugCompileOnly`).
//
// Per the 2026-05-20 arch-fitness audit: a previous version of this function
// classified by PREFIX only (e.g. `name.startsWith("implementation")`) which
// missed every Android variant configuration like `releaseImplementation` and
// `debugApi`. Forbidden edges could be hidden in variant configs and slip
// past `verifyModuleGraph`. Suffix classification closes that gap.
fun configurationIsProduction(name: String): Boolean {
    val lower = name.lowercase()
    // Exclude anything obviously test/quality-tooling-related, including
    // variant-prefixed test configurations like `testReleaseImplementation`
    // and `androidTestDebugApi`.
    if (lower.startsWith("test") || lower.startsWith("androidtest")) return false
    if (lower.startsWith("kover")) return false
    if (lower.startsWith("lint")) return false
    if (lower.contains("annotationprocessor")) return false
    if ("test" in lower && (lower.contains("implementation") || lower.contains("api") || lower.contains("compileonly") || lower.contains("runtimeonly") || lower.contains("kapt") || lower.contains("ksp"))) return false

    if (name in moduleGraphProductionExactNames) return true
    return moduleGraphProductionConfigurationSuffixes.any { suffix ->
        name.endsWith(suffix) && name.length > suffix.length && name[0].isLowerCase()
    }
}

// `ModuleEdge` is a plain serializable data carrier so the task can be
// stored in Gradle's configuration cache: the project graph is walked
// once at configuration time (via `gradle.projectsEvaluated`) and the
// resulting list of edges is captured into the task body as inert data.
data class ModuleEdge(val from: String, val configuration: String, val to: String) : java.io.Serializable

val collectedModuleEdges: MutableList<ModuleEdge> = mutableListOf()
val collectedKnownModules: MutableSet<String> = mutableSetOf()
gradle.projectsEvaluated {
    rootProject.subprojects.forEach { sub ->
        collectedKnownModules += sub.path
        sub.configurations.forEach config@{ config ->
            if (!configurationIsProduction(config.name)) return@config
            config.dependencies.withType(ProjectDependency::class.java).forEach { dep ->
                collectedModuleEdges += ModuleEdge(
                    from = sub.path,
                    configuration = config.name,
                    to = dep.dependencyProject.path,
                )
            }
        }
    }
}

tasks.register("verifyModuleGraph") {
    group = "verification"
    description = "Architecture fitness function: asserts the project-dependency graph matches the ADR-approved adjacency map."
    val capturedAllowed: Map<String, Set<String>> = allowedModuleEdges
    val capturedKnownAllowedModules: Set<String> = allowedModuleEdges.keys
    val capturedEdges: List<ModuleEdge> = collectedModuleEdges
    val capturedSeenModules: Set<String> = collectedKnownModules
    doLast {
        val violations = mutableListOf<String>()
        (capturedSeenModules + capturedKnownAllowedModules).forEach { modulePath ->
            if (modulePath !in capturedKnownAllowedModules) {
                violations += "Module $modulePath is not listed in allowedModuleEdges. Update android/build.gradle.kts and add an ADR before introducing a new module."
            }
        }
        capturedEdges.forEach { edge ->
            val allowed = capturedAllowed[edge.from] ?: return@forEach
            if (edge.to !in allowed) {
                violations += "Forbidden edge: ${edge.from} (configuration '${edge.configuration}') -> ${edge.to}. Allowed edges from ${edge.from}: $allowed. See ADR docs/decisions.md 'Android module layout'."
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Module-graph fitness function failed (${violations.size} violation${if (violations.size == 1) "" else "s"}):")
                    violations.distinct().forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("Architecture rule: docs/architecture.md 'Planned repository layout' and docs/decisions.md 'Android module layout'.")
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Architecture fitness function: :app UI / :data boundary (source-level guard)
// ---------------------------------------------------------------------------
//
// The intended invariant — `dev.liftorium.app.ui..` may NOT depend on
// `dev.liftorium.data..` — cannot be enforced by ArchUnit inside `:app`
// because the Android Gradle Plugin's ASM transform on the unit-test
// classpath crashes on the archunit JAR's shaded thirdparty layout
// ("duplicate entry: com/tngtech/archunit/thirdparty/"). It also cannot
// be enforced by `verifyModuleGraph`: the `:app -> :data` edge MUST exist
// at the module level so DI bootstrap can construct Room repositories.
//
// We instead enforce the rule at the SOURCE level by scanning every Kotlin
// or Java file under `app/src/<sourceSet>/{java,kotlin}/dev/liftorium/app/ui`
// for any production source set (main, debug, release, flavor, flavor-build-
// type). Test source sets (`test`, `androidTest`, `sharedTest`) are excluded
// because they may legitimately reference data internals for integration
// verification. The check looks at `import` statements and any fully-qualified
// usage. Bootstrap/DI code in the non-UI packages of `:app` is intentionally
// outside the scan scope (DI is the legitimate place to wire :data).
//
// Per ADR `docs/decisions.md` 2026-05-16 "Android module layout" and
// `docs/architecture.md`: UI talks to domain repository interfaces. The
// concrete Room implementations live in `:data` and are wired in by
// non-UI bootstrap code only.
val appSrcDir: File = rootProject.file("app/src")

fun isProductionSourceSetDir(name: String): Boolean {
    val lower = name.lowercase()
    return !lower.contains("test")
}

val appUiSourceRoots: List<File> = run {
    if (!appSrcDir.isDirectory) return@run emptyList()
    appSrcDir.listFiles { f -> f.isDirectory && isProductionSourceSetDir(f.name) }
        ?.flatMap { sourceSetDir ->
            listOf("java", "kotlin").map { lang ->
                File(sourceSetDir, "$lang/dev/liftorium/app/ui")
            }
        }
        ?.filter { it.isDirectory }
        ?: emptyList()
}

tasks.register("verifyAppUiBoundary") {
    group = "verification"
    description = "Architecture fitness function: asserts no Compose UI source file (any production source set) references `dev.liftorium.data..`."
    val forbiddenPattern = Regex("""\bdev\.liftorium\.data(?:\.[A-Za-z0-9_]+)*\b""")
    val sourceFiles: List<File> = appUiSourceRoots.flatMap { root ->
        root.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .toList()
    }
    val rootDir: File = rootProject.projectDir
    inputs.files(sourceFiles).withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    inputs.property("forbiddenPattern", forbiddenPattern.pattern)
    doLast {
        val violations = mutableListOf<String>()
        sourceFiles.forEach { file ->
            file.useLines { lines ->
                lines.forEachIndexed { idx, raw ->
                    val line = raw.trim()
                    if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) return@forEachIndexed
                    forbiddenPattern.find(line)?.let { match ->
                        violations += "${file.relativeTo(rootDir).path}:${idx + 1}: references `${match.value}` (UI may not depend on :data)"
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(":app UI/data boundary violation (${violations.size} occurrence${if (violations.size == 1) "" else "s"}):")
                    violations.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("Architecture rule: dev.liftorium.app.ui.. may not depend on dev.liftorium.data.. .")
                    appendLine("Move the persistence call to a domain repository interface and have :app's DI/bootstrap code wire the Room implementation.")
                    appendLine("See docs/decisions.md 2026-05-16 'Android module layout' and docs/architecture.md.")
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Architecture fitness function: Room locality (source-level guard)
// ---------------------------------------------------------------------------
//
// Per the arch-fitness audit: ArchUnit Rules F/G in `:data` only see the
// `:data` test compile classpath. They cannot catch a stray `androidx.room`
// annotation in `:app` or another module that adds Room as an external
// dependency. This source-level guard scans EVERY production Kotlin/Java
// source file in the repository outside `android/data/src/main` for
// `androidx.room.Entity`, `androidx.room.Dao`, or `androidx.room.Database`
// references. Bench tests, migration tests, and the `:data` main source set
// are exempt.
//
// Per ADR `docs/decisions.md` 2026-05-16: persistence types stay in `:data`.
val roomLocalityScanRoots: List<File> = run {
    val androidRoot = rootProject.projectDir
    val candidateModules = listOf("app", "domain", "core")
    candidateModules.flatMap { module ->
        val moduleSrc = File(androidRoot, "$module/src")
        if (!moduleSrc.isDirectory) return@flatMap emptyList<File>()
        moduleSrc.listFiles { f -> f.isDirectory && isProductionSourceSetDir(f.name) }
            ?.toList()
            ?: emptyList()
    }
}

tasks.register("verifyRoomLocality") {
    group = "verification"
    description = "Architecture fitness function: asserts androidx.room annotations only appear in :data."
    val forbiddenPattern = Regex("""\bandroidx\.room\.(Entity|Dao|Database|TypeConverter|TypeConverters|RoomDatabase)\b""")
    val sourceFiles: List<File> = roomLocalityScanRoots.flatMap { root ->
        root.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .toList()
    }
    val rootDir: File = rootProject.projectDir
    inputs.files(sourceFiles).withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    inputs.property("forbiddenPattern", forbiddenPattern.pattern)
    doLast {
        val violations = mutableListOf<String>()
        sourceFiles.forEach { file ->
            file.useLines { lines ->
                lines.forEachIndexed { idx, raw ->
                    val line = raw.trim()
                    if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) return@forEachIndexed
                    forbiddenPattern.find(line)?.let { match ->
                        violations += "${file.relativeTo(rootDir).path}:${idx + 1}: references `${match.value}` (Room may only appear in :data)"
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Room locality violation (${violations.size} occurrence${if (violations.size == 1) "" else "s"}):")
                    violations.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("Architecture rule: androidx.room.* annotations must reside in :data.")
                    appendLine("See docs/decisions.md 2026-05-16 'Android module layout' and docs/architecture.md.")
                },
            )
        }
    }
}

// Wire the fitness functions into the standard `check` lifecycle so CI fails
// the build if the module graph drifts. Each module's `check` task gets a
// dependency on the root-level architecture fitness tasks.
subprojects {
    afterEvaluate {
        tasks.findByName("check")?.let { check ->
            check.dependsOn(rootProject.tasks.named("verifyModuleGraph"))
            check.dependsOn(rootProject.tasks.named("verifyAppUiBoundary"))
            check.dependsOn(rootProject.tasks.named("verifyRoomLocality"))
        }
    }
}