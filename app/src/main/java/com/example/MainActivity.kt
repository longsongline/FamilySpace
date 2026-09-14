package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.AppUpdateSettingsDialog
import com.example.ui.components.CallLogSection
import com.example.ui.components.CountdownSection
import com.example.ui.components.FamilyCalendarView
import com.example.ui.components.FamilyTimelineSection
import com.example.ui.components.GitHubSyncCard
import com.example.ui.components.HomeVisitSection
import com.example.ui.components.MoodStatusSection
import com.example.ui.components.SafeArrivalCard
import com.example.ui.components.SafeArrivalDialog
import com.example.ui.components.SpaceHeader
import com.example.ui.theme.CozySage
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.WarmAmberPrimary
import com.example.ui.theme.WarmAmberSecondary
import com.example.ui.viewmodel.FamilyViewModel
import java.util.Locale

enum class FamilyTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("亲情港湾", Icons.Filled.Home, Icons.Outlined.Home),
    COUNTDOWN("生日倒计时", Icons.Filled.Cake, Icons.Outlined.Cake),
    ARRIVAL("到家平安", Icons.Filled.Security, Icons.Outlined.Security),
    SYNC_CONFIG("ID配置", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize notification channel and background periodic sync
        com.example.notification.FamilyNotificationHelper.createNotificationChannel(this)
        com.example.worker.FamilySyncWorker.enqueuePeriodicSync(this)

        // Request POST_NOTIFICATIONS permission on Android 13+ (TIRAMISU) so arrival notifications can pop up
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MyApplicationTheme {
                FamilySpaceApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.example.worker.FamilySyncWorker.enqueueImmediateSync(this)
    }
}

