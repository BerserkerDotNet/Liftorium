# Acceptance Hardening Workstream

## Scope owned

- Final acceptance scenario verification.
- Privacy and source-content checks.
- Runtime evidence review.
- Migration and persistence hardening.
- Coverage critique and rubber-duck review.
- MVP release readiness decision.

## Inputs to read

- `docs/mvp-roadmap.md`
- `docs/product.md`: acceptance/test matrix and MVP success criteria.
- `docs/testing-strategy.md`
- `docs/architecture.md`
- Handoffs from all implementation workstreams.

## Outputs to produce

- Passing verification commands for every changed area.
- Final acceptance results for A1-A9.
- Fixed or explicitly blocked coverage gaps.
- Updated durable docs only when product, architecture, contract, decision, command, or acceptance/test matrix changes.

## Contracts not to break

- No MVP scenario is complete without mapped tests passing or an approved matrix change.
- No private source excerpts appear in committed files or verification output.
- Runtime behavior must be proven where mocked tests cannot prove it.
- Review logs and prompt transcripts are not committed.

## Tests and evidence required

- Full Android, Web, schema, and import verification commands.
- Android runtime evidence on target API for lifecycle, process death, permissions, and locked-phone timer alerts.
- Web read-only guard evidence.
- Import/resource validation evidence without private excerpts.
- Critique and rubber-duck findings resolved or documented as blockers in the task handoff.

## Handoff to downstream sessions

If MVP is not releasable, hand off only unresolved blockers, failing command names, affected acceptance IDs, and exact durable docs/code that must change.
