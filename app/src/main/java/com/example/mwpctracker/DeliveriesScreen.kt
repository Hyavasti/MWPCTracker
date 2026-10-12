@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.example.mwpctracker

import android.content.Intent
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.auth.auth
import android.util.Log
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream

private val PrimaryPurple = Color(0xFF2E234B)
private val Purple = Color(0xFF3B2E7E)
private val GoldButton = Color(0xFFD4A373)
private val BackgroundGray = Color(0xFFF7F8FC)
private val CardBorderColor = Color(0xFFD6DBE5)
private val TextDark = Color(0xFF2C243B)
private val SubtitleColor = Color(0xFF6B6575)

enum class DeliveryWorkflowStep {
    CONFIRM_BOOKINGS,
    PROCEED_TO_PICKUP,
    ARRIVED_AT_PICKUP,
    CAPTURE_PICKUP_PHOTO,
    PROCEED_TO_DROPOFF,
    ARRIVED_AT_DROPOFF,
    CAPTURE_DROPOFF_PROOF,
    COMPLETED
}

//insert into Status_log
@Serializable
data class StatusLogInsert(
    @SerialName("booking_id") val bookingId: Long? = null,
    @SerialName("status") val status: String,
    @SerialName("changed_by") val changedBy: String? = null,
    @SerialName("table_name") val tableName: String
)

@Serializable
private data class BookingIdOnly(
    @SerialName("booking_id") val bookingId: Long
)

// Booking Model
@Serializable
data class Booking(
    @SerialName("booking_id") val bookingId: Long,
    @SerialName("delivery_id") val deliveryId: Long? = null,
    @SerialName("tracking_id") val trackingId: String? = null,
    @SerialName("status") var status: String? = null,
    @SerialName("service_type") val serviceType: String? = null,
    var senderName: String? = null,
    var senderMobile: Long? = null,
    var receiverName: String? = null,
    var receiverMobile: Long? = null,
    var origin: String? = null,
    var destination: String? = null,
    var vehicleDelivery: String? = null,
    var paymentMethod: String? = null,
    var paymentStatus: String? = null,
    var totalAmount: Double? = null,

    // Standard fields
    var itemDescription: String? = null,
    var category: String? = null,
    var dimension: String? = null,

    // Lipatbahay fields
    var moveDate: String? = null,
    var packingMaterials: Boolean? = null,
    var roundTrip: Boolean? = null,
    var lipatbahaySpecialInstruction: String? = null,

    // Commercial fields
    var commercialSpecialInstruction: String? = null
)

// Standard View Record
@Serializable
data class StandardDetailRecord(
    @SerialName("booking_id") val bookingId: Long,
    @SerialName("tracking_id") val trackingId: String? = null,
    @SerialName("service_type") val serviceType: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("sender_name") val senderName: String? = null,
    @SerialName("sender_mobile") val senderMobile: Long? = null,
    val origin: String? = null,
    @SerialName("receiver_name") val receiverName: String? = null,
    @SerialName("receiver_mobile") val receiverMobile: Long? = null,
    val destination: String? = null,
    @SerialName("vehicle_delivery") val vehicleDelivery: String? = null,
    @SerialName("item_description") val itemDescription: String? = null,
    val category: String? = null,
    val dimension: String? = null,
    @SerialName("Payment_Method") val paymentMethod: String? = null,
    @SerialName("total_amount") val totalAmount: Double? = null,
    @SerialName("payment_status") val paymentStatus: String? = null
)

// Lipatbahay View Record
@Serializable
data class LipatbahayDetailRecord(
    @SerialName("booking_id") val bookingId: Long,
    @SerialName("tracking_id") val trackingId: String? = null,
    @SerialName("service_type") val serviceType: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("sender_name") val senderName: String? = null,
    @SerialName("sender_mobile") val senderMobile: Long? = null,
    val origin: String? = null,
    @SerialName("receiver_name") val receiverName: String? = null,
    @SerialName("receiver_mobile") val receiverMobile: Long? = null,
    val destination: String? = null,
    @SerialName("vehicle_delivery") val vehicleDelivery: String? = null,
    @SerialName("move_date") val moveDate: String? = null,
    val category: String? = null,
    @SerialName("packing_materials") val packingMaterials: Boolean? = null,
    @SerialName("round_trip") val roundTrip: Boolean? = null,
    @SerialName("special_instruction") val specialInstruction: String? = null,
    @SerialName("Payment_Method") val paymentMethod: String? = null,
    @SerialName("total_amount") val totalAmount: Double? = null,
    @SerialName("payment_status") val paymentStatus: String? = null
)

