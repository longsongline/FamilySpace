package com.example.data.sync

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.model.CalendarNote
import com.example.data.model.CallRecord
import com.example.data.model.FamilyEvent
import com.example.data.model.FamilyMemory
import com.example.data.model.HomeVisitRecord
import com.example.data.model.SafeArrivalLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class SyncConfig(
    val githubToken: String = "",
    val gistId: String = "",
    val repoOwner: String = "",
    val repoName: String = "",
    val filePath: String = "family_space_sync.json",
    val isGistMode: Boolean = true,
    val lastSyncTime: String = "从未同步"
)

data class FullFamilyBackup(
    val lastSyncTime: String,
    val syncedBy: String,
    val rentalAddress: String,
    val rentalLatitude: Double = 0.0,
    val rentalLongitude: Double = 0.0,
    val rentalRadiusMeters: Int = 500,
    val rentalLocationTimestamp: Long = 0L,
    val isInsideRentalZone: Boolean = false,
    val distanceToRentalMeters: Double? = null,
    val childGpsInfo: String? = null,
    val lastArrivalTimestamp: Long = 0L,
    val childMood: String,
    val childEnergy: Int,
    val childMoodUpdated: String,
    val childMoodTimestamp: Long = 0L,
    val parentMood: String,
    val parentEnergy: Int,
    val parentMoodUpdated: String,
    val parentMoodTimestamp: Long = 0L,
    val events: List<FamilyEvent>,
    val calendarNotes: List<CalendarNote>,
    val visits: List<HomeVisitRecord>,
    val calls: List<CallRecord>,
    val memories: List<FamilyMemory>,
    val arrivalLogs: List<SafeArrivalLog>,
    val deletedKeys: List<String> = emptyList(),
    val syncTimestamp: Long = System.currentTimeMillis()
)

class GitHubSyncManager(private val context: Context) {

    companion object {
        const val BUILT_IN_GITHUB_TOKEN = ""
        const val BUILT_IN_GIST_ID = "2c7c588521c68a7236d938b1d003477f"
    }

