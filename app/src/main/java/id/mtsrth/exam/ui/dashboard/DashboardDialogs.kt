package id.mtsrth.exam.ui.dashboard

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import id.mtsrth.exam.R
import id.mtsrth.exam.data.crypto.CryptoEngine
import id.mtsrth.exam.data.model.ExamItem
import id.mtsrth.exam.data.sync.LocalSyncManager
import id.mtsrth.exam.ui.theme.JetBrainsMonoFontFamily
import id.mtsrth.exam.ui.theme.PrestigeTheme
import id.mtsrth.exam.util.ExamScheduleHelper
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun DeviceRequirementComposeDialog(
    hardwareStatus: HardwareStatusState,
    onOpenSettings: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = PrestigeTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.pureCrispWhite,
            shadowElevation = if (colors.isDark) 0.dp else 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            contentDescription = "Persyaratan Perangkat",
                            tint = colors.coralRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Kesiapan Perangkat",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.inkSlate
                        )
                        Text(
                            text = "Penuhi semua syarat sebelum memulai ujian",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.slateSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                RequirementCheckRow(
                    icon = Icons.Rounded.AirplanemodeActive,
                    label = if (hardwareStatus.isAirplaneOn) "Mode Pesawat: Aktif (Aman)" else "Mode Pesawat: Nonaktif (Wajib ON!)",
                    isOk = hardwareStatus.isAirplaneOn
                )
                RequirementCheckRow(
                    icon = Icons.Rounded.WifiOff,
                    label = if (hardwareStatus.isWifiOff) "Wi-Fi: Nonaktif (Aman)" else "Wi-Fi: Aktif (Wajib Mati!)",
                    isOk = hardwareStatus.isWifiOff
                )
                RequirementCheckRow(
                    icon = Icons.Rounded.BluetoothDisabled,
                    label = if (hardwareStatus.isBluetoothOff) "Bluetooth: Nonaktif (Aman)" else "Bluetooth: Aktif (Wajib Mati!)",
                    isOk = hardwareStatus.isBluetoothOff
                )
                RequirementCheckRow(
                    icon = Icons.Rounded.VolumeOff,
                    label = if (hardwareStatus.isRingerSilentOrVibrate) "Mode Suara: Hening/Getar (Aman)" else "Mode Suara: Berdering (Wajib Hening!)",
                    isOk = hardwareStatus.isRingerSilentOrVibrate
                )
                RequirementCheckRow(
                    icon = Icons.Rounded.Android,
                    label = "OS: Android ${hardwareStatus.osRelease} ${if (hardwareStatus.isAndroidVersionOk) "(Aman)" else "(Min. Android 8)"}",
                    isOk = hardwareStatus.isAndroidVersionOk
                )
                RequirementCheckRow(
                    icon = Icons.Rounded.Memory,
                    label = "RAM: ${hardwareStatus.formattedRamGb} GB ${if (hardwareStatus.isRamOk) "(Aman)" else "(Min. 2 GB)"}",
                    isOk = hardwareStatus.isRamOk
                )

                Spacer(modifier = Modifier.height(4.dp))

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
                        onClick = { onOpenSettings(Settings.ACTION_SETTINGS) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.deepEmerald,
                            contentColor = if (colors.isDark) Color(0xFF05241A) else Color.White
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("btn_dialog_open_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pengaturan")
                    }
                }
            }
        }
    }
}

