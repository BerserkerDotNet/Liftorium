# MVP Roadmap

This document splits MVP implementation into bounded workstreams so future sessions can load only the context they need. It is not a phase log.

## Session rule

Start each implementation session from one workstream. Read only:

1. This roadmap.
2. The selected workstream doc in `docs/workstreams/`.
3. The referenced sections of `docs/product.md`, `docs/architecture.md`, and `docs/decisions.md`.
4. Relevant skills that auto-load from the task description.

Do not carry unrelated workstream context into the session unless a dependency requires it.

## Workstream order

| Order | Workstream | Owns | Feeds |
| --- | --- | --- | --- |
| 1 | `foundation` | Project setup, module layout, verification command skeletons | All implementation workstreams |
| 2 | `program-resources` | Versioned JSON resource schema, validation, fixtures, activation rules | Import workflow, Android program runner, Web resource snapshots |
| 3 | `import-workflow` | Spreadsheet import, validation reports, correction loop, finalized resources | Android runnable content, Web report review |
| 4 | `android-program-runner` | Program resource loading into Room, program library, active run, version pinning, schedule entry | Android UI polish, Android workout logging |
| 5 | `android-ui-polish` | Compose theme + design tokens, typography scale, spacing scale, status/empty-state components, app icon, density rules | All subsequent Android workstreams (workout logging, timers, stats, etc.) |
| 6 | `android-workout-logging` | Offline active workout, tap-by-tap persistence, recovery | Max/progression, substitutions, timers, stats |
| 7 | `android-one-rep-max-progression` | 1RMs, percent work, rounding, progression | Workout prescriptions, stats caveats |
| 8 | `android-catalog-substitutions` | Exercise catalog approval, substitution flow, prescribed/performed history | Workout logging, stats |
| 9 | `android-rest-timers` | Timer policy, permissions, foreground-service locked alerts | Workout runtime UX |
| 10 | `android-stats-history` | History, PRs, e1RM, substitution stats, source drilldowns | Android acceptance, optional Web fixture data definitions |
| 11 | `web-readonly` | Online-only read-only Web surfaces over explicit snapshots | Web inspection/review surfaces |
| 12 | `acceptance-hardening` | Edge cases, privacy checks, final verification, coverage critique | MVP release decision |

## Todo ownership

| Todo | Workstream |
| --- | --- |
| `setup-projects-schema` | `foundation` |
| `program-resources-schema` | `program-resources` |
| `build-import-pipeline` | `import-workflow` |
| `android-program-runner` | `android-program-runner` |
| `android-ui-polish` | `android-ui-polish` |
| `android-workout-logging` | `android-workout-logging` |
| `one-rep-max-progression` | `android-one-rep-max-progression` |
| `catalog-substitutions` | `android-catalog-substitutions` |
| `android-rest-timers` | `android-rest-timers` |
| `stats-history` | `android-stats-history` |
| `web-secondary` | `web-readonly` |
| `hardening-verification` | `acceptance-hardening` |

## Cross-workstream handoffs

- `foundation` must register repeatable Android, Web, schema, and import commands in the verification-loop skill as tools appear. Registered commands must run real code, schemas, generated resources, builds, tests, or runtime checks.
- `program-resources` must publish schema, resource examples, validation behavior, and activation rules before import or Android runtime depends on resources.
- `import-workflow` must produce app-ready resources and reports without requiring original spreadsheets at runtime.
- `android-program-runner` owns Android `ProgramResourceLoader`: validate finalized JSON resources at runtime, load them transactionally into Room, preserve import audit/issues, enforce idempotency/hash conflicts, and create pinned program runs before workout logging starts.
- `android-ui-polish` produces the canonical Compose theme, typography/spacing/color tokens, and shared UI components (status badges, empty states, etc.). All subsequent Android workstreams MUST consume those tokens rather than reaching for raw Material 3 defaults. Existing `android-program-runner` screens are retroactively reskinned through the new tokens, with Paparazzi diffs as evidence.
- `android-workout-logging` must persist user-created workout state in Room with sync-ready metadata and expose integration seams for maxes, substitutions, timers, and stats.
- `android-one-rep-max-progression` must snapshot calculated prescriptions so history does not change when 1RMs change.
- `android-catalog-substitutions` must preserve original and performed exercise IDs for history and stats.
- `android-rest-timers` must not block workout logging when timer permissions are denied.
- `android-stats-history` must derive stats from raw logs and define any non-authoritative history/stat snapshot fixtures that Web may render. Android export is still out of MVP.
- `web-readonly` must own Web snapshot input schemas/fixtures for history and stats, consume explicit snapshots only, and must not imply live Android data access.
- `acceptance-hardening` must verify the acceptance/test matrix in `docs/product.md` and close privacy, runtime, migration, and coverage gaps.

## Shared completion rule

A workstream is complete only when its mapped acceptance scenarios, contracts, tests, and verification commands pass. If a mapped test or contract is wrong, update the durable source of truth rather than adding a session log.
