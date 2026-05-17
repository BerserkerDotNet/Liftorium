---
name: import-report-check
description: Use when reviewing Liftorium spreadsheet import reports for activation/blocking decisions, validation severities, operator approvals, unknown exercises, missing maxes, or privacy-safe provenance.
---

# Import Report Check

## Inputs

- Import report path.
- Program resource path.
- Construct matrix path.

## Checks

- Critical, warning, and info severities are used consistently.
- Critical issues block activation.
- Unknown exercises require operator approval.
- Missing max/reference values are surfaced.
- Source provenance is recorded without bundling source content.
- Unsupported constructs map to structured, note-only, or critical classifications.
- Operator approval status is explicit.
- Explicit cloud-processing consent is recorded before source-derived AI processing.
- The report answers: can the operator safely run this without opening the original spreadsheet?
- Each issue has severity, code, source reference, affected program area, operator action, and activation impact.
- Source excerpts are minimized; use filenames, hashes, sheet names, and cell references.

## Output

- Activation decision.
- Critical issue count.
- Warning count.
- Unknown exercise approvals needed.
- Privacy/provenance concerns.

