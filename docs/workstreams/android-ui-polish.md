# Android UI Polish Workstream

## Scope owned

- Compose theme tokens for Liftorium: color scheme (light + dark), typography scale, spacing/density scale, shape scale.
- Brand identity: app icon (adaptive icon + monochrome), launch screen, app name copy.
- Reusable Compose components that replace ad-hoc Material 3 usages:
  - `StatusBadge` (replaces the misuse of `AssistChip` for non-tappable status pills).
  - `EmptyState` (replaces inline `Column` + `Text` empty states).
  - `SectionHeader`, `ValueRow`, and other primitives that subsequent workstreams will reach for.
- Density and spacing audit of every screen that already exists from `android-program-runner` (library, detail, today, both modals, error banner).
- Updated Paparazzi goldens for every existing screen after the retheme; visual evidence is part of acceptance.
- Navigation Compose adoption (`androidx.navigation:navigation-compose`): replace the ad-hoc `LiftoriumNavHost` `sealed interface NavRoute` + `mutableStateOf` routing introduced in Phase 4. Define a typed route taxonomy (library / detail / today), an arg-serialization contract for `ProgramVersionId` and `ProgramRunId`, and a saved-state strategy that survives process death without losing the current screen. Per `docs/decisions.md` 2026-05-17 ("Defer Navigation Compose adoption to the android-ui-polish workstream").

## Inputs to read

- `docs/mvp-roadmap.md`
- `docs/workstreams/android-program-runner.md`: the screens that exist today.
- `docs/product.md`: workout-flow UX implications for the components that the next workstream will consume.
- `docs/decisions.md`: prior decisions about visual evidence (`Visual review of generated UI snapshots is required for UI deliverables`).

## Outputs to produce

- `:app/ui/theme/` package containing:
  - `LiftoriumColorScheme.kt` (seeded brand palette + dark variant).
  - `LiftoriumTypography.kt` (display / headline / title / body / label / numeric scales, with explicit usage rules — e.g. weight values use a tabular-numerics style).
  - `LiftoriumShapes.kt`, `LiftoriumSpacing.kt`, `LiftoriumDimens.kt`.
  - `LiftoriumTheme.kt` Composable wrapper that consumers use INSTEAD of `MaterialTheme {}`.
- `:app/ui/components/` package containing the shared primitives listed under Scope.
- Split `ProgramScreens.kt` (currently 508 lines, 5 public Composables — trips the soft file-size ceiling per `docs/decisions.md` 2026-05-25) into per-screen files under `:app/ui/program/`: `ProgramLibraryScreen.kt`, `ProgramDetailScreen.kt`, `TodaySessionScreen.kt`, `PendingReferencesDialog.kt`, `WeekVariantPicker.kt`, `ImportErrorBanner.kt`. Private helpers stay co-located with the single public Composable that uses them; helpers reused across screens promote to `:app/ui/components/`. Paparazzi/Robolectric test classes mirror the new file layout (one test class per screen file).
- Brand assets under `:app/src/main/res/mipmap-anydpi-v26/` + `drawable/` (adaptive icon, monochrome, themed icon).
- A style-guide note in `docs/design-system.md` documenting token names, when to reach for which typography step, when to use `StatusBadge` vs other surfaces, and the visual-review checklist contributors apply when adding new screens.

## Contracts not to break

- Every screen owned by `android-program-runner` must remain functionally identical (same testTags, same state-down/events-up shape, same semantics for the Robolectric Activate-flow test). Polish is visual only.
- No new persistence, no new domain types. The only permitted new module dependency is `androidx.navigation:navigation-compose` (and its transitive `navigation-runtime` / `navigation-common`); all other dependencies stay as Phase 4 ended them. `:app` continues to be the only module that touches Compose / Material 3 / Navigation.
- Tokens must be declared as `@Stable` / `@Immutable` data classes where appropriate so Compose recomposition stays efficient.
- Dark-mode parity: every token has both light and dark values; every component reads through the theme — no hard-coded colors anywhere.

## Tests and evidence required

- Updated Paparazzi snapshots for every existing screen (library populated/empty, detail activatable/pending-refs, both modals, today, error banner) under both light and dark themes.
- Each new PNG visually reviewed by the coding agent against the style guide before acceptance, per the visual-review policy in `docs/decisions.md`.
- Existing Robolectric Compose semantics test (`ActivateFlowSemanticsTest`) continues to pass without modification — proof that polish did not change behavior.
- Coverage gate (`:domain` + `:data`) unchanged — this workstream lives entirely in `:app` which is excluded from the gate.

## Handoff to downstream sessions

`android-workout-logging` and every subsequent Android workstream consume `LiftoriumTheme` instead of `MaterialTheme`, reach for `LiftoriumSpacing.*` / `LiftoriumTypography.*` instead of raw dp/sp values, and use the shared components for status pills and empty states. Adding a new ad-hoc `AssistChip` or inline empty-state `Column` is treated as a coverage-review finding.
