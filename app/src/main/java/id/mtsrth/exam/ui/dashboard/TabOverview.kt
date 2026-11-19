package id.mtsrth.exam.ui.dashboard

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.mtsrth.exam.ui.components.StatusPillBadge
import id.mtsrth.exam.ui.components.TonalPrestigeCard
import id.mtsrth.exam.ui.components.bounceClick
import id.mtsrth.exam.ui.components.pressBounce
import id.mtsrth.exam.ui.theme.JetBrainsMonoFontFamily
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

    val sensorReadyCount = listOf(
        hw.isAirplaneOn,
        hw.isWifiOff,
        hw.isBluetoothOff,
        hw.isRingerSilentOrVibrate
    ).count { it }

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
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DeviceReadinessHeroBanner(
                isAllCompliant = hw.isAllCompliant,
                readySensorCount = sensorReadyCount,
                totalSensorCount = 4,
                onQuickFixClick = { onOpenSystemSettings(Settings.ACTION_SETTINGS) }
            )

            SectionHeader(
                title = "Pemeriksaan Perangkat Real-Time",
                subtitle = "Ketuk kartu sensor untuk langsung membuka pengaturan sistem"
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
                        badgeText = if (hw.isWifiOff) "AMAN" else "MATIKAN",
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
                        badgeText = if (hw.isBluetoothOff) "AMAN" else "MATIKAN",
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
                        badgeText = if (hw.isRingerSilentOrVibrate) "AMAN" else "HENINGKAN",
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
            val ctaInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = onGoToExamsTab,
                interactionSource = ctaInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .pressBounce(ctaInteraction)
                    .testTag("btn_go_to_exams"),
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
    readySensorCount: Int,
    totalSensorCount: Int,
    onQuickFixClick: () -> Unit
) {
    val colors = PrestigeTheme.colors
    val progress by animateFloatAsState(
        targetValue = readySensorCount.toFloat() / totalSensorCount.coerceAtLeast(1).toFloat(),
        animationSpec = tween(420),
        label = "readiness_progress"
    )

    val startColor by animateColorAsState(
        targetValue = if (isAllCompliant) colors.bannerGradientStart else Color(0xFF7F1D1D),
        animationSpec = tween(350),
        label = "hero_grad_start"
    )
    val endColor by animateColorAsState(
        targetValue = if (isAllCompliant) colors.bannerGradientEnd else Color(0xFFB91C1C),
        animationSpec = tween(350),
        label = "hero_grad_end"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "warning_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isAllCompliant) 1f else 1.09f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("readiness_banner"),
        shape = RoundedCornerShape(22.dp),
        shadowElevation = if (colors.isDark) 0.dp else 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(startColor, endColor)))
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = size.height * 0.72f,
                    center = Offset(size.width * 0.94f, size.height * 0.14f),
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = size.height * 0.45f,
                    center = Offset(size.width * 0.06f, size.height * 0.92f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAllCompliant) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                            contentDescription = if (isAllCompliant) "Perangkat Siap" else "Peringatan Kesiapan",
                            tint = Color.White,
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
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (isAllCompliant) {
                                "Seluruh sensor & keamanan perangkat memenuhi standar madrasah."
                            } else {
                                "Aktifkan Mode Pesawat & Mode Hening, serta matikan Wi-Fi & Bluetooth."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.86f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kesiapan Sensor Perangkat",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Text(
                        text = "$readySensorCount / $totalSensorCount AMAN",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.24f),
                    strokeCap = StrokeCap.Round
                )

                AnimatedVisibility(
                    visible = !isAllCompliant,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(14.dp))
                        val quickFixInteraction = remember { MutableInteractionSource() }
                        Button(
                            onClick = onQuickFixClick,
                            interactionSource = quickFixInteraction,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .pressBounce(quickFixInteraction)
                                .testTag("btn_quick_fix_settings"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFFB91C1C)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Buka Pengaturan Perangkat",
                                style = MaterialTheme.typography.labelLarge
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
    val cardBorder by animateColorAsState(
        targetValue = if (isCompliant) colors.cardBorder else colors.coralRedSoftBorder,
        animationSpec = tween(300),
        label = "bento_border"
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
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .bounceClick(onClick = onClick),
        containerColor = cardBg,
        borderColor = cardBorder,
        shadowElevation = if (isCompliant) 1.dp else 0.dp,
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
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
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

                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Ubah $title",
                    tint = if (isCompliant) colors.slateMuted else colors.coralRed,
                    modifier = Modifier.size(18.dp)
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
        shadowElevation = 1.dp,
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
                        .size(34.dp)
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
