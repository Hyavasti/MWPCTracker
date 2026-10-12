@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.example.mwpctracker

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

private val PrimaryPurple = Color(0xFF2E234B)
private val BackgroundGray = Color(0xFFF7F8FC)
private val TextDark = Color(0xFF2C243B)
private val SubtitleColor = Color(0xFF7A7585)

@Serializable
data class IncidentRecord(
    @SerialName("incident_ref") val incidentRef: String?,
    @SerialName("incident_type") val incidentType: String?,
    @SerialName("incident_title") val incidentTitle: String?,
    @SerialName("incident_description") val incidentDescription: String?,
    @SerialName("incident_severity") val incidentSeverity: String?,
    @SerialName("incident_location") val incidentLocation: String?,
    @SerialName("incident_status") val incidentStatus: String?,
    @SerialName("img") val img: String?,
    @SerialName("uid") val uid: String?
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun IncidentReportScreen(
    currentDriverId: String,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // BringIntoView for auto-scrolling
    val titleRequester = remember { BringIntoViewRequester() }
    val locationRequester = remember { BringIntoViewRequester() }
    val descriptionRequester = remember { BringIntoViewRequester() }

    // Form fields
    var incidentTitle by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var incidentLocation by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    // Multiple image attachments (up to 5)
    val imageUris = remember { mutableStateListOf<Uri>() }
    val imageCodesMap = remember { mutableStateMapOf<Uri, String>() }

    var showImageSourceDialog by remember { mutableStateOf(false) }

    // Discard Confirmation Dialog State
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Success Modal state
    var showSuccessModal by remember { mutableStateOf(false) }
    var submittedRefId by remember { mutableStateOf("") }
    var generatedImgCodes by remember { mutableStateOf("") }

    // Dropdown: Incident Type
    val categoryOptions = listOf("Damaged Goods", "Wrong Address / Location", "Delivery Delay", "Customer Unavailable", "Other")
    var selectedCategory by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var customCategoryText by remember { mutableStateOf("") }

    // Dropdown: Severity
    val severityOptions = listOf("Low", "Medium", "High", "Critical")
    var selectedSeverity by remember { mutableStateOf("") }
    var severityExpanded by remember { mutableStateOf(false) }

    // Check if user has started typing or attaching anything
    val hasUnsavedChanges = remember(incidentTitle, description, incidentLocation, selectedCategory, selectedSeverity, imageUris) {
        incidentTitle.isNotBlank() || description.isNotBlank() || incidentLocation.isNotBlank() ||
                selectedCategory.isNotBlank() || selectedSeverity.isNotBlank() || imageUris.isNotEmpty()
    }

    // Intercept system back button
    BackHandler(enabled = hasUnsavedChanges) {
        showDiscardDialog = true
    }

    fun addImages(newUris: List<Uri>) {
        if (imageUris.size + newUris.size > 5) {
            Toast.makeText(context, "You can upload a maximum of 5 photos.", Toast.LENGTH_SHORT).show()
            return
        }
        for (uri in newUris) {
            imageUris.add(uri)
            val randomId = (1000..9999).random()
            val sizeKb = getUriFileSizeKb(context, uri)
            imageCodesMap[uri] = "img-$randomId ($sizeKb KB)"
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            addImages(uris)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val uri = bitmapToUri(context, bitmap)
            if (uri != null) {
                addImages(listOf(uri))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "File Incident Report",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) {
                            showDiscardDialog = true
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryPurple)
            )
        },
        containerColor = BackgroundGray
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(scrollState, enabled = true)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Notice card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Reports are sent directly to the database. Admin will review your entry shortly.",
                        fontSize = 12.sp,
                        color = SubtitleColor,
                        lineHeight = 16.sp
                    )
                }
            }

            // Incident Type Dropdown
            Text(text = "Incident Type", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded }
            ) {
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("Select incident type", color = SubtitleColor) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryPurple,
                        unfocusedBorderColor = Color.LightGray,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedTextColor = TextDark,
                        unfocusedTextColor = TextDark
                    )
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false },
                    modifier = Modifier
                        .background(Color.White)
                        .exposedDropdownSize(matchTextFieldWidth = true)
                ) {
                    categoryOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = TextDark) },
                            onClick = {
                                selectedCategory = option
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            // Conditional Text Field "Other"
            if (selectedCategory == "Other") {
                Text(text = "Specify Other Incident Type", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
                OutlinedTextField(
                    value = customCategoryText,
                    onValueChange = { customCategoryText = it },
                    placeholder = { Text("Enter custom incident type", color = SubtitleColor) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryPurple,
                        unfocusedBorderColor = Color.LightGray,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedTextColor = TextDark,
                        unfocusedTextColor = TextDark
                    )
                )
            }

            // Incident Title
            Text(text = "Incident Title / Short Summary", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
            OutlinedTextField(
                value = incidentTitle,
                onValueChange = { incidentTitle = it },
                placeholder = { Text("e.g., Package dropped and box torn", color = SubtitleColor) },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(titleRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(200)
                                titleRequester.bringIntoView()
                            }
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = Color.LightGray,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = TextDark,
                    unfocusedTextColor = TextDark
                )
            )

            // Severity Level Dropdown
            Text(text = "Severity Level", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
            ExposedDropdownMenuBox(
                expanded = severityExpanded,
                onExpandedChange = { severityExpanded = !severityExpanded }
            ) {
                OutlinedTextField(
                    value = selectedSeverity,
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("Select severity level", color = SubtitleColor) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = severityExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryPurple,
                        unfocusedBorderColor = Color.LightGray,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedTextColor = TextDark,
                        unfocusedTextColor = TextDark
                    )
                )
                ExposedDropdownMenu(
                    expanded = severityExpanded,
                    onDismissRequest = { severityExpanded = false },
                    modifier = Modifier
                        .background(Color.White)
                        .exposedDropdownSize(matchTextFieldWidth = true)
                ) {
                    severityOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = TextDark) },
                            onClick = {
                                selectedSeverity = option
                                severityExpanded = false
                            }
                        )
                    }
                }
            }

            // Location
            Text(text = "Location / Landmark", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
            OutlinedTextField(
                value = incidentLocation,
                onValueChange = { incidentLocation = it },
                placeholder = { Text("e.g., Near Evangelista St, Bacoor", color = SubtitleColor) },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(locationRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(200)
                                locationRequester.bringIntoView()
                            }
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = Color.LightGray,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = TextDark,
                    unfocusedTextColor = TextDark
                )
            )

            // Detailed Description
            Text(text = "Detailed Description", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { Text("Provide full context about what happened...", color = SubtitleColor) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .bringIntoViewRequester(descriptionRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(200)
                                descriptionRequester.bringIntoView()
                            }
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = Color.LightGray,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = TextDark,
                    unfocusedTextColor = TextDark
                )
            )

            // Photo Upload Section
            Text(text = "Photo Proof / Evidence (Up to 5)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(75.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                        .clickable { showImageSourceDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, tint = PrimaryPurple)
                        Text(
                            text = if (imageUris.isEmpty()) "Tap to Add Photos *" else "Add More Photos (${imageUris.size}/5)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryPurple
                        )
                    }
                }

                if (imageUris.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        imageUris.forEach { uri ->
                            val codeTag = imageCodesMap[uri] ?: "img-0000 (120 KB)"
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = codeTag,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PrimaryPurple
                                    )
                                    IconButton(
                                        onClick = {
                                            imageUris.remove(uri)
                                            imageCodesMap.remove(uri)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = SubtitleColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Submit Button
            Button(
                onClick = {
                    val finalCategory = if (selectedCategory == "Other") customCategoryText else selectedCategory

                    if (selectedCategory.isBlank() || selectedSeverity.isBlank() || incidentTitle.isBlank() || description.isBlank() || (selectedCategory == "Other" && customCategoryText.isBlank())) {
                        Toast.makeText(context, "Please complete all fields and selections.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (imageUris.isEmpty()) {
                        Toast.makeText(context, "Please attach at least one photo as proof.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isSubmitting = true
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val dateFormat = SimpleDateFormat("MMddyy", Locale.getDefault())
                            val dateString = dateFormat.format(Date())
                            val randomSuffix = (1000..9999).random()
                            val autoIncidentRef = "INC-$dateString-$randomSuffix"

                            val compressedBase64Imgs = imageUris.mapNotNull { uri ->
                                compressImageToCompressedBase64(context, uri, maxWidth = 800, quality = 70)
                            }.joinToString(",")

                            val imgCodeTags = imageUris.map { uri ->
                                imageCodesMap[uri] ?: "img-0000 (120 KB)"
                            }.joinToString(", ")

                            val newReport = IncidentRecord(
                                incidentRef = autoIncidentRef,
                                incidentType = finalCategory,
                                incidentTitle = incidentTitle,
                                incidentDescription = description,
                                incidentSeverity = selectedSeverity,
                                incidentLocation = incidentLocation.ifBlank { null },
                                incidentStatus = "Pending",
                                img = compressedBase64Imgs.ifBlank { null },
                                uid = currentDriverId.ifBlank { null }
                            )

                            SupabaseInstance.client
                                .from("Incident")
                                .insert(newReport)

                            withContext(Dispatchers.Main) {
                                isSubmitting = false
                                submittedRefId = autoIncidentRef
                                generatedImgCodes = imgCodeTags
                                showSuccessModal = true
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                isSubmitting = false
                                Toast.makeText(context, "Failed to submit: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryPurple,
                    disabledContainerColor = PrimaryPurple.copy(alpha = 0.4f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                ),
                enabled = !isSubmitting && imageUris.isNotEmpty()
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text(
                        text = if (imageUris.isEmpty()) "Attach a Photo to Submit" else "Submit Incident Report",
                        color = if (imageUris.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Discard Confirmation Dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Incident Report?", fontWeight = FontWeight.Bold, color = TextDark) },
            text = { Text("You have unsaved changes. Are you sure you want to leave and discard this report?", color = SubtitleColor) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBackClick()
                    }
                ) {
                    Text("Discard", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardDialog = false }
                ) {
                    Text("Continue Editing", color = PrimaryPurple, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Image Source Picker
    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Select Photo Source", fontWeight = FontWeight.Bold, color = TextDark) },
            text = { Text("Choose how you want to attach evidence photos.", color = SubtitleColor) },
            confirmButton = {
                TextButton(onClick = {
                    showImageSourceDialog = false
                    cameraLauncher.launch(null)
                }) {
                    Text("Camera", color = PrimaryPurple, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImageSourceDialog = false
                    galleryLauncher.launch("image/*")
                }) {
                    Text("Gallery", color = PrimaryPurple, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Success Modal Dialog
    if (showSuccessModal) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(PrimaryPurple.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(30.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Incident Reported!", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Your incident report has been successfully sent to the Admin for review.",
                        fontSize = 13.sp,
                        color = SubtitleColor,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = BackgroundGray)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Reference Number:", fontSize = 11.sp, color = SubtitleColor)
                            Text(text = submittedRefId, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryPurple)
                        }
                    }

                    if (generatedImgCodes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = BackgroundGray)
                        ) {
                            Text(
                                text = "Attached: $generatedImgCodes",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryPurple,
                                modifier = Modifier.padding(10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = {
                            saveSummaryReceiptToGallery(context, submittedRefId, imageUris)
                            Toast.makeText(context, "Summary receipt saved to Gallery!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = PrimaryPurple)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Receipt to Gallery", color = PrimaryPurple, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            showSuccessModal = false
                            onBackClick()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                    ) {
                        Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

private fun bitmapToUri(context: Context, bitmap: Bitmap): Uri? {
    val bytes = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
    val path = MediaStore.Images.Media.insertImage(context.contentResolver, bitmap, "IncidentPhoto_${System.currentTimeMillis()}", null)
    return Uri.parse(path)
}

private fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun getUriFileSizeKb(context: Context, uri: Uri): Long {
    return try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        val sizeIndex = cursor?.getColumnIndex(OpenableColumns.SIZE)
        cursor?.use {
            if (it.moveToFirst() && sizeIndex != null && sizeIndex != -1) {
                val sizeBytes = it.getLong(sizeIndex)
                return sizeBytes / 1024
            }
        }
        val inputStream = context.contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()?.size ?: 0
        inputStream?.close()
        (bytes / 1024).toLong()
    } catch (e: Exception) {
        120L
    }
}

private fun compressImageToCompressedBase64(context: Context, uri: Uri, maxWidth: Int, quality: Int): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        if (originalBitmap == null) return null

        val ratio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
        val width = if (originalBitmap.width > maxWidth) maxWidth else originalBitmap.width
        val height = (width / ratio).toInt()

        val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true)
        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()

        return "data:image/jpeg;base64," + android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun saveSummaryReceiptToGallery(context: Context, refId: String, imageUris: List<Uri>) {
    try {
        val width = 1080
        val height = 1500
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        canvas.drawColor(android.graphics.Color.parseColor("#F7F8FC"))

        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
        }

        // Header Background
        paint.color = android.graphics.Color.parseColor("#2E234B")
        canvas.drawRect(0f, 0f, width.toFloat(), 250f, paint)

        // Header Title Text
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 54f
        paint.isFakeBoldText = true
        canvas.drawText("MWPC Incident Report Receipt", 60f, 150f, paint)

        // Reference Card Background
        paint.color = android.graphics.Color.WHITE
        val cardRect = android.graphics.RectF(60f, 320f, (width - 60).toFloat(), 620f)
        canvas.drawRoundRect(cardRect, 30f, 30f, paint)

        // Reference Label & ID
        paint.color = android.graphics.Color.parseColor("#7A7585")
        paint.textSize = 36f
        paint.isFakeBoldText = false
        canvas.drawText("INCIDENT REFERENCE NUMBER:", 110f, 410f, paint)

        paint.color = android.graphics.Color.parseColor("#2E234B")
        paint.textSize = 52f
        paint.isFakeBoldText = true
        canvas.drawText(refId, 110f, 500f, paint)

        // Status Message Text
        paint.color = android.graphics.Color.parseColor("#2C243B")
        paint.textSize = 42f
        paint.isFakeBoldText = false
        canvas.drawText("Status: Successfully sent to the Admin", 60f, 720f, paint)
        canvas.drawText("Your report is currently pending.", 60f, 790f, paint)

        // actual photos
        if (imageUris.isNotEmpty()) {
            val startY = 870f
            val boxWidth = 210f
            val boxHeight = 210f
            val spacing = 25f
            var startX = 60f

            imageUris.take(4).forEach { uri ->
                val photoBitmap = uriToBitmap(context, uri)
                if (photoBitmap != null) {
                    val photoRect = android.graphics.RectF(startX, startY, startX + boxWidth, startY + boxHeight)

                    paint.color = android.graphics.Color.WHITE
                    canvas.drawRoundRect(photoRect, 20f, 20f, paint)

                    canvas.drawBitmap(photoBitmap, null, android.graphics.RectF(startX + 10f, startY + 10f, startX + boxWidth - 10f, startY + boxHeight - 10f), paint)
                }
                startX += boxWidth + spacing
            }
        }

        val filename = "Receipt_$refId.jpg"
        var fos: OutputStream? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MWPCTracker")
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                fos = resolver.openOutputStream(uri)
            }
        } else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val imageFile = java.io.File(imagesDir, filename)
            fos = java.io.FileOutputStream(imageFile)
        }

        fos?.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}