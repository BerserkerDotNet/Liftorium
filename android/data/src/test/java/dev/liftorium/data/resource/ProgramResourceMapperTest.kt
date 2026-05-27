package dev.liftorium.data.resource

import dev.liftorium.domain.resource.ExerciseCatalogEntry
import dev.liftorium.domain.resource.ExerciseEquipment
import dev.liftorium.domain.resource.ExerciseFamily
import dev.liftorium.domain.resource.ExerciseGroup
import dev.liftorium.domain.resource.GroupKind
import dev.liftorium.domain.resource.ImportAudit
import dev.liftorium.domain.resource.ImportSourceKind
import dev.liftorium.domain.resource.Metadata
import dev.liftorium.domain.resource.PrescriptionItem
import dev.liftorium.domain.resource.PrescriptionRole
import dev.liftorium.domain.resource.PrescriptionTarget
import dev.liftorium.domain.resource.ProgramBlock
import dev.liftorium.domain.resource.ProgramResource
import dev.liftorium.domain.resource.ProgramStructure
import dev.liftorium.domain.resource.ProgramWeek
import dev.liftorium.domain.resource.ProgressionRule
import dev.liftorium.domain.resource.ReferenceType
import dev.liftorium.domain.resource.RequiredReference
import dev.liftorium.domain.resource.RoundingOverride
import dev.liftorium.domain.resource.SessionTemplate
import dev.liftorium.domain.resource.SetKind
import dev.liftorium.domain.resource.SetPrescription
import dev.liftorium.domain.resource.ValidationStatus
import dev.liftorium.domain.common.WeightUnit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure JVM unit tests for [ProgramResourceMapper] that exercise the
 * `?.let` null-handling branches and the 5-way `when` over
 * [PrescriptionTarget]. These complement the loader integration tests
 * (which only exercise the populated fixture shape) and ensure both
 * branches of every conditional in the mapper are covered.
 */
class ProgramResourceMapperTest {

    @Test
    fun `toBundle maps all five PrescriptionTarget kinds with nullable optionals populated`() {
        val resource = buildResource(
            programDefaults = RoundingOverride(roundingIncrement = 2.5, roundingUnit = WeightUnit.Kg),
            structureRounding = RoundingOverride(roundingIncrement = 5.0, roundingUnit = WeightUnit.Lb),
            blockRounding = RoundingOverride(roundingIncrement = 1.25, roundingUnit = WeightUnit.Kg),
            displayName = "Display",
            authorAttribution = "Author",
            catalogDefaultRoundingUnit = WeightUnit.Kg,
            requiredReferenceUnit = WeightUnit.Lb,
            ruleParameters = JsonObject(mapOf("knob" to JsonPrimitive(1))),
            percentRoundingUnit = WeightUnit.Kg,
            targets = listOf(
                PrescriptionTarget.ExactLoadReps(loadValue = 100.0, loadUnit = WeightUnit.Kg, reps = 5),
                PrescriptionTarget.RepRange(repMin = 5, repMax = 8),
                PrescriptionTarget.Percent(
                    referenceId = "orm-bench",
                    percent = 70.0,
                    reps = 5,
                    amrap = true,
                    roundingIncrement = 2.5,
                    roundingUnit = WeightUnit.Kg,
                ),
                PrescriptionTarget.Rpe(target = 8.0, rangeMin = 7.0, rangeMax = 9.0, cap = 9.5),
                PrescriptionTarget.Rir(target = 2, rangeMin = 1, rangeMax = 3, floor = 0),
            ),
        )

        val bundle = ProgramResourceMapper.toBundle(resource, loadedAtEpochMillis = 1_700_000_000L)

        assertEquals(5, bundle.targets.size)
        assertEquals(setOf("exact_load_reps", "rep_range", "percent", "rpe", "rir"), bundle.targets.map { it.kind }.toSet())
        val exact = bundle.targets.first { it.kind == "exact_load_reps" }
        assertEquals(100.0, exact.loadValue)
        assertEquals("kg", exact.loadUnit)
        assertEquals(5, exact.reps)
        val range = bundle.targets.first { it.kind == "rep_range" }
        assertEquals(5, range.repMin)
        assertEquals(8, range.repMax)
        val percent = bundle.targets.first { it.kind == "percent" }
        assertEquals("orm-bench", percent.referenceId)
        assertEquals(70.0, percent.percent)
        assertEquals(true, percent.amrap)
        assertEquals("kg", percent.roundingUnit)
        val rpe = bundle.targets.first { it.kind == "rpe" }
        assertEquals(8.0, rpe.rpeTarget)
        assertEquals(9.5, rpe.rpeCap)
        val rir = bundle.targets.first { it.kind == "rir" }
        assertEquals(2, rir.rirTarget)
        assertEquals(0, rir.rirFloor)

        assertNotNull(bundle.version.programDefaultsJson)
        assertNotNull(bundle.version.programStructureRoundingOverrideJson)
        assertEquals("Display", bundle.version.displayName)
        assertEquals("Author", bundle.version.authorAttribution)
        val block = bundle.blocks.first()
        assertNotNull(block.roundingOverrideJson)
        val catalog = bundle.catalogEntries.first()
        assertEquals("kg", catalog.defaultRoundingUnit)
        val ref = bundle.requiredReferences.first()
        assertEquals("lb", ref.unit)
        val rule = bundle.progressionRules.first()
        val parametersJson = assertNotNull(rule.parametersJson, "progressionRule.parametersJson must be non-null")
        assertTrue(parametersJson.contains("knob"))
    }

