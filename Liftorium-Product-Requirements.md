# Liftorium Product Requirements Document

## Document purpose

This document captures the product requirements for Liftorium from a product-management perspective. It is intentionally not an implementation plan. It defines the user problem, target users, product promise, user flows, prioritized scope, quality bar, risks, success metrics, and open decisions that should guide later design and engineering planning.

This document reflects product decisions made during planning: the app experience is user-facing, while program import is a developer-time Copilot skill that produces finalized app-ready program resources.

## Product concept

Liftorium is a mobile-first workout tracking app for people who follow structured workout programs. The app helps users choose a program, run workouts day by day, log performance quickly in the gym, manage rest periods, substitute exercises when needed, track progress over time, and complete or repeat a program over its intended duration.

The product must be fast, reliable, and usable in real gym conditions: poor connectivity, busy equipment, sweaty hands, interrupted sessions, phone lock/backgrounding, missed workouts, and program materials that come from messy spreadsheets or PDFs.

## Product promise

Follow structured workout programs on mobile, even offline, without losing the program's intent or your training history.

## Current state

This is a greenfield product. Any existing proof-of-concept app should be ignored.

Sample program materials kept locally by the operator are useful only as examples of real-world program shape. They include PDFs/manuals and spreadsheet trackers with week/day structures, rest days, exercise notes, rep ranges, RPE targets, percent-of-1RM targets, top sets, back-off sets, optional work, alternatives, formulas, and tracking-oriented layouts. Source materials are never committed to the repository.

The product should therefore assume workout programs are inconsistent across sources. The app should preserve original program intent when structure is uncertain, rather than guessing silently or flattening rich instructions into simple sets and reps.

## Working name

Selected working name: Liftorium. The name is not final until trademark, domain, app-store, and social-handle checks are complete.

Other name candidates retained for backup:

1. Myoloom
2. Setra
3. Volumora
4. RepRift
5. Liftlyric
6. Kinestack
7. Strenova
8. Hypertrovia
9. Primalog
10. SetraForge

Final naming still requires trademark, domain, app-store, and social-handle checks.

## Launch target user

Primary launch audience: users who follow structured workout programs and currently rely on spreadsheets, PDFs, notes, or generic trackers. The first product emphasis is strength and hypertrophy-style programs because those are represented by the initial samples, but the app-supported program format should be flexible enough to represent any structured plan that the developer-time import process converts.

The first version should optimize for this user before trying to serve every fitness category.

## Secondary users

### Adaptive gym user

A user who follows a plan but often needs to adapt because equipment is unavailable, the gym is crowded, a movement causes discomfort, or a specific exercise variant is not practical that day.

### Program importer / curator

A developer/operator who has program materials and uses a local Copilot skill to convert those materials into an app-ready program resource with a summary/report and review before release into the app.

### Future coach / program creator

A coach or creator who wants their program represented faithfully, including notes, cues, progression rules, variants, phases, substitutions, and optional work. This is important for future roadmap, but not the first launch audience.

## Core user problems

- Spreadsheets and PDFs are awkward during workouts.
- Generic trackers lose program context, notes, RPEs, percentages, substitutions, and week-by-week intent.
- Users need to log sets quickly without fighting the app.
- Users train in places where network connectivity is unreliable.
- Users need to recover gracefully after closing the app, losing signal, or getting interrupted mid-session.
- Users need to understand progress through history, PRs, estimated 1RM trends, substitutions, and timer behavior.
- Users need to adapt exercises without erasing what the program originally prescribed.
- Program materials vary widely, making manual entry slow and error-prone. Liftorium should move this complexity into a developer-time import workflow so the user gets a clean app experience.

## Goals

- Help users confidently follow structured workout programs on mobile.
- Make routine workout logging fast enough for real gym use.
- Work reliably offline during active training.
- Preserve the intent of complex workout prescriptions.
- Support practical exercise substitutions while retaining prescribed-versus-performed history.
- Support rest timing without forcing users to use timers.
- Give users clear progress history and stats.
- Enable developer-time Copilot-assisted conversion of program materials into reviewed app-ready resources.
- Support Azure-backed account and sync capabilities later without making network access required for workouts.

