@file:OptIn(
    kotlinx.serialization.InternalSerializationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
package com.example.mwpctracker

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

@Serializable
data class Delivery(
    @SerialName("delivery_id") val deliveryId: Long = 0L,
    @SerialName("delivery_ref") val deliveryRef: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    var deliveredAt: String? = null,
    @SerialName("driver_name") val driverName: String? = null,
    var destinationAddress: String = "Specified Destination",
    @SerialName("service_type") val serviceType: String = "Standard Parcel",
    @SerialName("route_type") val routeType: String = "Door-to-Door",
    var pickupAddress: String = "Warehouse / Hub",
    @SerialName("current_task") val currentTask: String = "PICKUP",
    @SerialName("delivery_status") val deliveryStatus: String = "Pending"
)

@Serializable
data class DeliveryViewRecord(
    @SerialName("delivery_id") val deliveryId: Long = 0L,
    @SerialName("delivery_ref") val deliveryRef: String? = null,
    @SerialName("status") val deliveryStatus: String? = null,
    @SerialName("driver_name") val driverName: String? = null,
    @SerialName("tracking_ids") val trackingIds: kotlinx.serialization.json.JsonElement? = null
)

@Serializable
data class BookingViewRecord(
    @SerialName("booking_id") val bookingId: Long? = null,
    @SerialName("tracking_id") val trackingId: String? = null,
    @SerialName("service_type") val serviceType: String? = null,
    @SerialName("Origin") val origin: String? = null,
    @SerialName("Destination") val destination: String? = null
)

@Serializable
data class StatusLogRow(
    @SerialName("id") val id: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("booking_id") val bookingId: Long? = null
)

private val PrimaryPurple = Color(0xFF2E234B)
private val Purple = Color(0xFF3B2E7E)
val ViewPurple = Color(0xFF1E1035)
private val GoldButton = Color(0xFFD4A373)
private val BackgroundGray = Color(0xFFF7F8FC)
private val CardBorderColor = Color(0xFFD6DBE5)
private val TextDark = Color(0xFF2C243B)
private val SubtitleColor = Color(0xFF7A7585)

private fun isWithinLast5Minutes(timestampStr: String?): Boolean {
    if (timestampStr.isNullOrBlank()) return false
    return try {
        val cleanTimestamp = timestampStr
            .replace(" ", "T")
            .substringBefore("+")
            .removeSuffix("Z")

        val normalizedTimestamp = if (cleanTimestamp.contains(".")) {
            val mainPart = cleanTimestamp.substringBefore(".")
            val fraction = cleanTimestamp.substringAfter(".").take(3).padEnd(3, '0')
            "${mainPart}.${fraction}"
        } else {
            "${cleanTimestamp}.000"
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val parsedDate = sdf.parse(normalizedTimestamp)
        if (parsedDate != null) {
            val diffMs = abs(System.currentTimeMillis() - parsedDate.time)
            val fiveMinutesMs = 5L * 60 * 1000L
            diffMs <= fiveMinutesMs
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }
}

suspend fun fetchDeliveries(currentDriverName: String): List<Delivery> {
    return try {
        Log.d("SupabaseDebug", "Fetching deliveries for driver: '$currentDriverName'")

        // FIFO: Ascending by delivery_id (safe across views and represents sequence of creation)
        val deliveryRecords = SupabaseInstance.client
            .from("getAllDeliveries")
            .select {
                filter {
                    eq("driver_name", currentDriverName)
                }
                order("delivery_id", Order.ASCENDING)
            }
            .decodeList<DeliveryViewRecord>()

        if (deliveryRecords.isEmpty()) return emptyList()

        val allTrackingIds = mutableSetOf<String>()
        val deliveryTrackingMap = mutableMapOf<Long, List<String>>()

        deliveryRecords.forEach { record ->
            val ids = extractTrackingIds(record.trackingIds)
            deliveryTrackingMap[record.deliveryId] = ids
            allTrackingIds.addAll(ids)
        }

        val bookingRecords = if (allTrackingIds.isNotEmpty()) {
            try {
                SupabaseInstance.client
                    .from("getAllBookings")
                    .select {
                        filter {
                            isIn("tracking_id", allTrackingIds.toList())
                        }
                    }
                    .decodeList<BookingViewRecord>()
            } catch (e: Exception) {
                Log.e("SupabaseError", "Error fetching matching bookings: ${e.message}", e)
                emptyList()
            }
        } else {
            emptyList()
        }

        val bookingMap = bookingRecords.associateBy { it.trackingId?.trim()?.uppercase() }

        val deliveredBookingIds = bookingRecords.mapNotNull { it.bookingId }
        val deliveredTimestampsMap = mutableMapOf<Long, String>()

        if (deliveredBookingIds.isNotEmpty()) {
            try {
                val logs = SupabaseInstance.client
                    .from("Status_log")
                    .select {
                        filter {
                            isIn("booking_id", deliveredBookingIds)
                            eq("status", "Delivered")
                        }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<StatusLogRow>()

                logs.forEach { log ->
                    if (log.bookingId != null && log.createdAt != null) {
                        if (!deliveredTimestampsMap.containsKey(log.bookingId)) {
                            deliveredTimestampsMap[log.bookingId] = log.createdAt
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SupabaseError", "Error fetching delivered timestamps: ${e.message}", e)
            }
        }

        deliveryRecords.map { record ->
            val recordIds = deliveryTrackingMap[record.deliveryId] ?: emptyList()
            val matchedBooking = recordIds.firstNotNullOfOrNull { bookingMap[it] }
            val rawStatus = record.deliveryStatus?.trim()?.takeIf { it.isNotBlank() } ?: "Pending"
            val deliveryTime = matchedBooking?.bookingId?.let { deliveredTimestampsMap[it] }

            Delivery(
                deliveryId = record.deliveryId,
                deliveryRef = record.deliveryRef,
                deliveredAt = deliveryTime,
                driverName = record.driverName,
                deliveryStatus = rawStatus,
                serviceType = matchedBooking?.serviceType?.takeIf { it.isNotBlank() } ?: "Standard Parcel",
                pickupAddress = matchedBooking?.origin?.takeIf { it.isNotBlank() && it != "null" } ?: "Warehouse / Hub",
                destinationAddress = matchedBooking?.destination?.takeIf { it.isNotBlank() && it != "null" } ?: "Specified Destination"
            )
        }
    } catch (e: Exception) {
        Log.e("SupabaseError", "FATAL EXCEPTION in fetchDeliveries: ${e.message}", e)
        emptyList()
    }
}

private fun extractTrackingIds(element: kotlinx.serialization.json.JsonElement?): List<String> {
    val list = mutableListOf<String>()
    when (element) {
        is JsonArray -> {
            element.forEach { item ->
                val clean = item.toString().replace("\"", "").trim().uppercase()
                if (clean.isNotBlank() && clean != "NULL") list.add(clean)
            }
        }
        is JsonPrimitive -> {
            val clean = element.toString().replace("\"", "").trim().uppercase()
            if (clean.isNotBlank() && clean != "NULL") list.add(clean)
        }
        else -> {
            val rawStr = element?.toString() ?: ""
            val cleaned = rawStr.replace(Regex("[\\[\\]\"{}()]"), "")
            cleaned.split(",").forEach { part ->
                val trimmed = part.trim().uppercase()
                if (trimmed.isNotBlank() && trimmed != "NULL") list.add(trimmed)
            }
        }
    }
    return list
}

@Composable
fun DashboardScreen(
    currentDriverId: String,
    onTabSelected: (String) -> Unit = {},
    onStartClick: (Delivery) -> Unit = {}
) {
    var driverDisplayName by remember { mutableStateOf("Loading...") }
    var deliveries by remember { mutableStateOf<List<Delivery>>(emptyList()) }
    var isInitialLoading by remember { mutableStateOf(true) }

    val pullToRefreshState = rememberPullToRefreshState()

    var selectedFilter by remember { mutableStateOf("All") }
    val filterOptions = listOf("All", "Pending", "In Transit", "Picked Up", "Out for Delivery", "Delivered")

    val reloadDeliveries: suspend () -> Unit = {
        if (driverDisplayName != "Loading..." && driverDisplayName != "Unassigned") {
            deliveries = fetchDeliveries(driverDisplayName)
        }
    }

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            try {
                reloadDeliveries()
            } finally {
                pullToRefreshState.endRefresh()
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            val currentUser = SupabaseInstance.client.auth.currentUserOrNull()
            val userId = currentUser?.id ?: currentDriverId
            if (userId.isNotEmpty()) {
                val userProfiles = SupabaseInstance.client
                    .from("User")
                    .select { filter { eq("uid", userId) } }
                    .decodeList<SupabaseUserProfile>()

                val matched = userProfiles.firstOrNull()

                driverDisplayName = if (matched != null) {
                    val fName = matched.firstName ?: "Driver"
                    val lName = matched.lastName ?: ""
                    "$fName $lName".trim()
                } else {
                    "Assigned Driver"
                }
            } else {
                driverDisplayName = "Unassigned"
            }
        } catch (e: Exception) {
            driverDisplayName = "Assigned Driver"
        }
    }

    LaunchedEffect(driverDisplayName) {
        if (driverDisplayName != "Loading..." && driverDisplayName != "Unassigned") {
            isInitialLoading = true
            reloadDeliveries()
            isInitialLoading = false
        }
    }

    // Filter Logic:
    // - "All": Non-delivered tasks + tasks delivered within the last 5 minutes.
    // - "Delivered": ALL delivered tasks permanently.
    val filteredDeliveries = remember(deliveries, selectedFilter) {
        when (selectedFilter) {
            "All" -> deliveries.filter { delivery ->
                val isDelivered = delivery.deliveryStatus.trim().equals("Delivered", ignoreCase = true)
                if (isDelivered) {
                    delivery.deliveredAt?.let { isWithinLast5Minutes(it) } ?: false
                } else {
                    true
                }
            }
            "Delivered" -> deliveries.filter { delivery ->
                delivery.deliveryStatus.trim().equals("Delivered", ignoreCase = true)
            }
            else -> deliveries.filter { delivery ->
                delivery.deliveryStatus.trim().equals(selectedFilter.trim(), ignoreCase = true)
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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Dashboard,
                        contentDescription = "Dashboard Icon",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = "Dashboard",
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
                        isSelected = true,
                        onClick = {
                            selectedFilter = "All"
                            onTabSelected("Dashboard")
                        }
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
                        isSelected = false,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Deliveries Overview",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryPurple
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filterOptions) { filter ->
                        val isSelected = selectedFilter == filter
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) PrimaryPurple else Color.White,
                            border = BorderStroke(1.dp, if (isSelected) PrimaryPurple else CardBorderColor),
                            modifier = Modifier.height(32.dp),
                            onClick = { selectedFilter = filter }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = filter,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else TextDark
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (isInitialLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = PrimaryPurple
                        )
                    } else if (deliveries.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No deliveries assigned to you.\nSwipe down to refresh.",
                                fontSize = 13.sp,
                                color = SubtitleColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (filteredDeliveries.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No deliveries found for '$selectedFilter'",
                                fontSize = 13.sp,
                                color = SubtitleColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredDeliveries) { delivery ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .shadow(elevation = 3.dp, shape = RoundedCornerShape(14.dp)),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, CardBorderColor.copy(alpha = 0.6f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = delivery.deliveryRef ?: "#${delivery.deliveryId}",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PrimaryPurple
                                            )

                                            val cleanServiceType = delivery.serviceType.lowercase().trim()
                                            val (serviceBgColor, serviceTextColor) = when {
                                                cleanServiceType.contains("commercial") -> {
                                                    Pair(Color(0xFFFEF3C7), Color(0xFFD97706))
                                                }
                                                cleanServiceType.contains("lipat") -> {
                                                    Pair(Color(0xFFE0E7FF), Color(0xFF4F46E5))
                                                }
                                                else -> {
                                                    Pair(Color(0xFFD1FAE5), Color(0xFF047857))
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = serviceBgColor
                                            ) {
                                                Text(
                                                    text = delivery.serviceType,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = serviceTextColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = "Driver: $driverDisplayName",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextDark
                                            )

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = null,
                                                    tint = PrimaryPurple,
                                                    modifier = Modifier.size(14.dp).padding(top = 2.dp)
                                                )
                                                Text(
                                                    text = "Pickup: ${delivery.pickupAddress}",
                                                    fontSize = 12.sp,
                                                    color = SubtitleColor,
                                                    maxLines = 2
                                                )
                                            }

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.LocationOn,
                                                    contentDescription = null,
                                                    tint = Color(0xFFD32F2F),
                                                    modifier = Modifier.size(14.dp).padding(top = 2.dp)
                                                )
                                                Text(
                                                    text = "Drop-off: ${delivery.destinationAddress}",
                                                    fontSize = 12.sp,
                                                    color = SubtitleColor,
                                                    maxLines = 2
                                                )
                                            }
                                        }

                                        HorizontalDivider(color = CardBorderColor.copy(alpha = 0.4f), thickness = 0.5.dp)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val (pillBgColor, pillTextColor, _) = when (delivery.deliveryStatus.lowercase()) {
                                                "delivered" -> Triple(Color(0xFFD8F3DC), Color(0xFF2D6A4F), "Delivered")
                                                "drop off" -> Triple(Color(0xFFFDE8E8), Color(0xFF9B1C1C), "Drop Off")
                                                "out for delivery" -> Triple(Color(0xFFFFE5D9), Color(0xFFBC4749), "Out for Delivery")
                                                "picked up" -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "Picked Up")
                                                "in transit" -> Triple(Color(0xFFE0EDFF), Color(0xFF2563EB), "In Transit")
                                                "verified", "bookings verified" -> Triple(Color(0xFFE0F2FE), Color(0xFF0284C7), "Verified")
                                                "pending" -> Triple(Color(0xFFF1EEF9), Purple, "Pending")
                                                else -> Triple(Color(0xFFF3F4F6), Color(0xFF4B5563), delivery.deliveryStatus.replaceFirstChar { it.uppercase() })
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = pillBgColor
                                            ) {
                                                Text(
                                                    text = delivery.deliveryStatus,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = pillTextColor,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }

                                            val isDelivered = delivery.deliveryStatus.equals("Delivered", ignoreCase = true)

                                            Button(
                                                onClick = { onStartClick(delivery) },
                                                modifier = Modifier
                                                    .height(36.dp)
                                                    .width(110.dp),
                                                shape = RoundedCornerShape(50),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isDelivered) ViewPurple else GoldButton
                                                ),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text(
                                                    text = if (isDelivered) "VIEW" else "START",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
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

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val tintColor = if (isSelected) PrimaryPurple else SubtitleColor

    IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tintColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = tintColor
            )
        }
    }
}