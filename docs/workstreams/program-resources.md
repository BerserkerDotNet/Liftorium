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
- **Android input endpoint (android-program-runner deliverable):** a Compose UI flow
  that lets the user pick a `<programVersionId>.json` file produced by
  the import-workflow import-workflow skill, runs `ProgramResourceLoader`
  against it (schema-version gate + canonical contentHash recompute +
  `validationStatus ∈ {activatable, pending_runtime_references}` +
  pending-references gate + same-id-different-hash conflict check),
  and writes the program into Room in a single transaction. The
  loader does NOT re-run the full semantic validator on-device; see
  `docs/decisions.md` "Android runtime validation strategy =
  recheck_only" for rationale. This replaces the "user-facing import
  is out of MVP" restriction with a constrained file-picker import
  that consumes pre-validated JSON, not spreadsheets.
- **`ProgramResourceLoader` (android-program-runner deliverable):** the Android-side
  loader that enforces immutable program-version identity (same
  `programVersionId` + different `contentHash` = conflict) and rolls
  back partial loads on any persistence failure. Validation is
  `recheck_only`: schema-version range, canonical contentHash, status
  whitelist, and pending-references first-week TM gate. The import-workflow
  TS validator (`schema/scripts/validate-resource.ts` +
  `schema/semantics.ts`) remains the single source of truth for
  semantic activation rules.

## Contracts not to break

- Unsupported constructs are critical unless classified as structured or note-only.
- First-week missing max/reference values block activation.
- Later missing max/reference values may activate but must block affected workout start.
- Same program version ID with a different content hash is a conflict.
- Activated program versions are immutable.
- `validationStatus` is a four-state enum: `activatable`, `pending_runtime_references`, `blocked`, `rejected`. Adding values requires schemaVersion bump + ADR + loader update.
- `pending_runtime_references` is set only when the artifact's structural blockers reduce to first-week `reference.missing_first_week` criticals for `training_max` / `one_rep_max` references with `supplied: false`. The loader (not the importer) collects these at activation; injected values are stored on the program-run row (run-scoped), never on the loaded version row. The loader never rewrites `validationStatus` or `validationIssues` on persisted version rows.
- `contentHash` canonicalization excludes `validationStatus`, `validationIssues`, and `importAudit`, so flipping status (or runtime ref injection) does not invalidate the hash.
- Runtime validation on Android is `recheck_only`: schema-version range + canonical contentHash recompute + status whitelist + pending-references first-week TM gate. The Kotlin loader does NOT port `schema/semantics.ts`; the import-workflow TS validator stays the single source of truth for semantic activation rules.

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
