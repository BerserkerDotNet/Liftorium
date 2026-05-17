# Web Read-Only Workstream

## Scope owned

- Responsive Web app with React, TypeScript, and Vite.
- Program inspection from versioned resources.
- Import report review surfaces.
- Read-only history/stats rendering from explicit snapshot schemas/fixtures owned by this workstream.
- Web read-only guard tests.

## Inputs to read

- `docs/mvp-roadmap.md`
- `docs/product.md`: Web scope and success criteria.
- `docs/architecture.md`: Web data-source boundary.
- `docs/architecture.md`: Web read-only guard contract.
- `docs/workstreams/program-resources.md`

## Outputs to produce

- `web/` app setup and UI surfaces.
- Snapshot/resource runtime validation at the boundary.
- History/stat snapshot schemas or fixture contracts for Web rendering; these are not Android export contracts.
- Read/list/get-only data clients.
- Snapshot freshness display for history/stats surfaces.
- Web typecheck, test, read-only guard, and production build commands in the verification-loop skill.

## Contracts not to break

- Web cannot mutate workout sessions, substitutions, training maxes, program runs, timer state, or source program resources.
- Web cannot imply live Android Room/local data access.
- Web cannot claim offline workout logging, locked-phone timer behavior, or sync continuity.
- Protected-domain writes via API clients, mutation hooks, local storage, IndexedDB, or service workers are out of MVP.

## Tests and evidence required

- Typecheck and production build.
- Component tests for program/report/history/stat rendering.
- Runtime validation tests for invalid snapshots.
- Read-only guard tests for mutation verbs, mutation hooks, protected storage, and misleading capability copy.

## Handoff to downstream sessions

Acceptance hardening receives Web commands, read-only guard evidence, snapshot schemas, and known unsupported surfaces.
