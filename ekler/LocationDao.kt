package com.abdullahql.app.data

import androidx.room.*

@Dao
interface LocationDao {

    @Insert
    suspend fun insert(location: LocationEntity): Long

    @Query("SELECT * FROM location_queue WHERE synced = 0 ORDER BY timestamp ASC LIMIT 200")
    suspend fun getUnsynced(): List<LocationEntity>

    @Query("UPDATE location_queue SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM location_queue WHERE synced = 1 AND timestamp < :beforeTimestamp")
    suspend fun clearOldSynced(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM location_queue WHERE synced = 0")
    suspend fun unsyncedCount(): Int
}
