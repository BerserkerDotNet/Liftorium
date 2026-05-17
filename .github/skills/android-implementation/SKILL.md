---
name: android-implementation
description: Use when adding or modifying Liftorium Android Kotlin, Compose, Room, or Android module setup code. Do not use for Android verification-only work.
---

# Android Implementation

## Stack

- Kotlin, official Kotlin style.
- Jetpack Compose + Material 3.
- AndroidX Lifecycle/ViewModel.
- Coroutines and Flow.
- Room/SQLite.
- Android 14/API 34+ target/runtime baseline is accepted in `docs/decisions.md`.

## One-time Android setup decisions

Before first Android module setup, record these in `docs/decisions.md`, `docs/testing-strategy.md`, and `verification-loop`:

- `minSdk`, `targetSdk`, `compileSdk`.
- Gradle module layout.
- Dependency version catalog policy.
- Room schema export path.
- Android runtime test device/API matrix.
- Foreground service type and permissions for rest timers.
- Exact Gradle verification commands.

## MVP service checklist

Wire Android features through explicit services/use cases:

- `ProgramResourceLoader`
- `ProgramRunService`
- `WorkoutLoggingService`
- `TrainingMaxService`
- `SubstitutionService`
- `StatsService`
- `RestTimerService`

## Persistence checklist

- Use stable client IDs and sync-ready metadata on persisted entities.
- Store event instant, event zone, and denormalized local date where time affects history or stats.
- Wrap every user-visible mutation in a Room transaction.
- Snapshot calculated prescriptions for percent/max-based work so history does not change when maxes change.
- Treat raw logs as authoritative; derived stats caches are disposable and rebuildable.

## Rest timer specifics

- Require `POST_NOTIFICATIONS` and `FOREGROUND_SERVICE` handling before locked-phone alerts.
- Document the chosen foreground service type before implementation.
- Do not use exact alarms unless a later decision changes the architecture.
- Notification denial blocks timer start only; workout logging remains usable.

## Kotlin/domain conventions

- Prefer immutable `data class` values.
- Use `sealed interface` or `sealed class` for states, commands, results, and domain errors.
- Prefer value classes for IDs when practical.
- Inject `Clock`, dispatchers, and ID generators.
- Do not use nullable values as implicit state machines.
- Avoid broad catches and silent fallbacks.
- Domain modules must not import Android framework, Room, DAO, or Compose types.

## Dependency composition

- Default to manual dependency composition until a decision approves a DI framework.
- Domain owns repository interfaces.
- Data modules implement repository interfaces.
- App composition wires implementations.
- ViewModels receive dependencies through factories or the approved DI mechanism.

## Compose conventions

- State flows down; events flow up.
- Prefer one immutable screen state object per screen.
- Use `collectAsStateWithLifecycle` for Flow collection.
- Model one-shot events explicitly.
- Use `LaunchedEffect` only for lifecycle-tied side effects with stable keys.
- Keep business rules out of composables.
- Add previews for meaningful screen states once UI exists.
- Use stable keys for lists.
- Core gym controls need large touch targets and readable labels.

## Visual feedback loop for UI work (required)

When you add or modify any Compose surface (a `@Composable` rendered on screen or a screen-state class consumed by one), you MUST complete this loop before declaring the UI deliverable done. See `docs/decisions.md` ("Visual review required for UI deliverables").

1. Write or update a **Paparazzi snapshot test** under `android/app/src/test/java/...` covering each meaningful state:
   - Initial / default
   - Empty (no data)
   - Loading
   - Loaded with representative data
   - Error / blocked
   - Notable edge sizes when responsive (compact / expanded)
   - Dark mode when theme-sensitive
2. Write or update a **Robolectric Compose render test** under the same directory for any non-trivial behavior (click handling, state transitions, conditional visibility). Use `@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, and `createAndroidComposeRule<ComponentActivity>()`. Assert on the semantics tree via `onNodeWithText(...).assertIsDisplayed()` and similar.
3. Run `:app:recordPaparazziDebug` to regenerate PNGs. They land under `android/app/src/test/snapshots/images/` (gitignored).
4. Call the `view` tool on each generated PNG and assess against the plan / wireframe / design intent: layout, alignment, Material 3 token usage, text wrapping, empty/loading/error states, spacing.
5. If visuals diverge from intent, iterate the implementation and re-run.
6. List the reviewed PNG paths in the task handoff under runtime evidence.

Fidelity caveats:
- Paparazzi (LayoutLib) and Robolectric (host-side Skia) approximate device rendering but are NOT pixel-equivalent to a real device. They cover layout, text, theme tokens, and Material 3 surfaces well; GPU effects, animations mid-flight, system bars, OEM-specific fonts, and real lifecycle behavior are NOT covered.
- Runtime-critical UI (rest timer foreground service, locked-phone notification posting, process death recovery, OEM-specific behavior) still requires real-device `:app:connectedDebugAndroidTest` evidence. Paparazzi screenshots do NOT substitute for that.
- The two renderers are complementary: a divergence between Robolectric semantics-tree assertions and Paparazzi rendered output usually indicates a real bug in state-to-render wiring.

## Room conventions

- Keep Room entities separate from domain models unless a decision explicitly allows an exception.
- Use `@Transaction` or repository-level Room transactions for multi-table writes.
- Use `Flow` for observable queries and `suspend` for one-shot reads/writes.
- Define foreign keys and indices for queried relationships.
- Do not use destructive migrations.
- Export schemas from the first schema.
- Test every migration path.
- Persist every user-visible workout mutation transactionally.

## Architecture enforcement

When modules exist, add mechanical checks for:

- Domain not depending on Android UI, Room, or data implementation modules.
- Data not depending on UI feature modules.
- Web code not imported into Android modules.