    private val prefs = context.getSharedPreferences("github_sync_prefs", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    fun getEventSyncKey(e: FamilyEvent): String =
        if (e.syncId.isNotBlank()) e.syncId else "event_${e.person}_${e.title.trim()}_${e.isLunar}_${e.lunarMonth}_${e.lunarDay}_${e.targetDate}"

    fun getMemorySyncKey(m: FamilyMemory): String =
        if (m.syncId.isNotBlank()) m.syncId else "memory_${m.title.trim()}_${m.dateString}_${m.timestamp}"

    fun getNoteSyncKey(n: CalendarNote): String =
        if (n.syncId.isNotBlank()) n.syncId else "note_${n.dateString}_${n.title.trim()}"

    fun getVisitSyncKey(v: HomeVisitRecord): String =
        if (v.syncId.isNotBlank()) v.syncId else "visit_${v.dateString}_${v.visitTimestamp}"

    fun getCallSyncKey(c: CallRecord): String =
        if (c.syncId.isNotBlank()) c.syncId else "call_${c.person}_${c.dateString}_${c.callTimestamp}"

    fun getEventSemanticKey(e: FamilyEvent): String =
        "event_${e.person.trim()}_${e.title.trim()}"

    fun getMemorySemanticKey(m: FamilyMemory): String =
        "memory_${m.title.trim()}_${m.dateString.trim()}"

    fun getNoteSemanticKey(n: CalendarNote): String =
        "note_${n.dateString.trim()}_${n.title.trim()}"

    fun getVisitSemanticKey(v: HomeVisitRecord): String =
        "visit_${v.dateString.trim()}"

    fun getCallSemanticKey(c: CallRecord): String =
        "call_${c.person.trim()}_${c.dateString.trim()}"

    fun getDeletedKeys(): Set<String> {
        return prefs.getStringSet("deleted_sync_keys", emptySet()) ?: emptySet()
    }

    fun markKeyDeleted(key: String) {
        if (key.isBlank()) return
        val current = getDeletedKeys().toMutableSet()
        current.add(key)
        prefs.edit().putStringSet("deleted_sync_keys", current).apply()
    }

    fun unmarkKeyDeleted(key: String) {
        if (key.isBlank()) return
        val current = getDeletedKeys().toMutableSet()
        if (current.remove(key)) {
            prefs.edit().putStringSet("deleted_sync_keys", current).apply()
        }
    }

    fun recordDeletedKeys(keys: Set<String>) {
        if (keys.isEmpty()) return
        val current = getDeletedKeys().toMutableSet()
        current.addAll(keys.filter { it.isNotBlank() })
        prefs.edit().putStringSet("deleted_sync_keys", current).apply()
    }

    fun loadConfig(): SyncConfig {
        val savedToken = prefs.getString("token", "") ?: ""
        val token = if (savedToken.isNotBlank()) savedToken else BUILT_IN_GITHUB_TOKEN
        val savedGist = prefs.getString("gist_id", "") ?: ""
        val gistId = if (savedGist.isNotBlank()) savedGist else BUILT_IN_GIST_ID
        return SyncConfig(
            githubToken = token,
            gistId = gistId,
            repoOwner = prefs.getString("repo_owner", "") ?: "",
            repoName = prefs.getString("repo_name", "") ?: "",
            filePath = prefs.getString("file_path", "family_space_sync.json") ?: "family_space_sync.json",
            isGistMode = prefs.getBoolean("is_gist_mode", true),
            lastSyncTime = prefs.getString("last_sync_time", "三人同享中转就绪") ?: "三人同享中转就绪"
        )
    }

    suspend fun resolveOrCreateGistId(token: String): String? = withContext(Dispatchers.IO) {
        val currentGistId = prefs.getString("gist_id", "") ?: ""
        if (currentGistId.isNotBlank()) return@withContext currentGistId
        return@withContext BUILT_IN_GIST_ID
    }

    fun saveConfig(config: SyncConfig) {
        prefs.edit()
            .putString("token", config.githubToken)
            .putString("gist_id", config.gistId)
            .putString("repo_owner", config.repoOwner)
            .putString("repo_name", config.repoName)
            .putString("file_path", config.filePath)
            .putBoolean("is_gist_mode", config.isGistMode)
            .putString("last_sync_time", config.lastSyncTime)
            .apply()
    }

    fun updateLastSyncTime(timeStr: String) {
        prefs.edit().putString("last_sync_time", timeStr).apply()
    }

    /**
     * Serializes all family data to a JSON String
     */
    fun serializeToJson(backup: FullFamilyBackup): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("lastSyncTime", backup.lastSyncTime)
        root.put("syncedBy", backup.syncedBy)
        root.put("rentalAddress", backup.rentalAddress)
        root.put("rentalLatitude", backup.rentalLatitude)
        root.put("rentalLongitude", backup.rentalLongitude)
        root.put("rentalRadiusMeters", backup.rentalRadiusMeters)
        root.put("rentalLocationTimestamp", backup.rentalLocationTimestamp)
        root.put("isInsideRentalZone", backup.isInsideRentalZone)
        if (backup.distanceToRentalMeters != null) {
            root.put("distanceToRentalMeters", backup.distanceToRentalMeters)
        }
        root.put("childGpsInfo", backup.childGpsInfo ?: "")
        root.put("lastArrivalTimestamp", backup.lastArrivalTimestamp)
        root.put("childMood", backup.childMood)
        root.put("childEnergy", backup.childEnergy)
        root.put("childMoodUpdated", backup.childMoodUpdated)
        root.put("childMoodTimestamp", backup.childMoodTimestamp)
        root.put("parentMood", backup.parentMood)
        root.put("parentEnergy", backup.parentEnergy)
        root.put("parentMoodUpdated", backup.parentMoodUpdated)
        root.put("parentMoodTimestamp", backup.parentMoodTimestamp)
        root.put("syncTimestamp", backup.syncTimestamp)

        // Deleted tombstones
        val deletedArr = JSONArray()
        backup.deletedKeys.forEach { deletedArr.put(it) }
        root.put("deletedKeys", deletedArr)

        // Events
        val eventsArr = JSONArray()
        backup.events.forEach { e ->
            val o = JSONObject()
            o.put("id", e.id)
            o.put("syncId", getEventSyncKey(e))
            o.put("updatedAt", e.updatedAt)
            o.put("title", e.title)
            o.put("isLunar", e.isLunar)
            o.put("lunarMonth", e.lunarMonth)
            o.put("lunarDay", e.lunarDay)
            o.put("targetDate", e.targetDate)
            o.put("category", e.category)
            o.put("person", e.person)
            o.put("note", e.note)
            o.put("isAnnualRepeat", e.isAnnualRepeat)
            eventsArr.put(o)
        }
        root.put("events", eventsArr)

        // Calendar Notes
        val notesArr = JSONArray()
        backup.calendarNotes.forEach { n ->
            val o = JSONObject()
            o.put("id", n.id)
            o.put("syncId", getNoteSyncKey(n))
            o.put("updatedAt", n.updatedAt)
            o.put("dateString", n.dateString)
            o.put("title", n.title)
            o.put("content", n.content)
            o.put("tag", n.tag)
            notesArr.put(o)
        }
        root.put("calendarNotes", notesArr)

        // Visits
        val visitsArr = JSONArray()
        backup.visits.forEach { v ->
            val o = JSONObject()
            o.put("id", v.id)
            o.put("syncId", getVisitSyncKey(v))
            o.put("updatedAt", v.updatedAt)
            o.put("visitTimestamp", v.visitTimestamp)
            o.put("dateString", v.dateString)
            o.put("isoDate", v.isoDate)
            o.put("note", v.note)
            o.put("mealNotes", v.mealNotes)
            o.put("photoUri", v.photoUri ?: "")
            visitsArr.put(o)
        }
        root.put("visits", visitsArr)

        // Calls
        val callsArr = JSONArray()
        backup.calls.forEach { c ->
            val o = JSONObject()
            o.put("id", c.id)
            o.put("syncId", getCallSyncKey(c))
            o.put("updatedAt", c.updatedAt)
            o.put("callTimestamp", c.callTimestamp)
            o.put("dateString", c.dateString)
            o.put("isoDate", c.isoDate)
            o.put("person", c.person)
            o.put("durationMinutes", c.durationMinutes)
            o.put("topics", c.topics)
            o.put("audioUri", c.audioUri ?: "")
            o.put("audioDurationSeconds", c.audioDurationSeconds)
            callsArr.put(o)
        }
        root.put("calls", callsArr)

        // Memories
        val memoriesArr = JSONArray()
        backup.memories.forEach { m ->
            val o = JSONObject()
            o.put("id", m.id)
            o.put("syncId", getMemorySyncKey(m))
            o.put("updatedAt", m.updatedAt)
            o.put("title", m.title)
            o.put("timestamp", m.timestamp)
            o.put("dateString", m.dateString)
            o.put("isoDate", m.isoDate)
            o.put("location", m.location)
            o.put("content", m.content)
            o.put("photoUri", m.photoUri ?: "")
            o.put("mediaType", m.mediaType)
            o.put("videoUri", m.videoUri ?: "")
            o.put("mediaBase64", m.mediaBase64 ?: "")
            o.put("videoBase64", m.videoBase64 ?: "")
            o.put("tag", m.tag)
            memoriesArr.put(o)
        }
        root.put("memories", memoriesArr)

        // Arrival logs
        val arrivalArr = JSONArray()
        backup.arrivalLogs.forEach { a ->
            val o = JSONObject()
            o.put("id", a.id)
            o.put("syncId", a.syncId)
            o.put("updatedAt", a.updatedAt)
            o.put("timestamp", a.timestamp)
            o.put("timeString", a.timeString)
            o.put("isoDate", a.isoDate)
            o.put("destination", a.destination)
            o.put("alertMessage", a.alertMessage)
            o.put("notifiedParents", a.notifiedParents)
            arrivalArr.put(o)
        }
        root.put("arrivalLogs", arrivalArr)

        return root.toString(2)
    }

