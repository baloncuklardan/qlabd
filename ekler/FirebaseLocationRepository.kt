package com.abdullahql.app.remote

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.abdullahql.app.data.LocationEntity
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

object FirebaseLocationRepository {

    private val db by lazy { Firebase.firestore }

    suspend fun ensureSignedIn(): String {
        val current = Firebase.auth.currentUser
        if (current != null) return current.uid
        val result = Firebase.auth.signInAnonymously().await()
        return result.user!!.uid
    }

    /** 6 haneli tek kullanımlık eşleştirme kodu üretir ve Firestore'a yazar. */
    suspend fun generatePairingCode(myUid: String): String {
        val code = (100000..999999).random(Random(System.currentTimeMillis())).toString()
        db.collection("pairs").document(code)
            .set(mapOf("uid" to myUid, "createdAt" to System.currentTimeMillis()))
            .await()
        return code
    }

    /** Arkadaşın kodunu girerek karşılıklı eşleştirme oluşturur. */
    suspend fun pairWithCode(myUid: String, code: String): Boolean {
        val doc = db.collection("pairs").document(code).get().await()
        val partnerUid = doc.getString("uid") ?: return false
        if (partnerUid == myUid) return false

        db.collection("friendships").document(myUid)
            .set(mapOf("partnerUid" to partnerUid)).await()
        db.collection("friendships").document(partnerUid)
            .set(mapOf("partnerUid" to myUid)).await()
        return true
    }

    suspend fun getPartnerUid(myUid: String): String? {
        val doc = db.collection("friendships").document(myUid).get().await()
        return doc.getString("partnerUid")
    }

    /** Tek konum güncellemesini "en güncel konum" belgesi olarak yazar. */
    suspend fun pushLatestLocation(uid: String, loc: LocationEntity) {
        db.collection("locations").document(uid).set(
            mapOf(
                "latitude" to loc.latitude,
                "longitude" to loc.longitude,
                "accuracy" to loc.accuracy,
                "timestamp" to loc.timestamp,
                "batteryPercent" to loc.batteryPercent,
                "isCharging" to loc.isCharging,
                "networkType" to loc.networkType
            )
        ).await()
    }

    /** Room'da biriken kayıtları toplu olarak geçmiş koleksiyonuna yazar. */
    suspend fun pushBatch(uid: String, items: List<LocationEntity>) {
        val batch = db.batch()
        val historyCol = db.collection("locations_history").document(uid).collection("points")
        items.forEach { item ->
            val ref = historyCol.document(item.timestamp.toString())
            batch.set(
                ref,
                mapOf(
                    "latitude" to item.latitude,
                    "longitude" to item.longitude,
                    "accuracy" to item.accuracy,
                    "timestamp" to item.timestamp,
                    "batteryPercent" to item.batteryPercent,
                    "isCharging" to item.isCharging,
                    "networkType" to item.networkType
                )
            )
        }
        batch.commit().await()
        // Ayrıca en son noktayı "latest" olarak da güncelle
        items.maxByOrNull { it.timestamp }?.let { pushLatestLocation(uid, it) }
    }
}
