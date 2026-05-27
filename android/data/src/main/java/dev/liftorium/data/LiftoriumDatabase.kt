package dev.liftorium.data

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.liftorium.data.resource.LoadedExerciseCatalogEntryEntity
import dev.liftorium.data.resource.LoadedExerciseGroupEntity
import dev.liftorium.data.resource.LoadedPrescriptionItemEntity
import dev.liftorium.data.resource.LoadedPrescriptionTargetEntity
import dev.liftorium.data.resource.LoadedProgramBlockEntity
import dev.liftorium.data.resource.LoadedProgramVersionDao
import dev.liftorium.data.resource.LoadedProgramVersionEntity
import dev.liftorium.data.resource.LoadedProgramWeekEntity
import dev.liftorium.data.resource.LoadedProgressionRuleEntity
import dev.liftorium.data.resource.LoadedRequiredReferenceEntity
import dev.liftorium.data.resource.LoadedSessionTemplateEntity
import dev.liftorium.data.resource.LoadedSetPrescriptionEntity
import dev.liftorium.data.run.ProgramRunDao
import dev.liftorium.data.run.ProgramRunEntity
import dev.liftorium.data.run.ProgramRunReferenceValueEntity
import dev.liftorium.data.run.ScheduleOccurrenceEntity
import dev.liftorium.data.workout.ActualSetEntity
import dev.liftorium.data.workout.DeviceIdentityDao
import dev.liftorium.data.workout.DeviceIdentityEntity
import dev.liftorium.data.workout.LocalMutationEntity
import dev.liftorium.data.workout.PrescriptionCalculationSnapshotEntity
import dev.liftorium.data.workout.WorkoutExerciseLogEntity
import dev.liftorium.data.workout.WorkoutLoggingDao
import dev.liftorium.data.workout.WorkoutSessionEntity

/**
 * Liftorium Room database. The `android-program-runner` workstream lands
 * the v1 baseline with the finalized program resource tables, plus the
 * program-run / schedule-occurrence / run-scoped reference-value tables.
 * The v2 schema (Phase 4 review follow-up) adds defensive audit
 * columns and indices without changing row shape; see
 * [Migration1To2] for the additive migration script.
 * Future workstreams extend this:
 *
 * * android-workout-logging: workout sessions, set logs.
 * * android-training-max-progression+: substitutions, training-max history, stats.
 *
 * Exported schemas live at
 * `android/data/schemas/dev.liftorium.data.LiftoriumDatabase/{N}.json`
 * (path locked in `docs/decisions.md`, 2026-05-16). Each schema export
 * is the diff anchor for every future migration test.
 */
@Database(
    entities = [
        LoadedProgramVersionEntity::class,
        LoadedExerciseCatalogEntryEntity::class,
        LoadedRequiredReferenceEntity::class,
        LoadedProgressionRuleEntity::class,
        LoadedProgramBlockEntity::class,
        LoadedProgramWeekEntity::class,
        LoadedSessionTemplateEntity::class,
        LoadedExerciseGroupEntity::class,
        LoadedPrescriptionItemEntity::class,
        LoadedSetPrescriptionEntity::class,
        LoadedPrescriptionTargetEntity::class,
        ProgramRunEntity::class,
        ScheduleOccurrenceEntity::class,
        ProgramRunReferenceValueEntity::class,
        WorkoutSessionEntity::class,
        WorkoutExerciseLogEntity::class,
        ActualSetEntity::class,
        PrescriptionCalculationSnapshotEntity::class,
        LocalMutationEntity::class,
        DeviceIdentityEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
public abstract class LiftoriumDatabase : RoomDatabase() {
    public abstract fun loadedProgramVersionDao(): LoadedProgramVersionDao
    public abstract fun programRunDao(): ProgramRunDao
    public abstract fun workoutLoggingDao(): WorkoutLoggingDao
    public abstract fun deviceIdentityDao(): DeviceIdentityDao
}
