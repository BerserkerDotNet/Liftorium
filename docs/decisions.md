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