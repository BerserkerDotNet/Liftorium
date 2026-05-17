# Schema — Liftorium program resources

Versioned JSON Schema, semantic validator, fixtures, and example program
resources produced by the spreadsheet import workflow and consumed by the
Android `ProgramResourceLoader` and the read-only Web review surfaces.

## Layout

- `program-resource.schema.json` — canonical JSON Schema. Phase 2 ships
  `schemaVersion = 1` with the full substantive contract; later schema
  changes bump `schemaVersion` and never mutate a published version.
- `hash.ts` — canonical content hashing
  (`computeProgramResourceContentHash`). The hash covers `metadata`
  (minus `contentHash`), `programDefaults`, `exerciseCatalog`,
  `requiredReferences`, `programStructure`, and `progressionRules`. It
  deliberately excludes `validationStatus`, `validationIssues`, and
  `importAudit` so re-validating a resource never changes its hash.
- `semantics.ts` — semantic validator (`validateProgramResourceSemantics`)
  that enforces the cross-field rules JSON Schema cannot express.
  Activation requires both Ajv-strict validation **and** an empty
  critical-issue set from the semantic validator.
- `validator.ts` — Ajv strict (2020-12) entry point used by the tests
  and by `tools/import`. Re-exports the hash and semantic helpers.
- `fixtures/` — valid, blocked-by-semantics, and JSON-Schema-invalid
  documents the test suite exercises. Source spreadsheets must never
  appear here; every fixture is `importAudit.sourceKind = synthetic`.
- `examples/` — operator-facing example resource(s). Same shape as
  fixtures but living under a separate directory to mark them as the
  canonical references developers should read first.
- `scripts/refresh-fixture-hashes.mjs` — cross-platform refresher that
  recomputes and rewrites `metadata.contentHash` for every fixture and
  example. Invoked via `npm run refresh-fixture-hashes`.
- `test/` — Vitest suite covering schema, semantics, and content-hash
  freshness.

## Commands

```
npm install                          # one-time
npm test                             # full suite (schema + semantics + hashes)
npm run typecheck                    # tsc --noEmit
npm run refresh-fixture-hashes       # rewrite contentHash in every fixture and example
```

## Two-stage validation

A resource is activatable iff:

1. **Ajv-strict** validation succeeds against
   `program-resource.schema.json`. Phase 2 enforces `additionalProperties:
   false` on every closed object (`progressionRules[].parameters` is the
   only open object — runtime introspection of progression rules is owned
   by the Android program runner, not the validator).
2. `validateProgramResourceSemantics(resource)` returns no `critical`
   issues, and `validationStatus = "activatable"`. A resource marked
   `validationStatus = "blocked"` or `"rejected"` is never activatable,
   even with an empty issue list (rejected is treated as terminal).

## Discriminated prescription targets

`setPrescriptions[].targets[]` is a `oneOf` discriminated union keyed by
`kind`:

- `exact_load_reps` — explicit `loadValue`, `loadUnit`, `reps`.
- `rep_range` — `repMin`, `repMax`.
- `percent` — `percent`, `referenceId`, optional `reps`, `amrap`,
  rounding override.
- `rpe` — at least one of `target`, `{rangeMin, rangeMax}`, or `cap`.
- `rir` — at least one of `target`, `{rangeMin, rangeMax}`, or `floor`.

Each variant carries its own `additionalProperties: false`, so downstream
Kotlin/TypeScript consumers can rely on exhaustive matching over `kind`.

## Issue code namespace

Semantic-validator issues use stable dot-namespaced codes. Importers and
the Android loader should treat unknown codes as `critical` by default.

| Namespace | Examples |
| --- | --- |
| `schema.*` | `schema.malformed_root`, `schema.version_unsupported`, `schema.audit_version_mismatch` |
| `metadata.*` | `metadata.content_hash_mismatch` |
| `status.*` | `status.activatable_with_critical` |
| `catalog.*` | `catalog.duplicate_exercise_id`, `catalog.duplicate_alias_text` |
| `structure.*` | `structure.no_runnable_week`, `structure.duplicate_id`, `structure.duplicate_order`, `structure.ambiguous_week_order`, `structure.ambiguous_session_order` |
| `exercise.*` | `exercise.unknown_reference` |
| `reference.*` | `reference.unknown`, `reference.missing_first_week`, `reference.missing_later_week`, `reference.declared_week_mismatch`, `reference.unused_declaration` |
| `construct.*` | `construct.severity_understated`, `construct.severity_overstated`, `construct.must_be_critical`, plus the per-construct codes the importer emits (`construct.drop_set`, `construct.density_emom`, `construct.for_time`, `construct.unsupported_autoregulation`, `construct.unknown`, `construct.tempo`, `construct.rest_pause`, `construct.myo_reps`) |
| `provenance.*` | `provenance.private_import_zero_hash`, `provenance.private_import_missing_consent`, `provenance.synthetic_with_real_hash` |

The Program Construct Matrix in `docs/architecture.md` is the source of
truth for which construct codes are `critical` vs `warning`. The semantic
validator enforces that alignment with `construct.severity_understated`,
`construct.severity_overstated`, and `construct.must_be_critical`.

## Provenance and privacy (`importAudit.sourceKind`)

- `synthetic` — fixtures and examples. Permits the all-zero SHA-256
  sentinel as `sourceHash` so private spreadsheets never need to be
  named or hashed for tests.
- `private_import` — real operator spreadsheets. Requires a real
  64-hex SHA-256 `sourceHash` and `consentGranted: true`; missing
  either emits a `provenance.*` warning. Full per-import consent
  enforcement lives in the Phase 3 import workflow.

## Content hash refresh workflow

`metadata.contentHash` is the loader-side identity check (Phase 4) that
detects the "same `programVersionId`, different content = conflict" case.
For fixtures and examples it is computed via
`computeProgramResourceContentHash` and committed alongside the resource.
When you edit a fixture, re-run `npm run refresh-fixture-hashes`; the
script only touches the hash field, so the diff stays minimal.

`fixtures/blocked-content-hash-mismatch.json` deliberately carries a
stale hash to exercise `metadata.content_hash_mismatch`; the refresh
script skips it because it is not listed in `HASHED_FIXTURE_FILES`.

## Phase 2 scope

Phase 2 (`program-resources`) tightened the schema in place rather than
bumping to `schemaVersion = 2` because no production resource shipped
under the Phase 1 skeleton. Future schema changes that break consumers
must bump `schemaVersion`; minor additive changes that stay backward
compatible may stay on the current version.

Out of scope for Phase 2 and tracked elsewhere:

- Import workflow that produces resources — Phase 3 (`docs/workstreams/import.md`).
- Android `ProgramResourceLoader` runtime — Phase 4.
- `progressionRules[].parameters` runtime semantics — Phase 4/6.
- Generated Kotlin/TypeScript model types — the loader and Web
  workstreams own their own consumer types.

