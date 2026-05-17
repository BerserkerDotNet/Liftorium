# Import Workflow Workstream

## Scope owned

- Spreadsheet-first developer/operator import.
- Explicit per-import consent before cloud-assisted source processing.
- XLSX parsing, source references, formulas, merged-cell handling.
- Generated exercise catalog approval.
- Validation report, correction loop, and finalized app-ready JSON output.

## Inputs to read

- `docs/mvp-roadmap.md`
- `docs/product.md`: A6, A7, and acceptance/test matrix.
- `docs/architecture.md`: import privacy, validation severity, construct matrix, JSON versioning.
- `docs/workstreams/program-resources.md`

## Outputs to produce

- Import CLI/tooling under `tools/import/`.
- Privacy-safe validation report format.
- Correction loop that reruns validation.
- Finalized versioned JSON resources.
- Import report validation command registered in the verification-loop skill.

## Contracts not to break

- PDF-assisted import is out of MVP.
- User-facing import is out of MVP.
- Original spreadsheets, PDFs, private excerpts, and sensitive training data are not committed by default.
- Critical issues block finalization and activation.
- Unknown exercises require explicit approval or alias/canonical mapping.

## Tests and evidence required

- Synthetic spreadsheet fixtures.
- Approved private spreadsheet-derived fixtures only when explicitly allowed.
- Consent gate tests.
- Unknown exercise approval tests.
- Critical/warning/info report tests.
- End-to-end import to finalized resource/report.

## Handoff to downstream sessions

Android and Web sessions should never need original spreadsheets. They receive finalized JSON resources, validation reports, source hashes, and issue IDs.