// Commercial View Record
@Serializable
data class CommercialDetailRecord(
    @SerialName("booking_id") val bookingId: Long,
    @SerialName("tracking_id") val trackingId: String? = null,
    @SerialName("service_type") val serviceType: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("sender_name") val senderName: String? = null,
    @SerialName("sender_mobile") val senderMobile: Long? = null,
    val origin: String? = null,
    @SerialName("receiver_name") val receiverName: String? = null,
    @SerialName("receiver_mobile") val receiverMobile: Long? = null,
    val destination: String? = null,
    @SerialName("vehicle_delivery") val vehicleDelivery: String? = null,
    @SerialName("item_description") val itemDescription: String? = null,
    val category: String? = null,
    val dimension: String? = null,
    @SerialName("special_instruction") val specialInstruction: String? = null,
    @SerialName("Payment_Method") val paymentMethod: String? = null,
    @SerialName("total_amount") val totalAmount: Double? = null,
    @SerialName("payment_status") val paymentStatus: String? = null
)

// Documentation Record Payload bytea(hex) format
@Serializable
data class DocumentationRecord(
    @SerialName("booking_id") val bookingId: Long,
    @SerialName("pickup_image") val pickupImage: String? = null,
    @SerialName("dropoff_image") val dropoffImage: String? = null,
    @SerialName("signature") val signature: String? = null
)

@Serializable
data class DocumentationFetchRecord(
    @SerialName("booking_id") val bookingId: Long,
    @SerialName("pickup_image") val pickupImage: String? = null,
    @SerialName("dropoff_image") val dropoffImage: String? = null,
    @SerialName("signature") val signature: String? = null
)

private fun compressBitmapToPostgresHex(bitmap: Bitmap, maxWidth: Int = 800, quality: Int = 70): String {
    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
    val width = if (bitmap.width > maxWidth) maxWidth else bitmap.width
    val height = (width / ratio).toInt().coerceAtLeast(1)

    val resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
    val outputStream = ByteArrayOutputStream()
    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    val byteArray = outputStream.toByteArray()

    val hexString = byteArray.joinToString("") { "%02x".format(it) }
    return "\\x$hexString"
}

private fun convertPostgresHexToBitmap(hexString: String?): Bitmap? {
    if (hexString.isNullOrBlank()) return null
    return try {
        val cleanHex = hexString.removePrefix("\\x").removePrefix("0x")
        val len = cleanHex.length
        val bytes = ByteArray(len / 2)
        var i = 0
        while (i < len - 1) {
            bytes[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4) +
                    Character.digit(cleanHex[i + 1], 16)).toByte()
            i += 2
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        Log.e("BitmapError", "Failed to convert hex to bitmap: ${e.message}", e)
        null
    }
}

private fun exportSignaturePathsToBitmap(paths: List<Path>, width: Int = 600, height: Int = 400): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 5f
        style = android.graphics.Paint.Style.STROKE
        isAntiAlias = true
    }

    paths.forEach { path ->
        canvas.drawPath(path.asAndroidPath(), paint)
    }
    return bitmap
}

