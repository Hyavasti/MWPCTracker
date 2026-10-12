@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)
package com.example.mwpctracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File

private val PrimaryPurple = Color(0xFF2E234B)
private val BackgroundGray = Color(0xFFF7F8FC)
private val TextDark = Color(0xFF2C243B)
private val SubtitleColor = Color(0xFF7A7585)
private val WarningOrange = Color(0xFFED6C02)
private val SuccessGreen = Color(0xFF2E7D32)

@Serializable
data class AccountProfileModel(
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("profile_picture") val profilePicture: String? = null
)

@Serializable
data class ProfilePictureByteaUpdate(
    @SerialName("profile_picture") val profilePicture: String
)

@Serializable
data class DriverRecordModel(
    @SerialName("driver_id") val driverId: String,
    @SerialName("dri_license") val driLicense: String? = null
)

@Serializable
data class DriverLicenseUpdate(
    @SerialName("dri_license") val driLicense: String
)

suspend fun processAndEncodeImageToHex(context: Context, imageUri: Uri): String = withContext(Dispatchers.IO) {
    val inputStream = context.contentResolver.openInputStream(imageUri)
        ?: throw IllegalStateException("Could not open image file")

    val originalBitmap = BitmapFactory.decodeStream(inputStream)
        ?: throw IllegalStateException("Could not decode image")

    val maxDimension = 500
    val ratio = minOf(
        maxDimension.toFloat() / originalBitmap.width,
        maxDimension.toFloat() / originalBitmap.height,
        1.0f
    )
    val scaledWidth = (originalBitmap.width * ratio).toInt()
    val scaledHeight = (originalBitmap.height * ratio).toInt()

    val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)
    val outputStream = ByteArrayOutputStream()
    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
    val bytes = outputStream.toByteArray()

    val hexBuilder = StringBuilder("\\x")
    for (b in bytes) {
        hexBuilder.append(String.format("%02x", b))
    }
    return@withContext hexBuilder.toString()
}

fun parseByteaToBitmap(data: String?): Bitmap? {
    if (data.isNullOrBlank()) return null
    return try {
        val byteArray = if (data.startsWith("\\x", ignoreCase = true)) {
            val hexString = data.substring(2)
            val len = hexString.length
            val bytes = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                bytes[i / 2] = ((Character.digit(hexString[i], 16) shl 4) + Character.digit(hexString[i + 1], 16)).toByte()
                i += 2
            }
            bytes
        } else {
            Base64.decode(data, Base64.DEFAULT)
        }
        BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    } catch (_: Exception) {
        null
    }
}

suspend fun uploadLicenseImage(
    context: Context,
    userId: String,
    imageUri: Uri
): String = withContext(Dispatchers.IO) {
    val inputStream = context.contentResolver.openInputStream(imageUri)
        ?: throw IllegalStateException("Could not read image file")
    val bytes = inputStream.use { it.readBytes() }
    val fileName = "$userId/license_${System.currentTimeMillis()}.jpg"

    val bucket = SupabaseInstance.client.storage.from("driver-licenses")
    bucket.upload(fileName, bytes) {
        upsert = true
    }
    return@withContext fileName
}

suspend fun fetchLicenseImageBytes(filePath: String): ByteArray = withContext(Dispatchers.IO) {
    val bucket = SupabaseInstance.client.storage.from("driver-licenses")
    return@withContext bucket.downloadAuthenticated(filePath)
}

