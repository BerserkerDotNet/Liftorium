---
name: program-resource-validation
description: Use when validating versioned Liftorium JSON program resources against schema, activation contracts, construct matrix, or immutable program-version rules.
---

# Program Resource Validation

## Inputs

- Resource path.
- JSON Schema path, default `schema\program-resource.schema.json`.
- Construct matrix source, default `docs\architecture.md`.

## Checks

- JSON is valid and conforms to schema.
- `schemaVersion` is present and supported.
- Program ID and immutable program version ID are present.
- Import audit metadata is present.
- Critical validation issues are absent or unresolved activation is blocked.
- Required max/reference declarations exist for percent work.
- Exercise mappings are approved.
- Unsupported constructs map to the Program Construct Matrix.
- `activationStatus=activatable` appears only when no unresolved critical issues remain.
- Same program version ID plus different content hash is a conflict.
- Unknown exercises require approved alias or canonical mapping.
- First-week missing max blocks activation; later-week missing max may warn but must block affected workout start.
- Unsupported constructs default to critical unless the matrix classifies them as structured or note-only.

## Output

- Validation result: activatable or blocked.
- Critical issues.
- Warnings.
- Info/provenance summary.
- Schema version and resource version IDs.

