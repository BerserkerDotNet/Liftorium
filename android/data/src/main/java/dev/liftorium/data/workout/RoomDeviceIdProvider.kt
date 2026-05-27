package dev.liftorium.data.workout

import dev.liftorium.core.TimeSource
import dev.liftorium.domain.common.DeviceId
import dev.liftorium.domain.common.DeviceIdProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Room-backed [DeviceIdProvider]. Lazily resolves the per-install
 * device UUID on first call, persists it to the single-row
 * `device_identity` table, and caches the value in memory for the
 * lifetime of the process.
 *
 * Per ADR 2026-05-25 ("DeviceId is self-generated UUID"), the value is
 * a fresh `UUID.randomUUID()` — never derived from
 * `Settings.Secure.ANDROID_ID` (privacy + reset-friendly).
 *
 * The [Mutex] serialises concurrent first-call races so we never
 * insert two rows. After a successful read or insert, the cached
 * value short-circuits all subsequent calls.
 */
public class RoomDeviceIdProvider(
    private val dao: DeviceIdentityDao,
    private val timeSource: TimeSource,
    private val uuidFactory: () -> String = { UUID.randomUUID().toString() },
) : DeviceIdProvider {

    private val mutex = Mutex()
    @Volatile
    private var cached: DeviceId? = null

    override suspend fun current(): DeviceId {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return@withLock it }
            val existing = dao.find()
            val resolved = if (existing != null) {
                DeviceId(existing.deviceId)
            } else {
                val newId = uuidFactory()
                dao.insert(
                    DeviceIdentityEntity(
                        id = DeviceIdentityEntity.SINGLETON_ID,
                        deviceId = newId,
                        createdAtEpochMillis = timeSource.now().toEpochMilli(),
                    ),
                )
                val confirmed = dao.find()?.deviceId ?: newId
                DeviceId(confirmed)
            }
            cached = resolved
            resolved
        }
    }
}