suspend fun fetchBookingsForDelivery(deliveryId: Long): List<Booking> {
    return try {
        Log.d("SupabaseDebug", "Fetching bookings for deliveryId: $deliveryId")
        val bookings = SupabaseInstance.client
            .from("Booking")
            .select {
                filter { eq("delivery_id", deliveryId) }
            }
            .decodeList<Booking>()

        if (bookings.isEmpty()) return emptyList()

        val standardIds = bookings.filter { it.serviceType?.contains("standard", ignoreCase = true) == true }.map { it.bookingId }
        val lipatbahayIds = bookings.filter { it.serviceType?.contains("lipatbahay", ignoreCase = true) == true }.map { it.bookingId }
        val commercialIds = bookings.filter { it.serviceType?.contains("commercial", ignoreCase = true) == true }.map { it.bookingId }

        val standardDetails = if (standardIds.isNotEmpty()) {
            try { SupabaseInstance.client.from("getStandardBookingDetail").select { filter { isIn("booking_id", standardIds) } }.decodeList<StandardDetailRecord>() } catch (e: Exception) { emptyList() }
        } else emptyList()

        val lipatbahayDetails = if (lipatbahayIds.isNotEmpty()) {
            try { SupabaseInstance.client.from("getLipatbahayBookingDetail").select { filter { isIn("booking_id", lipatbahayIds) } }.decodeList<LipatbahayDetailRecord>() } catch (e: Exception) { emptyList() }
        } else emptyList()

        val commercialDetails = if (commercialIds.isNotEmpty()) {
            try { SupabaseInstance.client.from("getCommercialBookingDetail").select { filter { isIn("booking_id", commercialIds) } }.decodeList<CommercialDetailRecord>() } catch (e: Exception) { emptyList() }
        } else emptyList()

        val standardMap = standardDetails.associateBy { it.bookingId }
        val lipatbahayMap = lipatbahayDetails.associateBy { it.bookingId }
        val commercialMap = commercialDetails.associateBy { it.bookingId }

        bookings.forEach { booking ->
            val liveStatus = booking.status

            standardMap[booking.bookingId]?.let { std ->
                booking.senderName = std.senderName ?: "N/A"
                booking.senderMobile = std.senderMobile
                booking.origin = std.origin ?: "N/A"
                booking.receiverName = std.receiverName ?: "N/A"
                booking.receiverMobile = std.receiverMobile
                booking.destination = std.destination ?: "N/A"
                booking.vehicleDelivery = std.vehicleDelivery ?: "N/A"
                booking.itemDescription = std.itemDescription
                booking.category = std.category
                booking.dimension = std.dimension
                booking.paymentMethod = std.paymentMethod ?: "N/A"
                booking.totalAmount = std.totalAmount
                booking.paymentStatus = std.paymentStatus ?: "N/A"
            }

            lipatbahayMap[booking.bookingId]?.let { lip ->
                booking.senderName = lip.senderName ?: "N/A"
                booking.senderMobile = lip.senderMobile
                booking.origin = lip.origin ?: "N/A"
                booking.receiverName = lip.receiverName ?: "N/A"
                booking.receiverMobile = lip.receiverMobile
                booking.destination = lip.destination ?: "N/A"
                booking.vehicleDelivery = lip.vehicleDelivery ?: "N/A"
                booking.moveDate = lip.moveDate
                booking.category = lip.category
                booking.packingMaterials = lip.packingMaterials
                booking.roundTrip = lip.roundTrip
                booking.lipatbahaySpecialInstruction = lip.specialInstruction
                booking.paymentMethod = lip.paymentMethod ?: "N/A"
                booking.totalAmount = lip.totalAmount
                booking.paymentStatus = lip.paymentStatus ?: "N/A"
            }

            commercialMap[booking.bookingId]?.let { comm ->
                booking.senderName = comm.senderName ?: "N/A"
                booking.senderMobile = comm.senderMobile
                booking.origin = comm.origin ?: "N/A"
                booking.receiverName = comm.receiverName ?: "N/A"
                booking.receiverMobile = comm.receiverMobile
                booking.destination = comm.destination ?: "N/A"
                booking.vehicleDelivery = comm.vehicleDelivery ?: "N/A"
                booking.itemDescription = comm.itemDescription
                booking.category = comm.category
                booking.dimension = comm.dimension
                booking.commercialSpecialInstruction = comm.specialInstruction
                booking.paymentMethod = comm.paymentMethod ?: "N/A"
                booking.totalAmount = comm.totalAmount
                booking.paymentStatus = comm.paymentStatus ?: "N/A"
            }

            booking.status = liveStatus
        }

        bookings
    } catch (e: Exception) {
        Log.e("SupabaseError", "Failed to fetch bookings from views: ${e.message}", e)
        emptyList()
    }
}

@Composable
fun LabeledDetailText(label: String, value: String) {
    Text(
        text = buildAnnotatedString {
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = TextDark)) {
                append("$label: ")
            }
            withStyle(style = SpanStyle(fontWeight = FontWeight.Normal, color = SubtitleColor)) {
                append(value)
            }
        },
        fontSize = 12.sp
    )
}

