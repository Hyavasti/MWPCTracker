package com.example.mwpctracker

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocationForegroundService : Service() {

    companion object {
        const val ACTION_LOCATION_UPDATE = "com.example.mwpctracker.LOCATION_UPDATE"
        const val ACTION_STOP_TRACKING = "com.example.mwpctracker.STOP_TRACKING"
        private const val CHANNEL_ID = "location_channel"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var dbRef: DatabaseReference
    private var userId: String? = null

    // Tracking references strictly by delivery_id
    @Volatile
    private var activeDeliveryRef: String = ""
    @Volatile
    private var activeDeliveryId: Long = -1L

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private var isLocationUpdatesActive = false

    override fun onCreate() {
        super.onCreate()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MWPCTracker:LocationWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        userId = FirebaseAuth.getInstance().uid

        dbRef = if (userId != null) {
            FirebaseDatabase.getInstance().getReference("driver_locations").child(userId!!)
        } else {
            FirebaseDatabase.getInstance().getReference("driver_locations").child("anonymous")
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    sendToFirebaseSupabaseAndBroadcast(location)
                }
            }
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_TRACKING) {
            Log.d("ForegroundService", "Stopping service via ACTION_STOP_TRACKING.")
            stopSelf()
            return START_NOT_STICKY
        }

        // Safely extract delivery_id regardless of whether it's Long, Int, or String
        val parsedDeliveryId: Long = when {
            intent?.hasExtra("delivery_id") == true -> {
                when (val raw = intent.extras?.get("delivery_id")) {
                    is Long -> raw
                    is Int -> raw.toLong()
                    is String -> raw.toLongOrNull() ?: -1L
                    else -> -1L
                }
            }
            else -> -1L
        }

        val parsedDeliveryRef = intent?.getStringExtra("delivery_ref") ?: ""

        if (parsedDeliveryId != -1L) {
            activeDeliveryId = parsedDeliveryId
            activeDeliveryRef = parsedDeliveryRef
            Log.d("ForegroundService", "Tracking target updated -> Delivery ID: $activeDeliveryId, Ref: '$activeDeliveryRef'")
        } else {
            Log.w("ForegroundService", "onStartCommand received intent with no valid delivery_id. Current ID remains: $activeDeliveryId")
        }

        // Update notification
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val displayText = when {
            activeDeliveryRef.isNotBlank() -> "Broadcasting for Delivery: $activeDeliveryRef"
            activeDeliveryId != -1L -> "Broadcasting for Delivery #$activeDeliveryId"
            else -> "Driver location tracking active..."
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mabuhay Tracker Active")
            .setContentText(displayText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)

        // Only register location updates once
        if (!isLocationUpdatesActive) {
            val highAccuracy = intent?.getBooleanExtra("highAccuracy", true) ?: true
            startLocationUpdates(highAccuracy)
            isLocationUpdatesActive = true
        }

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(highAccuracy: Boolean) {
        val priority = if (highAccuracy) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val locationRequest = LocationRequest.Builder(priority, 10000L).apply {
            setMinUpdateIntervalMillis(10000L)
            setMinUpdateDistanceMeters(0f)
            setGranularity(Granularity.GRANULARITY_FINE)
            setWaitForAccurateLocation(false)
        }.build()

        fusedLocationClient.removeLocationUpdates(locationCallback)
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun sendToFirebaseSupabaseAndBroadcast(location: Location) {
        val currentDeliveryId = activeDeliveryId
        val currentDeliveryRef = activeDeliveryRef

        val data = hashMapOf<String, Any>(
            "lat" to location.latitude,
            "lng" to location.longitude,
            "bearing" to location.bearing,
            "speed" to location.speed,
            "accuracy" to location.accuracy,
            "timestamp" to System.currentTimeMillis()
        )

        // 1. Firebase (Driver overview)
        dbRef.child("current").setValue(data)
        dbRef.child("history").push().setValue(data)

        // 2. Supabase Table Update (Matching primary key delivery_id)
        if (currentDeliveryId != -1L) {
            serviceScope.launch {
                try {
                    SupabaseInstance.client.from("Deliveries").update(
                        update = {
                            set("loc_lat", location.latitude)
                            set("loc_long", location.longitude)
                        }
                    ) {
                        filter {
                            eq("delivery_id", currentDeliveryId)
                        }
                    }
                    Log.d("ForegroundService", "Supabase updated successfully: delivery_id=$currentDeliveryId (Ref=$currentDeliveryRef) -> Lat: ${location.latitude}, Lng: ${location.longitude}")
                } catch (e: Exception) {
                    Log.e("ForegroundService", "Supabase update failed for delivery_id=$currentDeliveryId: ${e.message}", e)
                }
            }
        } else {
            Log.w("ForegroundService", "No active delivery selected. Skipping Supabase Deliveries update.")
        }

        // 3. Local Broadcast
        val intent = Intent(ACTION_LOCATION_UPDATE).apply {
            putExtra("lat", location.latitude)
            putExtra("lng", location.longitude)
            putExtra("speed", location.speed)
            putExtra("accuracy", location.accuracy)
        }
        sendBroadcast(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("ForegroundService", "App swiped away from recent apps. Terminating location service.")
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        serviceScope.cancel()

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e("ForegroundService", "Error releasing WakeLock: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps live location tracking active in the background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}