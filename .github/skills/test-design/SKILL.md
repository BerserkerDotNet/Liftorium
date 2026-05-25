---
name: test-design
description: Use when about to add a new test file, a new top-level `describe` block or test class, or substantially expand the test matrix for a behavioral branch in Liftorium domain or data logic (Android `:core`/`:data`/`:domain`, Web `src/data`/`src/domain`, schema validators/semantics/hash). Orchestrates parallel sub-agent brainstorm across the relevant scenario dimensions, then a rubber-duck consolidation, producing a concrete test matrix BEFORE any test code is written. Do NOT use for trivial test edits (renames, formatting, fixing one missing assertion, updating expected strings); UI snapshot/Paparazzi tests — use `android-implementation` or `web-implementation`; or acceptance-traceability and runtime-evidence audit at phase completion — use `coverage-review`. Precedence when in doubt: test-design runs BEFORE implementation, the `*-implementation` skills run DURING implementation and own UI tests, `coverage-review` runs AFTER implementation and owns traceability + percentage gate audit.
---

# Test Design

Use this skill to design a concrete, high-coverage test matrix BEFORE writing test code for non-trivial Liftorium domain or data logic. The skill orchestrates parallel `explore` sub-agents (one per selected scenario dimension), then a single `rubber-duck` consolidation, producing a reviewable matrix that maps every test back to an acceptance scenario ID.

The matrix is the deliverable. Test code is written by the relevant `*-implementation` skill or by Copilot directly, against the matrix.

## When to invoke vs related skills

| Situation | Skill |
| --- | --- |
| About to add a new test file, new `describe`/test class, or substantially expand a behavioral test matrix | **test-design** (this skill) |
| Trivial test edits: renames, formatting, fixing one missing assertion, updating an expected string | None — make the edit directly |
| UI snapshot tests (Paparazzi/Robolectric on `:app`, React component tests on Web UI) | `android-implementation` / `web-implementation` (UI is outside the coverage gate; snapshot framing differs) |
| Final phase/feature acceptance review — traceability hygiene, runtime evidence, percentage gate audit | `coverage-review` |

If two or more apply, run test-design FIRST, then the implementation skill, then coverage-review.

## Inputs

Gather before starting:

1. Source file path(s) and the specific function/class/module under test.
2. The explicit contract: inputs, outputs, side effects, invariants, error conditions. Cite documented contracts (KDoc/JSDoc comments, schema docs, `docs/architecture.md` sections).
3. Acceptance scenario IDs from `docs/product.md` that this code serves. If none, pause and ask the user whether to add one before continuing.
4. Known prior regressions or bug reports for this code (these become required test cases).
5. The platform appendix (Android / Web / Schema — see below).

## Procedure (six steps)

### Step 1 — Frame the code under test

Write a short framing block (in chat, not committed) that captures:

- The unit (path + symbol).
- Its inputs, outputs, side effects, and explicit contract.
- The acceptance scenario IDs it serves (or "NO acceptance ID — paused for user confirmation").
- Any known prior regressions, listed as required test cases.
- Which **3–4 dimensions** (from the 8 listed in Step 2) are most relevant, and a one-line reason WHY each was selected and each unselected one was not.

This framing is the shared context every sub-agent receives.

### Step 2 — Select 3–4 dimensions for parallel exploration

Eight scenario dimensions. Copilot picks the 3–4 most relevant for the code under test:

1. **Happy path / typical inputs.** Canonical inputs producing canonical outputs.
2. **Equivalence classes.** Input domain partitioning — for each class, one representative case.
3. **Boundary conditions.** Min/max, zero/one/many, off-by-one, empty collections, just-over/just-under limits, first/last element, single-element collection, exhausted iterator.
4. **Error and invalid-input paths.** Null/undefined/missing, type violations, malformed data, schema violations, exceptions, defensive guards as defense-in-depth (test the behavior of short-circuiting on schema-invalid input).
5. **Concurrency, timing, and state ordering.** Room transactions, coroutines/Flow ordering, timer behaviors, idempotency, retry, race conditions, scheduler interleavings.
6. **Security and privacy.** Consent gates, source-data leakage, PII redaction, log hygiene, no formula/cell text in errors, no private workout source content in evidence or error messages.
7. **Integration contracts.** Producer/consumer wiring across components, migration shape, event ordering, hash round-trip stability, dependency injection seams.
8. **Invariants, properties, metamorphic behavior.** Ordering invariance, idempotence, round-trip stability ("same content different metadata = same hash"), monotonicity, commutativity, associativity, conservation laws (e.g. total volume unchanged after permutation).

