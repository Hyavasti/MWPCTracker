package com.example.mwpctracker

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.database.FirebaseDatabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TrackerViewModel : ViewModel() {

    var currentLat by mutableDoubleStateOf(14.4343)
        private set
    var currentLng by mutableDoubleStateOf(120.9850)
        private set

    private var isTrackingStarted = false
    private var activeDeliveryRef: String? = null
    private var trackingJob: Job? = null


     //Updates the delivery reference when an order is selected

    fun setActiveDeliveryRef(deliveryRef: String?) {
        activeDeliveryRef = deliveryRef
        Log.d("TrackerViewModel", "Active delivery ref updated to: $deliveryRef")
    }

    @SuppressLint("MissingPermission")
    fun startTracking(context: Context, driverId: String, deliveryRef: String? = null) {
        if (isTrackingStarted && driverId.isNotBlank() && driverId != "anonymous_driver") {
            // Update active order reference if tracking is already active
            if (deliveryRef != null) {
                activeDeliveryRef = deliveryRef
            }
            return
        }

        isTrackingStarted = true
        activeDeliveryRef = deliveryRef

        val databaseUrl = "https://mpc-bacoor-default-rtdb.asia-southeast1.firebasedatabase.app"
        val databaseRef = FirebaseDatabase.getInstance(databaseUrl).reference

        val validDriverId = if (driverId.isNotBlank()) driverId else "anonymous_driver"
        val sessionRef = databaseRef.child("driver_sessions").child(validDriverId).push()
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        trackingJob?.cancel()
        trackingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isTrackingStarted) {
                try {
                    // Suspend cleanly until the GPS location is returned
                    val location: Location? = fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        null
                    ).await()

                    if (location != null) {
                        val lat = location.latitude
                        val lng = location.longitude

                        // Update Compose state safely on the Main dispatcher
                        withContext(Dispatchers.Main) {
                            currentLat = lat
                            currentLng = lng
                        }

                        val timestamp = System.currentTimeMillis()
                        val locationPayload = mapOf(
                            "driver_uid" to validDriverId,
                            "lat" to lat,
                            "lng" to lng,
                            "timestamp" to timestamp
                        )

                        // Firebase updates tracks driver's live location upon sign-in
                        sessionRef.child("current_location").setValue(locationPayload).await()
                        sessionRef.child("location_history").push().setValue(locationPayload).await()
                        Log.d("TrackerDebug", "Firebase updated for driver $validDriverId: Lat $lat, Lng $lng")

                        // Supabase update (ONLY if a delivery is currently assigned and active)
                        val refToUpdate = activeDeliveryRef
                        if (!refToUpdate.isNullOrBlank()) {
                            try {
                                SupabaseInstance.client.from("Deliveries").update(
                                    update = {
                                        set("loc_lat", lat)
                                        set("loc_long", lng)
                                    }
                                ) {
                                    filter {
                                        eq("delivery_ref", refToUpdate)
                                    }
                                }
                                Log.d("SupabaseUpdate", "Supabase successfully updated: $refToUpdate (Lat: $lat, Lng: $lng)")
                            } catch (e: Exception) {
                                Log.e("SupabaseUpdate", "Supabase update query failed: ${e.message}", e)
                            }
                        } else {
                            Log.d("TrackerDebug", "No active delivery selected. Skipping Supabase Deliveries update.")
                        }

                    } else {
                        Log.w("TrackerDebug", "GPS returned null location.")
                    }
                } catch (e: Exception) {
                    Log.e("TrackerDebug", "Tracking cycle failed: ${e.message}", e)
                }

                delay(10_000L)
            }
        }
    }

    fun stopTracking() {
        isTrackingStarted = false
        trackingJob?.cancel()
        trackingJob = null
        activeDeliveryRef = null
        Log.d("TrackerViewModel", "Driver tracking stopped.")
    }

    override fun onCleared() {
        super.onCleared()
        stopTracking()
    }
}