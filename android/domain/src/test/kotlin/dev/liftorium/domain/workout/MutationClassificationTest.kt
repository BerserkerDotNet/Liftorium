package dev.liftorium.domain.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * UT-DUR-001 — mutation classification.
 *
 * Asserts the durability contract from
 * `docs/architecture.md` "Durability contract":
 *  * every user-visible mutation (i.e. all of [MutationType] except
 *    the session-only [MutationType.UndoSet]) writes a [LocalMutation]
 *    audit row;
 *  * [MutationType.UndoSet] is session-only and MUST NOT produce a
 *    durable audit row.
 *
 * The classifier is the SINGLE source of truth used by
 * `WorkoutLoggingService` to decide whether to attach an audit row.
 * Adding a new mutation type without classifying it here will fail
 * the exhaustiveness assertion below.
 */
class MutationClassificationTest {

    @Test
    fun `every mutation type is classified by isDurableUserVisible`() {
        val classified = MutationType.entries.map {
            it to MutationType.isDurableUserVisible(it)
        }
        assertEquals(
            MutationType.entries.size,
            classified.size,
            "isDurableUserVisible must be total over MutationType",
        )
    }

    @Test
    fun `only UndoSet is session-only undo`() {
        for (type in MutationType.entries) {
            val expected = type == MutationType.UndoSet
            assertEquals(
                expected,
                MutationType.isSessionVisibleUndo(type),
                "isSessionVisibleUndo($type) expected $expected",
            )
        }
    }

    @Test
    fun `durable user-visible set matches docs durability contract`() {
        val durable = MutationType.entries.filter { MutationType.isDurableUserVisible(it) }.toSet()
        val expected = setOf(
            MutationType.StartWorkout,
            MutationType.CompleteSet,
            MutationType.EditSet,
            MutationType.SkipSet,
            MutationType.SkipExercise,
            MutationType.AddExtraSet,
            MutationType.UpdateNote,
            MutationType.LogRpeRir,
            MutationType.CompleteWorkout,
            MutationType.AbandonWorkout,
        )
        assertEquals(expected, durable)
    }

    @Test
    fun `durable and session-visible-undo classifications are disjoint and complete`() {
        for (type in MutationType.entries) {
            val durable = MutationType.isDurableUserVisible(type)
            val undo = MutationType.isSessionVisibleUndo(type)
            assertFalse(durable && undo, "$type is both durable and session-only undo")
            assertTrue(durable || undo, "$type is neither classified")
        }
    }
}
