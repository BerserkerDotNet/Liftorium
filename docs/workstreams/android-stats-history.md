# Android Stats and History Workstream

## Scope owned

- Exercise history.
- PRs and source-set drilldowns.
- Epley and Brzycki e1RM trends for squat, bench, deadlift, and OHP.
- Stats inclusion/exclusion rules.
- Substitution history and performed-only stats.
- Non-authoritative history/stat snapshot fixture definitions for Web rendering when needed.

## Inputs to read

- `docs/mvp-roadmap.md`
- `docs/workstreams/android-app.md`
- `docs/workstreams/android-workout-logging.md`
- `docs/workstreams/android-training-max-progression.md`
- `docs/workstreams/android-catalog-substitutions.md`
- `docs/product.md`: A5 and supporting A3/A8 rows.
- `docs/architecture.md`: stats, PR, e1RM, substitution, and rounding rules.

## Outputs to produce

- Stats/history domain services and persistence.
- Rebuild/invalidation behavior for derived stats.
- History and stats UI with source drilldowns.
- Optional fixture data definitions that Web can render without implying Android export.

## Contracts not to break

- Raw logs are authoritative; derived stats caches are rebuildable.
- Edited/deleted logs invalidate or rebuild stats.
- Warmups and skipped sets do not create PR/e1RM records.
- Substituted sets count toward performed exercise stats only.
- Web snapshots are explicit fixtures/operator-provided data, not Android live data or MVP export.

## Tests and evidence required

- Unit tests for PR, e1RM, stats inclusion, caveats, and substitution stats.
- Integration tests for stats rebuild from logs and edited-set invalidation.
- Runtime evidence for history, PR/e1RM trend, substitution history, and drilldowns.

## Handoff to downstream sessions

Web receives only non-authoritative snapshot fixture definitions if needed. Acceptance hardening receives source drilldown evidence and stat correctness gaps.
