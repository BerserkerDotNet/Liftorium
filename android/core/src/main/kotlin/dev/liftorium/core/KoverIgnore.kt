package dev.liftorium.core

/**
 * Marks a class as exempt from Kover code-coverage measurement.
 *
 * The 95% coverage gate applies to behavior-bearing domain and data
 * code. Per the Exclusion Contract in `docs/decisions.md`
 * ("Coverage gate at 95% on all available metrics …"), the following
 * are legitimate exclusions:
 *
 * * Kotlin `data class` DTOs whose members are all compiler-generated
 *   (equals/hashCode/toString/copy/componentN) — no hand-written
 *   behavior to test.
 * * `sealed class` result hierarchies whose subtypes are themselves
 *   `data class` cases — covered indirectly by tests that assert on
 *   the result type.
 * * Room `@Entity` data classes — exercised through the DAO; the
 *   property accessors are compiler-generated.
 * * Behaviorless value classes / wrapper records.
 *
 * Apply this annotation surgically. Anything with a hand-written
 * `init` block, a non-trivial computed property, or a function body
 * that contains real control flow is NOT exempt — it must be tested
 * directly.
 *
 * The Kover `excludes { annotatedBy(...) }` rule in
 * `android/build.gradle.kts` reads this fully-qualified name; do not
 * rename without updating that rule.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
public annotation class KoverIgnore
