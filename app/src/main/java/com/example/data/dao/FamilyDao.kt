package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.CalendarNote
import com.example.data.model.CallRecord
import com.example.data.model.FamilyEvent
import com.example.data.model.FamilyMemory
import com.example.data.model.HomeVisitRecord
import com.example.data.model.SafeArrivalLog
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyDao {
    // Events
    @Query("SELECT * FROM family_events ORDER BY targetDate ASC")
    fun getAllEvents(): Flow<List<FamilyEvent>>

    @Query("SELECT * FROM family_events")
    suspend fun getEventsSnapshot(): List<FamilyEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: FamilyEvent): Long

    @Update
    suspend fun updateEvent(event: FamilyEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<FamilyEvent>)

    @Query("DELETE FROM family_events WHERE id = :id")
    suspend fun deleteEvent(id: Long)

    @Query("DELETE FROM family_events")
    suspend fun clearEvents()

    // Calendar Notes
    @Query("SELECT * FROM calendar_notes ORDER BY dateString ASC")
    fun getAllCalendarNotes(): Flow<List<CalendarNote>>

    @Query("SELECT * FROM calendar_notes WHERE dateString = :dateString")
    suspend fun getCalendarNotesForDate(dateString: String): List<CalendarNote>

    @Query("SELECT * FROM calendar_notes")
    suspend fun getCalendarNotesSnapshot(): List<CalendarNote>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarNote(note: CalendarNote): Long

    @Update
    suspend fun updateCalendarNote(note: CalendarNote)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarNotes(notes: List<CalendarNote>)

    @Query("DELETE FROM calendar_notes WHERE id = :id")
    suspend fun deleteCalendarNote(id: Long)

    @Query("DELETE FROM calendar_notes")
    suspend fun clearCalendarNotes()

    // Home Visits
    @Query("SELECT * FROM home_visits ORDER BY visitTimestamp DESC")
    fun getAllVisits(): Flow<List<HomeVisitRecord>>

    @Query("SELECT * FROM home_visits")
    suspend fun getVisitsSnapshot(): List<HomeVisitRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisit(visit: HomeVisitRecord): Long

    @Update
    suspend fun updateVisit(visit: HomeVisitRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisits(visits: List<HomeVisitRecord>)

    @Query("DELETE FROM home_visits WHERE id = :id")
    suspend fun deleteVisit(id: Long)

    @Query("DELETE FROM home_visits")
    suspend fun clearVisits()

    // Calls
    @Query("SELECT * FROM call_records ORDER BY callTimestamp DESC")
    fun getAllCalls(): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records")
    suspend fun getCallsSnapshot(): List<CallRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallRecord): Long

    @Update
    suspend fun updateCall(call: CallRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalls(calls: List<CallRecord>)

    @Query("DELETE FROM call_records WHERE id = :id")
    suspend fun deleteCall(id: Long)

    @Query("DELETE FROM call_records")
    suspend fun clearCalls()

    // Memories
    @Query("SELECT * FROM family_memories ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<FamilyMemory>>

    @Query("SELECT * FROM family_memories")
    suspend fun getMemoriesSnapshot(): List<FamilyMemory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: FamilyMemory): Long

    @Update
    suspend fun updateMemory(memory: FamilyMemory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemories(memories: List<FamilyMemory>)

    @Query("DELETE FROM family_memories WHERE id = :id")
    suspend fun deleteMemory(id: Long)

    @Query("DELETE FROM family_memories")
    suspend fun clearMemories()

    // Safe Arrival Logs
    @Query("SELECT * FROM safe_arrival_logs ORDER BY timestamp DESC")
    fun getAllArrivalLogs(): Flow<List<SafeArrivalLog>>

    @Query("SELECT * FROM safe_arrival_logs")
    suspend fun getArrivalLogsSnapshot(): List<SafeArrivalLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArrivalLog(log: SafeArrivalLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArrivalLogs(logs: List<SafeArrivalLog>)

    @Query("DELETE FROM safe_arrival_logs")
    suspend fun clearArrivalLogs()

    @Transaction
    suspend fun replaceAllFamilyData(
        events: List<FamilyEvent>,
        calendarNotes: List<CalendarNote>,
        visits: List<HomeVisitRecord>,
        calls: List<CallRecord>,
        memories: List<FamilyMemory>,
        arrivalLogs: List<SafeArrivalLog>
    ) {
        clearEvents()
        clearCalendarNotes()
        clearVisits()
        clearCalls()
        clearMemories()
        clearArrivalLogs()

        insertEvents(events)
        insertCalendarNotes(calendarNotes)
        insertVisits(visits)
        insertCalls(calls)
        insertMemories(memories)
        insertArrivalLogs(arrivalLogs)
    }
}
