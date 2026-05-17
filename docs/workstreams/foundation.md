# Foundation Workstream

## Scope owned

- Android Kotlin/Compose/Room project setup.
- Web React/TypeScript/Vite project setup.
- Initial schema/resource folders and fixtures.
- Repeatable verification commands for created tooling.
- Mechanical architecture checks where practical.

## Inputs to read

- `docs/mvp-roadmap.md`
- `docs/architecture.md`: planned repository layout, dependency direction, test architecture.
- `docs/decisions.md`: Android stack, Web stack, testing gate, project skills.
- `.github/copilot-instructions.md`: conventions and verification expectations.

## Outputs to produce

- `android/`, `web/`, `schema/`, and `tools/` foundations as needed.
- Initial Gradle/Vite/package configuration.
- Schema fixture locations and test harness skeletons.
- Verification commands registered in `.github/skills/verification-loop/SKILL.md`; commands must run code, schema validation, generated resources, builds, tests, or runtime checks.

## Contracts not to break

- Android is primary and local-first.
- Web is secondary, online-only, and read-only.
- Domain code must stay independent of Android framework, Room, DAO, Compose, and Web code.
- No destructive Room migrations, even during early setup.

## Tests and evidence required

- Project build/typecheck smoke commands.
- Initial Android unit and instrumentation command slots.
- Initial Web typecheck/test/build command slots.
- Schema/resource validation command slot.
- No file-presence or documentation-shape verifier counts as completion evidence.

## Handoff to downstream sessions

Downstream sessions must know exact commands, module paths, schema paths, exported Room schema path, and where generated or fixture resources live.
