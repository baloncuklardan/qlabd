package com.abdullahql.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.abdullahql.app.AbdullahQLApp
import com.abdullahql.app.R
import com.abdullahql.app.data.AppDatabase
import com.abdullahql.app.data.LocationEntity
import com.abdullahql.app.remote.FirebaseLocationRepository
import com.abdullahql.app.remote.RemoteConfigManager
import com.abdullahql.app.ui.MainActivity
import com.abdullahql.app.util.TelemetryUtil
import com.abdullahql.app.worker.SyncWorker
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class LocationForegroundService : Service() {

    companion object {
        const val NOTIF_ID = 4201
        const val PREFS = "abdullahql_prefs"
        const val KEY_SHARING_ENABLED = "sharing_enabled"

        fun start(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationForegroundService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedClient: FusedLocationProviderClient
    private var lastLocation: Location? = null
    private var lastSentAt: Long = 0
    private lateinit var db: AppDatabase

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            handleNewLocation(loc)
        }
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getInstance(this)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHARING_ENABLED, true).apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        serviceScope.launch {
            RemoteConfigManager.refresh()
            requestLocationUpdates()
        }
        schedulePeriodicSync()
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, AbdullahQLApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()
    }

    private fun requestLocationUpdates() {
        val distanceThreshold = RemoteConfigManager.distanceThresholdMeters()
        val liveMode = RemoteConfigManager.isLiveMode()

        val intervalMs = if (liveMode) 10_000L else 60_000L

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateDistanceMeters(if (liveMode) 0f else distanceThreshold)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            // İzin verilmemiş; servis izinsiz çalışamaz, onboarding ekranına yönlendirilmeli.
        }
    }

    private fun handleNewLocation(loc: Location) {
        val now = System.currentTimeMillis()
        val prev = lastLocation
        val stationaryIntervalMs = RemoteConfigManager.stationaryIntervalMinutes() * 60_000L
        val distanceThreshold = RemoteConfigManager.distanceThresholdMeters()

        val movedEnough = prev == null || prev.distanceTo(loc) >= distanceThreshold
        val stationaryDue = now - lastSentAt >= stationaryIntervalMs

        if (!movedEnough && !stationaryDue) return

        lastLocation = loc
        lastSentAt = now

        val entity = LocationEntity(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracy = loc.accuracy,
            timestamp = now,
            batteryPercent = TelemetryUtil.batteryPercent(this),
            isCharging = TelemetryUtil.isCharging(this),
            networkType = TelemetryUtil.networkType(this)
        )

        serviceScope.launch {
            val id = db.locationDao().insert(entity)
            trySendWithBackoff(entity.copy(id = id))
        }
    }

    /** Anlık gönderim dener; başarısız olursa kademeli aralıklarla (10sn->30sn->2dk->5dk) tekrar dener,
     *  nihayetinde WorkManager'a bırakır (kayıt zaten Room'da, veri kaybolmaz). */
    private suspend fun trySendWithBackoff(entity: LocationEntity) {
        val delays = longArrayOf(0, 10_000, 30_000, 120_000, 300_000)
        val uid = runCatching { FirebaseLocationRepository.ensureSignedIn() }.getOrNull() ?: return

        for (delayMs in delays) {
            if (delayMs > 0) delay(delayMs)
            if (!TelemetryUtil.isConnected(this)) continue
            val success = runCatching {
                FirebaseLocationRepository.pushLatestLocation(uid, entity)
            }.isSuccess
            if (success) {
                db.locationDao().markSynced(listOf(entity.id))
                return
            }
        }
        // Tüm denemeler başarısız oldu; WorkManager bağlantı geldiğinde toplu gönderecek.
    }

    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "abdullahql_sync", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedClient.removeLocationUpdates(locationCallback)
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHARING_ENABLED, false).apply()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
