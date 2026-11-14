package id.mtsrth.exam.ui.kiosk

import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.RecyclerView
import id.mtsrth.exam.R
import id.mtsrth.exam.ui.components.TonalPrestigeCard
import id.mtsrth.exam.ui.theme.JetBrainsMonoFontFamily
import id.mtsrth.exam.ui.theme.PrestigeTheme
import java.util.Locale

data class KioskUiState(
    val subjectName: String = "Ujian",
    val gradeLevel: Int = 7,
    val remainingSeconds: Long = 0L,
    val currentPage: Int = 1,
    val totalPages: Int = 0,
    val batteryPct: Int = 100,
    val isPinned: Boolean = true,
    val isBanActive: Boolean = false,
    val isPermanentLockout: Boolean = false,
    val violationDescription: String = "",
    val banRemainingSeconds: Long = 0L,
    val banStatusMessage: String? = null,
    val isDarkMode: Boolean = false,
    val isComfortMode: Boolean = false,
    val isPdfLoading: Boolean = true
)

@Composable
fun KioskExamScreen(
    uiState: KioskUiState,
    onAttachPdfViews: (ZoomableLayout, RecyclerView) -> Unit,
    onToggleComfortMode: () -> Unit,
    onOpenPageJumper: () -> Unit,
    onOpenSupervisorDialog: () -> Unit,
    onReactivatePin: () -> Unit
) {
    val colors = PrestigeTheme.colors
    val haptic = LocalHapticFeedback.current
    val shouldHideExamContent = uiState.isBanActive || !uiState.isPinned

    val canvasBackground by animateColorAsState(
        targetValue = when {
            uiState.isComfortMode -> Color(0xFF1F1B14)
            uiState.isDarkMode -> colors.coolAlabaster
            else -> Color(0xFFDFE5E3)
        },
        animationSpec = tween(250),
        label = "kiosk_canvas_bg"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            KioskTopStatusBar(
                uiState = uiState,
                onToggleComfortMode = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleComfortMode()
                },
                onOpenPageJumper = {
                    if (uiState.totalPages > 1) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenPageJumper()
                    }
                },
                onOpenSupervisorDialog = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpenSupervisorDialog()
                }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        val zoomable = ZoomableLayout(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                        val recycler = RecyclerView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            clipToPadding = false
                            setPadding(0, 12, 0, 24)
                        }
                        zoomable.addView(recycler)
                        onAttachPdfViews(zoomable, recycler)
                        zoomable
                    },
                    update = { zoomable ->
                        zoomable.visibility = if (shouldHideExamContent) View.INVISIBLE else View.VISIBLE
                    }
                )

                // Eye-comfort / night glare filter overlay (non-touch-blocking)
                if (!shouldHideExamContent && (uiState.isComfortMode || uiState.isDarkMode)) {
                    val filterColor = when {
                        uiState.isComfortMode && uiState.isDarkMode -> Color(0x3CD49B42)
                        uiState.isComfortMode -> Color(0x2EE6B86A)
                        else -> Color(0x1C08110E)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(filterColor)
                    )
                }

                if (uiState.isPdfLoading && !shouldHideExamContent) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = colors.deepEmerald,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Membuka soal terenkripsi...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (uiState.isComfortMode) Color(0xFFEBDDC3) else colors.inkSlate
                        )
                    }
                }
            }
        }

        // Overlay 1: Screen Pinning Kiosk Blocker
        AnimatedVisibility(
            visible = !uiState.isPinned && !uiState.isBanActive,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180))
        ) {
            KioskBlockerOverlay(onReactivatePin = onReactivatePin)
        }

        // Overlay 2: Violation Penalty Lockout
        AnimatedVisibility(
            visible = uiState.isBanActive,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180))
        ) {
            ViolationBanOverlay(
                uiState = uiState,
                onOpenSupervisorDialog = onOpenSupervisorDialog
            )
        }
    }
}

