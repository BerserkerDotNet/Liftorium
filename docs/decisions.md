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

## 2026-05-25: Training max concept removed; 1RM is the only user-supplied reference

- Status: accepted
- Context: The schema, domain, and UI carried two parallel reference concepts: `training_max` (Wendler-style submaximal anchor, typically 85–90% of true 1RM) and `one_rep_max`. In practice the app collected one value per lift from the user, labeled it "Training max" in some surfaces and "1RM" in others, and stored it under the `training_max` enum slot for 5/3/1-derived programs. During dogfooding the user could not tell which value to enter (true max vs adjusted training max), and the BBB fixture's percentages were calibrated against TM rather than 1RM, producing inconsistent working weights.
- Decision: Remove the `training_max` concept from the product surface entirely. The schema `referenceType` enum is reduced to `["one_rep_max", "bodyweight"]`. The user always enters a true 1RM. Programs that traditionally used a separate training max (e.g., 5/3/1) must encode the training-max adjustment into the target percentages themselves — for example, BBB's "65% TM" of a 90%-TM becomes "0.585 × 1RM" expressed as a percent target. The bundled 5/3/1 BBB fixture has been recalibrated under this convention.
- Rationale: One concept, one label, one stored type is dramatically simpler for users to reason about, and removes a class of "what does this number mean" import-time defects. Authors of progression-style programs already think in terms of the user's true 1RM when designing percentages; baking the TM adjustment into the percentage column at authoring time is closer to the spreadsheet they're translating anyway.
- Alternatives considered:
  - Keep both enum values and let authors choose — rejected: the user can't pick correctly without reading the program author's intent, and the UI cannot disambiguate at entry time.
  - Auto-derive TM = 0.9 × 1RM internally — rejected: traditional 5/3/1 uses 0.9, but BBB variants, Texas Method, RTS, and others use different anchors. Hardcoding one ratio leaks a program assumption into the platform.
- Consequences:
  - Schema-breaking change to `referenceType` enum. No real-world resources exist yet so the migration cost is zero today; any future archived resource that referenced `training_max` will need to be re-imported.
  - All schema fixtures, examples, the BBB asset, and the schema/Kotlin `contentHash` constants have been regenerated.
  - Domain `ReferenceType.TrainingMax` removed; `RUNTIME_REQUIRED_REFERENCE_TYPES = setOf("one_rep_max")`. The activation gate still blocks on first-week missing 1RMs (the rule is renamed, not relaxed).
  - All UI strings ("Training max" / "% TM") replaced with "1RM" / "% 1RM". The pending-references dialog title is "Enter 1RMs".
  - Workstream `android-training-max-progression` renamed to `android-one-rep-max-progression`. `TrainingMaxService`/`TrainingMaxEntry` renamed to `OneRepMaxService`/`OneRepMaxEntry` in architecture docs.
  - Supersedes the relevant clauses of prior TM-bearing decisions: 2026-05-23 pending_runtime_references status (TM-specific language replaced with 1RM), 2026-05-23 per-program-run runtime reference injection (the entity stores 1RM values, not TM values). The activation/runtime semantics described in those ADRs are unchanged; only the label and stored enum value change.
- Related docs/tests/code: `schema/program-resource.schema.json`, `schema/examples/*`, `schema/fixtures/*`, `schema/test/program-resource.semantics.test.ts`, `android/app/src/main/assets/programs/example-5-3-1-bbb.json`, `android/domain/src/main/kotlin/dev/liftorium/domain/resource/ProgramResourceEnums.kt`, `android/domain/src/main/kotlin/dev/liftorium/domain/weight/MRound.kt`, `android/data/src/main/java/dev/liftorium/data/workout/RoomWorkoutLoggingRepository.kt`, `android/app/src/main/java/dev/liftorium/app/ui/workout/*`, `docs/workstreams/android-one-rep-max-progression.md`, `docs/architecture.md`, `docs/mvp-roadmap.md`, `Liftorium-Product-Requirements.md`.

## 2026-05-25: Active workout breadcrumb replaces internal run/version IDs in the UI

- Status: accepted
- Context: The first emulator-verified pass of the active workout screen rendered "Run \<UUID\>" and "Pinned version \<UUID\>" at the top of the screen — internal identifiers that are useful for debugging but meaningless to a user. The user requested a friendlier label.
- Decision: The active workout screen renders a breadcrumb `<programDisplayName> · Cycle <n> · Week <n> · <sessionDisplayName>` instead of raw IDs. The breadcrumb is computed in the data layer's `RoomWorkoutLoggingRepository` by joining the open session's `programRunId` → `LoadedProgramVersionEntity.displayName`, the planned `ScheduleOccurrenceEntity` row, and the corresponding `LoadedProgramBlock` (`blockOrder` → cycle) + `LoadedProgramWeek` (`weekIndex`) + `LoadedSessionTemplate` (`displayName`) rows. It surfaces as `WorkoutBreadcrumb` on the domain `WorkoutSessionAggregate` and is rendered via `ActiveWorkoutUiState.title` + `subtitle`.
- Rationale: Source every breadcrumb field from rows that already exist on disk so the label survives process restart for free — no new Room table, no new migration. The block-order-as-cycle simplification is acceptable for the MVP because every bundled program has exactly one block per cycle; multi-block-per-cycle programs are a future workstream concern.
- Alternatives considered:
  - Denormalize the breadcrumb onto the `WorkoutSession` row — rejected: would have required a Room migration and another sync-metadata audit step for a purely cosmetic field.
  - Compute the breadcrumb in the ViewModel from in-memory program-version state — rejected: process-death recovery would either lose the label or require re-loading the program version on every cold start, both worse than a Room join.
- Consequences:
  - `WorkoutSessionAggregate` gained an optional `breadcrumb: WorkoutBreadcrumb?` field. Existing tests that built aggregates by hand still compile because the field defaults to null.
  - The breadcrumb is null when the planned occurrence or any joined row is missing (defensive fallback for partially-loaded fixtures). The UI gracefully omits the subtitle in that case.
  - `cycleIndex` for multi-block-per-cycle programs is provisional and will be revisited when the `android-one-rep-max-progression` workstream lands proper cycle tracking on `ProgramRun`.
- Related docs/tests/code: `android/domain/src/main/kotlin/dev/liftorium/domain/workout/WorkoutSession.kt`, `android/data/src/main/java/dev/liftorium/data/workout/RoomWorkoutLoggingRepository.kt`, `android/app/src/main/java/dev/liftorium/app/ui/workout/ActiveWorkoutScreen.kt`, `android/app/src/main/java/dev/liftorium/app/ui/workout/ActiveWorkoutUiState.kt`.

## 2026-05-25: Excel-MROUND is the canonical weight-rounding formula

- Status: accepted
- Context: The original `RoomWorkoutLoggingRepository.buildTarget` had three independent bugs that surfaced when the user reviewed the first running workout: (1) it multiplied the schema-stored percent integer (0–100) by the reference weight without dividing by 100, producing 6500% targets; (2) it ignored conjunctive target rows (a set with both a percent and an RPE cap), so the displayed weight dropped its companion RPE; (3) it had no rounding step at all, producing fractional working weights like 204.75 lb. The user provided the exact formula they want, matching the BBB Excel workbook the program was imported from: `=IF(unit="kg", MROUND(value, 2.5), MROUND(value, 5))`.
- Decision: Introduce `dev.liftorium.domain.weight.mround(value: Double, multiple: Double): Double` as the single source of truth for weight rounding. It mirrors Excel's MROUND: half-away-from-zero rounding, with sign-aware tie-breaking for negative inputs. `buildTarget` divides percent by 100, merges conjunctive target rows via `firstNotNullOfOrNull` per orthogonal field, and rounds via `mround`. Rounding precedence is target `roundingIncrement`/`roundingUnit` → program `programDefaults.RoundingOverride` (decoded from the stored JSON blob on `LoadedProgramVersionEntity`) → per-unit fallback (5 lb / 2.5 kg). The bundled BBB fixture's `roundingIncrement` was corrected from 2.5 to 5 to match the spreadsheet.
- Rationale: One helper, no per-call-site rounding logic, deterministic test coverage. The user pinned the formula explicitly; mirroring Excel byte-for-byte (including sign behaviour) keeps imported programs producing the exact same working weights as the source spreadsheet, which is the single most-watched correctness criterion when importing real coaching programs.
- Alternatives considered:
  - Use `Math.round(...)` with manual scaling — rejected: rounds half-to-even on the JVM in some cases, which deviates from Excel and would silently drift on .5 boundaries.
  - Push rounding into a domain `WeightCalculator` service — deferred: the only current caller is `buildTarget`. Promote when a second caller appears, per the codebase's "no utility files until two callers" convention.
- Consequences:
  - BBB squat working sets at a 315 lb 1RM now show 205 / 235 / 270 lb (65/75/85%) and 160 lb (50% BBB back-off), matching the source workbook.
  - 10 unit tests in `MRoundTest` cover the BBB anchor values, kg 2.5 ties, negatives, and require-guard input rejection.
  - Conjunctive percent+RPE targets now display both the calculated weight and the RPE companion, satisfying the Target Specificity contract in `docs/workstreams/android-one-rep-max-progression.md`.
- Related docs/tests/code: `android/domain/src/main/kotlin/dev/liftorium/domain/weight/MRound.kt`, `android/domain/src/test/kotlin/dev/liftorium/domain/weight/MRoundTest.kt`, `android/data/src/main/java/dev/liftorium/data/workout/RoomWorkoutLoggingRepository.kt`, `android/app/src/main/assets/programs/example-5-3-1-bbb.json`.

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
- Consequences: The Program Construct Matrix is an upfront gate that all workstreams downstream of `program-resources` depend on.
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

## 2026-05-16: Android SDK targets and JDK toolchain