@Composable
fun FamilySpaceApp(
    viewModel: FamilyViewModel = viewModel()
) {
    var currentTab by rememberSaveable { mutableStateOf(FamilyTab.HOME) }
    var highlightedMemoryId by remember { mutableStateOf<Long?>(null) }

    val events by viewModel.events.collectAsStateWithLifecycle()
    val visits by viewModel.visits.collectAsStateWithLifecycle()
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val arrivalLogs by viewModel.arrivalLogs.collectAsStateWithLifecycle()

    val rentalAddress by viewModel.rentalAddress.collectAsStateWithLifecycle()
    val rentalLatitude by viewModel.rentalLatitude.collectAsStateWithLifecycle()
    val rentalLongitude by viewModel.rentalLongitude.collectAsStateWithLifecycle()
    val rentalRadiusMeters by viewModel.rentalRadiusMeters.collectAsStateWithLifecycle()
    val distanceToRentalMeters by viewModel.distanceToRentalMeters.collectAsStateWithLifecycle()
    val isInsideRentalZone by viewModel.isInsideRentalZone.collectAsStateWithLifecycle()
    val isBackgroundLocationEnabled by viewModel.isBackgroundLocationServiceEnabled.collectAsStateWithLifecycle()
    val parentNotificationPopup by viewModel.parentNotificationPopup.collectAsStateWithLifecycle()

    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    val isChildMode = currentRole == com.example.ui.viewmodel.UserRole.CHILD

    val childMood by viewModel.childMood.collectAsStateWithLifecycle()
    val childEnergy by viewModel.childEnergy.collectAsStateWithLifecycle()
    val childMoodUpdated by viewModel.childMoodUpdated.collectAsStateWithLifecycle()

    val parentMood by viewModel.parentMood.collectAsStateWithLifecycle()
    val parentEnergy by viewModel.parentEnergy.collectAsStateWithLifecycle()
    val parentMoodUpdated by viewModel.parentMoodUpdated.collectAsStateWithLifecycle()

    val syncConfig by viewModel.syncConfig.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncStatusMessage by viewModel.syncStatusMessage.collectAsStateWithLifecycle()

    val currentGpsInfo by viewModel.currentGpsInfo.collectAsStateWithLifecycle()
    val isGpsLoading by viewModel.isGpsLoading.collectAsStateWithLifecycle()

    val updateCheckResult by viewModel.updateCheckResult.collectAsStateWithLifecycle()
    val updateDownloadState by viewModel.updateDownloadState.collectAsStateWithLifecycle()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsStateWithLifecycle()
    val showUpdateSettingsDialog by viewModel.showUpdateSettingsDialog.collectAsStateWithLifecycle()
    val updateToastMessage by viewModel.updateToastMessage.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.runtime.LaunchedEffect(updateToastMessage) {
        updateToastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearUpdateToast()
        }
    }

    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.fetchRealTimeGps(context)
        }
    }

    androidx.compose.runtime.LaunchedEffect(isChildMode) {
        if (isChildMode) {
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            viewModel.fetchRealTimeGps(context)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                windowInsets = WindowInsets.navigationBars,
                modifier = Modifier.testTag("bottom_navigation_bar")
            ) {
                FamilyTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = WarmAmberPrimary,
                            selectedTextColor = WarmAmberPrimary,
                            indicatorColor = WarmAmberPrimary.copy(alpha = 0.15f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab_transition",
                modifier = Modifier.fillMaxSize()
            ) { targetTab ->
                when (targetTab) {
                    FamilyTab.HOME -> {
                        HomeTabScreen(
                            isChildMode = isChildMode,
                            events = events,
                            visits = visits,
                            calls = calls,
                            memories = memories,
                            highlightedMemoryId = highlightedMemoryId,
                            onClearHighlight = { highlightedMemoryId = null },
                            rentalAddress = rentalAddress,
                            isInsideRentalZone = isInsideRentalZone,
                            distanceToRentalMeters = distanceToRentalMeters,
                            syncConfigTime = syncConfig.lastSyncTime,
                            isSyncing = isSyncing,
                            onSyncNow = { viewModel.refreshFamilyMediaSync { } },
                            childMood = childMood,
                            childEnergy = childEnergy,
                            childMoodUpdated = childMoodUpdated,
                            parentMood = parentMood,
                            parentEnergy = parentEnergy,
                            parentMoodUpdated = parentMoodUpdated,
                            onNavigateToTab = { currentTab = it },
                            onAddVisit = { dateStr, note, meals -> viewModel.addHomeVisit(dateStr, note, meals) },
                            onDeleteVisit = { viewModel.deleteVisit(it) },
                            onUpdateChildMood = { m, e -> viewModel.updateChildMood(m, e) },
                            onUpdateParentMood = { m, e -> viewModel.updateParentMood(m, e) },
                            onAddCall = { p, d, t, aUri, aDur -> viewModel.addCallRecord(p, d, t, audioUri = aUri, audioDurationSeconds = aDur) },
                            onDeleteCall = { viewModel.deleteCall(it) },
                            onAddMemory = { title, dateStr, loc, content, uri, tag, mediaType, videoUri ->
                                viewModel.addMemory(title, dateStr, loc, content, uri, tag, "", mediaType, videoUri)
                            },
                            onDeleteMemory = { viewModel.deleteMemory(it) },
                            calcDaysAndNextDate = { viewModel.calculateDaysRemainingForEvent(it) }
                        )
                    }

                    FamilyTab.COUNTDOWN -> {
                        CountdownTabScreen(
                            events = events,
                            onAddLunarEvent = { title, m, d, p, n -> viewModel.addLunarBirthday(title, m, d, p, n) },
                            onAddSolarEvent = { title, dStr, cat, p, n -> viewModel.addSolarEvent(title, dStr, cat, p, n) },
                            onDeleteEvent = { viewModel.deleteEvent(it) },
                            calcDaysAndNextDate = { viewModel.calculateDaysRemainingForEvent(it) },
                            onGetMappedEvents = { y, m, d -> viewModel.getEventsMappedForDate(y, m, d) },
                            onAddCalendarNote = { dStr, t, c, tag -> viewModel.addCalendarNote(dStr, t, c, tag) },
                            onNavigateToMemory = { memId ->
                                highlightedMemoryId = memId
                                currentTab = FamilyTab.HOME
                            },
                            onNavigateToTimeline = { currentTab = FamilyTab.HOME }
                        )
                    }

                    FamilyTab.ARRIVAL -> {
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        ArrivalTabScreen(
                            isChildMode = isChildMode,
                            onEnterChildMode = { viewModel.verifyAndSwitchToChildMode(it) },
                            onSwitchToParentMode = { viewModel.switchToParentMode() },
                            onChangePasscode = { viewModel.updateChildPasscode(it) },
                            rentalAddress = rentalAddress,
                            rentalLatitude = rentalLatitude,
                            rentalLongitude = rentalLongitude,
                            rentalRadiusMeters = rentalRadiusMeters,
                            distanceToRentalMeters = distanceToRentalMeters,
                            isInsideRentalZone = isInsideRentalZone,
                            arrivalLogs = arrivalLogs,
                            currentGpsInfo = currentGpsInfo,
                            isGpsLoading = isGpsLoading,
                            isBackgroundLocationEnabled = isBackgroundLocationEnabled,
                            onToggleBackgroundLocation = { viewModel.toggleBackgroundLocationService(it, ctx) },
                            onFetchGps = { viewModel.fetchRealTimeGps(ctx) },
                            onSetCurrentGpsAsBase = { addr, radius ->
                                viewModel.setRentalLocationFromCurrentGps(addr, radius)
                            },
                            onUpdateFullConfig = { addr, lat, lng, radius ->
                                viewModel.updateRentalLocationConfig(addr, lat, lng, radius)
                            },
                            onTriggerArrived = { note -> viewModel.triggerArrivedAtRentalHome(note) },
                            onUpdateAddress = { viewModel.updateRentalAddress(it) }
                        )
                    }

                    FamilyTab.SYNC_CONFIG -> {
                        SyncConfigTabScreen(
                            syncConfig = syncConfig,
                            isSyncing = isSyncing,
                            syncStatusMessage = syncStatusMessage,
                            isCheckingUpdate = isCheckingUpdate,
                            onPush = { token, gistId, owner, repo, path, isGist ->
                                viewModel.pushToGitHubTransit(token, gistId, owner, repo, path, isGist)
                            },
                            onPull = { token, gistId, owner, repo, path, isGist ->
                                viewModel.pullFromGitHubTransit(token, gistId, owner, repo, path, isGist)
                            },
                            onSaveConfig = { viewModel.saveSyncConfig(it) },
                            onDismissStatusMessage = { viewModel.dismissSyncMessage() },
                            onCheckUpdate = { viewModel.checkForUpdates(isManual = true) },
                            onOpenUpdateSettings = { viewModel.showUpdateSettingsDialog.value = true }
                        )
                    }
                }
            }
        }
    }

    // Safe Arrival Notification Dialog (differentiated for parent vs child)
    parentNotificationPopup?.let { data ->
        SafeArrivalDialog(
            data = data,
            isChildMode = isChildMode,
            onDismiss = { viewModel.dismissArrivalPopup() }
        )
    }

    // OTA In-App Update Dialog
    if (showUpdateDialog) {
        AppUpdateDialog(
            updateCheckResult = updateCheckResult,
            downloadState = updateDownloadState,
            onStartDownload = { viewModel.startDownloadUpdate(it) },
            onInstallApk = { viewModel.installDownloadedApk(it) },
            onDismiss = { viewModel.dismissUpdateDialog() },
            onIgnoreVersion = { viewModel.ignoreCurrentUpdateVersion(it) }
        )
    }

    // OTA In-App Update Settings Dialog
    if (showUpdateSettingsDialog) {
        AppUpdateSettingsDialog(
            currentOwner = viewModel.updateManager.getCustomRepoOwner(),
            currentRepo = viewModel.updateManager.getCustomRepoName(),
            currentUrl = viewModel.updateManager.getCustomUpdateUrl(),
            isChecking = isCheckingUpdate,
            onSaveConfig = { owner, repo, url ->
                viewModel.saveUpdateSourceConfig(owner, repo, url)
            },
            onCheckNow = { viewModel.checkForUpdates(isManual = true) },
            onDismiss = { viewModel.showUpdateSettingsDialog.value = false }
        )
    }
}