## Non-goals for the first product version

- Do not build a social network.
- Do not build a public workout-program marketplace.
- Do not provide medical, injury-rehabilitation, or clinical advice.
- Do not generate unreviewed training plans automatically.
- Do not require internet access to complete a workout.
- Do not redistribute copyrighted or paid program source materials.
- Do not expose program import complexity to end users at launch.
- Do not make in-app AI coaching a first-version promise.

## Core product principles

- Mobile first: one-handed use, large touch targets, low typing, readable in the gym.
- Offline first: active workout flows must not depend on network connectivity.
- Data trust: workout history must not be lost because of network, sync, app restart, or account-state changes.
- Fast logging: routine set logging should be possible in one or two taps when using prescribed or previous values.
- Program fidelity: the app must faithfully run the finalized app-ready program resources produced by the developer-time import workflow.
- User control: users choose programs, substitutions, rest timer behavior, and whether to repeat or change programs.
- Transparent import: Copilot conversion must expose summary/report information, warnings, and unresolved ambiguity to the developer/operator before a program is released into the app.
- Privacy by default: workout history, converted programs, and source materials are private. Converted paid programs are private-only with no sharing in v1.

## Product scope priorities

These are product priority tiers, not an engineering implementation plan.

### P0: First credible product experience

P0 is the minimum complete product promise for early users:

- Today / Active Workout as the primary home experience.
- Mobile-first program library.
- One active program run at a time.
- Ability to start, run, complete, repeat, abandon, or restart a structured program.
- Offline workout logging for the active program.
- In-progress workout recovery after app close or interruption.
- Fast set logging with edits, skips, extra sets, and notes.
- Rest timer controls with global and per-workout disable.
- Optional account placeholder only; no account is required for first beta use.
- Program-provided substitutions and recent/favorite substitutions.
- Flexible prescription display for finalized app-ready programs.
- 1RM inputs where required by a program.
- Exercise history.
- Personal records.
- Estimated 1RM trend for supported strength lifts.
- Substitution tracking.
- Exportable workout history.
- Private static program resources produced by the developer-time import workflow.
- Local Copilot-assisted spreadsheet-first program conversion skill that produces finalized app-ready program resources plus summary/report after developer review.
- Local-first app behavior; Azure sync is not required for the first beta.

### P1: Product expansion

- Full Azure-backed user account and multi-device sync.
- Restore across devices.
- Broader substitution catalog.
- Custom exercises.
- Custom substitutions.
- Program adherence dashboard.
- Volume trends.
- Program comparison across repeated runs.
- PDF import coverage or PDF-assisted import.
- User-facing import or user-editable imported programs, if the product later exposes import outside developer workflows.
- Rest timer usage stats, if later validated as useful to users.
- Expanded accessibility and personalization settings.

### P2: Later opportunities

- Public program marketplace.
- Coach or trainer portal.
- In-app program builder.
- Wearable integration.
- Health platform integrations.
- Plate calculator.
- Readiness, fatigue, or recovery tracking.
- Exercise demo videos.
- In-app AI coaching chat.
- Social sharing.

## Release assumptions

First release assumptions:

- Release type: personal/internal use.
- Launch platforms: Android and Web.
- Account model: optional account placeholder only; no required sign-in and no required cloud sync.
- Program content: only developer-imported programs; no bundled synthetic sample program for the first beta.
- Business model: free personal app first.
- Region and language: US/English first.

## First-time experience

The user should understand Liftorium's value within seconds: structured programs, fast logging, offline reliability, and progress tracking.

Requirements:

- Ask only for essential setup choices at first launch: units, rest timer preference, and optional account placeholder if present.
- Let users reach a useful state quickly.
- Let users start from developer-imported programs or an empty/private library.
- Explain that active workouts work offline.
- Avoid making account creation a blocker unless a later business decision requires it.
- Default the main app experience toward Today / Active Workout when a program is active, with program library and stats as secondary destinations.

## Program library

The program library is where users browse available programs, inspect details, and choose what to run.

Program details should include:

- Program name.
- Duration.
- Weekly frequency.
- Training goal.
- Difficulty or intended audience.
- Equipment needs.
- Unit assumptions.
- Program source type: bundled, imported, private, synced, or external.
- Program status: not started, active, completed, archived, or abandoned.
- Version or revision label when relevant.

