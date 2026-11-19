package id.mtsrth.exam.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mtsrth.exam.R
import id.mtsrth.exam.ui.components.AnimatedGradeSegmentedControl
import id.mtsrth.exam.ui.components.TonalPrestigeCard
import id.mtsrth.exam.ui.components.pressBounce
import id.mtsrth.exam.ui.components.shimmerEffect
import id.mtsrth.exam.ui.theme.JetBrainsMonoFontFamily
import id.mtsrth.exam.ui.theme.PrestigeTheme
import id.mtsrth.exam.util.ExamScheduleHelper
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsSyncTab(
    uiState: DashboardUiState,
    onSelectGrade: (Int) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSelectServerPreset: (String, String) -> Unit,
    onTestConnection: () -> Unit,
    onSyncExams: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PrestigeTheme.colors
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)

    val serverSectionBringIntoView = remember { BringIntoViewRequester() }
    val portFocusRequester = remember { FocusRequester() }
    var isHostFocused by remember { mutableStateOf(false) }
    var isPortFocused by remember { mutableStateOf(false) }
    val isServerFieldFocused = isHostFocused || isPortFocused

    LaunchedEffect(isServerFieldFocused, imeBottom) {
        if (isServerFieldFocused && imeBottom > 0) {
            delay(80L)
            serverSectionBringIntoView.bringIntoView()
        }
    }

    val isTesting = uiState.connectionTestState is ConnectionTestState.Testing
    val isSyncing = uiState.syncState is SyncState.Syncing
    val ctaSolidBg = if (colors.isDark) Color(0xFF147A58) else colors.deepEmerald

    val quickPresets = (uiState.serverHistory + listOf("192.168.1.100:8080", "192.168.43.1:8080"))
        .distinct()
        .take(3)

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
            // Prioritas 1: Jenjang Kelas Peserta (langsung dijangkau tanpa scroll)
            TonalPrestigeCard(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(colors.softMintContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.School,
                                contentDescription = "Jenjang Kelas",
                                tint = colors.deepEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Jenjang Kelas Peserta",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.inkSlate
                            )
                            Text(
                                text = "Hanya mengunduh & menyimpan soal angkatan terpilih",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.slateSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    AnimatedGradeSegmentedControl(
                        options = listOf(7, 8, 9),
                        selectedGrade = uiState.selectedGrade,
                        onSelectGrade = onSelectGrade
                    )
                }
            }

            // Prioritas 2: Server Proktor LAN & Sinkronisasi (aksi paling sering dipakai)
            TonalPrestigeCard(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(colors.softMintContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Dns,
                                contentDescription = "Server Proktor",
                                tint = colors.deepEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Server Proktor LAN",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.inkSlate
                            )
                            Text(
                                text = "Masukkan alamat IP lokal atau pilih riwayat server",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.slateSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.coolAlabaster)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = "Terakhir Sinkron",
                                tint = colors.deepEmerald,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = ExamScheduleHelper.formatSyncTimestamp(uiState.lastSyncTimestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.slateSecondary
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.OfflinePin,
                                contentDescription = "Tersimpan Offline",
                                tint = colors.vibrantTeal,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${uiState.downloadedExamCount} offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.deepEmerald
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(serverSectionBringIntoView)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = uiState.serverHost,
                                onValueChange = onHostChange,
                                label = { Text("Alamat IP Server") },
                                placeholder = { Text("192.168.1.100") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { portFocusRequester.requestFocus() }
                                ),
                                textStyle = TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 14.sp,
                                    color = colors.inkSlate
                                ),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.deepEmerald,
                                    unfocusedBorderColor = colors.subtleDivider,
                                    focusedLabelColor = colors.deepEmerald
                                ),
                                modifier = Modifier
                                    .weight(2.2f)
                                    .onFocusChanged { isHostFocused = it.isFocused }
                                    .testTag("input_server_host")
                            )

                            OutlinedTextField(
                                value = uiState.serverPort,
                                onValueChange = { if (it.length <= 5) onPortChange(it.filter { c -> c.isDigit() }) },
                                label = { Text("Port") },
                                placeholder = { Text("8080") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    }
                                ),
                                textStyle = TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 14.sp,
                                    color = colors.inkSlate
                                ),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.deepEmerald,
                                    unfocusedBorderColor = colors.subtleDivider,
                                    focusedLabelColor = colors.deepEmerald
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(portFocusRequester)
                                    .onFocusChanged { isPortFocused = it.isFocused }
                                    .testTag("input_server_port")
                            )
                        }

                        // Quick Server Preset Chips (minimum 48dp accessible touch target)
                        if (quickPresets.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Cepat:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.slateMuted
                                )
                                quickPresets.forEach { preset ->
                                    val hostPart = preset.substringBefore(":")
                                    val portPart = preset.substringAfter(":", "8080")
                                    val isCurrent = uiState.serverHost == hostPart && uiState.serverPort == portPart
                                    Surface(
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .heightIn(min = 38.dp)
                                            .clip(RoundedCornerShape(50))
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onSelectServerPreset(hostPart, portPart)
                                            },
                                        shape = RoundedCornerShape(50),
                                        color = if (isCurrent) colors.softMintContainer else colors.tonalSlateSurface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isCurrent) colors.softMintBorder else colors.cardBorder
                                        )
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = preset,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isCurrent) colors.deepEmerald else colors.slateSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val testBtnColors = when (uiState.connectionTestState) {
                                is ConnectionTestState.Success -> ButtonDefaults.outlinedButtonColors(
                                    containerColor = colors.vibrantTealSoftBg,
                                    contentColor = colors.deepEmerald
                                )
                                is ConnectionTestState.Failed -> ButtonDefaults.outlinedButtonColors(
                                    containerColor = colors.coralRedSoftBg,
                                    contentColor = colors.coralRed
                                )
                                else -> ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = colors.deepEmerald
                                )
                            }
                            val testInteraction = remember { MutableInteractionSource() }
                            val syncInteraction = remember { MutableInteractionSource() }

                            OutlinedButton(
                                onClick = onTestConnection,
                                enabled = !isTesting && !isSyncing,
                                interactionSource = testInteraction,
                                shape = RoundedCornerShape(12.dp),
                                colors = testBtnColors,
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .pressBounce(testInteraction, enabled = !isTesting && !isSyncing)
                                    .testTag("btn_test_connection")
                            ) {
                                AnimatedContent(
                                    targetState = uiState.connectionTestState,
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                    label = "test_btn_state"
                                ) { state ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        when (state) {
                                            is ConnectionTestState.Testing -> {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = colors.deepEmerald
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Menguji...", style = MaterialTheme.typography.labelMedium)
                                            }
                                            is ConnectionTestState.Success -> {
                                                Icon(
                                                    imageVector = Icons.Rounded.CheckCircle,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Terhubung", style = MaterialTheme.typography.labelMedium)
                                            }
                                            is ConnectionTestState.Failed -> {
                                                Icon(
                                                    imageVector = Icons.Rounded.ErrorOutline,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Gagal", style = MaterialTheme.typography.labelMedium)
                                            }
                                            ConnectionTestState.Idle -> {
                                                Icon(
                                                    imageVector = Icons.Rounded.WifiFind,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Tes Koneksi", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = onSyncExams,
                                enabled = !isSyncing,
                                interactionSource = syncInteraction,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ctaSolidBg,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                modifier = Modifier
                                    .weight(1.25f)
                                    .height(50.dp)
                                    .pressBounce(syncInteraction, enabled = !isSyncing)
                                    .testTag("btn_sync_exams")
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Menyinkronkan...", style = MaterialTheme.typography.labelLarge)
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sinkronkan Soal", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        ServerStatusFeedbackBanner(
                            connectionTestState = uiState.connectionTestState,
                            syncState = uiState.syncState
                        )
                    }
                }
            }

            // Prioritas 3: Identitas Lembaga Penyelenggara (di bawah aksi utama)
            MadrasahIdentityCard(
                schoolName = uiState.schoolName,
                tagline = uiState.schoolTagline,
                npsn = uiState.schoolNpsn,
                nsm = uiState.schoolNsm,
                address = uiState.schoolAddress
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "2026 All Rights Reserved • E-UJIAN MTS RTH",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.slateMuted
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "created by Lukmanul Hakim, S.E",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.slateMuted
                )
            }
        }
    }
}

