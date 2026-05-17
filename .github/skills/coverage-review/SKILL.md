---
name: coverage-review
description: Use only before final acceptance of a major Liftorium phase or feature, or when explicitly asked for coverage critique/rubber-duck review. Checks traceability, contract, migration, and runtime evidence gaps.
---

# Coverage Review

Use this skill before accepting major Liftorium work, not for small implementation edits.

## Inputs

- Phase or feature name.
- Acceptance scenario IDs and planned test IDs from `docs/product.md`.
- Contracts touched.
- Changed implementation files.
- Changed test files.
- Test matrix for deterministic rules.
- Test commands and results.
- Runtime evidence paths, if applicable.
- Known blockers requiring user approval.

## Required checklist

Coverage review fails if any item is false:

1. Every touched acceptance scenario has implemented tests for each mapped test ID, or the user approved a traceability change.
2. Test names or metadata include mapped traceability IDs.
3. Deterministic logic has a test matrix covering equivalence classes, boundaries, invalid inputs, missing data, unsupported cases, and tie-breakers.
4. Cross-component contracts have integration tests.
5. Persistence changes have migration or transaction tests.
6. Runtime-only behavior has Android/Web E2E evidence, not only mocked tests.
7. Verification commands were run and passed.
8. Review prompts/results avoid private source excerpts.
9. Valid critique/rubber-duck gaps were fixed before pass.
10. UI deliverables include a Paparazzi snapshot test per meaningful state, the regenerated PNGs listed as runtime evidence, and confirmation that the coding agent viewed each PNG and validated visuals against the plan (per `.github/skills/android-implementation/SKILL.md` visual-review loop). Runtime-critical UI also has real-device `connectedDebugAndroidTest` evidence — Paparazzi/Robolectric do NOT substitute.

## Review passes

Run one critique pass and one rubber-duck pass using the Inputs list. Focus only on missing mapped tests, contract gaps, migration gaps, runtime/E2E evidence gaps, privacy leaks, and false confidence.

## Output

Do not commit raw review logs or prompt transcripts. Report a pass/fail decision, acceptance IDs reviewed, commands run, missing coverage if any, runtime evidence paths, and user-approved acceptance/test matrix changes in the task handoff. Persist only durable project changes: decisions in `docs/decisions.md`, acceptance/test matrix changes in `docs/product.md`, command changes in `verification-loop`, and unresolved blockers where the phase plan tracks work.

Mapped acceptance tests require a strict pass/fail decision. Missing mapped tests fail unless the user explicitly approves changing traceability.

