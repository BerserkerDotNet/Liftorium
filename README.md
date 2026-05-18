# Liftorium

Follow structured workout programs on mobile, even offline, without losing the program's intent or training history.

> **Status: pre-MVP scaffold.** No release yet. The repository currently contains planning docs, the program-resource JSON schema, an empty Android scaffold, and a read-only Web placeholder. Workout logging, program runner, timers, and stats land in later workstreams. The product name is a working name pending trademark, domain, and app-store checks.

## What this is

Liftorium is a workout tracking app for people who already follow structured strength or hypertrophy programs and want something better than spreadsheets, PDFs, or generic trackers.

- **Android first**, local-first, offline-capable workout logging.
- **Web** is a secondary, online-only, read-only inspection surface over explicit snapshots.
- **Program import** is a developer-time spreadsheet workflow that produces versioned, app-ready program-resource JSON. Private program sources are never committed to the repository.
- **No account or cloud sync in MVP**, but data is stored locally in a sync-ready shape.

## Repository layout

```
android/                 Kotlin / Compose / Room — primary product surface (modules: app, core, data, domain)
web/                     TypeScript / React / Vite — read-only Web surface (MVP)
schema/                  Versioned program-resource JSON Schema, fixtures, Ajv-based validator
tools/import/            Developer-time spreadsheet import workflow (TypeScript)
docs/                    Planning, architecture, decisions, MVP roadmap, workstream specs
.github/                 Copilot project skills + instructions
```

Detailed planning lives under `docs/`:

- [`docs/product.md`](docs/product.md) — product scope, audience, MVP boundaries.
- [`docs/architecture.md`](docs/architecture.md) — system shape and cross-component contracts.
- [`docs/decisions.md`](docs/decisions.md) — recorded technical decisions with rationale.
- [`docs/mvp-roadmap.md`](docs/mvp-roadmap.md) — workstream order and ownership.
- [`docs/testing-strategy.md`](docs/testing-strategy.md) — unit, integration, runtime evidence.
- [`docs/workstreams/`](docs/workstreams/) — bounded specs per workstream.
- [`Liftorium-Product-Requirements.md`](Liftorium-Product-Requirements.md) — PRD-style product requirements.

## Local development

This repo expects:

- **JDK 21** for the Android build (Gradle launches under JDK 21; either set `JAVA_HOME` or pin `org.gradle.java.home` in `$GRADLE_USER_HOME/gradle.properties`; the repo intentionally does not commit a machine-specific path).
- **Android SDK** with platform 34 and build-tools 34.
- **Node 20+** for `web/`, `schema/`, and `tools/import/`.

Build / test, per surface:

```bash
# Android (from android/)
./gradlew testDebugUnitTest

# Web (from web/)
npm install && npm test

# Schema (from schema/)
npm install && npm test

# Import tool (from tools/import/)
npm install && npm test
```

## Privacy

Liftorium is intentionally local-first and privacy-conscious:

- Private program materials (PDFs, spreadsheets) are never committed; conversion to schema-compliant JSON happens at developer time via the `import-workflow` skill and is initiated by the developer themselves.
- Program-resource fixtures in this repo are synthetic and marked as such (`sourceKind: "synthetic"`, sentinel content hash).
- The MVP has no cloud backend, no account service, and no telemetry.

## Contributing

This is a pre-MVP solo project. Contributions are not currently being accepted. Issues and feedback are welcome once the MVP scaffolding stabilises and a `CONTRIBUTING.md` ships.

## License

[MIT](LICENSE) © 2026 Andrii Snihyr
