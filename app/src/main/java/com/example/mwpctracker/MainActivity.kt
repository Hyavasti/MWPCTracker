@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)
package com.example.mwpctracker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.mwpctracker.ui.theme.MWPCTrackerTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Driver(
    @SerialName("driver_id") val driverId: String? = null,
    @SerialName("driver_ref") val driverRef: String? = null,
    @SerialName("status") val status: String? = null
)

@Serializable
data class UserRecord(
    @SerialName("email") val email: String? = null,
    @SerialName("uid") val uid: String? = null,
    @SerialName("role") val role: String? = null
)

enum class AuthScreen {
    HOME,
    SIGN_IN,
    DASHBOARD,
    DELIVERIES,
    TRACKER,
    REPORTS,
    SUPPORT,
    ACCOUNT,
    INCIDENT_REPORT,
    DRIVER_GUIDELINES
}

class MainActivity : FragmentActivity() {

    private val trackerViewModel: TrackerViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedPreferences = getSharedPreferences("mwpc_driver_prefs", Context.MODE_PRIVATE)

        // Check if an existing driver profile is registered to this device
        val boundDriverUid = sharedPreferences.getString("bound_driver_uid", null)

        // Route directly to the MPIN quick-unlock screen if a bound driver exists
        val initialStartScreen = if (!boundDriverUid.isNullOrBlank()) {
            AuthScreen.SIGN_IN
        } else {
            AuthScreen.HOME
        }

