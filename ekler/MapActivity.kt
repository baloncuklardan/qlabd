package com.abdullahql.app.ui

import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.abdullahql.app.databinding.ActivityMapBinding
import com.abdullahql.app.remote.FirebaseLocationRepository
import com.google.firebase.firestore.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

class MapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapBinding
    private var myMarker: Marker? = null
    private var partnerMarker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Configuration.getInstance().load(
            this, PreferenceManager.getDefaultSharedPreferences(this)
        )
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(15.0)

        myMarker = Marker(binding.mapView).apply { title = "Ben" }
        partnerMarker = Marker(binding.mapView).apply { title = "Arkadaşım" }
        binding.mapView.overlays.add(myMarker)
        binding.mapView.overlays.add(partnerMarker)

        lifecycleScope.launch {
            val uid = FirebaseLocationRepository.ensureSignedIn()
            listenToLocation(uid, isSelf = true)

            val partnerUid = FirebaseLocationRepository.getPartnerUid(uid)
            if (partnerUid != null) {
                listenToLocation(partnerUid, isSelf = false)
            }
        }
    }

    private fun listenToLocation(uid: String, isSelf: Boolean) {
        Firebase.firestore.collection("locations").document(uid)
            .addSnapshotListener { snapshot, _ ->
                val lat = snapshot?.getDouble("latitude") ?: return@addSnapshotListener
                val lon = snapshot.getDouble("longitude") ?: return@addSnapshotListener
                val point = GeoPoint(lat, lon)

                if (isSelf) {
                    myMarker?.position = point
                    binding.mapView.controller.animateTo(point)
                } else {
                    partnerMarker?.position = point
                }
                binding.mapView.invalidate()
            }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }
}