@Composable
private fun MadrasahIdentityCard(
    schoolName: String,
    tagline: String,
    npsn: String,
    nsm: String,
    address: String
) {
    val colors = PrestigeTheme.colors

    TonalPrestigeCard(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 1.dp,
        cornerRadius = 22.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                colors.bannerGradientStart,
                                colors.bannerGradientEnd
                            )
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = "IDENTITAS LEMBAGA PENYELENGGARA",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFA7F3D0),
                    letterSpacing = 1.sp
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-26).dp)
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(68.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (colors.isDark) colors.tonalSlateSurface else colors.pureCrispWhite,
                    border = BorderStroke(2.dp, colors.pureCrispWhite),
                    shadowElevation = if (colors.isDark) 0.dp else 4.dp
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_sekolah),
                        contentDescription = "Logo Madrasah",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = schoolName,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.inkSlate,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.slateSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InstitutionCodeChip(
                        label = "NPSN",
                        value = npsn,
                        modifier = Modifier.weight(1f)
                    )
                    InstitutionCodeChip(
                        label = "NSM",
                        value = nsm,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.coolAlabaster)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = "Alamat",
                        tint = colors.deepEmerald,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.slateSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun InstitutionCodeChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = PrestigeTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.softMintContainer.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.deepEmerald
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = colors.darkForest
        )
    }
}

