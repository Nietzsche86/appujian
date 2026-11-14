package id.mtsrth.exam.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.mtsrth.exam.R
import id.mtsrth.exam.data.model.ExamItemUiModel
import id.mtsrth.exam.ui.theme.PrestigeTheme

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onSelectTab: (DashboardTab) -> Unit,
    onQuickSyncClick: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onOpenSystemSettings: (String) -> Unit,
    onToggleRulesAcknowledge: (Boolean) -> Unit,
    onExamAction: (ExamItemUiModel) -> Unit,
    onSelectFilter: (ExamFilterOption) -> Unit,
    onSelectGrade: (Int) -> Unit,
    onServerHostChange: (String) -> Unit,
    onServerPortChange: (String) -> Unit,
    onSelectServerPreset: (String, String) -> Unit,
    onTestConnection: () -> Unit,
    onSyncExams: () -> Unit
) {
    val colors = PrestigeTheme.colors
    val animatedBg by animateColorAsState(
        targetValue = colors.coolAlabaster,
        animationSpec = tween(260),
        label = "scaffold_bg"
    )
    val isSyncing = uiState.syncState is SyncState.Syncing
    var spinTrigger by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = animatedBg,
        topBar = {
            PrestigeTopAppBar(
                schoolName = uiState.schoolName,
                subtitle = uiState.currentTab.subtitle,
                isDarkMode = uiState.isDarkMode,
                isSyncing = isSyncing,
                spinTrigger = spinTrigger,
                onToggleDarkMode = onToggleDarkMode,
                onSyncClick = {
                    spinTrigger++
                    onQuickSyncClick()
                }
            )
        },
        bottomBar = {
            PrestigeBottomNavigationBar(
                currentTab = uiState.currentTab,
                onSelectTab = onSelectTab
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = uiState.currentTab,
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    (fadeIn(tween(220)) + slideInHorizontally(tween(240)) { width ->
                        if (forward) width / 8 else -width / 8
                    }) togetherWith (fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { width ->
                        if (forward) -width / 8 else width / 8
                    })
                },
                label = "dashboard_tab_transition"
            ) { tab ->
                when (tab) {
                    DashboardTab.DASHBOARD -> DashboardOverviewTab(
                        uiState = uiState,
                        onOpenSystemSettings = onOpenSystemSettings,
                        onGoToExamsTab = { onSelectTab(DashboardTab.EXAMS) }
                    )
                    DashboardTab.RULES -> RulesGuideTab(
                        rules = uiState.examRules,
                        rulesAcknowledged = uiState.rulesAcknowledged,
                        onToggleAcknowledged = onToggleRulesAcknowledge,
                        onGoToExamsTab = { onSelectTab(DashboardTab.EXAMS) }
                    )
                    DashboardTab.EXAMS -> ExamsScheduleTab(
                        uiState = uiState,
                        onSelectFilter = onSelectFilter,
                        onExamAction = onExamAction,
                        onGoToDashboardTab = { onSelectTab(DashboardTab.DASHBOARD) },
                        onGoToRulesTab = { onSelectTab(DashboardTab.RULES) },
                        onGoToSettingsTab = { onSelectTab(DashboardTab.SETTINGS) }
                    )
                    DashboardTab.SETTINGS -> SettingsSyncTab(
                        uiState = uiState,
                        onSelectGrade = onSelectGrade,
                        onHostChange = onServerHostChange,
                        onPortChange = onServerPortChange,
                        onSelectServerPreset = onSelectServerPreset,
                        onTestConnection = onTestConnection,
                        onSyncExams = onSyncExams
                    )
                }
            }
        }
    }
}

