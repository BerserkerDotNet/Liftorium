package dev.liftorium.data.arch

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import org.junit.Test

/**
 * ArchUnit fitness functions for the `:data` module.
 *
 * Per `docs/architecture.md` "Planned repository layout" and
 * `docs/decisions.md` 2026-05-16 ("Android module layout"), `:data` owns
 * Room persistence and resource I/O. It MUST NOT host UI surfaces, must
 * not depend on `:app`, and its Room entities should stay locally
 * discoverable.
 *
 * These rules are paired with the Gradle `verifyModuleGraph` task (which
 * forbids `:data -> :app` at the module dep level) and the `:domain`
 * ArchUnit rules (which forbid `:domain -> :data`). Together they form a
 * three-layer defence: classpath, bytecode usage, and source intent.
 */
class DataArchitectureTest {

    private val dataClasses: JavaClasses = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        // We intentionally DO include JARs here because Android library
        // unit tests resolve the module's own classes via the
        // `bundleLibCompileToJarDebug` JAR rather than the kotlin-classes
        // directory. Excluding JARs would make the @Entity and @Dao
        // population checks silently empty. We filter at the rule level
        // (resideInAPackage("dev.liftorium..")) to keep external deps out.
        .importPackages("dev.liftorium")

    /**
     * Rule E1 — `:data` may not depend on UI frameworks. Compose, Activity,
     * Fragment, Navigation, ViewModel/LiveData, raw Android views/widgets
     * all belong in `:app`. `android.content.Context` IS allowed because
     * Room legitimately needs it for database construction.
     *
     * Per the 2026-05-20 arch-fitness audit: `androidx.lifecycle..` is
     * banned wholesale (not just `androidx.lifecycle.viewmodel..`) because
     * `ViewModel`, `LiveData`, `LifecycleOwner` etc. live directly under
     * `androidx.lifecycle` and would otherwise leak into `:data`.
     */
    @Test
    fun `data module does not depend on UI frameworks`() {
        val forbiddenUiPackages = arrayOf(
            "androidx.compose..",
            "androidx.fragment..",
            "androidx.navigation..",
            "androidx.activity..",
            "androidx.lifecycle..",
            "android.view..",
            "android.widget..",
            "android.app..",
        )
        noClasses()
            .that()
            .resideInAPackage("dev.liftorium.data..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*forbiddenUiPackages)
            .because(
                "ADR `docs/decisions.md` 2026-05-16 'Android module layout' + " +
                    "`docs/architecture.md` 'Planned repository layout': " +
                    "`:data` is a headless persistence/IO layer. UI types and " +
                    "Android lifecycle types belong in `:app`.",
            )
            .check(dataClasses)
    }

    /**
     * Rule E2 — `:data` must not depend on `:app`. The app module wires
     * Compose UI and DI; data should be replaceable without touching app.
     */
    @Test
    fun `data module does not depend on app module`() {
        noClasses()
            .that()
            .resideInAPackage("dev.liftorium.data..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("dev.liftorium.app..")
            .because(
                "ADR `docs/decisions.md` 2026-05-16 'Android module layout': " +
                    "data is downstream of domain and independent of app. App " +
                    "depends on data; the reverse edge would couple persistence " +
                    "to Compose/DI wiring and is rejected by the same rule that " +
                    "`verifyModuleGraph` enforces at the Gradle level.",
            )
            .check(dataClasses)
    }

    /**
     * Rule F — Room `@Entity` types may only live under `dev.liftorium.data..`.
     *
     * SCOPE NOTE: this rule sees the `:data` test compile classpath, which
     * includes `:data`, `:domain`, `:core`, and external libraries. It does
     * NOT see `:app` sources, so an accidental `@Entity` in `:app` would be
     * caught by `verifyAppUiBoundary` and `verifyModuleGraph`-level
     * declarations rather than this rule. Within the modules this rule
     * can see, Room entities are forbidden outside `:data`.
     */
    @Test
    fun `room entity classes reside in data module`() {
        classes()
            .that()
            .areAnnotatedWith("androidx.room.Entity")
            .should()
            .resideInAPackage("dev.liftorium.data..")
            .because(
                "ADR `docs/decisions.md` 2026-05-16 'Android module layout': " +
                    "persistence types stay in `:data`. Leaking `@Entity` types " +
                    "into domain or app would couple business rules to Room.",
            )
            .check(dataClasses)
    }

    @Test
    fun `room DAO interfaces reside in data module`() {
        classes()
            .that()
            .areAnnotatedWith("androidx.room.Dao")
            .should()
            .resideInAPackage("dev.liftorium.data..")
            .because(
                "ADR `docs/decisions.md` 2026-05-16 'Android module layout': " +
                    "DAO types are Room infrastructure; only `:data` may reference them.",
            )
            .check(dataClasses)
    }

    @Test
    fun `data package slices are free of cycles`() {
        slices()
            .matching("dev.liftorium.data.(*)..")
            .should()
            .beFreeOfCycles()
            .because(
                "Cycle freedom inside `:data` prevents tangled persistence " +
                    "concerns. Fitness invariant of the data module layout " +
                    "(no ADR; convention enforced by this test).",
            )
            .check(dataClasses)
    }
}