@Composable
fun AccountScreen(
    onTabSelected: (String) -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val sharedPreferences = remember {
        context.getSharedPreferences("mwpc_driver_prefs", Context.MODE_PRIVATE)
    }

    var isLoadingProfile by remember { mutableStateOf(true) }
    var currentDriverUid by remember { mutableStateOf("") }

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var emailAddress by remember { mutableStateOf("") }
    var phoneNum by remember { mutableStateOf("") }

    var profilePicBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isUploadingAvatar by remember { mutableStateOf(false) }

    var driLicensePath by remember { mutableStateOf<String?>(null) }
    var isUploadingLicense by remember { mutableStateOf(false) }
    var showVerificationDialog by remember { mutableStateOf(false) }
    var tempLicensePhotoUri by remember { mutableStateOf<Uri?>(null) }

    var showImagePreviewDialog by remember { mutableStateOf(false) }
    var previewImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPreview by remember { mutableStateOf(false) }

    var showSignOutConfirmationDialog by remember { mutableStateOf(false) }
    var isSigningOut by remember { mutableStateOf(false) }

    // Persisted Notification Preferences
    var showNotificationDialog by remember { mutableStateOf(false) }
    var notifyNewDeliveries by remember {
        mutableStateOf(sharedPreferences.getBoolean("notify_new_deliveries", true))
    }
    var notifyDeliveryUpdates by remember {
        mutableStateOf(sharedPreferences.getBoolean("notify_delivery_updates", true))
    }
    var soundVibrationEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean("notify_sound_vibration", true))
    }

    val isLicenseVerified = !driLicensePath.isNullOrBlank()

    // Two-Factor Authentication Dialog (Fingerprint + MPIN)
    var showTwoFactorDialog by remember { mutableStateOf(false) }
    var showResetMpinDialog by remember { mutableStateOf(false) }

    var enableFingerprintSignIn by remember {
        mutableStateOf(sharedPreferences.getBoolean("enable_fingerprint_2fa", true))
    }

    fun handleLicenseUpload(uri: Uri) {
        coroutineScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isUploadingLicense = true }
            try {
                val currentUser = SupabaseInstance.client.auth.currentUserOrNull()
                    ?: throw IllegalStateException("Driver not authenticated")
                val userId = currentUser.id

                val storageFilePath = uploadLicenseImage(context, userId, uri)

                SupabaseInstance.client
                    .from("Driver")
                    .update(DriverLicenseUpdate(driLicense = storageFilePath)) {
                        filter { eq("driver_id", userId) }
                    }

                withContext(Dispatchers.Main) {
                    driLicensePath = storageFilePath
                    Toast.makeText(context, "License uploaded successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Upload failed: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) { isUploadingLicense = false }
            }
        }
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) { isUploadingAvatar = true }
                try {
                    val currentUser = SupabaseInstance.client.auth.currentUserOrNull()
                        ?: throw IllegalStateException("Driver not authenticated")
                    val userId = currentUser.id

                    val hexPayload = processAndEncodeImageToHex(context, uri)

                    SupabaseInstance.client
                        .from("User")
                        .update(ProfilePictureByteaUpdate(profilePicture = hexPayload)) {
                            filter { eq("uid", userId) }
                        }

                    val updatedBitmap = parseByteaToBitmap(hexPayload)

                    withContext(Dispatchers.Main) {
                        profilePicBitmap = updatedBitmap
                        Toast.makeText(context, "Profile picture saved!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to save: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) { isUploadingAvatar = false }
                }
            }
        }
    }

    val licenseGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            handleLicenseUpload(uri)
        }
    }

    val licenseCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = tempLicensePhotoUri
        if (success && uri != null) {
            handleLicenseUpload(uri)
        }
    }

    fun launchLicenseCamera() {
        try {
            val cacheImagesDir = File(context.cacheDir, "images").apply { mkdirs() }
            val tempFile = File.createTempFile("driver_license_", ".jpg", cacheImagesDir)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
            tempLicensePhotoUri = uri
            licenseCameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open camera: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    val licenseCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchLicenseCamera()
        } else {
            Toast.makeText(context, "Camera permission is required to capture license", Toast.LENGTH_LONG).show()
        }
    }

    fun requestLicenseCamera() {
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            launchLicenseCamera()
        } else {
            licenseCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun loadAndShowLicensePreview() {
        val path = driLicensePath ?: return
        showImagePreviewDialog = true
        isLoadingPreview = true
        previewImageBitmap = null

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val bytes = fetchLicenseImageBytes(path)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                withContext(Dispatchers.Main) {
                    previewImageBitmap = bitmap
                    isLoadingPreview = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoadingPreview = false
                    Toast.makeText(context, "Failed to load license photo: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val currentUser = SupabaseInstance.client.auth.currentUserOrNull()
                val userId = currentUser?.id

                if (userId != null) {
                    val profiles = SupabaseInstance.client
                        .from("User")
                        .select {
                            filter { eq("uid", userId) }
                        }
                        .decodeList<AccountProfileModel>()

                    val driverRecords = SupabaseInstance.client
                        .from("Driver")
                        .select {
                            filter { eq("driver_id", userId) }
                        }
                        .decodeList<DriverRecordModel>()

                    val user = profiles.firstOrNull()
                    val driver = driverRecords.firstOrNull()

                    val loadedBitmap = parseByteaToBitmap(user?.profilePicture)

                    withContext(Dispatchers.Main) {
                        currentDriverUid = userId
                        firstName = user?.firstName ?: "Driver"
                        lastName = user?.lastName ?: ""
                        emailAddress = user?.email ?: currentUser.email ?: ""
                        phoneNum = user?.phoneNumber ?: "Not provided"
                        profilePicBitmap = loadedBitmap
                        driLicensePath = driver?.driLicense
                        isLoadingProfile = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isLoadingProfile = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    firstName = "Driver"
                    lastName = ""
                    isLoadingProfile = false
                }
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
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Account Icon",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "My Profile",
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
                    AccountBottomNavItem(Icons.Default.Home, "Dashboard", false) { onTabSelected("Dashboard") }
                    AccountBottomNavItem(Icons.Default.LocationOn, "Tracker", false) { onTabSelected("Tracker") }
                    AccountBottomNavItem(Icons.Default.Assignment, "Reports", false) { onTabSelected("Reports") }
                    AccountBottomNavItem(Icons.Default.SupportAgent, "Support", false) { onTabSelected("Support") }
                    AccountBottomNavItem(Icons.Default.Person, "Account", true) { onTabSelected("Account") }
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
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF2E234B), Color(0xFF4A3B75))
                        )
                    )
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clickable(enabled = !isUploadingAvatar) {
                                    avatarPickerLauncher.launch("image/*")
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    isUploadingAvatar -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(28.dp),
                                            color = Color.White,
                                            strokeWidth = 3.dp
                                        )
                                    }
                                    profilePicBitmap != null -> {
                                        Image(
                                            bitmap = profilePicBitmap!!.asImageBitmap(),
                                            contentDescription = "Profile Picture",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(PrimaryPurple, CircleShape)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Change photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val headerTitle = when {
                                isLoadingProfile -> "Loading profile..."
                                lastName.isNotBlank() && firstName.isNotBlank() -> "$lastName, $firstName"
                                firstName.isNotBlank() -> firstName
                                else -> "Driver"
                            }

                            Text(
                                text = headerTitle,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = emailAddress.ifEmpty { "Driver Account" },
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = phoneNum,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    val licenseSubtitle = when {
                        isLoadingProfile -> "Checking status..."
                        isUploadingLicense -> "Uploading image..."
                        isLicenseVerified -> "License Attached & Submitted"
                        else -> "Tap to verify your license"
                    }

                    ProfileMenuItem(
                        icon = Icons.Default.VerifiedUser,
                        title = "Driver's License",
                        subtitle = licenseSubtitle,
                        badgeColor = if (isLicenseVerified) SuccessGreen else WarningOrange,
                        showWarning = !isLoadingProfile && !isLicenseVerified,
                        onClick = {
                            if (isLoadingProfile) return@ProfileMenuItem
                            showVerificationDialog = true
                        }
                    )

                    HorizontalDivider(color = BackgroundGray, thickness = 1.dp)

                    ProfileMenuItem(
                        icon = Icons.Default.Notifications,
                        title = "Notification Settings",
                        subtitle = "Alerts for new deliveries and status updates",
                        onClick = { showNotificationDialog = true }
                    )

                    HorizontalDivider(color = BackgroundGray, thickness = 1.dp)

                    val twoFactorSubtitle = if (enableFingerprintSignIn) {
                        "Fingerprint Biometrics & MPIN Enabled"
                    } else {
                        "Driver MPIN Keypad Only"
                    }

                    ProfileMenuItem(
                        icon = Icons.Default.Security,
                        title = "Two-Factor Authentication",
                        subtitle = twoFactorSubtitle,
                        onClick = { showTwoFactorDialog = true }
                    )

                    HorizontalDivider(color = BackgroundGray, thickness = 1.dp)

                    ProfileMenuItem(
                        icon = Icons.Default.Logout,
                        title = "Sign Out",
                        subtitle = "Log out from this device",
                        isDestructive = true,
                        onClick = {
                            showSignOutConfirmationDialog = true
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showSignOutConfirmationDialog) {
            Dialog(
                onDismissRequest = {
                    if (!isSigningOut) showSignOutConfirmationDialog = false
                }
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(Color(0xFFEF5350))
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE3F2FD)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF5A728A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = "Log out?",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0D47A1)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Are you sure you want to log out of your account?",
                                fontSize = 14.sp,
                                color = Color(0xFF2C243B),
                                fontWeight = FontWeight.Medium,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { showSignOutConfirmationDialog = false },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF0F2F5),
                                        contentColor = Color(0xFF2C243B)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    elevation = null,
                                    enabled = !isSigningOut
                                ) {
                                    Text(
                                        text = "Cancel",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Button(
                                    onClick = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            withContext(Dispatchers.Main) { isSigningOut = true }
                                            try {
                                                SupabaseInstance.client.auth.signOut()
                                            } catch (_: Exception) {}
                                            withContext(Dispatchers.Main) {
                                                isSigningOut = false
                                                showSignOutConfirmationDialog = false
                                                onSignOut()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFEF5350),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    elevation = null,
                                    enabled = !isSigningOut
                                ) {
                                    if (isSigningOut) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text(
                                            text = "Logout",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showVerificationDialog) {
            AlertDialog(
                onDismissRequest = { showVerificationDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = if (isLicenseVerified) "Driver's License on File" else "Verify Driver's License",
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (isLicenseVerified) {
                                "You already have a driver's license on file. You can preview it, take a new photo with your camera, or choose a file from your gallery."
                            } else {
                                "To ensure safety and compliance, please capture or upload a clear picture of your valid Driver's License."
                            },
                            fontSize = 14.sp,
                            color = SubtitleColor
                        )

                        if (isLicenseVerified) {
                            Text(
                                text = "Status: License record is active",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SuccessGreen
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        OutlinedButton(
                            onClick = {
                                showVerificationDialog = false
                                requestLicenseCamera()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isUploadingLicense
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = PrimaryPurple,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Take Photo with Camera", color = PrimaryPurple, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                showVerificationDialog = false
                                licenseGalleryLauncher.launch("image/*")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isUploadingLicense
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = PrimaryPurple,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose from Gallery", color = PrimaryPurple, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                confirmButton = {
                    if (isLicenseVerified) {
                        Button(
                            onClick = {
                                showVerificationDialog = false
                                loadAndShowLicensePreview()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("View Photo", color = Color.White)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showVerificationDialog = false }) {
                        Text(text = "Close", color = SubtitleColor)
                    }
                },
                containerColor = Color.White
            )
        }

        if (showImagePreviewDialog) {
            Dialog(onDismissRequest = { showImagePreviewDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Uploaded Driver's License",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(BackgroundGray, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                isLoadingPreview -> {
                                    CircularProgressIndicator(color = PrimaryPurple)
                                }
                                previewImageBitmap != null -> {
                                    Image(
                                        bitmap = previewImageBitmap!!.asImageBitmap(),
                                        contentDescription = "Driver License Preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                else -> {
                                    Text(
                                        text = "Unable to load image",
                                        color = SubtitleColor,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { showImagePreviewDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close", color = Color.White)
                        }
                    }
                }
            }
        }

        if (showNotificationDialog) {
            AlertDialog(
                onDismissRequest = { showNotificationDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Notification Settings",
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        NotificationToggleItem(
                            title = "New Cargo & Deliveries",
                            subtitle = "Alert when a new cargo run or parcel is assigned to you",
                            isChecked = notifyNewDeliveries,
                            onCheckedChange = { notifyNewDeliveries = it }
                        )

                        HorizontalDivider(color = BackgroundGray, thickness = 1.dp)

                        NotificationToggleItem(
                            title = "Status Updates & Cancellations",
                            subtitle = "Immediate notices when deliveries are changed or cancelled",
                            isChecked = notifyDeliveryUpdates,
                            onCheckedChange = { notifyDeliveryUpdates = it }
                        )

                        HorizontalDivider(color = BackgroundGray, thickness = 1.dp)

                        NotificationToggleItem(
                            title = "Sound & Vibration",
                            subtitle = "Loud audio tone and device vibration while driving",
                            isChecked = soundVibrationEnabled,
                            onCheckedChange = { soundVibrationEnabled = it }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            sharedPreferences.edit()
                                .putBoolean("notify_new_deliveries", notifyNewDeliveries)
                                .putBoolean("notify_delivery_updates", notifyDeliveryUpdates)
                                .putBoolean("notify_sound_vibration", soundVibrationEnabled)
                                .apply()

                            showNotificationDialog = false
                            Toast.makeText(context, "Preferences saved", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Done", color = Color.White)
                    }
                },
                containerColor = Color.White
            )
        }

        // Two-Factor Authentication Dialog
        if (showTwoFactorDialog) {
            AlertDialog(
                onDismissRequest = { showTwoFactorDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = "Two-Factor Authentication",
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Configure how you verify your identity during sign-in. Your 4-digit MPIN protects your account, and Fingerprint provides 1-tap access.",
                            fontSize = 13.sp,
                            color = SubtitleColor,
                            lineHeight = 18.sp
                        )

                        HorizontalDivider(color = BackgroundGray, thickness = 1.dp)

                        NotificationToggleItem(
                            title = "Fingerprint Quick-Unlock",
                            subtitle = "Automatically trigger fingerprint sensor on the MPIN screen",
                            isChecked = enableFingerprintSignIn,
                            onCheckedChange = { isChecked ->
                                enableFingerprintSignIn = isChecked
                                sharedPreferences.edit()
                                    .putBoolean("enable_fingerprint_2fa", isChecked)
                                    .apply()
                            }
                        )

                        HorizontalDivider(color = BackgroundGray, thickness = 1.dp)

                        OutlinedButton(
                            onClick = {
                                showTwoFactorDialog = false
                                showResetMpinDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = PrimaryPurple,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Change 4-Digit MPIN",
                                color = PrimaryPurple,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showTwoFactorDialog = false
                            Toast.makeText(context, "Security settings saved", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Done", color = Color.White)
                    }
                },
                containerColor = Color.White
            )
        }

        // Change/Reset MPIN Dialog
        if (showResetMpinDialog) {
            PinKeypadDialog(
                title = "Create New MPIN",
                subtitle = "Set a new 4-digit security PIN for this driver account",
                isSetupMode = true,
                onPinComplete = { newPin ->
                    val hashed = hashPin(newPin, currentDriverUid)
                    sharedPreferences.edit()
                        .putString("driver_pin_hash_$currentDriverUid", hashed)
                        .apply()
                    showResetMpinDialog = false
                    Toast.makeText(context, "New MPIN saved successfully!", Toast.LENGTH_SHORT).show()
                },
                onDismiss = {
                    showResetMpinDialog = false
                }
            )
        }
    }
}

@Composable
private fun NotificationToggleItem(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = SubtitleColor,
                lineHeight = 15.sp
            )
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryPurple,
                uncheckedThumbColor = SubtitleColor,
                uncheckedTrackColor = BackgroundGray
            )
        )
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeColor: Color = PrimaryPurple,
    showWarning: Boolean = false,
    isDestructive: Boolean = false,
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
                    .background(
                        if (isDestructive) Color(0xFFD32F2F).copy(alpha = 0.1f)
                        else PrimaryPurple.copy(alpha = 0.1f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isDestructive) Color(0xFFD32F2F) else PrimaryPurple,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDestructive) Color(0xFFD32F2F) else TextDark
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = SubtitleColor
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showWarning) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = WarningOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                tint = SubtitleColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AccountBottomNavItem(
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