/**
 * 1. 亲情港湾主页 (Home Tab)
 * 温馨日常：心情状态电池、回家相聚记录、通话关怀流水、聚餐时光轴，外加顶部快捷直达条
 */
@Composable
private fun HomeTabScreen(
    isChildMode: Boolean = false,
    events: List<com.example.data.model.FamilyEvent>,
    visits: List<com.example.data.model.HomeVisitRecord>,
    calls: List<com.example.data.model.CallRecord>,
    memories: List<com.example.data.model.FamilyMemory>,
    highlightedMemoryId: Long? = null,
    onClearHighlight: () -> Unit = {},
    rentalAddress: String,
    isInsideRentalZone: Boolean,
    distanceToRentalMeters: Double?,
    syncConfigTime: String,
    isSyncing: Boolean = false,
    onSyncNow: () -> Unit = {},
    childMood: String,
    childEnergy: Int,
    childMoodUpdated: String,
    parentMood: String,
    parentEnergy: Int,
    parentMoodUpdated: String,
    onNavigateToTab: (FamilyTab) -> Unit,
    onAddVisit: (String, String, String) -> Unit,
    onDeleteVisit: (Long) -> Unit,
    onUpdateChildMood: (String, Int) -> Unit,
    onUpdateParentMood: (String, Int) -> Unit,
    onAddCall: (String, Int, String, String?, Int) -> Unit,
    onDeleteCall: (Long) -> Unit,
    onAddMemory: (String, String, String, String, String?, String, String, String?) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    calcDaysAndNextDate: (com.example.data.model.FamilyEvent) -> Pair<Int, String>
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    androidx.compose.runtime.LaunchedEffect(highlightedMemoryId) {
        if (highlightedMemoryId != null) {
            try {
                listState.animateScrollToItem(2)
            } catch (_: Exception) {}
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Space Header (三人同享专属港湾)
        item {
            SpaceHeader(
                isChildMode = isChildMode,
                isInsideRentalZone = isInsideRentalZone,
                distanceToRentalMeters = distanceToRentalMeters
            )
        }

        // Quick Glance & Navigation Bar (直达卡片：生日倒计时 + 到家平安 + 同步状态)
        item {
            val nearestEvent = remember(events) {
                events.minByOrNull { calcDaysAndNextDate(it).first }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Birthday quick chip
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToTab(FamilyTab.COUNTDOWN) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🎂", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = if (nearestEvent != null) {
                                        val days = calcDaysAndNextDate(nearestEvent).first
                                        "${nearestEvent.title.take(4)}还有${days}天"
                                    } else "生日倒计时",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = WarmAmberPrimary
                                    )
                                )
                                Text(
                                    text = "点击直达 >",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        color = WarmAmberSecondary
                                    )
                                )
                            }
                        }
                    }

                    // Arrival quick chip
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8F4)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToTab(FamilyTab.ARRIVAL) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🛡️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                val statusText = if (isInsideRentalZone) {
                                    if (isChildMode) "已在出租房 🏠" else "孩子在出租房 🏠"
                                } else if (distanceToRentalMeters != null) {
                                    val d = distanceToRentalMeters
                                    val distStr = if (d >= 1000) String.format(Locale.US, "%.1fkm", d / 1000.0) else "${d.toInt()}m"
                                    "距家 $distStr"
                                } else {
                                    if (isChildMode) "到家守护" else "在外奔波"
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = CozySage
                                    )
                                )
                                Text(
                                    text = if (isChildMode) "报平安 >" else "查看平安 >",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        color = CozySage.copy(alpha = 0.8f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1. Dinner & Photo/Video Memories Timeline (聚餐与合照时光轴 - 往上提，居首呈现)
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                FamilyTimelineSection(
                    memories = memories,
                    isSyncing = isSyncing,
                    highlightedMemoryId = highlightedMemoryId,
                    onClearHighlight = onClearHighlight,
                    onSyncNow = onSyncNow,
                    onAddMemory = onAddMemory,
                    onDeleteMemory = onDeleteMemory
                )
            }
        }

        // 2. Today's Mood & Status Battery (今日心情与状态电池)
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                MoodStatusSection(
                    childMood = childMood,
                    childEnergy = childEnergy,
                    childUpdated = childMoodUpdated,
                    parentMood = parentMood,
                    parentEnergy = parentEnergy,
                    parentUpdated = parentMoodUpdated,
                    onUpdateChildMood = onUpdateChildMood,
                    onUpdateParentMood = onUpdateParentMood
                )
            }
        }

        // 3. Care Call Records (通话记录与频率关怀)
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                CallLogSection(
                    calls = calls,
                    onAddCall = onAddCall,
                    onDeleteCall = onDeleteCall
                )
            }
        }

        // 4. Home Visits Counter & Check-in (回家足迹记录 - 放在下方，避免一进首页就被看到)
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                HomeVisitSection(
                    visits = visits,
                    onAddVisit = onAddVisit,
                    onDeleteVisit = onDeleteVisit
                )
            }
        }
    }
}

