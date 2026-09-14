package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.db.AppDatabase
import com.example.data.repository.FamilyRepository
import com.example.data.sync.GitHubSyncManager
import com.example.notification.FamilyNotificationHelper
import java.util.concurrent.TimeUnit

class FamilySyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val syncManager = GitHubSyncManager(appContext)
        val config = syncManager.loadConfig()
        val effectiveGistId = if (config.gistId.isNotBlank()) config.gistId else GitHubSyncManager.BUILT_IN_GIST_ID
        val effectiveToken = if (config.githubToken.isNotBlank()) config.githubToken else GitHubSyncManager.BUILT_IN_GITHUB_TOKEN

        if (effectiveGistId.isBlank() && config.repoName.isBlank()) {
            return Result.success()
        }

        val pullResult = if (config.isGistMode) {
            syncManager.pullFromGist(effectiveToken, effectiveGistId)
        } else {
            syncManager.pullFromRepo(effectiveToken, config.repoOwner, config.repoName, config.filePath)
        }

        if (pullResult.isSuccess) {
            try {
                val json = pullResult.getOrNull() ?: ""
                if (json.isBlank()) return Result.success()

                val backup = syncManager.deserializeFromJson(json)

                val db = AppDatabase.getDatabase(appContext)
                val repository = FamilyRepository(db.familyDao())

                // 1. Sync data into Room database
                repository.restoreAllData(
                    events = backup.events,
                    calendarNotes = backup.calendarNotes,
                    visits = backup.visits,
                    calls = backup.calls,
                    memories = backup.memories,
                    arrivalLogs = backup.arrivalLogs
                )

                // 2. Persist moods to SharedPreferences for offline and background display
                val moodPrefs = appContext.getSharedPreferences("family_mood_prefs", Context.MODE_PRIVATE)
                moodPrefs.edit()
                    .putString("child_mood", backup.childMood)
                    .putInt("child_energy", backup.childEnergy)
                    .putString("child_mood_updated", backup.childMoodUpdated)
                    .putLong("child_mood_ts", backup.childMoodTimestamp)
                    .putString("parent_mood", backup.parentMood)
                    .putInt("parent_energy", backup.parentEnergy)
                    .putString("parent_mood_updated", backup.parentMoodUpdated)
                    .putLong("parent_mood_ts", backup.parentMoodTimestamp)
                    .apply()

                // 3. Check for latest safe arrival logs and notify parents if new
                val prefs = appContext.getSharedPreferences("family_notification_prefs", Context.MODE_PRIVATE)
                val lastNotifiedArrivalTimestamp = prefs.getLong("last_notified_arrival_ts", 0L)

                val latestArrival = backup.arrivalLogs.maxByOrNull { it.timestamp }
                val isRecent = latestArrival != null && (System.currentTimeMillis() - latestArrival.timestamp) < 24 * 3600 * 1000L
                if (latestArrival != null && latestArrival.timestamp > lastNotifiedArrivalTimestamp && (lastNotifiedArrivalTimestamp > 0L || isRecent)) {
                    // Save latest timestamp
                    prefs.edit().putLong("last_notified_arrival_ts", latestArrival.timestamp).apply()

                    val rolePrefs = appContext.getSharedPreferences("family_role_prefs", Context.MODE_PRIVATE)
                    val currentRole = rolePrefs.getString("current_role", "PARENT")
                    val isParent = currentRole != "CHILD"

                    if (isParent) {
                        // Trigger Android system notification for parents
                        FamilyNotificationHelper.showSafeArrivalNotification(
                            context = appContext,
                            title = "🏠 孩子已安全回到出租屋！",
                            message = "到达时间: ${latestArrival.timeString}\n留言: ${latestArrival.alertMessage}",
                            location = latestArrival.destination
                        )
                    }
                }

                return Result.success()
            } catch (e: Exception) {
                return Result.retry()
            }
        } else {
            return Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_WORK_TAG = "family_periodic_sync_work"
        private const val ONE_TIME_WORK_TAG = "family_one_time_sync_work"

        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Poll every 15 minutes (minimum allowable interval in Android WorkManager)
            val request = PeriodicWorkRequestBuilder<FamilySyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun enqueueImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<FamilySyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_TAG,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
