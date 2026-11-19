package id.mtsrth.exam.ui.dashboard

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import id.mtsrth.exam.ui.components.pressBounce
import id.mtsrth.exam.ui.theme.JetBrainsMonoFontFamily
import id.mtsrth.exam.ui.theme.PrestigeTheme
import id.mtsrth.exam.util.ExamScheduleHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
    val confirmBorder by animateColorAsState(
        targetValue = if (rulesAcknowledged) colors.softMintBorder else colors.cardBorder,
        animationSpec = tween(300),
        label = "rules_confirm_border"
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
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TonalPrestigeCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = colors.softMintContainer,
                borderColor = colors.softMintBorder,
                shadowElevation = 0.dp
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
                    val cardBorder = if (isPenalty) colors.coralRedSoftBorder else colors.warmAmberSoftBorder
                    val icon = if (isPenalty) Icons.Rounded.Security else Icons.Rounded.Lock

                    TonalPrestigeCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = cardBg,
                        borderColor = cardBorder,
                        shadowElevation = 0.dp
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
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    activeRules.forEachIndexed { index, ruleText ->
                        val isCrucial = crucialIndices.contains(index)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
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
                        if (index < activeRules.lastIndex) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = colors.tonalSlateSurface
                            )
                        }
                    }
                }
            }

            TonalPrestigeCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .testTag("card_rules_acknowledge")
                    .bounceClick {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleAcknowledged(!rulesAcknowledged)
                    },
                containerColor = confirmBg,
                borderColor = confirmBorder,
                shadowElevation = 1.dp
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

                    AnimatedVisibility(
                        visible = rulesAcknowledged,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            val proceedInteraction = remember { MutableInteractionSource() }
                            Button(
                                onClick = onGoToExamsTab,
                                interactionSource = proceedInteraction,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .pressBounce(proceedInteraction)
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
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScheduleTab(
    uiState: DashboardUiState,
    onSelectFilter: (ExamFilterOption) -> Unit,
    onExamAction: (ExamItemUiModel) -> Unit,
    onPullToRefresh: () -> Unit,
    onGoToDashboardTab: () -> Unit,
    onGoToRulesTab: () -> Unit,
    onGoToSettingsTab: () -> Unit,
    onFixNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PrestigeTheme.colors
    val haptic = LocalHapticFeedback.current
    val isSyncing = uiState.syncState is SyncState.Syncing

    val pullToRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) {
            onPullToRefresh()
        }
    }
    LaunchedEffect(isSyncing) {
        if (!isSyncing && pullToRefreshState.isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val activeItems = remember(uiState.examItems) {
        uiState.examItems.filter { it.status != ExamStatus.SELESAI }
    }
    val finishedItems = remember(uiState.examItems) {
        uiState.examItems.filter { it.status == ExamStatus.SELESAI }
    }
    val displayedItems = remember(uiState.examItems, uiState.examFilter, activeItems, finishedItems) {
        when (uiState.examFilter) {
            ExamFilterOption.ALL -> uiState.examItems
            ExamFilterOption.ACTIVE -> activeItems
            ExamFilterOption.FINISHED -> finishedItems
        }
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
            .nestedScroll(pullToRefreshState.nestedScrollConnection),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "hero_agenda_banner") {
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
                                text = "Tarik layar ke bawah untuk sinkronisasi cepat dengan Server Proktor",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA7F3D0),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            item(key = "preflight_strip") {
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
            }

            if (uiState.reminderMinutesBefore > 0 && (!uiState.isNotificationPermissionGranted || !uiState.isExactAlarmAllowed)) {
                item(key = "notification_permission_warning") {
                    NotificationPermissionWarningCard(
                        reminderMinutesBefore = uiState.reminderMinutesBefore,
                        hasNotificationPermission = uiState.isNotificationPermissionGranted,
                        hasExactAlarmPermission = uiState.isExactAlarmAllowed,
                        onFixClick = onFixNotificationPermission
                    )
                }
            }

            item(key = "exam_statistics_card") {
                ExamStatisticsCard(
                    selectedGrade = uiState.selectedGrade,
                    totalExams = uiState.totalExams,
                    finishedExams = uiState.finishedExams,
                    remainingExams = uiState.remainingExams
                )
            }

            item(key = "section_header_and_filters") {
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
                                        .minimumInteractiveComponentSize()
                                        .heightIn(min = 42.dp)
                                        .clip(RoundedCornerShape(50))
                                        .testTag("filter_chip_${option.name.lowercase()}")
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSelectFilter(option)
                                        },
                                    shape = RoundedCornerShape(50),
                                    color = chipBg,
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) colors.softMintBorder else colors.cardBorder
                                    ),
                                    shadowElevation = 0.dp
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "${option.label} (${counts[option] ?: 0})",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = chipFg,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            when {
                isSyncing -> {
                    item(key = "shimmer_loading") {
                        ShimmerExamSkeletonList(count = 3)
                    }
                }
                uiState.examItems.isEmpty() -> {
                    item(key = "empty_exams_state") {
                        TonalPrestigeCard(
                            modifier = Modifier.fillMaxWidth(),
                            shadowElevation = 0.dp
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
                }
                displayedItems.isEmpty() -> {
                    item(key = "empty_filter_state") {
                        TonalPrestigeCard(
                            modifier = Modifier.fillMaxWidth(),
                            shadowElevation = 0.dp
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
                }
                else -> {
                    items(
                        items = displayedItems,
                        key = { it.exam.id }
                    ) { item ->
                        ExamItemPrestigeCard(
                            item = item,
                            nowMillis = nowMillis,
                            isDownloading = uiState.downloadingExamId == item.exam.id,
                            onActionClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onExamAction(item)
                            }
                        )
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        PullToRefreshContainer(
            state = pullToRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            containerColor = colors.pureCrispWhite,
            contentColor = colors.deepEmerald
        )
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
    val border = if (isOk) colors.softMintBorder else colors.warmAmberSoftBorder
    val fg = if (isOk) colors.deepEmerald else colors.warmAmber
    val icon = if (isOk) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber

    TonalPrestigeCard(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .bounceClick(onClick = onClick),
        containerColor = bg,
        borderColor = border,
        shadowElevation = 0.dp,
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
        shadowElevation = 1.dp
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
private fun NotificationPermissionWarningCard(
    reminderMinutesBefore: Int,
    hasNotificationPermission: Boolean,
    hasExactAlarmPermission: Boolean,
    onFixClick: () -> Unit
) {
    val colors = PrestigeTheme.colors
    val title = if (!hasNotificationPermission) {
        "Izin Notifikasi Pengingat Belum Aktif"
    } else {
        "Izin Alarm Tepat Waktu Belum Aktif"
    }
    val message = if (!hasNotificationPermission) {
        "Aktifkan izin notifikasi agar pengingat $reminderMinutesBefore menit sebelum ujian muncul di HP."
    } else if (!hasExactAlarmPermission) {
        "Aktifkan izin Alarm & Pengingat agar notifikasi $reminderMinutesBefore menit sebelum ujian muncul tepat waktu."
    } else {
        ""
    }

    TonalPrestigeCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_notification_permission_warning"),
        containerColor = colors.warmAmberSoftBg,
        borderColor = colors.warmAmberSoftBorder,
        shadowElevation = 0.dp,
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.warmAmber.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = "Peringatan Izin Pengingat",
                    tint = colors.warmAmber,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.inkSlate,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.slateSecondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onFixClick,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("btn_fix_notification_permission"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.warmAmber,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                Text(
                    text = "Aktifkan",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ExamItemPrestigeCard(
    item: ExamItemUiModel,
    nowMillis: Long,
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
    val isFinished = item.status == ExamStatus.SELESAI
    val isInProgress = item.status == ExamStatus.SEDANG_UJIAN
    val scheduleState = ExamScheduleHelper.getScheduleState(exam, nowMillis)
    val isMakeupAvailable = scheduleState == ExamScheduleHelper.ScheduleState.EXPIRED && !isFinished && !isInProgress
    val urgencyLabel = if (!isFinished) {
        ExamScheduleHelper.getScheduleUrgencyLabel(exam, nowMillis)
    } else null
    val ctaSolidBg = if (colors.isDark) Color(0xFF147A58) else colors.deepEmerald

    val (buttonText, buttonBg, buttonFg, buttonEnabled) = when {
        isFinished -> Quad("Selesai", colors.vibrantTealSoftBg, colors.deepEmerald, true)
        isInProgress -> Quad("Lanjutkan", ctaSolidBg, Color.White, true)
        item.status == ExamStatus.BELUM_DIUNDUH &&
            (scheduleState == ExamScheduleHelper.ScheduleState.ACTIVE || isMakeupAvailable) -> {
            Quad("Unduh", ctaSolidBg, Color.White, !isDownloading)
        }
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
        isMakeupAvailable -> {
            Quad("Susulan", colors.warmAmberSoftBg, colors.warmAmber, true)
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
        borderColor = if (isInProgress) colors.softMintBorder else colors.cardBorder,
        shadowElevation = if (isInProgress) 2.dp else 1.dp
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
            HorizontalDivider(
                thickness = 1.dp,
                color = colors.tonalSlateSurface
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
                        if (item.isFilePresent && !isFinished) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• Siap Offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.vibrantTeal
                            )
                        }
                    }
                    if (urgencyLabel != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val isActiveState = scheduleState == ExamScheduleHelper.ScheduleState.ACTIVE
                        val pillBg = if (isActiveState) colors.vibrantTealSoftBg else colors.warmAmberSoftBg
                        val pillFg = if (isActiveState) colors.deepEmerald else colors.warmAmber
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(pillBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("exam_countdown_${exam.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = "Hitung Mundur",
                                tint = pillFg,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = urgencyLabel,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = pillFg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else if (isMakeupAvailable) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.warmAmberSoftBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("exam_makeup_badge_${exam.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AdminPanelSettings,
                                contentDescription = "Ujian Susulan",
                                tint = colors.warmAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Jadwal Terlewat • Susulan",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = colors.warmAmber,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                val actionInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = onActionClick,
                    enabled = buttonEnabled,
                    interactionSource = actionInteraction,
                    modifier = Modifier
                        .height(48.dp)
                        .pressBounce(actionInteraction, enabled = buttonEnabled)
                        .testTag("btn_exam_action_${exam.id}"),
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
                            item.status == ExamStatus.BELUM_DIUNDUH &&
                                (scheduleState == ExamScheduleHelper.ScheduleState.ACTIVE || isMakeupAvailable) -> Icons.Rounded.CloudDownload
                            isMakeupAvailable -> Icons.Rounded.AdminPanelSettings
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