/**
 * 2. 生日节日倒计时页面 (Countdown Tab)
 * 农历与公历生日倒计时、多角色标记、专属日历速查
 */
@Composable
private fun CountdownTabScreen(
    events: List<com.example.data.model.FamilyEvent>,
    onAddLunarEvent: (String, Int, Int, String, String) -> Unit,
    onAddSolarEvent: (String, String, String, String, String) -> Unit,
    onDeleteEvent: (Long) -> Unit,
    calcDaysAndNextDate: (com.example.data.model.FamilyEvent) -> Pair<Int, String>,
    onGetMappedEvents: (Int, Int, Int) -> List<com.example.ui.viewmodel.CalendarDayEvent>,
    onAddCalendarNote: (String, String, String, String) -> Unit,
    onNavigateToMemory: (Long) -> Unit = {},
    onNavigateToTimeline: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            TabBanner(
                title = "🎂 生日佳节 · 倒计时",
                subtitle = "记录我和爸爸妈妈的重要时刻",
                gradientColors = listOf(Color(0xFFD35400), Color(0xFFE67E22))
            )
        }

        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                CountdownSection(
                    events = events,
                    onAddLunarEvent = onAddLunarEvent,
                    onAddSolarEvent = onAddSolarEvent,
                    onDeleteEvent = onDeleteEvent,
                    calcDaysAndNextDate = calcDaysAndNextDate
                )
            }
        }

        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                FamilyCalendarView(
                    onGetMappedEvents = onGetMappedEvents,
                    onAddCalendarNote = onAddCalendarNote,
                    onNavigateToMemory = onNavigateToMemory,
                    onNavigateToTimeline = onNavigateToTimeline
                )
            }
        }
    }
}

