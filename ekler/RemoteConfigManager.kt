package com.abdullahql.app.remote

import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.tasks.await

object RemoteConfigManager {

    private val remoteConfig: FirebaseRemoteConfig by lazy {
        Firebase.remoteConfig.apply {
            setConfigSettingsAsync(
                remoteConfigSettings { minimumFetchIntervalInSeconds = 900 }
            )
            setDefaultsAsync(
                mapOf(
                    "distance_threshold_m" to 15L,
                    "stationary_interval_min" to 60L,
                    "live_mode" to false
                )
            )
        }
    }

    suspend fun refresh() {
        runCatching { remoteConfig.fetchAndActivate().await() }
    }

    fun distanceThresholdMeters(): Float =
        remoteConfig.getLong("distance_threshold_m").toFloat().takeIf { it > 0 } ?: 15f

    fun stationaryIntervalMinutes(): Long =
        remoteConfig.getLong("stationary_interval_min").takeIf { it > 0 } ?: 60L

    fun isLiveMode(): Boolean = remoteConfig.getBoolean("live_mode")
}
