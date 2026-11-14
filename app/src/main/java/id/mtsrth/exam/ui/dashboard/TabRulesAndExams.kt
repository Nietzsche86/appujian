package id.mtsrth.exam.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mtsrth.exam.data.model.ExamItemUiModel
import id.mtsrth.exam.data.model.ExamStatus
import id.mtsrth.exam.ui.components.ExamStatsRingChart
import id.mtsrth.exam.ui.components.ShimmerExamSkeletonList
import id.mtsrth.exam.ui.components.StatusPillBadge
import id.mtsrth.exam.ui.components.TonalPrestigeCard
import id.mtsrth.exam.ui.components.bounceClick
import id.mtsrth.exam.ui.theme.JetBrainsMonoFontFamily
import id.mtsrth.exam.ui.theme.PrestigeTheme
import id.mtsrth.exam.util.ExamScheduleHelper

private val DEFAULT_EXAM_RULES = listOf(
    "Siswa wajib memasuki ruang ujian 15 menit sebelum ujian dimulai.",
    "Perangkat Android WAJIB dalam Mode Pesawat, Mode Hening, dan Wi-Fi/Bluetooth mati.",
    "Aplikasi mengunci layar secara otomatis (Mode Kiosk). Dilarang keras mencoba keluar.",
    "Percobaan kecurangan (membuka aplikasi lain, reboot HP, atau swipe keluar) memicu PENALTI KUNCI LAYAR BERJENJANG.",
    "Waktu ujian tetap berjalan normal di latar belakang selama masa penalti.",
    "Soal ujian akan otomatis terkunci atau selesai ketika waktu jam ujian berakhir.",
    "Jawaban wajib disalin ke Lembar Jawaban Komputer (LJK) fisik yang disediakan pengawas.",
    "Jika ada kendala teknis atau baterai lemah, segera angkat tangan dan hubungi Pengawas Ujian.",
    "Pengawas memiliki Master PIN darurat untuk membuka penalti ban jika diperlukan.",
    "Berdoalah sebelum mengerjakan soal ujian. Kejujuran adalah cerminan akhlak mulia."
)

