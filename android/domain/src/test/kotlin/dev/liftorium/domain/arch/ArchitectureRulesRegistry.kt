package dev.liftorium.domain.arch

/**
 * Centralized exception registry for ArchUnit fitness functions.
 *
 * Per the architect's audit (2026-05-20 `arch-fitness-architect` rubber-duck
 * pass), legitimate architecture rule waivers MUST live in a single,
 * reviewable location rather than as `@Suppress` annotations scattered
 * through production code. An exception is a structured statement that:
 *
 *   * a named [ruleId] is being waived for a specific class or package,
 *   * the waiver is justified by an [adr] entry in `docs/decisions.md`,
 *   * the [reason] explains the architectural trade,
 *   * an optional [expires] field flags the waiver for future re-review.
 *
 * Adding an entry here REQUIRES a paired ADR commit. Removing a waiver
 * removes the bypass; the rule will then run unmodified against the
 * affected class.
 *
 * As of Phase 4 close-out, the registry is INTENTIONALLY EMPTY. The
 * architecture rules are all enforceable without exceptions. Keeping the
 * mechanism in place (and the empty list visible in tests) means a future
 * legitimate waiver does not require redesigning the test infrastructure.
 */
public data class ArchitectureRuleException(
    val ruleId: String,
    val classFqn: String,
    val adr: String,
    val reason: String,
    val expires: String? = null,
)

public object ArchitectureRulesRegistry {
    /** All currently-active rule waivers. Adding entries requires an ADR. */
    public val exceptions: List<ArchitectureRuleException> = emptyList()
}
