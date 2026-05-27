# Android Program Runner Workstream

## Scope owned

- Program library and program details.
- Android `ProgramResourceLoader`.
- Runtime validation and transactional loading of finalized JSON resources into Room.
- Import audit/issues preservation for loaded resources.
- Idempotency and content-hash conflict handling.
- One active program run at a time.
- Program version pinning.
- Start, repeat, abandon, restart, and schedule-control entry points.
- Today/active workout entry.

## Inputs to read

- `docs/mvp-roadmap.md`
- `docs/workstreams/android-app.md`
- `docs/workstreams/program-resources.md`
- `docs/product.md`: A4 and relevant A1/A9 rows.
- `docs/architecture.md`: JSON versioning, resource idempotency, durability, sync-readiness.

## Outputs to produce

- Program library/detail domain and UI flows.
- `ProgramResourceLoader` service and repository boundary.
- Transactional resource import into Room.
- Loaded resource audit/issues tables or equivalent persisted model.
- Program run entities with immutable program version references. `ProgramRunEntity` carries the pinned `programVersionId` + `contentHash`, the chosen week-variant map, and an `activeRunSlot INTEGER NULL` column with a `UNIQUE` index; `activeRunSlot=1` only while `status=Active` and is nulled on transitions to `Completed` / `Abandoned`. The SQL unique constraint is the source of truth for the one-active-run invariant; a racy concurrent insert surfaces as a typed `AlreadyActiveRun` error.
- Runtime-injected reference values are stored on `ProgramRunReferenceValueEntity(programRunId, referenceId, value, unit, source, suppliedAtUtc)`, scoped to the program run. The `LoadedRequiredReferenceEntity` row preserves the import-time `supplied=false` shape verbatim. Effective values at workout-start time come from a join of the two.
- Schedule occurrence model that keeps planned and actual dates distinct.
- Active workout entry contract for `android-workout-logging`.
- Pending-references activation flow: when `validationStatus == pending_runtime_references`, prompt the user for the unsupplied 1RM values, write them as `ProgramRunReferenceValueEntity` rows in the same Room transaction that inserts `ProgramRunEntity`, and only then mark the run `Active`. The recheck-only loader gates persistence on the in-memory composite (loaded refs + run-scoped injections + no other criticals); it does NOT mutate the loaded version row or its `validationStatus` / `validationIssues`. Reject with a structured error if any non-pending-ref critical reappears.
- Week-variant selection UI: when a week declares variants (e.g. 10A / 10B via `variantOf`), the runner picks ONE variant per program run; the choice is persisted with the run.
- schemaVersion compatibility gate: refuse to load any resource whose `schemaVersion` exceeds the runner's supported set, with a clear "update the app" message. The MVP targets schemaVersion 3.

## Contracts not to break

- Program runs pin immutable program versions.
- Finalized JSON resources are validated before loading. Runtime validation on Android is `recheck_only`: schema-version range, canonical contentHash recompute, status whitelist (`activatable` | `pending_runtime_references`), and the pending-references first-week TM gate. The import-workflow TS validator stays the single source of truth for semantic rules; the Kotlin loader does NOT port `schema/semantics.ts`. See `docs/decisions.md` "Android runtime validation strategy = recheck_only".
- Resource loading is transactional and idempotent.
- Same program version ID plus different content hash is a conflict.
- Import audit metadata and validation issues are preserved verbatim on the loaded version row; the loader never rewrites `validationStatus` or `validationIssues`.
- One active program run at a time in MVP, enforced at the DB layer by a `UNIQUE` index on `ProgramRunEntity.activeRunSlot` (NULL when `status` is not `Active`). Use-case checks remain as friendly UX but are not the source of truth.
- Repeating a program creates separate run history with its own `ProgramRunReferenceValueEntity` rows.
- Schedule state is distinct from workout completion.
- `pending_runtime_references` artifacts MUST NOT be persisted as activated until all referenced TMs are supplied (as run-scoped `ProgramRunReferenceValueEntity` rows) AND the recheck composite shows no remaining criticals. Activation must NOT silently downgrade `reference.missing_first_week` criticals.
- Range targets (`percentMin`/`percentMax`), conjunctive percent + RPE targets, range rest (`restMaxSecondsHint`), and structured warm-up counts (`warmupSetCount`) must round-trip into Room without losing range bounds, the RPE companion, or the warm-up count.

