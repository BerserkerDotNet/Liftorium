---
name: web-implementation
description: Use when adding or modifying Liftorium Web TypeScript, React, or Vite code. Do not use for read-only boundary checks unless Web data access, persistence, protected workout domains, or capability copy changes.
---

# Web Implementation

## Stack

- TypeScript strict mode.
- React + Vite.
- Vitest and React Testing Library for unit/component tests.
- Playwright only for browser/runtime flows that component tests cannot prove.
- MSW or equivalent request mocking only if Web reads through an HTTP-like boundary.

## Scope

Web MVP is read-only against versioned snapshot inputs:

- Program JSON resources.
- Import validation reports.
- Non-authoritative fixture or operator-provided history/stat snapshots.

Web must not imply live Android local data access.

## TypeScript conventions

- Avoid `any`; use `unknown` plus narrowing for untrusted data.
- Prefer generated or schema-derived types from JSON Schema.
- Runtime-validate snapshot/resource inputs at the boundary before rendering.
- Keep formatting and eligibility logic in tested helpers.

## React conventions

- Use function components.
- Use `PascalCase` for components and `useXyz` for hooks.
- Separate read-only data hooks from UI components.
- Model loading, empty, and error states explicitly.
- Show snapshot freshness for snapshot history/stats.
- Keep components deterministic and small.

## Read-only enforcement

For changes touching data clients, persistence/offline behavior, workout sessions, substitutions, 1RMs, program runs, timer state, or capability copy, use `web-readonly-guard`.

## Verification

Use `verification-loop`; use `web-readonly-guard` when a change touches Web data access, persistence, protected domains, or capability copy.

