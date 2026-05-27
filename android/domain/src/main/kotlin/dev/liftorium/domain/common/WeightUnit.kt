package dev.liftorium.domain.common

import dev.liftorium.core.KoverIgnore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Unit-of-measure tag for weight values.
 *
 * Lives in `dev.liftorium.domain.common` because both program-resource
 * import metadata (`:domain.resource`) and runtime 1RM
 * injection (`:domain.run`) reference it. Keeping it in either feature
 * package would create a cycle between the two, which the
 * `DomainArchUnitTest.domain package slices are free of cycles` rule
 * actively forbids.
 */
@KoverIgnore
@Serializable
public enum class WeightUnit {
    @SerialName("lb") Lb,
    @SerialName("kg") Kg,
}