@Composable
fun DocumentationPreviewDialog(
    pickupBitmap: Bitmap?,
    dropoffBitmap: Bitmap?,
    signatureBitmap: Bitmap?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delivery Documentation",
                fontWeight = FontWeight.Bold,
                color = PrimaryPurple,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Pickup Proof Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                if (pickupBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clipToBounds()
                            .border(1.dp, CardBorderColor, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = pickupBitmap.asImageBitmap(),
                            contentDescription = "Pickup Photo",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Text("No pickup photo available.", fontSize = 11.sp, color = SubtitleColor)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text("Drop-off Proof Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                if (dropoffBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clipToBounds()
                            .border(1.dp, CardBorderColor, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = dropoffBitmap.asImageBitmap(),
                            contentDescription = "Drop-off Photo",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Text("No drop-off photo available.", fontSize = 11.sp, color = SubtitleColor)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text("Receiver Signature", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                if (signatureBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clipToBounds()
                            .border(1.dp, CardBorderColor, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = signatureBitmap.asImageBitmap(),
                            contentDescription = "Receiver Signature",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Text("No signature recorded.", fontSize = 11.sp, color = SubtitleColor)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("Close", color = Color.White)
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White
    )
}

@Composable
fun DeliveriesScreen(
    delivery: Delivery,
    onBackClick: () -> Unit = {},
    onViewOnMapClick: (String) -> Unit = {},
    onMessageClick: () -> Unit = {},
    onCallClick: () -> Unit = {}
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val deliveryRefToDisplay = try {
        val field = delivery::class.java.getDeclaredField("deliveryRef").apply { isAccessible = true }
        field.get(delivery) as? String
    } catch (_: Exception) {
        null
    } ?: "DEL-${delivery.deliveryId}"

    LaunchedEffect(delivery.deliveryId) {
        Log.d("DeliveriesScreen", "Delivery selected: ID=${delivery.deliveryId}, Ref=$deliveryRefToDisplay. Starting location tracking.")
        val serviceIntent = Intent(context, LocationForegroundService::class.java).apply {
            putExtra("delivery_id", delivery.deliveryId)
            putExtra("delivery_ref", deliveryRefToDisplay)
            putExtra("highAccuracy", true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    var currentDeliveryStatus by remember { mutableStateOf(delivery.deliveryStatus ?: "Pending") }
    var showDocumentationDialog by remember { mutableStateOf(false) }

    var workflowStep by remember {
        mutableStateOf(
            when (currentDeliveryStatus.lowercase()) {
                "verified", "bookings verified" -> DeliveryWorkflowStep.PROCEED_TO_PICKUP
                "in transit" -> DeliveryWorkflowStep.ARRIVED_AT_PICKUP
                "picked up" -> DeliveryWorkflowStep.PROCEED_TO_DROPOFF
                "out for delivery" -> DeliveryWorkflowStep.ARRIVED_AT_DROPOFF
                "drop off" -> DeliveryWorkflowStep.CAPTURE_DROPOFF_PROOF
                "delivered" -> DeliveryWorkflowStep.COMPLETED
                else -> DeliveryWorkflowStep.CONFIRM_BOOKINGS
            }
        )
    }

    var driverDisplayName by remember { mutableStateOf("Loading driver...") }
    var bookingsList by remember { mutableStateOf<List<Booking>>(emptyList()) }

    LaunchedEffect(delivery.deliveryId) {
        bookingsList = fetchBookingsForDelivery(delivery.deliveryId)
    }

    suspend fun updateDeliveryAndBookingStatus(deliveryId: Long, newStatus: String) {
        try {
            Log.d("SupabaseUpdate", ">>> Starting update for deliveryId: $deliveryId to '$newStatus'")

            // Update the Deliveries table
            SupabaseInstance.client.from("Deliveries").update({
                set("status", newStatus)
            }) {
                filter { eq("delivery_id", deliveryId) }
            }

            // Update the Booking table
            SupabaseInstance.client.from("Booking").update({
                set("status", newStatus)
            }) {
                filter { eq("delivery_id", deliveryId) }
            }

            currentDeliveryStatus = newStatus

            // Instant UI badge update
            bookingsList = bookingsList.map { booking ->
                booking.copy(status = newStatus)
            }

            // Resolve target booking IDs safely
            val targetBookingIds = if (bookingsList.isNotEmpty()) {
                bookingsList.map { it.bookingId }
            } else {
                try {
                    SupabaseInstance.client.from("Booking")
                        .select(io.github.jan.supabase.postgrest.query.Columns.list("booking_id")) {
                            filter { eq("delivery_id", deliveryId) }
                        }
                        .decodeList<BookingIdOnly>()
                        .map { it.bookingId }
                } catch (e: Exception) {
                    Log.e("SupabaseStatusLog", "Failed fallback booking lookup: ${e.message}")
                    emptyList()
                }
            }

            val currentUserId = SupabaseInstance.client.auth.currentUserOrNull()?.id
            Log.d("SupabaseStatusLog", "Processing status update to '$newStatus' for booking IDs: $targetBookingIds | User: $currentUserId")

            // Log ONLY to Booking table in Status_log
            targetBookingIds.forEach { bId ->
                try {
                    val bookingLog = StatusLogInsert(
                        bookingId = bId,
                        status = newStatus,
                        changedBy = currentUserId,
                        tableName = "Booking"
                    )
                    val result = SupabaseInstance.client.from("Status_log").insert(bookingLog) {
                        select()
                    }.data
                    Log.d("SupabaseStatusLog", "SUCCESS: Logged Booking update for booking #$bId. Server confirmed: $result")
                } catch (e: Exception) {
                    Log.e("SupabaseStatusLogError", "Failed to log Booking table for #$bId: ${e.message}", e)
                }
            }

            Log.d("SupabaseUpdate", "Successfully completed update sequence for '$newStatus'")
        } catch (e: Exception) {
            Log.e("SupabaseUpdateError", "Failed overall status update: ${e.localizedMessage}", e)
        }
    }

    LaunchedEffect(Unit) {
        try {
            val currentUser = SupabaseInstance.client.auth.currentUserOrNull()
            val userId = currentUser?.id
            if (userId != null) {
                val userProfiles = SupabaseInstance.client
                    .from("User")
                    .select { filter { eq("uid", userId) } }
                    .decodeList<SupabaseUserProfile>()

                val matched = userProfiles.firstOrNull()
                if (matched != null) {
                    val fName = matched.firstName ?: "Driver"
                    val lName = matched.lastName ?: ""
                    driverDisplayName = "$fName $lName".trim()
                } else {
                    driverDisplayName = delivery.driverName ?: "Assigned Driver"
                }
            } else {
                driverDisplayName = delivery.driverName ?: "Unassigned"
            }
        } catch (e: Exception) {
            driverDisplayName = delivery.driverName ?: "Assigned Driver"
        }
    }

    var pickupImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var dropoffImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var signatureBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var signaturePaths by remember { mutableStateOf(listOf<Path>()) }
    var currentPath by remember { mutableStateOf(Path()) }

    LaunchedEffect(bookingsList) {
        if (bookingsList.isNotEmpty()) {
            val firstBookingId = bookingsList.first().bookingId
            try {
                val records = SupabaseInstance.client
                    .from("Documentation")
                    .select {
                        filter { eq("booking_id", firstBookingId) }
                    }
                    .decodeList<DocumentationFetchRecord>()

                val record = records.firstOrNull()
                if (record != null) {
                    if (pickupImageBitmap == null) {
                        pickupImageBitmap = convertPostgresHexToBitmap(record.pickupImage)
                    }
                    if (dropoffImageBitmap == null) {
                        dropoffImageBitmap = convertPostgresHexToBitmap(record.dropoffImage)
                    }
                    if (signatureBitmap == null) {
                        signatureBitmap = convertPostgresHexToBitmap(record.signature)
                    }
                }
            } catch (e: Exception) {
                Log.e("SupabaseError", "Failed to fetch saved documentation: ${e.message}", e)
            }
        }
    }

    var checkedBookingIds by remember(bookingsList, workflowStep, currentDeliveryStatus) {
        mutableStateOf(
            if (workflowStep != DeliveryWorkflowStep.CONFIRM_BOOKINGS || !currentDeliveryStatus.equals("pending", ignoreCase = true)) {
                bookingsList.map { it.bookingId }.toSet()
            } else {
                emptySet()
            }
        )
    }

    val allBookingsChecked = bookingsList.isNotEmpty() && checkedBookingIds.size == bookingsList.size

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            if (workflowStep == DeliveryWorkflowStep.CAPTURE_PICKUP_PHOTO) {
                pickupImageBitmap = bitmap
                coroutineScope.launch {
                    updateDeliveryAndBookingStatus(delivery.deliveryId, "Picked Up")

                    val pickupHex = compressBitmapToPostgresHex(bitmap)
                    bookingsList.forEach { booking ->
                        try {
                            val record = DocumentationRecord(
                                bookingId = booking.bookingId,
                                pickupImage = pickupHex
                            )
                            SupabaseInstance.client.from("Documentation").upsert(record) {
                                onConflict = "booking_id"
                            }
                        } catch (e: Exception) {
                            Log.e("SupabaseError", "Failed to save pickup image: ${e.message}", e)
                        }
                    }
                }
                workflowStep = DeliveryWorkflowStep.PROCEED_TO_DROPOFF
            } else if (workflowStep == DeliveryWorkflowStep.CAPTURE_DROPOFF_PROOF) {
                dropoffImageBitmap = bitmap
            }
        }
    }

    val (activeAddress, taskTitle, actionButtonText) = when (workflowStep) {
        DeliveryWorkflowStep.CONFIRM_BOOKINGS -> Triple(
            delivery.pickupAddress,
            "Confirm All Bookings",
            "Confirm All Bookings"
        )
        DeliveryWorkflowStep.PROCEED_TO_PICKUP -> Triple(
            delivery.pickupAddress,
            "Heading to Pickup",
            "Proceed to Pickup Location"
        )
        DeliveryWorkflowStep.ARRIVED_AT_PICKUP -> Triple(
            delivery.pickupAddress,
            "At Pickup Location",
            "Arrived at Pickup Location"
        )
        DeliveryWorkflowStep.CAPTURE_PICKUP_PHOTO -> Triple(
            delivery.pickupAddress,
            "At Pickup Location",
            "Capture Pickup Photo"
        )
        DeliveryWorkflowStep.PROCEED_TO_DROPOFF -> Triple(
            delivery.destinationAddress,
            "Pickup Complete",
            "Proceed to Dropoff Location"
        )
        DeliveryWorkflowStep.ARRIVED_AT_DROPOFF -> Triple(
            delivery.destinationAddress,
            "Heading to Drop-off",
            "Arrived at Dropoff Location"
        )
        DeliveryWorkflowStep.CAPTURE_DROPOFF_PROOF -> Triple(
            delivery.destinationAddress,
            "Drop-off Verification",
            "Complete Delivery"
        )
        DeliveryWorkflowStep.COMPLETED -> Triple(
            delivery.destinationAddress,
            "Completed",
            "Completed"
        )
    }

    val hasSignature = signaturePaths.isNotEmpty() || !currentPath.isEmpty
    val canProceed = when (workflowStep) {
        DeliveryWorkflowStep.CONFIRM_BOOKINGS -> allBookingsChecked
        DeliveryWorkflowStep.CAPTURE_DROPOFF_PROOF -> dropoffImageBitmap != null && hasSignature
        else -> true
    }

    Scaffold(
        topBar = {
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
                    Column {
                        Text(
                            text = "Active Delivery",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${delivery.serviceType} • ${delivery.routeType}",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
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
            if (workflowStep != DeliveryWorkflowStep.COMPLETED) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                    Button(
                        onClick = { onViewOnMapClick(activeAddress) },
                        modifier = Modifier.height(36.dp).width(160.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldButton),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Open in Maps",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            // Delivery Details Card
            Card(
                modifier = Modifier.fillMaxWidth()
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, CardBorderColor.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = taskTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryPurple
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = PrimaryPurple.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = delivery.routeType,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryPurple,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(64.dp)
                                .background(PrimaryPurple, shape = RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = deliveryRefToDisplay,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Text(
                                text = "Driver: $driverDisplayName",
                                fontSize = 13.sp,
                                color = TextDark
                            )
                            Text(
                                text = "Address: $activeAddress",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = SubtitleColor
                            )
                        }

                        IconButton(onClick = onCallClick, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Call",
                                tint = PrimaryPurple,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Bookings Checklist Card
            Card(
                modifier = Modifier.fillMaxWidth()
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, CardBorderColor.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Verify Bookings in Truck (${checkedBookingIds.size}/${bookingsList.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryPurple
                        )
                        TextButton(
                            enabled = workflowStep == DeliveryWorkflowStep.CONFIRM_BOOKINGS,
                            onClick = {
                                checkedBookingIds = if (checkedBookingIds.size == bookingsList.size) {
                                    emptySet()
                                } else {
                                    bookingsList.map { it.bookingId }.toSet()
                                }
                            }
                        ) {
                            Text(
                                text = if (checkedBookingIds.size == bookingsList.size) "Uncheck All" else "Check All",
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (bookingsList.isEmpty()) {
                        Text(
                            text = "Loading bookings for this delivery...",
                            fontSize = 12.sp,
                            color = SubtitleColor
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            bookingsList.forEach { booking ->
                                val isChecked = checkedBookingIds.contains(booking.bookingId)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = BackgroundGray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isChecked) Color(0xFF2DC653).copy(alpha = 0.4f) else CardBorderColor.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                enabled = workflowStep == DeliveryWorkflowStep.CONFIRM_BOOKINGS,
                                                onCheckedChange = { checked ->
                                                    checkedBookingIds = if (checked) {
                                                        checkedBookingIds + booking.bookingId
                                                    } else {
                                                        checkedBookingIds - booking.bookingId
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = PrimaryPurple,
                                                    uncheckedColor = SubtitleColor,
                                                    checkmarkColor = Color.White,
                                                    disabledCheckedColor = PrimaryPurple.copy(alpha = 0.6f),
                                                    disabledUncheckedColor = Color.LightGray
                                                )
                                            )
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(
                                                    text = booking.trackingId ?: "Booking #${booking.bookingId}",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextDark
                                                )

                                                val statusToDisplay = booking.status ?: currentDeliveryStatus
                                                val (pillBgColor, pillTextColor, pillDisplayText) = when (statusToDisplay.lowercase()) {
                                                    "delivered" -> Triple(Color(0xFFD8F3DC), Color(0xFF2D6A4F), "Delivered")
                                                    "drop off" -> Triple(Color(0xFFFDE8E8), Color(0xFF9B1C1C), "Drop Off")
                                                    "out for delivery" -> Triple(Color(0xFFFFE5D9), Color(0xFFBC4749), "Out for Delivery")
                                                    "picked up" -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "Picked Up")
                                                    "in transit" -> Triple(Color(0xFFE0EDFF), Color(0xFF2563EB), "In Transit")
                                                    "verified", "bookings verified" -> Triple(Color(0xFFE0F2FE), Color(0xFF0284C7), "Verified")
                                                    "pending" -> Triple(Color(0xFFF1EEF9), Purple, "Pending")
                                                    else -> Triple(Color(0xFFF3F4F6), Color(0xFF4B5563), statusToDisplay.replaceFirstChar { it.uppercase() })
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = pillBgColor
                                                ) {
                                                    Text(
                                                        text = pillDisplayText,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = pillTextColor,
                                                        maxLines = 1,
                                                        modifier = Modifier.padding(
                                                            horizontal = 10.dp,
                                                            vertical = 3.dp
                                                        )
                                                    )
                                                }

                                                Text(
                                                    text = "Booking ID: ${booking.bookingId} • Service: ${booking.serviceType ?: "N/A"}",
                                                    fontSize = 11.sp,
                                                    color = SubtitleColor
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(
                                        color = Color.LightGray.copy(alpha = 0.4f),
                                        thickness = 0.5.dp
                                    )

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 36.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        LabeledDetailText(
                                            label = "Sender",
                                            value = "${booking.senderName ?: "N/A"} (${booking.senderMobile ?: "No contact"})"
                                        )
                                        LabeledDetailText(
                                            label = "Origin",
                                            value = booking.origin ?: "N/A"
                                        )
                                        LabeledDetailText(
                                            label = "Receiver",
                                            value = "${booking.receiverName ?: "N/A"} (${booking.receiverMobile ?: "No contact"})"
                                        )
                                        LabeledDetailText(
                                            label = "Destination",
                                            value = booking.destination ?: "N/A"
                                        )
                                        LabeledDetailText(
                                            label = "Vehicle Delivery",
                                            value = booking.vehicleDelivery ?: "N/A"
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))

                                        if (booking.serviceType?.contains("standard", ignoreCase = true) == true) {
                                            booking.itemDescription?.let { LabeledDetailText(label = "Item Description", value = it) }
                                            booking.category?.let { LabeledDetailText(label = "Category", value = it) }
                                            booking.dimension?.let { LabeledDetailText(label = "Dimension", value = it) }
                                        }

                                        if (booking.serviceType?.contains("lipatbahay", ignoreCase = true) == true) {
                                            booking.moveDate?.let { LabeledDetailText(label = "Move Date", value = it) }
                                            booking.category?.let { LabeledDetailText(label = "Category", value = it) }
                                            booking.packingMaterials?.let { LabeledDetailText(label = "Packing Materials", value = if (it) "Yes" else "No") }
                                            booking.roundTrip?.let { LabeledDetailText(label = "Round Trip", value = if (it) "Yes" else "No") }
                                            booking.lipatbahaySpecialInstruction?.let { LabeledDetailText(label = "Special Instruction", value = it) }
                                        }

                                        if (booking.serviceType?.contains("commercial", ignoreCase = true) == true) {
                                            booking.itemDescription?.let { LabeledDetailText(label = "Item Description", value = it) }
                                            booking.category?.let { LabeledDetailText(label = "Category", value = it) }
                                            booking.dimension?.let { LabeledDetailText(label = "Dimension", value = it) }
                                            booking.commercialSpecialInstruction?.let { LabeledDetailText(label = "Special Instruction", value = it) }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        HorizontalDivider(
                                            color = Color.LightGray.copy(alpha = 0.4f),
                                            thickness = 0.5.dp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))

                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            val amountStr = if (booking.totalAmount != null) "₱%,.2f".format(booking.totalAmount) else "N/A"
                                            LabeledDetailText(
                                                label = "Payment Method",
                                                value = "${booking.paymentMethod ?: "N/A"} ($amountStr)"
                                            )
                                            LabeledDetailText(
                                                label = "Payment Status",
                                                value = booking.paymentStatus ?: "N/A"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Verification & Proof Card
            Card(
                modifier = Modifier.fillMaxWidth()
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, CardBorderColor.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Verification & Proof",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryPurple
                    )

                    if (workflowStep == DeliveryWorkflowStep.CAPTURE_DROPOFF_PROOF) {
                        Button(
                            onClick = { cameraLauncher.launch(null) },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryPurple,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (dropoffImageBitmap == null) "Take Drop-off Photo" else "Retake Photo",
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }

                        if (dropoffImageBitmap != null) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(120.dp)
                                    .background(Color.LightGray, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = dropoffImageBitmap!!.asImageBitmap(),
                                    contentDescription = "Dropoff Proof",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Receiver Signature:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .background(BackgroundGray, RoundedCornerShape(8.dp))
                                .clipToBounds()
                                .border(1.dp, CardBorderColor, RoundedCornerShape(8.dp))
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            currentPath = Path().apply { moveTo(offset.x, offset.y) }
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            currentPath = Path().apply {
                                                addPath(currentPath)
                                                lineTo(change.position.x, change.position.y)
                                            }
                                        },
                                        onDragEnd = {
                                            signaturePaths = signaturePaths + currentPath
                                            currentPath = Path()
                                        },
                                        onDragCancel = {
                                            currentPath = Path()
                                        }
                                    )
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                signaturePaths.forEach { path ->
                                    drawPath(
                                        path = path,
                                        color = Color.Black,
                                        style = Stroke(width = 4f)
                                    )
                                }
                                drawPath(
                                    path = currentPath,
                                    color = Color.Black,
                                    style = Stroke(width = 4f)
                                )
                            }
                            TextButton(
                                onClick = {
                                    signaturePaths = emptyList()
                                    currentPath = Path()
                                },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Text("Clear", fontSize = 10.sp, color = Color.Red)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val buttonLabel = when {
                        workflowStep == DeliveryWorkflowStep.CONFIRM_BOOKINGS && !canProceed -> "Verify All Bookings First"
                        workflowStep == DeliveryWorkflowStep.CAPTURE_DROPOFF_PROOF && !canProceed -> "Photo & Signature Required"
                        else -> actionButtonText
                    }

                    Button(
                        enabled = canProceed,
                        onClick = {
                            coroutineScope.launch {
                                Log.d("TrackingDebug", "Tracking: $deliveryRefToDisplay, delivery_id: ${delivery.deliveryId}")

                                when (workflowStep) {
                                    // Confirm All Bookings - Verified
                                    DeliveryWorkflowStep.CONFIRM_BOOKINGS -> {
                                        updateDeliveryAndBookingStatus(
                                            delivery.deliveryId,
                                            "Verified"
                                        )
                                        workflowStep = DeliveryWorkflowStep.PROCEED_TO_PICKUP
                                    }

                                    // Proceed to Pickup Location - In Transit
                                    DeliveryWorkflowStep.PROCEED_TO_PICKUP -> {
                                        updateDeliveryAndBookingStatus(
                                            delivery.deliveryId,
                                            "In Transit"
                                        )

                                        onViewOnMapClick(delivery.pickupAddress)
                                        workflowStep = DeliveryWorkflowStep.ARRIVED_AT_PICKUP
                                    }

                                    // Arrived at Pickup Location - photo capture
                                    DeliveryWorkflowStep.ARRIVED_AT_PICKUP -> {
                                        val bringToFrontIntent = Intent(context, context::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        }
                                        context.startActivity(bringToFrontIntent)

                                        workflowStep = DeliveryWorkflowStep.CAPTURE_PICKUP_PHOTO
                                    }

                                    //Capture Pickup Photo - Launches camera "Picked Up"
                                    DeliveryWorkflowStep.CAPTURE_PICKUP_PHOTO -> {
                                        cameraLauncher.launch(null)
                                    }

                                    //Proceed to Dropoff Location - Out for Delivery
                                    DeliveryWorkflowStep.PROCEED_TO_DROPOFF -> {
                                        updateDeliveryAndBookingStatus(
                                            delivery.deliveryId,
                                            "Out for Delivery"
                                        )

                                        onViewOnMapClick(delivery.destinationAddress)
                                        workflowStep = DeliveryWorkflowStep.ARRIVED_AT_DROPOFF
                                    }

                                    //Arrived at Dropoff Location - Drop Off
                                    DeliveryWorkflowStep.ARRIVED_AT_DROPOFF -> {
                                        updateDeliveryAndBookingStatus(
                                            delivery.deliveryId,
                                            "Drop Off"
                                        )

                                        val bringToFrontIntent = Intent(context, context::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        }
                                        context.startActivity(bringToFrontIntent)

                                        workflowStep = DeliveryWorkflowStep.CAPTURE_DROPOFF_PROOF
                                    }

                                    // Complete Delivery -> Delivered
                                    DeliveryWorkflowStep.CAPTURE_DROPOFF_PROOF -> {
                                        if (dropoffImageBitmap != null && hasSignature) {
                                            updateDeliveryAndBookingStatus(
                                                delivery.deliveryId,
                                                "Delivered"
                                            )

                                            val finalPaths = if (!currentPath.isEmpty) signaturePaths + currentPath else signaturePaths
                                            signaturePaths = finalPaths
                                            currentPath = Path()

                                            val dropoffHex = compressBitmapToPostgresHex(dropoffImageBitmap!!)
                                            val sigBitmap = exportSignaturePathsToBitmap(finalPaths)
                                            signatureBitmap = sigBitmap
                                            val signatureHex = compressBitmapToPostgresHex(sigBitmap, maxWidth = 600, quality = 80)

                                            bookingsList.forEach { booking ->
                                                try {
                                                    val record = DocumentationRecord(
                                                        bookingId = booking.bookingId,
                                                        dropoffImage = dropoffHex,
                                                        signature = signatureHex
                                                    )
                                                    SupabaseInstance.client.from("Documentation").upsert(record) {
                                                        onConflict = "booking_id"
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("SupabaseError", "Failed to save drop-off documentation: ${e.message}", e)
                                                }
                                            }

                                            val stopIntent = Intent(context, LocationForegroundService::class.java).apply {
                                                action = LocationForegroundService.ACTION_STOP_TRACKING
                                            }
                                            context.startService(stopIntent)

                                            workflowStep = DeliveryWorkflowStep.COMPLETED
                                        }
                                    }

                                    DeliveryWorkflowStep.COMPLETED -> {}
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (workflowStep == DeliveryWorkflowStep.COMPLETED) Color(0xFF2DC653) else GoldButton,
                            disabledContainerColor = Color.Gray.copy(alpha = 0.4f)
                        )
                    ) {
                        Icon(
                            imageVector = when (workflowStep) {
                                DeliveryWorkflowStep.CAPTURE_PICKUP_PHOTO, DeliveryWorkflowStep.CAPTURE_DROPOFF_PROOF -> Icons.Default.CameraAlt
                                DeliveryWorkflowStep.COMPLETED -> Icons.Default.Check
                                else -> Icons.Default.CheckCircle
                            },
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = buttonLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    if (workflowStep == DeliveryWorkflowStep.COMPLETED) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { showDocumentationDialog = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple.copy(alpha = 0.85f))
                        ) {
                            Icon(imageVector = Icons.Default.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "View Documentation & Signature",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onBackClick,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                        ) {
                            Icon(imageVector = Icons.Default.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Return to Dashboard",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDocumentationDialog) {
        DocumentationPreviewDialog(
            pickupBitmap = pickupImageBitmap,
            dropoffBitmap = dropoffImageBitmap,
            signatureBitmap = signatureBitmap,
            onDismiss = { showDocumentationDialog = false }
        )
    }
}