## Tests and evidence required

- Unit tests for run identity, version pinning, and schedule rules.
- Unit tests for resource hash/idempotency conflict rules.
- Integration tests for resource validation, transactional Room load, audit preservation, start/repeat/run history separation.
- Runtime evidence that a user can start a program and open an active workout offline.

## Status

android-program-runner (Slices 0–3) delivered:

- `:domain` program-resource models + content-hash recomputer with cross-language fixture parity (`HashParityTest`).
- `:data` `LiftoriumDatabase` v2 (Phase 4 review uplift over the v1 baseline; both schemas exported at `android/data/schemas/dev.liftorium.data.LiftoriumDatabase/{1,2}.json`). v2 adds `program_run.updatedAtEpochMillis`, `schedule_occurrence.updatedAtEpochMillis`, a `program_run.startedAtEpochMillis` index, a composite `schedule_occurrence(programRunId, plannedEpochDay, sessionIndex)` index replacing the v1 single-column index, and a `UNIQUE INDEX` on `loaded_program_version.contentHash`. `Migration1To2` is additive and data-preserving; `MigrationTest.migrate1To2_addsAuditColumnsAndIndexesWithoutDataLoss` covers the round-trip. `ProgramResourceLoader` enforces `recheck_only` (schemaVersion 1..3, contentHash recompute, status whitelist) and writes the full normalized tree in one transaction. `IT-IMP-006` (same `programVersionId` + different `contentHash` rejected) covered by `ProgramResourceLoaderTest` and now also by the DB unique-index constraint.
- Run vertical: `ProgramRunEntity` with `activeRunSlot` unique index; `ProgramRunReferenceValueEntity` for run-scoped runtime injections; `ScheduleOccurrenceEntity` seeded transactionally at run start. `StartProgramRun` / `RepeatProgramRun` / `AbandonProgramRun` use cases land variant-aware occurrence seeding. `AbandonProgramRun` runs as a single Room transaction (`markAbandonedAndReturn`) that nulls `activeRunSlot` and returns the updated row atomically. Invalid variant choices fail with the typed `InvalidWeekVariantChoice` error.
- Domain IDs: `ProgramId`, `ProgramVersionId`, and `ProgramRunId` are Kotlin `@JvmInline value class` wrappers in `:domain` (`RunIds.kt`). All domain types and the `ProgramRunRepository` interface use the typed wrappers at boundaries; `:data` mappers unwrap to `String` at the Room edge; the kotlinx-serialization DTO stays `String`. Per `docs/decisions.md` 2026-05-17 ("Domain ID types use Kotlin value classes for the three most-confusable IDs").
- `:app` Compose surfaces: `ProgramLibraryScreen`, `ProgramDetailScreen`, `PendingReferencesDialog`, `WeekVariantPicker`, `TodaySessionScreen`, `ImportErrorBanner`. Sample-state-driven nav host (`LiftoriumNavHost`) wires the Activate gating chain for visual demos pending the follow-on slice's manual DI container.
- Paparazzi snapshots (`ProgramRunnerPaparazziTest`, 9 PNGs) cover empty + populated library, both detail variants, both modal dialogs, and the Today stub. Robolectric semantics test validates the Library → Detail → Today path for the `activatable` flow; the AlertDialog flow is gated on `connectedDebugAndroidTest` (Robolectric's dialog idling is unreliable).
- Coverage gate (`:domain` + `:data` only, `:app` excluded): branch 95.19% / line 98.9% / instruction 98.77%.

Follow-on slice (still in this workstream, separate slice):

- Manual DI container (`LiftoriumAppContainer`) wiring `LiftoriumDatabase`, `ProgramResourceLoader`, run use cases, `JvmUuidIdGenerator`, and `TimeSource`. `MainActivity` replaces the sample state with the wired container output.
- Storage Access Framework JSON import flow with success/error surfacing on `ProgramLibraryScreen`.
- Schedule rescheduling, repeat/abandon/restart UI, and calendar scheduling beyond the one-session-per-day MVP policy.
- `connectedDebugAndroidTest` coverage for the AlertDialog input/confirm flow and any timer/lifecycle behavior.

## Handoff to downstream sessions

`android-workout-logging` receives the active workout entry contract, loaded Room resource model, pinned program version, planned occurrence identity, and required persistence IDs.
