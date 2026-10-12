package com.example.mwpctracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PrimaryPurple = Color(0xFF2E234B)
private val BackgroundGray = Color(0xFFF7F8FC)
private val TextDark = Color(0xFF2C243B)
private val SubtitleColor = Color(0xFF7A7585)

@Composable
fun SupportScreen(
    onTabSelected: (String) -> Unit = {},
    onNavigateToIncidentReport: () -> Unit = {},
    onNavigateToGuidelines: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimaryPurple)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SupportAgent,
                        contentDescription = "Support Icon",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Driver Support Center",
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
                    SupportBottomNavItem(
                        icon = Icons.Default.Dashboard,
                        label = "Dashboard",
                        isSelected = false,
                        onClick = { onTabSelected("Dashboard") }
                    )
                    SupportBottomNavItem(
                        icon = Icons.Default.LocationOn,
                        label = "Tracker",
                        isSelected = false,
                        onClick = { onTabSelected("Tracker") }
                    )
                    SupportBottomNavItem(
                        icon = Icons.Default.Assignment,
                        label = "Reports",
                        isSelected = false,
                        onClick = { onTabSelected("Reports") }
                    )
                    SupportBottomNavItem(
                        icon = Icons.Default.SupportAgent,
                        label = "Support",
                        isSelected = true,
                        onClick = { onTabSelected("Support") }
                    )
                    SupportBottomNavItem(
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quick Actions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    SupportMenuItem(
                        icon = Icons.Default.ReportProblem,
                        title = "Incident Report",
                        subtitle = "Damaged goods, wrong address, or delays",
                        onClick = onNavigateToIncidentReport
                    )
                    HorizontalDivider(color = BackgroundGray, thickness = 1.dp)
                    SupportMenuItem(
                        icon = Icons.Default.HelpOutline,
                        title = "Driver Guidelines & FAQs",
                        subtitle = "SOPs, COD rules, app troubleshooting & FAQs",
                        onClick = onNavigateToGuidelines
                    )
                }
            }

            //Operations Hours & Dispatch Assistance Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = PrimaryPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Dispatch Support Hours",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    }
                    Text(
                        text = "Monday to Saturday: 8:00 AM – 6:00 PM\nFor problems encountered within or outside business hours please use the Incident Report tab.",
                        fontSize = 13.sp,
                        color = SubtitleColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(PrimaryPurple.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PrimaryPurple,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = SubtitleColor
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = SubtitleColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SupportBottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
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