@Composable
private fun ServerStatusFeedbackBanner(
    connectionTestState: ConnectionTestState,
    syncState: SyncState
) {
    val colors = PrestigeTheme.colors
    val isActive = connectionTestState != ConnectionTestState.Idle || syncState != SyncState.Idle
    AnimatedVisibility(visible = isActive) {
        Column(modifier = Modifier.padding(top = 14.dp)) {
            when {
                syncState is SyncState.Syncing -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .shimmerEffect()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = syncState.message,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.darkForest
                        )
                        if (syncState.total > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { syncState.current.toFloat() / syncState.total.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = colors.deepEmerald,
                                trackColor = colors.softMintContainer,
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }
                }
                connectionTestState is ConnectionTestState.Testing -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .shimmerEffect()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.deepEmerald
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Menguji jangkauan ke ${connectionTestState.url}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkSlate,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                syncState is SyncState.Success -> {
                    StatusMessageStrip(
                        text = syncState.message,
                        isSuccess = true
                    )
                }
                syncState is SyncState.Error -> {
                    StatusMessageStrip(
                        text = syncState.message,
                        isSuccess = false
                    )
                }
                connectionTestState is ConnectionTestState.Success -> {
                    StatusMessageStrip(
                        text = connectionTestState.message,
                        isSuccess = true
                    )
                }
                connectionTestState is ConnectionTestState.Failed -> {
                    StatusMessageStrip(
                        text = connectionTestState.message,
                        isSuccess = false
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusMessageStrip(
    text: String,
    isSuccess: Boolean
) {
    val colors = PrestigeTheme.colors
    val bg = if (isSuccess) colors.vibrantTealSoftBg else colors.coralRedSoftBg
    val fg = if (isSuccess) colors.deepEmerald else colors.coralRed
    val icon = if (isSuccess) Icons.Rounded.CloudDone else Icons.Rounded.ErrorOutline

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = fg,
            fontWeight = FontWeight.SemiBold
        )
    }
}
