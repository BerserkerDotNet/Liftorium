# private/imports/

Canonical output sink for the `import-workflow` skill. The Phase 3 importer
procedure (`.github/skills/import-workflow/SKILL.md`) writes its two
artifacts here:

- `<programVersionId>.json` — the canonical `ProgramResource`.
- `<programVersionId>.import-report.json` — privacy-safe sidecar with
  source SHA-256, activation decision, and every operator-approval
  decision made during the session.

## Why this folder is gitignored

Source workbooks and the resources derived from them may be proprietary.
The whole `private/` tree is gitignored by default. This folder is
preserved (via `.gitkeep` + this README) so:

1. The skill can reference a stable, predictable output path in Steps 7
   and 10 without per-session bikeshedding.
2. Operators can drop artifacts here knowing they will not be committed
   accidentally.

## Committing a synthetic fixture

If — and only if — the operator confirms the source workbook is synthetic
or non-proprietary AND the resulting resource carries no private content,
a fixture can be committed by adding an explicit negation to `.gitignore`,
e.g.:

```
!private/imports/<programVersionId>.json
!private/imports/<programVersionId>.import-report.json
```

Default posture: do NOT commit anything else here.

## Related

- `.github/skills/import-workflow/SKILL.md` — Phase 3 importer skill.
- `.github/skills/import-report-check/SKILL.md` — checker for the
  produced sidecar.
- `tools/import/README.md` — explains why there is no TypeScript runtime
  importer (the skill IS the importer).
- `docs/decisions.md` — 2026-05-17 entry covers the privacy-safe report
  contract; the gitignore pattern below the `private/` block carries the
  same intent.