Requirements:

- User can browse available programs.
- User can inspect a program before starting it.
- User can start one program as the active program.
- User can see completed and abandoned program runs separately.
- User can repeat a completed program as a new run.
- Program materials should be runnable without requiring the original spreadsheet or PDF during workouts.

## Active program and scheduling

An active program represents the user's current run through a specific program version.

Requirements:

- User can choose a program start date.
- App maps program week/day/session to calendar dates when schedule information is available.
- User can start any scheduled or unscheduled workout from the active program.
- User can reschedule missed sessions.
- User can repeat a workout or week without corrupting history.
- User can skip ahead or go back while preserving actual completion dates.
- User can pause, abandon, or complete a program run.
- User can start a new program only after confirming what happens to the current active run.
- Planned program order and actual completion history should be stored separately.
- Completed history remains tied to the exact program version used.

## Workout session

The workout session is the core product surface. It should prioritize speed, clarity, and confidence over analytics-heavy dashboards.

Requirements:

- User can open the active workout quickly.
- User can see day title, program week/day, prescribed exercises, notes, cues, rest recommendations, and optional choices.
- User can log sets, reps, load, RPE/RIR, notes, and completion state.
- User can edit logged sets.
- User can add extra sets.
- User can skip a set or exercise.
- User can undo recent logging actions.
- User can mark prescribed values complete quickly when no edits are needed.
- User can copy previous set values.
- User can see relevant prior performance for the same or related exercise.
- User can close the app mid-workout and recover progress later.
- User can complete the workout and see a summary.
- Notes and RPE/RIR should not be mandatory unless the program requires them.

Gym-session usability requirements:

- Routine set logging should be possible in one or two taps when using prescribed or previous values.
- Logging should be usable one-handed.
- Important controls should be large enough for fatigued or sweaty use.
- The app should avoid unnecessary navigation during set logging.
- The user should never need network access to continue an active workout.
- The workout screen should show prescribed, previous, and actual values clearly when available.

## Rest timer

Rest timing should help users who want it without annoying users who do not.

Requirements:

- User can enable or disable rest timers globally.
- User can disable rest timers for a specific workout.
- User can skip, pause, or adjust a timer during a workout.
- Program-prescribed rest periods should be visible.
- If timers are disabled globally, the app should not auto-start timers.
- Timer alerts should be clear but not disruptive.
- Foreground timers should remain accurate.
- Background or lock-screen timer behavior should clearly communicate platform limitations where they exist.
- Rest timer usage analytics are not a launch requirement; timer behavior may be logged internally only if needed for debugging or future product analysis.

## Exercise substitutions

Substitutions are a first-class requirement because real workouts often need adaptation.

Requirements:

- User can substitute an exercise from available alternatives.
- Substitution flow should prioritize program-provided alternatives, recent substitutions, favorite substitutions, and equipment-compatible alternatives.
- User can choose a quick reason such as equipment unavailable, discomfort, preference, no time, or program alternative.
- User can apply a substitution once, for the rest of the program run, or according to future product rules.
- App records both the original prescribed exercise and the performed substitute.
- Original exercise remains visible after substitution.
- User can undo a substitution.
- Users cannot create custom exercises in P0. Unknown or missing exercises should be resolved during the developer-time import/catalog mapping process before a program is released into the app.
- Substitutions should preserve program intent where possible.

Substitution recommendations should consider:

- Movement pattern.
- Primary muscle groups.
- Equipment.
- Difficulty.
- Unilateral versus bilateral movement.
- Loading profile.
- Range of motion.
- Fatigue cost.
- Program role: strength-skill practice, hypertrophy stimulus, accessory work, warm-up/prehab, or optional pump work.

Stats and history must distinguish prescribed exercise, performed exercise, and whether the substitute should count toward original movement history, substitute exercise history, or both.

## Flexible workout prescriptions

Workout programs can include many prescription styles. The app must support the finalized program format produced by the developer-time import workflow and correctly utilize all imported workout data, including exercises, set/rep prescriptions, percent-of-1RM work, RPE/RIR work, rest intervals, alternatives, and notes.