    /**
     * Deserializes JSON string back to full family backup
     */
    fun deserializeFromJson(jsonString: String): FullFamilyBackup {
        val root = JSONObject(jsonString)

        val lastSyncTime = root.optString("lastSyncTime", "刚刚")
        val syncedBy = root.optString("syncedBy", "家庭成员")
        val rentalAddress = root.optString("rentalAddress", "阳光里青年社区 3号楼 502室")
        val rentalLatitude = root.optDouble("rentalLatitude", 0.0)
        val rentalLongitude = root.optDouble("rentalLongitude", 0.0)
        val rentalRadiusMeters = root.optInt("rentalRadiusMeters", 500)
        val rentalLocationTimestamp = root.optLong("rentalLocationTimestamp", 0L)
        val isInsideRentalZone = root.optBoolean("isInsideRentalZone", false)
        val distanceToRentalMeters = if (root.has("distanceToRentalMeters")) root.optDouble("distanceToRentalMeters") else null
        val childGpsInfo = root.optString("childGpsInfo", "").ifBlank { null }
        val lastArrivalTimestamp = root.optLong("lastArrivalTimestamp", 0L)
        val childMood = root.optString("childMood", "今天下班准时，吃了热汤面，精神饱满 ✨")
        val childEnergy = root.optInt("childEnergy", 90)
        val childMoodUpdated = root.optString("childMoodUpdated", "今天 19:30")
        val childMoodTimestamp = root.optLong("childMoodTimestamp", 0L)
        val parentMood = root.optString("parentMood", "在小院晒太阳摆弄花草，身体很棒，心情舒畅 🌸")
        val parentEnergy = root.optInt("parentEnergy", 95)
        val parentMoodUpdated = root.optString("parentMoodUpdated", "今天 18:00")
        val parentMoodTimestamp = root.optLong("parentMoodTimestamp", 0L)
        val syncTimestamp = root.optLong("syncTimestamp", System.currentTimeMillis())

        // Deleted tombstones
        val deletedKeys = mutableListOf<String>()
        val delArr = root.optJSONArray("deletedKeys")
        if (delArr != null) {
            for (i in 0 until delArr.length()) {
                val k = delArr.optString(i, "")
                if (k.isNotBlank()) deletedKeys.add(k)
            }
        }

        // Events
        val eventsList = mutableListOf<FamilyEvent>()
        val eventsArr = root.optJSONArray("events")
        if (eventsArr != null) {
            for (i in 0 until eventsArr.length()) {
                val o = eventsArr.getJSONObject(i)
                val title = o.getString("title")
                val person = o.optString("person", "全家")
                val isLunar = o.optBoolean("isLunar", true)
                val lunarMonth = o.optInt("lunarMonth", 1)
                val lunarDay = o.optInt("lunarDay", 1)
                val targetDate = o.optString("targetDate", "")
                val fallbackSyncId = "event_${person}_${title.trim()}_${isLunar}_${lunarMonth}_${lunarDay}_${targetDate}"
                val syncId = o.optString("syncId", "").ifBlank { fallbackSyncId }
                val updatedAt = o.optLong("updatedAt", System.currentTimeMillis())

                eventsList.add(
                    FamilyEvent(
                        id = o.optLong("id", 0),
                        syncId = syncId,
                        updatedAt = updatedAt,
                        title = title,
                        isLunar = isLunar,
                        lunarMonth = lunarMonth,
                        lunarDay = lunarDay,
                        targetDate = targetDate,
                        category = o.optString("category", "BIRTHDAY"),
                        person = person,
                        note = o.optString("note", ""),
                        isAnnualRepeat = o.optBoolean("isAnnualRepeat", true)
                    )
                )
            }
        }

        // Calendar Notes
        val notesList = mutableListOf<CalendarNote>()
        val notesArr = root.optJSONArray("calendarNotes")
        if (notesArr != null) {
            for (i in 0 until notesArr.length()) {
                val o = notesArr.getJSONObject(i)
                val dateString = o.getString("dateString")
                val title = o.getString("title")
                val fallbackSyncId = "note_${dateString}_${title.trim()}"
                val syncId = o.optString("syncId", "").ifBlank { fallbackSyncId }
                val updatedAt = o.optLong("updatedAt", System.currentTimeMillis())

                notesList.add(
                    CalendarNote(
                        id = o.optLong("id", 0),
                        syncId = syncId,
                        updatedAt = updatedAt,
                        dateString = dateString,
                        title = title,
                        content = o.optString("content", ""),
                        tag = o.optString("tag", "备忘")
                    )
                )
            }
        }

        // Visits
        val visitsList = mutableListOf<HomeVisitRecord>()
        val visitsArr = root.optJSONArray("visits")
        if (visitsArr != null) {
            for (i in 0 until visitsArr.length()) {
                val o = visitsArr.getJSONObject(i)
                val photo = o.optString("photoUri", "")
                val visitTimestamp = o.optLong("visitTimestamp", System.currentTimeMillis())
                val dateString = o.getString("dateString")
                val fallbackSyncId = "visit_${dateString}_${visitTimestamp}"
                val syncId = o.optString("syncId", "").ifBlank { fallbackSyncId }
                val updatedAt = o.optLong("updatedAt", visitTimestamp)

                visitsList.add(
                    HomeVisitRecord(
                        id = o.optLong("id", 0),
                        syncId = syncId,
                        updatedAt = updatedAt,
                        visitTimestamp = visitTimestamp,
                        dateString = dateString,
                        isoDate = o.optString("isoDate", ""),
                        note = o.optString("note", ""),
                        mealNotes = o.optString("mealNotes", ""),
                        photoUri = if (photo.isBlank()) null else photo
                    )
                )
            }
        }

        // Calls
        val callsList = mutableListOf<CallRecord>()
        val callsArr = root.optJSONArray("calls")
        if (callsArr != null) {
            for (i in 0 until callsArr.length()) {
                val o = callsArr.getJSONObject(i)
                val callTimestamp = o.optLong("callTimestamp", System.currentTimeMillis())
                val dateString = o.getString("dateString")
                val person = o.getString("person")
                val fallbackSyncId = "call_${person}_${dateString}_${callTimestamp}"
                val syncId = o.optString("syncId", "").ifBlank { fallbackSyncId }
                val updatedAt = o.optLong("updatedAt", callTimestamp)

                callsList.add(
                    CallRecord(
                        id = o.optLong("id", 0),
                        syncId = syncId,
                        updatedAt = updatedAt,
                        callTimestamp = callTimestamp,
                        dateString = dateString,
                        isoDate = o.optString("isoDate", ""),
                        person = person,
                        durationMinutes = o.optInt("durationMinutes", 15),
                        topics = o.optString("topics", ""),
                        audioUri = o.optString("audioUri", "").ifBlank { null },
                        audioDurationSeconds = o.optInt("audioDurationSeconds", 0)
                    )
                )
            }
        }

        // Memories
        val memoriesList = mutableListOf<FamilyMemory>()
        val memoriesArr = root.optJSONArray("memories")
        if (memoriesArr != null) {
            for (i in 0 until memoriesArr.length()) {
                val o = memoriesArr.getJSONObject(i)
                val title = o.getString("title")
                var photo = o.optString("photoUri", "")
                val mediaType = o.optString("mediaType", "IMAGE")
                var videoUri = o.optString("videoUri", "")
                val mediaBase64 = o.optString("mediaBase64", "")
                val videoBase64 = o.optString("videoBase64", "")
                val memTimestamp = o.optLong("timestamp", System.currentTimeMillis())
                val dateString = o.getString("dateString")
                val fallbackSyncId = "memory_${title.trim()}_${dateString}_${memTimestamp}"
                val syncId = o.optString("syncId", "").ifBlank { fallbackSyncId }
                val updatedAt = o.optLong("updatedAt", memTimestamp)

                // Restore media files locally from synced Base64 payload
                if (mediaBase64.isNotBlank()) {
                    val localFile = if (mediaType == "VIDEO") {
                        com.example.util.FamilyMediaStorage.saveBase64ToFile(
                            context,
                            mediaBase64,
                            "sync_thumb_$memTimestamp",
                            "jpg"
                        )
                    } else {
                        com.example.util.FamilyMediaStorage.saveBase64ToFile(
                            context,
                            mediaBase64,
                            "sync_photo_$memTimestamp",
                            "jpg"
                        )
                    }
                    if (localFile != null && localFile.exists()) {
                        photo = localFile.absolutePath
                    }
                }

                if (videoBase64.isNotBlank()) {
                    val vidFile = com.example.util.FamilyMediaStorage.saveBase64ToFile(
                        context,
                        videoBase64,
                        "sync_video_$memTimestamp",
                        "mp4"
                    )
                    if (vidFile != null && vidFile.exists()) {
                        videoUri = vidFile.absolutePath
                    }
                }

                memoriesList.add(
                    FamilyMemory(
                        id = o.optLong("id", 0),
                        syncId = syncId,
                        updatedAt = updatedAt,
                        title = title,
                        timestamp = memTimestamp,
                        dateString = dateString,
                        isoDate = o.optString("isoDate", ""),
                        location = o.optString("location", ""),
                        content = o.optString("content", ""),
                        photoUri = if (photo.isBlank()) null else photo,
                        mediaType = mediaType,
                        videoUri = if (videoUri.isBlank()) null else videoUri,
                        mediaBase64 = if (mediaBase64.isBlank()) null else mediaBase64,
                        videoBase64 = if (videoBase64.isBlank()) null else videoBase64,
                        tag = o.optString("tag", "聚餐合影")
                    )
                )
            }
        }

        // Arrival Logs
        val arrivalList = mutableListOf<SafeArrivalLog>()
        val arrivalArr = root.optJSONArray("arrivalLogs")
        if (arrivalArr != null) {
            for (i in 0 until arrivalArr.length()) {
                val o = arrivalArr.getJSONObject(i)
                val timestamp = o.optLong("timestamp", System.currentTimeMillis())
                val syncId = o.optString("syncId", "").ifBlank { "arrival_$timestamp" }
                val updatedAt = o.optLong("updatedAt", timestamp)

                arrivalList.add(
                    SafeArrivalLog(
                        id = o.optLong("id", 0),
                        syncId = syncId,
                        updatedAt = updatedAt,
                        timestamp = timestamp,
                        timeString = o.getString("timeString"),
                        isoDate = o.optString("isoDate", ""),
                        destination = o.optString("destination", ""),
                        alertMessage = o.optString("alertMessage", ""),
                        notifiedParents = o.optBoolean("notifiedParents", true)
                    )
                )
            }
        }

        return FullFamilyBackup(
            lastSyncTime = lastSyncTime,
            syncedBy = syncedBy,
            rentalAddress = rentalAddress,
            rentalLatitude = rentalLatitude,
            rentalLongitude = rentalLongitude,
            rentalRadiusMeters = rentalRadiusMeters,
            rentalLocationTimestamp = rentalLocationTimestamp,
            isInsideRentalZone = isInsideRentalZone,
            distanceToRentalMeters = distanceToRentalMeters,
            childGpsInfo = childGpsInfo,
            lastArrivalTimestamp = lastArrivalTimestamp,
            childMood = childMood,
            childEnergy = childEnergy,
            childMoodUpdated = childMoodUpdated,
            childMoodTimestamp = childMoodTimestamp,
            parentMood = parentMood,
            parentEnergy = parentEnergy,
            parentMoodUpdated = parentMoodUpdated,
            parentMoodTimestamp = parentMoodTimestamp,
            events = eventsList,
            calendarNotes = notesList,
            visits = visitsList,
            calls = callsList,
            memories = memoriesList,
            arrivalLogs = arrivalList,
            deletedKeys = deletedKeys,
            syncTimestamp = syncTimestamp
        )
    }