@Composable
private fun PrestigeTopAppBar(
    schoolName: String,
    subtitle: String,
    isDarkMode: Boolean,
    isSyncing: Boolean,
    spinTrigger: Int,
    onToggleDarkMode: () -> Unit,
    onSyncClick: () -> Unit
) {
    val colors = PrestigeTheme.colors
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(spinTrigger, isSyncing) {
        if (isSyncing) {
            while (true) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(durationMillis = 850, easing = LinearEasing)
                )
            }
        } else if (spinTrigger > 0) {
            rotation.animateTo(
                targetValue = rotation.value + 360f,
                animationSpec = tween(durationMillis = 550, easing = LinearEasing)
            )
        }
    }

    val barBg by animateColorAsState(
        targetValue = colors.pureCrispWhite,
        animationSpec = tween(260),
        label = "topbar_bg"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = barBg,
        shadowElevation = if (colors.isDark) 0.dp else 3.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.coolAlabaster
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_sekolah),
                        contentDescription = "Logo Madrasah",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = schoolName,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.inkSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AnimatedContent(
                        targetState = subtitle,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(150)) },
                        label = "header_subtitle"
                    ) { currentSubtitle ->
                        Text(
                            text = currentSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.deepEmerald,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Light / Dark Mode Toggle Button
                IconButton(
                    onClick = onToggleDarkMode,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDarkMode) colors.warmAmberSoftBg
                            else colors.tonalSlateSurface
                        )
                        .testTag("btn_toggle_theme")
                ) {
                    AnimatedContent(
                        targetState = isDarkMode,
                        transitionSpec = {
                            (fadeIn(tween(200)) + scaleIn(initialScale = 0.75f)) togetherWith
                                (fadeOut(tween(160)) + scaleOut(targetScale = 0.75f))
                        },
                        label = "theme_toggle_icon"
                    ) { dark ->
                        Icon(
                            imageVector = if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = if (dark) "Aktifkan Mode Terang" else "Aktifkan Mode Gelap",
                            tint = if (dark) colors.warmAmber else colors.inkSlate,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Quick Sync Button
                IconButton(
                    onClick = onSyncClick,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.softMintContainer)
                        .testTag("btn_header_quick_sync")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = "Sinkronisasi Cepat",
                        tint = colors.deepEmerald,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = rotation.value }
                    )
                }
            }

            if (colors.isDark) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp)
                        .background(colors.subtleDivider)
                        .size(height = 1.dp, width = 1.dp)
                )
            }
        }
    }
}

private data class NavTabMeta(
    val tab: DashboardTab,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
private fun PrestigeBottomNavigationBar(
    currentTab: DashboardTab,
    onSelectTab: (DashboardTab) -> Unit
) {
    val colors = PrestigeTheme.colors
    val items = listOf(
        NavTabMeta(DashboardTab.DASHBOARD, Icons.Rounded.PhoneAndroid, Icons.Outlined.PhoneAndroid),
        NavTabMeta(DashboardTab.RULES, Icons.Rounded.Gavel, Icons.Outlined.Gavel),
        NavTabMeta(DashboardTab.EXAMS, Icons.AutoMirrored.Rounded.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
        NavTabMeta(DashboardTab.SETTINGS, Icons.Rounded.Settings, Icons.Outlined.Settings)
    )

    val navBg by animateColorAsState(
        targetValue = colors.pureCrispWhite,
        animationSpec = tween(260),
        label = "bottom_nav_bg"
    )

    Surface(
        color = navBg,
        shadowElevation = if (colors.isDark) 0.dp else 8.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (colors.isDark) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(height = 1.dp, width = 1.dp)
                        .background(colors.subtleDivider)
                )
            }
            NavigationBar(
                containerColor = navBg,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                items.forEach { meta ->
                    val selected = currentTab == meta.tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onSelectTab(meta.tab) },
                        modifier = Modifier.testTag("nav_tab_${meta.tab.name.lowercase()}"),
                        icon = {
                            Icon(
                                imageVector = if (selected) meta.selectedIcon else meta.unselectedIcon,
                                contentDescription = meta.tab.title
                            )
                        },
                        label = {
                            Text(
                                text = meta.tab.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.deepEmerald,
                            selectedTextColor = colors.deepEmerald,
                            indicatorColor = colors.softMintContainer,
                            unselectedIconColor = colors.slateSecondary,
                            unselectedTextColor = colors.slateSecondary
                        )
                    )
                }
            }
        }
    }
}
