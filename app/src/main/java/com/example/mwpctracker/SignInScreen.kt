@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)
package com.example.mwpctracker

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.IOException

// Color palette matching the MWPC branding
private val PrimaryPurple = Color(0xFF2E234B)
private val FieldBorderColor = Color(0xFFD6DBE5)
private val TextDark = Color(0xFF2C243B)
private val SubtitleColor = Color(0xFF9E9AA7)
private val BackgroundGray = Color(0xFFF7F8FC)
private val ErrorRed = Color(0xFFD32F2F)
private val SuccessGreen = Color(0xFF2E7D32)


@Serializable
data class EmailRpcParam(
    @SerialName("target_email") val targetEmail: String
)

@Serializable
data class LockoutStatusResponse(
    val status: String? = null,
    @SerialName("seconds_remaining") val secondsRemaining: Long? = 0L,
    val attempts: Int? = 0
)

@Serializable
data class SupabaseUserProfile(
    @SerialName("uid") val uid: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("role") val role: String? = null
)

@Serializable
data class SetMpinRpcParams(
    @SerialName("target_user_id") val targetUserId: String,
    @SerialName("raw_pin") val rawPin: String
)

@Serializable
data class VerifyMpinRpcParams(
    @SerialName("target_user_id") val targetUserId: String,
    @SerialName("test_pin") val testPin: String
)

@Serializable
data class CheckMpinRpcParams(
    @SerialName("target_user_id") val targetUserId: String
)

