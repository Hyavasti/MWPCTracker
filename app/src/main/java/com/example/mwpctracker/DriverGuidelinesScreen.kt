package com.example.mwpctracker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PrimaryPurple = Color(0xFF2E234B)
private val BackgroundGray = Color(0xFFF7F8FC)
private val TextDark = Color(0xFF2C243B)
private val SubtitleColor = Color(0xFF7A7585)

data class GuidelineItem(
    val question: String,
    val answer: String,
    val category: String = "General"
)

private val sampleGuidelines = listOf(
    GuidelineItem(
        question = "How do I view my assigned deliveries?",
        answer = "Open the Dashboard tab to view all assigned deliveries, whether standard parcels, lipat-bahay, or commercial cargo."
    ),
    GuidelineItem(
        question = "How do I verify the items loaded into my vehicle?",
        answer = "Select an assigned delivery and tap START to open the active delivery details and cargo checklist. Check each item off as you load it into your vehicle, or tap Check All once everything is loaded."
    ),
    GuidelineItem(
        question = "Where can I see special customer instructions or payment details?",
        answer = "Scroll down on the active delivery screen to view the customer’s name, phone number, destination, special handling instructions, payment method (e.g., Bank Transfer or COD), and payment status."
    ),
    GuidelineItem(
        question = "How does GPS location tracking work on the app?",
        answer = "Once an active trip starts, your app automatically broadcasts live GPS tracking data continuously, even when switching screens or minimizing the app. Ensure high-accuracy location permissions are always enabled on your phone for smooth tracking."
    ),
    GuidelineItem(
        question = "Can I open my route in external navigation apps?",
        answer = "Yes. Tap the Open in Maps button on the Active Delivery screen or Open Live Location in Google Maps on the Tracker tab to start turn-by-turn navigation."
    ),
    GuidelineItem(
        question = "How do I collect Proof of Delivery upon arrival?",
        answer = "At the drop-off location, go to the Verification & Proof section of the Active Delivery screen. Capture the required photos of the delivered items and collect the recipient's e-signature directly on your phone screen before marking the job completed."
    ),
    GuidelineItem(
        question = "What should I do if an accident, delay, or problem occurs during transit?",
        answer = "Go to the Support tab and select Incident Report. Fill out the form by selecting the Incident Type (e.g., damaged goods, customer unavailable, or delivery delay), setting the severity level, entering your location or landmark, and attaching clear photos of what happened. Admin will be notified immediately."
    )
)

@Composable
fun DriverGuidelinesScreen(
    onNavigateBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var expandedIndices by remember { mutableStateOf(setOf<Int>()) }

    val filteredGuidelines = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            sampleGuidelines
        } else {
            sampleGuidelines.filter {
                it.question.contains(searchQuery, ignoreCase = true) ||
                        it.answer.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimaryPurple)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Driver Guidelines & FAQs",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        },
        containerColor = BackgroundGray
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search question or keyword...", color = SubtitleColor, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = SubtitleColor
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = PrimaryPurple,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Accordion Items
            itemsIndexed(filteredGuidelines) { index, item ->
                val isExpanded = expandedIndices.contains(index)
                val iconRotation by animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    label = "chevron_anim"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedIndices = if (isExpanded) {
                                    expandedIndices - index
                                } else {
                                    expandedIndices + index
                                }
                            }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(PrimaryPurple.copy(alpha = 0.08f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HelpOutline,
                                        contentDescription = null,
                                        tint = PrimaryPurple,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = item.question,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextDark,
                                    lineHeight = 18.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = SubtitleColor,
                                modifier = Modifier
                                    .size(22.dp)
                                    .rotate(iconRotation)
                            )
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = BackgroundGray, thickness = 1.dp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = item.answer,
                                    fontSize = 13.sp,
                                    color = SubtitleColor,
                                    lineHeight = 19.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}