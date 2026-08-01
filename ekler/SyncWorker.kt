package com.abdullahql.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.abdullahql.app.data.AppDatabase
import com.abdullahql.app.remote.FirebaseLocationRepository

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            val pending = db.locationDao().getUnsynced()
            if (pending.isEmpty()) return Result.success()

            val uid = FirebaseLocationRepository.ensureSignedIn()
            FirebaseLocationRepository.pushBatch(uid, pending)
            db.locationDao().markSynced(pending.map { it.id })

            val cutoff = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
            db.locationDao().clearOldSynced(cutoff)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
