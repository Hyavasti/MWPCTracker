package com.example.mwpctracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.security.MessageDigest

private val PrimaryPurple = Color(0xFF2E234B)
private val TextDark = Color(0xFF2C243B)
private val SubtitleColor = Color(0xFF9E9AA7)
private val ErrorRed = Color(0xFFD32F2F)

fun hashPin(pin: String, salt: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest((salt + pin).toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

@Composable
fun PinKeypadDialog(
    title: String = "Enter Driver MPIN",
    subtitle: String = "Enter your PIN to continue",
    isSetupMode: Boolean = false,
    showFingerprint: Boolean = false,
    errorMessage: String? = null,
    onPinComplete: (String) -> Unit,
    onFingerprintClick: (() -> Unit)? = null,
    onForgotPinClick: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(PrimaryPurple.copy(alpha = 0.1f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = SubtitleColor,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 4-Dot Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        val isFilled = index < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) PrimaryPurple else Color(0xFFE2E4EB)
                                )
                        )
                    }
                }

                if (!errorMessage.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage,
                        color = ErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3x4 Keypad
                val keypadButtons = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("FP", "0", "DEL")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    keypadButtons.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row.forEach { key ->
                                when (key) {
                                    "FP" -> {
                                        if (showFingerprint && !isSetupMode && onFingerprintClick != null) {
                                            KeypadActionButton(onClick = onFingerprintClick) {
                                                Icon(
                                                    imageVector = Icons.Default.Fingerprint,
                                                    contentDescription = "Fingerprint shortcut",
                                                    tint = PrimaryPurple,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.size(64.dp))
                                        }
                                    }
                                    "DEL" -> {
                                        KeypadActionButton(
                                            onClick = {
                                                if (enteredPin.isNotEmpty()) {
                                                    enteredPin = enteredPin.dropLast(1)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "Backspace",
                                                tint = TextDark,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        KeypadNumberButton(
                                            digit = key,
                                            onClick = {
                                                if (enteredPin.length < 4) {
                                                    val newPin = enteredPin + key
                                                    enteredPin = newPin
                                                    if (newPin.length == 4) {
                                                        onPinComplete(newPin)
                                                        enteredPin = ""
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Forgot MPIN? & Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onForgotPinClick != null && !isSetupMode) {
                        TextButton(onClick = onForgotPinClick) {
                            Text(
                                text = "Forgot MPIN?",
                                color = PrimaryPurple,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Cancel",
                            color = SubtitleColor,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadNumberButton(digit: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        shape = CircleShape,
        color = Color(0xFFF4F5F9)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark
            )
        }
    }
}

@Composable
private fun KeypadActionButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        shape = CircleShape,
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}