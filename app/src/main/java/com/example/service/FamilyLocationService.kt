package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.data.db.AppDatabase
import com.example.data.model.SafeArrivalLog
import com.example.data.repository.FamilyRepository
import com.example.data.sync.FullFamilyBackup
import com.example.data.sync.GitHubSyncManager
import com.example.notification.FamilyNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FamilyLocationService : Service(), LocationListener {

    companion object {
        private const val TAG = "FamilyLocationService"
        const val ACTION_START = "ACTION_START_LOCATION_SERVICE"
        const val ACTION_STOP = "ACTION_STOP_LOCATION_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, FamilyLocationService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start FamilyLocationService", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FamilyLocationService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop FamilyLocationService", e)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var locationManager: LocationManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var periodicJob: Job? = null

    private var lastPushedTimestamp = 0L
    private var lastDistanceMeters: Double? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FamilyLocationService onCreate")

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FamilySpace:LocationWakeLock")?.apply {
            setReferenceCounted(false)
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground()
        acquireWakeLock()
        startLocationUpdates()
        startPeriodicSyncLoop()

        return START_STICKY
    }

    private fun startInForeground() {
        val notification = FamilyNotificationHelper.buildLocationServiceNotification(
            this,
            "正在后台守护位置与到家安全同步"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FamilyNotificationHelper.NOTIFICATION_ID_LOCATION_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(
                FamilyNotificationHelper.NOTIFICATION_ID_LOCATION_SERVICE,
                notification
            )
        }
    }

    private fun acquireWakeLock() {
        try {
            wakeLock?.acquire(60 * 60 * 1000L) // 1 hour max, renewed periodically
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        try {
            val lm = locationManager ?: return

            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    60_000L, // 1 minute
                    15f,     // 15 meters
                    this
                )
            }

            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    60_000L,
                    20f,
                    this
                )
            }

            // Also inspect last known location immediately
            val lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val best = listOfNotNull(lastGps, lastNet).maxByOrNull { it.time }
            best?.let { onLocationChanged(it) }

        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing in FamilyLocationService", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates", e)
        }
    }

    private fun startPeriodicSyncLoop() {
        periodicJob?.cancel()
        periodicJob = serviceScope.launch {
            while (isActive) {
                try {
                    sampleCurrentLocation()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic location sample", e)
                }
                delay(3 * 60 * 1000L)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sampleCurrentLocation() {
        try {
            val lm = locationManager ?: return
            val lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val best = listOfNotNull(lastGps, lastNet).maxByOrNull { it.time }
            if (best != null) {
                handleLocation(best)
            }
        } catch (_: SecurityException) {}
    }

    override fun onLocationChanged(location: Location) {
        handleLocation(location)
    }

    private fun handleLocation(location: Location) {
        serviceScope.launch {
            val rentalPrefs = getSharedPreferences("family_rental_prefs", Context.MODE_PRIVATE)
            val rLat = rentalPrefs.getFloat("rental_lat", 0f).toDouble()
            val rLng = rentalPrefs.getFloat("rental_lng", 0f).toDouble()
            val radius = rentalPrefs.getInt("rental_radius", 500)

            if (rLat == 0.0 || rLng == 0.0) {
                return@launch
            }

            val distance = calculateDistanceMeters(location.latitude, location.longitude, rLat, rLng)
            lastDistanceMeters = distance
            val isInside = distance <= radius
            val wasInside = rentalPrefs.getBoolean("is_inside_zone", false)

            val distText = if (distance >= 1000) {
                String.format(Locale.US, "%.1f公里", distance / 1000.0)
            } else {
                "${distance.toInt()}米"
            }

            val statusInfo = if (isInside) {
                "已在出租屋范围内 (距出租房约 ${distance.toInt()}米)"
            } else {
                "在外奔波中 (距出租屋约 $distText)"
            }

            rentalPrefs.edit()
                .putBoolean("is_inside_zone", isInside)
                .putFloat("distance_to_rental", distance.toFloat())
                .putString("child_gps_info", statusInfo)
                .putLong("child_gps_update_ts", System.currentTimeMillis())
                .apply()

            updateNotificationContent(if (isInside) "已在出租屋范围内 🏠 (守护中)" else "在外奔波中 (距出租屋 $distText) · 实时同步中")

            val nowMs = System.currentTimeMillis()
            val lastArrivalTs = rentalPrefs.getLong("last_auto_arrival_ts", 0L)
            if (isInside && !wasInside && (nowMs - lastArrivalTs > 30 * 60 * 1000L)) {
                rentalPrefs.edit().putLong("last_auto_arrival_ts", nowMs).apply()
                recordSafeArrivalLog(distance.toInt())
            }

            if (isInside != wasInside || (nowMs - lastPushedTimestamp > 2 * 60 * 1000L)) {
                lastPushedTimestamp = nowMs
                pushBackgroundStateToTransit()
            }
        }
    }

    private fun updateNotificationContent(content: String) {
        try {
            val notification = FamilyNotificationHelper.buildLocationServiceNotification(this, content)
            val manager = NotificationManagerCompat.from(this)
            manager.notify(FamilyNotificationHelper.NOTIFICATION_ID_LOCATION_SERVICE, notification)
        } catch (_: SecurityException) {}
    }

    private suspend fun recordSafeArrivalLog(distMeters: Int) {
        try {
            val db = AppDatabase.getDatabase(this)
            val repository = FamilyRepository(db.familyDao())

            val rentalPrefs = getSharedPreferences("family_rental_prefs", Context.MODE_PRIVATE)
            val address = rentalPrefs.getString("rental_address", "阳光里青年社区 3号楼 502室 (出租房)") ?: "出租房"

            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val autoMessage = "【后台GPS自动守护】已安全回到出租屋（距基准点约${distMeters}米）"

            val newLog = SafeArrivalLog(
                timestamp = System.currentTimeMillis(),
                timeString = timeStr,
                isoDate = isoDate,
                destination = address,
                alertMessage = autoMessage,
                notifiedParents = true
            )
            repository.insertArrivalLog(newLog)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record safe arrival log", e)
        }
    }

    private suspend fun pushBackgroundStateToTransit() {
        try {
            val syncManager = GitHubSyncManager(this)
            val config = syncManager.loadConfig()
            val effectiveGistId = if (config.gistId.isNotBlank()) config.gistId else GitHubSyncManager.BUILT_IN_GIST_ID
            val effectiveToken = if (config.githubToken.isNotBlank()) config.githubToken else GitHubSyncManager.BUILT_IN_GITHUB_TOKEN

            if (effectiveGistId.isBlank() && config.repoName.isBlank()) return

            val db = AppDatabase.getDatabase(this)
            val repository = FamilyRepository(db.familyDao())

            val events = repository.getEventsSnapshot()
            val notes = repository.getCalendarNotesSnapshot()
            val visits = repository.getVisitsSnapshot()
            val calls = repository.getCallsSnapshot()
            val memories = repository.getMemoriesSnapshot()
            val arrivalLogs = repository.getArrivalLogsSnapshot()

            val moodPrefs = getSharedPreferences("family_mood_prefs", Context.MODE_PRIVATE)
            val rentalPrefs = getSharedPreferences("family_rental_prefs", Context.MODE_PRIVATE)

            val childMood = moodPrefs.getString("child_mood", "元气满满") ?: "元气满满"
            val childEnergy = moodPrefs.getInt("child_energy", 88)
            val childMoodUpdated = moodPrefs.getString("child_mood_updated", "") ?: ""
            val childMoodTs = moodPrefs.getLong("child_mood_ts", 0L)

            val parentMood = moodPrefs.getString("parent_mood", "想念牵挂") ?: "想念牵挂"
            val parentEnergy = moodPrefs.getInt("parent_energy", 85)
            val parentMoodUpdated = moodPrefs.getString("parent_mood_updated", "") ?: ""
            val parentMoodTs = moodPrefs.getLong("parent_mood_ts", 0L)

            val rentalAddress = rentalPrefs.getString("rental_address", "") ?: ""
            val rentalLat = rentalPrefs.getFloat("rental_lat", 0f).toDouble()
            val rentalLng = rentalPrefs.getFloat("rental_lng", 0f).toDouble()
            val rentalRadius = rentalPrefs.getInt("rental_radius", 500)
            val rentalLocationTs = rentalPrefs.getLong("rental_location_ts", 0L)

            val dist = if (rentalPrefs.contains("distance_to_rental")) {
                rentalPrefs.getFloat("distance_to_rental", -1f).toDouble().takeIf { it >= 0 }
            } else null
            val isInsideZone = rentalPrefs.getBoolean("is_inside_zone", false)
            val childGpsInfo = rentalPrefs.getString("child_gps_info", "")

            val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())

            val backup = FullFamilyBackup(
                lastSyncTime = nowStr,
                syncedBy = "孩子端 (后台静默守护)",
                rentalAddress = rentalAddress,
                rentalLatitude = rentalLat,
                rentalLongitude = rentalLng,
                rentalRadiusMeters = rentalRadius,
                rentalLocationTimestamp = rentalLocationTs,
                isInsideRentalZone = isInsideZone,
                distanceToRentalMeters = dist,
                childGpsInfo = childGpsInfo,
                lastArrivalTimestamp = arrivalLogs.maxOfOrNull { it.timestamp } ?: 0L,
                childMood = childMood,
                childEnergy = childEnergy,
                childMoodUpdated = childMoodUpdated,
                childMoodTimestamp = childMoodTs,
                parentMood = parentMood,
                parentEnergy = parentEnergy,
                parentMoodUpdated = parentMoodUpdated,
                parentMoodTimestamp = parentMoodTs,
                events = events,
                calendarNotes = notes,
                visits = visits,
                calls = calls,
                memories = memories,
                arrivalLogs = arrivalLogs,
                syncTimestamp = System.currentTimeMillis()
            )

            val json = syncManager.serializeToJson(backup)

            if (config.isGistMode) {
                syncManager.pushToGist(effectiveToken, effectiveGistId, json)
            } else {
                syncManager.pushToRepo(effectiveToken, config.repoOwner, config.repoName, config.filePath, json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Background transit push failed", e)
        }
    }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FamilyLocationService onDestroy")
        try {
            locationManager?.removeUpdates(this)
        } catch (_: Exception) {}

        periodicJob?.cancel()
        serviceScope.cancel()

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}
