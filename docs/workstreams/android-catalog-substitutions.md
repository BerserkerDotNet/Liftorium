# Android Catalog and Substitutions Workstream

## Scope owned

- Generated exercise catalog approval in app data.
- Program-provided and generated substitution alternatives.
- Substitution reason and scope.
- Substitute, undo, and future-run scope rules.
- Prescribed/performed history fields.

## Inputs to read

- `docs/mvp-roadmap.md`
- `docs/workstreams/android-app.md`
- `docs/workstreams/import-workflow.md`
- `docs/workstreams/android-workout-logging.md`
- `docs/product.md`: A3 and A5 rows.
- `docs/architecture.md`: substitution policy, exercise recognition, stats inclusion.

## Outputs to produce

- Exercise catalog/alias persistence.
- Substitution domain services and UI flows.
- Workout log fields preserving original and performed exercise IDs.
- Undo/audit behavior.

## Contracts not to break

- Unknown exercises require approved mapping before activation.
- Substitutions preserve original prescribed exercise ID and performed exercise ID.
- Performed work counts toward performed exercise stats only.
- Original exercise remains visible in adherence and prescribed/performed history.

## Tests and evidence required

- Unit tests for substitution stats policy and undo rules.
- Integration tests from substitution to log/history/stats.
- Runtime evidence that a substitute can be logged and later inspected.

## Handoff to downstream sessions

Stats receives original/performed exercise IDs, reasons, scope, and undo/audit fields.
