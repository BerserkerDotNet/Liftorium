package dev.liftorium.domain.workout

import dev.liftorium.domain.EntityId

/**
 * Stable identity for a [WorkoutSession]. A workout session is the
 * actual attempt at a planned [dev.liftorium.domain.run.ScheduleOccurrence]
 * (one occurrence may have zero or many attempts; one attempt may have
 * no occurrence in MVP+1 when ad-hoc workouts land, though MVP requires
 * a planned occurrence).
 */
@JvmInline
public value class WorkoutSessionId(override val value: String) : EntityId {
    init {
        require(value.isNotEmpty()) { "WorkoutSessionId must not be empty" }
    }

    override fun toString(): String = value
}

@JvmInline
public value class WorkoutExerciseLogId(override val value: String) : EntityId {
    init {
        require(value.isNotEmpty()) { "WorkoutExerciseLogId must not be empty" }
    }

    override fun toString(): String = value
}

@JvmInline
public value class ActualSetId(override val value: String) : EntityId {
    init {
        require(value.isNotEmpty()) { "ActualSetId must not be empty" }
    }

    override fun toString(): String = value
}

@JvmInline
public value class PrescriptionCalculationSnapshotId(override val value: String) : EntityId {
    init {
        require(value.isNotEmpty()) { "PrescriptionCalculationSnapshotId must not be empty" }
    }

    override fun toString(): String = value
}
