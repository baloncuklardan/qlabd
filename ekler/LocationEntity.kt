package com.abdullahql.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_queue")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val networkType: String,
    val synced: Boolean = false
)