@Composable
private fun RequirementCheckRow(
    icon: ImageVector,
    label: String,
    isOk: Boolean
) {
    val colors = PrestigeTheme.colors
    val bg = if (isOk) colors.vibrantTealSoftBg else colors.coralRedSoftBg
    val fg = if (isOk) colors.deepEmerald else colors.coralRed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (isOk) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun TokenInputComposeDialog(
    exam: ExamItem,
    onDismiss: () -> Unit,
    onTokenVerified: (String) -> Unit
) {
    val colors = PrestigeTheme.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var tokenText by remember { mutableStateOf("") }
    var isDecrypting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val totalMinutes = ExamScheduleHelper.getTotalDurationMinutes(exam)

    Dialog(onDismissRequest = { if (!isDecrypting) onDismiss() }) {
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
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.softMintContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LockOpen,
                            contentDescription = "Token Ujian",
                            tint = colors.deepEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Masukkan Token Ujian",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.inkSlate
                        )
                        Text(
                            text = "${exam.subjectName} (Kelas ${exam.gradeLevel}) • $totalMinutes Menit",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.slateSecondary
                        )
                    }
                }

                Text(
                    text = "Masukkan token dari pengawas untuk mendekripsi paket soal secara aman.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.slateSecondary
                )

                OutlinedTextField(
                    value = tokenText,
                    onValueChange = {
                        tokenText = it
                        errorMessage = null
                    },
                    label = { Text("TOKEN UJIAN") },
                    placeholder = { Text("Contoh: 123456") },
                    singleLine = true,
                    enabled = !isDecrypting,
                    isError = errorMessage != null,
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 2.sp,
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
                        .testTag("input_exam_token")
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
                        enabled = !isDecrypting,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_cancel_token")
                    ) {
                        Text("Batal")
                    }
                    Button(
                        onClick = {
                            val trimmed = tokenText.trim()
                            if (trimmed.isEmpty()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                errorMessage = "Token tidak boleh kosong"
                                return@Button
                            }
                            isDecrypting = true
                            errorMessage = null
                            scope.launch {
                                val syncManager = LocalSyncManager(context)
                                val cryptoEngine = CryptoEngine(context)
                                val encFile = syncManager.getExamFile(exam.id)

                                if (!encFile.exists()) {
                                    isDecrypting = false
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    errorMessage = "Berkas soal terenkripsi belum diunduh!"
                                    return@launch
                                }

                                var resolvedToken = trimmed
                                var result = cryptoEngine.decryptToUnlinkedPfd(
                                    encryptedFile = encFile,
                                    token = resolvedToken,
                                    saltHex = exam.pbkdf2SaltHex,
                                    ivHex = exam.gcmIvHex
                                )
                                val upper = trimmed.uppercase(Locale.ROOT)
                                if (result.isFailure && upper != trimmed) {
                                    val upperResult = cryptoEngine.decryptToUnlinkedPfd(
                                        encryptedFile = encFile,
                                        token = upper,
                                        saltHex = exam.pbkdf2SaltHex,
                                        ivHex = exam.gcmIvHex
                                    )
                                    if (upperResult.isSuccess) {
                                        resolvedToken = upper
                                        result = upperResult
                                    }
                                }
                                isDecrypting = false

                                result.onSuccess { session ->
                                    runCatching { session.pfd.close() }
                                    runCatching { session.tempFile.delete() }
                                    onTokenVerified(resolvedToken)
                                }.onFailure { err ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    errorMessage = if (err.localizedMessage?.contains("PDF") == true) {
                                        err.localizedMessage ?: context.getString(R.string.error_invalid_token)
                                    } else {
                                        context.getString(R.string.error_invalid_token)
                                    }
                                }
                            }
                        },
                        enabled = !isDecrypting,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.deepEmerald,
                            contentColor = if (colors.isDark) Color(0xFF05241A) else Color.White
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("btn_submit_token")
                    ) {
                        if (isDecrypting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = if (colors.isDark) Color(0xFF05241A) else Color.White
                            )
                        } else {
                            Text("Buka Soal")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GradeSwitchConfirmationDialog(
    targetGrade: Int,
    downloadedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = PrestigeTheme.colors

    Dialog(onDismissRequest = onDismiss) {
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
                            .background(colors.warmAmberSoftBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = "Konfirmasi Ganti Kelas",
                            tint = colors.warmAmber,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Pindah ke Kelas $targetGrade?",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.inkSlate
                    )
                }

                Text(
                    text = "Saat ini tersimpan $downloadedCount paket soal offline. Berkas soal kelas sebelumnya akan diganti ketika Anda menekan \"Sinkronkan Soal\" untuk Kelas $targetGrade.",
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
                    ) {
                        Text("Batal")
                    }
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (colors.isDark) Color(0xFF147A58) else colors.deepEmerald,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                    ) {
                        Text("Ya, Pindah")
                    }
                }
            }
        }
    }
}
