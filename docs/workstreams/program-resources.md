# Program Resources Workstream

## Scope owned

- Versioned JSON program resource schema.
- Program Construct Matrix enforcement.
- Activation validation rules.
- Immutable program version identity and content hashing.
- Synthetic fixtures and approved private-fixture handling rules.

## Inputs to read

- `docs/mvp-roadmap.md`
- `docs/product.md`: A6, A7, and acceptance/test matrix.
- `docs/architecture.md`: construct matrix, validation severity, JSON versioning, import privacy.
- `docs/decisions.md`: spreadsheet-only import, unsupported constructs.

## Outputs to produce

- `schema/program-resource.schema.json`.
- Resource models/types or generated type pipeline.
- Valid, warning, and blocked fixtures.
- Resource validation command registered in the verification-loop skill.
- **Android input endpoint (Phase 4 deliverable):** a Compose UI flow
  that lets the user pick a `<programVersionId>.json` file produced by
  the Phase 3 import-workflow skill, runs `ProgramResourceLoader`
  against it (schema + semantics + content-hash + cross-resource
  conflict check), and writes the program into Room in a single
  transaction. This replaces the "user-facing import is out of MVP"
  restriction with a constrained file-picker import that consumes
  pre-validated JSON, not spreadsheets.
- **`ProgramResourceLoader` (Phase 4 deliverable):** the Android-side
  loader that enforces immutable program-version identity (same
  `programVersionId` + different `contentHash` = conflict) and rolls
  back partial loads on any persistence failure.

## Contracts not to break

- Unsupported constructs are critical unless classified as structured or note-only.
- First-week missing max/reference values block activation.
- Later missing max/reference values may activate but must block affected workout start.
- Same program version ID with a different content hash is a conflict.
- Activated program versions are immutable.
- `validationStatus` is a four-state enum: `activatable`, `pending_runtime_references`, `blocked`, `rejected`. Adding values requires schemaVersion bump + ADR + loader update.
- `pending_runtime_references` is set only when the artifact's structural blockers reduce to first-week `reference.missing_first_week` criticals for `training_max` / `one_rep_max` references with `supplied: false`. The loader (not the importer) supplies these at activation and re-validates.
- `contentHash` canonicalization excludes `validationStatus`, `validationIssues`, and `importAudit`, so flipping status (or runtime ref injection) does not invalidate the hash.

## Schema-version compatibility table

| schemaVersion | Adds | Loader requirements |
| --- | --- | --- |
| 1 | Baseline. | Required. |
| 2 | Week variants (`variantOf`, variant labels). | Loader must reject or fall back gracefully. |
| 3 | Percent ranges (`percentMin`/`percentMax`), rest ranges (`restMaxSecondsHint`), structured warm-up counts (`warmupSetCount`), conjunctive percent + RPE targets. | Loader must understand range targets, range rest, and warm-up counts; must NOT silently drop range bounds. |

## Tests and evidence required

- Schema validation tests.
- Construct classification tests.
- Activation severity tests.
- Immutable version/hash conflict tests.
- Fixture validation output without private source excerpts.

## Handoff to downstream sessions

Import workflow receives schema and validation APIs. Android program runner owns runtime loading through `ProgramResourceLoader` after this workstream provides the schema, validators, examples, and activation rules. Web receives read-only resource snapshots and validation reports.
