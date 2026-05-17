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
- Program run entities with immutable program version references.
- Schedule occurrence model that keeps planned and actual dates distinct.
- Active workout entry contract for `android-workout-logging`.

## Contracts not to break

- Program runs pin immutable program versions.
- Finalized JSON resources are validated before loading.
- Resource loading is transactional and idempotent.
- Same program version ID plus different content hash is a conflict.
- Import audit metadata and validation issues are preserved.
- One active program run at a time in MVP.
- Repeating a program creates separate run history.
- Schedule state is distinct from workout completion.

## Tests and evidence required

- Unit tests for run identity, version pinning, and schedule rules.
- Unit tests for resource hash/idempotency conflict rules.
- Integration tests for resource validation, transactional Room load, audit preservation, start/repeat/run history separation.
- Runtime evidence that a user can start a program and open an active workout offline.

## Handoff to downstream sessions

`android-workout-logging` receives the active workout entry contract, loaded Room resource model, pinned program version, planned occurrence identity, and required persistence IDs.
