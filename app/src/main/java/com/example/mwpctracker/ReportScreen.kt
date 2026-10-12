@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.example.mwpctracker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IncidentReportViewItem(
    @SerialName("incident_ref") val incidentRef: String? = null,
    @SerialName("incident_type") val incidentType: String? = null,
    @SerialName("incident_title") val incidentTitle: String? = null,
    @SerialName("incident_description") val incidentDescription: String? = null,
    @SerialName("incident_severity") val incidentSeverity: String? = null,
    @SerialName("incident_location") val incidentLocation: String? = null,
    @SerialName("incident_status") val incidentStatus: String? = null,
    @SerialName("uid") val uid: String? = null
)

private val PrimaryPurple = Color(0xFF2E234B)
private val BackgroundGray = Color(0xFFF7F8FC)
private val CardBorderColor = Color(0xFFD6DBE5)
private val TextDark = Color(0xFF2C243B)
private val SubtitleColor = Color(0xFF7A7585)

// Helper model for badge colors
data class BadgeColors(val background: Color, val text: Color)

// Color Incident Severity
private fun getSeverityColors(severity: String?): BadgeColors {
    return when (severity?.trim()?.lowercase()) {
        "critical" -> BadgeColors(
            background = Color(0xFFFFE5E5),
            text = Color(0xFFD32F2F)
        )
        "high" -> BadgeColors(
            background = Color(0xFFFEF3C7),
            text = Color(0xFFD97706)
        )
        "medium" -> BadgeColors(
            background = Color(0xFFE0F2FE),
            text = Color(0xFF0284C7)
        )
        "low" -> BadgeColors(
            background = Color(0xFFDCFCE7),
            text = Color(0xFF16A34A)
        )
        else -> BadgeColors(
            background = Color(0xFFF1F5F9),
            text = Color(0xFF475569)
        )
    }
}

// Color Incident Status
private fun getStatusColors(status: String?): BadgeColors {
    return when (status?.trim()?.lowercase()) {
        "under review" -> BadgeColors(
            background = Color(0xFFDBEAFE),
            text = Color(0xFF2563EB)
        )
        "pending" -> BadgeColors(
            background = Color(0xFFF3E8FF),
            text = Color(0xFF7E22CE)
        )
        "resolved" -> BadgeColors(
            background = Color(0xFFDCFCE7),
            text = Color(0xFF16A34A)
        )
        "closed" -> BadgeColors(
            background = Color(0xFFF1F5F9),
            text = Color(0xFF475569)
        )
        else -> BadgeColors(
            background = Color(0xFFF1F5F9),
            text = Color(0xFF475569)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    currentDriverUid: String,
    onTabSelected: (String) -> Unit = {}
) {
    var reportsList by remember { mutableStateOf<List<IncidentReportViewItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(currentDriverUid) {
        try {
            isLoading = true
            val records = SupabaseInstance.client
                .from("Incident")
                .select {
                    if (currentDriverUid.isNotBlank()) {
                        filter { eq("uid", currentDriverUid) }
                    }
                }
                .decodeList<IncidentReportViewItem>()
            reportsList = records
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimaryPurple)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Assignment,
                        contentDescription = "Reports Icon",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = "Incident Reports",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
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
                    BottomNavItem(
                        icon = Icons.Default.Dashboard,
                        label = "Dashboard",
                        isSelected = false,
                        onClick = { onTabSelected("Dashboard") }
                    )
                    BottomNavItem(
                        icon = Icons.Default.LocationOn,
                        label = "Tracker",
                        isSelected = false,
                        onClick = { onTabSelected("Tracker") }
                    )
                    BottomNavItem(
                        icon = Icons.Default.Assignment,
                        label = "Reports",
                        isSelected = true,
                        onClick = { onTabSelected("Reports") }
                    )
                    BottomNavItem(
                        icon = Icons.Default.SupportAgent,
                        label = "Support",
                        isSelected = false,
                        onClick = { onTabSelected("Support") }
                    )
                    BottomNavItem(
                        icon = Icons.Default.Person,
                        label = "Account",
                        isSelected = false,
                        onClick = { onTabSelected("Account") }
                    )
                }
            }
        },
        containerColor = BackgroundGray
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Submitted Incident Reports",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryPurple
            )

            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = PrimaryPurple
                    )
                } else if (reportsList.isEmpty()) {
                    Text(
                        text = "No incident reports found.",
                        fontSize = 14.sp,
                        color = SubtitleColor,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(reportsList) { report ->
                            val severityColors = getSeverityColors(report.incidentSeverity)
                            val statusColors = getStatusColors(report.incidentStatus)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(12.dp)),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, CardBorderColor.copy(alpha = 0.6f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = report.incidentRef ?: "INC-REPORT",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryPurple
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = severityColors.background
                                        ) {
                                            Text(
                                                text = report.incidentSeverity ?: "Normal",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = severityColors.text,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = report.incidentTitle ?: "Untitled Incident",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextDark
                                    )

                                    Text(
                                        text = report.incidentDescription ?: "No description provided.",
                                        fontSize = 12.sp,
                                        color = SubtitleColor,
                                        maxLines = 3
                                    )

                                    if (!report.incidentLocation.isNullOrBlank()) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = Color(0xFFD32F2F),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = report.incidentLocation,
                                                fontSize = 11.sp,
                                                color = SubtitleColor
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = CardBorderColor.copy(alpha = 0.4f), thickness = 0.5.dp)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Type: ${report.incidentType ?: "General"}",
                                            fontSize = 11.sp,
                                            color = SubtitleColor
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = statusColors.background
                                        ) {
                                            Text(
                                                text = report.incidentStatus ?: "Pending",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = statusColors.text,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}