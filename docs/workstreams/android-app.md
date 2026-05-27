# Android App Shared Context

This file is shared context for Android implementation sessions. It owns no SQL todo by itself; choose the specific Android sub-workstream doc for implementation.

## Android sub-workstreams

- `android-program-runner.md`
- `android-workout-logging.md`
- `android-one-rep-max-progression.md`
- `android-catalog-substitutions.md`
- `android-rest-timers.md`
- `android-stats-history.md`

## Inputs to read

- `docs/mvp-roadmap.md`
- The selected Android sub-workstream doc.
- `docs/product.md`: relevant A1-A5 and A8-A9 acceptance/test matrix rows.
- `docs/architecture.md`: Android layers, Room source-of-truth, timer, stats.
- `docs/architecture.md`: durability, timer, stats, substitutions, 1RMs, sync-readiness.
- `docs/workstreams/program-resources.md`

## Shared outputs

- Android domain, data, and UI modules.
- Room schema, DAO/repositories, migrations, and sync-ready metadata.
- Android verification commands registered in the verification-loop skill.

## Contracts not to break

- Room is the source of truth for active workout state and logs.
- Every user-visible workout mutation is transactional.
- Program runs pin immutable program versions.
- 1RM updates affect future prescriptions only.
- Raw logs are authoritative; derived stats caches are rebuildable.
- Notification denial blocks timer start only, not workout logging.

## Tests and evidence required

- Domain unit tests for deterministic rules.
- Room transaction and migration tests.
- Integration tests for resource loading, program runs, substitutions, max updates, stats rebuild.
- Runtime/E2E tests for offline workout, process death recovery, timer permissions, locked-phone alerts, repeats, rescheduling, and substitution history.

## Handoff to downstream sessions

Web receives only explicit snapshots, not Room access. Acceptance hardening receives commands, runtime evidence paths, known device/API limitations, and any unresolved contract gaps.
