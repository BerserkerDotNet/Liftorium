package dev.liftorium.app.ui

import androidx.compose.runtime.Immutable
import dev.liftorium.domain.common.WeightUnit
import dev.liftorium.domain.run.ProgramRunId
import dev.liftorium.domain.common.ProgramVersionId
import dev.liftorium.domain.run.WeekVariantGroupKey
import kotlinx.collections.immutable.ImmutableList

/**
 * Stateless UI state for the program library / activation flow. The
 * android-program-runner screens are state-down/events-up composables; no Android or
 * Compose types live here so the state is testable from a JVM unit
 * test without Robolectric / Paparazzi.
 */
@Immutable
public data class ProgramVersionRow(
    val programVersionId: ProgramVersionId,
    val displayName: String,
    val versionLabel: String,
    val authorAttribution: String?,
    val validationStatus: String,
)

@Immutable
public data class PendingReferenceRow(
    val referenceId: String,
    val displayLabel: String,
    val referenceType: String,
    val defaultUnit: WeightUnit,
)

@Immutable
public data class ProgramDetailUi(
    val programVersionId: ProgramVersionId,
    val displayName: String,
    val versionLabel: String,
    val authorAttribution: String?,
    val validationStatus: String,
    val blocks: ImmutableList<BlockUi>,
    val pendingReferences: ImmutableList<PendingReferenceRow>,
    val variantGroups: ImmutableList<VariantGroupUi>,
)

@Immutable
public data class BlockUi(
    val blockId: String,
    val displayName: String,
    val weeks: ImmutableList<WeekUi>,
)

@Immutable
public data class WeekUi(
    val weekId: String,
    val label: String,
    val sessionTitles: ImmutableList<String>,
)

@Immutable
public data class VariantGroupUi(
    val key: WeekVariantGroupKey,
    val baseLabel: String,
    val options: ImmutableList<VariantOptionUi>,
)

@Immutable
public data class VariantOptionUi(
    val weekId: String,
    val label: String,
)

@Immutable
public data class TodaySessionUi(
    val programRunId: ProgramRunId,
    val plannedOccurrenceId: String,
    val programDisplayName: String,
    val sessionTitle: String,
    val plannedEpochDay: Long,
    val items: ImmutableList<TodayItemUi>,
)

@Immutable
public data class TodayItemUi(
    val itemId: String,
    val exerciseName: String,
    val role: String,
    val setLines: ImmutableList<String>,
)