- Status: accepted
- Context: Foundation workstream requires recording one-time Android setup decisions in `docs/decisions.md` before Android scaffolding lands. Decisions.md previously fixed "Android 14 / API 34+" for runtime; the remaining values (`minSdk`, `compileSdk`, `targetSdk`, Gradle JDK toolchain) still needed to be locked.
- Decision: `minSdk = 30` (Android 11), `compileSdk = 34`, `targetSdk = 34`. Gradle JDK toolchain pinned to JDK 21 via `jvmToolchain(21)`. Gradle itself must run on JDK 21 to keep AGP 8.7 off whatever JDK happens to be on `PATH`; each contributor is responsible for ensuring Gradle launches under JDK 21 (preferred: set `org.gradle.java.home` in `$GRADLE_USER_HOME/gradle.properties`, or set `JAVA_HOME` before invoking `./gradlew`). The repo intentionally does not commit a machine-specific path in `android/gradle.properties`.
- Rationale: API 30+ covers the vast majority of active Android devices in 2026 while letting us use modern platform APIs without compatibility shims. JDK 21 is the long-term-supported toolchain that AGP 8.7 and Kotlin 2.0 both validate against; pinning `org.gradle.java.home` avoids the silent "wrong JDK on PATH" failure mode that AGP 8.x is known for.
- Alternatives considered: `minSdk = 24` (broader reach, but forces desugaring complexity for time APIs and unused on the target user base), `minSdk = 26`, JDK 17 toolchain (works but matures slower for Kotlin 2.x and Compose Compiler), leaving JDK selection to `PATH` (rejected — produces inconsistent builds across machines).
- Consequences: Android features may freely use Java 8+ time APIs (`java.time.*`) without core-library desugaring. Contributors must have JDK 21 installed and ensure Gradle launches under it (via `$GRADLE_USER_HOME/gradle.properties` or `JAVA_HOME`); the repo no longer pins a machine-specific path. API 34 platform + build-tools 34 must be installed via the Android SDK Manager before `assembleDebug`, `testDebugUnitTest`, or `connectedDebugAndroidTest` can succeed. The Kotlin/Java *bytecode target* on Android modules is pinned to JVM 17 (`compileOptions` + `kotlin.compilerOptions.jvmTarget = JVM_17`) even though the toolchain JDK is 21, because AGP 8.7 + D8 do not support JVM 21 bytecode for Android targets yet. Pure JVM modules (`:core`, `:domain`) compile at JVM 21.
- Related docs/tests/code: `android/gradle.properties`, `android/gradle/libs.versions.toml`, `android/build.gradle.kts`, `.github/skills/verification-loop/SKILL.md`.

## 2026-05-16: Android module layout (coarse)

- Status: accepted
- Context: The architecture document describes a fine-grained module map (`:core-schema`, `:core-time`, `:data-room`, `:domain-program`, `:domain-workout`, multiple `:feature-*` modules). Splitting into that many empty modules at scaffold time creates churn before any real code exists.
- Decision: The `foundation` workstream delivered a coarse four-module layout under `android/`: `:app` (`com.android.application`, Compose entry point), `:core` (pure `org.jetbrains.kotlin.jvm` shared utilities), `:data` (`com.android.library`, owns Room and `schemas/` export), `:domain` (pure `org.jetbrains.kotlin.jvm`, framework-free domain models and repository interfaces). Subsequent workstreams may split `:core` into `:core-schema` / `:core-time`, `:data` into `:data-room` / others, and `:domain` into per-feature domain modules as their code lands.
- Rationale: Pure-JVM `:domain` and `:core` modules mechanically prevent Android, Room, and Compose types from leaking into framework-independent code — the import simply does not resolve. A coarse start lets each downstream workstream introduce its own module split with real motivation rather than pre-committing to empty modules.
- Alternatives considered: Full architecture-map split at scaffold time (rejected as premature), single Android library module (rejected — domain code would lose its mechanical framework-independence guarantee).
- Consequences: Downstream workstreams own their own module-splitting work. The dependency direction (`:app` → `:data` and `:domain`; `:data` → `:domain`, `:core`; `:domain` → `:core` only) must be preserved when splits happen. The foundation `:domain` module includes a mechanical guard unit test that fails if Android, Room, or Compose runtime classes are resolvable from the `:domain` classpath; future splits must preserve an equivalent guard for every framework-free module.
- Related docs/tests/code: `android/settings.gradle.kts`, `android/domain/src/test/kotlin/`, `docs/architecture.md` (module map).

## 2026-05-16: Room schema export path

- Status: accepted
- Context: `docs/architecture.md` and `.github/copilot-instructions.md` both require exporting Room schemas from the first schema and never using destructive migrations.
- Decision: Room schemas are exported to `android/data/schemas/` via the KSP argument `room.schemaLocation`. This folder is checked into source control. Every Room database class in `:data` must keep `exportSchema = true`.
- Rationale: Co-locating exported schemas with the module that owns them keeps migration tests in the same module that defines the `@Database`. Source-controlled schema JSON is the input to migration regression tests in every later workstream.
- Alternatives considered: `android/schemas/` at the Android-project root (rejected — couples a Room-specific path to the Android root), suppressing schema export (rejected — directly violates the destructive-migration prohibition).
- Consequences: A `:data` Room migration test command is registered as gated until the first `@Database` exists. Every Room schema bump must include a checked-in migration plus updated schema JSON. Schema folder may be empty during foundation (no entities yet); that is documented as gated, not as evidence.
- Related docs/tests/code: `android/data/build.gradle.kts`, `android/data/schemas/`, `.github/skills/verification-loop/SKILL.md`.

## 2026-05-16: Web stack scaffold

- Status: accepted
- Context: `docs/decisions.md` already accepts React + TypeScript + Vite for the Web MVP. foundation needs the concrete tooling stack pinned so verification commands are real.
- Decision: Web project under `web/` is scaffolded with Vite + React + TypeScript strict (`strict: true`, `noUncheckedIndexedAccess: true`), Vitest + jsdom + React Testing Library for unit/component tests, and no Playwright in foundation. Runtime validation of snapshot/resource inputs will be added when Web data clients are introduced (web-readonly); the read-only guard verification command is therefore registered as gated until web-readonly.
- Rationale: Vitest covers all current testing needs (no Web data clients exist yet); deferring Playwright avoids committing to a browser test runtime before there are real user flows to test. `noUncheckedIndexedAccess` matches the "avoid `any`; narrow `unknown`" guidance in `.github/copilot-instructions.md`.
- Alternatives considered: Vitest + Playwright in foundation (rejected — no Web UI to exercise), Jest instead of Vitest (rejected — slower, doesn't share Vite's transform pipeline), TS strict mode without `noUncheckedIndexedAccess` (rejected — weaker guarantees for snapshot/resource consumers).
- Consequences: Web typecheck, Vitest, and production build are registered as passing now. The read-only guard is registered as gated until the first Web data client exists.
- Related docs/tests/code: `web/package.json`, `web/tsconfig.json`, `web/vitest.config.ts`, `.github/skills/verification-loop/SKILL.md`.

## 2026-05-16: Schema validator runtime

