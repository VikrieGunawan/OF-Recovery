package com.orangefox.unofficial.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Room cache for the OrangeFox device catalog. The app works fully offline:
 * when the API bridge is unreachable the last cached catalog (or the bundled
 * offline seed) is shown instead.
 */
@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val codename: String,
    val name: String,
    val oem: String,
    val imageUrl: String?,
    val source: String, // "api" = live bridge, "offline" = bundled seed
    val updatedAt: Long
)

@Dao
interface DeviceDao {

    @Query("SELECT * FROM devices ORDER BY oem COLLATE NOCASE, name COLLATE NOCASE")
    fun observeAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE codename = :codename LIMIT 1")
    suspend fun byCodename(codename: String): DeviceEntity?

    @Query(
        "SELECT * FROM devices WHERE name LIKE '%' || :query || '%' " +
            "OR codename LIKE '%' || :query || '%' " +
            "OR oem LIKE '%' || :query || '%' " +
            "ORDER BY name COLLATE NOCASE"
    )
    suspend fun search(query: String): List<DeviceEntity>

    @Upsert
    suspend fun upsertAll(devices: List<DeviceEntity>)

    @Query("DELETE FROM devices")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM devices")
    suspend fun count(): Int
}

@Database(entities = [DeviceEntity::class], version = 1, exportSchema = false)
abstract class FoxDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
}
