package dev.liftorium.app.ui.program

import dev.liftorium.app.ui.components.BadgeTone

/**
 * Maps a finalized `validationStatus` string from the loaded program
 * resource to a display label + [BadgeTone]. Keep this in one place so
 * Library, Detail, and any future stats surface render the same status
 * the same way.
 *
 * Unknown values fall through to the raw string with the neutral tone —
 * that surfaces new statuses to the user without crashing, while making
 * it obvious that the status was unrecognised.
 */
internal data class ValidationStatusBadge(
    val label: String,
    val tone: BadgeTone,
) {
    public companion object {
        public fun of(status: String): ValidationStatusBadge = when (status) {
            "activatable" -> ValidationStatusBadge("Activatable", BadgeTone.Primary)
            "pending_runtime_references" -> ValidationStatusBadge("Pending refs", BadgeTone.Tertiary)
            "blocked" -> ValidationStatusBadge("Blocked", BadgeTone.Error)
            "rejected" -> ValidationStatusBadge("Rejected", BadgeTone.Error)
            else -> ValidationStatusBadge(status, BadgeTone.Neutral)
        }
    }
}
