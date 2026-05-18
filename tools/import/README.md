# tools/import

Intentionally empty.

The `import-workflow` workstream is **skill-driven**, not library-driven.
There is no TypeScript runtime importer. Spreadsheet → `ProgramResource`
JSON is produced by a developer running a Copilot session and following
the procedure in `.github/skills/import-workflow/SKILL.md`.

The skill calls these existing helpers:

- `schema/validator.ts` — Ajv structural validator for the
  `ProgramResource` JSON Schema (Phase 2).
- `schema/semantics.ts` — semantic validator + construct severity sets.
- `schema/hash.ts` — canonical content-hash computation.
- `cd schema; npm run validate:resource -- <path/to/resource.json>` —
  Phase 3 CLI that wraps the three above, prints the privacy-safe
  import report, and exits non-zero on any blocking issue.

## Output location

The skill writes its two artifacts (`<programVersionId>.json` and
`<programVersionId>.import-report.json`) to **`private/imports/`** at the
repo root. That folder is gitignored except for `.gitkeep` and
`README.md`, so produced JSON never leaks into commits by default. See
`private/imports/README.md` for the rules governing exceptions for
synthetic / non-proprietary fixtures.

Phase 4 (`program-resources`) owns the Android input endpoint that
loads finalized JSON into Room via `ProgramResourceLoader`.

See `docs/workstreams/import-workflow.md` and
`docs/workstreams/program-resources.md` for the workstream charter and
the Phase 3 → Phase 4 handoff contract.