/**
 * 3. 到家平安守护页面 (Arrival Tab)
 * 出租房安全守护、电子围栏、实时直线距离、一键报平安与到达历史流水
 */
@Composable
private fun ArrivalTabScreen(
    isChildMode: Boolean,
    onEnterChildMode: (String) -> Boolean,
    onSwitchToParentMode: () -> Unit,
    onChangePasscode: (String) -> Boolean,
    rentalAddress: String,
    rentalLatitude: Double,
    rentalLongitude: Double,
    rentalRadiusMeters: Int,
    distanceToRentalMeters: Double?,
    isInsideRentalZone: Boolean,
    arrivalLogs: List<com.example.data.model.SafeArrivalLog>,
    currentGpsInfo: String?,
    isGpsLoading: Boolean,
    isBackgroundLocationEnabled: Boolean,
    onToggleBackgroundLocation: (Boolean) -> Unit,
    onFetchGps: () -> Unit,
    onSetCurrentGpsAsBase: (String, Int) -> Unit,
    onUpdateFullConfig: (String, Double, Double, Int) -> Unit,
    onTriggerArrived: (String) -> Unit,
    onUpdateAddress: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val bannerTitle = if (isChildMode) "🛡️ 出租房守护 · 我的模式" else "🛡️ 出租房守护"
            val bannerSubtitle = if (isChildMode) {
                "电子围栏设定 · 实时距离 · 向父母报平安"
            } else {
                "实时位置距离 · 到家状态 · 温暖关怀"
            }
            TabBanner(
                title = bannerTitle,
                subtitle = bannerSubtitle,
                gradientColors = if (isChildMode) {
                    listOf(Color(0xFF4A7C59), Color(0xFF639272))
                } else {
                    listOf(Color(0xFFD97706), Color(0xFFB45309))
                }
            )
        }

        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                SafeArrivalCard(
                    isChildMode = isChildMode,
                    onEnterChildMode = onEnterChildMode,
                    onSwitchToParentMode = onSwitchToParentMode,
                    onChangePasscode = onChangePasscode,
                    rentalAddress = rentalAddress,
                    rentalLatitude = rentalLatitude,
                    rentalLongitude = rentalLongitude,
                    rentalRadiusMeters = rentalRadiusMeters,
                    distanceToRentalMeters = distanceToRentalMeters,
                    isInsideRentalZone = isInsideRentalZone,
                    arrivalLogs = arrivalLogs,
                    gpsInfo = currentGpsInfo,
                    isGpsLoading = isGpsLoading,
                    isBackgroundLocationEnabled = isBackgroundLocationEnabled,
                    onToggleBackgroundLocation = onToggleBackgroundLocation,
                    onFetchGps = onFetchGps,
                    onSetCurrentGpsAsBase = onSetCurrentGpsAsBase,
                    onUpdateFullConfig = onUpdateFullConfig,
                    onTriggerArrived = onTriggerArrived,
                    onUpdateAddress = onUpdateAddress
                )
            }
        }
    }
}

