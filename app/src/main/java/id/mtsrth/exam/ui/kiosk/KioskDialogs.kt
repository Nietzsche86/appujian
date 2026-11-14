package id.mtsrth.exam.ui.kiosk

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mtsrth.exam.ExamApp
import id.mtsrth.exam.R
import id.mtsrth.exam.data.sync.LocalSyncManager
import id.mtsrth.exam.ui.components.KeyboardAwareDialog
import id.mtsrth.exam.ui.theme.JetBrainsMonoFontFamily
import id.mtsrth.exam.ui.theme.PrestigeTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JumpToPageDialog(
    currentPage: Int,
    totalPages: Int,
    onJumpToPage: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = PrestigeTheme.colors
    val haptic = LocalHapticFeedback.current
    val safeTotal = totalPages.coerceAtLeast(1)
    var selectedPage by remember(currentPage, safeTotal) {
        mutableFloatStateOf(currentPage.coerceIn(1, safeTotal).toFloat())
    }
    val ctaSolidBg = if (colors.isDark) Color(0xFF147A58) else colors.deepEmerald

    KeyboardAwareDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.pureCrispWhite,
            shadowElevation = if (colors.isDark) 0.dp else 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.softMintContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = "Navigasi Halaman",
                            tint = colors.deepEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Lompat ke Halaman",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.inkSlate
                        )
                        Text(
                            text = "Total $safeTotal halaman lembar soal",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.slateSecondary
                        )
                    }
                }

                if (safeTotal > 1) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.coolAlabaster)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Halaman Pilihan",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.slateSecondary
                            )
                            Text(
                                text = "${selectedPage.roundToInt()} / $safeTotal",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = colors.deepEmerald
                            )
                        }

                        Slider(
                            value = selectedPage,
                            onValueChange = { selectedPage = it },
                            valueRange = 1f..safeTotal.toFloat(),
                            steps = (safeTotal - 2).coerceAtLeast(0),
                            colors = SliderDefaults.colors(
                                thumbColor = colors.deepEmerald,
                                activeTrackColor = colors.deepEmerald,
                                inactiveTrackColor = colors.tonalSlateSurface
                            )
                        )
                    }
                }

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (page in 1..safeTotal) {
                        val isSelected = selectedPage.roundToInt() == page
                        Surface(
                            modifier = Modifier
                                .size(width = 48.dp, height = 40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .testTag("jump_page_pill_$page")
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onJumpToPage(page)
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) ctaSolidBg else colors.tonalSlateSurface
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$page",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isSelected) Color.White else colors.inkSlate
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Tutup")
                    }
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onJumpToPage(selectedPage.roundToInt())
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ctaSolidBg,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("btn_confirm_jump_page")
                    ) {
                        Text("Buka Hal. ${selectedPage.roundToInt()}")
                    }
                }
            }
        }
    }
}

@Composable
fun SupervisorPinComposeDialog(
    onResetPenalty: () -> Unit,
    onFinishEarly: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = PrestigeTheme.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val ctaSolidBg = if (colors.isDark) Color(0xFF147A58) else colors.deepEmerald

    var pinText by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }
    var isVerified by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    KeyboardAwareDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.pureCrispWhite,
            shadowElevation = if (colors.isDark) 0.dp else 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isVerified) colors.vibrantTealSoftBg else colors.warmAmberSoftBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isVerified) Icons.Rounded.CheckCircle else Icons.Rounded.AdminPanelSettings,
                            contentDescription = "Akses Pengawas",
                            tint = if (isVerified) colors.deepEmerald else colors.warmAmber,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isVerified) "Panel Pengawas Ujian" else "Otorisasi Pengawas Ujian",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.inkSlate
                        )
                        Text(
                            text = if (isVerified) {
                                "PIN valid. Pilih tindakan pengendalian sesi."
                            } else {
                                "Masukkan Master PIN pengawas untuk membuka akses."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.slateSecondary
                        )
                    }
                }

                AnimatedContent(
                    targetState = isVerified,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "supervisor_pin_stage"
                ) { verified ->
                    if (!verified) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = pinText,
                                onValueChange = {
                                    pinText = it
                                    errorMessage = null
                                },
                                label = { Text("MASTER PIN PENGAWAS") },
                                placeholder = { Text("••••••") },
                                singleLine = true,
                                isError = errorMessage != null,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { keyboardController?.hide() }
                                ),
                                visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { pinVisible = !pinVisible }) {
                                        Icon(
                                            imageVector = if (pinVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = if (pinVisible) "Sembunyikan PIN" else "Tampilkan PIN"
                                        )
                                    }
                                },
                                textStyle = TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    letterSpacing = 3.sp,
                                    color = colors.inkSlate
                                ),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.deepEmerald,
                                    unfocusedBorderColor = colors.subtleDivider,
                                    focusedLabelColor = colors.deepEmerald
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_supervisor_pin")
                            )

                            if (errorMessage != null) {
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.coralRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("btn_cancel_supervisor_pin")
                                ) {
                                    Text("Batal")
                                }
                                Button(
                                    onClick = {
                                        val pin = pinText.trim()
                                        if (pin.isEmpty()) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            errorMessage = "Master PIN tidak boleh kosong"
                                            return@Button
                                        }
                                        val expectedHash = ExamApp.instance.secureStorage.masterPinHash
                                        val actualHash = LocalSyncManager.calculateSha256(pin)
                                        val valid = if (expectedHash.isNotBlank()) {
                                            actualHash.equals(expectedHash, ignoreCase = true)
                                        } else {
                                            pin == "888999"
                                        }
                                        if (valid) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            errorMessage = null
                                            isVerified = true
                                        } else {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            errorMessage = context.getString(R.string.error_wrong_pin)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ctaSolidBg,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(48.dp)
                                        .testTag("btn_verify_supervisor_pin")
                                ) {
                                    Text("Verifikasi PIN")
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onResetPenalty()
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.vibrantTealSoftBg,
                                    contentColor = colors.deepEmerald
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("btn_reset_penalty_action")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LockReset,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Reset Penalti & Lanjutkan Ujian",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onFinishEarly()
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.coralSolidAction,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("btn_finish_early_action")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Selesaikan Ujian Lebih Awal",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("Tutup")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmFinishComposeDialog(
    subjectName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = PrestigeTheme.colors

    KeyboardAwareDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.pureCrispWhite,
            shadowElevation = if (colors.isDark) 0.dp else 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.coralRedSoftBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = "Konfirmasi Selesai",
                            tint = colors.coralRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Akhiri Sesi Ujian?",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.inkSlate
                        )
                        Text(
                            text = subjectName,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.slateSecondary
                        )
                    }
                }

                Text(
                    text = "Soal $subjectName akan ditandai SELESAI dan tidak dapat dibuka kembali oleh siswa. Pastikan jawaban pada LJK sudah diperiksa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.slateSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_cancel_confirm_finish")
                    ) {
                        Text("Batal")
                    }
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.coralSolidAction,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("btn_confirm_finish_exam")
                    ) {
                        Text("Ya, Akhiri Ujian")
                    }
                }
            }
        }
    }
}
