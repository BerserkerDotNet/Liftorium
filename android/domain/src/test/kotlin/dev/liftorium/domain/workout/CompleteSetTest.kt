package dev.liftorium.domain.workout

import dev.liftorium.domain.common.WeightUnit
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class CompleteSetTest {

    @Test
    fun `successfully forwards all conjunctive fields to the repository`() = runTest {
        val repo = FakeWorkoutLoggingRepository()
        val useCase = CompleteSet(
            repository = repo,
            timeSource = WorkoutTestFixtures.fixedTimeSource(),
            idGenerator = WorkoutTestFixtures.sequentialIdGenerator(),
            deviceIdProvider = WorkoutTestFixtures.fixedDeviceIdProvider(),
        )

        val outcome = useCase(
            CompleteSetCommand(
                actualSetId = ActualSetId("set-42"),
                actualLoad = 102.5,
                actualLoadUnit = WeightUnit.Kg,
                actualReps = 5,
                actualRpe = 8.5,
                actualRir = 2,
                notes = "felt strong",
            ),
        )

        assertEquals(CompleteSetOutcome.Success, outcome)
        val captured = assertNotNull(repo.lastComplete)
        assertEquals(ActualSetId("set-42"), captured.actualSetId)
        assertEquals(102.5, captured.actualLoad)
        assertEquals(WeightUnit.Kg, captured.actualLoadUnit)
        assertEquals(5, captured.actualReps)
        assertEquals(8.5, captured.actualRpe)
        assertEquals(2, captured.actualRir)
        assertEquals("felt strong", captured.notes)
        assertEquals(MutationType.CompleteSet, captured.mutation.type)
        assertEquals(ACTUAL_SET_ENTITY_TYPE, captured.mutation.entityType)
        assertEquals("set-42", captured.mutation.entityId)
    }

    @Test
    fun `load without unit fails at command construction`() {
        assertFailsWith<IllegalArgumentException> {
            CompleteSetCommand(
                actualSetId = ActualSetId("set-1"),
                actualLoad = 100.0,
                actualLoadUnit = null,
                actualReps = 5,
                actualRpe = null,
                actualRir = null,
                notes = null,
            )
        }
    }

    @Test
    fun `out of range rpe fails at command construction`() {
        assertFailsWith<IllegalArgumentException> {
            CompleteSetCommand(
                actualSetId = ActualSetId("set-1"),
                actualLoad = null,
                actualLoadUnit = null,
                actualReps = null,
                actualRpe = 10.5,
                actualRir = null,
                notes = null,
            )
        }
    }
}
