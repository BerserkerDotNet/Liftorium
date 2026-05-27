# Android 1RM and Progression Workstream

## Scope owned

- 1RM/reference setup.
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
- `docs/architecture.md`: prescription, rounding, 1RM, progression, stats rules.

## Outputs to produce

- 1RM domain services and persistence.
- Prescription calculation helpers.
- Snapshot fields consumed by workout logging/history.
- Clear blockers for missing required max/reference values.
- Target specificity resolver: when a set carries multiple targets, pick the most specific available in this priority — (1) actual prescribed weight (range or single), (2) `percent`/`percentMin`+`percentMax` against a `one_rep_max` reference, (3) `rpe` / `rir`. When percent and RPE coexist (conjunctive), display the calculated weight AND the RPE companion; never drop one.
- Range expansion: when a percent range or weight range is present, compute BOTH bounds, round each per the program's rounding rules, and surface a "min–max" UI label (e.g. "150–160 lb at 75–80%"). Equal bounds collapse to a single value.

## Contracts not to break

- 1RM updates affect future prescriptions only.
- Historical workouts keep calculation snapshots unchanged.
- First-week missing 1RMs block activation; later missing 1RMs block affected workout start.
- Rounding follows default and override precedence.
- Target specificity order is fixed: weight > percent > RPE/RIR. A more-specific target never silently replaces a less-specific one's metadata (e.g. the RPE companion of a conjunctive set stays visible).
- Range-target calculation snapshots store BOTH the min and max calculated weight, not just one bound.

## Tests and evidence required

- Unit tests for effective-dated 1RMs, rounding, percent calculations, and progression.
- Integration tests for future prescription changes and unchanged history snapshots.
- Runtime evidence for updating a 1RM and seeing future-only changes.

## Handoff to downstream sessions

Workout logging receives calculated prescription snapshots. Stats receives caveats/source fields needed for drilldowns.
