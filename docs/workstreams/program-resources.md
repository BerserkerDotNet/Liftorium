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
- `docs/architecture.md`: construct matrix, validation severity, JSON versioning, import consent.
- `docs/decisions.md`: spreadsheet-only import, unsupported constructs, cloud-assisted import consent.

## Outputs to produce

- `schema/program-resource.schema.json`.
- Resource models/types or generated type pipeline.
- Valid, warning, and blocked fixtures.
- Resource validation command registered in the verification-loop skill.

## Contracts not to break

- Unsupported constructs are critical unless classified as structured or note-only.
- First-week missing max/reference values block activation.
- Later missing max/reference values may activate but must block affected workout start.
- Same program version ID with a different content hash is a conflict.
- Activated program versions are immutable.

## Tests and evidence required

- Schema validation tests.
- Construct classification tests.
- Activation severity tests.
- Immutable version/hash conflict tests.
- Fixture validation output without private source excerpts.

## Handoff to downstream sessions

Import workflow receives schema and validation APIs. Android program runner owns runtime loading through `ProgramResourceLoader` after this workstream provides the schema, validators, examples, and activation rules. Web receives read-only resource snapshots and validation reports.
