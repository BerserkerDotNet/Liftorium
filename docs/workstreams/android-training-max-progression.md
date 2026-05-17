# Android Training Max and Progression Workstream

## Scope owned

- Training max/reference setup.
- Percent load calculation and rounding.
- Program-specific rounding overrides.
- Calculation snapshots on logged prescriptions.
- Future-only max updates.
- Supported progression automation.

## Inputs to read

- `docs/mvp-roadmap.md`
- `docs/workstreams/android-app.md`
- `docs/workstreams/android-workout-logging.md`
- `docs/product.md`: A8 and A5 rows.
- `docs/architecture.md`: prescription, rounding, training max, progression, stats rules.

## Outputs to produce

- Training max domain services and persistence.
- Prescription calculation helpers.
- Snapshot fields consumed by workout logging/history.
- Clear blockers for missing required max/reference values.

## Contracts not to break

- Training max updates affect future prescriptions only.
- Historical workouts keep calculation snapshots unchanged.
- First-week missing maxes block activation; later missing maxes block affected workout start.
- Rounding follows default and override precedence.

## Tests and evidence required

- Unit tests for effective-dated maxes, rounding, percent calculations, and progression.
- Integration tests for future prescription changes and unchanged history snapshots.
- Runtime evidence for updating a max and seeing future-only changes.

## Handoff to downstream sessions

Workout logging receives calculated prescription snapshots. Stats receives caveats/source fields needed for drilldowns.