// Helper to grab the host FragmentActivity for the biometric dialog prompt
private fun Context.findFragmentActivity(): FragmentActivity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun SignInScreen(
    initialEmail: String = "",
    onSignInSuccess: (email: String) -> Unit = {}
) {
    val context = LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences("mwpc_driver_prefs", Context.MODE_PRIVATE)
    }

    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    // Retrieve saved user and device data
    val savedEmail = remember { sharedPreferences.getString("saved_email", "") ?: "" }
    val savedRememberMe = remember { sharedPreferences.getBoolean("remember_me", false) }
    val boundDriverUid = remember { sharedPreferences.getString("bound_driver_uid", null) }
    val boundDriverName = remember { sharedPreferences.getString("bound_driver_name", "Driver") ?: "Driver" }

    var email by remember(initialEmail) {
        mutableStateOf(if (initialEmail.isNotEmpty()) initialEmail else savedEmail)
    }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(savedRememberMe) }

    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    // Screen and dialog visibility states
    var isLoading by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorModalMessage by remember { mutableStateOf("") }
    var remainingAttemptsMessage by remember { mutableStateOf<String?>(null) }
    var signedInDriverName by remember { mutableStateOf(boundDriverName) }
    var currentDriverUid by remember { mutableStateOf(boundDriverUid ?: "") }

    // Flag to determine if the next successful password login should trigger an MPIN reset
    var isResettingMpinAfterPassword by remember { mutableStateOf(false) }

    var showSetupPinDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinErrorMessage by remember { mutableStateOf<String?>(null) }
    var mpinFailedCount by remember { mutableIntStateOf(0) }

    // Account lockout states
    var showLockedOutDialog by remember { mutableStateOf(false) }
    var showAccountDisabledDialog by remember { mutableStateOf(false) }
    var lockoutSecondsRemaining by remember { mutableLongStateOf(0L) }

    // Check if phone hardware supports fingerprint scanning
    val canUseBiometricHardware = remember {
        val biometricManager = BiometricManager.from(context)
        biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    val isFingerprintToggledOn = remember {
        sharedPreferences.getBoolean("enable_fingerprint_2fa", true)
    }
    val showFingerprintButton = canUseBiometricHardware && isFingerprintToggledOn

    // If driver already logged in on this phone and session exists, show MPIN pad directly on open
    LaunchedEffect(Unit) {
        if (!boundDriverUid.isNullOrBlank()) {
            currentDriverUid = boundDriverUid
            showPinDialog = true
        }
    }

    // Timer countdown for temporary account lockout
    LaunchedEffect(showLockedOutDialog, lockoutSecondsRemaining) {
        if (showLockedOutDialog && lockoutSecondsRemaining > 0) {
            delay(1000L)
            lockoutSecondsRemaining -= 1
            if (lockoutSecondsRemaining <= 0) {
                showLockedOutDialog = false
            }
        }
    }

    // Handles hardware fingerprint prompt when icon is tapped
    fun executeBiometricPrompt() {
        val activity = context.findFragmentActivity() ?: return
        val executor = ContextCompat.getMainExecutor(context)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Driver Verification")
            .setSubtitle("Confirm your identity with fingerprint")
            .setNegativeButtonText("Use MPIN")
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    mpinFailedCount = 0
                    pinErrorMessage = null
                    showPinDialog = false
                    showSuccessDialog = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(context, "Fingerprint not recognized. Try again or enter MPIN.", Toast.LENGTH_SHORT).show()
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }

    // Primary login handler for email and password authentication
    fun handleSignIn() {
        focusManager.clearFocus()
        val emailTrimmed = email.trim()
        val currentPassword = password

        emailError = when {
            emailTrimmed.isEmpty() -> "Please enter your email"
            !emailTrimmed.contains("@") || !emailTrimmed.contains(".") -> "Invalid email format"
            !emailTrimmed.lowercase().contains("maupay") -> "Must be a valid Maupay employee email"
            else -> null
        }

        passwordError = when {
            currentPassword.isEmpty() -> "Please enter your password"
            else -> null
        }

        if (emailError == null && passwordError == null) {
            isLoading = true

            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // Check database if driver account is currently suspended or on cooldown
                    val lockCheck = try {
                        SupabaseInstance.client.postgrest.rpc(
                            function = "check_driver_lockout",
                            parameters = EmailRpcParam(emailTrimmed)
                        ).decodeAs<LockoutStatusResponse>()
                    } catch (e: Exception) {
                        null
                    }

                    if (lockCheck != null) {
                        if (lockCheck.status == "DEACTIVATED") {
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                showAccountDisabledDialog = true
                            }
                            return@launch
                        }

                        if (lockCheck.status == "LOCKED_UNTIL" && (lockCheck.secondsRemaining ?: 0L) > 0) {
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                lockoutSecondsRemaining = lockCheck.secondsRemaining ?: 0L
                                showLockedOutDialog = true
                            }
                            return@launch
                        }
                    }

                    // Authenticate driver credentials with Supabase
                    try {
                        SupabaseInstance.client.auth.signInWith(Email) {
                            this.email = emailTrimmed
                            this.password = currentPassword
                        }
                    } catch (authError: Exception) {
                        val attemptResult = try {
                            SupabaseInstance.client.postgrest.rpc(
                                function = "record_failed_attempt",
                                parameters = EmailRpcParam(emailTrimmed)
                            ).decodeAs<LockoutStatusResponse>()
                        } catch (e: Exception) {
                            null
                        }

                        withContext(Dispatchers.Main) {
                            isLoading = false
                            val attempts = attemptResult?.attempts ?: 1

                            when (attemptResult?.status) {
                                "DEACTIVATED" -> {
                                    showAccountDisabledDialog = true
                                }
                                "LOCKED_UNTIL" -> {
                                    lockoutSecondsRemaining = attemptResult.secondsRemaining ?: 60L
                                    showLockedOutDialog = true
                                }
                                else -> {
                                    val remainingBeforeLock = if (attempts < 5) 5 - attempts else 10 - attempts
                                    val phaseText = if (attempts < 5) {
                                        "Attempts remaining before 1 min lockout: $remainingBeforeLock / 5"
                                    } else {
                                        "Attempts remaining before account suspension: $remainingBeforeLock / 5"
                                    }

                                    errorModalMessage = "Incorrect email or password.\nPlease try again."
                                    remainingAttemptsMessage = phaseText
                                    showErrorDialog = true
                                }
                            }
                        }
                        return@launch
                    }

                    // Password passed: reset failed login attempts counter
                    try {
                        SupabaseInstance.client.postgrest.rpc(
                            function = "reset_failed_attempts",
                            parameters = EmailRpcParam(emailTrimmed)
                        )
                    } catch (e: Exception) {
                        Log.e("MWPC_RPC", "Failed to reset login attempts: ${e.message}")
                    }

                    val currentUser = SupabaseInstance.client.auth.currentUserOrNull()
                    val userId = currentUser?.id ?: throw IllegalStateException("Driver not authenticated")

                    val userProfiles = try {
                        SupabaseInstance.client
                            .from("User")
                            .select { filter { eq("uid", userId) } }
                            .decodeList<SupabaseUserProfile>()
                    } catch (e: Exception) {
                        emptyList()
                    }

                    val matchedUser = userProfiles.firstOrNull()
                    val userRole = matchedUser?.role?.trim()?.lowercase()

                    // Ensure account belongs strictly to driver role
                    if (userRole != "driver") {
                        try { SupabaseInstance.client.auth.signOut() } catch (_: Exception) {}

                        withContext(Dispatchers.Main) {
                            isLoading = false
                            errorModalMessage = "Unauthorized role. Access is restricted to drivers."
                            remainingAttemptsMessage = null
                            showErrorDialog = true
                        }
                        return@launch
                    }

                    val fName = matchedUser?.firstName ?: "Driver"
                    val lName = matchedUser?.lastName ?: ""
                    val fullName = "$fName $lName".trim()

                    // Save session details to local preferences for device binding
                    with(sharedPreferences.edit()) {
                        if (rememberMe) {
                            putString("saved_email", emailTrimmed)
                            putBoolean("remember_me", true)
                        } else {
                            remove("saved_email")
                            putBoolean("remember_me", false)
                        }
                        putString("bound_driver_uid", userId)
                        putString("bound_driver_name", fullName)
                        apply()
                    }

                    // Check if driver has already created an MPIN in Supabase
                    val hasMpin = try {
                        SupabaseInstance.client.postgrest.rpc(
                            function = "has_driver_mpin",
                            parameters = CheckMpinRpcParams(targetUserId = userId)
                        ).decodeAs<Boolean>()
                    } catch (e: Exception) {
                        false
                    }

                    withContext(Dispatchers.Main) {
                        isLoading = false
                        currentDriverUid = userId
                        signedInDriverName = if (fullName.isNotEmpty()) fullName else "Driver"
                        mpinFailedCount = 0
                        pinErrorMessage = null

                        // If new driver OR driver logged in after forgetting MPIN, force set a new MPIN
                        if (!hasMpin || isResettingMpinAfterPassword) {
                            isResettingMpinAfterPassword = false
                            showPinDialog = false
                            showSetupPinDialog = true
                        } else {
                            showPinDialog = true
                        }
                    }
                } catch (e: RestException) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        errorModalMessage = if (e.statusCode == 400 || e.statusCode == 401) {
                            "Incorrect email or password.\nPlease try again."
                        } else {
                            "Server error (${e.statusCode}). Please try again."
                        }
                        remainingAttemptsMessage = null
                        showErrorDialog = true
                    }
                } catch (e: IOException) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        errorModalMessage = "Connection timed out. Please check your internet."
                        remainingAttemptsMessage = null
                        showErrorDialog = true
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        errorModalMessage = "Sign-in could not be completed. Please try again."
                        remainingAttemptsMessage = null
                        showErrorDialog = true
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGray)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.bg),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0x991E2A4A), Color(0xDD2E234B))
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "MWPC Logo",
                        modifier = Modifier.size(90.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "MWPC Tracker",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Maupay Waraynon Driver Portal",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-24).dp)
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Welcome Back!",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Text(
                        text = "Please sign in to continue",
                        fontSize = 12.sp,
                        color = SubtitleColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    SignInOutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it.filter { char -> !char.isWhitespace() }
                            if (emailError != null) emailError = null
                        },
                        placeholder = "Email Address",
                        leadingIcon = Icons.Default.Email,
                        keyboardType = KeyboardType.Email,
                        isError = emailError != null,
                        errorMessage = emailError,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
                    )

                    SignInOutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it.filter { char -> !char.isWhitespace() }
                            if (passwordError != null) passwordError = null
                        },
                        placeholder = "Password",
                        leadingIcon = Icons.Default.VpnKey,
                        isPassword = true,
                        passwordVisible = passwordVisible,
                        onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
                        isError = passwordError != null,
                        errorMessage = passwordError,
                        imeAction = ImeAction.Done,
                        onImeAction = {
                            focusManager.clearFocus()
                            handleSignIn()
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { rememberMe = !rememberMe },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = PrimaryPurple,
                                uncheckedColor = FieldBorderColor,
                                checkmarkColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Remember Me",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextDark
                        )
                    }

                    Button(
                        onClick = { handleSignIn() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                    ) {
                        Text(
                            text = "Sign In",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Loading indicator dialog
        if (isLoading) {
            Dialog(onDismissRequest = {}) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = PrimaryPurple,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Signing in...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextDark
                        )
                    }
                }
            }
        }

        // Setup MPIN dialog for initial setup or after recovery
        if (showSetupPinDialog) {
            PinKeypadDialog(
                title = "Create Driver MPIN",
                subtitle = "Set a 4-digit PIN for quick daily sign-in",
                isSetupMode = true,
                showFingerprint = false,
                onPinComplete = { newPin ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val success = SupabaseInstance.client.postgrest.rpc(
                                function = "set_driver_mpin",
                                parameters = SetMpinRpcParams(
                                    targetUserId = currentDriverUid,
                                    rawPin = newPin
                                )
                            ).decodeAs<Boolean>()

                            withContext(Dispatchers.Main) {
                                if (success) {
                                    showSetupPinDialog = false
                                    Toast.makeText(context, "MPIN saved successfully!", Toast.LENGTH_SHORT).show()
                                    showSuccessDialog = true
                                } else {
                                    Toast.makeText(context, "Failed to set MPIN. Please try again.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onDismiss = {
                    showSetupPinDialog = false
                    coroutineScope.launch(Dispatchers.IO) {
                        try { SupabaseInstance.client.auth.signOut() } catch (_: Exception) {}
                    }
                }
            )
        }

        // Active MPIN input keypad
        if (showPinDialog) {
            PinKeypadDialog(
                title = "Enter MPIN",
                subtitle = "Welcome back, $signedInDriverName!\nEnter your 4-digit security PIN to continue",
                showFingerprint = showFingerprintButton,
                errorMessage = pinErrorMessage,
                onPinComplete = { enteredPin ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val isValid = SupabaseInstance.client.postgrest.rpc(
                                function = "verify_driver_mpin",
                                parameters = VerifyMpinRpcParams(
                                    targetUserId = currentDriverUid,
                                    testPin = enteredPin
                                )
                            ).decodeAs<Boolean>()

                            withContext(Dispatchers.Main) {
                                if (isValid) {
                                    mpinFailedCount = 0
                                    pinErrorMessage = null
                                    showPinDialog = false
                                    showSuccessDialog = true
                                } else {
                                    mpinFailedCount += 1
                                    val remainingTries = 3 - mpinFailedCount

                                    if (remainingTries > 0) {
                                        pinErrorMessage = "Incorrect MPIN. $remainingTries attempt(s) remaining."
                                    } else {
                                        // 3 Failed Attempts: Log out and route to password screen to reset MPIN
                                        showPinDialog = false
                                        mpinFailedCount = 0
                                        pinErrorMessage = null
                                        password = ""
                                        isResettingMpinAfterPassword = true

                                        coroutineScope.launch(Dispatchers.IO) {
                                            try { SupabaseInstance.client.auth.signOut() } catch (_: Exception) {}
                                        }

                                        errorModalMessage = "Too many incorrect MPIN attempts.\nPlease sign in with your password to set a new MPIN."
                                        remainingAttemptsMessage = null
                                        showErrorDialog = true
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                pinErrorMessage = "Verification error. Please retry."
                            }
                        }
                    }
                },
                onFingerprintClick = if (showFingerprintButton) {
                    { executeBiometricPrompt() }
                } else null,
                onForgotPinClick = {
                    // Manual Forgot MPIN flow: forces password re-verification
                    showPinDialog = false
                    mpinFailedCount = 0
                    pinErrorMessage = null
                    password = ""
                    isResettingMpinAfterPassword = true

                    coroutineScope.launch(Dispatchers.IO) {
                        try { SupabaseInstance.client.auth.signOut() } catch (_: Exception) {}
                    }

                    Toast.makeText(context, "Please sign in with your password to set a new MPIN.", Toast.LENGTH_LONG).show()
                },
                onDismiss = {
                    showPinDialog = false
                    pinErrorMessage = null
                    mpinFailedCount = 0
                    password = ""
                    coroutineScope.launch(Dispatchers.IO) {
                        try { SupabaseInstance.client.auth.signOut() } catch (_: Exception) {}
                    }
                }
            )
        }

        // Login success confirmation dialog
        if (showSuccessDialog) {
            Dialog(onDismissRequest = {}) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(SuccessGreen.copy(alpha = 0.12f), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = SuccessGreen,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Sign In Successful!",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Welcome back,\n$signedInDriverName!",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = SubtitleColor,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                showSuccessDialog = false
                                onSignInSuccess(email.trim())
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                        ) {
                            Text(
                                text = "Continue to Dashboard",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Temporary 1-minute lockout dialog
        if (showLockedOutDialog) {
            val minutes = lockoutSecondsRemaining / 60
            val seconds = lockoutSecondsRemaining % 60
            val timeFormatted = String.format("%02d:%02d", minutes, seconds)

            Dialog(onDismissRequest = { showLockedOutDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .background(Color(0xFFFFEBEE), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Error",
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Something went wrong!",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E56A0),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Too many failed login attempts. Please try again in 1 minute.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF333333),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = timeFormatted,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF5350)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { showLockedOutDialog = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                        ) {
                            Text(
                                text = "Close",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Account deactivated dialog after maximum attempts exceeded
        if (showAccountDisabledDialog) {
            Dialog(onDismissRequest = {}) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(ErrorRed.copy(alpha = 0.12f), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Account Disabled",
                                tint = ErrorRed,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Account Locked",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "This account has been deactivated. Please contact support.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = SubtitleColor,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { showAccountDisabledDialog = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                        ) {
                            Text(
                                text = "Close",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Generic error dialog
        if (showErrorDialog) {
            Dialog(onDismissRequest = { showErrorDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(ErrorRed.copy(alpha = 0.12f), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Failed",
                                tint = ErrorRed,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Sign In Failed!",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = errorModalMessage,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = SubtitleColor,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )

                        if (!remainingAttemptsMessage.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ErrorRed.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = remainingAttemptsMessage!!,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ErrorRed,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { showErrorDialog = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                        ) {
                            Text(
                                text = "Try Again",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

// Reusable text input field with error and visibility toggles
@Composable
fun SignInOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePasswordVisibility: () -> Unit = {},
    isError: Boolean,
    errorMessage: String?,
    imeAction: ImeAction,
    onImeAction: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            singleLine = true,
            isError = isError,
            placeholder = {
                Text(text = placeholder, color = SubtitleColor, fontSize = 13.sp)
            },
            leadingIcon = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (isError) ErrorRed else PrimaryPurple,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (isPassword) {
                    IconButton(onClick = onTogglePasswordVisibility) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle visibility",
                            tint = if (isError) ErrorRed else PrimaryPurple,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryPurple,
                unfocusedBorderColor = FieldBorderColor,
                errorBorderColor = ErrorRed,
                focusedTextColor = TextDark,
                unfocusedTextColor = TextDark,
                errorTextColor = TextDark,
                cursorColor = PrimaryPurple
            )
        )

        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                color = ErrorRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 16.dp, top = 3.dp)
            )
        }
    }
}