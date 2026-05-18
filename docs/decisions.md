# Decisions Log

Use this file to ground future Copilot actions. Every important architecture or business decision must include context, decision, rationale, alternatives considered, consequences, and related tests/docs/code.

## Decision template

### YYYY-MM-DD: Short decision title

- Status: proposed | accepted | superseded
- Context:
- Decision:
- Rationale:
- Alternatives considered:
- Consequences:
- Related docs/tests/code:

## 2026-05-15: Native Android primary and Web secondary

- Status: accepted
- Context: The product must support reliable offline workout logging and Android locked-phone timer alerts. Web storage and timer behavior cannot provide equal guarantees.
- Decision: Build native Android as the primary MVP surface and a separate responsive Web app as a secondary online-only read-only surface.
- Rationale: Android can provide stronger local persistence, Room migrations, foreground-service timer behavior, and runtime verification for gym use.
- Alternatives considered: shared Flutter app, React Native/Expo, PWA-first, equal Android/Web MVP parity.
- Consequences: Android acceptance scenarios drive MVP completion. Web cannot mutate workout data in MVP.
- Related docs/tests/code: `docs/architecture.md`, `docs/product.md`, Android runtime/E2E tests, Web read-only mutation tests.

## 2026-05-15: Android stack

- Status: accepted
- Context: Android is the primary app and must support local-first durable storage and modern mobile UI.
- Decision: Use Kotlin, Jetpack Compose, Room/SQLite, Android 14/API 34+.
- Rationale: This stack supports native UI, strong persistence, migration testing, foreground services, and modern Android APIs.
- Alternatives considered: Kotlin Views, Java/mixed Android, shared cross-platform stack.
- Consequences: Android project setup must include Room migration tests and Compose UI/runtime test foundations.
- Related docs/tests/code: `docs/architecture.md`, `docs/testing-strategy.md`.

## 2026-05-15: Web stack and scope

- Status: accepted
- Context: Web is required but is not the primary gym workout surface.
- Decision: Use React, TypeScript, and Vite for an online-only responsive read-only Web app.
- Rationale: Keeps Web implementation lightweight while avoiding unsupported offline/timer parity.
- Alternatives considered: Next.js, SvelteKit, PWA-first, Web workout logging.
- Consequences: Web MVP cannot create or mutate workout, timer, substitution, training max, or program-run state.
- Related docs/tests/code: `docs/architecture.md`, Web read-only mutation tests.

## 2026-05-15: Full P0 except export

- Status: accepted
- Context: The PRD P0 scope is broad, and the user explicitly selected full P0 while removing export from MVP.
- Decision: Implement remaining P0 acceptance scenarios; exportable workout history is not MVP.
- Rationale: The user wants the credible MVP to cover the full product promise except export.
- Alternatives considered: staged vertical-slice first beta, smaller scope, PRD P0 with export.
- Consequences: Scope is large. Phase milestones reduce risk, but MVP is not complete until remaining acceptance scenarios pass.
- Related docs/tests/code: `docs/product.md`, acceptance tests.

## 2026-05-15: Spreadsheet-only import and versioned JSON resources

- Status: accepted
- Context: Program import is developer/operator-facing in MVP, with real samples represented by spreadsheet workbooks and PDFs.
- Decision: MVP importer is spreadsheet-only and outputs versioned JSON resources. PDF/manual import is out of MVP.
- Rationale: Spreadsheet import is the highest-value first import path and directly feeds the Android app runner.
- Alternatives considered: PDF-assisted import, hand-authored resources only, SQLite seed packages.
- Consequences: Program resources need schema versioning and validation. PDF content cannot be required to run MVP workouts.
- Related docs/tests/code: `docs/architecture.md`, import validation tests.

## 2026-05-15: Cloud-assisted import with explicit consent

- Status: accepted
- Context: Source program materials can contain private or proprietary text.
- Decision: Cloud-assisted Copilot processing is allowed only after explicit per-import consent.
- Rationale: The workflow can benefit from AI assistance while making the privacy boundary explicit.
- Alternatives considered: local-only processing, metadata-only cloud processing, undecided import privacy.
- Consequences: Import workflow must include consent wording and provenance rules before source-derived content is processed.
- Related docs/tests/code: `docs/architecture.md`, import consent tests.

## 2026-05-15: Unsupported import constructs block activation

- Status: accepted
- Context: The app must preserve program intent and avoid silently flattening complex prescriptions.
- Decision: Imported constructs must be classified as structured, note-only, or critical. Critical issues block activation.
- Rationale: Running ambiguous programs in the gym would undermine data trust and program fidelity.
- Alternatives considered: warning-only preservation, operator-decides per issue, schema expansion before every activation.
- Consequences: The Program Construct Matrix is a Phase 0 gate.
- Related docs/tests/code: `docs/architecture.md`, import validation tests.

## 2026-05-15: Android locked-phone rest timer alerts

- Status: accepted
- Context: User selected locked-phone timer alerts as required for Android MVP.
- Decision: Implement Android foreground-service/notification path for locked-phone timer alerts. If notification permission is denied, timer start is blocked; workout logging remains usable.
- Rationale: Timer promises must be honest and verifiable without blocking core workout logging.
- Alternatives considered: foreground-only timer, best-effort background notifications, Web locked alerts.
- Consequences: Timer service behavior and permission gating need runtime tests on Android 14/API 34+.
- Related docs/tests/code: `docs/architecture.md`, Android timer runtime tests.

## 2026-05-15: Testing and critique gate

- Status: accepted
- Context: Copilot actions must be verifiable, and unit/integration coverage must not drift from contracts.
- Decision: Every implementation change requires relevant tests. Major phase test coverage must be reviewed by both a critique agent and a rubber-duck pass before acceptance.
- Rationale: Tests are the executable spec and reduce divergence across Copilot-driven work.
- Alternatives considered: ad hoc tests, final-only QA, code review without runtime verification.
- Consequences: Each phase must plan tests first or alongside implementation.
- Related docs/tests/code: `docs/testing-strategy.md`.

## 2026-05-15: Project skills for repetitive Copilot workflows

- Status: accepted
- Context: Repetitive Copilot actions should not rely on ad hoc prompts or one-off commands because that causes divergence across phases.
- Decision: Encode repetitive Liftorium workflows as GitHub Copilot project agent skills under `.github/skills/<skill-name>/SKILL.md`.
- Rationale: Agent skills are the agreed and documented abstraction for repeatable Copilot behavior in this project.
- Alternatives considered: ad hoc prompts, user-scoped automation, bespoke CLI tools outside the skill workflow.
- Consequences: Copilot should auto-discover project skills from their descriptions for repeated workflows such as validation, test execution, and coverage review. Skills must be real `SKILL.md` files with frontmatter under `.github/skills/`.
- Related docs/tests/code: `.github/skills/`.

## 2026-05-16: Phase 1 Android SDK targets and JDK toolchain

- Status: accepted
- Context: Foundation workstream requires recording one-time Android setup decisions in `docs/decisions.md` before Android scaffolding lands. Decisions.md previously fixed "Android 14 / API 34+" for runtime; the remaining values (`minSdk`, `compileSdk`, `targetSdk`, Gradle JDK toolchain) still needed to be locked.
- Decision: `minSdk = 30` (Android 11), `compileSdk = 34`, `targetSdk = 34`. Gradle JDK toolchain pinned to JDK 21 via `jvmToolchain(21)`. Gradle itself must run on JDK 21 to keep AGP 8.7 off whatever JDK happens to be on `PATH`; each contributor is responsible for ensuring Gradle launches under JDK 21 (preferred: set `org.gradle.java.home` in `$GRADLE_USER_HOME/gradle.properties`, or set `JAVA_HOME` before invoking `./gradlew`). The repo intentionally does not commit a machine-specific path in `android/gradle.properties`.
- Rationale: API 30+ covers the vast majority of active Android devices in 2026 while letting us use modern platform APIs without compatibility shims. JDK 21 is the long-term-supported toolchain that AGP 8.7 and Kotlin 2.0 both validate against; pinning `org.gradle.java.home` avoids the silent "wrong JDK on PATH" failure mode that AGP 8.x is known for.
- Alternatives considered: `minSdk = 24` (broader reach, but forces desugaring complexity for time APIs and unused on the target user base), `minSdk = 26`, JDK 17 toolchain (works but matures slower for Kotlin 2.x and Compose Compiler), leaving JDK selection to `PATH` (rejected — produces inconsistent builds across machines).
- Consequences: Android features may freely use Java 8+ time APIs (`java.time.*`) without core-library desugaring. Contributors must have JDK 21 installed and ensure Gradle launches under it (via `$GRADLE_USER_HOME/gradle.properties` or `JAVA_HOME`); the repo no longer pins a machine-specific path. API 34 platform + build-tools 34 must be installed via the Android SDK Manager before `assembleDebug`, `testDebugUnitTest`, or `connectedDebugAndroidTest` can succeed. The Kotlin/Java *bytecode target* on Android modules is pinned to JVM 17 (`compileOptions` + `kotlin.compilerOptions.jvmTarget = JVM_17`) even though the toolchain JDK is 21, because AGP 8.7 + D8 do not support JVM 21 bytecode for Android targets yet. Pure JVM modules (`:core`, `:domain`) compile at JVM 21.
- Related docs/tests/code: `android/gradle.properties`, `android/gradle/libs.versions.toml`, `android/build.gradle.kts`, `.github/skills/verification-loop/SKILL.md`.

