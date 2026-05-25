package dev.liftorium.app.ui.components

/**
 * Tone for [StatusBadge]. Maps onto the Material 3 color roles so dark
 * mode and accessibility palettes work without extra wiring. New status
 * meanings should add a new value here rather than reach for raw colors
 * at the call site.
 */
public enum class BadgeTone {
    Primary,
    Tertiary,
    Error,
    Neutral,
}