        setContent {
            MWPCTrackerTheme {
                val locationPermissionState = rememberPermissionState(
                    permission = Manifest.permission.ACCESS_FINE_LOCATION
                )

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF7F8FC)
                ) {
                    var currentScreen by remember { mutableStateOf(initialStartScreen) }
                    var registeredEmail by remember {
                        mutableStateOf(sharedPreferences.getString("saved_email", "") ?: "")
                    }
                    var currentDriverId by remember { mutableStateOf(boundDriverUid ?: "") }
                    var incidentReportReturnScreen by remember { mutableStateOf(AuthScreen.SUPPORT) }
                    var selectedDelivery by remember { mutableStateOf<Delivery?>(null) }

                    // Automatic location tracking activation once authorized and driver verified
                    LaunchedEffect(locationPermissionState.status.isGranted, currentDriverId) {
                        if (locationPermissionState.status.isGranted && currentDriverId.isNotBlank()) {
                            trackerViewModel.startTracking(applicationContext, currentDriverId)
                        }
                    }

                    when (currentScreen) {
                        AuthScreen.HOME -> {
                            HomeScreen(
                                onNavigateToSignIn = { currentScreen = AuthScreen.SIGN_IN },
                                onNavigateToCreateAccount = { currentScreen = AuthScreen.SIGN_IN }
                            )
                        }

                        AuthScreen.SIGN_IN -> {
                            SignInScreen(
                                initialEmail = registeredEmail,
                                onSignInSuccess = { email ->
                                    registeredEmail = email

                                    lifecycleScope.launch(Dispatchers.IO) {
                                        try {
                                            val userRecord = SupabaseInstance.client
                                                .from("User")
                                                .select { filter { eq("email", email) } }
                                                .decodeSingleOrNull<UserRecord>()

                                            withContext(Dispatchers.Main) {
                                                if (userRecord?.uid != null) {
                                                    currentDriverId = userRecord.uid
                                                    sharedPreferences.edit()
                                                        .putString("bound_driver_uid", userRecord.uid)
                                                        .apply()
                                                    currentScreen = AuthScreen.DASHBOARD
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("MWPCTracker", "Error fetching user profile: ${e.message}", e)
                                        }
                                    }
                                }
                            )
                        }

                        AuthScreen.DASHBOARD -> {
                            DashboardScreen(
                                currentDriverId = currentDriverId,
                                onStartClick = { delivery ->
                                    selectedDelivery = delivery
                                    currentScreen = AuthScreen.DELIVERIES
                                },
                                onTabSelected = { tabName ->
                                    when (tabName) {
                                        "Dashboard" -> currentScreen = AuthScreen.DASHBOARD
                                        "Tracker" -> currentScreen = AuthScreen.TRACKER
                                        "Reports" -> currentScreen = AuthScreen.REPORTS
                                        "Support" -> currentScreen = AuthScreen.SUPPORT
                                        "Account" -> currentScreen = AuthScreen.ACCOUNT
                                    }
                                }
                            )
                        }

                        AuthScreen.DELIVERIES -> {
                            DeliveriesScreen(
                                delivery = selectedDelivery ?: Delivery(
                                    deliveryRef = "Default Delivery",
                                    pickupAddress = "Warehouse / Hub",
                                    destinationAddress = "Las Piñas"
                                ),
                                onBackClick = { currentScreen = AuthScreen.DASHBOARD },
                                onViewOnMapClick = { targetAddress ->
                                    val encodedAddress = Uri.encode(targetAddress)
                                    val gmmIntentUri = Uri.parse("google.navigation:q=$encodedAddress&mode=d")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                        setPackage("com.google.android.apps.maps")
                                    }
                                    if (mapIntent.resolveActivity(packageManager) != null) {
                                        startActivity(mapIntent)
                                    } else {
                                        val webIntent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$encodedAddress")
                                        )
                                        startActivity(webIntent)
                                    }
                                },
                                onMessageClick = {},
                                onCallClick = {
                                    val phoneIntent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:09123456789")
                                    }
                                    startActivity(phoneIntent)
                                }
                            )
                        }

                        AuthScreen.TRACKER -> {
                            TrackerScreen(
                                viewModel = trackerViewModel,
                                onTabSelected = { tabName ->
                                    when (tabName) {
                                        "Dashboard" -> currentScreen = AuthScreen.DASHBOARD
                                        "Tracker" -> currentScreen = AuthScreen.TRACKER
                                        "Reports" -> currentScreen = AuthScreen.REPORTS
                                        "Support" -> currentScreen = AuthScreen.SUPPORT
                                        "Account" -> currentScreen = AuthScreen.ACCOUNT
                                    }
                                }
                            )
                        }

                        AuthScreen.REPORTS -> {
                            ReportsScreen(
                                currentDriverUid = currentDriverId,
                                onTabSelected = { tabName ->
                                    when (tabName) {
                                        "Dashboard" -> currentScreen = AuthScreen.DASHBOARD
                                        "Tracker" -> currentScreen = AuthScreen.TRACKER
                                        "Reports" -> currentScreen = AuthScreen.REPORTS
                                        "Support" -> currentScreen = AuthScreen.SUPPORT
                                        "Account" -> currentScreen = AuthScreen.ACCOUNT
                                    }
                                }
                            )
                        }

                        AuthScreen.SUPPORT -> {
                            SupportScreen(
                                onTabSelected = { tabName ->
                                    when (tabName) {
                                        "Dashboard" -> currentScreen = AuthScreen.DASHBOARD
                                        "Tracker" -> currentScreen = AuthScreen.TRACKER
                                        "Reports" -> currentScreen = AuthScreen.REPORTS
                                        "Support" -> currentScreen = AuthScreen.SUPPORT
                                        "Account" -> currentScreen = AuthScreen.ACCOUNT
                                    }
                                },
                                onNavigateToIncidentReport = {
                                    incidentReportReturnScreen = AuthScreen.SUPPORT
                                    currentScreen = AuthScreen.INCIDENT_REPORT
                                },
                                onNavigateToGuidelines = {
                                    currentScreen = AuthScreen.DRIVER_GUIDELINES
                                }
                            )
                        }

                        AuthScreen.DRIVER_GUIDELINES -> {
                            DriverGuidelinesScreen(
                                onNavigateBack = { currentScreen = AuthScreen.SUPPORT }
                            )
                        }

                        AuthScreen.INCIDENT_REPORT -> {
                            IncidentReportScreen(
                                currentDriverId = currentDriverId,
                                onBackClick = { currentScreen = incidentReportReturnScreen }
                            )
                        }

                        AuthScreen.ACCOUNT -> {
                            AccountScreen(
                                onTabSelected = { tabName ->
                                    when (tabName) {
                                        "Dashboard" -> currentScreen = AuthScreen.DASHBOARD
                                        "Tracker" -> currentScreen = AuthScreen.TRACKER
                                        "Reports" -> currentScreen = AuthScreen.REPORTS
                                        "Support" -> currentScreen = AuthScreen.SUPPORT
                                        "Account" -> currentScreen = AuthScreen.ACCOUNT
                                    }
                                },
                                onSignOut = {
                                    currentDriverId = ""
                                    // Remove bound profile so app asks for master credentials on next open
                                    sharedPreferences.edit()
                                        .remove("bound_driver_uid")
                                        .remove("bound_driver_name")
                                        .apply()
                                    currentScreen = AuthScreen.SIGN_IN
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}