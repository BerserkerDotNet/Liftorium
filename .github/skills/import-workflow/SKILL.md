---
name: import-workflow
description: Use when a developer/operator session needs to convert a Liftorium program spreadsheet (.xlsx) into a schema-compliant ProgramResource JSON for handoff to the Android app. Drives workbook orientation, construct detection, exercise approval, validation/correction loop, and two-artifact finalization (JSON + import-report sidecar). Do NOT use for Android ProgramResourceLoader implementation, Room program tables, generic schema-only validation, or review-only checks of an already-finalized import report.
---

# Import Workflow

Convert a Liftorium program spreadsheet (.xlsx) into a schema-compliant
`ProgramResource` JSON, plus a privacy-safe sidecar that records every
decision made during the session. The Copilot session running this skill
IS the importer. There is no TypeScript runtime library; `tools/import/`
is intentionally empty.

The output is two files handed to the user:

- `<programVersionId>.json` — the canonical `ProgramResource`.
- `<programVersionId>.import-report.json` — privacy-safe sidecar that
  records the source filename + hash, activation decision, and every
  issue + operator-approval decision made during the session.

**Canonical output location: `private/imports/`** (repo-relative).
`private/imports/` is the gitignored sink that the skill writes to and
reads from. The folder itself is committed (via `.gitkeep` + `README.md`);
its contents are ignored by default so proprietary program data never
leaks into git. See `private/imports/README.md` for the commit-exception
rules for synthetic / non-proprietary fixtures.

Phase 4 (`program-resources`) owns the Android input endpoint that
ingests these files into Room via `ProgramResourceLoader`.

## Inputs

- Path to the source `.xlsx`.
- `programId` and `programVersionId` (caller-supplied, never inferred).
- Optional: existing-version lookup so version conflicts can be flagged
  before the resource is handed off.

## Outputs

Both files MUST be written to `private/imports/` (repo-relative). The
operator never has to choose a path; the skill writes there unconditionally.
The folder is gitignored except for `.gitkeep` and `README.md`, so
nothing here ends up in commits unless the operator adds an explicit
negation per `private/imports/README.md`.

- `private/imports/<programVersionId>.json` — finalized `ProgramResource`.
- `private/imports/<programVersionId>.import-report.json` — sidecar
  (six per-issue fields + five report-level fields per
  `.github/skills/import-report-check/SKILL.md` line 25).
- Activation decision: `activatable` / `activatable_with_warnings` /
  `pending_runtime_references` / `blocked` / `rejected`.

## Step 0 — Pre-flight metadata

Before reading any workbook content:

- Compute the file's basename, on-disk size, and SHA-256 of the bytes.
  The basename and SHA-256 land in the resource JSON as
  `importAudit.sourceFilename` and `importAudit.sourceHash`; the size
  is captured only in the chat-visible audit summary so the operator
  can confirm they tagged the right file.
- Set `importAudit.sourceKind = "private_import"`.
- Everything from Step 1 onward MUST obey the Privacy boundary contract
  below — that rule exists because the **content** is private.

## Privacy boundary contract

Throughout the entire procedure:

- Never include raw cell text, formula text, proprietary excerpts, or
  private fixture paths in any of: chat output, validation report,
  resource JSON, commit messages, decision-log entries, plan files, or
  the final summary.
- Refer to source data only via: file basename, source SHA-256,
  sheet name, and cell reference (e.g. `Week3!C12`).
- Never commit the source workbook, the produced JSON, or the produced
  sidecar to the repository unless the operator explicitly approves
  AND the content is synthetic / non-proprietary.
- The validation report's `message` field summarizes the issue
  *category*; it never quotes the source. The `locationHint` field is
  the cell ref, not the cell value.
- If you suspect a value leaked into a message, fix the message before
  writing the sidecar.

## Step 1 — Workbook orientation

Use an ad-hoc inspection method local to the developer's machine.
This procedure does NOT ship a workbook parser; pick whichever local
tool the developer has available:

- Python with `openpyxl` (`pip install openpyxl` once):

  ```python
  from openpyxl import load_workbook
  wb = load_workbook("/path/to/program.xlsx", data_only=False)
  for name in wb.sheetnames:
      ws = wb[name]
      print(name, ws.max_row, ws.max_column)
  ```

- Or Node with ExcelJS via a throwaway script (do NOT add ExcelJS to
  any committed `package.json`).

