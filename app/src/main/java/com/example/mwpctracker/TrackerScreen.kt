package com.example.mwpctracker

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

// Color Theme matching Mabuhay Padala Center branding
private val PrimaryPurple = Color(0xFF2E234B)
private val BackgroundGray = Color(0xFFF7F8FC)
private val TextDark = Color(0xFF2C243B)
private val SubtitleColor = Color(0xFF7A7585)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun TrackerScreen(
    sessionTitle: String = "Active Tracking Session",
    onBackClick: () -> Unit = {},
    onTabSelected: (String) -> Unit = {},
    viewModel: TrackerViewModel = viewModel()
) {
    BackHandler {
        onBackClick()
    }
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("Tracker") }

    val locationPermission = android.Manifest.permission.ACCESS_FINE_LOCATION
    val fineLocationPermissionState = rememberPermissionState(permission = locationPermission)

    Scaffold(
        topBar = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PrimaryPurple)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(onClick = onBackClick, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Tracker Icon",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = "Mabuhay Tracker",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = sessionTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            text = if (fineLocationPermissionState.status.isGranted) "Broadcasting Live GPS Data..." else "Location permission required",
                            fontSize = 13.sp,
                            color = SubtitleColor
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackerBottomNavItem(
                        icon = Icons.Default.Home,
                        label = "Dashboard",
                        isSelected = selectedTab == "Dashboard",
                        onClick = {
                            selectedTab = "Dashboard"
                            onTabSelected("Dashboard")
                        }
                    )
                    TrackerBottomNavItem(
                        icon = Icons.Default.LocationOn,
                        label = "Tracker",
                        isSelected = selectedTab == "Tracker",
                        onClick = {
                            selectedTab = "Tracker"
                            onTabSelected("Tracker")
                        }
                    )
                    TrackerBottomNavItem(
                        icon = Icons.Default.Assignment,
                        label = "Reports",
                        isSelected = selectedTab == "Reports",
                        onClick = {
                            selectedTab = "Reports"
                            onTabSelected("Reports")
                        }
                    )
                    TrackerBottomNavItem(
                        icon = Icons.Default.SupportAgent,
                        label = "Support",
                        isSelected = selectedTab == "Support",
                        onClick = {
                            selectedTab = "Support"
                            onTabSelected("Support")
                        }
                    )
                    TrackerBottomNavItem(
                        icon = Icons.Default.Person,
                        label = "Account",
                        isSelected = selectedTab == "Account",
                        onClick = {
                            selectedTab = "Account"
                            onTabSelected("Account")
                        }
                    )
                }
            }
        },
        containerColor = BackgroundGray
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (fineLocationPermissionState.status.isGranted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(PrimaryPurple.copy(alpha = 0.1f), RoundedCornerShape(36.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = PrimaryPurple,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Text(
                            text = "Live GPS Active",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BackgroundGray, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Latitude:", color = SubtitleColor, fontSize = 14.sp)
                                Text("${viewModel.currentLat}", color = TextDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Longitude:", color = SubtitleColor, fontSize = 14.sp)
                                Text("${viewModel.currentLng}", color = TextDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }

                        Text(
                            text = "Tracking continues automatically in the background across all app screens.",
                            fontSize = 12.sp,
                            color = SubtitleColor,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                val uri = "https://www.google.com/maps/search/?api=1&query=${viewModel.currentLat},${viewModel.currentLng}".toUri()
                                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                    setPackage("com.google.android.apps.maps")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val webIntent = Intent(Intent.ACTION_VIEW, uri)
                                    context.startActivity(webIntent)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Map,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Open Live Location in Google Maps",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOff,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Location Permission Required",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Mabuhay Tracker needs access to your device location to broadcast live telemetry and maintain tracking sessions.",
                        fontSize = 14.sp,
                        color = SubtitleColor,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { fineLocationPermissionState.launchPermissionRequest() },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                    ) {
                        Text(
                            text = "Allow Location Access",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackerBottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) PrimaryPurple else SubtitleColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) PrimaryPurple else SubtitleColor
        )
    }
}