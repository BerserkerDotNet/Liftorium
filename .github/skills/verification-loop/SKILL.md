---
name: verification-loop
description: Use when choosing, running, or updating Liftorium verification commands and recording evidence for changed areas. Do not use for implementation planning or coverage critique.
---

# Verification Loop

Use this as the single command registry for the repo. Add commands here as projects and capabilities are created.

Verification commands must exercise implementation outputs: code, schemas, generated resources, builds, tests, or runtime behavior. Do not count file-presence checks, documentation-shape checks, or prompt/process logs as completion verification.

## Status legend

- `passes now` — command runs successfully in the current repo with no extra setup.
- `passes now, JDK 21 on PATH` — runs successfully but requires JDK 21 visible to the wrapper (set `JAVA_HOME` or rely on `org.gradle.java.home` in `android/gradle.properties`).
- `gated: <reason>` — command is registered as the canonical entry point for this area but will not pass until the reason is resolved. A gated command does NOT count as completion evidence for the area it covers.

## Current command registry

All commands are run from the repository root unless noted.

### Android — Kotlin/JVM domain and core (no Android SDK required)

| Area | Command | Status | Required before done |
| --- | --- | --- | --- |
| Domain unit tests (incl. architecture guard) | `cd android; .\gradlew.bat :domain:test --console=plain` | passes now, JDK 21 on PATH | Any change to `:domain` or to the framework-independence guarantee |
| Core unit tests | `cd android; .\gradlew.bat :core:test --console=plain` | passes now, JDK 21 on PATH | Any change to `:core` |

### Android — modules requiring the Android SDK platform

These commands need `platforms;android-34` and `build-tools;34.0.0` installed locally (see one-time setup below).

| Area | Command | Status | Required before done |
| --- | --- | --- | --- |
| App + data JVM unit tests | `cd android; .\gradlew.bat :app:testDebugUnitTest :data:testDebugUnitTest --console=plain` | passes now, JDK 21 + SDK 34 on machine | Any change to `:app` or `:data`. `:app:testDebugUnitTest` runs the trivial JUnit smoke, Robolectric-driven Compose render/semantics tests, and Paparazzi snapshot verification against committed goldens. |
| Android build smoke | `cd android; .\gradlew.bat :app:assembleDebug --console=plain` | passes now, JDK 21 + SDK 34 on machine | Any change that touches `:app` Gradle config, manifest, or release surface |
| Android lint | `cd android; .\gradlew.bat :app:lintDebug --console=plain` | passes now, JDK 21 + SDK 34 on machine | Any change to `:app` Compose UI or manifest |
| Compose visual snapshots (Paparazzi) | `cd android; .\gradlew.bat :app:recordPaparazziDebug --console=plain` | passes now, JDK 21 + SDK 34 on machine | Any change that adds or modifies a Compose surface or its screen state. Regenerates PNGs under `android/app/src/test/snapshots/images/` (gitignored). The agent MUST then call `view` on each generated PNG and list the paths in evidence. See the visual-review policy in `docs/decisions.md`. |
| Room migration tests | `cd android; .\gradlew.bat :data:testDebugUnitTest --tests "*Migration*" --console=plain` | gated: first Room `@Database` does not exist yet (Phase 4) | Any Room schema bump |
| Android instrumentation/Compose | `cd android; .\gradlew.bat :app:connectedDebugAndroidTest --console=plain` | gated: requires an attached API 30+ device or emulator | Runtime-critical UI (rest timer foreground service, locked-phone notification, process death). NOT substituted by Robolectric/Paparazzi host-side renders. |
| All Android tests (umbrella) | `cd android; .\gradlew.bat check --console=plain` | passes now, JDK 21 + SDK 34 on machine | Major Android features |

### Web

| Area | Command | Status | Required before done |
| --- | --- | --- | --- |
| Web typecheck | `cd web; npm run typecheck` | passes now | Any change under `web/` |
| Web unit/component tests | `cd web; npm test` | passes now | Any change under `web/` |
| Web production build | `cd web; npm run build` | passes now | Any change that ships to the Web release surface |
| Web read-only guard | (registered when the first Web data client lands) | gated: no Web data client yet (Phase 10) | Web data client, persistence, protected-domain, or capability copy change |

### Schema / program resources

| Area | Command | Status | Required before done |
| --- | --- | --- | --- |
| Program-resource schema validation | `cd schema; npm test` | passes now | Any change to `schema/program-resource.schema.json`, the semantic validator (`schema/semantics.ts`), the content-hash helper (`schema/hash.ts`), fixtures, or examples |
| Program-resource TypeScript typecheck | `cd schema; npm run typecheck` | passes now | Any change to `schema/*.ts` or `schema/test/*.ts` |
| Refresh fixture/example content hashes | `cd schema; npm run refresh-fixture-hashes` | passes now | Any edit to a fixture or example under `schema/fixtures/` (except `blocked-content-hash-mismatch.json`) or `schema/examples/`. Must be run before `npm test` so the hash-freshness assertions pass. |

### Tools

| Area | Command | Status | Required before done |
| --- | --- | --- | --- |
| Import tooling typecheck | `cd tools\import; npm run typecheck` | passes now | Any change under `tools/import` |
| Import tooling smoke tests | `cd tools\import; npm test` | passes now | Any change under `tools/import` |
| Import report validation | (added by Phase 3 once a real importer exists) | gated: no importer yet (Phase 3) | Import pipeline changes |

## Workflow

1. Identify changed areas.
2. Run every registered command for those areas that is not gated.
3. If a changed area lacks a registered command that is `passes now`, define a runnable command first or stop with a blocker. A feature cannot be complete without runnable verification for its area.
4. For any change that adds or modifies a Compose surface, run `:app:recordPaparazziDebug`, then call the `view` tool on each regenerated PNG under `android/app/src/test/snapshots/images/` and assess against the plan/wireframe. List the PNG paths in evidence. See visual-review policy in `docs/decisions.md`.
5. Capture pass/fail output without private source excerpts.
6. Fix failures caused by the change.
7. Update this registry whenever commands change.
8. Whenever a gating condition is resolved (e.g. Android SDK installed, first Room schema landed, first Web data client exists), promote the affected row from `gated` to `passes now` and run it before declaring related work complete.

## One-time local setup

- Install Android SDK platform 34 and build-tools 34.0.0 via Android Studio's SDK Manager (or via `sdkmanager` if the command-line tools are installed). The `compileSdk = 34` value in `android/app/build.gradle.kts` and `android/data/build.gradle.kts` requires both to exist locally. Once installed, create `android/local.properties` with `sdk.dir=<path>` (auto-created by Android Studio on first open; gitignored).
- JDK 21 must be installed. The committed `android/gradle.properties` pins `org.gradle.java.home` to `C:/Program Files/Android/openjdk/jdk-21.0.8`. If your JDK 21 lives elsewhere, override it locally in `%GRADLE_USER_HOME%/gradle.properties` rather than editing the committed pin.

## Evidence

Record commands run, pass/fail result, failing test names, concise diagnostics, and runtime evidence paths when runtime behavior is involved. For UI changes, runtime evidence MUST include the regenerated Paparazzi PNG paths the agent reviewed.

## Rules

- Do not rely on undocumented local commands.
- Do not skip runtime verification for lifecycle, persistence, permission, timer, or real UI behavior.
- Do not print private program source content in verification output.
- Do not treat a `gated:` row as evidence; promote it first.