What to capture (everything stays in-session, never committed):

- Sheet names, row/column dimensions, merged ranges.
- The program's block/week/session/exercise layout convention.
- Where prescriptions live (e.g. `Week1!C5` reads `"5x5 @ 85%"`).
- Where training maxes / reference values live.
- Any free-text notes that look like progression rules or constructs.

### Step 1.1 — Header-row recipe (when the first row is not the header)

Tracking-template workbooks frequently push the actual header down by
a banner, title row, instructions, etc. (Power Building 1's "Week 1"
header sat on row 11.) Don't assume row 1.

- Scan the first ~30 rows and look for a row whose cells contain any
  of: `Week 1`, `Day 1`, `Session 1`, `Set 1`, `Exercise`, `Reps`,
  `Weight`, `%1RM`, `RPE`, `RIR`. Pick the lowest row index whose
  count of those keywords is highest — that's the header.
- Capture which columns map to which fields (e.g. `B=Exercise`,
  `D=Sets`, `E=Reps`, `H=%1RM`, `I=RPE`, `J=Rest`, `K=Notes`). This
  mapping IS the per-workbook orientation; record it in the audit trail.
- If multiple sheets look like prescription sheets (one per day, or
  one per block), repeat per sheet. The orientation may differ.

## Step 2 — Construct detection (against the Program Construct Matrix)

The Program Construct Matrix lives in `docs/architecture.md`. The
authoritative severity sets live in `schema/semantics.ts`:

- `KNOWN_CRITICAL_CONSTRUCT_CODES`:
  - `construct.drop_set`
  - `construct.density_emom`
  - `construct.for_time`
  - `construct.unsupported_autoregulation`
  - `construct.unknown`
- `KNOWN_WARNING_CONSTRUCT_CODES`:
  - `construct.tempo`
  - `construct.rest_pause`
  - `construct.myo_reps`

Rules:

- Every detected construct must be emitted as a `construct.<code>`
  validation issue.
- Unknown construct codes default to `critical`.
- Do NOT invent new `construct.*` codes — if the matrix doesn't list
  it, use `construct.unknown` and let the operator decide.
- AMRAP/`+` patterns are NOT a construct — they map onto existing
  prescription fields (`setKind: "amrap"` or `percent.amrap: true`).
- **Warning-class constructs (tempo, rest_pause, myo_reps) are
  note-only.** Encode the cue inline on the affected
  `prescriptionItem.notes[]` with the matching `kind` (e.g.
  `{ "kind": "tempo", "text": "3010" }`). The
  `construct.<code>` warning issue is ALSO emitted (one per
  detection) so the import-report retains the count, but these
  warnings are NOT correction-loop fodder — see Step 8. Operators
  do not iterate on them; they ship as warnings.

## Step 3 — Exercise resolution (operator approval REQUIRED)

For every exercise referenced by the workbook:

- Generate a stable kebab-case candidate ID (e.g. `back-squat`,
  `barbell-bench-press`).
- If an existing canonical ID matches via the exercise alias rules,
  use the existing one and record the alias mapping.
- If no match: this is a *candidate* and MUST appear in the
  Exercise Approval Table (Step 6) before finalization.

Never silently add a new exercise to `exerciseCatalog[]` without an
operator decision. The validation CLI flags unknown
`prescribedExerciseId` references via `exercise.unknown_reference`;
that issue stays critical until the operator approves the candidate or
maps it to an alias.

## Step 4 — Reference values (training maxes)

For each percent-based prescription (`targetKind: "percent"`):

- The percent target carries a `referenceId`, e.g. `tm-back-squat`.
- That reference must be declared in `requiredReferences[]`.
- Required references are emitted as `supplied: false` at import
  time. The runtime `ProgramResourceLoader` (Phase 4) injects the
  user's actual training-max / one-rep-max values at activation,
  flipping each to `supplied: true` with `value` + `unit`.
- First-week missing values: `reference.missing_first_week`,
  severity `critical`, BLOCKS activation at import time. This is
  EXPECTED for user-program imports — see "pending_runtime_references"
  below.
- Later-week missing values: `reference.missing_later_week`,
  severity `warning`. Activates, but Phase 4's runner must block the
  affected workout until the value is supplied.

### Step 4.1 — Do NOT capture pre-calculated weights