    /**
     * Intelligent two-way merge between local and remote backup.
     * Incorporates Tombstone deletion tracking so deleted items are NEVER resurrected,
     * and Last-Write-Wins modification resolution so edits are properly preserved!
     */
    fun mergeBackups(local: FullFamilyBackup, remote: FullFamilyBackup): FullFamilyBackup {
        // Collect all tombstones from local, remote, and persistent local storage
        val allDeletedKeys = (local.deletedKeys + remote.deletedKeys + getDeletedKeys()).filter { it.isNotBlank() }.toSet()
        recordDeletedKeys(allDeletedKeys)

        // 1. Merge events
        fun isEventDeleted(e: FamilyEvent): Boolean =
            allDeletedKeys.contains(e.syncId) ||
            allDeletedKeys.contains(getEventSyncKey(e)) ||
            allDeletedKeys.contains(getEventSemanticKey(e))

        val filteredLocalEvents = local.events.filterNot { isEventDeleted(it) }
        val filteredRemoteEvents = remote.events.filterNot { isEventDeleted(it) }
        val mergedEvents = mutableListOf<FamilyEvent>()
        mergedEvents.addAll(filteredLocalEvents)

        for (re in filteredRemoteEvents) {
            val reKey = getEventSyncKey(re)
            val reSemantic = getEventSemanticKey(re)
            val localIndex = mergedEvents.indexOfFirst { le ->
                (le.syncId.isNotBlank() && le.syncId == re.syncId) ||
                (getEventSyncKey(le) == reKey) ||
                (getEventSemanticKey(le) == reSemantic) ||
                (le.person == re.person && le.title.trim() == re.title.trim())
            }
            if (localIndex < 0) {
                mergedEvents.add(re.copy(id = 0))
            } else {
                val le = mergedEvents[localIndex]
                if (re.updatedAt > le.updatedAt) {
                    mergedEvents[localIndex] = re.copy(id = le.id)
                }
            }
        }

        // 2. Merge calendar notes
        fun isNoteDeleted(n: CalendarNote): Boolean =
            allDeletedKeys.contains(n.syncId) ||
            allDeletedKeys.contains(getNoteSyncKey(n)) ||
            allDeletedKeys.contains(getNoteSemanticKey(n))

        val filteredLocalNotes = local.calendarNotes.filterNot { isNoteDeleted(it) }
        val filteredRemoteNotes = remote.calendarNotes.filterNot { isNoteDeleted(it) }
        val mergedNotes = mutableListOf<CalendarNote>()
        mergedNotes.addAll(filteredLocalNotes)

        for (rn in filteredRemoteNotes) {
            val rnKey = getNoteSyncKey(rn)
            val rnSemantic = getNoteSemanticKey(rn)
            val localIndex = mergedNotes.indexOfFirst { ln ->
                (ln.syncId.isNotBlank() && ln.syncId == rn.syncId) ||
                (getNoteSyncKey(ln) == rnKey) ||
                (getNoteSemanticKey(ln) == rnSemantic) ||
                (ln.dateString == rn.dateString && ln.title.trim() == rn.title.trim())
            }
            if (localIndex < 0) {
                mergedNotes.add(rn.copy(id = 0))
            } else {
                val ln = mergedNotes[localIndex]
                if (rn.updatedAt > ln.updatedAt) {
                    mergedNotes[localIndex] = rn.copy(id = ln.id)
                }
            }
        }

        // 3. Merge visits
        fun isVisitDeleted(v: HomeVisitRecord): Boolean =
            allDeletedKeys.contains(v.syncId) ||
            allDeletedKeys.contains(getVisitSyncKey(v)) ||
            allDeletedKeys.contains(getVisitSemanticKey(v))

        val filteredLocalVisits = local.visits.filterNot { isVisitDeleted(it) }
        val filteredRemoteVisits = remote.visits.filterNot { isVisitDeleted(it) }
        val mergedVisits = mutableListOf<HomeVisitRecord>()
        mergedVisits.addAll(filteredLocalVisits)

        for (rv in filteredRemoteVisits) {
            val rvKey = getVisitSyncKey(rv)
            val rvSemantic = getVisitSemanticKey(rv)
            val localIndex = mergedVisits.indexOfFirst { lv ->
                (lv.syncId.isNotBlank() && lv.syncId == rv.syncId) ||
                (getVisitSyncKey(lv) == rvKey) ||
                (getVisitSemanticKey(lv) == rvSemantic) ||
                (lv.dateString == rv.dateString || (lv.isoDate.isNotBlank() && lv.isoDate == rv.isoDate))
            }
            if (localIndex < 0) {
                mergedVisits.add(rv.copy(id = 0))
            } else {
                val lv = mergedVisits[localIndex]
                if (rv.updatedAt > lv.updatedAt) {
                    mergedVisits[localIndex] = rv.copy(id = lv.id, photoUri = lv.photoUri ?: rv.photoUri)
                }
            }
        }

        // 4. Merge calls
        fun isCallDeleted(c: CallRecord): Boolean =
            allDeletedKeys.contains(c.syncId) ||
            allDeletedKeys.contains(getCallSyncKey(c)) ||
            allDeletedKeys.contains(getCallSemanticKey(c))

        val filteredLocalCalls = local.calls.filterNot { isCallDeleted(it) }
        val filteredRemoteCalls = remote.calls.filterNot { isCallDeleted(it) }
        val mergedCalls = mutableListOf<CallRecord>()
        mergedCalls.addAll(filteredLocalCalls)

        for (rc in filteredRemoteCalls) {
            val rcKey = getCallSyncKey(rc)
            val rcSemantic = getCallSemanticKey(rc)
            val localIndex = mergedCalls.indexOfFirst { lc ->
                (lc.syncId.isNotBlank() && lc.syncId == rc.syncId) ||
                (getCallSyncKey(lc) == rcKey) ||
                (getCallSemanticKey(lc) == rcSemantic) ||
                (lc.dateString == rc.dateString && lc.person == rc.person)
            }
            if (localIndex < 0) {
                mergedCalls.add(rc.copy(id = 0))
            } else {
                val lc = mergedCalls[localIndex]
                if (rc.updatedAt > lc.updatedAt) {
                    mergedCalls[localIndex] = rc.copy(id = lc.id)
                }
            }
        }

        // 5. Merge memories
        fun isMemoryDeleted(m: FamilyMemory): Boolean =
            allDeletedKeys.contains(m.syncId) ||
            allDeletedKeys.contains(getMemorySyncKey(m)) ||
            allDeletedKeys.contains(getMemorySemanticKey(m))

        val filteredLocalMemories = local.memories.filterNot { isMemoryDeleted(it) }
        val filteredRemoteMemories = remote.memories.filterNot { isMemoryDeleted(it) }
        val mergedMemories = mutableListOf<FamilyMemory>()
        mergedMemories.addAll(filteredLocalMemories)

        for (rm in filteredRemoteMemories) {
            val rmKey = getMemorySyncKey(rm)
            val rmSemantic = getMemorySemanticKey(rm)
            val localIndex = mergedMemories.indexOfFirst { lm ->
                (lm.syncId.isNotBlank() && lm.syncId == rm.syncId) ||
                (getMemorySyncKey(lm) == rmKey) ||
                (getMemorySemanticKey(lm) == rmSemantic) ||
                (lm.title.trim() == rm.title.trim() && lm.dateString == rm.dateString)
            }
            if (localIndex < 0) {
                mergedMemories.add(rm.copy(id = 0))
            } else {
                val lm = mergedMemories[localIndex]
                val enrichedPhoto = lm.photoUri ?: rm.photoUri
                val enrichedVideo = lm.videoUri ?: rm.videoUri
                val enrichedBase64 = lm.mediaBase64 ?: rm.mediaBase64
                val enrichedVidBase64 = lm.videoBase64 ?: rm.videoBase64
                if (rm.updatedAt > lm.updatedAt) {
                    mergedMemories[localIndex] = rm.copy(
                        id = lm.id,
                        photoUri = enrichedPhoto,
                        videoUri = enrichedVideo,
                        mediaBase64 = enrichedBase64,
                        videoBase64 = enrichedVidBase64
                    )
                } else {
                    mergedMemories[localIndex] = lm.copy(
                        photoUri = enrichedPhoto,
                        videoUri = enrichedVideo,
                        mediaBase64 = enrichedBase64,
                        videoBase64 = enrichedVidBase64
                    )
                }
            }
        }

        // 6. Merge arrival logs by timestamp
        val mergedArrivalLogs = mutableListOf<SafeArrivalLog>()
        mergedArrivalLogs.addAll(local.arrivalLogs)
        for (ra in remote.arrivalLogs) {
            val exists = mergedArrivalLogs.any { la -> la.timestamp == ra.timestamp || (la.timeString == ra.timeString && la.isoDate == ra.isoDate) }
            if (!exists) {
                mergedArrivalLogs.add(ra.copy(id = 0))
            }
        }

        // 7. Choose newest mood / status with timestamp comparison
        val useRemoteChildMood = if (remote.childMoodTimestamp > 0L || local.childMoodTimestamp > 0L) {
            remote.childMoodTimestamp > local.childMoodTimestamp
        } else {
            remote.childMoodUpdated.compareTo(local.childMoodUpdated) > 0
        }
        val useRemoteParentMood = if (remote.parentMoodTimestamp > 0L || local.parentMoodTimestamp > 0L) {
            remote.parentMoodTimestamp > local.parentMoodTimestamp
        } else {
            remote.parentMoodUpdated.compareTo(local.parentMoodUpdated) > 0
        }

        val finalChildMood = if (useRemoteChildMood) remote.childMood else local.childMood
        val finalChildEnergy = if (useRemoteChildMood) remote.childEnergy else local.childEnergy
        val finalChildUpdated = if (useRemoteChildMood) remote.childMoodUpdated else local.childMoodUpdated
        val finalChildTimestamp = if (useRemoteChildMood) remote.childMoodTimestamp else local.childMoodTimestamp

        val finalParentMood = if (useRemoteParentMood) remote.parentMood else local.parentMood
        val finalParentEnergy = if (useRemoteParentMood) remote.parentEnergy else local.parentEnergy
        val finalParentUpdated = if (useRemoteParentMood) remote.parentMoodUpdated else local.parentMoodUpdated
        val finalParentTimestamp = if (useRemoteParentMood) remote.parentMoodTimestamp else local.parentMoodTimestamp

        // 8. Rental Info Merge (Last-Write-Wins based on rentalLocationTimestamp)
        val useRemoteRental = when {
            remote.rentalLocationTimestamp > local.rentalLocationTimestamp -> true
            local.rentalLocationTimestamp > remote.rentalLocationTimestamp -> false
            remote.rentalLatitude != 0.0 && local.rentalLatitude == 0.0 -> true
            remote.rentalAddress.isNotBlank() && (local.rentalAddress.isBlank() || local.rentalAddress == "阳光里青年社区 3号楼 502室 (出租房)") -> true
            else -> false
        }

        val finalAddr = if (useRemoteRental) remote.rentalAddress else local.rentalAddress
        val finalLat = if (useRemoteRental) remote.rentalLatitude else local.rentalLatitude
        val finalLng = if (useRemoteRental) remote.rentalLongitude else local.rentalLongitude
        val finalRadius = if (useRemoteRental) remote.rentalRadiusMeters else local.rentalRadiusMeters
        val finalRentalTs = maxOf(local.rentalLocationTimestamp, remote.rentalLocationTimestamp)

        // 9. Arrival & Zone Status (Adopt newer arrival state or remote if it reported safe arrival)
        val remoteHasNewerArrival = (remote.lastArrivalTimestamp > local.lastArrivalTimestamp) ||
                (remote.arrivalLogs.isNotEmpty() && (local.arrivalLogs.isEmpty() || (remote.arrivalLogs.maxOfOrNull { it.timestamp } ?: 0L) >= (local.arrivalLogs.maxOfOrNull { it.timestamp } ?: 0L)))
        val finalIsInsideZone = if (remoteHasNewerArrival) remote.isInsideRentalZone else (local.isInsideRentalZone || remote.isInsideRentalZone)
        val finalDist = if (remoteHasNewerArrival && remote.distanceToRentalMeters != null) remote.distanceToRentalMeters else (local.distanceToRentalMeters ?: remote.distanceToRentalMeters)
        val finalGps = if (remoteHasNewerArrival && !remote.childGpsInfo.isNullOrBlank()) remote.childGpsInfo else (local.childGpsInfo ?: remote.childGpsInfo)
        val finalArrivalTs = maxOf(local.lastArrivalTimestamp, remote.lastArrivalTimestamp)

        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())