Required prescription concepts:

- Program, block/phase, week, day/session, exercise group, exercise, and prescription item.
- Blocks or phases such as accumulation, intensification, peaking, deload, testing, intro, or coach-defined phases.
- Exact reps.
- Rep ranges.
- Load targets.
- Percent-of-1RM targets.
- RPE or RIR targets.
- Prescribed warm-up sets.
- User-added warm-up sets.
- Ramping sets.
- Working sets.
- Top sets.
- Back-off sets.
- AMRAP sets.
- Optional work.
- Supersets, paired work, circuits, or alternating sets.
- Rest days.
- Time-based work.
- Per-side or per-limb work.
- Notes and coaching cues.
- Mutually exclusive options where the user performs one option but not another.

The app must preserve original prescription text when useful for user understanding.

The developer-time import workflow should resolve unsupported or ambiguous structures before release, either by converting them into supported app data or preserving them as reviewable program notes.

## 1RM and measurement options

Many structured programs use true 1RM, estimated 1RM, recent best, body weight, or a program-specific test value. Liftorium stores user-supplied values as 1RM; programs that traditionally used a separate "training max" must encode that adjustment into their target percentages.

Requirements:

- User can enter relevant 1RM values when required by a program.
- Percentage-based prescriptions should identify what reference value they use.
- User can update maxes over time.
- Updating a 1RM should affect future prescriptions but should not rewrite historical completed workouts.
- Program-run-specific 1RM overrides can differ from all-time personal records.
- User can follow either/or measurement instructions when a program offers alternatives.
- App does not force the user to complete both sides of a mutually exclusive prescription.
- Workout history records the selected option and the reference value used for calculated targets.
- Estimated 1RM trends should compare multiple formulas rather than relying on a single formula, and should include clear caveats when based on high-rep, high-fatigue, RPE-based, or substituted work.

## Progression and autoregulation

Structured programs often change future work based on prior performance. Liftorium should help users follow these rules without pretending every program is fully automated on day one.

Requirements:

- Workout screen should show relevant prior performance and calculated targets where available.
- User can override calculated or suggested targets.
- App can record whether the user followed, exceeded, or missed a prescription.
- Programs may include simple progression rules such as add weight after successful completion, repeat after missed target, reduce load after failure, or adjust from AMRAP/RPE outcome.
- Unsupported progression instructions should be preserved as visible coach notes.
- For RPE/RIR-based work, user can log actual RPE/RIR and whether the effort target was met.
- The app should distinguish fixed-load prescriptions from effort-target prescriptions.

## History, stats, and progress insights

Historical data and progress stats are core product experiences. Users should understand whether they are getting stronger, training consistently, adapting workouts, and improving key lifts over time.

Stats experience:

- Simple summary for quick understanding.
- Detailed drill-downs for users who want deeper analysis.
- Every stat should let users inspect the underlying workouts or sets that produced it.
- Stats should be useful for training decisions, not just vanity dashboards.

Must-have launch stats:

- Exercise history.
- Personal records.
- Estimated 1RM trend for supported strength lifts.
- Substitution tracking.

Stats definitions:

- Exercise history includes completed sets, reps, load, RPE/RIR, notes, substitutions, and dates.
- Personal records should identify record type, such as heaviest load, best estimated 1RM, best reps at a load, or other meaningful achievement.
- Estimated 1RM trends should compare multiple formulas, disclose limitations, and show which formula produced each estimate.
- Substitution tracking should show original prescribed exercise, performed substitute, reason, frequency, and whether it was one-time or repeated.
- Edited or deleted historical workouts should update related stats.
- Warm-ups, optional work, skipped work, and substituted work should have clear inclusion rules for stats.
- Launch e1RM trends should support squat, bench press, deadlift, and overhead press.
- Launch personal record types are heaviest load, best estimated 1RM, and best reps at a given load.

Variant and grouping requirements:

- Stats should support exact exercise variant history.
- Stats should also support grouped exercise-family history where useful.
- The app must make clear when records or trends combine variants.
- PRs across incompatible variants should not be merged silently.

Later stats candidates:

- Workout count by week and month.
- Program adherence percentage.
- Total volume by exercise, muscle group, workout, week, and program.
- Average RPE by exercise or workout.
- Skipped exercise tracking.
- Program run comparison.
- Rest timer usage, if later validated as useful to users.

## Program adherence

Adherence should explain what actually happened, not just whether a workout was marked complete.

Adherence states may include:

- Completed as prescribed.
- Completed with substitutions.
- Completed with skipped work.
- Partially completed.
- Missed.
- Rescheduled.
- Repeated.
- Extra work added.

Adherence should eventually account for prescription fidelity:

- Prescribed sets completed.
- Rep targets hit or missed.
- Load targets hit or missed.
- RPE/RIR targets hit or missed.
- Optional work completed or skipped.
- Deload/test week completed.
- Substitutions and load deviations.

## Program completion

At the end of a program, the product should help the user decide what comes next.

Requirements:

- User sees that the program is complete.
- User sees a completion summary.
- User can repeat the same program.
- User can start a new program.
- User can remain without an active program.
- User's completed run remains in history.
- Repeating a program creates a new run rather than overwriting the old one.
- If a newer program version exists, user should understand whether they are repeating the same version or starting a different version.

## Program versioning

Program versioning is required to preserve history and stats correctness.

Requirements:

- Starting a program run pins that run to a specific program version.
- Completed history remains tied to the program version used at the time.
- Imported program edits should create a new version rather than mutating completed history.
- Bundled program updates should not silently change active or completed runs.
- Repeated program runs should be distinguishable.
- Comparisons across program runs should disclose version differences.

## Copilot-assisted program import

The product should include a dedicated local Copilot-assisted program-conversion skill for transforming program materials into finalized app-ready program resources with review and validation. This is a developer-time workflow, not an end-user in-app import experience for P0.

Product boundary:

- The skill is developer/operator-facing for P0.
- The first supported import target is spreadsheet-first.
- PDF-only import and PDF-assisted import are later scope unless manually handled during developer review.
- Converted programs should become private/static app resources by default.
- Original source files should not be bundled, redistributed, or committed by default.
- The app should support any structured program that the developer-time import process converts into the supported app program format.

User requirements:

- Developer/operator can provide structured program materials, with spreadsheets as the primary P0 source.
- Copilot analyzes the materials and creates a structured program resource.
- Copilot flags ambiguous sections instead of guessing silently.
- Developer/operator reviews the converted program and can chat with AI to correct issues before release.
- The skill produces a finalized app-ready program resource plus a summary/report.
- Import review should answer: "Can I safely run this program in the gym without opening the original spreadsheet or PDF?"

Import quality requirements:

- Detect program title, duration, weeks, days, rest days, and exercises.
- Detect set, rep, load, RPE/RIR, percent, rest, and note fields when present.
- Detect blocks/phases, deloads, test weeks, optional work, and alternatives when present.
- Preserve source references for review where legally and practically appropriate.
- Identify unknown exercises.
- Identify alternative exercises and substitutions.
- Identify conditional or mutually exclusive work.
- Identify missing maxes needed to calculate loads.
- Identify ambiguous week/day ordering.
- Produce a validation report understandable by a non-engineer.
- Critical validation errors block program activation until resolved.

Initial import test families:

- Powerbuilding.
- Powerlifting/peaking.
- Hypertrophy/bodybuilding.
- Beginner linear progression.

Import audit requirements:

- Preserve import date.
- Preserve source filename metadata.
- Preserve validation status.
- Preserve unresolved warnings.
- Preserve user/operator approval status.
- Minimize source excerpts and avoid redistributing proprietary content.

## Account, Azure backend, and cloud sync

Azure-backed cloud functionality is required for the product direction, but workouts must remain usable offline. The first beta is local-first; Azure sync is later scope.

User requirements:

- User can continue using the app when offline.
- When sync is introduced, user can sync workout history and preferences when signed in.
- When sync is introduced, user can access data across devices after sync.
- When sync is introduced, user can see sync status in plain language.
- When sync is introduced, user can recover from sync failures without losing local workout data.

Offline-first contract:

- Once a program is started, all data needed to complete upcoming active-program workouts should be available offline.
- Active workout logging must not be blocked by sync.
- User-entered workout history must not be lost because of network failure, sync failure, app restart, or account-state changes.

Future sync contract:

- The app should distinguish saved locally, syncing, synced, and sync failed states.
- Sync must not silently overwrite newer local workout history.
- Unsafe conflicts should preserve both versions or clearly ask the user which version to keep.
- Duplicate workouts or sets should not appear after reconnecting.
- If a workout is edited on multiple devices, the user-facing conflict behavior must be explicit.

Data recovery expectations:

- User can start, continue, edit, and complete a workout entirely offline.
- Logged sets should be recoverable after app termination or device restart.
- Recovery should match the last user-visible saved state.
- Failed export or restore should not corrupt local history.
- When accounts and sync are introduced, failed sign-in, sign-out, or sync should not corrupt local history.

## Privacy, ownership, and legal requirements

Requirements:

- Workout history, converted program resources, source materials, notes, and stats are private by default.
- User must own or have rights to imported program materials.
- Converted paid programs should not be redistributed without permission.
- Source files must never be uploaded for AI processing through the user-facing app. Developer-time spreadsheet conversion in a Copilot session is the operator's explicit action and does not require an additional in-app prompt.
- If source files leave the device, the app should disclose that clearly.
- User can export their data.
- User can delete account data where accounts are supported.
- User can delete local data and converted program resources.
- Telemetry should avoid capturing proprietary program text, personal notes, or sensitive training details unless explicitly anonymized or consented.

## Data export, restore, and portability

Requirements:

- User can export complete portable workout history.
- Export should include enough raw data to reconstruct stats.
- Export should include program versions used, substitutions, notes, and max values needed to understand historical workouts.
- User should eventually be able to restore/import their own exported history.
- Failed export or restore should not corrupt existing data.

## Accessibility requirements

Core workout logging, timers, substitutions, and history must be usable by a broad range of users.

Requirements:

- Large touch targets.
- VoiceOver/TalkBack support.
- Dynamic text size support.
- High-contrast friendly UI.
- Color-blind-safe charts and status indicators.
- Reduced motion support.
- Haptic/audio/visual timer alternatives where appropriate.
- No critical state communicated by color only.
- Stat drill-downs and tables should remain readable on small screens.

## Error and edge-case states

The product should define clear user-facing behavior for:

- Offline unavailable cloud features.
- Failed sync.
- Sync conflict.
- Import failure.
- Unsupported program construct.
- Unknown exercise.
- Missing 1RM.
- Ambiguous prescription.
- Timer background limitations.
- Corrupted or incomplete imported program.
- Account deletion in progress.
- Data export in progress.
- Low storage or save failure.
- Abandoned active program.
- Starting a new program while another is active.

## Launch acceptance scenarios

These are product-level acceptance scenarios for later design and engineering planning.

- User starts a program, logs a workout offline, closes the app, reopens it, and sees all logged work recovered.
- User disables rest timers globally and no timer auto-starts during workouts.
- User substitutes an exercise once and history shows both prescribed and performed exercise.
- User repeats a completed program and sees separate run histories.
- User views exercise history, personal records, estimated 1RM trend, and substitution history.
- Developer/operator runs the spreadsheet-first Copilot skill and receives a finalized app-ready program resource plus summary/report.
- Developer/operator can review the imported program summary, chat with AI to correct issues, and rerun or finalize the resource before it is released into the app.
- User updates a 1RM and future calculated prescriptions change while historical workouts remain unchanged.
- User reschedules a missed workout and actual completion date remains distinct from planned program day.
- When sync is introduced later, user completes a workout offline, reconnects, and history syncs without duplicates.

## Key success metrics

### Activation

- Percentage of new users who start a program.
- Percentage of new users who complete first workout.
- Time from first launch to first logged set.
- Time from app open to active workout screen.

### Engagement

- Weekly active users.
- Workouts completed per active user.
- Program continuation rate after first week.
- Percentage of users who complete an entire program.
- Percentage of active users who view progress stats or history.
- Repeat usage of stats views after multiple completed workouts.

### Workout usability

