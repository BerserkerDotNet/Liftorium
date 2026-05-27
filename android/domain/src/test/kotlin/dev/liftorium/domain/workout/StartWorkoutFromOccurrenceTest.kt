package dev.liftorium.domain.workout

import dev.liftorium.domain.common.WeightUnit
import dev.liftorium.domain.run.ProgramRunId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class StartWorkoutFromOccurrenceTest {

    @Test
    fun `unknown occurrence returns failure without inserting`() = runTest {
        val repo = FakeWorkoutLoggingRepository(plan = null)
        val useCase = makeUseCase(repo)

        val result = useCase(
            StartWorkoutCommand(
                programRunId = ProgramRunId("run-1"),
                plannedOccurrenceId = "occ-missing",
            ),
        )
        assertEquals(
            StartWorkoutResult.Failure.UnknownOccurrence("occ-missing"),
            result,
        )
        assertNull(repo.lastInsert)
    }

    @Test
    fun `empty plan returns failure`() = runTest {
        val repo = FakeWorkoutLoggingRepository(
            plan = WorkoutTestFixtures.plan(exercises = emptyList()),
        )
        val useCase = makeUseCase(repo)

        val result = useCase(StartWorkoutCommand(ProgramRunId("run-1"), "occ-1"))
        assertTrue(result is StartWorkoutResult.Failure.EmptyPlan)
        assertNull(repo.lastInsert)
    }

    @Test
    fun `warmup set count generates that many warmup sets in pending state`() = runTest {
        val plan = WorkoutTestFixtures.plan(
            exercises = listOf(
                WorkoutTestFixtures.planExercise(
                    warmupSetCount = 3,
                    sets = listOf(WorkoutTestFixtures.workingSet()),
                ),
            ),
        )
        val repo = FakeWorkoutLoggingRepository(plan = plan)
        val useCase = makeUseCase(repo)

        val result = useCase(StartWorkoutCommand(ProgramRunId("run-1"), "occ-1"))
        assertTrue(result is StartWorkoutResult.Success)

        val captured = assertNotNull(repo.lastInsert)
        val warmups = captured.sets.filter { it.role == SetRole.Warmup }
        assertEquals(3, warmups.size, "exactly warmupSetCount warmup rows are seeded")
        for (warmup in warmups) {
            assertEquals(SetState.Pending, warmup.state)
            assertNull(warmup.prescribedSetId, "warmup rows have no prescribedSetId")
            assertNull(warmup.calculationSnapshotId, "warmup rows have no calculation snapshot")
        }
        val warmupSnapshots = captured.snapshots.filter { snap ->
            warmups.any { it.actualSetId == snap.actualSetId }
        }
        assertTrue(warmupSnapshots.isEmpty(), "warmup rows do not produce calculation snapshots")
    }

    @Test
    fun `absent warmup count yields no auto-generated warmup rows`() = runTest {
        val plan = WorkoutTestFixtures.plan(
            exercises = listOf(
                WorkoutTestFixtures.planExercise(
                    warmupSetCount = 0,
                    sets = listOf(WorkoutTestFixtures.workingSet()),
                ),
            ),
        )
        val repo = FakeWorkoutLoggingRepository(plan = plan)
        val useCase = makeUseCase(repo)

        val result = useCase(StartWorkoutCommand(ProgramRunId("run-1"), "occ-1"))
        assertTrue(result is StartWorkoutResult.Success)
        val captured = assertNotNull(repo.lastInsert)
        assertTrue(captured.sets.none { it.role == SetRole.Warmup })
    }

    @Test
    fun `conjunctive percent and rpe targets preserve both in snapshot`() = runTest {
        val plan = WorkoutTestFixtures.plan(
            exercises = listOf(
                WorkoutTestFixtures.planExercise(
                    sets = listOf(
                        WorkoutTestFixtures.workingSet(
                            percent = 0.80,
                            targetRpe = 8.5,
                            targetReps = 5,
                            displayLoad = 110.0,
                        ),
                    ),
                ),
            ),
        )
        val repo = FakeWorkoutLoggingRepository(plan = plan)
        val useCase = makeUseCase(repo)

        useCase(StartWorkoutCommand(ProgramRunId("run-1"), "occ-1"))
        val snapshot = repo.lastInsert!!.snapshots.single()
        assertEquals(0.80, snapshot.percent, "percent retained")
        assertEquals(8.5, snapshot.targetRpe, "RPE retained alongside percent")
        assertEquals(5, snapshot.targetReps)
        assertEquals(110.0, snapshot.displayLoad)
        assertEquals(WeightUnit.Kg, snapshot.displayLoadUnit)
    }

    @Test
    fun `start mutation is StartWorkout and references the session id`() = runTest {
        val repo = FakeWorkoutLoggingRepository()
        val useCase = makeUseCase(repo)

        useCase(StartWorkoutCommand(ProgramRunId("run-1"), "occ-1"))
        val captured = assertNotNull(repo.lastInsert)
        assertEquals(MutationType.StartWorkout, captured.startMutation.type)
        assertEquals(WORKOUT_SESSION_ENTITY_TYPE, captured.startMutation.entityType)
        assertEquals(captured.session.workoutSessionId.value, captured.startMutation.entityId)
        assertEquals(
            captured.session.lastSavedMutationId,
            captured.startMutation.clientMutationId,
            "session.lastSavedMutationId matches start mutation id",
        )
    }

    @Test
    fun `every seeded row carries the start mutation id in sync metadata`() = runTest {
        val plan = WorkoutTestFixtures.plan(
            exercises = listOf(
                WorkoutTestFixtures.planExercise(
                    warmupSetCount = 2,
                    sets = listOf(
                        WorkoutTestFixtures.workingSet(prescribedSetId = "set-a"),
                        WorkoutTestFixtures.workingSet(prescribedSetId = "set-b"),
                    ),
                ),
            ),
        )
        val repo = FakeWorkoutLoggingRepository(plan = plan)
        val useCase = makeUseCase(repo)

        useCase(StartWorkoutCommand(ProgramRunId("run-1"), "occ-1"))
        val captured = assertNotNull(repo.lastInsert)
        val mutationId = captured.startMutation.clientMutationId
        assertEquals(mutationId, captured.session.syncMetadata.clientMutationId)
        for (log in captured.exercises) {
            assertEquals(mutationId, log.syncMetadata.clientMutationId)
            assertEquals(1L, log.syncMetadata.localRevision, "initial insert revision is 1")
        }
        for (set in captured.sets) {
            assertEquals(mutationId, set.syncMetadata.clientMutationId)
            assertEquals(1L, set.syncMetadata.localRevision)
        }
    }

    @Test
    fun `already active session race is propagated`() = runTest {
        val repo = FakeWorkoutLoggingRepository()
        repo.setInsertOutcome(InsertWorkoutSessionOutcome.AlreadyActiveSession)
        val useCase = makeUseCase(repo)

        val result = useCase(StartWorkoutCommand(ProgramRunId("run-1"), "occ-1"))
        assertEquals(StartWorkoutResult.Failure.AlreadyActiveSession, result)
    }

    @Test
    fun `set sequence runs warmups first then working, contiguous within exercise`() = runTest {
        val plan = WorkoutTestFixtures.plan(
            exercises = listOf(
                WorkoutTestFixtures.planExercise(
                    warmupSetCount = 2,
                    sets = listOf(
                        WorkoutTestFixtures.workingSet(prescribedSetId = "set-a"),
                        WorkoutTestFixtures.workingSet(prescribedSetId = "set-b"),
                    ),
                ),
            ),
        )
        val repo = FakeWorkoutLoggingRepository(plan = plan)
        val useCase = makeUseCase(repo)

        useCase(StartWorkoutCommand(ProgramRunId("run-1"), "occ-1"))
        val captured = assertNotNull(repo.lastInsert)
        val byExercise = captured.sets.groupBy { it.workoutExerciseLogId }
        for ((_, sets) in byExercise) {
            val sorted = sets.sortedBy { it.sequence }
            assertEquals(sets.map { it.sequence }.sorted(), (0 until sets.size).toList())
            // first 2 are warmups, last 2 are working
            assertEquals(SetRole.Warmup, sorted[0].role)
            assertEquals(SetRole.Warmup, sorted[1].role)
            assertEquals(SetRole.Working, sorted[2].role)
            assertEquals(SetRole.Working, sorted[3].role)
        }
    }

    @Test
    fun `success result echoes the rows that were sent to the repository`() = runTest {
        val repo = FakeWorkoutLoggingRepository()
        val useCase = makeUseCase(repo)

        val result = useCase(StartWorkoutCommand(ProgramRunId("run-1"), "occ-1"))
        if (result !is StartWorkoutResult.Success) fail("expected Success, got $result")
        val captured = assertNotNull(repo.lastInsert)
        assertEquals(captured.session, result.session)
        assertEquals(captured.exercises, result.exercises)
        assertEquals(captured.sets, result.sets)
        assertEquals(captured.snapshots, result.snapshots)
        assertEquals(captured.startMutation, result.startMutation)
    }

    private fun makeUseCase(repo: FakeWorkoutLoggingRepository) = StartWorkoutFromOccurrence(
        repository = repo,
        timeSource = WorkoutTestFixtures.fixedTimeSource(),
        idGenerator = WorkoutTestFixtures.sequentialIdGenerator(),
        deviceIdProvider = WorkoutTestFixtures.fixedDeviceIdProvider(),
    )
}
