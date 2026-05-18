# Product Scope

## Product promise

Follow structured workout programs on mobile, even offline, without losing the program's intent or training history.

## MVP audience

Primary user: a person following structured strength or hypertrophy programs who currently relies on spreadsheets, PDFs, notes, or generic trackers.

Secondary user: developer/operator importing private spreadsheet programs into app-ready resources.

## MVP platform decisions

- Android is the primary product surface.
- Web is secondary, online-only, responsive, and read-only.
- Account and cloud sync are not in MVP.

## MVP included scope

- Program library and program details.
- One active program run at a time.
- Start, run, complete, repeat, abandon, restart, and schedule-control flows for structured programs.
- Offline Android workout logging for the active program.
- In-progress workout recovery after app close, process death, or interruption.
- Fast set logging with edits, skips, extra sets, notes, and session-visible undo.
- Rest timer controls and Android locked-phone timer alerts through foreground-service/notification behavior.
- Program-provided substitutions and generated catalog substitutions with operator approval.
- Training max/reference values where required by a program.
- Percent/training-max prescriptions.
- RPE/RIR prescriptions.
- Supported progression automation.
- Exercise history, PRs, e1RM trends, and substitution history.
- Spreadsheet-first developer-time Copilot import producing finalized versioned JSON resources.

## MVP excluded scope

- Exportable workout history.
- Azure account or cloud sync.
- Web workout logging.
- Web offline behavior.
- PDF-assisted import.
- User-facing import.
- User-created custom exercises.
- Unscheduled/non-program workouts.
- Formal accessibility acceptance, beyond large touch targets and readable mobile gym UI.
- Social features, marketplace, coach portal, wearables, health integrations, and in-app AI coaching.

## Acceptance scenarios

- A1: User starts a program, logs a workout offline, closes/kills the app, reopens, and sees all logged work recovered.
- A2: User disables rest timers globally and no timer auto-starts.
- A3: User substitutes an exercise and history shows both prescribed and performed exercise.
- A4: User repeats a completed program and sees separate run histories.
- A5: User views exercise history, PRs, e1RM trend, and substitution history.
- A6: Developer/operator runs spreadsheet-first Copilot import and receives finalized app-ready JSON plus validation report.
- A7: Developer/operator reviews, chats with Copilot to correct issues, reruns validation, and finalizes before release.
- A8: User updates a training max and future calculated prescriptions change while historical workouts remain unchanged.
- A9: User reschedules a missed workout and actual completion date remains distinct from planned program day.

## Acceptance and test matrix

Test IDs are stable labels for future implementation and must be updated if the behavior spec changes.

