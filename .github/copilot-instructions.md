# Liftorium Copilot Instructions

Keep this file short. It records repo-wide guardrails that apply to all work. Project skills in `.github/skills/` are auto-discovered from their descriptions; do not duplicate routing tables here.

## Stack baseline

- Android primary: Kotlin, Jetpack Compose, Material 3, Room/SQLite, coroutines/Flow.
- Web secondary: TypeScript strict mode, React, Vite, read-only snapshot surfaces.
- Program resources: versioned JSON Schema.
- Import: spreadsheet-first developer-time Copilot workflow with explicit per-import consent before cloud-assisted source processing.
- MVP has no account or cloud sync, but persisted data must be sync-ready.

## Before non-trivial work

1. Identify the affected user flow, acceptance/test IDs, contracts, and downstream consumers.
2. Read only the affected sections of `docs/decisions.md`, `docs/architecture.md`, and `docs/product.md`.
3. State needed outcomes directly; relevant project skills should be auto-discovered from their descriptions.
4. Use `ask_user` before major product, architecture, privacy, data-loss, or testing-scope decisions; if unavailable, stop and report the decision blocker.
5. Record major decisions in `docs/decisions.md`. Supersede old decisions with new entries instead of rewriting history.

## Skill use

Use natural task requests that match skill descriptions, such as validating changes, running coverage critique, discussing a change with rubber-duck review, checking Web read-only boundaries, validating program resources, or reviewing import reports.

## Repo-wide guardrails

- Build vertical slices: entry point, domain logic, persistence/resource boundary, UI or CLI surface, tests, and verification must work together.
- Every user-visible workout mutation must be transactional and testable.
- Program runs pin immutable program versions. Never mutate activated program versions.
- Web MVP is read-only and snapshot-based. Do not add Web workout mutations, offline workout storage, timer behavior, or implied access to live Android data.
- Do not print, copy, or commit private program/training source content; use IDs, hashes, filenames, sheet/cell references, and summarized issue labels.
- A task is done only when mapped tests and required runtime evidence pass.

## Development conventions

- Prefer small, complete vertical slices over disconnected layer work.
- Keep domain rules deterministic and framework-independent.
- Reuse existing helpers and contracts before adding new abstractions.
- Model invalid states explicitly with typed results/errors; do not use nullable values, broad catches, or silent fallbacks as control flow.
- Inject clocks, ID generators, dispatchers, and external boundaries so tests can prove behavior.
- Treat raw workout logs and imported resources as source-of-truth data; derived stats and caches must be rebuildable.

## Android conventions

- Android workout logging is local-first. Active workout source-of-truth lives in Room, not only ViewModel or Compose state.
- Use Kotlin, Compose, Material 3, Room/SQLite, coroutines/Flow, and AndroidX Lifecycle/ViewModel.
- Keep domain modules free of Android framework, Room, DAO, and Compose types.
- Use immutable UI state objects, state-down/events-up Compose, `collectAsStateWithLifecycle`, stable list keys, and explicit one-shot events.
- Use Room transactions for multi-table writes; export schemas from the first schema and never use destructive migrations.
- Rest timer permission denial blocks timer start only, never workout logging.

## Web conventions

- Use TypeScript strict mode, React, and Vite.
- Avoid `any`; use `unknown` plus narrowing for untrusted snapshot/resource input.
- Runtime-validate snapshot/resource data at the boundary before rendering.
- Keep Web data clients read/list/get-only for MVP and show snapshot freshness for history/stats surfaces.
- Do not introduce protected-domain writes through API clients, mutation hooks, local storage, IndexedDB, service workers, or capability copy.

## Program/import conventions

- Program resources are versioned JSON validated against schema and activation contracts.
- Unsupported imported constructs are critical unless the Program Construct Matrix classifies them as structured or note-only.
- Unknown exercises require approved alias/canonical mappings.
- First-week missing max/reference values block activation; later missing values must block affected workout start.
- Cloud-assisted import requires explicit per-import consent before source-derived AI processing.

## Testing conventions

- Write or update tests before or alongside implementation for deterministic rules and cross-component contracts.
- Unit tests cover domain calculations, validation severity, rounding, RPE/RIR matching, PR/e1RM rules, stats inclusion, substitutions, and date/time derivation.
- Integration tests prove producer/consumer wiring, Room transactions, resource loading, program-version pinning, and mutation side effects.
- Runtime/E2E evidence is required for Android lifecycle, process death, permissions, locked-phone timers, real UI behavior, and browser behavior.
- Major phase/feature acceptance requires coverage critique and rubber-duck review of traceability, contracts, migration coverage, runtime evidence, and privacy.

## Verification

Run relevant verification for changed areas and keep the verification-loop skill's command registry current.