        return FullFamilyBackup(
            lastSyncTime = nowStr,
            syncedBy = "家庭成员",
            rentalAddress = finalAddr,
            rentalLatitude = finalLat,
            rentalLongitude = finalLng,
            rentalRadiusMeters = finalRadius,
            rentalLocationTimestamp = finalRentalTs,
            isInsideRentalZone = finalIsInsideZone,
            distanceToRentalMeters = finalDist,
            childGpsInfo = finalGps,
            lastArrivalTimestamp = finalArrivalTs,
            childMood = finalChildMood,
            childEnergy = finalChildEnergy,
            childMoodUpdated = finalChildUpdated,
            childMoodTimestamp = finalChildTimestamp,
            parentMood = finalParentMood,
            parentEnergy = finalParentEnergy,
            parentMoodUpdated = finalParentUpdated,
            parentMoodTimestamp = finalParentTimestamp,
            events = mergedEvents,
            calendarNotes = mergedNotes,
            visits = mergedVisits,
            calls = mergedCalls,
            memories = mergedMemories,
            arrivalLogs = mergedArrivalLogs,
            deletedKeys = allDeletedKeys.toList(),
            syncTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Push full family data to GitHub Gist as transit relay
     */
    suspend fun pushToGist(
        token: String,
        gistId: String,
        jsonContent: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fileName = "family_space_sync.json"
            val fileObj = JSONObject()
            fileObj.put("content", jsonContent)

            val filesObj = JSONObject()
            filesObj.put(fileName, fileObj)

            val bodyObj = JSONObject()
            bodyObj.put("description", "亲情空间 · 三人家庭共享中转数据")
            bodyObj.put("public", false)
            bodyObj.put("files", filesObj)

            val requestBody = bodyObj.toString().toRequestBody(JSON_MEDIA_TYPE)

            val effectiveGistId = if (gistId.isNotBlank()) gistId else resolveOrCreateGistId(token) ?: ""

            val (url, method) = if (effectiveGistId.isBlank()) {
                Pair("https://api.github.com/gists", "POST")
            } else {
                Pair("https://api.github.com/gists/$effectiveGistId", "PATCH")
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", "FamilySpace-Android-App")
                .method(method, requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: "HTTP ${response.code}"
                    return@withContext Result.failure(IOException("GitHub Gist 推送失败 ($err)"))
                }
                val respStr = response.body?.string() ?: ""
                val respJson = JSONObject(respStr)
                val returnedId = respJson.optString("id", effectiveGistId)
                if (returnedId.isNotBlank()) {
                    prefs.edit().putString("gist_id", returnedId).apply()
                }
                Result.success(returnedId)
            }
        } catch (e: Exception) {
            Log.e("GitHubSyncManager", "pushToGist error", e)
            Result.failure(e)
        }
    }