The workbook frequently shows a column with calculated weights
(e.g. "183 lb at 75% of 245") computed from the operator's own
TM via spreadsheet formulas. **Discard those calculated weights.**
Only capture the `percent` (or `percentMin`/`percentMax`) target
plus the `referenceId`. The runtime computes the weight from the
user's TM with the program's rounding rules — that's what makes
the program shareable across users with different maxes.

If the cell ONLY contains a calculated weight with no percent
visible (no formula trail, no header), use the orientation pass to
locate the percent column or row; if there genuinely is no percent,
encode as a literal `weightKg`/`weightLb` target with a
`source.formulaCell` audit reference.

### Step 4.2 — pending_runtime_references status

When the artifact passes everything else but the only remaining
criticals are `reference.missing_first_week` for `training_max` or
`one_rep_max` references with `supplied: false`, set

```json
"validationStatus": "pending_runtime_references"
```

instead of `"blocked"`. This is a distinct activation gate: the
operator has nothing further to iterate; the runtime supplies the
references at activation and re-validates.

Detection rule (applied at the end of Step 5, before validate):

1. Build the candidate JSON with all structural fixes applied.
2. If the declared `validationIssues` carry no criticals AND
   `requiredReferences[]` contains at least one entry with
   `referenceType` in `{training_max, one_rep_max}` and
   `supplied: false`, set
   `validationStatus = "pending_runtime_references"`.
3. Otherwise leave it `blocked` (or `activatable`).

The validator CLI surfaces this as `activationDecision:
pending_runtime_references` (exit code 1). Do NOT correction-loop
on `reference.missing_first_week` when this status is set; the
status itself is the answer.

## Step 5 — Compose JSON (deterministic IDs and ordering)

Compose the resource matching `schema/program-resource.schema.json`.
Use these deterministic rules so reruns produce the same structure:

| Field | Rule |
| --- | --- |
| Block IDs | `block-<n>` (1-indexed in workbook order) |
| Week IDs | `wk-<n>` (1-indexed, scoped to block) |
| Session IDs | `<weekId>-s<n>` (1-indexed within week) |
| Exercise group IDs | `<sessionId>-g<n>` (workbook order; ties → first appearance row, then column) |
| Prescription item IDs | `<groupId>-i<n>` |
| Set prescription IDs | `<itemId>-set<n>` |
| Exercise canonical ID | kebab-case of operator-approved display name; lowercase ASCII; spaces and `/` → `-`; consecutive `-` collapsed |
| Required reference ID | `tm-<exerciseId>` for training maxes; `ref-<slug>` for other references |
| Alias text source | `"operator"` if from operator input, `"source"` if derived from workbook |

Ordering ties are broken by (row, column, then insertion order).
Duplicate display names get a `-2`, `-3`, ... suffix at the operator's
explicit confirmation, never silently.

### Step 5.1 — Prescription target encoding (priority + conjunctive)

A single set may have multiple targets in the workbook (weight column +
%1RM column + RPE column). Pick the **most specific** target as the
authoritative driver, and **emit the others conjunctively** — never drop
the lower-priority companion.

Specificity, most → least:

1. **Actual prescribed weight**. If the cell carries an explicit weight
   that is NOT a formula derived from the user's TM, encode as a
   `weight` target with `value` (or `valueMin`/`valueMax` for a range)
   and `unit`.
2. **Percent of training max / one-rep max**. Encode as a `percent`
   target with `referenceId`. Single percent → `percent`. Range
   percent → `percentMin` + `percentMax` (mutually exclusive with
   `percent`; equal bounds collapse to `percent`).
3. **RPE / RIR**. Encode as an `rpe` or `rir` target with `target`.

When percent + RPE coexist (very common in autoregulated programs),
emit **both** targets on the same set:

```json
"targets": [
  { "kind": "percent", "percentMin": 75, "percentMax": 80, "referenceId": "tm-squat" },
  { "kind": "rpe", "target": 7.5 }
]
```

The runner displays calculated weight from the percent AND the RPE
companion. Dropping either is a data-loss bug.

### Step 5.2 — Cell parsing patterns (range recognition)

Don't collapse ranges to single values. Recognize these patterns:

