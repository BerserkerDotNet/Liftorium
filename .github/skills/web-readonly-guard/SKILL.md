---
name: web-readonly-guard
description: Use when a Liftorium Web change touches data clients, persistence/offline behavior, workout sessions, substitutions, 1RMs, program runs, timer state, or Web capability copy. Checks MVP read-only boundaries.
---

# Web Read-Only Guard

## Inputs

- Web source path.
- API client path.
- Test command.

## Checks

- No workout logging mutation path exists.
- No substitution mutation path exists.
- No 1RM mutation path exists.
- No program-run mutation path exists.
- No timer-state mutation path exists.
- No offline workout logging, locked-phone timer, or live Android sync claim exists.
- API client exposes read-only operations only.
- Snapshot freshness renders wherever history or stats snapshots render.

## Suggested enforcement

- Typed API client exposes GET/read methods only in MVP.
- Tests fail if mutation hooks, non-GET methods, write-capable protected-domain storage, service-worker offline workout persistence, or misleading capability copy are introduced in Web MVP.
- Components import read-only data hooks only.
- Scan for `POST`, `PUT`, `PATCH`, `DELETE`, `mutate`, `create`, `update`, `delete`, `save`, `startWorkout`, `completeSet`, React Query/SWR mutation hooks, `localStorage`, `indexedDB`, and service workers in protected MVP domains.
- UI-only preferences may use local storage only when unrelated to protected MVP domains.

## Output

- Pass/fail result.
- Mutation paths found, if any.
- Files requiring review.