@Composable
fun RulesGuideTab(
    rules: List<String>,
    rulesAcknowledged: Boolean,
    onToggleAcknowledged: (Boolean) -> Unit,
    onGoToExamsTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PrestigeTheme.colors
    val haptic = LocalHapticFeedback.current
    val activeRules = rules.ifEmpty { DEFAULT_EXAM_RULES }

    val crucialIndices = activeRules.mapIndexedNotNull { index, text ->
        val lower = text.lowercase()
        if (lower.contains("kiosk") || lower.contains("penalti") || lower.contains("kecurangan")) {
            index
        } else {
            null
        }
    }.toSet()

    val confirmBg by animateColorAsState(
        targetValue = if (rulesAcknowledged) colors.vibrantTealSoftBg else colors.pureCrispWhite,
        animationSpec = tween(300),
        label = "rules_confirm_bg"
    )
    val ctaSolidBg = if (colors.isDark) Color(0xFF147A58) else colors.deepEmerald

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TonalPrestigeCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = colors.softMintContainer,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(ctaSolidBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Gavel,
                            contentDescription = "Tata Tertib",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tata Tertib & Petunjuk Ujian",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.darkForest
                        )
                        Text(
                            text = "Pahami aturan krusial sebelum membuka paket soal terenkripsi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.deepEmerald
                        )
                    }
                }
            }

            if (crucialIndices.isNotEmpty()) {
                SectionHeader(
                    title = "Sorotan Aturan Krusial",
                    subtitle = "Pelanggaran memicu sistem penguncian otomatis"
                )

                crucialIndices.forEach { idx ->
                    val ruleText = activeRules[idx]
                    val isPenalty = ruleText.lowercase().contains("penalti") || ruleText.lowercase().contains("kecurangan")
                    val accent = if (isPenalty) colors.coralRed else colors.warmAmber
                    val badgeBg = if (isPenalty) colors.coralSolidAction else Color(0xFFD97706)
                    val cardBg = if (isPenalty) colors.coralRedSoftBg else colors.warmAmberSoftBg
                    val icon = if (isPenalty) Icons.Rounded.Security else Icons.Rounded.Lock

                    TonalPrestigeCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = cardBg,
                        shadowElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = "Aturan Krusial",
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                StatusPillBadge(
                                    text = "POIN #${idx + 1} • KRUSIAL",
                                    contentColor = Color.White,
                                    containerColor = badgeBg
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = ruleText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.inkSlate,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            SectionHeader(
                title = "Ketentuan Pelaksanaan",
                subtitle = "${activeRules.size} poin tata tertib resmi madrasah"
            )

            TonalPrestigeCard(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    activeRules.forEachIndexed { index, ruleText ->
                        val isCrucial = crucialIndices.contains(index)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCrucial) colors.softMintContainer
                                        else ctaSolidBg
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isCrucial) colors.deepEmerald else Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = ruleText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.inkSlate,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            TonalPrestigeCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_rules_acknowledge")
                    .bounceClick {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleAcknowledged(!rulesAcknowledged)
                    },
                containerColor = confirmBg,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = rulesAcknowledged,
                            onCheckedChange = { checked ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleAcknowledged(checked)
                            },
                            modifier = Modifier.testTag("checkbox_rules_acknowledge"),
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.deepEmerald,
                                uncheckedColor = colors.slateSecondary,
                                checkmarkColor = if (colors.isDark) Color(0xFF05241A) else Color.White
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Saya Telah Membaca & Memahami Tata Tertib",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (rulesAcknowledged) colors.darkForest else colors.inkSlate
                            )
                            Text(
                                text = if (rulesAcknowledged) {
                                    "Konfirmasi tercatat. Silakan lanjut ke Daftar Soal."
                                } else {
                                    "Centang pernyataan di atas sebagai komitmen kejujuran ujian."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (rulesAcknowledged) colors.deepEmerald else colors.slateSecondary
                            )
                        }
                    }

                    if (rulesAcknowledged) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onGoToExamsTab,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_rules_to_exams"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ctaSolidBg,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Lanjut ke Daftar Paket Soal",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun ExamsScheduleTab(
    uiState: DashboardUiState,
    onSelectFilter: (ExamFilterOption) -> Unit,
    onExamAction: (ExamItemUiModel) -> Unit,
    onGoToDashboardTab: () -> Unit,
    onGoToRulesTab: () -> Unit,
    onGoToSettingsTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PrestigeTheme.colors
    val haptic = LocalHapticFeedback.current
    val isSyncing = uiState.syncState is SyncState.Syncing

    val activeItems = uiState.examItems.filter { it.status != ExamStatus.SELESAI }
    val finishedItems = uiState.examItems.filter { it.status == ExamStatus.SELESAI }
    val displayedItems = when (uiState.examFilter) {
        ExamFilterOption.ALL -> uiState.examItems
        ExamFilterOption.ACTIVE -> activeItems
        ExamFilterOption.FINISHED -> finishedItems
    }

    val hw = uiState.hardwareStatus
    val readySensorCount = listOf(
        hw.isAirplaneOn,
        hw.isWifiOff,
        hw.isBluetoothOff,
        hw.isRingerSilentOrVibrate
    ).count { it }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Agenda Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                shadowElevation = if (colors.isDark) 0.dp else 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    colors.bannerGradientStart,
                                    colors.bannerGradientEnd
                                )
                            )
                        )
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.06f),
                            radius = size.height * 0.65f,
                            center = Offset(size.width * 0.92f, size.height * 0.15f),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.05f),
                            radius = size.height * 0.42f,
                            center = Offset(size.width * 0.08f, size.height * 0.9f),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        StatusPillBadge(
                            text = "AGENDA UJIAN AKTIF • KELAS ${uiState.selectedGrade}",
                            contentColor = if (colors.isDark) colors.darkForest else Color(0xFF064E3B),
                            containerColor = colors.softMintContainer
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = uiState.examTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pilih dan kerjakan paket soal sesuai jadwal dan arahan pengawas",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA7F3D0),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Pre-Flight Check Status Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PreFlightStatusPill(
                    title = "Sensor Perangkat",
                    statusText = if (hw.isAllCompliant) "4/4 Siap" else "$readySensorCount/4 Siap",
                    isOk = hw.isAllCompliant,
                    onClick = onGoToDashboardTab,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("preflight_sensor_pill")
                )
                PreFlightStatusPill(
                    title = "Tata Tertib",
                    statusText = if (uiState.rulesAcknowledged) "Disetujui" else "Belum Baca",
                    isOk = uiState.rulesAcknowledged,
                    onClick = onGoToRulesTab,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("preflight_rules_pill")
                )
            }

            // Statistik Pelaksanaan Ujian
            ExamStatisticsCard(
                selectedGrade = uiState.selectedGrade,
                totalExams = uiState.totalExams,
                finishedExams = uiState.finishedExams,
                remainingExams = uiState.remainingExams
            )

            // Section Title + Filter Chips
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    title = "Daftar Paket Soal Ujian",
                    subtitle = if (uiState.examItems.isNotEmpty()) {
                        "${displayedItems.size} dari ${uiState.examItems.size} paket soal"
                    } else null
                )

                if (uiState.examItems.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val counts = mapOf(
                            ExamFilterOption.ALL to uiState.examItems.size,
                            ExamFilterOption.ACTIVE to activeItems.size,
                            ExamFilterOption.FINISHED to finishedItems.size
                        )
                        ExamFilterOption.entries.forEach { option ->
                            val selected = uiState.examFilter == option
                            val chipBg by animateColorAsState(
                                targetValue = if (selected) colors.softMintContainer else colors.pureCrispWhite,
                                animationSpec = tween(200),
                                label = "filter_chip_bg"
                            )
                            val chipFg = if (selected) colors.deepEmerald else colors.slateSecondary

                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .testTag("filter_chip_${option.name.lowercase()}")
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelectFilter(option)
                                    },
                                shape = RoundedCornerShape(50),
                                color = chipBg,
                                shadowElevation = if (colors.isDark) 0.dp else 1.dp
                            ) {
                                Text(
                                    text = "${option.label} (${counts[option] ?: 0})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = chipFg,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            when {
                isSyncing -> {
                    ShimmerExamSkeletonList(count = 3)
                }
                uiState.examItems.isEmpty() -> {
                    TonalPrestigeCard(
                        modifier = Modifier.fillMaxWidth(),
                        shadowElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(colors.softMintContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                    contentDescription = "Belum Ada Soal",
                                    tint = colors.deepEmerald,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Belum Ada Paket Soal",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.inkSlate
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Hubungkan perangkat ke Server Proktor di menu Pengaturan untuk menyinkronkan soal ujian.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.slateSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = onGoToSettingsTab,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Buka Pengaturan Server")
                            }
                        }
                    }
                }
                displayedItems.isEmpty() -> {
                    TonalPrestigeCard(
                        modifier = Modifier.fillMaxWidth(),
                        shadowElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Tidak ada soal di kategori \"${uiState.examFilter.label}\"",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.inkSlate
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Pilih filter \"Semua\" untuk melihat seluruh paket ujian.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.slateSecondary
                            )
                        }
                    }
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        displayedItems.forEach { item ->
                            ExamItemPrestigeCard(
                                item = item,
                                isDownloading = uiState.downloadingExamId == item.exam.id,
                                onActionClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onExamAction(item)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PreFlightStatusPill(
    title: String,
    statusText: String,
    isOk: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PrestigeTheme.colors
    val bg = if (isOk) colors.vibrantTealSoftBg else colors.warmAmberSoftBg
    val fg = if (isOk) colors.deepEmerald else colors.warmAmber
    val icon = if (isOk) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber

    TonalPrestigeCard(
        modifier = modifier.bounceClick(onClick = onClick),
        containerColor = bg,
        shadowElevation = 1.dp,
        cornerRadius = 14.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = fg,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.slateSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = fg,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ExamStatisticsCard(
    selectedGrade: Int,
    totalExams: Int,
    finishedExams: Int,
    remainingExams: Int
) {
    val colors = PrestigeTheme.colors

    TonalPrestigeCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_exam_statistics"),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Statistik Pengerjaan Soal",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.inkSlate
                )
                StatusPillBadge(
                    text = "Kelas $selectedGrade",
                    contentColor = colors.deepEmerald,
                    containerColor = colors.softMintContainer
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExamStatsRingChart(
                    total = totalExams,
                    finished = finishedExams
                )

                Spacer(modifier = Modifier.width(18.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExamStatMetricRow(
                        label = "Total Paket",
                        count = totalExams,
                        dotColor = colors.inkSlate,
                        valueColor = colors.inkSlate,
                        bgColor = colors.tonalSlateSurface
                    )
                    ExamStatMetricRow(
                        label = "Selesai",
                        count = finishedExams,
                        dotColor = colors.vibrantTeal,
                        valueColor = colors.deepEmerald,
                        bgColor = colors.vibrantTealSoftBg
                    )
                    ExamStatMetricRow(
                        label = "Belum Selesai",
                        count = remainingExams,
                        dotColor = colors.warmAmber,
                        valueColor = colors.warmAmber,
                        bgColor = colors.warmAmberSoftBg
                    )
                }
            }
        }
    }
}

@Composable
private fun ExamStatMetricRow(
    label: String,
    count: Int,
    dotColor: Color,
    valueColor: Color,
    bgColor: Color
) {
    val colors = PrestigeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor.copy(alpha = if (colors.isDark) 0.9f else 0.7f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.slateSecondary,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = count.toString(),
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = valueColor
        )
    }
}

@Composable
private fun ExamItemPrestigeCard(
    item: ExamItemUiModel,
    isDownloading: Boolean,
    onActionClick: () -> Unit
) {
    val colors = PrestigeTheme.colors
    val exam = item.exam
    val dateLabel = ExamScheduleHelper.formatDisplayDate(exam.examDate, exam.examDay)
    val timeSchedule = when {
        exam.startTime.isNotBlank() && exam.endTime.isNotBlank() -> "${exam.startTime} - ${exam.endTime} WIB"
        exam.startTime.isNotBlank() -> "Mulai ${exam.startTime} WIB"
        else -> "Waktu Bebas"
    }
    val shortCode = if (exam.shortName.isNotBlank()) {
        exam.shortName.uppercase()
    } else {
        exam.subjectName.filter { it.isLetterOrDigit() }.take(4).uppercase().ifEmpty { "MAPEL" }
    }
    val totalMinutes = ExamScheduleHelper.getTotalDurationMinutes(exam)
    val urgencyLabel = if (item.status != ExamStatus.SELESAI) {
        ExamScheduleHelper.getScheduleUrgencyLabel(exam)
    } else null

    val isFinished = item.status == ExamStatus.SELESAI
    val isInProgress = item.status == ExamStatus.SEDANG_UJIAN
    val scheduleState = item.scheduleState
    val ctaSolidBg = if (colors.isDark) Color(0xFF147A58) else colors.deepEmerald

    val (buttonText, buttonBg, buttonFg, buttonEnabled) = when {
        isFinished -> Quad("Selesai", colors.vibrantTealSoftBg, colors.deepEmerald, true)
        isInProgress -> Quad("Lanjutkan", ctaSolidBg, Color.White, true)
        scheduleState == ExamScheduleHelper.ScheduleState.NOT_STARTED_YET -> {
            val dateCompare = ExamScheduleHelper.compareExamDateWithToday(exam.examDate)
            val label = if (dateCompare != null && dateCompare < 0) {
                "Belum Buka"
            } else if (exam.startTime.isNotBlank()) {
                "Jam ${exam.startTime}"
            } else {
                "Belum Buka"
            }
            Quad(label, colors.tonalSlateSurface, colors.slateMuted, false)
        }
        scheduleState == ExamScheduleHelper.ScheduleState.EXPIRED -> {
            Quad("Waktu Habis", colors.coralRedSoftBg, colors.coralRed, false)
        }
        item.status == ExamStatus.BELUM_DIUNDUH -> {
            Quad("Unduh", ctaSolidBg, Color.White, !isDownloading)
        }
        else -> {
            Quad("Mulai Ujian", ctaSolidBg, Color.White, true)
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "in_progress_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isInProgress) 0.55f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    TonalPrestigeCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exam_card_${exam.id}"),
        containerColor = if (isInProgress) colors.softMintContainer.copy(alpha = 0.45f) else colors.pureCrispWhite,
        shadowElevation = if (isInProgress) 4.dp else 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isInProgress) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .graphicsLayer { alpha = pulseAlpha }
                            .clip(CircleShape)
                            .background(colors.vibrantTeal)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SESI SEDANG BERLANGSUNG • KETUK LANJUTKAN",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.deepEmerald
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.softMintContainer)
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarToday,
                            contentDescription = "Tanggal",
                            tint = colors.deepEmerald,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.darkForest,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = "Jam",
                            tint = colors.warmAmber,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = timeSchedule,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.slateSecondary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                StatusPillBadge(
                    text = "Kls ${exam.gradeLevel}",
                    contentColor = colors.slateSecondary,
                    containerColor = colors.tonalSlateSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.tonalSlateSurface)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF0B6E4F),
                                    Color(0xFF10B981)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = shortCode,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exam.subjectName,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.inkSlate,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AccessTime,
                            contentDescription = "Durasi",
                            tint = colors.warmAmber,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$totalMinutes Menit",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.slateSecondary
                        )
                        if (urgencyLabel != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• $urgencyLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (scheduleState == ExamScheduleHelper.ScheduleState.ACTIVE) colors.deepEmerald else colors.warmAmber
                            )
                        } else if (item.isFilePresent && !isFinished) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• Siap Offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.vibrantTeal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = onActionClick,
                    enabled = buttonEnabled,
                    modifier = Modifier
                        .height(42.dp)
                        .testTag("btn_exam_action_${exam.id}")
                        .bounceClick(enabled = buttonEnabled, onClick = onActionClick),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonBg,
                        contentColor = buttonFg,
                        disabledContainerColor = buttonBg,
                        disabledContentColor = buttonFg
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        val actionIcon = when {
                            isFinished -> Icons.Rounded.CheckCircle
                            isInProgress -> Icons.Rounded.Shield
                            scheduleState == ExamScheduleHelper.ScheduleState.ACTIVE && item.status == ExamStatus.BELUM_DIUNDUH -> Icons.Rounded.CloudDownload
                            scheduleState == ExamScheduleHelper.ScheduleState.ACTIVE -> Icons.Rounded.PlayArrow
                            else -> null
                        }
                        if (actionIcon != null) {
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(
                            text = buttonText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
