---
name: feature-development
description: Use only for non-trivial cross-layer Liftorium feature, product, contract, or data-model changes that need decisions, traceability, vertical-slice tests, and docs. Do not use for pure platform implementation, verification, review, resource validation, or import-report checks.
---

# Feature Development

## Workflow

1. Identify the user-facing flow, acceptance scenario IDs, affected contracts, and downstream consumers.
2. Read only the affected sections of `docs/decisions.md`, `docs/architecture.md`, and `docs/product.md`.
3. If a product, privacy, data-loss, or testing-scope decision is unresolved, use `ask_user` when available; otherwise stop and report a decision blocker. In autonomous mode, choose only reversible defaults already supported by `docs/decisions.md`.
4. Record major decisions in `docs/decisions.md` with context, decision, rationale, alternatives considered, consequences, and related tests/docs/code.
5. Define or update acceptance/test matrix entries in `docs/product.md`.
6. Write or update unit tests for deterministic rules before or alongside implementation.
7. Write integration tests for cross-component behavior.
8. Add runtime/E2E tests when behavior depends on Android lifecycle, Room durability, permissions, foreground service behavior, real UI, or browser behavior.
9. Implement the smallest complete vertical slice across all affected layers.
10. Run the verification loop.
11. For major work, use the coverage-review skill.
12. Update docs in the same change when behavior, scope, commands, or contracts change.

## Related skill routing

- Android code: also use `android-implementation`; use `android-verification` for Android-specific checks.
- Web code: also use `web-implementation`; use `web-readonly-guard` when Web data access, persistence, protected domains, or capability copy changes.
- Program JSON/schema: use `program-resource-validation`.
- Import reports/correction loop: use `import-report-check`.

## Rules

- Do not implement disconnected producer/consumer layers.
- Do not guess on behavior that affects product scope, architecture, privacy, data loss, or testing scope.
- Do not mark work done until mapped tests and runtime evidence pass.