## 2026-05-16: Phase 1 Android module layout (coarse)

- Status: accepted
- Context: The architecture document describes a fine-grained module map (`:core-schema`, `:core-time`, `:data-room`, `:domain-program`, `:domain-workout`, multiple `:feature-*` modules). Splitting into that many empty modules at scaffold time creates churn before any real code exists.
- Decision: Phase 1 ships a coarse four-module layout under `android/`: `:app` (`com.android.application`, Compose entry point), `:core` (pure `org.jetbrains.kotlin.jvm` shared utilities), `:data` (`com.android.library`, owns Room and `schemas/` export), `:domain` (pure `org.jetbrains.kotlin.jvm`, framework-free domain models and repository interfaces). Subsequent workstreams may split `:core` into `:core-schema` / `:core-time`, `:data` into `:data-room` / others, and `:domain` into per-feature domain modules as their code lands.
- Rationale: Pure-JVM `:domain` and `:core` modules mechanically prevent Android, Room, and Compose types from leaking into framework-independent code — the import simply does not resolve. A coarse start lets each downstream workstream introduce its own module split with real motivation rather than pre-committing to empty modules.
- Alternatives considered: Full architecture-map split at scaffold time (rejected as premature), single Android library module (rejected — domain code would lose its mechanical framework-independence guarantee).
- Consequences: Downstream workstreams own their own module-splitting work. The dependency direction (`:app` → `:data` and `:domain`; `:data` → `:domain`, `:core`; `:domain` → `:core` only) must be preserved when splits happen. The Phase 1 `:domain` module includes a mechanical guard unit test that fails if Android, Room, or Compose runtime classes are resolvable from the `:domain` classpath; future splits must preserve an equivalent guard for every framework-free module.
- Related docs/tests/code: `android/settings.gradle.kts`, `android/domain/src/test/kotlin/`, `docs/architecture.md` (module map).

## 2026-05-16: Phase 1 Room schema export path

- Status: accepted
- Context: `docs/architecture.md` and `.github/copilot-instructions.md` both require exporting Room schemas from the first schema and never using destructive migrations.
- Decision: Room schemas are exported to `android/data/schemas/` via the KSP argument `room.schemaLocation`. This folder is checked into source control. Every Room database class in `:data` must keep `exportSchema = true`.
- Rationale: Co-locating exported schemas with the module that owns them keeps migration tests in the same module that defines the `@Database`. Source-controlled schema JSON is the input to migration regression tests in every later workstream.
- Alternatives considered: `android/schemas/` at the Android-project root (rejected — couples a Room-specific path to the Android root), suppressing schema export (rejected — directly violates the destructive-migration prohibition).
- Consequences: A `:data` Room migration test command is registered as gated until the first `@Database` exists. Every Room schema bump must include a checked-in migration plus updated schema JSON. Schema folder may be empty during Phase 1 (no entities yet); that is documented as gated, not as evidence.
- Related docs/tests/code: `android/data/build.gradle.kts`, `android/data/schemas/`, `.github/skills/verification-loop/SKILL.md`.

## 2026-05-16: Phase 1 Web stack scaffold

- Status: accepted
- Context: `docs/decisions.md` already accepts React + TypeScript + Vite for the Web MVP. Phase 1 needs the concrete tooling stack pinned so verification commands are real.
- Decision: Web project under `web/` is scaffolded with Vite + React + TypeScript strict (`strict: true`, `noUncheckedIndexedAccess: true`), Vitest + jsdom + React Testing Library for unit/component tests, and no Playwright in Phase 1. Runtime validation of snapshot/resource inputs will be added when Web data clients are introduced (Phase 10); the read-only guard verification command is therefore registered as gated until Phase 10.
- Rationale: Vitest covers all current testing needs (no Web data clients exist yet); deferring Playwright avoids committing to a browser test runtime before there are real user flows to test. `noUncheckedIndexedAccess` matches the "avoid `any`; narrow `unknown`" guidance in `.github/copilot-instructions.md`.
- Alternatives considered: Vitest + Playwright in Phase 1 (rejected — no Web UI to exercise), Jest instead of Vitest (rejected — slower, doesn't share Vite's transform pipeline), TS strict mode without `noUncheckedIndexedAccess` (rejected — weaker guarantees for snapshot/resource consumers).
- Consequences: Web typecheck, Vitest, and production build are registered as passing now. The read-only guard is registered as gated until the first Web data client exists.
- Related docs/tests/code: `web/package.json`, `web/tsconfig.json`, `web/vitest.config.ts`, `.github/skills/verification-loop/SKILL.md`.

## 2026-05-16: Phase 1 schema validator runtime

