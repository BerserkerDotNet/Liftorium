# Import Workflow Workstream

## Scope owned

- Spreadsheet-first developer/operator import.
- XLSX parsing, source references, formulas, merged-cell handling.
- Generated exercise catalog approval.
- Validation report, correction loop, and finalized app-ready JSON output.

## Inputs to read

- `docs/mvp-roadmap.md`
- `docs/product.md`: A6, A7, and acceptance/test matrix.
- `docs/architecture.md`: import privacy, validation severity, construct matrix, JSON versioning.
- `docs/workstreams/program-resources.md`

## Outputs to produce

- `.github/skills/import-workflow/SKILL.md` — the developer-time
  procedure that drives the import end-to-end.
- `schema/scripts/validate-resource.ts` — CLI helper the skill invokes
  to validate a candidate JSON file. Wraps `schema/validator.ts`,
  `schema/semantics.ts`, and `schema/hash.ts`; prints the privacy-safe
  6-per-issue + 5-report-level fields contract defined in
  `.github/skills/import-report-check/SKILL.md`.
- `npm run validate:resource` script in `schema/package.json`.
- Acceptance procedure entries in `docs/product.md` A6/A7 traceability
  rows for tests that move from code coverage to skill-procedure
  coverage (CLI-IMP-001/002, UT-IMP-004/005, IT-IMP-001/004/005).
- Import report validation command registered in the verification-loop
  skill (`cd schema; npm run validate:resource -- <path>`).
- `tools/import/` stays intentionally empty (placeholder README only);
  no TypeScript runtime importer exists or is planned.

## Contracts not to break

- PDF-assisted import is out of MVP.
- User-facing import endpoint (file picker → `ProgramResourceLoader` →
  Room) is owned by the `program-resources` workstream (android-program-runner), not
  by this workstream. The import-workflow skill only produces JSON files on
  the developer's local filesystem.
- Original spreadsheets, PDFs, private excerpts, and sensitive
  training data are not committed by default.
- Critical issues block finalization and activation.
- Unknown exercises require explicit approval or alias/canonical
  mapping.
- The import-workflow → android-program-runner handoff is two artifacts: the
  `ProgramResource` JSON and the `import-report.json` sidecar.

## Tests and evidence required

- Synthetic spreadsheet walk-through evidence for CLI-IMP-001/002
  produced once during import-workflow acceptance (not committed; recorded in
  the coverage-review report).
- Approved private spreadsheet-derived JSON only when explicitly
  allowed by the operator.
- Validator CLI exit-code coverage across `schema/fixtures/` proves
  classification of activatable / warnings / blocked / rejected.
- Critical/warning/info report contract proven by the validator CLI's
  6-per-issue projection on those fixtures.
- End-to-end import to finalized resource/report is verified by
  walking the import-workflow skill procedure on a synthetic workbook
  during phase acceptance.

## Handoff to downstream sessions

Android and Web sessions should never need original spreadsheets. They
receive finalized JSON resources plus the privacy-safe import-report
sidecar from the import-workflow skill (import-workflow).

android-program-runner assumptions for the loader:

- Receives `<programVersionId>.json` and `<programVersionId>.import-report.json`.
- MUST revalidate the JSON independently against
  `schema/program-resource.schema.json`, the semantic validator, and a
  recomputed `metadata.contentHash`. The sidecar report is operator
  audit evidence, not a substitute for loader validation.
- MUST treat same `programVersionId` with a different `contentHash`
  as a conflict; reject the load and surface the conflict to the user.
- MUST NOT attempt to read or open the original spreadsheet.
- Loader-side cross-resource conflict gating is authoritative; the
  import-workflow skill is advisory only.