    @Test
    fun `toBundle maps with all nullable optionals null (covers null branches)`() {
        val resource = buildResource(
            programDefaults = null,
            structureRounding = null,
            blockRounding = null,
            displayName = null,
            authorAttribution = null,
            catalogDefaultRoundingUnit = null,
            requiredReferenceUnit = null,
            ruleParameters = null,
            percentRoundingUnit = null,
            targets = listOf(
                PrescriptionTarget.Percent(referenceId = "orm-bench", percent = 70.0),
            ),
        )

        val bundle = ProgramResourceMapper.toBundle(resource, loadedAtEpochMillis = 1_700_000_000L)

        assertNull(bundle.version.displayName)
        assertNull(bundle.version.authorAttribution)
        assertNull(bundle.version.programDefaultsJson)
        assertNull(bundle.version.programStructureRoundingOverrideJson)
        assertNull(bundle.blocks.first().roundingOverrideJson)
        assertNull(bundle.catalogEntries.first().defaultRoundingUnit)
        assertNull(bundle.requiredReferences.first().unit)
        assertNull(bundle.progressionRules.first().parametersJson)
        assertNull(bundle.targets.first().roundingUnit)
    }

    private fun buildResource(
        programDefaults: RoundingOverride?,
        structureRounding: RoundingOverride?,
        blockRounding: RoundingOverride?,
        displayName: String?,
        authorAttribution: String?,
        catalogDefaultRoundingUnit: WeightUnit?,
        requiredReferenceUnit: WeightUnit?,
        ruleParameters: JsonObject?,
        percentRoundingUnit: WeightUnit?,
        targets: List<PrescriptionTarget>,
    ): ProgramResource = ProgramResource(
        schemaVersion = 1,
        metadata = Metadata(
            programId = "p-test",
            programVersionId = "p-test@v1",
            versionLabel = "v1",
            displayName = displayName,
            authorAttribution = authorAttribution,
            contentHash = "0".repeat(64),
        ),
        validationStatus = ValidationStatus.Activatable,
        validationIssues = emptyList(),
        importAudit = ImportAudit(
            sourceHash = "0".repeat(64),
            sourceFilename = "x.xlsx",
            importedAtUtc = "2026-05-18T00:00:00Z",
            sourceKind = ImportSourceKind.Synthetic,
            schemaVersionUsed = 1,
        ),
        programDefaults = programDefaults,
        exerciseCatalog = listOf(
            ExerciseCatalogEntry(
                id = "bench",
                displayName = "Bench",
                family = ExerciseFamily.Bench,
                equipment = ExerciseEquipment.Barbell,
                defaultRoundingIncrement = catalogDefaultRoundingUnit?.let { 2.5 },
                defaultRoundingUnit = catalogDefaultRoundingUnit,
            ),
        ),
        requiredReferences = listOf(
            RequiredReference(
                id = "orm-bench",
                referenceType = ReferenceType.OneRepMax,
                exerciseId = "bench",
                firstRunnableWeekIndex = 1,
                supplied = false,
                value = null,
                unit = requiredReferenceUnit,
            ),
        ),
        progressionRules = listOf(
            ProgressionRule(
                id = "rule1",
                kind = "linear_weekly_increment",
                appliesToExerciseIds = listOf("bench"),
                parameters = ruleParameters,
            ),
        ),
        programStructure = ProgramStructure(
            roundingOverride = structureRounding,
            blocks = listOf(
                ProgramBlock(
                    id = "block1",
                    order = 1,
                    displayName = "B1",
                    roundingOverride = blockRounding,
                    weeks = listOf(
                        ProgramWeek(
                            id = "w1",
                            weekIndex = 1,
                            sessions = listOf(
                                SessionTemplate(
                                    id = "s1",
                                    sessionIndex = 1,
                                    groups = listOf(
                                        ExerciseGroup(
                                            id = "g1",
                                            order = 1,
                                            kind = GroupKind.Single,
                                            prescriptionItems = listOf(
                                                PrescriptionItem(
                                                    id = "i1",
                                                    order = 1,
                                                    prescribedExerciseId = "bench",
                                                    role = PrescriptionRole.Working,
                                                    setPrescriptions = listOf(
                                                        SetPrescription(
                                                            id = "set1",
                                                            order = 1,
                                                            setKind = SetKind.Working,
                                                            targets = targets,
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}
