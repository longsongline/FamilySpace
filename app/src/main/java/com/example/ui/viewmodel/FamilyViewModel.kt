package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.CalendarNote
import com.example.data.model.CallRecord
import com.example.data.model.FamilyEvent
import com.example.data.model.FamilyMemory
import com.example.data.model.HomeVisitRecord
import com.example.data.model.SafeArrivalLog
import com.example.data.repository.FamilyRepository
import com.example.data.sync.FullFamilyBackup
import com.example.data.sync.GitHubSyncManager
import com.example.data.sync.SyncConfig
import com.example.notification.FamilyNotificationHelper
import com.example.util.AppUpdateManager
import com.example.util.AppVersionInfo
import com.example.util.LunarCalendarUtil
import com.example.util.UpdateCheckResult
import com.example.util.UpdateDownloadState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ParentNotificationData(
    val arrivalTime: String,
    val address: String,
    val childMessage: String,
    val distanceInfo: String = ""
)

enum class UserRole(val displayName: String) {
    PARENT("父母模式"),
    CHILD("孩子模式")
}

data class CalendarDayEvent(
    val type: String, // BIRTHDAY, FESTIVAL, VISIT, MEMORY, CALL, NOTE
    val title: String,
    val detail: String,
    val timeOrTag: String,
    val isLunar: Boolean = false,
    val memory: FamilyMemory? = null,
    val visit: HomeVisitRecord? = null,
    val call: CallRecord? = null,
    val note: CalendarNote? = null,
    val event: FamilyEvent? = null
)

class FamilyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FamilyRepository
    val syncManager: GitHubSyncManager

    // ==========================================
    // Role Mode: 父母模式 (默认) vs 孩子模式 (我的模式)
    // ==========================================
    private val rolePrefs = application.getSharedPreferences("family_role_prefs", Context.MODE_PRIVATE)
    private val rentalPrefs = application.getSharedPreferences("family_rental_prefs", Context.MODE_PRIVATE)
    val currentRole = MutableStateFlow(UserRole.PARENT)
    val childPasscode = MutableStateFlow("1234")

    // ==========================================
    // GitHub Cloud Transit Station (三人同享中转站)
    // ==========================================
    val syncConfig = MutableStateFlow(SyncConfig())
    val isSyncing = MutableStateFlow(false)
    val syncStatusMessage = MutableStateFlow<String?>(null)
    private var autoPushJob: Job? = null

    // ==========================================
    // OTA App Update System (应用内在线自动更新)
    // ==========================================
    val updateManager = AppUpdateManager(application)
    val updateCheckResult = MutableStateFlow<UpdateCheckResult?>(null)
    val updateDownloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val isCheckingUpdate = MutableStateFlow(false)
    val showUpdateDialog = MutableStateFlow(false)
    val showUpdateSettingsDialog = MutableStateFlow(false)
    val updateToastMessage = MutableStateFlow<String?>(null)

    init {
        val db = AppDatabase.getDatabase(application)
        repository = FamilyRepository(db.familyDao())
        syncManager = GitHubSyncManager(application)
        syncConfig.value = syncManager.loadConfig()

        // Passcode loaded from storage; defaults to 1234
        val savedPasscode = rolePrefs.getString("child_passcode", "1234") ?: "1234"
        childPasscode.value = savedPasscode

        // Role persistence: Defaults to PARENT initially; stays in CHILD if user logged in
        val savedRoleName = rolePrefs.getString("current_role", UserRole.PARENT.name) ?: UserRole.PARENT.name
        val initialRole = try {
            UserRole.valueOf(savedRoleName)
        } catch (_: Exception) {
            UserRole.PARENT
        }
        currentRole.value = initialRole

        viewModelScope.launch {
            // First pull from shared cloud to get family's latest data
            val cfg = syncConfig.value
            val effectiveGistId = if (cfg.gistId.isNotBlank()) cfg.gistId else GitHubSyncManager.BUILT_IN_GIST_ID
            if (effectiveGistId.isNotBlank() || (cfg.repoOwner.isNotBlank() && cfg.repoName.isNotBlank())) {
                silentAutoPullOnLaunch()
            }
            // Only populate defaults if database is still completely empty on first launch
            repository.prepopulateIfEmpty(application)

            // Auto-check for app updates on launch (with 1 hour interval check)
            val lastCheck = updateManager.getLastCheckTime()
            if (System.currentTimeMillis() - lastCheck > 60 * 60 * 1000L) {
                delay(3000) // Delay slightly after startup sync
                checkForUpdates(isManual = false)
            }
        }

        // Periodic real-time background sync every 15 seconds
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                val cfg = syncConfig.value
                val effectiveGistId = if (cfg.gistId.isNotBlank()) cfg.gistId else GitHubSyncManager.BUILT_IN_GIST_ID
                if (effectiveGistId.isNotBlank() || (cfg.repoOwner.isNotBlank() && cfg.repoName.isNotBlank())) {
                    silentAutoPullOnLaunch()
                }
            }
        }

        // Start background location service if child mode is active
        if (currentRole.value == UserRole.CHILD && rentalPrefs.getBoolean("bg_location_enabled", true)) {
            com.example.service.FamilyLocationService.start(getApplication())
        }
    }

    // Mood & Status state (Persistent with exact timestamps for Last-Write-Wins sync)
    private val moodPrefs = application.getSharedPreferences("family_mood_prefs", Context.MODE_PRIVATE)

    val childMood = MutableStateFlow(
        moodPrefs.getString("child_mood", "今天下班很准时，状态很棒，吃了热腾腾的面条 🍜") ?: "今天下班很准时，状态很棒，吃了热腾腾的面条 🍜"
    )
    val childEnergy = MutableStateFlow(moodPrefs.getInt("child_energy", 90))
    val childMoodUpdated = MutableStateFlow(moodPrefs.getString("child_mood_updated", "今天 19:40") ?: "今天 19:40")
    val childMoodTimestamp = MutableStateFlow(moodPrefs.getLong("child_mood_ts", 0L))

    val parentMood = MutableStateFlow(
        moodPrefs.getString("parent_mood", "爸爸今天在阳台摆弄花草，妈妈傍晚和邻居散步，一切安好 🌸") ?: "爸爸今天在阳台摆弄花草，妈妈傍晚和邻居散步，一切安好 🌸"
    )
    val parentEnergy = MutableStateFlow(moodPrefs.getInt("parent_energy", 95))
    val parentMoodUpdated = MutableStateFlow(moodPrefs.getString("parent_mood_updated", "今天 18:20") ?: "今天 18:20")
    val parentMoodTimestamp = MutableStateFlow(moodPrefs.getLong("parent_mood_ts", 0L))

    fun updateChildMood(mood: String, energy: Int) {
        val nowMs = System.currentTimeMillis()
        val sdf = SimpleDateFormat("HH:mm", Locale.CHINA)
        val timeStr = "今天 " + sdf.format(Date())
        childMood.value = mood
        childEnergy.value = energy
        childMoodUpdated.value = timeStr
        childMoodTimestamp.value = nowMs

        moodPrefs.edit()
            .putString("child_mood", mood)
            .putInt("child_energy", energy)
            .putString("child_mood_updated", timeStr)
            .putLong("child_mood_ts", nowMs)
            .apply()

        triggerAutoSyncPush()
    }

    fun updateParentMood(mood: String, energy: Int) {
        val nowMs = System.currentTimeMillis()
        val sdf = SimpleDateFormat("HH:mm", Locale.CHINA)
        val timeStr = "今天 " + sdf.format(Date())
        parentMood.value = mood
        parentEnergy.value = energy
        parentMoodUpdated.value = timeStr
        parentMoodTimestamp.value = nowMs

        moodPrefs.edit()
            .putString("parent_mood", mood)
            .putInt("parent_energy", energy)
            .putString("parent_mood_updated", timeStr)
            .putLong("parent_mood_ts", nowMs)
            .apply()

        triggerAutoSyncPush()
    }

    // Role switcher & passcode validation
    fun verifyAndSwitchToChildMode(enteredCode: String): Boolean {
        if (enteredCode.trim() == childPasscode.value.trim()) {
            currentRole.value = UserRole.CHILD
            rolePrefs.edit().putString("current_role", UserRole.CHILD.name).commit()
            if (isBackgroundLocationServiceEnabled.value) {
                com.example.service.FamilyLocationService.start(getApplication())
            }
            return true
        }
        return false
    }

    fun switchToParentMode() {
        currentRole.value = UserRole.PARENT
        rolePrefs.edit().putString("current_role", UserRole.PARENT.name).commit()
        com.example.service.FamilyLocationService.stop(getApplication())
    }

    fun updateChildPasscode(newCode: String): Boolean {
        val clean = newCode.trim()
        if (clean.length >= 4) {
            childPasscode.value = clean
            rolePrefs.edit().putString("child_passcode", clean).commit()
            return true
        }
        return false
    }

    // Database flows
    val events: StateFlow<List<FamilyEvent>> = repository.allEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val calendarNotes: StateFlow<List<CalendarNote>> = repository.allCalendarNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visits: StateFlow<List<HomeVisitRecord>> = repository.allVisits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val calls: StateFlow<List<CallRecord>> = repository.allCalls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<FamilyMemory>> = repository.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val arrivalLogs: StateFlow<List<SafeArrivalLog>> = repository.allArrivalLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Geofence / Rental home safe arrival & GPS tracking
    val isBackgroundLocationServiceEnabled = MutableStateFlow(
        rentalPrefs.getBoolean("bg_location_enabled", true)
    )

    fun toggleBackgroundLocationService(enabled: Boolean, context: Context) {
        isBackgroundLocationServiceEnabled.value = enabled
        rentalPrefs.edit().putBoolean("bg_location_enabled", enabled).apply()
        if (enabled && currentRole.value == UserRole.CHILD) {
            com.example.service.FamilyLocationService.start(context)
        } else {
            com.example.service.FamilyLocationService.stop(context)
        }
    }

    val rentalAddress = MutableStateFlow(
        rentalPrefs.getString("rental_address", "阳光里青年社区 3号楼 502室 (出租房)") ?: "阳光里青年社区 3号楼 502室 (出租房)"
    )
    val rentalLatitude = MutableStateFlow(rentalPrefs.getFloat("rental_lat", 0f).toDouble())
    val rentalLongitude = MutableStateFlow(rentalPrefs.getFloat("rental_lng", 0f).toDouble())
    val rentalRadiusMeters = MutableStateFlow(rentalPrefs.getInt("rental_radius", 500))
    val rentalLocationTimestamp = MutableStateFlow(rentalPrefs.getLong("rental_location_ts", 0L))

    val currentLatitude = MutableStateFlow<Double?>(null)
    val currentLongitude = MutableStateFlow<Double?>(null)
    val distanceToRentalMeters = MutableStateFlow<Double?>(
        if (rentalPrefs.contains("distance_to_rental")) {
            val d = rentalPrefs.getFloat("distance_to_rental", -1f).toDouble()
            if (d >= 0) d else null
        } else null
    )
    val isInsideRentalZone = MutableStateFlow(rentalPrefs.getBoolean("is_inside_zone", false))
    val currentGpsInfo = MutableStateFlow<String?>(rentalPrefs.getString("child_gps_info", "待获取实时位置"))
    val isGpsLoading = MutableStateFlow(false)

    /**
     * Compute distance between two coordinates using standard Haversine formula in meters
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    private fun processLocationUpdate(location: android.location.Location) {
        currentLatitude.value = location.latitude
        currentLongitude.value = location.longitude
        val latStr = String.format(Locale.US, "%.4f", location.latitude)
        val lngStr = String.format(Locale.US, "%.4f", location.longitude)

        val rLat = rentalLatitude.value
        val rLng = rentalLongitude.value
        if (rLat != 0.0 && rLng != 0.0) {
            val dist = calculateDistanceMeters(location.latitude, location.longitude, rLat, rLng)
            distanceToRentalMeters.value = dist
            val inside = dist <= rentalRadiusMeters.value
            val wasInside = isInsideRentalZone.value
            isInsideRentalZone.value = inside
            if (inside) {
                currentGpsInfo.value = "已在出租屋范围内 (距出租房约 ${dist.toInt()}米)"
                // Auto-trigger safe arrival report only when newly entering zone and not recently reported (30 min cooldown)
                val latestArrival = arrivalLogs.value.maxByOrNull { it.timestamp }
                val lastTs = latestArrival?.timestamp ?: 0L
                val nowMs = System.currentTimeMillis()
                if (!wasInside && (nowMs - lastTs > 30 * 60 * 1000L)) {
                    triggerArrivedAtRentalHome("【GPS自动检测】检测到已安全回到出租屋（距出租房约${dist.toInt()}米），已自动为您向父母报平安～")
                }
            } else {
                val distStr = if (dist >= 1000) String.format(Locale.US, "%.1f公里", dist / 1000.0) else "${dist.toInt()}米"
                currentGpsInfo.value = "在外奔波中 (距出租屋约 $distStr)"
            }

            rentalPrefs.edit()
                .putBoolean("is_inside_zone", inside)
                .putFloat("distance_to_rental", dist.toFloat())
                .putString("child_gps_info", currentGpsInfo.value)
                .apply()
        } else {
            currentGpsInfo.value = "GPS: $latStr°N, $lngStr°E (可一键设为出租屋)"
        }
    }

    fun fetchRealTimeGps(context: Context) {
        viewModelScope.launch {
            isGpsLoading.value = true
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                if (lm != null) {
                    var bestLocation: android.location.Location? = null
                    try {
                        if (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                            bestLocation = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                        }
                        if (bestLocation == null && lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                            bestLocation = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                        }
                    } catch (_: SecurityException) {}

                    if (bestLocation != null) {
                        processLocationUpdate(bestLocation)
                    }

                    // Request fresh active location update from providers
                    try {
                        val listener = object : android.location.LocationListener {
                            override fun onLocationChanged(loc: android.location.Location) {
                                processLocationUpdate(loc)
                                try {
                                    lm.removeUpdates(this)
                                } catch (_: Exception) {}
                                isGpsLoading.value = false
                            }
                            override fun onProviderEnabled(provider: String) {}
                            override fun onProviderDisabled(provider: String) {}
                            @Deprecated("Deprecated in Java")
                            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                        }
                        if (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                            lm.requestLocationUpdates(
                                android.location.LocationManager.GPS_PROVIDER,
                                1000L,
                                1f,
                                listener,
                                android.os.Looper.getMainLooper()
                            )
                        }
                        if (lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                            lm.requestLocationUpdates(
                                android.location.LocationManager.NETWORK_PROVIDER,
                                1000L,
                                1f,
                                listener,
                                android.os.Looper.getMainLooper()
                            )
                        }
                    } catch (_: SecurityException) {}
                }
            } catch (e: Exception) {
                currentGpsInfo.value = "无法获取当前GPS位置: ${e.message}"
            } finally {
                isGpsLoading.value = false
            }
        }
    }

    /**
     * 1-Tap: Set current GPS location as rental home base point
     */
    fun setRentalLocationFromCurrentGps(addressName: String? = null, radius: Int? = null) {
        val curLat = currentLatitude.value
        val curLng = currentLongitude.value
        if (curLat != null && curLng != null) {
            val nowMs = System.currentTimeMillis()
            rentalLatitude.value = curLat
            rentalLongitude.value = curLng
            if (!addressName.isNullOrBlank()) {
                rentalAddress.value = addressName
            }
            if (radius != null) {
                rentalRadiusMeters.value = radius
            }
            rentalLocationTimestamp.value = nowMs
            distanceToRentalMeters.value = 0.0
            isInsideRentalZone.value = true

            rentalPrefs.edit()
                .putString("rental_address", rentalAddress.value)
                .putFloat("rental_lat", curLat.toFloat())
                .putFloat("rental_lng", curLng.toFloat())
                .putInt("rental_radius", rentalRadiusMeters.value)
                .putLong("rental_location_ts", nowMs)
                .commit()

            val latStr = String.format(Locale.US, "%.4f", curLat)
            val lngStr = String.format(Locale.US, "%.4f", curLng)
            currentGpsInfo.value = "已将当前GPS($latStr, $lngStr)设为出租屋基准 (围栏半径${rentalRadiusMeters.value}米)"
            triggerAutoSyncPush()
        }
    }

    /**
     * Update custom coordinates & radius
     */
    fun updateRentalLocationConfig(addressName: String, lat: Double, lng: Double, radius: Int) {
        val nowMs = System.currentTimeMillis()
        rentalAddress.value = addressName
        rentalLatitude.value = lat
        rentalLongitude.value = lng
        rentalRadiusMeters.value = radius
        rentalLocationTimestamp.value = nowMs

        rentalPrefs.edit()
            .putString("rental_address", addressName)
            .putFloat("rental_lat", lat.toFloat())
            .putFloat("rental_lng", lng.toFloat())
            .putInt("rental_radius", radius)
            .putLong("rental_location_ts", nowMs)
            .commit()

        val curLat = currentLatitude.value
        val curLng = currentLongitude.value
        if (curLat != null && curLng != null && lat != 0.0 && lng != 0.0) {
            val dist = calculateDistanceMeters(curLat, curLng, lat, lng)
            distanceToRentalMeters.value = dist
            val inside = dist <= radius
            isInsideRentalZone.value = inside
            if (inside) {
                currentGpsInfo.value = "已在出租屋范围内 (距出租房约 ${dist.toInt()}米)"
            } else {
                val distStr = if (dist >= 1000) String.format(Locale.US, "%.1f公里", dist / 1000.0) else "${dist.toInt()}米"
                currentGpsInfo.value = "在外奔波中 (距出租屋约 $distStr)"
            }
        }
        triggerAutoSyncPush()
    }

    // Notification alert modal (Simulating what parents receive or current arrival banner)
    private val _parentNotificationPopup = MutableStateFlow<ParentNotificationData?>(null)
    val parentNotificationPopup: StateFlow<ParentNotificationData?> = _parentNotificationPopup.asStateFlow()

    fun dismissArrivalPopup() {
        _parentNotificationPopup.value = null
    }

    // Trigger arrival at rental home (auto or manual simulated geofence trigger)
    fun triggerArrivedAtRentalHome(customNote: String = "爸妈，我顺利回到出租屋了，已经到家冲了热水澡，一切安好，请放心～") {
        viewModelScope.launch {
            val curLat = currentLatitude.value
            val curLng = currentLongitude.value
            val rLat = rentalLatitude.value
            val rLng = rentalLongitude.value

            val dist = if (curLat != null && curLng != null && rLat != 0.0 && rLng != 0.0) {
                calculateDistanceMeters(curLat, curLng, rLat, rLng)
            } else {
                distanceToRentalMeters.value
            }

            if (dist != null) {
                distanceToRentalMeters.value = dist
                isInsideRentalZone.value = (dist <= rentalRadiusMeters.value)
            }

            val now = Date()
            val timeSdf = SimpleDateFormat("HH:mm", Locale.CHINA)
            val fullSdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
            val isoSdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            val timeStr = timeSdf.format(now)

            triggerVibration()

            val distStr = if (dist != null) {
                if (dist >= 1000) String.format(Locale.US, "%.1f公里", dist / 1000.0) else "${dist.toInt()}米"
            } else null

            val gpsInfoNote = if (distStr != null) {
                if (isInsideRentalZone.value) " [GPS实测: 已在围栏内, 距出租屋约$distStr]"
                else " [GPS实测: 距出租屋约$distStr]"
            } else ""

            val finalMessage = customNote + gpsInfoNote

            val log = SafeArrivalLog(
                timestamp = now.time,
                timeString = fullSdf.format(now),
                isoDate = isoSdf.format(now),
                destination = rentalAddress.value,
                alertMessage = finalMessage,
                notifiedParents = true
            )
            repository.insertArrivalLog(log)

            // Prevent self-notification on child's device
            val notifPrefs = getApplication<Application>().getSharedPreferences("family_notification_prefs", Context.MODE_PRIVATE)
            notifPrefs.edit().putLong("last_notified_arrival_ts", now.time).apply()

            if (currentRole.value == UserRole.PARENT) {
                _parentNotificationPopup.value = ParentNotificationData(
                    arrivalTime = timeStr,
                    address = rentalAddress.value,
                    childMessage = customNote,
                    distanceInfo = if (distStr != null) "实测距出租房约 $distStr" else "已记录报备"
                )
            }

            // Immediately push to GitHub transit without debounce delay so parents receive it instantly
            val cfg = syncConfig.value
            val effectiveGistId = if (cfg.gistId.isNotBlank()) cfg.gistId else GitHubSyncManager.BUILT_IN_GIST_ID
            val effectiveToken = if (cfg.githubToken.isNotBlank()) cfg.githubToken else GitHubSyncManager.BUILT_IN_GITHUB_TOKEN
            pushToGitHubTransit(
                token = effectiveToken,
                gistId = effectiveGistId,
                owner = cfg.repoOwner,
                repo = cfg.repoName,
                filePath = cfg.filePath,
                isGistMode = cfg.isGistMode,
                isSilent = false
            )
        }
    }

    /**
     * Check if there's a new arrival log from the child and notify the parents
     */
    private fun checkAndNotifyParentArrival(arrivalLogs: List<SafeArrivalLog>) {
        if (arrivalLogs.isEmpty()) return
        val notifPrefs = getApplication<Application>().getSharedPreferences("family_notification_prefs", Context.MODE_PRIVATE)
        val lastNotifiedTimestamp = notifPrefs.getLong("last_notified_arrival_ts", 0L)
        val latest = arrivalLogs.maxByOrNull { it.timestamp } ?: return

        val isRecent = (System.currentTimeMillis() - latest.timestamp) < 24 * 3600 * 1000L
        if (latest.timestamp > lastNotifiedTimestamp && (lastNotifiedTimestamp > 0L || isRecent)) {
            notifPrefs.edit().putLong("last_notified_arrival_ts", latest.timestamp).apply()

            val isParent = currentRole.value == UserRole.PARENT
            _parentNotificationPopup.value = ParentNotificationData(
                arrivalTime = latest.timeString,
                address = latest.destination,
                childMessage = latest.alertMessage,
                distanceInfo = if (isParent) "孩子已安全到达出租屋" else "已同步到家报平安记录"
            )
            FamilyNotificationHelper.showSafeArrivalNotification(
                context = getApplication<Application>(),
                title = if (isParent) "🏠 孩子已安全回到出租屋！" else "🏠 已同步收到到家报平安！",
                message = "${latest.timeString}：${latest.alertMessage}",
                location = latest.destination
            )
            triggerVibration()
        }
    }

    fun updateRentalAddress(newAddress: String) {
        val nowMs = System.currentTimeMillis()
        rentalAddress.value = newAddress
        rentalLocationTimestamp.value = nowMs
        rentalPrefs.edit()
            .putString("rental_address", newAddress)
            .putLong("rental_location_ts", nowMs)
            .commit()
        triggerAutoSyncPush()
    }

    private fun triggerVibration() {
        try {
            val context = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(180)
                }
            }
        } catch (_: Exception) {}
    }

    // Event actions (Support Lunar birthdays & festivals)
    fun addLunarBirthday(
        title: String,
        lunarMonth: Int,
        lunarDay: Int,
        person: String,
        note: String
    ) {
        viewModelScope.launch {
            val nextSolar = LunarCalendarUtil.getNextSolarDateForLunar(lunarMonth, lunarDay)
            val solarStr = String.format(Locale.US, "%04d-%02d-%02d", nextSolar.first, nextSolar.second, nextSolar.third)
            val monthName = LunarCalendarUtil.getLunarMonthName(lunarMonth)
            val dayName = LunarCalendarUtil.getLunarDayName(lunarDay)
            val displayTitle = if (title.contains("农历")) title else "$title (农历$monthName$dayName)"

            val newEvent = FamilyEvent(
                title = displayTitle,
                isLunar = true,
                lunarMonth = lunarMonth,
                lunarDay = lunarDay,
                targetDate = solarStr,
                category = "BIRTHDAY",
                person = person,
                note = note
            )
            val fallbackKey = syncManager.getEventSyncKey(newEvent)
            syncManager.unmarkKeyDeleted(fallbackKey)
            syncManager.unmarkKeyDeleted(newEvent.syncId)
            repository.insertEvent(newEvent)
            triggerAutoSyncPush()
        }
    }

    fun addSolarEvent(
        title: String,
        targetDate: String,
        category: String,
        person: String,
        note: String
    ) {
        viewModelScope.launch {
            val newEvent = FamilyEvent(
                title = title,
                isLunar = false,
                targetDate = targetDate,
                category = category,
                person = person,
                note = note
            )
            val fallbackKey = syncManager.getEventSyncKey(newEvent)
            syncManager.unmarkKeyDeleted(fallbackKey)
            syncManager.unmarkKeyDeleted(newEvent.syncId)
            repository.insertEvent(newEvent)
            triggerAutoSyncPush()
        }
    }

    fun updateEvent(event: FamilyEvent) {
        viewModelScope.launch {
            repository.updateEvent(event.copy(updatedAt = System.currentTimeMillis()))
            triggerAutoSyncPush()
        }
    }

    fun deleteEvent(id: Long) {
        viewModelScope.launch {
            val event = events.value.find { it.id == id }
            if (event != null) {
                val key = syncManager.getEventSyncKey(event)
                syncManager.markKeyDeleted(key)
                if (event.syncId.isNotBlank()) syncManager.markKeyDeleted(event.syncId)
            }
            repository.deleteEvent(id)
            triggerAutoSyncPush()
        }
    }

    // Calendar custom notes
    fun addCalendarNote(dateString: String, title: String, content: String, tag: String) {
        viewModelScope.launch {
            val newNote = CalendarNote(
                dateString = dateString,
                title = title,
                content = content,
                tag = tag
            )
            val fallbackKey = syncManager.getNoteSyncKey(newNote)
            syncManager.unmarkKeyDeleted(fallbackKey)
            syncManager.unmarkKeyDeleted(newNote.syncId)
            repository.insertCalendarNote(newNote)
            triggerAutoSyncPush()
        }
    }

    fun updateCalendarNote(note: CalendarNote) {
        viewModelScope.launch {
            repository.updateCalendarNote(note.copy(updatedAt = System.currentTimeMillis()))
            triggerAutoSyncPush()
        }
    }

    fun deleteCalendarNote(id: Long) {
        viewModelScope.launch {
            val note = calendarNotes.value.find { it.id == id }
            if (note != null) {
                val key = syncManager.getNoteSyncKey(note)
                syncManager.markKeyDeleted(key)
                if (note.syncId.isNotBlank()) syncManager.markKeyDeleted(note.syncId)
            }
            repository.deleteCalendarNote(id)
            triggerAutoSyncPush()
        }
    }

    // Home Visit Check-in
    fun addHomeVisit(dateString: String, note: String, meals: String, isoDate: String = "", photoUri: String? = null) {
        viewModelScope.launch {
            val actualIso = if (isoDate.isNotBlank()) isoDate else SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
            val newVisit = HomeVisitRecord(
                visitTimestamp = System.currentTimeMillis(),
                dateString = dateString,
                isoDate = actualIso,
                note = note,
                mealNotes = meals,
                photoUri = photoUri
            )
            val fallbackKey = syncManager.getVisitSyncKey(newVisit)
            syncManager.unmarkKeyDeleted(fallbackKey)
            syncManager.unmarkKeyDeleted(newVisit.syncId)
            repository.insertVisit(newVisit)
            triggerAutoSyncPush()
        }
    }

    fun updateVisit(visit: HomeVisitRecord) {
        viewModelScope.launch {
            repository.updateVisit(visit.copy(updatedAt = System.currentTimeMillis()))
            triggerAutoSyncPush()
        }
    }

    fun deleteVisit(id: Long) {
        viewModelScope.launch {
            val visit = visits.value.find { it.id == id }
            if (visit != null) {
                val key = syncManager.getVisitSyncKey(visit)
                syncManager.markKeyDeleted(key)
                if (visit.syncId.isNotBlank()) syncManager.markKeyDeleted(visit.syncId)
            }
            repository.deleteVisit(id)
            triggerAutoSyncPush()
        }
    }

    // Call logging with optional audio voice memo / recording
    fun addCallRecord(
        person: String,
        durationMinutes: Int,
        topics: String,
        isoDate: String = "",
        audioUri: String? = null,
        audioDurationSeconds: Int = 0
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = Date()
            val sdf = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
            val actualIso = if (isoDate.isNotBlank()) isoDate else SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(now)

            var finalAudioUri: String? = null
            var finalAudioDur = audioDurationSeconds
            if (!audioUri.isNullOrBlank()) {
                try {
                    val parsed = Uri.parse(audioUri)
                    val (localFile, measuredDuration) = com.example.util.FamilyMediaStorage.copyAudioUriToLocalStorage(
                        getApplication(),
                        parsed
                    )
                    if (localFile != null && localFile.exists()) {
                        finalAudioUri = localFile.absolutePath
                        if (finalAudioDur <= 0) {
                            finalAudioDur = measuredDuration
                        }
                    } else if (java.io.File(audioUri).exists()) {
                        finalAudioUri = audioUri
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FamilyViewModel", "Error saving audio recording", e)
                }
            }

            val newCall = CallRecord(
                callTimestamp = now.time,
                dateString = sdf.format(now),
                isoDate = actualIso,
                person = person,
                durationMinutes = durationMinutes,
                topics = topics,
                audioUri = finalAudioUri,
                audioDurationSeconds = finalAudioDur
            )
            val fallbackKey = syncManager.getCallSyncKey(newCall)
            syncManager.unmarkKeyDeleted(fallbackKey)
            syncManager.unmarkKeyDeleted(newCall.syncId)
            repository.insertCall(newCall)
            triggerAutoSyncPush()
        }
    }

    fun updateCall(call: CallRecord) {
        viewModelScope.launch {
            repository.updateCall(call.copy(updatedAt = System.currentTimeMillis()))
            triggerAutoSyncPush()
        }
    }

    fun deleteCall(id: Long) {
        viewModelScope.launch {
            val call = calls.value.find { it.id == id }
            if (call != null) {
                val key = syncManager.getCallSyncKey(call)
                syncManager.markKeyDeleted(key)
                if (call.syncId.isNotBlank()) syncManager.markKeyDeleted(call.syncId)
            }
            repository.deleteCall(id)
            triggerAutoSyncPush()
        }
    }

    // Timeline memories with full photo and video support + crash-proof local storage
    fun addMemory(
        title: String,
        dateString: String,
        location: String,
        content: String,
        photoUri: String?,
        tag: String,
        isoDate: String = "",
        mediaType: String = "IMAGE",
        videoUri: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val actualIso = if (isoDate.isNotBlank()) isoDate else SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
            var finalPhotoUri = photoUri
            var finalVideoUri = videoUri
            var mediaBase64: String? = null

            if (mediaType == "VIDEO" && !videoUri.isNullOrBlank()) {
                try {
                    val parsed = Uri.parse(videoUri)
                    val localVidFile = com.example.util.FamilyMediaStorage.copyUriToLocalStorage(
                        getApplication(),
                        parsed,
                        isVideo = true
                    )
                    if (localVidFile != null && localVidFile.exists()) {
                        finalVideoUri = localVidFile.absolutePath
                        val thumbFile = com.example.util.FamilyMediaStorage.extractVideoThumbnail(
                            getApplication(),
                            localVidFile
                        )
                        if (thumbFile != null && thumbFile.exists()) {
                            finalPhotoUri = thumbFile.absolutePath
                            mediaBase64 = com.example.util.FamilyMediaStorage.compressImageToBase64(thumbFile)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FamilyViewModel", "Error processing video memory", e)
                }
            } else if (mediaType == "IMAGE" && !photoUri.isNullOrBlank()) {
                try {
                    val parsed = Uri.parse(photoUri)
                    val localImgFile = com.example.util.FamilyMediaStorage.copyUriToLocalStorage(
                        getApplication(),
                        parsed,
                        isVideo = false
                    )
                    if (localImgFile != null && localImgFile.exists()) {
                        finalPhotoUri = localImgFile.absolutePath
                        mediaBase64 = com.example.util.FamilyMediaStorage.compressImageToBase64(localImgFile)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FamilyViewModel", "Error processing photo memory", e)
                }
            }

            val newMemory = FamilyMemory(
                title = title,
                dateString = dateString,
                isoDate = actualIso,
                location = location,
                content = content,
                photoUri = finalPhotoUri,
                mediaType = mediaType,
                videoUri = finalVideoUri,
                mediaBase64 = mediaBase64,
                videoBase64 = null, // Always keep null to protect SQLite 2MB CursorWindow
                tag = tag
            )
            val fallbackKey = syncManager.getMemorySyncKey(newMemory)
            syncManager.unmarkKeyDeleted(fallbackKey)
            syncManager.unmarkKeyDeleted(newMemory.syncId)
            repository.insertMemory(newMemory)
            triggerAutoSyncPush()
        }
    }

    fun updateMemory(memory: FamilyMemory) {
        viewModelScope.launch {
            repository.updateMemory(memory.copy(updatedAt = System.currentTimeMillis()))
            triggerAutoSyncPush()
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            val mem = memories.value.find { it.id == id }
            if (mem != null) {
                val key = syncManager.getMemorySyncKey(mem)
                syncManager.markKeyDeleted(key)
                if (mem.syncId.isNotBlank()) syncManager.markKeyDeleted(mem.syncId)
            }
            repository.deleteMemory(id)
            triggerAutoSyncPush()
        }
    }

    // Calculate countdown days & next solar date
    fun calculateDaysRemainingForEvent(event: FamilyEvent): Pair<Int, String> {
        return if (event.isLunar) {
            val nextSolar = LunarCalendarUtil.getNextSolarDateForLunar(event.lunarMonth, event.lunarDay)
            val days = LunarCalendarUtil.calculateDaysUntil(nextSolar.first, nextSolar.second, nextSolar.third)
            val solarStr = "${nextSolar.second}月${nextSolar.third}日"
            Pair(days, solarStr)
        } else {
            val days = calculateSolarDaysRemaining(event.targetDate)
            Pair(days, event.targetDate)
        }
    }

    private fun calculateSolarDaysRemaining(targetDateStr: String): Int {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return try {
            val target = Calendar.getInstance()
            val parsed = sdf.parse(targetDateStr) ?: return 0
            target.time = parsed

            val targetThisYear = Calendar.getInstance().apply {
                time = target.time
                set(Calendar.YEAR, now.get(Calendar.YEAR))
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (targetThisYear.before(now)) {
                targetThisYear.add(Calendar.YEAR, 1)
            }
            val diffMs = targetThisYear.timeInMillis - now.timeInMillis
            TimeUnit.MILLISECONDS.toDays(diffMs).toInt()
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Automatic calendar mapping:
     * Returns all events, visits, calls, memories, and custom notes that map onto this day
     */
    fun getEventsMappedForDate(
        year: Int,
        month: Int,
        day: Int
    ): List<CalendarDayEvent> {
        val isoStr = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
        val lunar = LunarCalendarUtil.solarToLunar(year, month, day)
        val result = mutableListOf<CalendarDayEvent>()

        // 1. Check Birthdays & Holidays
        events.value.forEach { event ->
            if (event.isLunar) {
                if (event.lunarMonth == lunar.month && event.lunarDay == lunar.day) {
                    result.add(
                        CalendarDayEvent(
                            type = "BIRTHDAY",
                            title = "🎂 " + event.title,
                            detail = if (event.note.isNotBlank()) event.note else "祝${event.person}生日快乐！",
                            timeOrTag = "农历${lunar.toShortChineseString()}",
                            isLunar = true,
                            event = event
                        )
                    )
                }
            } else {
                if (event.targetDate.endsWith(String.format(Locale.US, "-%02d-%02d", month, day))) {
                    result.add(
                        CalendarDayEvent(
                            type = "BIRTHDAY",
                            title = "🎈 " + event.title,
                            detail = event.note,
                            timeOrTag = "公历纪念日",
                            event = event
                        )
                    )
                }
            }
        }

        // 2. Check Traditional Festivals
        val festival = LunarCalendarUtil.getLunarFestival(lunar.month, lunar.day)
        if (festival != null) {
            result.add(
                CalendarDayEvent(
                    type = "FESTIVAL",
                    title = "🏮 传统节日 · $festival",
                    detail = "农历${lunar.toShortChineseString()} 阖家欢庆",
                    timeOrTag = "农历佳节",
                    isLunar = true
                )
            )
        }

        // 3. Check Home Visits
        visits.value.forEach { visit ->
            if (visit.isoDate == isoStr || visit.dateString.contains("${month}月${day}日")) {
                result.add(
                    CalendarDayEvent(
                        type = "VISIT",
                        title = "🏠 回家打卡记录",
                        detail = if (visit.mealNotes.isNotBlank()) "品尝了：${visit.mealNotes}。${visit.note}" else visit.note,
                        timeOrTag = "回家陪伴",
                        visit = visit
                    )
                )
            }
        }

        // 4. Check Memories & Dining
        memories.value.forEach { memory ->
            if (memory.isoDate == isoStr || memory.dateString.contains("${year}年${month}月${day}日") || memory.dateString.contains("${month}月${day}日")) {
                result.add(
                    CalendarDayEvent(
                        type = "MEMORY",
                        title = "📸 " + memory.title,
                        detail = memory.content,
                        timeOrTag = memory.tag,
                        memory = memory
                    )
                )
            }
        }

        // 5. Check Calls
        calls.value.forEach { call ->
            if (call.isoDate == isoStr || call.dateString.contains("${month}月${day}日")) {
                result.add(
                    CalendarDayEvent(
                        type = "CALL",
                        title = "📞 与${call.person}亲情通话",
                        detail = "通话 ${call.durationMinutes} 分钟，话题：${call.topics}",
                        timeOrTag = "${call.durationMinutes}分钟",
                        call = call
                    )
                )
            }
        }

        // 6. Check Calendar Notes
        calendarNotes.value.forEach { note ->
            if (note.dateString == isoStr) {
                result.add(
                    CalendarDayEvent(
                        type = "NOTE",
                        title = "📝 " + note.title,
                        detail = note.content,
                        timeOrTag = note.tag,
                        note = note
                    )
                )
            }
        }

        return result
    }

    private suspend fun buildCurrentBackup(timeStr: String): FullFamilyBackup {
        val arrivalLogsSnapshot = repository.getArrivalLogsSnapshot()
        val latestArrivalTs = arrivalLogsSnapshot.maxOfOrNull { it.timestamp } ?: 0L
        return FullFamilyBackup(
            lastSyncTime = timeStr,
            syncedBy = "家庭成员",
            rentalAddress = rentalAddress.value,
            rentalLatitude = rentalLatitude.value,
            rentalLongitude = rentalLongitude.value,
            rentalRadiusMeters = rentalRadiusMeters.value,
            rentalLocationTimestamp = rentalLocationTimestamp.value,
            isInsideRentalZone = isInsideRentalZone.value,
            distanceToRentalMeters = distanceToRentalMeters.value,
            childGpsInfo = currentGpsInfo.value,
            lastArrivalTimestamp = latestArrivalTs,
            childMood = childMood.value,
            childEnergy = childEnergy.value,
            childMoodUpdated = childMoodUpdated.value,
            childMoodTimestamp = childMoodTimestamp.value,
            parentMood = parentMood.value,
            parentEnergy = parentEnergy.value,
            parentMoodUpdated = parentMoodUpdated.value,
            parentMoodTimestamp = parentMoodTimestamp.value,
            events = repository.getEventsSnapshot(),
            calendarNotes = repository.getCalendarNotesSnapshot(),
            visits = repository.getVisitsSnapshot(),
            calls = repository.getCallsSnapshot(),
            memories = repository.getMemoriesSnapshot(),
            arrivalLogs = arrivalLogsSnapshot,
            deletedKeys = syncManager.getDeletedKeys().toList(),
            syncTimestamp = System.currentTimeMillis()
        )
    }

    private suspend fun applyBackupToLocal(finalBackup: FullFamilyBackup) {
        // 1. Restore Room database tables
        repository.restoreAllData(
            events = finalBackup.events,
            calendarNotes = finalBackup.calendarNotes,
            visits = finalBackup.visits,
            calls = finalBackup.calls,
            memories = finalBackup.memories,
            arrivalLogs = finalBackup.arrivalLogs
        )

        // 2. Restore shared rental address & coordinates if remote has newer config
        if (finalBackup.rentalLocationTimestamp >= rentalLocationTimestamp.value || rentalLocationTimestamp.value == 0L) {
            if (finalBackup.rentalAddress.isNotBlank()) rentalAddress.value = finalBackup.rentalAddress
            if (finalBackup.rentalLatitude != 0.0) rentalLatitude.value = finalBackup.rentalLatitude
            if (finalBackup.rentalLongitude != 0.0) rentalLongitude.value = finalBackup.rentalLongitude
            if (finalBackup.rentalRadiusMeters > 0) rentalRadiusMeters.value = finalBackup.rentalRadiusMeters
            rentalLocationTimestamp.value = finalBackup.rentalLocationTimestamp

            rentalPrefs.edit()
                .putString("rental_address", rentalAddress.value)
                .putFloat("rental_lat", rentalLatitude.value.toFloat())
                .putFloat("rental_lng", rentalLongitude.value.toFloat())
                .putInt("rental_radius", rentalRadiusMeters.value)
                .putLong("rental_location_ts", rentalLocationTimestamp.value)
                .commit()
        }

        // 3. Update moods & timestamps
        childMood.value = finalBackup.childMood
        childEnergy.value = finalBackup.childEnergy
        childMoodUpdated.value = finalBackup.childMoodUpdated
        childMoodTimestamp.value = finalBackup.childMoodTimestamp

        parentMood.value = finalBackup.parentMood
        parentEnergy.value = finalBackup.parentEnergy
        parentMoodUpdated.value = finalBackup.parentMoodUpdated
        parentMoodTimestamp.value = finalBackup.parentMoodTimestamp

        // Persist moods to SharedPreferences
        moodPrefs.edit()
            .putString("child_mood", finalBackup.childMood)
            .putInt("child_energy", finalBackup.childEnergy)
            .putString("child_mood_updated", finalBackup.childMoodUpdated)
            .putLong("child_mood_ts", finalBackup.childMoodTimestamp)
            .putString("parent_mood", finalBackup.parentMood)
            .putInt("parent_energy", finalBackup.parentEnergy)
            .putString("parent_mood_updated", finalBackup.parentMoodUpdated)
            .putLong("parent_mood_ts", finalBackup.parentMoodTimestamp)
            .apply()

        // 4. Update zone arrival status & distance (only for Parent view; Child device uses live device GPS)
        if (currentRole.value == UserRole.PARENT) {
            val radius = if (finalBackup.rentalRadiusMeters > 0) finalBackup.rentalRadiusMeters else rentalRadiusMeters.value
            if (finalBackup.distanceToRentalMeters != null) {
                distanceToRentalMeters.value = finalBackup.distanceToRentalMeters
                isInsideRentalZone.value = (finalBackup.distanceToRentalMeters <= radius)
            } else {
                isInsideRentalZone.value = finalBackup.isInsideRentalZone
            }

            if (!finalBackup.childGpsInfo.isNullOrBlank()) {
                currentGpsInfo.value = finalBackup.childGpsInfo
            } else if (isInsideRentalZone.value) {
                val distText = distanceToRentalMeters.value?.let { " (距出租房约 ${it.toInt()}米)" } ?: ""
                currentGpsInfo.value = "已在出租屋范围内$distText 🏠"
            } else if (distanceToRentalMeters.value != null) {
                val d = distanceToRentalMeters.value!!
                val distStr = if (d >= 1000) String.format(Locale.US, "%.1f公里", d / 1000.0) else "${d.toInt()}米"
                currentGpsInfo.value = "在外奔波中 (距出租屋约 $distStr) 🚶"
            }

            rentalPrefs.edit()
                .putBoolean("is_inside_zone", isInsideRentalZone.value)
                .putFloat("distance_to_rental", (distanceToRentalMeters.value ?: -1.0).toFloat())
                .putString("child_gps_info", currentGpsInfo.value)
                .apply()
        }

        // 5. Alert parent if a new safe arrival report is present
        checkAndNotifyParentArrival(finalBackup.arrivalLogs)
    }

    fun saveSyncConfig(config: SyncConfig) {
        syncManager.saveConfig(config)
        syncConfig.value = config
        // Always pull and merge first on configuration save
        pullFromGitHubTransit(
            config.githubToken,
            config.gistId,
            config.repoOwner,
            config.repoName,
            config.filePath,
            config.isGistMode,
            onSuccess = {
                syncStatusMessage.value = "已加入家庭共享中转，双向融合最新数据！"
            },
            onError = {
                triggerAutoSyncPush()
            }
        )
    }

    fun dismissSyncMessage() {
        syncStatusMessage.value = null
    }

    /**
     * Silent auto-pull on launch with lossless merge
     */
    fun silentAutoPullOnLaunch() {
        val cfg = syncConfig.value
        val effectiveGistId = if (cfg.gistId.isNotBlank()) cfg.gistId else GitHubSyncManager.BUILT_IN_GIST_ID
        val effectiveToken = if (cfg.githubToken.isNotBlank()) cfg.githubToken else GitHubSyncManager.BUILT_IN_GITHUB_TOKEN
        if (effectiveGistId.isBlank() && cfg.repoName.isBlank()) return

        viewModelScope.launch {
            val jsonRes = if (cfg.isGistMode) {
                syncManager.pullFromGist(effectiveToken, effectiveGistId)
            } else {
                syncManager.pullFromRepo(effectiveToken, cfg.repoOwner, cfg.repoName, cfg.filePath)
            }

            if (jsonRes.isSuccess) {
                try {
                    val json = jsonRes.getOrNull() ?: ""
                    if (json.isNotBlank()) {
                        val remoteBackup = syncManager.deserializeFromJson(json)
                        val localBackup = buildCurrentBackup(cfg.lastSyncTime)
                        val merged = syncManager.mergeBackups(local = localBackup, remote = remoteBackup)

                        applyBackupToLocal(merged)

                        val nowStr = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date())
                        syncStatusMessage.value = "已自动融合三人最新状态 ($nowStr)"
                    }
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Explicit trigger for real-time media & timeline sync (can be called from UI)
     */
    fun refreshFamilyMediaSync(onComplete: (String) -> Unit = {}) {
        val cfg = syncConfig.value
        val effectiveGistId = if (cfg.gistId.isNotBlank()) cfg.gistId else GitHubSyncManager.BUILT_IN_GIST_ID
        val effectiveToken = if (cfg.githubToken.isNotBlank()) cfg.githubToken else GitHubSyncManager.BUILT_IN_GITHUB_TOKEN
        if (effectiveGistId.isBlank() && cfg.repoName.isBlank()) {
            onComplete("请先在ID配置页配置家庭中转ID")
            return
        }

        viewModelScope.launch {
            isSyncing.value = true
            val jsonRes = if (cfg.isGistMode) {
                syncManager.pullFromGist(effectiveToken, effectiveGistId)
            } else {
                syncManager.pullFromRepo(effectiveToken, cfg.repoOwner, cfg.repoName, cfg.filePath)
            }

            if (jsonRes.isSuccess) {
                try {
                    val json = jsonRes.getOrNull() ?: ""
                    if (json.isNotBlank()) {
                        val remoteBackup = syncManager.deserializeFromJson(json)
                        val localBackup = buildCurrentBackup(cfg.lastSyncTime)
                        val merged = syncManager.mergeBackups(local = localBackup, remote = remoteBackup)

                        applyBackupToLocal(merged)

                        val nowStr = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date())
                        val msg = "图文视频已实时同步 ($nowStr)"
                        syncStatusMessage.value = msg
                        onComplete(msg)
                    } else {
                        onComplete("云端暂无更新")
                    }
                } catch (e: Exception) {
                    onComplete("同步解析失败")
                }
            } else {
                onComplete("网络未连通，稍后重试")
            }
            isSyncing.value = false
        }
    }

    /**
     * Automatically schedule a push to GitHub whenever data changes
     * (Debounced 1.2s so multiple rapid actions don't spam the API)
     */
    private fun triggerAutoSyncPush() {
        val cfg = syncConfig.value
        val effectiveGistId = if (cfg.gistId.isNotBlank()) cfg.gistId else GitHubSyncManager.BUILT_IN_GIST_ID
        val effectiveToken = if (cfg.githubToken.isNotBlank()) cfg.githubToken else GitHubSyncManager.BUILT_IN_GITHUB_TOKEN
        if (effectiveGistId.isBlank() && cfg.repoName.isBlank()) return

        autoPushJob?.cancel()
        autoPushJob = viewModelScope.launch {
            delay(1200) // Debounce
            pushToGitHubTransit(
                effectiveToken,
                effectiveGistId,
                cfg.repoOwner,
                cfg.repoName,
                cfg.filePath,
                cfg.isGistMode,
                isSilent = true
            )
        }
    }

    /**
     * Push to GitHub transit station with pre-merge protection
     */
    fun pushToGitHubTransit(
        token: String,
        gistId: String,
        owner: String,
        repo: String,
        filePath: String,
        isGistMode: Boolean,
        isSilent: Boolean = false,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            isSyncing.value = true
            if (!isSilent) syncStatusMessage.value = "正在双向融合并同步至 GitHub 中转站..."

            val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
            val localBackup = buildCurrentBackup(nowStr)
            val effectiveGistId = if (gistId.isNotBlank()) gistId else GitHubSyncManager.BUILT_IN_GIST_ID
            val effectiveToken = if (token.isNotBlank()) token else GitHubSyncManager.BUILT_IN_GITHUB_TOKEN

            // Pre-pull remote to merge any changes from parents/child so NO DATA IS EVER LOST
            val remoteJsonRes = if (isGistMode) {
                syncManager.pullFromGist(effectiveToken, effectiveGistId)
            } else {
                syncManager.pullFromRepo(effectiveToken, owner, repo, filePath)
            }

            val finalBackup = if (remoteJsonRes.isSuccess) {
                try {
                    val remoteJson = remoteJsonRes.getOrNull() ?: ""
                    if (remoteJson.isNotBlank()) {
                        val remoteBackup = syncManager.deserializeFromJson(remoteJson)
                        syncManager.mergeBackups(local = localBackup, remote = remoteBackup)
                    } else localBackup
                } catch (_: Exception) {
                    localBackup
                }
            } else {
                localBackup
            }

            // Apply merged result to local state & check parent notification
            applyBackupToLocal(finalBackup)

            val jsonContent = syncManager.serializeToJson(finalBackup)

            if (isGistMode) {
                val res = syncManager.pushToGist(effectiveToken, effectiveGistId, jsonContent)
                isSyncing.value = false
                if (res.isSuccess) {
                    val finalId = res.getOrNull() ?: effectiveGistId
                    val newConfig = syncConfig.value.copy(
                        githubToken = effectiveToken,
                        gistId = finalId,
                        isGistMode = true,
                        lastSyncTime = nowStr
                    )
                    syncManager.saveConfig(newConfig)
                    syncConfig.value = newConfig
                    syncStatusMessage.value = "三人同享中转已双向融合 ($nowStr)"
                    onSuccess(finalId)
                } else {
                    val errMsg = res.exceptionOrNull()?.message ?: "网络异常"
                    if (!isSilent) syncStatusMessage.value = "同步提示: $errMsg"
                    onError(errMsg)
                }
            } else {
                val res = syncManager.pushToRepo(effectiveToken, owner, repo, filePath, jsonContent)
                isSyncing.value = false
                if (res.isSuccess) {
                    val newConfig = syncConfig.value.copy(
                        githubToken = effectiveToken,
                        repoOwner = owner,
                        repoName = repo,
                        filePath = filePath,
                        isGistMode = false,
                        lastSyncTime = nowStr
                    )
                    syncManager.saveConfig(newConfig)
                    syncConfig.value = newConfig
                    syncStatusMessage.value = "三人同享已同步至 GitHub ($nowStr)"
                    onSuccess("$owner/$repo")
                } else {
                    val errMsg = res.exceptionOrNull()?.message ?: "网络异常"
                    if (!isSilent) syncStatusMessage.value = "同步提示: $errMsg"
                    onError(errMsg)
                }
            }
        }
    }

    /**
     * Pull from GitHub transit station with lossless merge
     */
    fun pullFromGitHubTransit(
        token: String,
        gistId: String,
        owner: String,
        repo: String,
        filePath: String,
        isGistMode: Boolean,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            isSyncing.value = true
            syncStatusMessage.value = "正在从 GitHub 获取并融合家庭数据..."

            val effectiveGistId = if (gistId.isNotBlank()) gistId else GitHubSyncManager.BUILT_IN_GIST_ID
            val effectiveToken = if (token.isNotBlank()) token else GitHubSyncManager.BUILT_IN_GITHUB_TOKEN

            val jsonRes = if (isGistMode) {
                syncManager.pullFromGist(effectiveToken, effectiveGistId)
            } else {
                syncManager.pullFromRepo(effectiveToken, owner, repo, filePath)
            }

            isSyncing.value = false
            if (jsonRes.isSuccess) {
                try {
                    val json = jsonRes.getOrNull() ?: ""
                    val remoteBackup = syncManager.deserializeFromJson(json)
                    val localBackup = buildCurrentBackup(syncConfig.value.lastSyncTime)
                    val mergedBackup = syncManager.mergeBackups(local = localBackup, remote = remoteBackup)

                    // Apply merged result to local state & trigger parent notifications
                    applyBackupToLocal(mergedBackup)

                    val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
                    val newConfig = syncConfig.value.copy(
                        githubToken = effectiveToken,
                        gistId = effectiveGistId,
                        repoOwner = owner,
                        repoName = repo,
                        filePath = filePath,
                        isGistMode = isGistMode,
                        lastSyncTime = nowStr
                    )
                    syncManager.saveConfig(newConfig)
                    syncConfig.value = newConfig

                    syncStatusMessage.value = "已融合至三人最新版本 (${mergedBackup.lastSyncTime})"
                    onSuccess()
                } catch (e: Exception) {
                    syncStatusMessage.value = "数据解析异常: ${e.message}"
                    onError(e.message ?: "解析失败")
                }
            } else {
                val errMsg = jsonRes.exceptionOrNull()?.message ?: "拉取失败"
                syncStatusMessage.value = "拉取提示: $errMsg"
                onError(errMsg)
            }
        }
    }

    // ==========================================
    // OTA App Update Actions (检查更新、下载与安装)
    // ==========================================

    fun checkForUpdates(isManual: Boolean = false) {
        viewModelScope.launch {
            if (isCheckingUpdate.value) return@launch
            isCheckingUpdate.value = true
            updateDownloadState.value = UpdateDownloadState.Checking

            try {
                val result = updateManager.checkForUpdates()
                updateCheckResult.value = result
                isCheckingUpdate.value = false

                if (result.hasUpdate && result.latestVersion != null) {
                    val ignored = updateManager.getIgnoredVersionCode()
                    if (!isManual && result.latestVersion.versionCode == ignored) {
                        // Suppress automatic popup if user previously ignored this version
                        updateDownloadState.value = UpdateDownloadState.Idle
                    } else {
                        updateDownloadState.value = UpdateDownloadState.Idle
                        showUpdateDialog.value = true
                    }
                } else {
                    updateDownloadState.value = UpdateDownloadState.Idle
                    if (isManual) {
                        if (result.error != null) {
                            updateToastMessage.value = result.error
                        } else {
                            updateToastMessage.value = "当前已是最新版本 (v${result.currentVersionName})"
                        }
                    }
                }
            } catch (e: Exception) {
                isCheckingUpdate.value = false
                updateDownloadState.value = UpdateDownloadState.Idle
                if (isManual) {
                    updateToastMessage.value = "检查更新失败: ${e.message}"
                }
            }
        }
    }

    fun startDownloadUpdate(versionInfo: AppVersionInfo) {
        viewModelScope.launch {
            updateDownloadState.value = UpdateDownloadState.Downloading(0f, 0.0, versionInfo.apkSizeMb ?: 0.0)
            val downloadRes = updateManager.downloadApk(versionInfo.downloadUrl) { progress, downloadedMb, totalMb ->
                updateDownloadState.value = UpdateDownloadState.Downloading(progress, downloadedMb, totalMb)
            }

            if (downloadRes.isSuccess) {
                val apkFile = downloadRes.getOrNull()!!
                updateDownloadState.value = UpdateDownloadState.ReadyToInstall(apkFile, versionInfo)
                // Automatically prompt installation
                updateManager.installApk(apkFile)
            } else {
                val err = downloadRes.exceptionOrNull()?.message ?: "下载中断"
                updateDownloadState.value = UpdateDownloadState.Error(err)
            }
        }
    }

    fun installDownloadedApk(file: File) {
        updateManager.installApk(file)
    }

    fun dismissUpdateDialog() {
        showUpdateDialog.value = false
        if (updateDownloadState.value is UpdateDownloadState.Error) {
            updateDownloadState.value = UpdateDownloadState.Idle
        }
    }

    fun ignoreCurrentUpdateVersion(versionCode: Int) {
        updateManager.ignoreVersion(versionCode)
    }

    fun saveUpdateSourceConfig(owner: String, repo: String, customUrl: String) {
        updateManager.saveUpdateConfig(owner, repo, customUrl)
        updateToastMessage.value = "更新源配置已保存"
    }

    fun clearUpdateToast() {
        updateToastMessage.value = null
    }
}