- Routine set logging interaction count.
- Workout abandonment during logging.
- Frequency of edited, skipped, and extra sets.
- Frequency of substitutions.
- User-reported ease of logging.

### Reliability

- Workout recovery success after app close.
- Offline workout completion rate.
- Data-loss incidents.
- Sync failure rate, when sync is introduced.
- Duplicate workout or duplicate set incidents after sync, when sync is introduced.

### Import quality

- Percentage of imported programs that validate without critical errors.
- Number of unresolved ambiguities per imported program.
- Developer/operator approval rate after import.
- Time required to convert a program into usable form.
- Percentage of imports rejected before activation.

### User satisfaction

- User rating after completing several workouts.
- Reported confidence in workout data safety.
- Reported usefulness of progress stats.
- Reported confidence in imported program accuracy.

## Product risks

- The MVP can become too large because offline logging, flexible prescriptions, substitutions, stats, and developer-time Copilot import are each substantial product areas.
- Program schema complexity may grow quickly because real workout programs vary widely.
- Copilot import may produce inaccurate results if source materials are messy or ambiguous.
- Offline-first behavior can become difficult to explain when sync is introduced later.
- Users may expect copyrighted or purchased program materials to be shareable, creating legal and policy risk.
- Rest timers can become annoying if they interrupt the workout flow.
- Exercise substitution quality depends on a strong exercise catalog and classification system.
- Stats correctness can be hard across substitutions, variants, program versions, and edited history.
- Azure sync expectations may increase support burden later if users rely on multi-device continuity.

## Product assumptions

- Users are willing to select developer-imported structured programs rather than manually build everything from scratch.
- Users value offline reliability more than social features in the first version.
- A developer/operator review step is acceptable for Copilot-imported programs.
- A flexible prescription display is more valuable than forcing perfect structured interpretation on day one.
- The app-supported program format can represent any structured plan that the developer-time import process converts.
- Users care about PRs, exercise history, estimated 1RM trends, and substitutions enough to make those launch stats useful.

## Open PM decisions

- What checks are required before Liftorium can move from working name to final name?
- What level of polish is expected for personal/internal use before considering a broader beta?
- When should Azure sync move from P1 into active implementation?
- What user or product signal would justify moving from free personal app to a different business model?
- What privacy requirements must be satisfied before any broader release beyond personal/internal use?
- What additional structured program families should be added after powerbuilding, powerlifting/peaking, hypertrophy/bodybuilding, and beginner linear progression?
- When should user-facing import, custom exercises, and PDF import be reconsidered? Current default: only if the app is later released broadly.

## Recommended PM defaults

If no further decisions are made yet, use these defaults for future planning:

- App name: Liftorium as working name only.
- Launch audience: users following structured workout programs, with initial samples and validation focused on strength/hypertrophy-style plans.
- Release type: personal/internal use.
- Account model: optional account placeholder only; offline use allowed without sign-in.
- Launch platforms: Android and Web.
- Program content: only private static resources produced by developer-time import; no bundled synthetic sample program for first beta.
- Imported/converted program privacy: private by default; no sharing in v1.
- Copilot import: local-first developer/operator skill with review, AI-assisted correction, and finalized app-ready resource output.
- Import launch scope: spreadsheet-first.
- First beta scope: app logging is user-facing; import is dev-time only.
- Azure sync: later/P1, not required for first beta.
- Custom exercises: later/P1; unknown exercises are resolved during developer-time import/catalog mapping before release.
- Launch stats: exercise history, personal records, estimated 1RM trend, and substitution tracking.
- e1RM approach: compare multiple formulas.
- e1RM launch lifts: squat, bench press, deadlift, and overhead press.
- Launch PR types: heaviest load, best estimated 1RM, and best reps at a given load.
- Stats UX: simple summaries with detailed drill-downs.
- Rest timer analytics: later/P1; timer controls remain P0.
- Business model: free personal app first.
- Region/language: US/English first.
- Initial import test families: powerbuilding, powerlifting/peaking, hypertrophy/bodybuilding, and beginner linear progression.
- User-facing import/custom exercises/PDF import: no user-facing import for now; reconsider only if the app is later released broadly.
- Product promise: "Follow structured workout programs on mobile, even offline, without losing the program's intent or your training history."