/**
 * 4. ID配置与云端中转页面 (Sync Config Tab)
 * 免账号GitHub Gist/仓库中转配置、家庭专属ID一键共享、防覆盖并集同步说明
 */
@Composable
private fun SyncConfigTabScreen(
    syncConfig: com.example.data.sync.SyncConfig,
    isSyncing: Boolean,
    syncStatusMessage: String?,
    isCheckingUpdate: Boolean,
    onPush: (String, String, String, String, String, Boolean) -> Unit,
    onPull: (String, String, String, String, String, Boolean) -> Unit,
    onSaveConfig: (com.example.data.sync.SyncConfig) -> Unit,
    onDismissStatusMessage: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenUpdateSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            TabBanner(
                title = "⚙️ 云端中转 · ID配置",
                subtitle = "家庭专属极简配置 · 跨手机多端免账号互联 · 防覆盖保护",
                gradientColors = listOf(Color(0xFF2C3E50), Color(0xFF34495E))
            )
        }

        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                GitHubSyncCard(
                    config = syncConfig,
                    isSyncing = isSyncing,
                    syncStatusMessage = syncStatusMessage,
                    onPush = onPush,
                    onPull = onPull,
                    onSaveConfig = onSaveConfig,
                    onDismissStatusMessage = onDismissStatusMessage
                )
            }
        }

        // OTA Software Version & Online Update Card
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(WarmAmberPrimary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = WarmAmberPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "软件在线升级 (OTA)",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "当前版本：v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }

                        IconButton(
                            onClick = onOpenUpdateSettings,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "更新设置",
                                tint = Color.Gray.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "支持通过 GitHub 仓库 Releases 或自定义更新源一键检测推送，手机无需手动重装即可自动覆盖升级，聊天记录与家庭数据完全保留。",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            lineHeight = 17.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onCheckUpdate,
                            enabled = !isCheckingUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = WarmAmberPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("正在检测...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("检查新版本")
                            }
                        }

                        OutlinedButton(
                            onClick = onOpenUpdateSettings,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("更新源配置", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF285E8E),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "👨‍👩‍👦 三人手机如何同步互通？",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "① 孩子配置：在孩子手机上方点击“生成家庭专属中转ID”，保存后点击“保存配置”。\n" +
                                "② 发给父母：点击“复制配置ID”，微信发给爸爸妈妈。\n" +
                                "③ 父母加入：父母打开各自手机的本页“ID配置”，粘贴该ID并点击“加入中转站并同步”。\n\n" +
                                "🛡️ 智能防覆盖说明：系统采用无损智能并集算法（Lossless Merge），即使父母端初次同步，也只会拉取您已建好的生日、围栏与回忆，绝不会把您的数据回滚抹除！",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            lineHeight = 18.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * 统一的各 Tab 顶部轻量级彩色横幅
 */
@Composable
private fun TabBanner(
    title: String,
    subtitle: String,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            .background(Brush.verticalGradient(gradientColors))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