@Composable
private fun KioskTopStatusBar(
    uiState: KioskUiState,
    onToggleComfortMode: () -> Unit,
    onOpenPageJumper: () -> Unit,
    onOpenSupervisorDialog: () -> Unit
) {
    val colors = PrestigeTheme.colors
    val isUrgent = uiState.remainingSeconds in 1..300
    val isWarning = uiState.remainingSeconds in 301..900

    val pulseTransition = rememberInfiniteTransition(label = "kiosk_timer_pulse")
    val urgentAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isUrgent) 0.45f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "urgent_timer_alpha"
    )

    val timerBg = when {
        isUrgent -> colors.coralRedSoftBg
        isWarning -> colors.warmAmberSoftBg
        else -> colors.softMintContainer
    }
    val timerFg = when {
        isUrgent -> colors.coralRed
        isWarning -> colors.warmAmber
        else -> colors.deepEmerald
    }

    val hours = uiState.remainingSeconds / 3600
    val minutes = (uiState.remainingSeconds % 3600) / 60
    val seconds = uiState.remainingSeconds % 60
    val formattedTimer = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.pureCrispWhite,
        shadowElevation = if (colors.isDark) 0.dp else 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Subject Title + Comfort Mode + Supervisor Lock (48dp touch targets)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${uiState.subjectName} • Kls ${uiState.gradeLevel}",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.inkSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.vibrantTeal)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (uiState.isComfortMode) "Mode Kiosk • Nyaman Mata Aktif" else "Mode Kiosk Terkunci",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.slateSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onToggleComfortMode,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (uiState.isComfortMode) colors.warmAmberSoftBg else colors.tonalSlateSurface
                        )
                        .testTag("btn_toggle_comfort_mode")
                ) {
                    Icon(
                        imageVector = if (uiState.isComfortMode) Icons.Rounded.WbTwilight else Icons.Rounded.RemoveRedEye,
                        contentDescription = if (uiState.isComfortMode) "Matikan Mode Nyaman Mata" else "Aktifkan Mode Nyaman Mata",
                        tint = if (uiState.isComfortMode) colors.warmAmber else colors.slateSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onOpenSupervisorDialog,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.softMintContainer)
                        .testTag("btn_supervisor_lock")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AdminPanelSettings,
                        contentDescription = "Akses Pengawas Ujian",
                        tint = colors.deepEmerald,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            // Row 2: Countdown Timer + Tappable Page Pill + Battery
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(timerBg)
                        .graphicsLayer { alpha = urgentAlpha }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = "Sisa Waktu",
                        tint = timerFg,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formattedTimer,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = timerFg
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val pageText = if (uiState.totalPages > 0) {
                        "Hal ${uiState.currentPage} / ${uiState.totalPages}"
                    } else {
                        "Hal - / -"
                    }
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .testTag("btn_page_jumper")
                            .clickable(enabled = uiState.totalPages > 1, onClick = onOpenPageJumper),
                        shape = RoundedCornerShape(10.dp),
                        color = colors.tonalSlateSurface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MenuBook,
                                contentDescription = "Lompat Halaman",
                                tint = colors.deepEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = pageText,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = colors.inkSlate
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (uiState.batteryPct <= 15) colors.coralRedSoftBg else colors.tonalSlateSurface
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BatteryStd,
                            contentDescription = "Baterai",
                            tint = if (uiState.batteryPct <= 15) colors.coralRed else colors.slateSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${uiState.batteryPct}%",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = if (uiState.batteryPct <= 15) colors.coralRed else colors.inkSlate
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KioskBlockerOverlay(
    onReactivatePin: () -> Unit
) {
    val colors = PrestigeTheme.colors
    val ctaSolidBg = if (colors.isDark) Color(0xFF147A58) else colors.deepEmerald

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEB08120E))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {}
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        TonalPrestigeCard(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
            shadowElevation = 8.dp,
            cornerRadius = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.softMintContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = "Mode Kiosk",
                        tint = colors.deepEmerald,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Text(
                    text = "Kunci Layar Ujian Diperlukan",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.inkSlate,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Untuk menjaga integritas ujian, aktifkan kembali Penyematan Layar (Screen Pinning) agar lembar soal dapat ditampilkan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.slateSecondary,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onReactivatePin,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ctaSolidBg,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_reactivate_pin")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kunci Layar Sekarang",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun ViolationBanOverlay(
    uiState: KioskUiState,
    onOpenSupervisorDialog: () -> Unit
) {
    val colors = PrestigeTheme.colors

    val min = uiState.banRemainingSeconds / 60
    val sec = uiState.banRemainingSeconds % 60
    val banCountdownText = when {
        uiState.isPermanentLockout -> stringResource(id = R.string.msg_permanent_ban)
        uiState.banStatusMessage != null -> uiState.banStatusMessage
        else -> stringResource(id = R.string.countdown_ban, min, sec)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF51A090B))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {}
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        TonalPrestigeCard(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
            containerColor = colors.pureCrispWhite,
            shadowElevation = 10.dp,
            cornerRadius = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.coralRedSoftBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Penalti Terkunci",
                        tint = colors.coralRed,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Text(
                    text = "LAYAR TERKUNCI (PENALTI)",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.coralRed,
                    textAlign = TextAlign.Center
                )

                if (uiState.violationDescription.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.coralRedSoftBg)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = uiState.violationDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.inkSlate,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.coolAlabaster)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = banCountdownText,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (uiState.isPermanentLockout || uiState.banStatusMessage != null) 14.sp else 20.sp,
                        color = colors.coralRed,
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    text = "Timer ujian tetap berjalan di latar belakang. Hubungi Pengawas Ujian jika memerlukan otorisasi pembukaan kunci.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.slateSecondary,
                    textAlign = TextAlign.Center
                )

                OutlinedButton(
                    onClick = onOpenSupervisorDialog,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_supervisor_emergency")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AdminPanelSettings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Akses Pengawas Ujian")
                }
            }
        }
    }
}
