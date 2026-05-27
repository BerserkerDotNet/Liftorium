package dev.liftorium.domain.resource

import dev.liftorium.core.KoverIgnore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Validation status enum from `schema/program-resource.schema.json`.
 *
 * `pending_runtime_references` is the android-program-runner runtime injection state:
 * the resource is structurally complete and the only remaining
 * blocking issues are first-week `reference.missing_first_week`
 * criticals for unsupplied training-max / one-rep-max references that
 * the Android runner injects at activation. Activation requires the
 * loader to assert the run-scoped reference values cover every such
 * unsupplied first-week reference.
 *
 * `blocked` and `rejected` cannot be loaded by ProgramResourceLoader.
 */
@KoverIgnore
@Serializable
public enum class ValidationStatus {
    @SerialName("activatable")
    Activatable,

    @SerialName("pending_runtime_references")
    PendingRuntimeReferences,

    @SerialName("blocked")
    Blocked,

    @SerialName("rejected")
    Rejected,
}

@KoverIgnore
@Serializable
public enum class ValidationSeverity {
    @SerialName("info") Info,
    @SerialName("warning") Warning,
    @SerialName("critical") Critical,
}

@KoverIgnore
@Serializable
public enum class ExerciseFamily {
    @SerialName("squat") Squat,
    @SerialName("bench") Bench,
    @SerialName("deadlift") Deadlift,
    @SerialName("overhead_press") OverheadPress,
    @SerialName("accessory") Accessory,
    @SerialName("other") Other,
}

@KoverIgnore
@Serializable
public enum class ExerciseEquipment {
    @SerialName("barbell") Barbell,
    @SerialName("dumbbell") Dumbbell,
    @SerialName("machine") Machine,
    @SerialName("bodyweight") Bodyweight,
    @SerialName("cable") Cable,
    @SerialName("other") Other,
}

@KoverIgnore
@Serializable
public enum class ReferenceType {
    @SerialName("one_rep_max") OneRepMax,
    @SerialName("bodyweight") Bodyweight,
}

@KoverIgnore
@Serializable
public enum class GroupKind {
    @SerialName("single") Single,
    @SerialName("superset") Superset,
    @SerialName("circuit") Circuit,
    @SerialName("paired") Paired,
}

@KoverIgnore
@Serializable
public enum class PrescriptionRole {
    @SerialName("working") Working,
    @SerialName("warmup") Warmup,
    @SerialName("top_set") TopSet,
    @SerialName("back_off") BackOff,
    @SerialName("amrap") Amrap,
    @SerialName("optional") Optional,
    @SerialName("extra") Extra,
}

@KoverIgnore
@Serializable
public enum class SetKind {
    @SerialName("working") Working,
    @SerialName("warmup") Warmup,
    @SerialName("top_set") TopSet,
    @SerialName("back_off") BackOff,
    @SerialName("amrap") Amrap,
    @SerialName("optional") Optional,
}

@KoverIgnore
@Serializable
public enum class NoteKind {
    @SerialName("tempo") Tempo,
    @SerialName("rest_pause") RestPause,
    @SerialName("myo_reps") MyoReps,
    @SerialName("free") Free,
}

@KoverIgnore
@Serializable
public enum class ImportSourceKind {
    @SerialName("synthetic") Synthetic,
    @SerialName("private_import") PrivateImport,
}

@KoverIgnore
@Serializable
public enum class AliasSource {
    @SerialName("import") Import,
    @SerialName("operator") Operator,
}
