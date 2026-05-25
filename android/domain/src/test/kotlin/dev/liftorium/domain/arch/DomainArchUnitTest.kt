package dev.liftorium.domain.arch

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import kotlin.test.Test

/**
 * ArchUnit fitness functions for the `:domain` module.
 *
 * `:domain` is framework-free per `docs/decisions.md` 2026-05-16
 * ("Android module layout") and `docs/architecture.md` ("Planned
 * repository layout"). These rules turn that ADR into automated CI
 * enforcement.
 *
 * The companion runtime tripwire `DomainArchitectureGuardTest` (sibling
 * file) verifies the SAME invariant from a different angle: it checks
 * that forbidden classes are not even resolvable on the test classpath.
 * ArchUnit catches bytecode-level dependencies; `Class.forName` catches
 * classpath pollution. The Gradle `verifyModuleGraph` task in the root
 * build adds a third orthogonal check on the project-dependency graph.
 * All three are required per the 2026-05-20 architect audit.
 *
 * Adding a rule waiver REQUIRES an entry in [ArchitectureRulesRegistry]
 * and a paired ADR.
 */
class DomainArchUnitTest {

    private val domainClasses: JavaClasses = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
        .importPackages("dev.liftorium.domain")

    /**
     * Rule A — allowlist of packages domain code may reference.
     *
     * Domain code may depend on:
     *   * its own package tree
     *   * `:core` utilities
     *   * Kotlin stdlib and language runtime
     *   * kotlinx-coroutines (non-android)
     *   * kotlinx-serialization (used for `ProgramResource` DTOs)
     *   * `java..`/`javax..` runtime types
     *
     * Adding a new external package to the allowlist requires an ADR.
     */
    @Test
    fun `domain classes only depend on approved packages`() {
        val allowedPackages = arrayOf(
            "dev.liftorium.domain..",
            "dev.liftorium.core..",
            "kotlin..",
            "kotlinx.coroutines..",
            "kotlinx.serialization..",
            "java..",
            "javax..",
            // Kotlin compiler emits @NotNull / @Nullable JVM metadata on
            // every Kotlin field; this is intrinsic to compiling Kotlin
            // for the JVM, not a runtime dependency on JetBrains tooling.
            "org.jetbrains.annotations..",
        )
        classes()
            .that()
            .resideInAPackage("dev.liftorium.domain..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(*allowedPackages)
            .because(
                "ADR `docs/decisions.md` 2026-05-16 'Android module layout' + " +
                    "`docs/architecture.md` 'Planned repository layout': :domain is framework-free. " +
                    "New external dependencies in domain require an ADR.",
            )
            .check(domainClasses)
    }

    /**
     * Rule B — explicit denylist of platform-specific kotlinx-coroutines
     * dispatchers. Domain code must use injected `CoroutineDispatcher`s,
     * not reach for `Dispatchers.Main` (Android-specific).
     */
    @Test
    fun `domain classes do not depend on Android-specific coroutine dispatchers`() {
        noClasses()
            .that()
            .resideInAPackage("dev.liftorium.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("kotlinx.coroutines.android..")
            .because(
                "ADR `docs/decisions.md` 2026-05-16 'Android module layout': " +
                    "`Dispatchers.Main` would couple domain to the Android Looper. " +
                    "Inject dispatchers from `:core` instead.",
            )
            .check(domainClasses)
    }

    /**
     * Rule C — domain is free of internal cyclic package dependencies.
     * Cycles erode boundary clarity and force readers to load arbitrarily
     * large slices to understand any one type. The 2026-05-20 audit
     * surfaced (and the same-session refactor eliminated) a real cycle
     * between `:domain.resource` and `:domain.run`; this rule prevents
     * a regression.
     */
    @Test
    fun `domain package slices are free of cycles`() {
        slices()
            .matching("dev.liftorium.domain.(*)..")
            .should()
            .beFreeOfCycles()
            .because(
                "Cycle freedom is an evolvability invariant for the domain layer. " +
                    "Convention enforced by this test; see `docs/architecture.md` " +
                    "'Domain layer' description.",
            )
            .check(domainClasses)
    }

    /**
     * Rule C2 — `dev.liftorium.domain.common` is a SHARED-types package
     * that must remain a dependency leaf. It must not depend on any other
     * `domain.*` subpackage. Catches semantic drift where someone moves a
     * `resource`-specific type into `common` and accidentally drags a
     * `resource` reference along with it.
     */
    @Test
    fun `domain common package does not depend on other domain subpackages`() {
        noClasses()
            .that()
            .resideInAPackage("dev.liftorium.domain.common..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "dev.liftorium.domain.resource..",
                "dev.liftorium.domain.run..",
            )
            .because(
                "`dev.liftorium.domain.common` hosts cross-feature value " +
                    "types (`WeightUnit`, `ProgramVersionId`). It is intentionally " +
                    "a one-way leaf so feature packages can depend on it without " +
                    "introducing cycles. See `docs/architecture.md` 'Planned " +
                    "repository layout'.",
            )
            .check(domainClasses)
    }

    /**
     * Rule D — repository interfaces are interfaces, not abstract classes
     * or open classes. The dependency-inversion contract requires a
     * pure port; data implements the port and owns construction.
     */
    @Test
    fun `domain repository types are interfaces`() {
        classes()
            .that()
            .haveSimpleNameEndingWith("Repository")
            .and()
            .resideInAPackage("dev.liftorium.domain..")
            .should()
            .beInterfaces()
            .because(
                "ADR `docs/decisions.md` 2026-05-16 'Android module layout': " +
                    "domain owns repository ports as interfaces; data implements them.",
            )
            .check(domainClasses)
    }

    /**
     * Rule E — registry sanity. If any waivers are added without
     * touching this test, fail. Forces the registry to be reviewed
     * alongside every architecture change.
     */
    @Test
    fun `architecture rule waiver registry contains only approved exceptions`() {
        val approvedRegistry: Set<String> = emptySet()
        val current = ArchitectureRulesRegistry.exceptions.map { it.ruleId + ":" + it.classFqn }.toSet()
        check(current == approvedRegistry) {
            "ArchitectureRulesRegistry drift: expected $approvedRegistry but found $current. " +
                "If you're adding a waiver, update both the registry AND the approved set here, " +
                "and commit the paired ADR in docs/decisions.md."
        }
    }

    private fun dependOnClassFqn(fqn: String): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("depend on $fqn") {
            override fun check(item: JavaClass, events: ConditionEvents) {
                val matches = item.directDependenciesFromSelf.any { it.targetClass.fullName == fqn }
                events.add(SimpleConditionEvent(item, matches, "${item.fullName} depends on $fqn: $matches"))
            }
        }
}
