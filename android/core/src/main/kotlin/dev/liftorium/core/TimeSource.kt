package dev.liftorium.core

import java.time.Clock
import java.time.Instant

/**
 * Time abstraction injected into domain/data services so unit tests can
 * supply deterministic instants and Compose surfaces never depend on
 * `System.currentTimeMillis()` directly.
 *
 * `Clock` here is the JDK `java.time.Clock`. `:core` stays a pure JVM module
 * (no Android, no Room, no Compose) so `:domain` can depend on it safely.
 */
public interface TimeSource {
    public fun now(): Instant

    public companion object {
        public fun system(): TimeSource = JvmTimeSource(Clock.systemUTC())

        public fun fixed(instant: Instant): TimeSource = JvmTimeSource(Clock.fixed(instant, java.time.ZoneOffset.UTC))
    }
}

internal class JvmTimeSource(private val clock: Clock) : TimeSource {
    override fun now(): Instant = clock.instant()
}