- Status: accepted
- Context: `schema/` will house the versioned program-resource JSON Schema and fixtures starting in program-resources. foundation must provide a runnable validation harness so program-resources has an existing place to drop the real schema and reports.
- Decision: Schema validation runs in Node via [Ajv](https://ajv.js.org/) (`ajv` + `ajv-formats`) plus Vitest. The harness lives under `schema/` with its own `package.json`. The schema delivered by the `foundation` workstream is a real skeleton encoding the contract fields (`schemaVersion`, `programId`, `programVersionId`, `versionLabel`, `validationStatus`, `validationIssues[]`, `importAudit`); the `program-resources` workstream will expand it with full program structure.
- Rationale: Node + Ajv keeps the toolchain unified with the Web project (no Python prerequisite). Vitest gives consistent test semantics across both Web and schema validators. A real (not vacuous) skeleton means program-resources cannot accidentally regress the activation contract.
- Alternatives considered: Python + jsonschema (rejected — adds a second runtime), no schema scaffold this phase (rejected — file-presence verifier failure mode).
- Consequences: `schema/` has its own `package.json` and `npm test`. program-resources expands the schema and fixtures inside the same harness. Schema validation is registered as passing now.
- Related docs/tests/code: `schema/program-resource.schema.json`, `schema/fixtures/`, `schema/package.json`, `.github/skills/verification-loop/SKILL.md`.

## 2026-05-16: DI framework and foreground service type deferred

- Status: accepted
- Context: The android-implementation skill lists "foreground service type for rest timers" and "dependency composition mechanism" among one-time setup decisions. foundation does not exercise either: there is no rest timer code yet (android-rest-timers owns it) and no service/use case wiring beyond an empty `App` composable.
- Decision: The `foundation` workstream does NOT pick a DI framework. Manual dependency composition (factories / hand-wired) is the default until a later workstream proposes a framework. `foundation` does NOT pick a foreground-service type for rest timers; that decision is deferred to the `android-rest-timers` workstream.
- Rationale: Locking these in before any consumer code exists invites churn. The current code surface (an empty `App` composable + a Room scaffold with no `@Database`) needs neither.
- Alternatives considered: Pre-committing to Hilt for DI (rejected — premature), pre-picking `mediaPlayback` / `dataSync` foreground service type (rejected — out of scope for foundation).
- Consequences: android-rest-timers must open the foreground-service-type decision before timer implementation lands. Whichever workstream first needs cross-module dependency wiring beyond constructor arguments must record a DI decision before adding a framework.
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


## 2026-05-16: Program-resource schema (semantics, hash, discriminated targets, sourceKind)

- Status: accepted
- Context: The `foundation` workstream left `schema/program-resource.schema.json` as a contract skeleton: it locked schemaVersion, IDs, validationStatus, validationIssues, and importAudit, but kept the substantive program content (exerciseCatalog, programStructure, progressionRules) as permissive open objects. The `program-resources` workstream had to land the substantive contract that downstream workstreams (import, Android ProgramResourceLoader, Web read-only) consume, plus the activation-severity rules that JSON Schema alone cannot express.
- Decision:
  1. **Tighten in place at `schemaVersion = 1`** instead of bumping to `2`: no production resource shipped under the foundation skeleton, so there are no consumers to break. The README's general "bump on every change" guidance now scopes to post-Phase-2 changes.
  2. **Two-stage validation**: structural Ajv-strict + a new pure-function semantic validator (`schema/semantics.ts`) covering cross-field rules. Activation requires both stages to pass (no semantic-critical issues) AND `validationStatus = "activatable"`. Resources with `validationStatus = "blocked"` or `"rejected"` are never activatable, even with an empty issue list.
  3. **Stable dot-namespaced issue codes** (`schema.*`, `metadata.*`, `status.*`, `catalog.*`, `structure.*`, `exercise.*`, `reference.*`, `construct.*`, `provenance.*`). Unknown codes default to `critical` per the Program Construct Matrix; the semantic validator emits `construct.must_be_critical` / `construct.severity_understated` / `construct.severity_overstated` when an importer disagrees with the matrix.
  4. **Prescription targets as a discriminated union** keyed by `kind` (`exact_load_reps` | `rep_range` | `percent` | `rpe` | `rir`). Each variant carries its own `additionalProperties: false` so downstream Kotlin/TypeScript consumers can switch exhaustively.
  5. **`progressionRules[].parameters` stays open** in program-resources. Rule introspection is the Android program runner's job (android-program-runner/6); the validator only checks shape.
  6. **`metadata.contentHash` is required and is the loader-side identity check**. The `program-resources` workstream ships `computeProgramResourceContentHash` + a `metadata.content_hash_mismatch` self-consistency check; the cross-resource "same versionId, different hash = conflict" rule is implemented in the android-program-runner loader. The hash covers `metadata` (minus `contentHash`), `programDefaults`, `exerciseCatalog`, `requiredReferences`, `programStructure`, `progressionRules`; it deliberately excludes `validationStatus`, `validationIssues`, `importAudit` so re-validating never changes the hash.
  7. **`importAudit.sourceKind` discriminator** (`synthetic` | `private_import`). `synthetic` permits the all-zero SHA-256 sentinel so fixtures and examples never need to be associated with a real private spreadsheet. `private_import` requires a real 64-hex SHA-256 and `consentGranted = true`; missing either emits a `provenance.*` warning. Full per-import consent enforcement is import-workflow.
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
  - import-workflow consumes the issue-code namespace, the `sourceKind` discriminator, and the construct severity classifier; android-program-runner `ProgramResourceLoader` consumes `metadata.contentHash` for cross-resource conflict detection.
  - Generated Kotlin / TypeScript model types are still out of scope; the loader (android-program-runner) and Web (web-readonly) workstreams own their own consumer types.
- Related docs/tests/code: `schema/program-resource.schema.json`, `schema/hash.ts`, `schema/semantics.ts`, `schema/validator.ts`, `schema/test/program-resource.schema.test.ts`, `schema/test/program-resource.semantics.test.ts`, `schema/test/hash-freshness.test.ts`, `schema/fixtures/`, `schema/examples/example-5-3-1-bbb.json`, `schema/scripts/refresh-fixture-hashes.mjs`, `schema/README.md`, `docs/workstreams/program-resources.md`, `docs/architecture.md` (Program Construct Matrix + Validation severity contract + JSON resource versioning contract), `.github/skills/verification-loop/SKILL.md`.
## 2026-05-16: Import workflow surface and scope

- Status: superseded by "2026-05-17: Import workflow reset to Copilot skill"
- Context: The `foundation` workstream left `tools/import/` as a placeholder. The `program-resources` workstream shipped the program-resource schema, semantic validator, content-hash helper, and the `construct.*` / `provenance.*` issue-code namespaces. The `import-workflow` workstream must replace the placeholder with the real import pipeline that A6 (workbook → activatable resource) and A7 (operator correction loop) require, without inventing parallel contracts.
- Decision: Build `tools/import` as a library-only TypeScript package that adopts ExcelJS (MIT) for `.xlsx` parsing, exposes `runImport(request)` / `applyCorrection(request, correction)` / `finalize(result)`, requires the caller to supply `importedAtUtc` and an optional `existingVersionsLookup`, and reuses the program-resources `schema/` package directly. No CLI binary, no LLM client wired (the Copilot session is the cloud-assisted processor), no end-to-end `.xlsx` fixtures committed.
- Rationale: A library-only surface keeps the importer testable from Vitest and avoids a parallel CLI that would duplicate the JS API. ExcelJS handles formulas, merged ranges, and shared strings without a native dependency and is permissively licensed. Reusing `buildProgramResourceValidator()`, `validateProgramResourceSemantics()`, and `computeProgramResourceContentHash()` keeps construct-matrix severity and content-hash identity in exactly one source of truth (`schema/`). Caller-supplied `importedAtUtc` is required so identical inputs reproduce byte-identical canonical JSON (CLI-IMP-002). The consent gate keys off `processingIntent` (not on parse), matching `docs/architecture.md` lines 511-519 ("consent denied → local metadata/structure extraction only"). The `existingVersionsLookup` guard is advisory: the importer emits `metadata.version_conflict` and throws `VersionConflictError`, but android-program-runner's `ProgramResourceLoader` remains the authoritative cross-resource gate. The correction model covers the full A7 override surface (`exerciseApprovals`, `aliasMappings`, `weekOrderOverrides`, `sessionOrderOverrides`, `ignoredRows`, `referenceValueOverrides`, `constructClassificationOverrides` matrix-permitted-only, `noteOnlyApprovals`) so a single rerun reflows the whole pipeline.
- Alternatives considered: a Node CLI binary (rejected — duplicates the JS API for no consumer; tests already exercise the library directly); SheetJS / `xlsx` (rejected — licensing friction); committing `.xlsx` fixtures (rejected — repo bloat + privacy risk; constructed `WorkbookModel`s are sufficient since downstream tests don't exercise ExcelJS parsing); wiring a real LLM client (rejected — the Copilot session IS the cloud-assisted processor; the importer's job is to enforce and record consent, not call out); maintaining a parallel construct-severity table in the importer (rejected — every code must come from `schema/semantics.ts` to avoid drift); auto-creating exercise catalog entries for unapproved candidates (rejected — would mask `exercise.unknown_reference` from the semantic validator and let unknown exercises silently activate).
- Consequences:
  - `tools/import/` ships a library with the architecture documented in `tools/import/README.md`: parser → detect → normalize → validate → correct → pipeline.
  - `WorkbookModel` is the only surface where raw cell/formula text is allowed; the `ValidationReport`, in-resource `validationIssues[]`, and any other exported field never contain source excerpts (enforced by `test/report-privacy.test.ts` with sentinel strings).
  - `tools/import` declares Ajv + ajv-formats + ExcelJS as runtime deps so Vitest can resolve them under the package; `@types/node` matches the `schema/` package.
  - 63 tests pass under `cd tools\import; npm test` covering every A6/A7 mapped ID (UT-IMP-001..005, UT-JSON-001/002, IT-IMP-001..006, CLI-IMP-001/002) plus the parser, prescription patterns, structure detection, report contract, and privacy boundary.
  - `.github/skills/verification-loop/SKILL.md` promotes the "Import report validation" row from gated to `cd tools\import; npm test`.
  - android-program-runner `ProgramResourceLoader` consumes finalized resources directly; it does not depend on `tools/import` at runtime.
- Related docs/tests/code: `tools/import/README.md`, `tools/import/src/`, `tools/import/test/`, `schema/program-resource.schema.json`, `schema/semantics.ts`, `schema/hash.ts`, `schema/validator.ts`, `docs/architecture.md` (import workflow + Program Construct Matrix + Validation severity contract + JSON resource versioning contract), `docs/product.md` A6/A7, `docs/workstreams/import-workflow.md`, `.github/skills/verification-loop/SKILL.md`, `.github/skills/import-report-check/SKILL.md`.

## 2026-05-17: Import workflow reset to Copilot skill

- Status: accepted
- Context: The previous 2026-05-16 import-workflow decision built `tools/import/` as a TypeScript runtime library (parser/detect/normalize/validate/correct/pipeline). On 2026-05-17 the user clarified the actual A6/A7 contract: A6 says "Developer/operator runs spreadsheet-first **Copilot** import" and A7 says "Developer/operator reviews, **chats with Copilot** to correct issues". The Copilot session IS the importer. A runtime library duplicates what the skill+Copilot do directly, and the typed `Correction` API contradicts A7's conversational correction loop. Additionally, the user clarified that the Android input endpoint (lifter picks a JSON file and loads it into Room) belongs to the `program-resources` workstream (android-program-runner) where `ProgramResourceLoader` and the Room program tables live.
- Decision: Reset import-workflow to a skill-only deliverable. (1) Delete `tools/import/src/` and `tools/import/test/`; leave only a `README.md` pointing at the skill. (2) Create `.github/skills/import-workflow/SKILL.md` with the full A6/A7 conversational procedure: hard pre-bytes consent gate, privacy boundary contract, workbook orientation (ad-hoc Python openpyxl / Node ExcelJS — nothing committed), construct detection against `KNOWN_*_CONSTRUCT_CODES` in `schema/semantics.ts`, exercise approval table (operator decision required before finalization), deterministic ID/ordering rules, JSON composition with `metadata.contentHash` via `schema/hash.ts`, validation via `schema/scripts/validate-resource.ts`, conversational correction loop, twice-rerun determinism check, and two-artifact finalization (`<programVersionId>.json` + `<programVersionId>.import-report.json`). (3) Add `schema/scripts/validate-resource.ts` (run via `npm run validate:resource -- <path>`) that wraps `buildProgramResourceValidator()`, `validateProgramResourceSemantics()`, and `computeProgramResourceContentHash()`, prints the privacy-safe 6-per-issue + 5-report-level fields contract owned by `.github/skills/import-report-check/SKILL.md`, and exits 0/1/2/3 for activatable / activatable_with_warnings / blocked / rejected. (4) Mark CLI-IMP-001, CLI-IMP-002, UT-IMP-004, UT-IMP-005, IT-IMP-001, IT-IMP-004, IT-IMP-005 in `docs/product.md` as "verified via skill procedure" with explicit Step references; keep UT-IMP-001/002/003, UT-JSON-001/002, IT-IMP-002/003 mapped to schema-package tests + validator-CLI exit-code coverage on `schema/fixtures/`. (5) Move the Android input endpoint to `docs/workstreams/program-resources.md` outputs as a android-program-runner deliverable; explicitly note in `docs/workstreams/import-workflow.md` that user-facing import on Android is owned by `program-resources`. (6) The import-workflow → android-program-runner handoff is two artifacts; the Android loader MUST revalidate independently and MUST treat same `programVersionId` + different `contentHash` as a conflict.
- Rationale: A skill matches the literal text of A6/A7 ("Copilot import", "chats with Copilot"). A typed runtime importer would always be a second implementation alongside Copilot's session-level work, and the typed `Correction` API actively conflicts with the conversational correction loop the spec calls for. Keeping `tools/import/` empty removes a maintenance burden and a class of bugs (parallel construct severity tables, type-vs-schema drift, library/skill divergence). The validator CLI is the one piece of code that genuinely belongs in code — Copilot cannot reliably recompute a 64-character canonical content hash in chat. Placing the CLI under `schema/` (not `tools/import/`) keeps it close to the helpers it wraps and avoids reintroducing a `tools/import/` package. The two-artifact finalization (JSON + sidecar) gives android-program-runner a privacy-safe audit trail without bundling source content. Moving the Android input endpoint to android-program-runner is the natural boundary: that endpoint depends on Room program tables and `ProgramResourceLoader`, both android-program-runner responsibilities. Coverage-review traceability is preserved: every A6/A7 mapped test ID either still has a real test (schema package + validator CLI exit-code coverage) or an explicit skill-procedure mapping with a Step reference; user explicitly approved this traceability change (`private_fixtures = retire_recreate` on 2026-05-17).
- Alternatives considered: keep the TypeScript runtime library and add a skill on top (rejected — duplicates work; correction-loop API contradicts A7); keep a thin TypeScript helper under `tools/import/` for xlsx-to-cells dumping (rejected — Copilot can use openpyxl / one-off ExcelJS scripts ad hoc and that keeps `tools/import/` truly empty); build the Android input endpoint in this phase (rejected — depends on android-program-runner Room schema and `ProgramResourceLoader` which don't exist; would expand import-workflow to cover roughly half of android-program-runner); add a stub Room table that stores the raw JSON blob in import-workflow (rejected — would create a migration the android-program-runner loader has to undo); persist `private_fixtures = keep_validator_tests` (rejected — most tests asserted behaviors of code that is now deleted); validator CLI under `tools/import/` (rejected — would resurrect a package directory the reset is explicitly emptying).
- Consequences:
  - `tools/import/` contains only `README.md` pointing at the skill. No `package.json`, no source, no tests.
  - `.github/skills/import-workflow/SKILL.md` is the authoritative procedure. The skill's discovery description is precise so future Copilot sessions handling an `.xlsx` find it and so it is NOT used for Android loader work or report-only review.
  - `schema/scripts/validate-resource.ts` is the only piece of import-workflow code. Invoked via `cd schema; npm run validate:resource -- <path>`. Exit code 0=activatable, 1=activatable_with_warnings, 2=blocked, 3=rejected, 4=usage error. Supports `--json` for the sidecar artifact.
  - `schema/package.json` gains `tsx` as a devDependency (TypeScript loader for the CLI under Node 22) and a `validate:resource` script.
  - `docs/product.md` A6/A7 traceability rows annotate every test ID with either its existing schema-package home or its skill-procedure Step reference.
  - `docs/workstreams/import-workflow.md` outputs now read "skill + validator CLI + procedure"; android-program-runner handoff lists the two artifacts and the loader's revalidation/conflict obligations.
  - `docs/workstreams/program-resources.md` outputs gain the Android input endpoint + `ProgramResourceLoader` as android-program-runner deliverables, replacing the blanket "user-facing import is out of MVP" restriction with a constrained file-picker import that consumes pre-validated JSON only.
  - `.github/skills/verification-loop/SKILL.md` "Import report validation" row points at `cd schema; npm run validate:resource -- <path>` and `cd schema; npm test`.
  - `cd schema; npm test` is now 57/57 green (the pre-existing 4 failures were fixture-hash staleness and were refreshed via `npm run refresh-fixture-hashes` during this reset).
  - android-program-runner has a concrete handoff contract: receives JSON + sidecar; MUST revalidate independently against schema + semantics + recomputed `contentHash`; MUST reject same `programVersionId` + different `contentHash`; MUST NOT read original spreadsheets.
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
  6. **Vacuous-pass guard for Web.** With `include: ['src/data/**', 'src/domain/**']` and no matching files, vitest reports 0/0 = 100% (vacuous pass). `web/scripts/coverage-guard.mjs` runs before vitest: exits 0 with a message if neither dir exists (import-workflow baseline); exits 1 if either dir exists but contains zero source files (gate silently neutered); otherwise execs vitest.
  7. **New `test-design` skill (`.github/skills/test-design/SKILL.md`).** Auto-discovers when Copilot is about to add a new test file, new top-level `describe` block / test class, or substantially expand a behavioral test matrix in domain or data code. The skill runs a 6-step procedure: frame the code under test, select 3–4 of 8 scenario dimensions, spawn parallel `explore` sub-agents (one per dimension), consolidate with one `rubber-duck` agent, produce a reviewable test matrix table mapped to acceptance IDs, then implement and verify coverage. UI tests, trivial test edits, and acceptance-traceability audits are explicitly out of scope (those route to `*-implementation` / `coverage-review`).
- Rationale: A measurable percentage gate prevents silent erosion of test coverage. Domain/data-only scope keeps UI snapshot testing (which has its own discipline via Paparazzi and the visual-review loop) out of the gate; UI tests would otherwise distort the metric in either direction (snapshot tests inflate line coverage cheaply; failed snapshots block unrelated work). The Exclusion Contract codifies the small number of legitimate escape hatches so future contributors don't have to relitigate them. The parallel-explore + rubber-duck design for `test-design` separates brainstorm (where breadth matters) from consolidation (where dedup, gap-flagging across the full 8 dimensions, and traceability matter), and produces a reviewable matrix BEFORE test code is written so the user can correct framing cheaply.
- Alternatives considered:
  - **Lower gate (e.g. 80%).** Rejected: too easy to drift toward without resistance.
  - **Per-file thresholds with overrides.** Rejected: invites bargaining; the Exclusion Contract is a more principled escape hatch.
  - **CI-enforced from day one.** Deferred: scripts-only enforcement via `verification-loop` is the import-workflow baseline; CI lands later without changing the rules.
  - **Equivalent counter mapping between vitest and Kover.** Rejected: Kover has no METHOD counter; pretending otherwise would hide the divergence. Documenting it honestly is better.
  - **Single test-design agent (no parallelism).** Rejected: parallel explore agents cover the dimension space faster; the rubber-duck consolidation reclaims the dedup/gap-flagging benefit a single agent would have given.
  - **Test-design triggered only on "non-trivial logic" with no concrete criteria.** Rejected: the skill description now uses concrete triggers ("new test file, new top-level describe / test class, substantial expansion") with explicit anti-triggers ("renames, formatting, one missing assertion, updating expected text").
- Consequences:
  - `schema/vitest.config.ts` enforces 95/95/95/95 on `validator.ts`, `semantics.ts`, `hash.ts`. import-workflow result: 100% statements/lines/functions, 99.63% branches. The remaining 0.37% uncovered branch (`semantics.ts` `isContiguousFromOne([])` early return) is unreachable from existing callsites; gate still passes.
  - A real bug surfaced and was fixed during this work: `schema/semantics.ts` line 71 crashed on `null` `validationIssues` entries; defensive `isObject(i) &&` guard added (documented in checkpoint history).
  - `web/vite.config.ts` and `web/scripts/coverage-guard.mjs` activate the gate when android-program-runner+ adds `src/data/` or `src/domain/`. Today the guard reports "no coverage targets yet" and exits 0.
  - `android/build.gradle.kts` applies Kover at the root project (NOT `apply false`) with aggregation dependencies on `:core`/`:data`/`:domain`; modules apply Kover individually. `./gradlew koverVerify` passes today: `:core` reports 100% from `TimeSource`'s unit test; `:data`/`:domain` report "No sources" (their current code is behaviorless markers, correctly classified by Kover). Gate activates as android-program-runner+ adds real domain/data behavior.
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
  - android-program-runner `ProgramResourceLoader` does NOT need to read or check `consentGranted` (the field is gone).
  - Supersedes: the 2026-05-15 `Cloud-assisted import with explicit consent` decision (the consent-gate model is no longer in force) and item #7 of the 2026-05-16 `program-resources program resource schema, validator, fixtures, examples` decision regarding `consentGranted = true` enforcement. The `sourceKind` discriminator and the `private_import` / `synthetic` distinction are retained.
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
  - Schema-version-2 resources are NOT loadable by variant-unaware loaders. android-program-runner `ProgramResourceLoader` MUST refuse to activate any resource with `schemaVersion >= 2` until it supports variants — documented in the schema description and this decision.
  - The validator now treats a TM consumed only in a variant of week 1 as critical (was warning). This is a behavioral change for `reference.missing_first_week` / `missing_later_week` only when variants are used — variant-free resources are unaffected.
  - `schema/README.md` issue-code table grows by 6 codes (all `structure.*`).
  - Future work (android-program-runner / runner): document the variant choice UX and ensure the choice is recorded in `WorkoutSession` so progression engine and stats reflect the picked variant only.
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
  - Generate `setKind: "warmup"` placeholder setPrescriptions for each warm-up set — rejected: the source doesn't specify loads/reps per warm-up set; a placeholder with no meaningful target would fail the `targets minItems: 1` invariant and force a synthetic "house warm-up scheme" decision at import time. android-program-runner runner can interpret `warmupSetCount` to generate a runtime warm-up scheme without baking it into the resource.
  - Keep `warmupSetCount` as a free note — rejected: the user explicitly asked for a structured field; free notes are not queryable.
- Consequences:
  - schemaVersion-3 resources are NOT loadable by version 1 or 2 loaders. android-program-runner `ProgramResourceLoader` MUST refuse to activate any resource with `schemaVersion >= 3` until it supports range targets and warmup counts.
  - The extractor must normalize `percentMin == percentMax` to the single-percent form before emission (otherwise `target.percent_range_invalid` fires).
  - `schema/README.md` issue-code table grows by 5 codes (`target.percent_range_invalid`, `item.rest_range_invalid`, three `structure.*_schema_version_too_low`).
  - Power Building 1 import is regenerated at `schemaVersion: 3` with populated `percentMin/percentMax`, `restMaxSecondsHint`, `warmupSetCount`, and the "Includes N warm-up set(s)" free notes removed.
  - Future work (android-program-runner / runner): UI surfaces a calculated weight range (e.g. "150-160 lbs at 75-80%") when ranges are present; warm-up runner reads `warmupSetCount` and generates a default warm-up scheme using user-configurable progressions.
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


## 2026-05-17: Android runtime validation strategy = recheck_only

- Status: accepted
- Context: Earlier android-program-runner contracts (in `docs/workstreams/program-resources.md` and `docs/workstreams/android-program-runner.md`) said the Android loader would re-run "schema + semantics + content-hash + cross-resource conflict check". Porting `schema/semantics.ts` to Kotlin would duplicate ~700 lines of TS into Kotlin, fork the source of truth, and risk drift on every schema change. The import-workflow TS validator (`schema/scripts/validate-resource.ts` + `schema/semantics.ts`) is the canonical activation gate; an artifact whose `validationStatus` is `activatable` or `pending_runtime_references` and whose `contentHash` matches the canonical recomputation has already cleared every semantic check. The only failure modes left at runtime are (a) file corruption, (b) a malicious or accidental edit after finalization, and (c) the device app being older than the schema the artifact was produced against.
- Decision: The Android `ProgramResourceLoader` performs a `recheck_only` validation pass, not a full semantic re-run:
  1. Assert `schemaVersion` is in the supported range for this app build.
  2. Recompute the canonical `contentHash` and assert it matches `metadata.contentHash` byte-for-byte.
  3. Assert `validationStatus ∈ {activatable, pending_runtime_references}`. `blocked` and `rejected` artifacts are refused.
  4. For `pending_runtime_references` artifacts, the loader must collect runtime values for every `requiredReference` whose `supplied=false`, `firstRunnableWeekIndex == 1`, and `referenceType ∈ {training_max, one_rep_max}`, AND assert that no other `severity=critical` issue exists in `validationIssues`. The loader never rewrites `validationStatus` or `validationIssues` on the persisted version.
  5. Same `programVersionId` + different `contentHash` = conflict (immutable program version identity is preserved).
  import-workflow's TS validator stays the single source of truth for semantic activation rules. The Kotlin loader does NOT port `schema/semantics.ts`.
- Rationale: Eliminates the duplicate-validator drift risk; keeps the device-side loader small, deterministic, and dependency-light; lets future schema-rule changes ship as a single import-workflow TS update plus an app schemaVersion bump, not coordinated TS+Kotlin edits.
- Alternatives considered:
  - Port `schema/semantics.ts` to Kotlin and run it on-device (rejected: drift risk and ~700 LOC duplication).
  - Run the TS validator on-device via JS engine (rejected: no JVM JS runtime in MVP toolchain; adds APK weight and a new failure mode).
  - Trust the artifact entirely with no recheck (rejected: file corruption and tampering are real; the content-hash recompute is cheap and catches both).
- Consequences:
  - Loader contract simplifies to schema-version + hash + status + pending-refs check. No on-device semantic re-validation.
  - The 95% coverage gate on the loader is measured on the recheck steps, not on a Kotlin port of semantics rules that don't exist.
  - Tampered artifacts (post-finalization edits) are caught by the hash mismatch and rejected as a structured `ContentHashMismatch` error.
  - If a future schema change introduces a semantic rule the device-side loader cannot enforce, that rule must be enforced upstream by the TS validator before the artifact is shipped to the device.
- Related docs/tests/code: `docs/workstreams/program-resources.md`, `docs/workstreams/android-program-runner.md`, `android/data/src/main/java/dev/liftorium/data/resource/ProgramResourceLoader.kt`, `android/data/src/test/java/dev/liftorium/data/resource/ProgramResourceLoaderTest.kt`, `schema/scripts/validate-resource.ts`, `schema/semantics.ts`.

## 2026-05-17: Pending-reference runtime values are run-scoped, not version-scoped

- Status: accepted
- Context: When an artifact is `pending_runtime_references`, the user must supply training-max or 1RM values before the program can be activated. The naive design would mutate the loaded `LoadedRequiredReferenceEntity` row (set `supplied=true`, `value`, `unit`) and flip `validationStatus` to `activatable`. That breaks two invariants: (1) loaded program versions are immutable (same `programVersionId` ⇒ same persisted shape), and (2) the same loaded version can power multiple program runs (e.g. a future "restart with new TMs" flow) which would each need their own runtime values.
- Decision: Runtime-injected reference values live in a new entity `ProgramRunReferenceValueEntity(programRunId, referenceId, value, unit, source, suppliedAtUtc)`, scoped to `ProgramRunEntity`, not to the loaded version. The `LoadedRequiredReferenceEntity` rows preserve the import-time shape verbatim (`supplied=false`, `value=null`, `unit=null` where applicable). At workout-start time the program runner joins `LoadedRequiredReferenceEntity` with `ProgramRunReferenceValueEntity` (filtered by `programRunId`) to resolve the effective value. The loaded version row and its `validationStatus` / `validationIssues` are NEVER rewritten by the runtime.
- Rationale: Preserves immutable program-version identity; lets a single loaded version power multiple runs with different TMs (e.g. cutting vs. bulking restart) without re-loading; keeps the persisted shape isomorphic to the artifact's canonical form, so a future "export this run" feature can recompute the hash trivially.
- Alternatives considered:
  - Mutate `LoadedRequiredReferenceEntity` in place (rejected: breaks immutability; same-id-different-content collision rule weakens).
  - Store injected values in a JSON blob on `ProgramRunEntity` (rejected: harder to query, no FK to referenceId, no per-value timestamps).
  - Store on a separate `UserProfileTrainingMaxEntity` keyed by `exerciseId` (rejected: TMs are program-run scoped — the same user may run two different programs with different TMs in flight, and "restart this run with new TMs" must not affect another run).
- Consequences:
  - android-program-runner Slice 2 adds a new `ProgramRunReferenceValueEntity` and a join helper on the runner repository.
  - Activation flow becomes: pick file → loader validates → if `pending_runtime_references`, prompt user → write `ProgramRunReferenceValueEntity` rows in the same transaction as `ProgramRunEntity` → mark run `Active`.
  - The `checkPendingReferencesStatus` rule on the loader operates on the in-memory composite (loaded refs + run-scoped injections) at start-time, not on the persisted `validationStatus`.
  - Restart with new TMs is a new `ProgramRunEntity` with fresh `ProgramRunReferenceValueEntity` rows; the loaded version row is unchanged.
- Related docs/tests/code: `docs/workstreams/android-program-runner.md` (Outputs `Program run entities` and `Pending-references activation flow`), `android/data/src/main/java/dev/liftorium/data/run/` (Slice 2), Slice 2 use cases `StartProgramRun` / `RepeatProgramRun`.

## 2026-05-17: One active program run enforced by DB unique index on activeRunSlot

- Status: accepted
- Context: `docs/workstreams/android-program-runner.md` mandates "one active program run at a time in MVP". The intuitive enforcement is a use-case guard that queries `ProgramRunEntity WHERE status='Active'` before insert. That is racy: two concurrent `StartProgramRun` invocations (e.g. process death recovery + manual restart) can both pass the check and both insert. `UNIQUE(status)` cannot work because `Completed` and `Abandoned` rows must coexist freely.
- Decision: Add `activeRunSlot INTEGER NULL` to `ProgramRunEntity` with a `UNIQUE INDEX` on it. The use cases set `activeRunSlot = 1` only when `status = Active`, and explicitly null it on transitions to `Completed` or `Abandoned`. Concurrent inserts of two `Active` rows fail at the SQL layer with a unique-constraint violation, which the repository converts to a typed `AlreadyActiveRun` error. The use-case-level pre-check stays as a friendly UX but is no longer the source of truth.
- Rationale: A DB-level invariant survives process death, app restart, concurrent coroutines, and future cross-thread bugs. `activeRunSlot` is a tiny, dedicated column with no other semantics; the partial-uniqueness shape (`UNIQUE` ignores NULL in SQLite) gives us "at most one Active row" without forcing a soft-delete of completed rows.
- Alternatives considered:
  - Use-case guard only (rejected: racy under concurrency and process-death recovery).
  - `UNIQUE(status)` (rejected: only one `Completed` row would ever be allowed).
  - Compute `activeRunSlot` from `status` via a SQL trigger (rejected: triggers are harder to test through Room and the explicit column is cheap).
  - Use a separate `ActiveProgramRunPointer` row (rejected: extra table for a single nullable pointer; harder to keep transactionally consistent with the run row's `status`).
- Consequences:
  - `ProgramRunEntity` schema adds `activeRunSlot INTEGER NULL` plus a unique index (Slice 2).
  - `StartProgramRun` inserts the run with `status=Active, activeRunSlot=1` in a Room transaction; `RepeatProgramRun` does the same on the new run after the previous run's slot is nulled in the same transaction.
  - `AbandonProgramRun` and `CompleteProgramRun` set `activeRunSlot=NULL` in a transaction.
  - Loader and run repositories surface `AlreadyActiveRun` as a typed error, not a bare `SQLiteConstraintException`.
  - Slice 2 tests must include a concurrent-start race test (two coroutines calling `StartProgramRun` in parallel; exactly one succeeds, the other fails with `AlreadyActiveRun`).
- Related docs/tests/code: `docs/workstreams/android-program-runner.md` (Outputs `Program run entities`), Slice 2 `ProgramRunEntity` definition, Slice 2 `StartProgramRun` / `RepeatProgramRun` / `AbandonProgramRun` use cases.

## 2026-05-17: Domain ID types use Kotlin value classes for the three most-confusable IDs

- Status: accepted
- Context: Slice 0/1/2 surfaced multiple IDs that are all `String` at the wire/Room boundary but represent very different concepts: `programId` (logical program — e.g. "5/3/1 BBB"), `programVersionId` (a specific immutable revision — e.g. "5-3-1-bbb@2026-05-15"), and `programRunId` (a single user's activation). The Phase 4 review pointed out that mistakenly passing `programVersionId` where `programRunId` is expected (or vice versa) would compile, would not be caught by Room schema typing (`TEXT NOT NULL` everywhere), and would only fail when a downstream `SELECT … WHERE programRunId = ?` returned zero rows. Block/week/session/group/item IDs are also `String` but rarely confused with each other in practice because they appear together in tight scopes (a single session-template builder), so the blast radius is smaller.
- Decision: Introduce three Kotlin `@JvmInline value class` wrappers in `:domain` — `ProgramId(value: String)`, `ProgramVersionId(value: String)`, `ProgramRunId(value: String)` — each implementing `EntityId` and with `init { require(value.isNotEmpty()) }`. All `:domain` types (`ProgramRun`, `ScheduleOccurrence`, `ProgramRunReferenceValue`, `StartProgramRunCommand`, `ProgramVersionPrerequisites`, `LoaderResult.{Loaded, Idempotent, ConflictDifferentHash}`, every `Result.Failure` carrying an ID) and the `ProgramRunRepository` interface use the value classes at boundaries. `:data` mappers wrap/unwrap `.value` at the Room edge. The DTO `ProgramResource` (kotlinx.serialization JSON model) keeps `String` to avoid a custom `KSerializer`; conversion happens in the loader when constructing `LoaderResult`. `blockId`/`weekId`/`sessionTemplateId`/`groupId`/`itemId`/`setId` stay `String`.
- Rationale: Value classes give us compile-time confusion-prevention at zero runtime cost (the JVM erases them to the underlying `String`). The `init` guard prevents empty-string IDs from sneaking through. Bounding the rollout to the three IDs that actually appear in cross-layer contracts keeps the blast radius small. Keeping the JSON DTO `String` avoids polymorphic serializer registration and keeps `schema/hash.ts` parity trivial.
- Alternatives considered:
  - Wrap every ID in a value class (rejected: large surface, low payoff for IDs that don't cross layers, and `kotlinx.serialization`'s `@Serializable(with = …)` ceremony multiplies).
  - Use `typealias ProgramRunId = String` (rejected: typealiases provide zero type-checking — `fun start(p: ProgramRunId)` still accepts any `String`).
  - Add Room `TypeConverter`s so entity columns are also typed (rejected: opaque DAO error messages and extra mapping per column; mapper-level conversion is clearer and localized to `RoomProgramRunRepository`).
- Consequences:
  - `:domain` exposes the three value classes as `public` so `:data` and `:app` can construct/unwrap them.
  - `:data` mappers in `RoomProgramRunRepository` and `ProgramResourceLoader` wrap raw `String` rows in `ProgramVersionId(...)` / `ProgramRunId(...)` when crossing into domain types and unwrap via `.value` when crossing into DAO `String` parameters.
  - `:app` UI state types (`ProgramVersionRow`, `ProgramDetailUi`, `TodaySessionUi`) and the `LiftoriumNavHost` sample-state `Map`s key on `ProgramVersionId` directly; Compose `LazyColumn` keys and `testTag`s use `.value` to keep the underlying `String` identity stable.
  - Tests in `:domain`, `:data`, and `:app` wrap literal IDs in the value-class constructors at the call site. Bulk updates use PowerShell `-replace`; the `String` literal at the source is the same hash-stable identity, so Paparazzi/snapshot identity is unchanged.
  - `kotlinx.serialization` continues to round-trip the DTO without custom serializers; the JSON file format is unchanged.
- Related docs/tests/code: `android/domain/src/main/kotlin/dev/liftorium/domain/run/RunIds.kt`, `android/domain/src/main/kotlin/dev/liftorium/domain/run/{ProgramRun,StartProgramRunCommand,ProgramRunRepository,RepeatProgramRun,StartProgramRun,ScheduleOccurrenceSeeding}.kt`, `android/domain/src/main/kotlin/dev/liftorium/domain/resource/LoaderResult.kt`, `android/data/src/main/java/dev/liftorium/data/run/RoomProgramRunRepository.kt`, `android/data/src/main/java/dev/liftorium/data/resource/ProgramResourceLoader.kt`, `android/app/src/main/java/dev/liftorium/app/ui/{UiState,LiftoriumNavHost,ProgramScreens}.kt`.

## 2026-05-17: LiftoriumDatabase v1→v2 audit columns and composite occurrence index

- Status: accepted
- Context: The Phase 4 review (mobile-DB specialist + Skippy passes) called out three issues against the v1 Room schema. (1) Neither `program_run` nor `schedule_occurrence` carried an `updatedAt` timestamp, making "what was the last write?" debuggability impossible and blocking future replication/sync work that needs a row-level vector clock. (2) The v1 `schedule_occurrence` index covered only `programRunId`, but the actual DAO query is `WHERE programRunId = ? ORDER BY plannedEpochDay ASC, sessionIndex ASC` — SQLite was forced to load all matching rows and sort them in memory. (3) The activation contract requires `(programVersionId, contentHash)` to be unique across the loaded-version table, but v1 enforced this only in the repository (`findByContentHash` then `insertOrIgnore`), which is racy under concurrent loads.
- Decision: Ship `LiftoriumDatabase` version 2 with an additive, data-preserving `Migration1To2`:
  - Add `program_run.updatedAtEpochMillis INTEGER NOT NULL DEFAULT 0`. Backfill to `startedAtEpochMillis` for existing rows so they don't appear as never-touched.
  - Add `schedule_occurrence.updatedAtEpochMillis INTEGER NOT NULL DEFAULT 0`. Leave existing rows at `0` (legacy carry-over) and document that workout-logging writes will populate it; runtime never reads `0` as "today".
  - Add `index_program_run_startedAtEpochMillis` for the history list query.
  - Drop the v1 single-column `index_schedule_occurrence_programRunId` and create the composite `index_schedule_occurrence_programRunId_plannedEpochDay_sessionIndex` covering the DAO's read pattern.
  - Add `UNIQUE INDEX index_loaded_program_version_contentHash` to elevate the activation invariant from repository code to a DB constraint.
- Rationale: Each change is additive (ALTER TABLE ADD COLUMN with a default + CREATE INDEX) and therefore data-preserving — the migration test (`MigrationTest.migrate1To2_addsAuditColumnsAndIndexesWithoutDataLoss`) seeds a v1 DB, runs the migration, and asserts every column is present and every row survives. The audit columns are the smallest possible step toward replication-ready persistence: a single `INTEGER NOT NULL` per row, written by the same use cases that already mutate the row, with no behavior change for readers that don't care. The composite index replaces an in-memory sort with an index-ordered scan. The `contentHash` unique index closes a real race that the `ProgramResourceLoader` previously had to defend against in application code.
- Alternatives considered:
  - Skip audit columns until workout-logging arrives (rejected: the migration window is the cheap time to add them; retrofitting later forces a v2→v3 migration for what should have been a single bump).
  - Use SQLite triggers to maintain `updatedAtEpochMillis` (rejected: triggers are hard to test through Room and would silently mask repository bugs that fail to set the column).
  - Keep the v1 single-column index and rely on covering indexes for the sort (rejected: SQLite cannot use a covering index for the `ORDER BY` columns unless they are part of the index; composite is the textbook fix).
  - Enforce `contentHash` uniqueness purely in code (rejected: this is precisely the invariant that "the next dev to add a write path" forgets to check).
- Consequences:
  - `android/data/schemas/dev.liftorium.data.LiftoriumDatabase/2.json` is committed alongside `1.json`.
  - `MigrationTestHelper`-driven `MigrationTest` covers the v1→v2 path and is wired into `:data:testDebugUnitTest`.
  - `verification-loop/SKILL.md` Room-migration row is promoted from `gated` to `passes now`.
  - Future migrations bump to v3 and append additional `Migration2To3` instances to `LIFTORIUM_DATABASE_MIGRATIONS`. No destructive migration shortcuts are permitted.
  - `LoadedProgramVersionDaoTest.versionRow()` and `RoomProgramRunRepositoryTest.loadVersion()` test helpers now derive distinct content-hash strings per fixture row (`id.hashCode().toString().padStart(64, '0').take(64)`) to respect the new unique index.
- Related docs/tests/code: `android/data/src/main/java/dev/liftorium/data/Migrations.kt`, `android/data/src/main/java/dev/liftorium/data/LiftoriumDatabase.kt`, `android/data/src/test/java/dev/liftorium/data/MigrationTest.kt`, `android/data/schemas/dev.liftorium.data.LiftoriumDatabase/{1,2}.json`, `.github/skills/verification-loop/SKILL.md`.

## 2026-05-17: Defer Navigation Compose adoption to the android-ui-polish workstream

- Status: accepted
- Context: Phase 4's Slice 3 needed multi-screen navigation (Library → Detail → Today). The Phase 4 review explicitly asked whether the app should adopt `androidx.navigation:navigation-compose` for the routing layer. The current implementation in `LiftoriumNavHost` uses a small in-memory `sealed interface NavRoute` + `mutableStateOf` + a `BackHandler`, which is sufficient for three screens with no deep-linking, no process-death state restoration concerns beyond what Compose's `rememberSaveable` already gives us, and no inter-graph nesting.
- Decision: Do not adopt Navigation Compose during Phase 4. Track the migration as work item `ui-polish-navigation-compose` inside the `android-ui-polish` workstream (workstream 5). The current ad-hoc nav host stays in place through Phase 4 acceptance and Slice 3 Paparazzi snapshots; it is replaced wholesale when the `android-ui-polish` workstream defines a screen taxonomy, deep-link contract (for shared workout links and import shortcuts), and saved-state strategy.
- Rationale: Navigation Compose pays off when the app has (a) deep-linking, (b) typed args via Kotlin Serialization routes, (c) nested nav graphs, or (d) ViewModel-scoped-to-route lifecycles. None of those exist in Phase 4. Adopting it now would force a route-id scheme, an arg-serialization contract, and a `NavHostController` lifecycle that the workout-logging workstream would almost certainly want to redesign once it knows what cross-screen state it needs to hand off. Deferring keeps Phase 4 acceptance close to the verified runtime evidence (Paparazzi PNGs) without locking the future workstream into a navigation contract chosen before its requirements were known.
- Alternatives considered:
  - Adopt Navigation Compose now (rejected: premature; would invent routes/args without product justification).
  - Adopt Voyager or Compose Destinations (rejected: third-party deps; Navigation Compose is the Google-official baseline and any future migration would target that).
  - Keep the ad-hoc nav host indefinitely (rejected: it will not scale past ~5 screens or any deep-linking requirement; deferring is not abandoning).
- Consequences:
  - `LiftoriumNavHost` stays as the routing layer through Phase 4 acceptance. The `Map<ProgramVersionId, ProgramDetailUi>` / `Map<ProgramVersionId, TodaySessionUi>` sample-state shape is part of this contract.
  - `android-ui-polish` workstream owns: (a) introduce `navigation-compose`, (b) define a route taxonomy + arg-type contract, (c) migrate the three Phase 4 screens, (d) re-record Paparazzi snapshots if the route container changes the layout.
  - No new dependency is added to `libs.versions.toml` in Phase 4.
- Related docs/tests/code: `android/app/src/main/java/dev/liftorium/app/ui/LiftoriumNavHost.kt`, `docs/workstreams/android-ui-polish.md` (workstream 5), `docs/mvp-roadmap.md`.
## 2026-05-24: Architecture fitness functions (ArchUnit + Gradle source scans)

- Status: accepted
- Context: Phase 4 ended with a single Class.forName tripwire ('DomainArchitectureGuardTest') as the only enforcement of module-boundary architecture rules. The Phase 4 review surfaced (and a same-week cycle-detection refactor confirmed) that this was insufficient: a real cycle existed between 'domain.resource' and 'domain.run' via 'WeightUnit' and 'ProgramVersionId'. Reviewer discipline plus a sentinel-class smoke test cannot replace runtime-enforced architecture invariants on a multi-module codebase that is about to grow.
- Decision: Adopt a three-layer architecture fitness function strategy:
  - Layer 1 — Gradle module-graph audit (`verifyModuleGraph`): asserts only ADR-approved inter-module ProjectDependency edges exist. Classifies configurations by suffix (Api / Implementation / CompileOnly / RuntimeOnly / Kapt / Ksp), so Android variant configurations (releaseImplementation, debugApi, flavorDebug*) are caught alongside the base configs. Excludes test, androidTest, kover, lint, annotationProcessor.
  - Layer 2 — ArchUnit bytecode rules on :domain (DomainArchUnitTest) and :data (DataArchitectureTest). Domain allowlist (Kotlin/JDK/AndroidX-annotation/kotlinx.serialization/kotlinx.coroutines-core only). Domain bans 'kotlinx.coroutines.android..'. Domain slice-cycle freedom. Repository types must be interfaces. `domain.common` must not depend on other domain subpackages. :data bans UI frameworks ('androidx.compose..', 'androidx.lifecycle..', etc.), bans :app dependency, asserts @Entity and @Dao locality, asserts data slice-cycle freedom.
  - Layer 3 — Source-level Gradle guards for cases ArchUnit cannot enforce inside :app (AGP's ASM transform crashes on `archunit-junit4` due to its shaded thirdparty layout — confirmed as a known unresolved AGP-ArchUnit incompatibility): `verifyAppUiBoundary` scans every production source set under `app/src/<sourceSet>/{java,kotlin}/dev/liftorium/app/ui` and fails on any reference to `dev.liftorium.data..`. `verifyRoomLocality` scans every production source file in :app, :domain, :core for `androidx.room.Entity`/`Dao`/`Database`/`TypeConverter*`/`RoomDatabase` references and fails outside :data. All three fitness tasks are wired into every module's `check` lifecycle.
  - Created `dev.liftorium.domain.common` package as the canonical home for cross-feature value classes ('WeightUnit', 'ProgramId', 'ProgramVersionId') to break the resource ↔ run cycle. `domain.common` is enforced as a dependency leaf by ArchUnit.
- Rationale: ArchUnit catches bytecode-level violations that source scans miss (e.g. transitive references through generics). Gradle module-graph audit catches violations ArchUnit cannot see (edges that would compile but cross approved boundaries). Source-level guards plug the AGP-imposed gap on :app and the cross-module Room-locality gap that no single ArchUnit test classpath can see. Together the three layers cover the practical surface; each fails the build in CI and provides a citation pointing back to this ADR.
- Alternatives considered:
  - Add archunit-junit4 to :app as well (rejected: AGP ASM transform crash, web-confirmed unresolved incompatibility).
  - Stand up a JVM-only `:archtests` module (deferred: the source-scan guards already cover the failure modes ArchUnit on :app would have caught; revisit if future invariants require bytecode-level inspection of :app).
  - Detekt forbidden-import rule (rejected: Detekt not yet wired into the build; would duplicate ArchUnit conventions across two tools).
  - Keep the Class.forName tripwire as the only guard (rejected: did not catch the real domain.resource ↔ domain.run cycle; insufficient for evolvability).
- Consequences:
  - Every PR runs three new fitness tasks via `check`. Cycle freedom, allowlists, locality, and module-graph integrity are CI-enforceable.
  - New cross-feature value types belong in `dev.liftorium.domain.common`. Adding them elsewhere risks reintroducing cycles.
  - Architects expanding the module graph must update `allowedModuleEdges` in `android/build.gradle.kts` and cite an ADR.
  - :app cannot use archunit-junit4 until the AGP-ArchUnit ASM transform issue is resolved upstream; UI/data and Room locality there are enforced by source-scan guards instead.
  - The legacy `DomainArchitectureGuardTest` `Class.forName` tripwire is retained as a classpath-level belt-and-suspenders complement.
- Related docs/tests/code: `android/build.gradle.kts` (verifyModuleGraph, verifyAppUiBoundary, verifyRoomLocality), `android/domain/src/test/kotlin/dev/liftorium/domain/arch/DomainArchUnitTest.kt`, `android/domain/src/test/kotlin/dev/liftorium/domain/arch/ArchitectureRulesRegistry.kt`, `android/data/src/test/java/dev/liftorium/data/arch/DataArchitectureTest.kt`, `android/domain/src/main/kotlin/dev/liftorium/domain/common/`.


## 2026-05-25: Detekt scoped to Compose stability discipline only

- Status: accepted
- Context: `@Immutable` is a runtime promise to the Compose compiler that the compiler cannot verify. If a UI state class is annotated `@Immutable` but carries an unstable type (e.g. `List<T>` — the interface could be backed by a `MutableList` at runtime), the compiler trusts the lie and may skip recomposition when state actually changed, producing stale UI. Phase 4's review surfaced this gap and the user asked for tooling to keep the promise enforceable, alongside the existing Layer-1 construction discipline (`val`, read-only types, `@Immutable` annotations) that `UiState.kt` already follows.
- Decision: Adopt Detekt 1.23.7 with `io.nlopez.compose.rules:detekt:0.4.22` (`mrmans0n/compose-rules`) on `:app` only. The Detekt config (`android/config/detekt/detekt-compose.yml`) disables every default Detekt ruleset (`complexity`, `style`, `naming`, `performance`, etc.) and every compose-rule that is not stability-related. Only four rules are active: `Compose.MutableParams`, `Compose.UnstableCollections`, `Compose.ComposableParamOrder`, `Compose.MutableStateParam`. Wired into `check` via `tasks.named("check") { dependsOn("detekt") }` at the `:app` level. As the corollary fix, `List<T>` was replaced with `kotlinx.collections.immutable.ImmutableList<T>` in every `@Immutable` UI state DTO and every Composable parameter that takes a collection (`UiState.kt`, `LiftoriumNavHost.kt`, `ProgramScreens.kt`); construction sites in `SampleStateFactory` use `persistentListOf(...)`. `kotlinx-collections-immutable:0.3.8` added to the version catalog and to `:app`.
- Rationale: Detekt is the only widely-adopted Kotlin static analyzer that hosts the `compose-rules` plugin. Running it `:app`-only matches the architecture rule that Compose lives only in `:app` (ArchUnit E1) — running it on `:domain`/`:data` would surface zero findings and burn CI time. Narrowing to four rules avoids adopting Detekt as a general linter (we already have Kover, ArchUnit, and Compose compiler warnings for that surface area). `ImmutableList` is the canonical Compose pairing for `@Immutable` data classes; the Compose compiler recognizes it as stable. `persistentListOf` over `.toImmutableList()` because the cost is identical for static sample data and `persistentListOf` makes intent explicit at the construction site.
- Alternatives considered:
  - Compose compiler stability report (`-P plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=...`). Complementary, not redundant; provides a human-readable report at build time but does not fail CI. Could be added later.
  - Custom reflection-based test scanning every `@Immutable` class for unstable field types. Would require maintenance for every new UI DTO; Detekt's compose-rule is already maintained upstream.
  - Adopt Detekt with default rules. Rejected — we'd inherit hundreds of style/complexity findings unrelated to the actual problem, and we have neither the capacity nor the desire to baseline that.
  - Refactor Composables to take parent `@Immutable` DTOs instead of destructured `List` parameters. Larger API surface change for no extra safety; `ImmutableList` parameters express intent more clearly.
- Consequences:
  - `:app:detekt` runs as part of `:app:check`. CI green is gated on it.
  - Future UI state DTOs that store collections must use `ImmutableList<T>` / `ImmutableMap<K,V>`; `List`/`Map` will fail Detekt at the Composable parameter boundary.
  - The narrow scope is documented in `detekt-compose.yml`'s header so future contributors don't add general Detekt rules without an ADR.
  - `verification-loop` skill registers `:app:detekt` as a runtime evidence command.
- Related docs/tests/code: `android/config/detekt/detekt-compose.yml`, `android/gradle/libs.versions.toml` (`detekt`, `detektComposeRules`, `kotlinxCollectionsImmutable`), `android/app/build.gradle.kts` (Detekt block + `detektPlugins` + `kotlinx-collections-immutable` dep), `android/app/src/main/java/dev/liftorium/app/ui/UiState.kt`, `ProgramScreens.kt`, `LiftoriumNavHost.kt`, `android/app/src/debug/java/dev/liftorium/app/ui/SampleStateFactory.kt`, `android/app/src/release/java/dev/liftorium/app/ui/BootstrapState.kt`.


## 2026-05-25: File-organization discipline — soft instruction tier + hard Detekt tier

- Status: accepted
- Context: As `:app` started accumulating UI code, `ProgramScreens.kt` reached 508 lines with five public Composables. The user pushed back on rigid line-based rules (Detekt's stock `LongMethod` / `LargeClass` at 100-150 lines) because they punish cohesive features and reward mechanical splits that fragment a single concern into three files that only make sense read together. The repo is agent-driven; conventions must be both (a) visible to every agent session (no opt-in fetching) and (b) flexible enough that authorial judgment is preserved within a generous envelope.
- Decision: Two-tier file-organization discipline with three artifacts:
  1. **Soft tier** lives in `.github/copilot-instructions.md` under `## Development conventions`. Bullets cover: package-by-feature, one public top-level declaration per file when feasible, no catch-all `Utils.kt`/`Helpers.kt` files, and the soft ceiling itself (~400 lines OR ~3 public top-level declarations triggers a "is this still one cohesive concern?" review prompt). The soft tier is NOT CI-enforced — it shapes habit at write/review time.
  2. **Hard tier** lives in `android/config/detekt/detekt-compose.yml`. Detekt's built-in `complexity.LargeClass` is enabled with `threshold: 800`; `naming.MatchingDeclarationName` is enabled to ensure a single-public-decl file is named for that declaration (forcing multi-decl files to use a generic plural name like `ProgramScreens.kt` which then visibly trips the soft-tier review prompt). Every other rule in `complexity` and `naming` stays off — explicitly NOT enabled: `LongMethod` (Compose builder DSL legitimately runs long), `TooManyFunctions` (private helpers are encouraged), `CyclomaticComplexMethod`, `StringLiteralDuplication`, naming-style rules, etc.
  3. **ADR** (this entry) records the rationale so the thresholds can be revisited as a deliberate decision, not drift.
- Rationale: An agent-driven codebase needs conventions loaded BEFORE code is written, which means the instruction file. But instructions alone drift under fast work — Detekt at the hard ceiling is the backstop that catches runaway files (5,000-line "everything" modules) without fighting normal development. Two tiers separate "shape habit" (soft) from "prevent disaster" (hard). The `MatchingDeclarationName` rule is the most under-appreciated lever: a single-decl file is mechanically forced to name itself for that declaration, so authors can't hide a multi-concern file under an innocuous name. Hard threshold 800 is generous (3-4× a typical screen file) so it never fires on legitimately cohesive work; it only catches drift.
- Alternatives considered:
  - Stock Detekt thresholds (`LongMethod`: 60, `LargeClass`: 150, `TooManyFunctions`: 11). Rejected — they punish Compose builder DSLs and feature-cohesive files with private helpers; would force fragmentation contrary to the intent.
  - Single tier (hard only). Rejected — either too low (fights normal work) or too high (catches nothing); no shaping force on day-to-day work.
  - Single tier (soft only, no CI). Rejected — drifts. Nothing prevents a future agent from shipping a 3,000-line file.
  - Skill (`.github/skills/file-organization/SKILL.md`). Rejected — skills route by task description; "deciding how to organize a file" is incidental to almost every implementation task, so the skill would never fire reliably. Conventions are background discipline, not workflows.
  - New `docs/conventions.md` doc. Rejected — duplicates the natural home in `copilot-instructions.md` (which already hosts "Development conventions" bullets) and adds a hop the agent has to know about. `docs/` is for architecture and decisions, not behavioral rules.
- Consequences:
  - Every agent session sees the four new bullets in `copilot-instructions.md` immediately.
  - `:app:detekt` now also gates against a hard 800-line ceiling and the matching-declaration-name rule. Currently 0 code smells.
  - `ProgramScreens.kt` (508 lines, 5 public Composables) trips the soft tier; the split is deferred to `android-ui-polish` (Phase 5) where the per-screen reorganization happens alongside theme/component extraction. Recorded as a workstream Output.
  - Future Detekt-rule additions in `complexity` or `naming` rulesets are blocked by ADR — adding any other rule requires a follow-up decision.
  - Soft-tier thresholds (~400 / 3) can be re-tuned in `copilot-instructions.md` without an ADR. Hard-tier threshold (800) changes require an ADR amendment.
- Related docs/tests/code: `.github/copilot-instructions.md` (`## Development conventions`), `android/config/detekt/detekt-compose.yml` (`complexity.LargeClass`, `naming.MatchingDeclarationName`), `docs/workstreams/android-ui-polish.md` (Outputs: split `ProgramScreens.kt`).


## 2026-05-25: Liftorium design tokens and LiftoriumTheme as canonical wrapper

- Status: accepted
- Context: `android-program-runner` shipped with raw `MaterialTheme {}` wrappers, hard-coded `.dp` paddings, ad-hoc `SuggestionChip(onClick={}, enabled=false)` status pills, and inline empty-state `Column` blocks. Without a canonical token surface, every subsequent Android workstream would re-derive its own spacing, typography, and color decisions.
- Decision: Introduce `dev.liftorium.app.ui.theme.LiftoriumTheme` as the canonical theme wrapper for every Liftorium Compose surface, backed by `LiftoriumColorScheme` (hand-picked steel/iron palette, light + dark, no dynamic color), `LiftoriumMaterialTypography` (M3 baseline), an extra `LiftoriumTypography.numeric` slot enabling `tnum` for column-aligned weights/reps, `LiftoriumSpacing` (xs/sm/md/lg/xl/xxl = 4/8/12/16/24/32 dp), `LiftoriumDimens`, and `LiftoriumShapes`. Non-Material tokens are exposed via `LiftoriumTokens` (CompositionLocal-backed, `@ReadOnlyComposable` getters). Shared components `StatusBadge` + `BadgeTone` and `EmptyState` replace the previous ad-hoc patterns at every existing call site. `LiftoriumNavHost` no longer self-wraps in `MaterialTheme {}` — theming is the caller's responsibility.
- Rationale: A single token surface keeps brand consistency, makes dark-mode parity a check-in concern instead of a per-screen audit, and unblocks `android-workout-logging` and later workstreams from re-deriving design decisions. Dynamic color is rejected because it would defeat brand consistency and make Paparazzi goldens non-deterministic across wallpapers. Removing `LiftoriumNavHost`'s inner `MaterialTheme` is required so an outer `LiftoriumTheme` actually takes effect; otherwise the M3 default theme would silently shadow brand tokens.
- Alternatives considered: keep raw `MaterialTheme` and a thin extension function (rejected — no place to attach spacing/numeric tokens); dynamic color (rejected — brand drift + non-deterministic snapshots); ship Navigation Compose and the brand icon in the same change (rejected — scoped out to keep the surface area review-friendly and reduce the chance of a botched migration of either).
- Consequences:
  - Every subsequent Android workstream MUST wrap its surface in `LiftoriumTheme {}` and reach for `LiftoriumTokens.spacing.*` / `LiftoriumTokens.dimens.*` / `LiftoriumTokens.lTypography.numeric` instead of raw `.dp` / `.sp` values.
  - New status pills MUST use `StatusBadge(text, tone)` and (for validation statuses) `ValidationStatusBadge.of(status)`. Reintroducing `SuggestionChip(onClick={}, enabled=false)` for a non-tappable pill is a coverage-review finding.
  - Empty list/library surfaces MUST use the shared `EmptyState` instead of an inline `Column { Text; Spacer; Text }`.
  - Callers of `LiftoriumNavHost` MUST wrap it in a Material / Liftorium theme themselves. `ActivateFlowSemanticsTest` and both Paparazzi test classes were updated accordingly.
  - Paparazzi snapshots gain a parallel dark-theme class (`ProgramRunnerPaparazziDarkTest`) so dark-mode parity is part of every UI change's evidence.
  - Navigation Compose adoption and the brand adaptive icon remain deferred under the `android-ui-polish` workstream and have not regressed.
- Related docs/tests/code: `docs/design-system.md`, `docs/workstreams/android-ui-polish.md`, `android/app/src/main/java/dev/liftorium/app/ui/theme/` (LiftoriumTheme, LiftoriumColorScheme, LiftoriumTypography, LiftoriumSpacing, LiftoriumDimens, LiftoriumShapes, LiftoriumTokens), `android/app/src/main/java/dev/liftorium/app/ui/components/` (StatusBadge, BadgeTone, EmptyState), `android/app/src/main/java/dev/liftorium/app/ui/program/` (one public Composable per file), `android/app/src/testDebug/java/dev/liftorium/app/ProgramRunnerPaparazziTest.kt`, `android/app/src/testDebug/java/dev/liftorium/app/ProgramRunnerPaparazziDarkTest.kt`, `android/app/src/testDebug/java/dev/liftorium/app/ActivateFlowSemanticsTest.kt`.
