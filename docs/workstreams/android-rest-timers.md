# Android Rest Timers Workstream

## Scope owned

- Global and workout-level timer disable.
- Timer start/stop policy from preferences and workout context.
- Notification permission gating.
- Foreground-service locked-phone timer alerts.
- Clear timer failure states.

## Inputs to read

- `docs/mvp-roadmap.md`
- `docs/workstreams/android-app.md`
- `docs/workstreams/android-workout-logging.md`
- `docs/product.md`: A2 and supporting A1 rows.
- `docs/architecture.md`: timer permission/runtime contract.
- `docs/decisions.md`: Android locked-phone rest timer alerts.

## Outputs to produce

- Rest timer policy service.
- Permission UX and blocked-timer states.
- Foreground service/notification implementation.
- Runtime verification command coverage.

## Contracts not to break

- Timer disabled globally prevents auto-start.
- Notification denial blocks timer start only.
- Workout logging remains usable without timer permission.
- Locked-phone timer behavior must be verified on target API.

## Tests and evidence required

- Unit tests for global/per-workout disable and permission blocked state.
- Integration tests for timer policy from preferences and persisted timer state.
- Runtime evidence for notification denial and locked-phone alerts.

## Handoff to downstream sessions

Acceptance hardening receives target API/device evidence and any documented OS limitations.