All 8 are still **considered** during Step 4 consolidation — selection narrows the parallel exploration but does not waive any dimension.

### Step 3 — Spawn parallel `explore` sub-agents

One `explore` agent per selected dimension, launched in parallel (single response with multiple `task` tool calls). Each agent receives:

- The Step 1 framing.
- Its specific dimension prompt (template in **Sub-agent prompt templates** below).
- Source file paths, line ranges, and any relevant docs paths (not the file contents — let the agent read them).
- Privacy guardrail: "do not echo private workout source content; cite paths and line numbers".

Each agent returns a list of concrete test cases: name, inputs, expected outcome, rationale.

### Step 4 — Consolidate with one `rubber-duck`

Send all dimension outputs plus the Step 1 framing AND the full 8-dimension checklist to a single `rubber-duck` sub-agent. The duck must:

- **Deduplicate** identical or near-identical cases.
- **Flag gaps** in any of the 8 dimensions — even the unselected ones — and add cases where appropriate.
- **Reject** tests that assert implementation details rather than observable behavior.
- **Reject** tests that would pass vacuously (e.g. asserting `toBeDefined()` on an always-defined value).
- **Classify each case** as `unit`, `integration`, or `runtime-evidence required`.
- **Assign a traceability ID** to each case (existing acceptance test ID where it maps; otherwise propose a new ID and flag for user confirmation).
- **Call out** any case that would require a new fixture, mock, or test harness — these need extra approval before implementation.

### Step 5 — Produce the test matrix

Output (in chat, not committed) a markdown table:

| ID | Layer | Dimension | Inputs | Expected outcome | Acceptance ID |
| --- | --- | --- | --- | --- | --- |

Present to the user. Wait for confirmation or edits before any test code is written.

### Step 6 — Implement, then verify coverage

After tests are written and pass:

1. Run the platform's coverage command (see appendices).
2. Confirm ≥95% on all available metrics for the new file(s):
   - **Schema / Web (vitest):** lines, branches, statements, functions — all four.
   - **Android (Kover):** lines, branches, instructions — three (Kover has no METHOD counter; instructions effectively cover uncovered methods because their bytecode goes uncovered).
3. If any metric falls short:
   - Go back to **Step 4** and consolidate missing cases. **DO NOT** lower the gate or add wholesale file exclusions.
   - Line-level ignore directives are allowed only per the Exclusion Contract in `docs/decisions.md` (genuinely unreachable code, paired with rationale comment).

## Sub-agent prompt templates

Copy-pasteable. Replace `{...}` placeholders.

### Explore agent prompt template

```
You are an explore sub-agent for the Liftorium test-design skill.

CODE UNDER TEST
- Path: {path}
- Symbol: {function/class/module}
- Contract: {inputs/outputs/side effects from Step 1 framing}
- Acceptance IDs served: {ids}
- Known prior regressions: {list or "none"}

YOUR DIMENSION: {one of: happy-path / equivalence / boundary / error-and-invalid / concurrency-timing / security-privacy / integration-contracts / invariants-and-properties}

Read the source and surrounding tests. Return a list of concrete test cases for THIS DIMENSION ONLY. Each case:
  - name: short description in present tense ("rejects empty exerciseId")
  - inputs: literal values or a precise description
  - expected outcome: assertion(s) only, no implementation details
  - rationale: one sentence on why this case is necessary

Rules:
  - Do not echo private workout source content.
  - Do not propose tests that assert implementation details (private fields, internal call counts).
  - Do not propose vacuous tests (e.g. "result is defined").
  - Cite file paths and line numbers when referring to existing code.
  - If the dimension does not apply to this code, return "NOT APPLICABLE" with a one-sentence explanation.
```

### Rubber-duck consolidation prompt template