| Cell text | Encoding |
| --- | --- |
| `75%`, `0.75`, `75` (in a %1RM column) | `percent: 75` |
| `75-80%`, `75%-80%`, `75 to 80%` | `percentMin: 75, percentMax: 80` |
| `5x5` | sets=5, reps=5 |
| `5x5+`, `5x5 AMRAP` (last set "+") | sets=5, reps=5, last set `setKind: "amrap"` |
| `5-8` reps | `repMin: 5, repMax: 8` |
| `3 min`, `180s` | `restSecondsHint: 180` |
| `3-4 min`, `2:30-3:00` | `restSecondsHint: 180, restMaxSecondsHint: 240` (schemaVersion 3) |
| `RPE 8`, `@8` | `kind: "rpe", target: 8` |
| `RPE 7-8`, `RPE 7.5` | `target: 7.5` (midpoint) or future range field |
| `Includes 3 warm-up sets` (or warm-up column) | `warmupSetCount: 3` on the item (schemaVersion 3) — NOT a free note |
| Tempo cue `3010`, `2-0-X-0` | `notes[]: { kind: "tempo", text: "..." }` (schemaVersion 1+) plus a `construct.tempo` warning per Step 2 |

### Step 5.3 — Week variants (e.g. 10A / 10B)

When the workbook numbers weeks with letter suffixes (`10A`, `10B`,
`Deload A`, etc.) it means the program offers parallel choices for that
week and the runner picks ONE at activation. Encode each variant as its
own week, with the second-and-later variants pointing to the first:

```json
{ "id": "wk-10a", "label": "Week 10A", "order": 10 },
{ "id": "wk-10b", "label": "Week 10B", "order": 10, "variantOf": "wk-10a", "variantLabel": "B" }
```

Detection rule: weeks whose source numbering shares the base integer
but differs by suffix (A/B/C, "Easy"/"Hard", "Deload" vs the prior
numbered week directly above) get grouped. The first one in document
order is the canonical week; the rest set `variantOf` to its id.

### Step 5.4 — schemaVersion selection (deterministic)

Pick the lowest schemaVersion that fits the resource's features:

| schemaVersion | Bump trigger (any of) |
| --- | --- |
| 1 | Default. No variants, no range targets, no rest range, no `warmupSetCount`. |
| 2 | Any week has `variantOf` set. No range/warmup fields. |
| 3 | Any `percentMin` / `percentMax` (range target), any `restMaxSecondsHint`, any `warmupSetCount`, or any conjunctive percent + RPE on the same set. (Variants may also be present; v3 supersedes v2.) |

Apply the bump after composing the resource, not before — walk the
structure once to detect the highest feature in use, then set both
`resource.schemaVersion` AND `resource.importAudit.schemaVersionUsed`
to the same value.

Compute `metadata.contentHash` via `schema/hash.ts`:

```ts
// One-off Node ESM snippet the developer can run locally. The candidate
// JSON lives at private/imports/<programVersionId>.json — the skill's
// canonical output location.
import { computeProgramResourceContentHash } from './schema/hash.ts';
const path = 'private/imports/<programVersionId>.json';
const resource = JSON.parse(readFileSync(path, 'utf-8'));
resource.metadata.contentHash =
  computeProgramResourceContentHash(resource);
writeFileSync(path, JSON.stringify(resource, null, 2));
```

The validator CLI in Step 7 recomputes and verifies this hash. Do
not trust a manually-pasted hash. The canonicalization excludes
`validationStatus`, `validationIssues`, `importAudit`, and
`metadata.contentHash` itself — so flipping status (Step 4.2) or
runtime ref injection does NOT change the hash.

## Step 6 — Exercise Approval Table

Before validating, surface this table to the operator and capture
decisions. Unresolved approvals block finalization.

| Candidate ID | Display name | First seen | Decision (approved / alias to <id> / rejected) | Notes |
| --- | --- | --- | --- | --- |

Record the decisions in two places:

- In the produced JSON: approved exercises become entries in
  `exerciseCatalog[]`; aliases are added to the canonical entry's
  `aliases[]` with `source: "operator"`.
- In the sidecar import-report: the full table with decisions and
  the operator's reasoning. This is the audit trail.

## Step 7 — Validate

Run from the repo root. The skill writes the candidate to
`private/imports/<programVersionId>.json` in Step 5; the validator reads
from the same path:

```powershell
cd schema
npm run validate:resource -- ..\private\imports\<programVersionId>.json
```

Exit codes:

