package dev.liftorium.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Mechanical architecture guard for the framework-free `:domain` module.
 *
 * The architecture rule in docs/architecture.md is:
 *   "Domain code must not import Room entities, DAOs, Android Context, or
 *    platform services directly."
 *
 * `:domain` is configured as a pure `org.jetbrains.kotlin.jvm` Gradle module,
 * so Android, Room, and Compose dependencies are not declared. This test
 * makes that guarantee testable: if a future change accidentally pulls one of
 * the forbidden libraries onto the test classpath (because of a transitive
 * dependency or a misclassified module), the corresponding type becomes
 * resolvable via `Class.forName` and the test fails loudly.
 *
 * If a later workstream needs one of these capabilities inside the domain
 * layer, the correct response is to revisit the architecture decision in
 * docs/decisions.md — not to weaken this test.
 */
class DomainArchitectureGuardTest {

    private val forbiddenClasses = listOf(
        // Android framework
        "android.content.Context",
        "android.app.Activity",
        "android.app.Application",
        "android.os.Bundle",
        // AndroidX runtime
        "androidx.lifecycle.ViewModel",
        // Room (database driver and entities)
        "androidx.room.Room",
        "androidx.room.RoomDatabase",
        "androidx.room.Entity",
        // Compose runtime / UI
        "androidx.compose.runtime.Composable",
        "androidx.compose.ui.Modifier",
        "androidx.compose.material3.MaterialTheme",
    )

    @Test
    fun `domain classpath does not contain Android, Room, or Compose runtime types`() {
        val leaked = forbiddenClasses.filter { fqn ->
            try {
                Class.forName(fqn, /* initialize = */ false, javaClass.classLoader)
                true
            } catch (_: ClassNotFoundException) {
                false
            } catch (_: NoClassDefFoundError) {
                false
            }
        }

        if (leaked.isNotEmpty()) {
            fail(
                buildString {
                    appendLine("Forbidden framework classes were resolvable from the :domain test classpath.")
                    appendLine("This means :domain has picked up an Android, Room, or Compose dependency,")
                    appendLine("which violates docs/architecture.md and docs/decisions.md (2026-05-16: Phase 1 Android module layout).")
                    appendLine("Leaked classes:")
                    leaked.forEach { appendLine("  - $it") }
                },
            )
        }
    }

    @Test
    fun `domain module exposes its own kotlin stdlib`() {
        // Sanity check that the test infrastructure itself is working: the
        // architecture guard above only catches false negatives if the
        // classpath is being inspected for real.
        assertEquals("kotlin.String", String::class.qualifiedName)
    }
}
