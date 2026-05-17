# Testing Strategy

This file only records testing policy that is not already captured in `.github/copilot-instructions.md`, `docs/product.md`, or the verification-loop skill.

## Command registry

The canonical verification command registry lives in `.github/skills/verification-loop/SKILL.md`.

Only register commands that exercise implementation outputs: code, schemas, generated resources, builds, tests, or runtime behavior. Do not use file-presence or documentation-shape scripts as completion gates.

No MVP implementation code exists yet, so no code-output verification command is currently registered. When Android, Web, schema, or import tooling is created, add the exact runnable commands to the verification-loop skill before completing related feature work.

## Required evidence by test type

- Unit tests: deterministic rules, invalid inputs, missing data, unsupported cases, boundaries, and tie-breakers.
- Integration tests: cross-component contracts, persistence transactions, resource loading, program-version pinning, and mutation side effects.
- Migration tests: every Room migration path from the first exported schema.
- Runtime/E2E tests: Android lifecycle, process death, permissions, locked-phone timer alerts, real UI behavior, and browser-only behavior.
- Import/resource tests: synthetic fixtures by default; private spreadsheet-derived fixtures only when approved, with source excerpts excluded from output.

## Coverage review gate

Before accepting each major phase, run relevant verification, ask critique/rubber-duck reviewers to challenge missing coverage and false confidence, then fix valid gaps.

Do not commit raw review logs or prompt transcripts. Capture durable outcomes only where they belong: decisions in `docs/decisions.md`, acceptance/test matrix changes in `docs/product.md`, command changes in `.github/skills/verification-loop/SKILL.md`, and unresolved blockers in the current task handoff.