```
You are the consolidation rubber-duck for the Liftorium test-design skill.

CONTEXT
- Step 1 framing: {paste}
- Selected dimensions and outputs:
  - {dimension 1}: {paste cases}
  - {dimension 2}: {paste cases}
  - {dimension 3}: {paste cases}
  - {dimension 4}: {paste cases}
- The full 8-dimension checklist: happy-path, equivalence, boundary, error-and-invalid, concurrency-timing, security-privacy, integration-contracts, invariants-and-properties.

YOUR TASK
1. Deduplicate identical or near-identical cases across dimensions.
2. Flag gaps in any of the 8 dimensions — even unselected ones — and propose additions.
3. Reject tests that assert implementation details. Replace with behavioral assertions if possible.
4. Reject tests that pass vacuously.
5. Classify each case as `unit` / `integration` / `runtime-evidence required`.
6. Assign a traceability ID. Reuse an existing acceptance test ID from docs/product.md where possible; otherwise propose a new ID prefixed `TBD-` and flag for user confirmation.
7. Call out cases that require new fixtures, mocks, or harnesses — these need approval before implementation.

Output a markdown table:
  | ID | Layer | Dimension | Inputs | Expected outcome | Acceptance ID |

Followed by:
  - A "Gaps closed" list (additions you made for unselected dimensions).
  - A "Needs user confirmation" list (new acceptance IDs, new fixtures, new harnesses).
  - A "Rejected" list (cases dropped, with reasons).
```

## Appendix A — Android (Kover)

**Module placement.** Tests for the gate live in `android/:core`, `android/:data`, or `android/:domain`. **NEVER** in `:app` — `:app` is excluded from the gate because UI and framework glue live there.

**Banned dependencies in `:domain`.** Android framework types, Room, Compose runtime, AndroidX lifecycle. The `DomainArchitectureGuardTest` mechanically enforces this. If your test needs one of these, the code under test belongs in `:data` or `:app`, not `:domain`.

**Frameworks.** JUnit 4 + `kotlin-test-junit` + `kotlinx-coroutines-test`. Truth is optional but allowed.

**Counter mapping — Kover is NOT semantically equivalent to vitest.** Kover 0.8.3 supports three counters:

- `LINE` — source lines (≈ vitest "lines").
- `BRANCH` — control-flow branches (≈ vitest "branches").
- `INSTRUCTION` — JVM bytecode instructions. NOT source statements; Kotlin emits extra instructions for default args, sealed bridges, synthetic accessors, etc. A method with no test invocations contributes ALL its instructions to the missed pool, so INSTRUCTION coverage effectively catches uncovered methods.

Kover has **NO METHOD counter**. The vitest "functions" metric is approximated by INSTRUCTION. This divergence is intentional and documented in `docs/decisions.md` (Exclusion Contract).

**Behaviorless-model code.** Data classes, sealed result classes, DTOs, Room `@Entity` data classes, value classes whose members are exclusively compiler-generated may be excluded per the Exclusion Contract. Today this is done in `android/build.gradle.kts` via class-name patterns; future option is a repo-local `@KoverIgnore` annotation declared in `:core`. **Don't pad coverage by writing tests for compiler-generated `equals`/`hashCode`/`copy`** — exclude instead.

**Paparazzi/Robolectric are orthogonal.** Paparazzi tests live in `:app` (excluded from the gate). They do NOT contribute to `:core`/`:data`/`:domain` coverage. Domain/data behavior MUST have its own JVM unit tests in the respective module — Paparazzi cannot substitute.

**Run the gate locally.**

```
cd android
.\gradlew.bat koverVerify --console=plain
```

Reports written to `android/build/reports/kover/`. Use `.\gradlew.bat koverHtmlReport` for a clickable report; use `.\gradlew.bat koverLog` for a quick numeric printout per module.

**Run a single module's coverage.**

```
cd android
.\gradlew.bat :core:koverVerify --console=plain
.\gradlew.bat :data:koverVerify --console=plain
.\gradlew.bat :domain:koverVerify --console=plain
```

**Defensive guards.** When a defensive guard exists (e.g. an early-return on schema-invalid input), test the BEHAVIOR of short-circuiting (feed bad input, assert clean exit) rather than excluding the line. This is defense-in-depth, not coverage busywork. The schema package uses this pattern extensively (`schema/semantics.ts` lines 39–43 explicitly contract "may run on schema-invalid input").

## Appendix B — Web (vitest)

**Source placement.** Coverage targets are `web/src/data/**` and `web/src/domain/**`. UI components (`src/components/**`, `src/pages/**`, `App.tsx`, `main.tsx`) are excluded by config and tested via React Testing Library snapshot/render tests (see `web-implementation`) which do NOT contribute to the gate.

