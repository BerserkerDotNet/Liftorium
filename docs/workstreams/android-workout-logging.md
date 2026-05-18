# Android Workout Logging Workstream

## Scope owned

- Offline active workout execution.
- Tap-by-tap set logging, edits, skips, extra sets, notes, RPE/RIR, and session-visible undo.
- Process-death recovery from Room.
- Calculation snapshot storage points used by max/progression.
- Integration seams for substitutions, timers, and stats.

## Inputs to read

- `docs/mvp-roadmap.md`
- `docs/workstreams/android-app.md`
- `docs/workstreams/android-program-runner.md`
- `docs/product.md`: A1 and supporting A3/A8/A9 rows.
- `docs/architecture.md`: durability, mutation metadata, sync-readiness.

## Outputs to produce

- Active workout Room entities, DAOs, repositories, and transactions.
- Logging UI and ViewModel state.
- Recovery flow after app/process death.
- Mutation metadata and tombstone strategy where needed.

## Contracts not to break

- Room is the source of truth for active workout state.
- Every user-visible mutation is transactional.
- Raw logs are authoritative.
- Workout logging must remain usable when timer permission is denied.
- When a prescription item declares `warmupSetCount > 0`, the logging UI presents that many warm-up set rows distinct from working sets; warm-up logs do not contribute to PR/e1RM derivation. When the field is absent, no warm-up rows are auto-generated.
- Conjunctive percent + RPE prescriptions log both the calculated weight and the RPE companion on the set; neither is dropped on save.

## Tests and evidence required

- Unit tests for mutation classification and recovery state rules.
- Integration tests for open session persistence and Room recovery.
- Runtime/E2E tests for offline workout completion and process kill recovery.

## Handoff to downstream sessions

Max/progression, substitutions, timers, and stats sessions receive stable workout/session/set IDs, mutation APIs, and recovery behavior they must preserve.
