package id.mtsrth.exam.ui.dashboard

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mtsrth.exam.ui.components.StatusPillBadge
import id.mtsrth.exam.ui.components.TonalPrestigeCard
import id.mtsrth.exam.ui.components.bounceClick
import id.mtsrth.exam.ui.theme.PrestigeTheme

@Composable
fun DashboardOverviewTab(
    uiState: DashboardUiState,
    onOpenSystemSettings: (String) -> Unit,
    onGoToExamsTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PrestigeTheme.colors
    val hw = uiState.hardwareStatus
    val haptic = LocalHapticFeedback.current
    var wasCompliant by remember { mutableStateOf(hw.isAllCompliant) }

    LaunchedEffect(hw.isAllCompliant) {
        if (!wasCompliant && hw.isAllCompliant) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasCompliant = hw.isAllCompliant
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DeviceReadinessHeroBanner(
                isAllCompliant = hw.isAllCompliant,
                onQuickFixClick = { onOpenSystemSettings(Settings.ACTION_SETTINGS) }
            )

            SectionHeader(
                title = "Pemeriksaan Perangkat Real-Time",
                subtitle = "Ketuk kartu untuk menyesuaikan pengaturan"
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BentoSensorCard(
                        title = "Mode Pesawat",
                        statusDetail = if (hw.isAirplaneOn) "Aktif" else "Nonaktif",
                        badgeText = if (hw.isAirplaneOn) "AMAN" else "WAJIB ON",
                        isCompliant = hw.isAirplaneOn,
                        icon = Icons.Rounded.AirplanemodeActive,
                        onClick = { onOpenSystemSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sensor_card_airplane")
                    )
                    BentoSensorCard(
                        title = "Koneksi Wi-Fi",
                        statusDetail = if (hw.isWifiOff) "Nonaktif" else "Aktif",
                        badgeText = if (hw.isWifiOff) "AMAN" else "WAJIB MATIKAN",
                        isCompliant = hw.isWifiOff,
                        icon = Icons.Rounded.WifiOff,
                        onClick = { onOpenSystemSettings(Settings.ACTION_WIFI_SETTINGS) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sensor_card_wifi")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BentoSensorCard(
                        title = "Bluetooth",
                        statusDetail = if (hw.isBluetoothOff) "Nonaktif" else "Aktif",
                        badgeText = if (hw.isBluetoothOff) "AMAN" else "WAJIB MATIKAN",
                        isCompliant = hw.isBluetoothOff,
                        icon = Icons.Rounded.BluetoothDisabled,
                        onClick = { onOpenSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sensor_card_bluetooth")
                    )
                    BentoSensorCard(
                        title = "Mode Suara",
                        statusDetail = if (hw.isRingerSilentOrVibrate) "Hening / Getar" else "Berdering",
                        badgeText = if (hw.isRingerSilentOrVibrate) "AMAN" else "WAJIB HENING",
                        isCompliant = hw.isRingerSilentOrVibrate,
                        icon = Icons.AutoMirrored.Rounded.VolumeOff,
                        onClick = { onOpenSystemSettings(Settings.ACTION_SOUND_SETTINGS) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sensor_card_ringer")
                    )
                }
            }

            SectionHeader(
                title = "Spesifikasi Perangkat Layak Ujian",
                subtitle = "Standar minimum perangkat peserta"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HardwareSpecCard(
                    label = "Sistem Operasi",
                    value = "Android ${hw.osRelease.ifBlank { "-" }}",
                    requirement = "Min. Android 8 Oreo",
                    isCompliant = hw.isAndroidVersionOk,
                    icon = Icons.Rounded.Android,
                    modifier = Modifier.weight(1f)
                )
                HardwareSpecCard(
                    label = "Kapasitas RAM",
                    value = "${hw.formattedRamGb} GB",
                    requirement = "Minimal RAM 2 GB",
                    isCompliant = hw.isRamOk,
                    icon = Icons.Rounded.Memory,
                    modifier = Modifier.weight(1f)
                )
            }

            val ctaBg = if (colors.isDark) Color(0xFF147A58) else colors.deepEmerald
            Button(
                onClick = onGoToExamsTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_go_to_exams")
                    .bounceClick(onClick = onGoToExamsTab),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ctaBg,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 18.dp)
            ) {
                Text(
                    text = "Buka Jadwal & Daftar Soal",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DeviceReadinessHeroBanner(
    isAllCompliant: Boolean,
    onQuickFixClick: () -> Unit
) {
    val colors = PrestigeTheme.colors
    val bgColor by animateColorAsState(
        targetValue = if (isAllCompliant) colors.vibrantTealSoftBg else colors.coralRedSoftBg,
        animationSpec = tween(350),
        label = "banner_bg"
    )
    val accentColor by animateColorAsState(
        targetValue = if (isAllCompliant) colors.deepEmerald else colors.coralRed,
        animationSpec = tween(350),
        label = "banner_accent"
    )
    val iconBgColor by animateColorAsState(
        targetValue = if (isAllCompliant) {
            colors.softMintContainer
        } else {
            colors.coralRedSoftBorder.copy(alpha = 0.7f)
        },
        animationSpec = tween(350),
        label = "banner_icon_bg"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "warning_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isAllCompliant) 1f else 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    TonalPrestigeCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("readiness_banner"),
        containerColor = bgColor,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isAllCompliant) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                        contentDescription = if (isAllCompliant) "Perangkat Siap" else "Peringatan Kesiapan",
                        tint = accentColor,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isAllCompliant) {
                            "Perangkat Siap Ujian"
                        } else {
                            "Pengaturan Belum Sesuai Standar"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = accentColor
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (isAllCompliant) {
                            "Seluruh sensor & keamanan perangkat memenuhi standar madrasah."
                        } else {
                            "Aktifkan Mode Pesawat & Mode Hening, serta matikan Wi-Fi & Bluetooth."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.slateSecondary
                    )
                }
            }

            AnimatedVisibility(
                visible = !isAllCompliant,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_quick_fix_settings")
                            .bounceClick(onClick = onQuickFixClick),
                        shape = RoundedCornerShape(12.dp),
                        color = colors.coralSolidAction
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Perbaiki Pengaturan Perangkat",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BentoSensorCard(
    title: String,
    statusDetail: String,
    badgeText: String,
    isCompliant: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PrestigeTheme.colors
    val cardBg by animateColorAsState(
        targetValue = if (isCompliant) colors.pureCrispWhite else colors.coralRedSoftBg,
        animationSpec = tween(300),
        label = "bento_bg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isCompliant) colors.vibrantTeal else colors.coralRed,
        animationSpec = tween(300),
        label = "bento_icon_tint"
    )
    val iconContainer by animateColorAsState(
        targetValue = if (isCompliant) {
            colors.vibrantTealSoftBg
        } else {
            colors.coralRedSoftBorder.copy(alpha = 0.55f)
        },
        animationSpec = tween(300),
        label = "bento_icon_bg"
    )
    val badgeBg = if (isCompliant) colors.vibrantTealSoftBg else colors.coralSolidAction
    val badgeFg = if (isCompliant) colors.deepEmerald else Color.White

    TonalPrestigeCard(
        modifier = modifier.bounceClick(onClick = onClick),
        containerColor = cardBg,
        shadowElevation = if (isCompliant) 2.dp else 1.dp,
        cornerRadius = 18.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                StatusPillBadge(
                    text = badgeText,
                    contentColor = badgeFg,
                    containerColor = badgeBg
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.inkSlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = statusDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCompliant) colors.deepEmerald else colors.coralRed,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HardwareSpecCard(
    label: String,
    value: String,
    requirement: String,
    isCompliant: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val colors = PrestigeTheme.colors
    val badgeBg = if (isCompliant) colors.vibrantTealSoftBg else colors.coralRedSoftBg
    val badgeFg = if (isCompliant) colors.deepEmerald else colors.coralRed

    TonalPrestigeCard(
        modifier = modifier,
        shadowElevation = 2.dp,
        cornerRadius = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.softMintContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = colors.deepEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                }
                StatusPillBadge(
                    text = if (isCompliant) "LAYAK" else "KURANG",
                    contentColor = badgeFg,
                    containerColor = badgeBg
                )
            }

            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.slateSecondary
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.inkSlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = requirement,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.slateMuted,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null
) {
    val colors = PrestigeTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.inkSlate
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.slateSecondary
            )
        }
    }
}