| Exit | Decision | Meaning |
| --- | --- | --- |
| 0 | `activatable` | Ship it. |
| 1 | `activatable_with_warnings` | Ship; operator should see warnings. |
| 1 | `pending_runtime_references` | Ship; runtime injects the unsupplied training maxes at activation (see Step 4.2). Do NOT iterate on the `reference.missing_first_week` criticals — they are the gate, not a defect. |
| 2 | `blocked` | One or more critical issues OTHER than runtime-pending refs. Go to Step 8. |
| 3 | `rejected` | Schema-invalid. The structure itself is wrong. |
| 4 | CLI usage error / unreadable file | Fix the invocation. |

For the sidecar artifact, also run with `--json`. The output goes to the
same folder so the two artifacts stay paired:

```powershell
cd schema
npm run validate:resource -- ..\private\imports\<programVersionId>.json --json `
  > ..\private\imports\<programVersionId>.import-report.json
```

The `--json` form is the file you hand to the user alongside the
resource JSON. It already conforms to the privacy-safe report contract.

## Step 8 — Correction loop (conversational)

While the report shows critical issues OTHER than the runtime-pending
references handled in Step 4.2:

1. Read the report. For each critical issue:
   - Confirm the operator's intent (e.g. "Drop set in Week 3 isn't
     supported by MVP — replace with straight sets, omit, or annotate
     as note-only?").
   - Ask clarifying questions only when the report's `operatorAction`
     is ambiguous for this program.
2. Apply the fix by editing the JSON directly.
3. Recompute `contentHash` (Step 5 snippet).
4. Rerun the validator (Step 7).
5. Repeat until exit code is 0 (`activatable`) or 1
   (`activatable_with_warnings` or `pending_runtime_references`).

Do NOT iterate on:

- Warning-class construct issues (`construct.tempo`,
  `construct.rest_pause`, `construct.myo_reps`). They ship as warnings;
  the cue is preserved on `prescriptionItem.notes[].kind`.
- `reference.missing_first_week` criticals for `training_max` /
  `one_rep_max` references with `supplied: false`. They are the
  runtime-injection gate; the status flips to
  `pending_runtime_references` per Step 4.2.

Do not invent overrides. If a construct is critical per the matrix,
the operator must change the program or accept that it cannot
activate — there is no "reclassify as warning" path for matrix-critical
codes.

## Step 9 — Determinism check

After the final correction:

1. Run the validator twice in succession.
2. Confirm `metadata.contentHash` is identical on both runs.
3. Confirm both runs print the same activation decision and issue
   list (same codes, same counts).

If anything differs, there is non-determinism in your composition.
Common causes: an array sorted by Map insertion order, a timestamp
written inside the hashed body, ordering of an `additionalProperties:
false` object's keys leaking through `JSON.stringify`.

## Step 10 — Finalize

Both artifacts live in `private/imports/` already (written in Steps 5
and 7). Confirm the two files are present and exclusively these two:

- `private/imports/<programVersionId>.json`
- `private/imports/<programVersionId>.import-report.json`

Tell the operator:

- "Both files are in `private/imports/`. The folder is gitignored — they
  won't be committed accidentally."
- "Activation decision is <X>."
- "<N> warning(s) will surface in the run UI."
- "If you regenerate, the JSON will be byte-identical for the same
  inputs."
- "Phase 4's Android input endpoint will revalidate this file
  independently before accepting it. The sidecar report is operator
  audit evidence, not a substitute for that revalidation."

To commit either file (only acceptable for synthetic / non-proprietary
fixtures), the operator must add an explicit negation in `.gitignore`
per the rules in `private/imports/README.md`. The default posture is
**do not commit anything from `private/imports/`**.

## What this skill does NOT do

- It does not build an Android UI to load the JSON. Phase 4 owns that
  (`program-resources` workstream).
- It does not maintain a TypeScript runtime importer. There is none.
- It does not auto-approve exercises, auto-classify constructs, or
  silently override matrix severities.
- It does not commit fixtures by default.
- It does not call an LLM endpoint — the Copilot session running this
  skill IS the cloud-assisted processor.

## Verification

After completing the procedure, the verification-loop skill's
"Import report validation" row is satisfied by:

```powershell
cd schema; npm run validate:resource -- ..\private\imports\<programVersionId>.json
```

returning exit code 0 (`activatable`) or 1
(`activatable_with_warnings` or `pending_runtime_references`) on
the final artifact.