**Frameworks.** vitest + `@testing-library/react` for any domain hooks that touch React; plain vitest for pure logic.

**Read-only constraint.** Web MVP is read-only. Do not introduce tests that exercise mutation paths, IndexedDB writes, service workers, or protected-domain capability copy. See `web-readonly-guard`.

**Vacuous-pass guard.** `web/scripts/coverage-guard.mjs` runs before `vitest --coverage`:

- If neither `src/data/` nor `src/domain/` exists → exit 0 with "no coverage targets yet" message. (import-workflow baseline.)
- If either exists but contains zero source files → **exit 1** (gate has been silently neutered).
- Otherwise → exec `vitest --coverage`.

This prevents `include: ['src/data/**', 'src/domain/**']` from passing vacuously when a typo or folder rename removes all measured files.

**Run the gate locally.**

```
cd web
npm run test:coverage
```

Reports written to `web/coverage/`. The text summary prints to stdout; `coverage/coverage-summary.json` is the machine-readable form; open `coverage/index.html` for the clickable report.

**Ignore directive.** `/* v8 ignore next */` (NOT c8) on the line above the unreachable code, paired with a rationale comment. Vitest uses the v8 provider, not c8.

## Appendix C — Schema (vitest + Ajv)

**Source placement.** Coverage targets are `schema/validator.ts`, `schema/semantics.ts`, `schema/hash.ts`. Excludes: `schema/scripts/**` (CLI helpers), `schema/fixtures/**`, `schema/test/**`, `schema/program-resource.schema.json`, config files.

**Frameworks.** vitest + Ajv. Fixtures under `schema/fixtures/` double as a behavioral test matrix — adding a new fixture often adds coverage for free.

**Defensive guards in `schema/semantics.ts`.** The module's explicit contract (lines 39–43) is "may run on schema-invalid input". Every defensive guard is therefore tested as defense-in-depth: feed malformed input (null entries, missing required keys, wrong types), assert the guard short-circuits cleanly with the expected validation issue or graceful early return. These tests are cheap and load-bearing — they prove the contract.

**Hash invariants.** `schema/hash.ts` is a prime candidate for **dimension 8 (invariants / properties / metamorphic)**:

- Ordering invariance: shuffling object key insertion order MUST NOT change the hash.
- Round-trip stability: parse-then-serialize-then-hash MUST equal hash-of-original.
- Metadata independence: changing only metadata fields (already excluded by `excludePaths`) MUST NOT change the hash.

When designing tests for hash-related code, the rubber-duck SHOULD flag if invariants are missing.

**Fixture-hash discipline.** If your test fixtures live under `schema/fixtures/` or `schema/examples/`, run `npm run refresh-fixture-hashes` BEFORE `npm test`. Otherwise the hash-freshness assertions in the existing test suite will fail.

**End-to-end CLI verification.**

```
cd schema
npm run validate:resource -- <path-to-resource>
```

Runs the published validator binary against a real resource file. Use after integration changes to confirm the CLI surface still works.

**Run the gate locally.**

```
cd schema
npm run test:coverage
```

Reports written to `schema/coverage/`. The text summary prints to stdout; `coverage/coverage-summary.json` is machine-readable; `coverage/index.html` is clickable.

**Ignore directive.** `/* v8 ignore next */` paired with a rationale comment. Use only for genuinely unreachable code (e.g. exhaustive switch default arms, `throw new Error("unreachable")`). A single missed branch never justifies whole-file exclusion — prefer refactor or directive-with-rationale.

## Output

This skill produces:

1. The Step 1 framing (in chat, not committed).
2. The Step 5 test matrix table (in chat, not committed) — user confirms before tests are written.
3. Step 6 coverage numbers after implementation, demonstrating ≥95% on every available metric for the new file(s).

Do NOT commit raw sub-agent transcripts. Persist only durable project changes: acceptance ID additions in `docs/product.md` (with user approval), decisions in `docs/decisions.md`, command updates in `verification-loop`.

## Rules

- Do not write test code before producing and confirming a test matrix.
- Do not lower the coverage gate or add whole-file exclusions to make a failing run pass — go back to Step 4.
- Do not propose tests that echo private workout source content.
- Do not propose tests that assert implementation details.
- Do not skip the rubber-duck consolidation step; it is the dedup, gap-closing, and traceability layer.