    /**
     * Pull full family data from GitHub Gist
     */
    suspend fun pullFromGist(
        token: String,
        gistId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val effectiveGistId = if (gistId.isNotBlank()) gistId else resolveOrCreateGistId(token) ?: ""
            if (effectiveGistId.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("尚未找到中转 Gist，可先点击一次同步或保存"))
            }

            val requestBuilder = Request.Builder()
                .url("https://api.github.com/gists/$effectiveGistId")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", "FamilySpace-Android-App")

            if (token.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: "HTTP ${response.code}"
                    return@withContext Result.failure(IOException("从 GitHub Gist 拉取失败 ($err)"))
                }
                val respStr = response.body?.string() ?: ""
                val respJson = JSONObject(respStr)
                val files = respJson.optJSONObject("files")
                    ?: return@withContext Result.failure(IOException("Gist 中未找到文件"))

                // Look for family_space_sync.json or the first available file
                val fileObj = files.optJSONObject("family_space_sync.json")
                    ?: files.optJSONObject(files.keys().asSequence().firstOrNull() ?: "")
                    ?: return@withContext Result.failure(IOException("未在 Gist 找到中转数据文件"))

                val content = fileObj.optString("content", "")
                if (content.isBlank()) {
                    return@withContext Result.failure(IOException("Gist 中转文件内容为空"))
                }
                Result.success(content)
            }
        } catch (e: Exception) {
            Log.e("GitHubSyncManager", "pullFromGist error", e)
            Result.failure(e)
        }
    }

    /**
     * Pull full family data from a GitHub Repository file
     */
    suspend fun pullFromRepo(
        token: String,
        owner: String,
        repo: String,
        path: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/contents/$path"
            val reqBuilder = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", "FamilySpace-Android-App")

            if (token.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: "HTTP ${response.code}"
                    return@withContext Result.failure(IOException("从 GitHub 仓库拉取失败 ($err)"))
                }
                val respStr = response.body?.string() ?: ""
                val respJson = JSONObject(respStr)
                val base64Content = respJson.optString("content", "").replace("\n", "")
                val decodedBytes = Base64.decode(base64Content, Base64.DEFAULT)
                val content = String(decodedBytes, StandardCharsets.UTF_8)
                Result.success(content)
            }
        } catch (e: Exception) {
            Log.e("GitHubSyncManager", "pullFromRepo error", e)
            Result.failure(e)
        }
    }

    /**
     * Push full family data to a GitHub Repository file
     */
    suspend fun pushToRepo(
        token: String,
        owner: String,
        repo: String,
        path: String,
        jsonContent: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/contents/$path"

            // 1. Check if file already exists to get its SHA
            var sha: String? = null
            val checkReq = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", "FamilySpace-Android-App")
                .build()

            client.newCall(checkReq).execute().use { checkResp ->
                if (checkResp.isSuccessful) {
                    val respStr = checkResp.body?.string() ?: ""
                    val json = JSONObject(respStr)
                    sha = json.optString("sha", null)
                }
            }

            // 2. Put file
            val base64Content = Base64.encodeToString(
                jsonContent.toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP
            )

            val bodyJson = JSONObject()
            bodyJson.put("message", "更新家庭亲情空间同步数据 (三人共享中转站)")
            bodyJson.put("content", base64Content)
            if (sha != null) {
                bodyJson.put("sha", sha)
            }

            val putReq = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", "FamilySpace-Android-App")
                .put(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(putReq).execute().use { putResp ->
                if (!putResp.isSuccessful) {
                    val err = putResp.body?.string() ?: "HTTP ${putResp.code}"
                    return@withContext Result.failure(IOException("推送到 GitHub 仓库失败 ($err)"))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e("GitHubSyncManager", "pushToRepo error", e)
            Result.failure(e)
        }
    }
}