- Status: accepted
- Context: `schema/` will house the versioned program-resource JSON Schema and fixtures starting in Phase 2. Phase 1 must provide a runnable validation harness so Phase 2 has an existing place to drop the real schema and reports.
- Decision: Schema validation runs in Node via [Ajv](https://ajv.js.org/) (`ajv` + `ajv-formats`) plus Vitest. The harness lives under `schema/` with its own `package.json`. The Phase 1 schema is a real skeleton encoding the contract fields (`schemaVersion`, `programId`, `programVersionId`, `versionLabel`, `validationStatus`, `validationIssues[]`, `importAudit`); Phase 2 will expand it with full program structure.
- Rationale: Node + Ajv keeps the toolchain unified with the Web project (no Python prerequisite). Vitest gives consistent test semantics across both Web and schema validators. A real (not vacuous) skeleton means Phase 2 cannot accidentally regress the activation contract.
- Alternatives considered: Python + jsonschema (rejected — adds a second runtime), no schema scaffold this phase (rejected — file-presence verifier failure mode).
- Consequences: `schema/` has its own `package.json` and `npm test`. Phase 2 expands the schema and fixtures inside the same harness. Schema validation is registered as passing now.
- Related docs/tests/code: `schema/program-resource.schema.json`, `schema/fixtures/`, `schema/package.json`, `.github/skills/verification-loop/SKILL.md`.

## 2026-05-16: Phase 1 DI framework and foreground service type deferred

- Status: accepted
- Context: The android-implementation skill lists "foreground service type for rest timers" and "dependency composition mechanism" among one-time setup decisions. Phase 1 does not exercise either: there is no rest timer code yet (Phase 8 owns it) and no service/use case wiring beyond an empty `App` composable.
- Decision: Phase 1 does NOT pick a DI framework. Manual dependency composition (factories / hand-wired) is the default until a later workstream proposes a framework. Phase 1 does NOT pick a foreground-service type for rest timers; that decision is deferred to the `android-rest-timers` workstream.
- Rationale: Locking these in before any consumer code exists invites churn. The current code surface (an empty `App` composable + a Room scaffold with no `@Database`) needs neither.
- Alternatives considered: Pre-committing to Hilt for DI (rejected — premature), pre-picking `mediaPlayback` / `dataSync` foreground service type (rejected — out of scope for foundation).
- Consequences: Phase 8 must open the foreground-service-type decision before timer implementation lands. Whichever workstream first needs cross-module dependency wiring beyond constructor arguments must record a DI decision before adding a framework.
- Related docs/tests/code: `docs/workstreams/android-rest-timers.md`, `.github/skills/android-implementation/SKILL.md`.

## 2026-05-16: Host-side UI rendering tests adopted (Robolectric + Paparazzi)

- Status: accepted
- Context: Foundation phase had no UI runtime evidence beyond build/test pipeline artifacts (APK, JUnit XML, lint HTML). UI work in later phases needs fast, no-emulator feedback that produces both behavior evidence (semantics tree) and visual evidence (rendered PNG) for agent review. Real-device instrumentation is gated on a device/emulator and is too slow for inner-loop UI iteration.
- Decision: Adopt two complementary host-side test stacks on `:app`:
  - **Robolectric** 4.14 + `androidx.compose.ui:ui-test-junit4` for Compose **behavior / semantics** tests. Tests drive state with `performClick()`, assert on the semantics tree via `onNodeWithText().assertIsDisplayed()`, and run under `:app:testDebugUnitTest`.
  - **Paparazzi** 1.3.5 for Compose **visual snapshots** via LayoutLib. PNGs generated by `:app:recordPaparazziDebug`. Artifact-only mode (no committed golden baselines for MVP); the agent regenerates PNGs each run and reviews them with the `view` tool.
- Rationale: Robolectric covers interactive behavior; Paparazzi covers visual fidelity through a different renderer (LayoutLib vs. host-side Skia). Two renderers reduce the risk of either being silently wrong. `captureToImage()` under Robolectric proved unreliable in the spike (`ComposeTimeoutException` on `waitForIdle`); Paparazzi handled screenshot capture cleanly, so the Robolectric-screenshot variant was dropped to avoid pulling in a third library (Roborazzi) without a justifying use case.
- Alternatives considered: Robolectric-only (rejected — loses LayoutLib fidelity), Paparazzi-only (rejected — Paparazzi snapshots are static; no interactive flow driving), Roborazzi added so Robolectric `captureToImage()` works (rejected — adds a third library with no value Paparazzi doesn't already provide), committing Paparazzi golden baselines (deferred — useful for visual regression but inflates repo size; revisit when UI surface is large).
- Consequences:
  - `:app` gains test dependencies on Robolectric, `androidx.test:core-ktx`, `androidx.test.ext:junit-ktx`, and `androidx.compose.ui:ui-test-junit4` (added to test source set in addition to existing androidTest).
  - `:app` applies the `app.cash.paparazzi` Gradle plugin.
  - `android/app/src/test/snapshots/` is gitignored (Paparazzi golden output location, treated as build artifact).
  - `android/app/build/reports/paparazzi/debug/` holds the Paparazzi HTML report and copy of each PNG.
  - `:app:testDebugUnitTest` now includes Robolectric Compose render tests AND Paparazzi snapshot verification — Paparazzi tests pass in verify mode against the goldens generated by the most recent `recordPaparazziDebug`.
  - Real-device `:app:connectedDebugAndroidTest` remains required for runtime-critical UI (timer foreground service, locked-phone notifications, process death). Robolectric + Paparazzi do not substitute for that.
- Related docs/tests/code: `android/app/build.gradle.kts`, `android/app/src/test/java/dev/liftorium/app/LiftoriumAppRenderTest.kt`, `android/app/src/test/java/dev/liftorium/app/LiftoriumAppPaparazziTest.kt`, `android/gradle/libs.versions.toml`, `.github/skills/verification-loop/SKILL.md`, `.github/skills/android-implementation/SKILL.md`, `.github/skills/android-verification/SKILL.md`.

## 2026-05-16: Visual review required for UI deliverables

- Status: accepted
- Context: The global Copilot rule "verify with runtime evidence" requires runtime proof for every deliverable layer. UI changes have historically been declared done based on code review alone, which misses layout, alignment, theming, text-wrapping, and empty-state bugs that are only visible in rendered output. Host-side Paparazzi rendering now makes per-screen visual evidence cheap to produce, and the coding agent can view PNGs directly.
- Decision: When the coding agent adds or modifies any Compose surface (a `@Composable` rendered on screen, or a screen-state class consumed by one), the agent MUST:
  1. Add or update a Paparazzi snapshot test covering each meaningful state (initial, empty, loading, loaded, error, edge sizes, dark mode where applicable).
  2. Run the snapshot task (`:app:recordPaparazziDebug` or per-test equivalent) to regenerate PNGs.
  3. Call the `view` tool on each generated PNG and assess it against the plan / wireframe / design intent (layout, alignment, theme tokens, text wrapping, empty states).
  4. List the screenshot file paths in the task handoff under runtime evidence.
  5. Iterate the implementation if visuals diverge from intent.
- Rationale: Mechanical enforcement of "agent saw the rendered output" prevents the common failure mode where code looks correct but renders incorrectly. Listing screenshot paths in the handoff makes the evidence reviewable by the user without re-running the build.
- Alternatives considered: "Recommended" or "on-demand" visual review (rejected by user — too easy for future sessions to skip), PR-level mechanical gate that fails when Compose files change without corresponding screenshot test changes (deferred — useful if the policy proves easy to bypass; not built yet to avoid premature tooling).
- Consequences:
  - `android-implementation` skill documents the required steps and what counts as a meaningful state.
  - `android-verification` skill documents the screenshot test commands and output paths.
  - `verification-loop` skill requires UI changes to list screenshot paths in evidence.
  - `coverage-review` skill checks for screenshot test presence and agent visual review evidence before passing UI deliverables.
  - Real-device evidence for runtime-critical UI (rest timer foreground service, lock-screen notification, process death, OEM-specific behavior) remains required and is NOT substituted by Paparazzi/Robolectric screenshots.
- Related docs/tests/code: `.github/skills/android-implementation/SKILL.md`, `.github/skills/android-verification/SKILL.md`, `.github/skills/verification-loop/SKILL.md`, `.github/skills/coverage-review/SKILL.md`.


## 2026-05-16: Phase 2 program-resource schema (semantics, hash, discriminated targets, sourceKind)

- Status: accepted
- Context: Phase 1 left `schema/program-resource.schema.json` as a contract skeleton: it locked schemaVersion, IDs, validationStatus, validationIssues, and importAudit, but kept the substantive program content (exerciseCatalog, programStructure, progressionRules) as permissive open objects. Phase 2 (program-resources workstream) had to land the substantive contract that downstream phases (import, Android ProgramResourceLoader, Web read-only) consume, plus the activation-severity rules that JSON Schema alone cannot express.
- Decision:
  1. **Tighten in place at `schemaVersion = 1`** instead of bumping to `2`: no production resource shipped under the Phase 1 skeleton, so there are no consumers to break. The README's general "bump on every change" guidance now scopes to post-Phase-2 changes.
  2. **Two-stage validation**: structural Ajv-strict + a new pure-function semantic validator (`schema/semantics.ts`) covering cross-field rules. Activation requires both stages to pass (no semantic-critical issues) AND `validationStatus = "activatable"`. Resources with `validationStatus = "blocked"` or `"rejected"` are never activatable, even with an empty issue list.
  3. **Stable dot-namespaced issue codes** (`schema.*`, `metadata.*`, `status.*`, `catalog.*`, `structure.*`, `exercise.*`, `reference.*`, `construct.*`, `provenance.*`). Unknown codes default to `critical` per the Program Construct Matrix; the semantic validator emits `construct.must_be_critical` / `construct.severity_understated` / `construct.severity_overstated` when an importer disagrees with the matrix.
  4. **Prescription targets as a discriminated union** keyed by `kind` (`exact_load_reps` | `rep_range` | `percent` | `rpe` | `rir`). Each variant carries its own `additionalProperties: false` so downstream Kotlin/TypeScript consumers can switch exhaustively.
  5. **`progressionRules[].parameters` stays open** in Phase 2. Rule introspection is the Android program runner's job (Phase 4/6); the validator only checks shape.
  6. **`metadata.contentHash` is required and is the loader-side identity check**. Phase 2 ships `computeProgramResourceContentHash` + a `metadata.content_hash_mismatch` self-consistency check; the cross-resource "same versionId, different hash = conflict" rule is implemented in the Phase 4 loader. The hash covers `metadata` (minus `contentHash`), `programDefaults`, `exerciseCatalog`, `requiredReferences`, `programStructure`, `progressionRules`; it deliberately excludes `validationStatus`, `validationIssues`, `importAudit` so re-validating never changes the hash.
  7. **`importAudit.sourceKind` discriminator** (`synthetic` | `private_import`). `synthetic` permits the all-zero SHA-256 sentinel so fixtures and examples never need to be associated with a real private spreadsheet. `private_import` requires a real 64-hex SHA-256 and `consentGranted = true`; missing either emits a `provenance.*` warning. Full per-import consent enforcement is Phase 3.
  8. **First-runnable-week severity is derived from actual usage** in `programStructure`, not from the importer-declared `firstRunnableWeekIndex` alone. The declared value is cross-checked and a divergence emits `reference.declared_week_mismatch` (warning).
  9. **Alias text uniqueness is enforced after normalization** (trim + collapse whitespace + lowercase).
  10. **Ajv `strictRequired: false`** is set in the validator while keeping `strict: true` for type/format/unknown-keyword checks. This relaxation accommodates idiomatic `anyOf`/`allOf if/then` patterns without forcing redundant `properties` skeletons inside every branch. Activation safety is preserved by the semantic validator and by `additionalProperties: false` on every closed object.
- Rationale: Two-stage validation keeps the JSON Schema focused on structural well-formedness while keeping cross-field activation rules in a typed, testable TypeScript function. Stable issue codes give the import workflow and the Android loader a contract they can branch on without parsing free-text messages. The `sourceKind` discriminator makes the privacy-friendly fixture contract mechanical: tests never need to fake real source hashes. Computing first-runnable-week from real usage catches importer self-inconsistency early and removes a class of "declared but not enforced" bugs. `strictRequired: false` keeps the schema author-friendly without sacrificing meaningful strictness.
- Alternatives considered: bumping to `schemaVersion = 2` (rejected — no existing consumers); embedding semantic checks as Ajv keywords (rejected — harder to test and to reuse from non-Ajv consumers); keeping `content_hash` over the full document including `validationIssues` (rejected — re-validation would mutate identity); declaring `construct.*` codes as a closed enum (rejected — importers will discover new constructs over time; "unknown defaults to critical" is the safer policy); using string `kind` discriminators without `oneOf` (rejected — loses the exhaustive-match guarantee for consumers); requiring all `then` branches to list every required property (rejected — verbose; `strictRequired: false` accomplishes the same goal with no loss of safety because closed objects carry `additionalProperties: false`).
- Consequences:
  - `schema/` adds `hash.ts` and `semantics.ts`; `validator.ts` re-exports both.
  - 12 fixtures (2 activatable, 1 activatable-with-warnings, 8 blocked-by-semantics, 1 rejected, 1 provenance-warning) plus the operator-facing `schema/examples/example-5-3-1-bbb.json` exercise the new surface. `schema/fixtures/blocked-content-hash-mismatch.json` carries a deliberately stale hash and must NOT be refreshed by `npm run refresh-fixture-hashes`.
  - 57 tests pass under `cd schema; npm test` and `cd schema; npm run typecheck` is clean.
  - `npm run refresh-fixture-hashes` is the canonical way to rewrite `metadata.contentHash` for fixtures and examples; it runs only the hash-freshness suite to avoid the parallel-read race with other tests.
  - Phase 3 import workflow consumes the issue-code namespace, the `sourceKind` discriminator, and the construct severity classifier; Phase 4 `ProgramResourceLoader` consumes `metadata.contentHash` for cross-resource conflict detection.
  - Generated Kotlin / TypeScript model types are still out of scope; the loader (Phase 4) and Web (Phase 10) workstreams own their own consumer types.
- Related docs/tests/code: `schema/program-resource.schema.json`, `schema/hash.ts`, `schema/semantics.ts`, `schema/validator.ts`, `schema/test/program-resource.schema.test.ts`, `schema/test/program-resource.semantics.test.ts`, `schema/test/hash-freshness.test.ts`, `schema/fixtures/`, `schema/examples/example-5-3-1-bbb.json`, `schema/scripts/refresh-fixture-hashes.mjs`, `schema/README.md`, `docs/workstreams/program-resources.md`, `docs/architecture.md` (Program Construct Matrix + Validation severity contract + JSON resource versioning contract), `.github/skills/verification-loop/SKILL.md`.
## 2026-05-16: Phase 3 import workflow surface and scope

- Status: superseded by "2026-05-17: Phase 3 import workflow reset to Copilot skill"
- Context: Phase 1 left `tools/import/` as a placeholder. Phase 2 shipped the program-resource schema, semantic validator, content-hash helper, and the `construct.*` / `provenance.*` issue-code namespaces. Phase 3 must replace the placeholder with the real import pipeline that A6 (workbook → activatable resource) and A7 (operator correction loop) require, without inventing parallel contracts.
- Decision: Build `tools/import` as a library-only TypeScript package that adopts ExcelJS (MIT) for `.xlsx` parsing, exposes `runImport(request)` / `applyCorrection(request, correction)` / `finalize(result)`, requires the caller to supply `importedAtUtc` and an optional `existingVersionsLookup`, and reuses the Phase 2 `schema/` package directly. No CLI binary, no LLM client wired (the Copilot session is the cloud-assisted processor), no end-to-end `.xlsx` fixtures committed.
- Rationale: A library-only surface keeps the importer testable from Vitest and avoids a parallel CLI that would duplicate the JS API. ExcelJS handles formulas, merged ranges, and shared strings without a native dependency and is permissively licensed. Reusing `buildProgramResourceValidator()`, `validateProgramResourceSemantics()`, and `computeProgramResourceContentHash()` keeps construct-matrix severity and content-hash identity in exactly one source of truth (`schema/`). Caller-supplied `importedAtUtc` is required so identical inputs reproduce byte-identical canonical JSON (CLI-IMP-002). The consent gate keys off `processingIntent` (not on parse), matching `docs/architecture.md` lines 511-519 ("consent denied → local metadata/structure extraction only"). The `existingVersionsLookup` guard is advisory: the importer emits `metadata.version_conflict` and throws `VersionConflictError`, but Phase 4's `ProgramResourceLoader` remains the authoritative cross-resource gate. The correction model covers the full A7 override surface (`exerciseApprovals`, `aliasMappings`, `weekOrderOverrides`, `sessionOrderOverrides`, `ignoredRows`, `referenceValueOverrides`, `constructClassificationOverrides` matrix-permitted-only, `noteOnlyApprovals`) so a single rerun reflows the whole pipeline.
- Alternatives considered: a Node CLI binary (rejected — duplicates the JS API for no consumer; tests already exercise the library directly); SheetJS / `xlsx` (rejected — licensing friction); committing `.xlsx` fixtures (rejected — repo bloat + privacy risk; constructed `WorkbookModel`s are sufficient since downstream tests don't exercise ExcelJS parsing); wiring a real LLM client (rejected — the Copilot session IS the cloud-assisted processor; the importer's job is to enforce and record consent, not call out); maintaining a parallel construct-severity table in the importer (rejected — every code must come from `schema/semantics.ts` to avoid drift); auto-creating exercise catalog entries for unapproved candidates (rejected — would mask `exercise.unknown_reference` from the semantic validator and let unknown exercises silently activate).
- Consequences:
  - `tools/import/` ships a library with the architecture documented in `tools/import/README.md`: parser → detect → normalize → validate → correct → pipeline.
  - `WorkbookModel` is the only surface where raw cell/formula text is allowed; the `ValidationReport`, in-resource `validationIssues[]`, and any other exported field never contain source excerpts (enforced by `test/report-privacy.test.ts` with sentinel strings).
  - `tools/import` declares Ajv + ajv-formats + ExcelJS as runtime deps so Vitest can resolve them under the package; `@types/node` matches the `schema/` package.
  - 63 tests pass under `cd tools\import; npm test` covering every A6/A7 mapped ID (UT-IMP-001..005, UT-JSON-001/002, IT-IMP-001..006, CLI-IMP-001/002) plus the parser, prescription patterns, structure detection, report contract, and privacy boundary.
  - `.github/skills/verification-loop/SKILL.md` promotes the "Import report validation" row from gated to `cd tools\import; npm test`.
  - Phase 4 `ProgramResourceLoader` consumes finalized resources directly; it does not depend on `tools/import` at runtime.
- Related docs/tests/code: `tools/import/README.md`, `tools/import/src/`, `tools/import/test/`, `schema/program-resource.schema.json`, `schema/semantics.ts`, `schema/hash.ts`, `schema/validator.ts`, `docs/architecture.md` (import workflow + Program Construct Matrix + Validation severity contract + JSON resource versioning contract), `docs/product.md` A6/A7, `docs/workstreams/import-workflow.md`, `.github/skills/verification-loop/SKILL.md`, `.github/skills/import-report-check/SKILL.md`.

## 2026-05-17: Phase 3 import workflow reset to Copilot skill

- Status: accepted
- Context: The previous 2026-05-16 Phase 3 decision built `tools/import/` as a TypeScript runtime library (parser/detect/normalize/validate/correct/pipeline). On 2026-05-17 the user clarified the actual A6/A7 contract: A6 says "Developer/operator runs spreadsheet-first **Copilot** import" and A7 says "Developer/operator reviews, **chats with Copilot** to correct issues". The Copilot session IS the importer. A runtime library duplicates what the skill+Copilot do directly, and the typed `Correction` API contradicts A7's conversational correction loop. Additionally, the user clarified that the Android input endpoint (lifter picks a JSON file and loads it into Room) belongs to the `program-resources` workstream (Phase 4) where `ProgramResourceLoader` and the Room program tables live.
- Decision: Reset Phase 3 to a skill-only deliverable. (1) Delete `tools/import/src/` and `tools/import/test/`; leave only a `README.md` pointing at the skill. (2) Create `.github/skills/import-workflow/SKILL.md` with the full A6/A7 conversational procedure: hard pre-bytes consent gate, privacy boundary contract, workbook orientation (ad-hoc Python openpyxl / Node ExcelJS — nothing committed), construct detection against `KNOWN_*_CONSTRUCT_CODES` in `schema/semantics.ts`, exercise approval table (operator decision required before finalization), deterministic ID/ordering rules, JSON composition with `metadata.contentHash` via `schema/hash.ts`, validation via `schema/scripts/validate-resource.ts`, conversational correction loop, twice-rerun determinism check, and two-artifact finalization (`<programVersionId>.json` + `<programVersionId>.import-report.json`). (3) Add `schema/scripts/validate-resource.ts` (run via `npm run validate:resource -- <path>`) that wraps `buildProgramResourceValidator()`, `validateProgramResourceSemantics()`, and `computeProgramResourceContentHash()`, prints the privacy-safe 6-per-issue + 5-report-level fields contract owned by `.github/skills/import-report-check/SKILL.md`, and exits 0/1/2/3 for activatable / activatable_with_warnings / blocked / rejected. (4) Mark CLI-IMP-001, CLI-IMP-002, UT-IMP-004, UT-IMP-005, IT-IMP-001, IT-IMP-004, IT-IMP-005 in `docs/product.md` as "verified via skill procedure" with explicit Step references; keep UT-IMP-001/002/003, UT-JSON-001/002, IT-IMP-002/003 mapped to schema-package tests + validator-CLI exit-code coverage on `schema/fixtures/`. (5) Move the Android input endpoint to `docs/workstreams/program-resources.md` outputs as a Phase 4 deliverable; explicitly note in `docs/workstreams/import-workflow.md` that user-facing import on Android is owned by `program-resources`. (6) The Phase 3 → Phase 4 handoff is two artifacts; the Android loader MUST revalidate independently and MUST treat same `programVersionId` + different `contentHash` as a conflict.
- Rationale: A skill matches the literal text of A6/A7 ("Copilot import", "chats with Copilot"). A typed runtime importer would always be a second implementation alongside Copilot's session-level work, and the typed `Correction` API actively conflicts with the conversational correction loop the spec calls for. Keeping `tools/import/` empty removes a maintenance burden and a class of bugs (parallel construct severity tables, type-vs-schema drift, library/skill divergence). The validator CLI is the one piece of code that genuinely belongs in code — Copilot cannot reliably recompute a 64-character canonical content hash in chat. Placing the CLI under `schema/` (not `tools/import/`) keeps it close to the helpers it wraps and avoids reintroducing a `tools/import/` package. The two-artifact finalization (JSON + sidecar) gives Phase 4 a privacy-safe audit trail without bundling source content. Moving the Android input endpoint to Phase 4 is the natural boundary: that endpoint depends on Room program tables and `ProgramResourceLoader`, both Phase 4 responsibilities. Coverage-review traceability is preserved: every A6/A7 mapped test ID either still has a real test (schema package + validator CLI exit-code coverage) or an explicit skill-procedure mapping with a Step reference; user explicitly approved this traceability change (`private_fixtures = retire_recreate` on 2026-05-17).
- Alternatives considered: keep the TypeScript runtime library and add a skill on top (rejected — duplicates work; correction-loop API contradicts A7); keep a thin TypeScript helper under `tools/import/` for xlsx-to-cells dumping (rejected — Copilot can use openpyxl / one-off ExcelJS scripts ad hoc and that keeps `tools/import/` truly empty); build the Android input endpoint in this phase (rejected — depends on Phase 4 Room schema and `ProgramResourceLoader` which don't exist; would expand Phase 3 to cover roughly half of Phase 4); add a stub Room table that stores the raw JSON blob in Phase 3 (rejected — would create a migration the Phase 4 loader has to undo); persist `private_fixtures = keep_validator_tests` (rejected — most tests asserted behaviors of code that is now deleted); validator CLI under `tools/import/` (rejected — would resurrect a package directory the reset is explicitly emptying).
- Consequences:
  - `tools/import/` contains only `README.md` pointing at the skill. No `package.json`, no source, no tests.
  - `.github/skills/import-workflow/SKILL.md` is the authoritative procedure. The skill's discovery description is precise so future Copilot sessions handling an `.xlsx` find it and so it is NOT used for Android loader work or report-only review.
  - `schema/scripts/validate-resource.ts` is the only piece of Phase 3 code. Invoked via `cd schema; npm run validate:resource -- <path>`. Exit code 0=activatable, 1=activatable_with_warnings, 2=blocked, 3=rejected, 4=usage error. Supports `--json` for the sidecar artifact.
  - `schema/package.json` gains `tsx` as a devDependency (TypeScript loader for the CLI under Node 22) and a `validate:resource` script.
  - `docs/product.md` A6/A7 traceability rows annotate every test ID with either its existing schema-package home or its skill-procedure Step reference.
  - `docs/workstreams/import-workflow.md` outputs now read "skill + validator CLI + procedure"; Phase 4 handoff lists the two artifacts and the loader's revalidation/conflict obligations.
  - `docs/workstreams/program-resources.md` outputs gain the Android input endpoint + `ProgramResourceLoader` as Phase 4 deliverables, replacing the blanket "user-facing import is out of MVP" restriction with a constrained file-picker import that consumes pre-validated JSON only.
  - `.github/skills/verification-loop/SKILL.md` "Import report validation" row points at `cd schema; npm run validate:resource -- <path>` and `cd schema; npm test`.
  - `cd schema; npm test` is now 57/57 green (the pre-existing 4 failures were fixture-hash staleness and were refreshed via `npm run refresh-fixture-hashes` during this reset).
  - Phase 4 has a concrete handoff contract: receives JSON + sidecar; MUST revalidate independently against schema + semantics + recomputed `contentHash`; MUST reject same `programVersionId` + different `contentHash`; MUST NOT read original spreadsheets.
- Related docs/tests/code: `.github/skills/import-workflow/SKILL.md`, `.github/skills/import-report-check/SKILL.md`, `.github/skills/verification-loop/SKILL.md`, `.github/skills/program-resource-validation/SKILL.md`, `schema/scripts/validate-resource.ts`, `schema/scripts/refresh-fixture-hashes.mjs`, `schema/validator.ts`, `schema/semantics.ts`, `schema/hash.ts`, `schema/program-resource.schema.json`, `schema/fixtures/`, `schema/package.json`, `tools/import/README.md`, `docs/product.md` A6/A7 rows, `docs/workstreams/import-workflow.md`, `docs/workstreams/program-resources.md`, `docs/architecture.md` (Program Construct Matrix + Validation severity contract + Import workflow architecture sections).

## 2026-05-17: Code coverage gate at 95% on domain and data layers

- Status: accepted
- Context: Liftorium had no coverage tooling on any platform. Tests ran but no platform measured or enforced line/branch/statement/function percentages. Without a measurable floor, regressions and silently-untested code paths could accumulate. The user requested a high coverage gate AND a structured test-design workflow to make hitting the gate cheap rather than punitive.
- Decision:
  1. Adopt a **95% coverage gate on all available metrics** for the domain and data layers of each platform: schema (vitest), Web (vitest), Android (Kover).
     - Schema and Web (vitest v8 provider): line, branch, statement, function — all four metrics enforced at 95%.
     - Android (Kover 0.8.3): line, branch, instruction — three metrics enforced at 95%. **Kover has no METHOD counter**, so vitest "functions" is approximated by INSTRUCTION (a method with no test invocations contributes its entire bytecode to the missed pool, so uncovered methods still pull the gate down). The divergence is platform-native, not semantically equivalent, and is documented honestly.
  2. **UI and framework glue are exempt** and excluded by config: Web `src/components`, `src/pages`, `src/main.tsx`, `src/App.tsx`; Android :app module (Compose/Activity/Application/Hilt/Room `_Impl`/Compose preview classes/serializer companions) and class-name patterns for the same in `:core`/`:data`/`:domain`; schema `scripts/**`/`fixtures/**`/`test/**` and config files. UI is tested via Paparazzi/Robolectric and React Testing Library, which do NOT contribute to the gate.
  3. Enforcement is **scripts-only** for now: `schema/` `npm run test:coverage`; `web/` `npm run test:coverage` (preceded by `scripts/coverage-guard.mjs` vacuous-pass guard); `android/` `./gradlew koverVerify`. No CI workflow yet — verification-loop invokes the scripts.
  4. **No threshold overrides.** 95% is firm. If something legitimately cannot reach 95%, the remedy is principled exclusion under the Exclusion Contract below, not lowering the gate.
  5. **Exclusion Contract.** The no-override policy is firm at the threshold layer; the escape hatches are exclusions, with strict rules:
     - **Whole-file exclusion** is allowed only for: build configs, generated code, CLI helpers, framework glue (Application/Activity/Compose/Hilt/Room `_Impl`/serializer/Preview classes), and behaviorless model code (Kotlin data classes, sealed result classes, DTOs, Room `@Entity` data classes, value classes whose members are exclusively compiler-generated).
     - **Line-level ignore directives** are allowed only for genuinely unreachable code (e.g. exhaustive sealed-class switch default, `throw new Error("unreachable")`). They MUST be paired with an inline rationale comment. Vitest provider-correct directive is `/* v8 ignore next */` (NOT c8). Kover supports class-pattern exclusions; for fine-grained line exclusion in Kover, refactor instead.
     - **Defensive guards against schema-invalid input** are NOT excluded — they are tested as behavior-bearing defense-in-depth: feed malformed input, assert the guard fires cleanly. `schema/semantics.ts` (contract at lines 39–43: "may run on schema-invalid input") uses this pattern extensively.
     - **A single missed branch never justifies whole-file exclusion.** Prefer refactor or directive-with-rationale.
     - **Ignored locations must appear in the coverage report output.** No silent exclusions.
  6. **Vacuous-pass guard for Web.** With `include: ['src/data/**', 'src/domain/**']` and no matching files, vitest reports 0/0 = 100% (vacuous pass). `web/scripts/coverage-guard.mjs` runs before vitest: exits 0 with a message if neither dir exists (Phase 3 baseline); exits 1 if either dir exists but contains zero source files (gate silently neutered); otherwise execs vitest.
  7. **New `test-design` skill (`.github/skills/test-design/SKILL.md`).** Auto-discovers when Copilot is about to add a new test file, new top-level `describe` block / test class, or substantially expand a behavioral test matrix in domain or data code. The skill runs a 6-step procedure: frame the code under test, select 3–4 of 8 scenario dimensions, spawn parallel `explore` sub-agents (one per dimension), consolidate with one `rubber-duck` agent, produce a reviewable test matrix table mapped to acceptance IDs, then implement and verify coverage. UI tests, trivial test edits, and acceptance-traceability audits are explicitly out of scope (those route to `*-implementation` / `coverage-review`).
- Rationale: A measurable percentage gate prevents silent erosion of test coverage. Domain/data-only scope keeps UI snapshot testing (which has its own discipline via Paparazzi and the visual-review loop) out of the gate; UI tests would otherwise distort the metric in either direction (snapshot tests inflate line coverage cheaply; failed snapshots block unrelated work). The Exclusion Contract codifies the small number of legitimate escape hatches so future contributors don't have to relitigate them. The parallel-explore + rubber-duck design for `test-design` separates brainstorm (where breadth matters) from consolidation (where dedup, gap-flagging across the full 8 dimensions, and traceability matter), and produces a reviewable matrix BEFORE test code is written so the user can correct framing cheaply.
- Alternatives considered:
  - **Lower gate (e.g. 80%).** Rejected: too easy to drift toward without resistance.
  - **Per-file thresholds with overrides.** Rejected: invites bargaining; the Exclusion Contract is a more principled escape hatch.
  - **CI-enforced from day one.** Deferred: scripts-only enforcement via `verification-loop` is the Phase 3 baseline; CI lands later without changing the rules.
  - **Equivalent counter mapping between vitest and Kover.** Rejected: Kover has no METHOD counter; pretending otherwise would hide the divergence. Documenting it honestly is better.
  - **Single test-design agent (no parallelism).** Rejected: parallel explore agents cover the dimension space faster; the rubber-duck consolidation reclaims the dedup/gap-flagging benefit a single agent would have given.
  - **Test-design triggered only on "non-trivial logic" with no concrete criteria.** Rejected: the skill description now uses concrete triggers ("new test file, new top-level describe / test class, substantial expansion") with explicit anti-triggers ("renames, formatting, one missing assertion, updating expected text").
- Consequences:
  - `schema/vitest.config.ts` enforces 95/95/95/95 on `validator.ts`, `semantics.ts`, `hash.ts`. Phase 3 result: 100% statements/lines/functions, 99.63% branches. The remaining 0.37% uncovered branch (`semantics.ts` `isContiguousFromOne([])` early return) is unreachable from existing callsites; gate still passes.
  - A real bug surfaced and was fixed during this work: `schema/semantics.ts` line 71 crashed on `null` `validationIssues` entries; defensive `isObject(i) &&` guard added (documented in checkpoint history).
  - `web/vite.config.ts` and `web/scripts/coverage-guard.mjs` activate the gate when Phase 4+ adds `src/data/` or `src/domain/`. Today the guard reports "no coverage targets yet" and exits 0.
  - `android/build.gradle.kts` applies Kover at the root project (NOT `apply false`) with aggregation dependencies on `:core`/`:data`/`:domain`; modules apply Kover individually. `./gradlew koverVerify` passes today: `:core` reports 100% from `TimeSource`'s unit test; `:data`/`:domain` report "No sources" (their current code is behaviorless markers, correctly classified by Kover). Gate activates as Phase 4+ adds real domain/data behavior.
  - `verification-loop/SKILL.md` gains three new rows (schema, web, android coverage commands).
  - `test-design` becomes a routinely-invoked skill for any new domain/data test work.
  - Future ratchet: if a behaviorless-model exclusion becomes inadequate (e.g. data classes with non-trivial `init` blocks), revisit by adding a `@KoverIgnore` annotation in `:core` rather than blanket pattern excludes.
- Related docs/tests/code: `schema/vitest.config.ts`, `schema/test/coverage-gap.semantics.test.ts`, `schema/semantics.ts`, `schema/hash.ts`, `web/vite.config.ts`, `web/scripts/coverage-guard.mjs`, `android/build.gradle.kts`, `android/gradle/libs.versions.toml`, `android/{core,data,domain}/build.gradle.kts`, `.github/skills/test-design/SKILL.md`, `.github/skills/verification-loop/SKILL.md`.

## 2026-05-17: Drop the import-workflow consent gate

- Status: accepted
- Context: The 2026-05-15 decision (`Cloud-assisted import with explicit consent`) and decision 2026-05-16 item #7 (`importAudit.sourceKind` requires `consentGranted = true` for `private_import`) and the 2026-05-17 reset's Step 0 "hard pre-bytes consent gate" all assumed the import workflow needed an explicit per-import consent prompt and a `consentGranted` field in the resource. The `import-workflow` skill is a developer-time procedure invoked inside a Copilot session against a workbook the developer tagged in. The Copilot session IS the cloud-assisted processor by construction — there is no `local_only` vs `cloud_assisted` toggle to choose between. Prompting the developer every time adds friction without protecting anything: the developer's act of invoking the skill IS the consent. The `consentGranted` field was therefore ceremonial.
- Decision: Remove the consent gate and field entirely.
  1. `schema/program-resource.schema.json` — drop `consentGranted` from `importAudit.required` and `importAudit.properties`. `additionalProperties: false` stays, so any future input carrying `consentGranted` is now invalid.
  2. `schema/semantics.ts` — drop the `consent` read and the `provenance.private_import_missing_consent` issue. Keep `provenance.private_import_zero_hash` (sentinel-hash check) and `provenance.synthetic_with_real_hash` (synthetic-with-real-hash check).
  3. All `schema/fixtures/*.json` and `schema/examples/*.json` — strip the `consentGranted` line (19 fixture files + 1 example). `importAudit` is excluded from `metadata.contentHash` per decision 2026-05-16 #6, so resource hashes do not change.
  4. Schema tests — drop the two `provenance.private_import_missing_consent` tests and remove `consentGranted` from inline test fixtures. Net: 159 → 157 tests, all passing; 100% statement coverage, 99.63% branch coverage retained.
  5. `.github/skills/import-workflow/SKILL.md` — replace the "Pre-bytes privacy posture" section with a slim "Step 0 — Pre-flight metadata" that just computes basename + size + SHA-256 and pins `sourceKind = "private_import"`. Drop `processingIntent` / `cloudConsent` inputs. The "Privacy boundary contract" section (no raw cell text in chat/commits/summaries) is KEPT — it is about content discipline, not about consent.
  6. `.github/skills/import-report-check/SKILL.md` — drop the "explicit cloud-processing consent is recorded" check line; replace with explicit operator-approval check for unknown exercises + matrix-permitted overrides.
  7. `docs/architecture.md` — drop the "consent granted / consent denied" alt-branch from the import sequence diagram; rewrite the "Import privacy and consent contract" section as "Import privacy contract"; drop the "Cloud-assisted processing requires explicit per-import consent" bullet from `### Import privacy`.
  8. `docs/product.md` A6/A7 traceability — UT-IMP-005 reframed from "consent recorded in sidecar" to "source provenance recorded" (pins `sourceKind` + `sourceHash` + `sourceFilename` pre-parse). CLI-IMP-001 mapping updated to "Step 0 pre-flight metadata".
  9. `docs/mvp-roadmap.md`, `docs/workstreams/import-workflow.md`, `docs/workstreams/program-resources.md`, `.github/copilot-instructions.md`, `README.md`, `private/imports/README.md`, `schema/README.md`, `Liftorium-Product-Requirements.md` — drop or rephrase consent language. PRD line 564 reframed to clarify that user-facing app uploads are forbidden and developer-time Copilot import is the operator's explicit action; PRD line 569 (telemetry consent) is unrelated and left alone.
- Rationale: The consent gate protected against a flow that does not exist. Users do not upload spreadsheets through the app for AI processing; the only path that exists is a developer running the import-workflow skill against a tagged workbook, which IS the consent. Making the developer click through a prompt every time wastes operator attention without producing audit value. The audit trail that does matter — `sourceHash` + `sourceFilename` + `sourceKind` + `importedAtUtc` + the sidecar's per-issue operator decisions — is preserved. Schema-level `additionalProperties: false` ensures future code cannot quietly reintroduce the field without a schema change.
- Alternatives considered: keep the schema field but stop asking for it (rejected — leaves a ceremonial required field nothing uses, invites confusion); keep the interactive prompt with a default-yes confirmation (rejected — user explicitly said "no need to annoy the developer every time"); record a fixed `consentRecord` text in the sidecar (rejected — would be a constant string of zero information value); strip telemetry-consent language from the PRD as well (rejected — runtime telemetry is a separate concern from developer-time import).
- Consequences:
  - Schema breaking change: any input file carrying `importAudit.consentGranted` is now schema-invalid (rejected by `additionalProperties: false`). Acceptable because no production data exists yet; all in-repo fixtures and examples have been updated in the same change.
  - `provenance.*` namespace shrinks to two codes: `private_import_zero_hash` and `synthetic_with_real_hash`. `schema/README.md` issue-code table reflects this.
  - `schema/fixtures/warning-private-import-provenance.json` still emits one warning (`provenance.private_import_zero_hash` from the sentinel hash with `sourceKind: "private_import"`), so validator CLI exit code 1 (`activatable_with_warnings`) is unchanged — verified by smoke-testing the CLI against representative fixtures (valid-activatable exits 0; valid-activatable-warnings exits 1; blocked-drop-set exits 2; rejected-not-activatable exits 3; warning-private-import-provenance exits 1).
  - UT-IMP-005 acceptance ID retained but its contract narrowed from "consent recorded" to "source provenance recorded".
  - Phase 4 `ProgramResourceLoader` does NOT need to read or check `consentGranted` (the field is gone).
  - Supersedes: the 2026-05-15 `Cloud-assisted import with explicit consent` decision (the consent-gate model is no longer in force) and item #7 of the 2026-05-16 `Phase 2 program resource schema, validator, fixtures, examples` decision regarding `consentGranted = true` enforcement. The `sourceKind` discriminator and the `private_import` / `synthetic` distinction are retained.
- Related docs/tests/code: `schema/program-resource.schema.json`, `schema/semantics.ts`, `schema/test/program-resource.semantics.test.ts`, `schema/test/coverage-gap.semantics.test.ts`, `schema/fixtures/*.json`, `schema/examples/example-5-3-1-bbb.json`, `schema/README.md`, `.github/skills/import-workflow/SKILL.md`, `.github/skills/import-report-check/SKILL.md`, `docs/architecture.md` (import sequence + import privacy + import privacy contract sections), `docs/product.md` A6/A7 rows, `docs/mvp-roadmap.md`, `docs/workstreams/import-workflow.md`, `docs/workstreams/program-resources.md`, `.github/copilot-instructions.md`, `README.md`, `private/imports/README.md`, `Liftorium-Product-Requirements.md`.



## 2026-05-17: programWeek runtime variants (Week 10A/10B alternates)

- Status: accepted
- Context: Real-world programs (e.g., Power Building 1) have weeks where the user picks ONE of N templates and runs only that one, then proceeds to the next sequential week. Concrete example: "Week 10A" (PR test) vs "Week 10B" (volume) — both occupy the same logical week-10 slot. The MVP schema before this decision had no way to express this; the import-workflow forced a choice between (a) collapsing variants and losing one template's content, (b) emitting both variants as sequential weeks (silently letting the user run both, which is wrong), or (c) treating one variant as critical so the resource never activates. The user explicitly chose a schema extension over alternatives (separate blocks, sequential-with-warning, defer).
- Decision: Add two OPTIONAL fields to `programWeek` to express week-level runtime alternates.
  1. `variantOf: string` — id of another `programWeek` within the SAME block. The week declaring `variantOf` is an alternate of that base week; at runtime the loader presents the base and all variants as a one-of choice and runs only the chosen week.
  2. `variantLabel: string` — operator-facing short label distinguishing this variant within its group (e.g. "A", "B", "heavy"). Required on every member of a multi-member variant group; labels within a group must be unique after trim+lowercase.
  3. `schema/program-resource.schema.json` — fields added under `$defs/programWeek`. `additionalProperties: false` is preserved. `schemaVersion` description updated: resources using `variantOf` MUST declare `schemaVersion >= 2`.
  4. `schema/semantics.ts` — new `checkVariantWeeks` validation:
     - `structure.unknown_variant_target` (critical) — variantOf references a non-existent week id OR a week in a different block.
     - `structure.variant_chain_depth` (critical) — variantOf target itself has variantOf (chain depth is bounded to 1; multiple alternates at the same position are modeled as siblings targeting the same base, not as a chain).
     - `structure.variant_group_not_contiguous` (critical) — base and variants are not contiguous in the block's `weeks[]` array. Runners must not have to scan the whole array to discover a variant.
     - `structure.variant_missing_label` (critical) — multi-member group has a member without `variantLabel`.
     - `structure.variant_duplicate_label` (critical) — two members of one group share a normalized label.
     - `structure.variant_schema_version_too_low` (critical) — resource uses `variantOf` but declares `schemaVersion < 2`.
  5. `schema/semantics.ts` `walkPercentTargets` — uses an "effective week index" for variant weeks (variants resolve to their base's `weekIndex`), so a required reference consumed only in a variant of week 1 still fires `reference.missing_first_week` (critical), not the `missing_later_week` warning.
  6. `SUPPORTED_SCHEMA_VERSIONS` extended to `{1, 2}`. Existing fixtures stay on `schemaVersion: 1`; only resources using variants must declare 2.
  7. Tests added: 8 new semantics tests + 2 new schema tests (167 total, all passing). Coverage gate still passes at 100% statements / 100% lines / 100% functions / 98.07% branches on `schema/`.
- Rationale: This is a small, additive, backwards-compatible schema change that unblocks a real program import (Power Building 1) without compromising existing contracts. Optional fields default to absent, so all existing fixtures, examples, and validators continue to work unchanged. Variant-unaware consumers can detect variant resources via the `schemaVersion >= 2` marker (rule 6) and reject them cleanly — they cannot silently treat variants as sequential weeks.
- Alternatives considered:
  - Separate blocks per variant — rejected: a block represents a phase boundary (e.g. accumulation → intensification), not a runtime fork. Bending it would distort training-max progression semantics.
  - Sequential weeks with a `variant_choice_required` warning — rejected: defers the modeling problem to the runtime UX and lets variant-unaware loaders silently run both.
  - Defer the decision — rejected: the user is dogfooding the import workflow today and needs a path forward for the Power Building 1 artifact.
  - Symmetric `variantGroupId` field on all members — considered: cleaner mental model but requires a separate uniqueness check and doesn't match the asymmetry inherent in "base vs alternate" UI presentation.
- Consequences:
  - Schema-version-2 resources are NOT loadable by variant-unaware loaders. Phase 4 `ProgramResourceLoader` MUST refuse to activate any resource with `schemaVersion >= 2` until it supports variants — documented in the schema description and this decision.
  - The validator now treats a TM consumed only in a variant of week 1 as critical (was warning). This is a behavioral change for `reference.missing_first_week` / `missing_later_week` only when variants are used — variant-free resources are unaffected.
  - `schema/README.md` issue-code table grows by 6 codes (all `structure.*`).
  - Future work (Phase 4 / runner): document the variant choice UX and ensure the choice is recorded in `WorkoutSession` so progression engine and stats reflect the picked variant only.
- Related docs/tests/code: `schema/program-resource.schema.json` (programWeek + schemaVersion), `schema/semantics.ts` (`checkVariantWeeks`, updated `walkPercentTargets`, `buildVariantBaseIndexMap`), `schema/test/program-resource.semantics.test.ts` (8 new tests), `schema/test/program-resource.schema.test.ts` (2 new tests), `docs/architecture.md` (program structure section), `private/imports/power-building-1-v1.json` (first variant-using resource).

## 2026-05-17: Prescription range encoding and warm-up count (schemaVersion 3)

- Status: accepted
- Context: Power Building 1 prescriptions in the source workbook commonly carry three concurrent drivers per working set: a `%1RM` value (often a RANGE like `75-80%`), a calculated load (`G` column, derived by xlsx `MROUND(TM*pct, 5)` formulas), an RPE value, AND a rest hint that is itself a range (`3-4 min`). Column `D` separately gives a warm-up set COUNT with no per-set loads/reps. The schema before this decision could express only single-value `percent`, a single `restSecondsHint`, no structured warm-up count, and no convention for which target wins when multiple drivers coexist on a set. The extractor consequently dropped `%1RM` range strings entirely (because the cell value is a string, not a number), fell through to RPE-only, lost rest range information by parsing only the lower bound, and emitted the warm-up count as an unstructured free note.
- Decision: Extend the program-resource schema (schemaVersion 3) to express prescription ranges and warm-up counts as first-class structured fields, and define conjunctive-target semantics so percent and RPE can coexist on the same set.
  1. `schema/program-resource.schema.json` — `$defs/percentTarget` gains `percentMin` and `percentMax` as an alternative to `percent`. Mutual exclusion is enforced by `oneOf` with explicit `not` clauses on the opposing branch, so partial mixed forms (`percent + percentMin`, `percent + percentMax`, etc.) are rejected at schema level. `additionalProperties: false` is preserved.
  2. `$defs/prescriptionItem` gains optional `restMaxSecondsHint` (integer >= 0) and optional `warmupSetCount` (integer >= 0). A `dependentRequired` rule enforces `restMaxSecondsHint` requires `restSecondsHint`. The existing `restSecondsHint` becomes the lower bound when the max is present, a single hint otherwise.
  3. `schemaVersion` description updated: bump to 3 when any of these new fields are used. Loaders that do not understand a higher schemaVersion MUST reject the resource.
  4. `schema/semantics.ts` — new `checkPrescriptionExtensions` validation:
     - `target.percent_range_invalid` (critical) — `percentMin >= percentMax`. Extractor normalizes equal values to single-percent form, so equal is also a programming error.
     - `item.rest_range_invalid` (warning) — `restMaxSecondsHint <= restSecondsHint`. Warning rather than critical because the runtime can clamp.
     - `structure.percent_range_schema_version_too_low` (critical) — percent range form used with `schemaVersion < 3`.
     - `structure.rest_range_schema_version_too_low` (critical) — `restMaxSecondsHint` used with `schemaVersion < 3`.
     - `structure.warmup_count_schema_version_too_low` (critical) — `warmupSetCount` used with `schemaVersion < 3`.
  5. `SUPPORTED_SCHEMA_VERSIONS` extended to `{1, 2, 3}`.
  6. `walkPercentTargets` is unchanged: it keys on `target['kind'] === 'percent' && typeof target['referenceId'] === 'string'`, which is true for both the single-percent and percent-range forms, so a range target consumes the same reference as a single target (one reference per target, not two).
  7. Conjunctive-target semantics: when a `setPrescription.targets` array carries more than one target (e.g. percent + rpe), both are constraints the runtime must surface and check. There is no priority ranking inside the resource — the priority documented in this decision (weight > %1RM > RPE) is an EXTRACTOR-side preference for which signal becomes a structured target versus a note, not a runtime fallback.
  8. Tests added: 11 new semantics tests + 8 new schema tests (186 total, all passing). Coverage gate at 100% statements / 100% lines / 100% functions / 97.92% branches on `schema/`.
- Rationale: This is the smallest schema change that preserves information from the source workbook end-to-end without losing %1RM ranges, rest ranges, or warm-up counts. Optional fields default to absent, so all existing fixtures, examples, and schema-version-1 and -2 resources stay valid unchanged. The `schemaVersion` gate (rule 3) gives variant-unaware and range-unaware loaders a clean reject pathway. Encoding percent rather than load lets the runtime mirror the xlsx `MROUND(TM*pct, increment)` formulas using `programDefaults.roundingIncrement` / `roundingUnit` (or per-target overrides), so users see a calculated weight that updates as their training maxes change.
- Alternatives considered:
  - Single-value with max + note ("range was 75-80%") — rejected: loses structured information; runtime cannot display "150-160 lbs" without re-parsing the note.
  - Single-value with min + note — same issue.
  - Range object `{ min, max }` instead of paired keys — considered: works but creates inconsistency with the existing `rpeTarget.rangeMin/rangeMax` pattern, where range bounds are sibling keys.
  - Generate `setKind: "warmup"` placeholder setPrescriptions for each warm-up set — rejected: the source doesn't specify loads/reps per warm-up set; a placeholder with no meaningful target would fail the `targets minItems: 1` invariant and force a synthetic "house warm-up scheme" decision at import time. Phase 4 runner can interpret `warmupSetCount` to generate a runtime warm-up scheme without baking it into the resource.
  - Keep `warmupSetCount` as a free note — rejected: the user explicitly asked for a structured field; free notes are not queryable.
- Consequences:
  - schemaVersion-3 resources are NOT loadable by version 1 or 2 loaders. Phase 4 `ProgramResourceLoader` MUST refuse to activate any resource with `schemaVersion >= 3` until it supports range targets and warmup counts.
  - The extractor must normalize `percentMin == percentMax` to the single-percent form before emission (otherwise `target.percent_range_invalid` fires).
  - `schema/README.md` issue-code table grows by 5 codes (`target.percent_range_invalid`, `item.rest_range_invalid`, three `structure.*_schema_version_too_low`).
  - Power Building 1 import is regenerated at `schemaVersion: 3` with populated `percentMin/percentMax`, `restMaxSecondsHint`, `warmupSetCount`, and the "Includes N warm-up set(s)" free notes removed.
  - Future work (Phase 4 / runner): UI surfaces a calculated weight range (e.g. "150-160 lbs at 75-80%") when ranges are present; warm-up runner reads `warmupSetCount` and generates a default warm-up scheme using user-configurable progressions.
- Related docs/tests/code: `schema/program-resource.schema.json` (percentTarget, prescriptionItem, schemaVersion), `schema/semantics.ts` (`checkPrescriptionExtensions`), `schema/test/program-resource.semantics.test.ts` (11 new tests), `schema/test/program-resource.schema.test.ts` (8 new tests), `private/imports/power-building-1-v1.json` (first schemaVersion-3 resource).


## 2026-05-17: Distinguish runtime-pending references from import defects in validationStatus

- Status: Accepted
- Context: A program import that has cleared every operator-iterable defect can still carry first-week `reference.missing_first_week` criticals when training-max references are declared `supplied: false`. Those references are intentionally unsupplied at import time and are injected from the user profile by the runtime `ProgramResourceLoader` at activation. The previous `validationStatus` enum (`activatable | blocked | rejected`) collapsed two very different artifact states into `blocked`: 'the operator must iterate' vs 'the artifact is structurally complete and waiting for runtime references'. The user noticed this conflation while dogfooding Power Building 1 (simulated TM injection flipped activation to `activatable_with_warnings` with no further edits).
- Decision:
  - Add a fourth `validationStatus` value `pending_runtime_references` describing a structurally complete artifact whose only criticals are first-week unsupplied references.
  - Semantics (`checkPendingReferencesStatus`) gate the status:
    - If any critical other than `reference.missing_first_week` is present alongside the status, raise `status.pending_with_blocking_critical` (critical) — the resource is not really pending-only and should iterate as blocked.
    - If the status is declared but no pending-reference criticals exist, raise `status.pending_without_pending_refs` (warning) — the resource is in fact activatable and should declare so.
    - `status.activatable_with_critical` continues to fire ONLY for the `activatable` declared status; the new pending status is a separate, allowed home for the reference critical.
  - The activation gate is unchanged at the resource-loader level: `activatable` still requires zero criticals. Pending-references resources are NOT loadable as-is; the runtime must supply the references first, re-validate, and only then activate.
  - The validator script (`validate-resource.ts`) maps `pending_runtime_references` to a distinct `activationDecision` of the same name (exit code 1), so operators and CI can distinguish pending from blocked without parsing issue codes.
  - The importer / extractor sets `validationStatus = pending_runtime_references` automatically when the artifact's only criticals are first-week unsupplied training-max references. Otherwise it continues to set `blocked`.
- Rationale:
  - Preserves the schema invariant 'activatable means no criticals' while giving the importer a way to communicate 'the operator did their job; only runtime injection remains'.
  - Avoids downgrading `reference.missing_first_week` to a warning (which would silently bypass the activation gate if the loader regressed).
  - Avoids ad-hoc metadata fields on `validationIssue` (whose schema disallows extra properties).
- Alternatives considered:
  - Reclassify `reference.missing_first_week` to warning when `referenceType` is `training_max` — rejected: the invariant 'first-week missing max blocks activation' would silently weaken; loader bugs become silent.
  - Add an `activationGate` metadata field at the artifact root — rejected: parallels the existing `validationStatus` field and creates two sources of truth.
  - Leave the status as `blocked` and document the convention in the sidecar — rejected: the artifact's own status field should describe its own state without requiring the consumer to read the sidecar.
- Consequences:
  - `validationStatus` enum grows from 3 to 4 values; consumers that pattern-match on the enum must add the new case (no released loader exists; the extension is additive at schemaVersion 3).
  - `activationDecision` in the validator CLI grows from 4 to 5 values; exit code is 1 (same family as `activatable_with_warnings`).
  - `schema/README.md` issue-code table grows by 2 codes (`status.pending_with_blocking_critical`, `status.pending_without_pending_refs`).
  - Power Building 1 import regenerates with `validationStatus: pending_runtime_references` (the operator has nothing further to iterate; the runtime supplies the TMs).
- Related docs/tests/code: `schema/program-resource.schema.json` (validationStatus enum), `schema/semantics.ts` (`checkPendingReferencesStatus`), `schema/scripts/validate-resource.ts` (activationDecision mapping), `schema/test/program-resource.semantics.test.ts` (5 new tests), `schema/test/program-resource.schema.test.ts` (2 new tests), `private/imports/power-building-1-v1.json`.
