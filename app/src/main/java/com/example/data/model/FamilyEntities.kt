package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "family_events")
data class FamilyEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val title: String,
    val isLunar: Boolean = true, // Traditional Chinese birthdays are lunar by default
    val lunarMonth: Int = 1,
    val lunarDay: Int = 1,
    val targetDate: String, // YYYY-MM-DD (calculated next solar date or explicit solar date)
    val category: String, // BIRTHDAY, HOLIDAY, ANNIVERSARY, HEALTH
    val person: String, // 妈妈, 爸爸, 全家
    val note: String = "",
    val isAnnualRepeat: Boolean = true
)

@Entity(tableName = "calendar_notes")
data class CalendarNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val dateString: String, // YYYY-MM-DD
    val title: String,
    val content: String = "",
    val tag: String = "备忘" // 备忘, 约定, 提醒
)

@Entity(tableName = "home_visits")
data class HomeVisitRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val visitTimestamp: Long = System.currentTimeMillis(),
    val dateString: String, // e.g. "2026-09-06" or "9月6日"
    val isoDate: String = "", // Standard YYYY-MM-DD for calendar mapping
    val note: String,
    val mealNotes: String = "",
    val photoUri: String? = null
)

@Entity(tableName = "call_records")
data class CallRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val callTimestamp: Long = System.currentTimeMillis(),
    val dateString: String,
    val isoDate: String = "", // Standard YYYY-MM-DD for calendar mapping
    val person: String, // 妈妈, 爸爸, 父母全家
    val durationMinutes: Int,
    val topics: String,
    val audioUri: String? = null, // Local audio path for call recording / voice note
    val audioDurationSeconds: Int = 0 // Audio duration in seconds
)

@Entity(tableName = "family_memories")
data class FamilyMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val dateString: String,
    val isoDate: String = "", // Standard YYYY-MM-DD for calendar mapping
    val location: String,
    val content: String,
    val photoUri: String? = null,
    val mediaType: String = "IMAGE", // "IMAGE" or "VIDEO"
    val videoUri: String? = null,
    val mediaBase64: String? = null, // Base64 thumbnail / photo payload for real-time sync
    val videoBase64: String? = null, // Base64 video payload for short clips
    val tag: String // 聚餐, 全家福, 散步, 节日, 温馨视频
)

@Entity(tableName = "safe_arrival_logs")
data class SafeArrivalLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val timeString: String,
    val isoDate: String = "", // Standard YYYY-MM-DD
    val destination: String,
    val alertMessage: String,
    val notifiedParents: Boolean = true
)