| Scenario | Contract areas | Planned unit tests | Planned integration tests | Planned runtime/E2E tests |
| --- | --- | --- | --- | --- |
| A1 Offline workout recovery | Durability, Room migration, sync-readiness, Android architecture | UT-DUR-001 transaction classification; UT-DUR-002 recovery state rules; UT-MUT-001 local mutation metadata | IT-DB-001 open session persistence; IT-DB-002 recovery from Room; IT-MIG-001 raw logs preserved | E2E-AND-001 offline workout complete; E2E-AND-002 process kill recovery |
| A2 Timer disabled no auto-start | Timer contract, user preferences | UT-TMR-001 global disable; UT-TMR-002 per-workout disable; UT-TMR-003 permission blocked state | IT-TMR-001 timer policy from preferences; IT-TMR-002 persisted timer state | E2E-AND-003 timer disabled no auto-start; E2E-AND-004 notification denied blocks timer only |
| A3 Substitution history | Substitution stats, durability, exercise recognition | UT-SUB-001 performed-only stats; UT-SUB-002 undo policy; UT-SUB-003 original/performed preservation | IT-SUB-001 substitution to log/history/stats; IT-SUB-002 substitution undo audit | E2E-AND-005 substitute and inspect history |
| A4 Repeat completed program | JSON versioning, schedule, sync-readiness | UT-SCH-001 repeat run identity; UT-SCH-002 version pinning; UT-SCH-003 separate actual attempts | IT-RUN-001 repeat creates new run; IT-RUN-002 active/completed histories remain separate | E2E-AND-006 repeat completed program |
| A5 History, PRs, e1RM, substitution stats | Stats inclusion, PR definitions, e1RM, substitution stats | UT-STATS-001 PR heaviest load; UT-STATS-002 best reps at load; UT-E1RM-001 Epley; UT-E1RM-002 Brzycki; UT-STATS-003 inclusion matrix; UT-STATS-004 source drilldown IDs | IT-STATS-001 stats rebuild from logs; IT-STATS-002 edited set invalidates stats; IT-STATS-003 substitution stats performed-only | E2E-AND-007 inspect stats drilldown |
| A6 Spreadsheet import output | Program Construct Matrix, validation severity, import privacy, JSON resource versioning | UT-IMP-001 construct classification (schema/semantics.ts severity sets); UT-IMP-002 severity assignment (schema fixtures + validator CLI exit codes); UT-IMP-003 provenance privacy (warning-private-import-provenance.json fixture + validator CLI privacy scan); UT-JSON-001 schema version required (schema/test/program-resource.schema.test.ts) | IT-IMP-001 spreadsheet to JSON/report (verified via skill procedure — `.github/skills/import-workflow/SKILL.md` Steps 1–10); IT-IMP-002 activation blocked by critical (blocked-* fixtures + validator CLI exit 2); IT-IMP-003 approved exercise mappings required (blocked-unknown-exercise.json fixture + validator CLI) | CLI-IMP-001 private fixture import (verified via skill procedure — Step 0 pre-flight metadata + Steps 1–10 procedure run) |
| A7 Import correction/finalization | Validation severity, JSON versioning, import privacy, skill workflow | UT-IMP-004 correction clears critical (verified via skill procedure — Step 8 correction loop); UT-JSON-002 resource version identity (schema/test/hash-freshness.test.ts + validator CLI hash recompute); UT-IMP-005 source provenance recorded (verified via skill procedure — Step 0 pre-flight metadata pins `importAudit.sourceKind = "private_import"` + `sourceHash` + `sourceFilename` before any cell is parsed) | IT-IMP-004 correction reruns validation (verified via skill procedure — Step 8 loop + Step 9 determinism check); IT-IMP-005 finalization preserves audit (verified via skill procedure — Step 10 two-artifact contract; schema/importAudit field constraints); IT-IMP-006 same version different hash rejected (Phase 4 ProgramResourceLoader at runtime; Phase 3 covers in-resource hash contract via blocked-content-hash-mismatch.json) | CLI-IMP-002 Copilot correction loop (verified via skill procedure — Step 8 loop + Step 9 byte-stable contentHash across rerun) |
| A8 Training max future-only updates | Training max, prescription snapshots, rounding, progression | UT-MAX-001 effective-dated max; UT-CALC-001 percent rounding; UT-CALC-002 program rounding override; UT-PROG-001 supported progression | IT-MAX-001 future prescription changes; IT-MAX-002 history snapshot unchanged; IT-PROG-001 progression updates future only | E2E-AND-008 update max future-only |
| A9 Reschedule missed workout | Schedule, time zone, durability | UT-SCH-004 planned vs actual date; UT-TZ-001 local date from event zone; UT-SCH-005 reschedule mutation metadata | IT-SCH-001 reschedule preserves actual completion; IT-SCH-002 planned occurrence distinct from workout session | E2E-AND-009 reschedule missed session |

No scenario may be marked complete until all mapped tests are implemented and passing, or the user explicitly approves an acceptance/test matrix change.

## MVP success criteria

- All acceptance scenarios A1-A9 pass on Android unless the scenario is explicitly developer/import-facing.
- Every acceptance scenario maps to contracts and planned test IDs in the acceptance and test matrix above.
- Every supported deterministic rule has unit tests or an explicit contract gap blocking implementation.
- Every cross-component behavior has integration or runtime tests.
- Web MVP remains read-only and online-only.
- Import validation blocks activation for critical issues and produces an understandable report.

