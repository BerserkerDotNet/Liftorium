package dev.liftorium.domain.workout

import dev.liftorium.core.KoverIgnore

/**
 * Lifecycle states for a [WorkoutSession].
 *
 * Only one session per device may be `InProgress` at a time; the DB
 * enforces this via the partial-unique index on
 * `WorkoutSessionEntity.activeWorkoutSlot` (see ADR 2026-05-25
 * "One in-progress workout session enforced by DB unique index on
 * activeWorkoutSlot").
 */
@KoverIgnore
public enum class WorkoutSessionStatus {
    Planned,
    InProgress,
    Completed,
    Abandoned,
}

/**
 * Set classification copied from the prescription. Mirrors the
 * `setPrescription.role` enum on the resource side
 * (`schema/program-resource.schema.json`).
 *
 * [Extra] is the user-added overflow row that never had a prescription
 * counterpart; the prescription enum does not have an `extra` value.
 */
@KoverIgnore
public enum class SetRole {
    Warmup,
    Working,
    TopSet,
    BackOff,
    Amrap,
    Optional,
    Extra,
}

/**
 * Lifecycle states for an [ActualSet].
 *
 * `Pending` is the seeded-but-not-yet-touched state. `Completed` and
 * `Skipped` are terminal user-visible states; either can transition
 * back to `Pending` only via an undo mutation (session-visible undo;
 * see [MutationType.UndoSet]).
 */
@KoverIgnore
public enum class SetState {
    Pending,
    Completed,
    Skipped,
}

/**
 * Recognised user-visible mutation types for workout logging.
 *
 * SINGLE source of truth for the durability contract defined in
 * `docs/architecture.md` ("Durability contract" — `MVP user-visible
 * mutations also include ...`). Adding a value REQUIRES a paired
 * transactional persistence test and a contract update.
 *
 * The companion exposes the classification helpers used by
 * `WorkoutLoggingService`:
 *  * [isDurableUserVisible] — writes a [LocalMutation] audit row;
 *  * [isSessionVisibleUndo] — session-only, no durable row.
 */
@KoverIgnore
public enum class MutationType {
    StartWorkout,
    CompleteSet,
    EditSet,
    SkipSet,
    SkipExercise,
    AddExtraSet,
    UpdateNote,
    LogRpeRir,
    CompleteWorkout,
    AbandonWorkout,
    UndoSet,
    ;

    public companion object {
        public fun isDurableUserVisible(type: MutationType): Boolean = type != UndoSet
        public fun isSessionVisibleUndo(type: MutationType): Boolean = type == UndoSet
    }
}
