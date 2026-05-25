# Room schemas

Room exports a JSON schema per database version into this folder so every
migration is reviewable and testable. The exact KSP argument is wired in
`android/data/build.gradle.kts`. The path is locked in `docs/decisions.md`
(2026-05-16: foundation Room schema export path).

The `foundation` workstream delivered no Room databases, so this folder is intentionally empty
(other than this README and the `.gitkeep` marker). android-program-runner fills it in.

Rule: never delete an exported schema. Every migration test must be able to
load every prior schema version.
