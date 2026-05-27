package dev.liftorium.data.workout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Read/write surface for the single-row `device_identity` table.
 *
 * The device identity is generated lazily on first read by
 * [RoomDeviceIdProvider]; this DAO just provides the persistence
 * primitives. Per ADR 2026-05-25 ("DeviceId is self-generated UUID"),
 * the value is opaque to consumers and never derived from
 * `Settings.Secure.ANDROID_ID`.
 */
@Dao
public abstract class DeviceIdentityDao {

    @Query("SELECT * FROM device_identity WHERE id = :id LIMIT 1")
    public abstract suspend fun find(id: Int = DeviceIdentityEntity.SINGLETON_ID): DeviceIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public abstract suspend fun insert(entity: DeviceIdentityEntity): Long
}
