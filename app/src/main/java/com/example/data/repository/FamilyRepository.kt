package com.example.data.repository

import com.example.data.dao.FamilyDao
import com.example.data.model.CalendarNote
import com.example.data.model.CallRecord
import com.example.data.model.FamilyEvent
import com.example.data.model.FamilyMemory
import com.example.data.model.HomeVisitRecord
import com.example.data.model.SafeArrivalLog
import com.example.util.LunarCalendarUtil
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FamilyRepository(private val dao: FamilyDao) {

    val allEvents: Flow<List<FamilyEvent>> = dao.getAllEvents()
    val allCalendarNotes: Flow<List<CalendarNote>> = dao.getAllCalendarNotes()
    val allVisits: Flow<List<HomeVisitRecord>> = dao.getAllVisits()
    val allCalls: Flow<List<CallRecord>> = dao.getAllCalls()
    val allMemories: Flow<List<FamilyMemory>> = dao.getAllMemories()
    val allArrivalLogs: Flow<List<SafeArrivalLog>> = dao.getAllArrivalLogs()

    suspend fun insertEvent(event: FamilyEvent) = dao.insertEvent(event)
    suspend fun updateEvent(event: FamilyEvent) = dao.updateEvent(event)
    suspend fun deleteEvent(id: Long) = dao.deleteEvent(id)

    suspend fun insertCalendarNote(note: CalendarNote) = dao.insertCalendarNote(note)
    suspend fun updateCalendarNote(note: CalendarNote) = dao.updateCalendarNote(note)
    suspend fun deleteCalendarNote(id: Long) = dao.deleteCalendarNote(id)

    suspend fun insertVisit(visit: HomeVisitRecord) = dao.insertVisit(visit)
    suspend fun updateVisit(visit: HomeVisitRecord) = dao.updateVisit(visit)
    suspend fun deleteVisit(id: Long) = dao.deleteVisit(id)

    suspend fun insertCall(call: CallRecord) = dao.insertCall(call)
    suspend fun updateCall(call: CallRecord) = dao.updateCall(call)
    suspend fun deleteCall(id: Long) = dao.deleteCall(id)

    suspend fun insertMemory(memory: FamilyMemory) = dao.insertMemory(memory)
    suspend fun updateMemory(memory: FamilyMemory) = dao.updateMemory(memory)
    suspend fun deleteMemory(id: Long) = dao.deleteMemory(id)

    suspend fun insertArrivalLog(log: SafeArrivalLog) = dao.insertArrivalLog(log)

    suspend fun getEventsSnapshot() = dao.getEventsSnapshot()
    suspend fun getCalendarNotesSnapshot() = dao.getCalendarNotesSnapshot()
    suspend fun getVisitsSnapshot() = dao.getVisitsSnapshot()
    suspend fun getCallsSnapshot() = dao.getCallsSnapshot()
    suspend fun getMemoriesSnapshot() = dao.getMemoriesSnapshot()
    suspend fun getArrivalLogsSnapshot() = dao.getArrivalLogsSnapshot()

    suspend fun restoreAllData(
        events: List<FamilyEvent>,
        calendarNotes: List<CalendarNote>,
        visits: List<HomeVisitRecord>,
        calls: List<CallRecord>,
        memories: List<FamilyMemory>,
        arrivalLogs: List<SafeArrivalLog>
    ) {
        dao.replaceAllFamilyData(events, calendarNotes, visits, calls, memories, arrivalLogs)
    }

    suspend fun hasAnyData(): Boolean {
        return dao.getEventsSnapshot().isNotEmpty() ||
               dao.getVisitsSnapshot().isNotEmpty() ||
               dao.getCallsSnapshot().isNotEmpty() ||
               dao.getMemoriesSnapshot().isNotEmpty()
    }

    suspend fun prepopulateIfEmpty(context: android.content.Context? = null) {
        if (context != null) {
            val prefs = context.getSharedPreferences("family_prepopulate_prefs", android.content.Context.MODE_PRIVATE)
            if (prefs.getBoolean("has_prepopulated_once", false)) {
                return // User already had initial data populated; do not recreate deleted items
            }
            prefs.edit().putBoolean("has_prepopulated_once", true).apply()
        }
        val existingEvents = dao.getEventsSnapshot()
        if (existingEvents.isEmpty()) {
            // Calculate next solar dates for Chinese Lunar birthdays
            val momSolar = LunarCalendarUtil.getNextSolarDateForLunar(8, 16)
            val momSolarStr = String.format(Locale.US, "%04d-%02d-%02d", momSolar.first, momSolar.second, momSolar.third)

            val dadSolar = LunarCalendarUtil.getNextSolarDateForLunar(9, 9)
            val dadSolarStr = String.format(Locale.US, "%04d-%02d-%02d", dadSolar.first, dadSolar.second, dadSolar.third)

            val midAutumnSolar = LunarCalendarUtil.getNextSolarDateForLunar(8, 15)
            val midAutumnStr = String.format(Locale.US, "%04d-%02d-%02d", midAutumnSolar.first, midAutumnSolar.second, midAutumnSolar.third)

            dao.insertEvent(
                FamilyEvent(
                    syncId = "event_mom_birthday",
                    updatedAt = 1000L,
                    title = "妈妈生日 (农历八月十六)",
                    isLunar = true,
                    lunarMonth = 8,
                    lunarDay = 16,
                    targetDate = momSolarStr,
                    category = "BIRTHDAY",
                    person = "妈妈",
                    note = "记得提前订妈妈最喜欢的蓝莓千层蛋糕 🎂"
                )
            )

            dao.insertEvent(
                FamilyEvent(
                    syncId = "event_dad_birthday",
                    updatedAt = 1000L,
                    title = "爸爸生日 (农历九月初九)",
                    isLunar = true,
                    lunarMonth = 9,
                    lunarDay = 9,
                    targetDate = dadSolarStr,
                    category = "BIRTHDAY",
                    person = "爸爸",
                    note = "给爸爸买保暖羊毛开衫，带两瓶老家好茶 🍵"
                )
            )

            dao.insertEvent(
                FamilyEvent(
                    syncId = "event_mid_autumn",
                    updatedAt = 1000L,
                    title = "中秋节全家团聚 (农历八月十五)",
                    isLunar = true,
                    lunarMonth = 8,
                    lunarDay = 15,
                    targetDate = midAutumnStr,
                    category = "HOLIDAY",
                    person = "全家",
                    note = "提前抢高铁票，中秋回家吃大闸蟹赏月 🥮"
                )
            )
        }

        val existingVisits = dao.getVisitsSnapshot()
        if (existingVisits.isEmpty()) {
            dao.insertVisit(
                HomeVisitRecord(
                    syncId = "visit_init_1",
                    updatedAt = 1000L,
                    dateString = "8月23日 (立秋探望)",
                    isoDate = "2026-08-23",
                    note = "周末带了水果回老家，帮爸妈给客厅换了更亮的新吸顶灯，坐在小院乘凉聊天到深夜。",
                    mealNotes = "妈妈炖的排骨莲藕汤、清炒丝瓜、红烧小黄鱼"
                )
            )
            dao.insertVisit(
                HomeVisitRecord(
                    syncId = "visit_init_2",
                    updatedAt = 1000L,
                    dateString = "7月12日 (夏日回家)",
                    isoDate = "2026-07-12",
                    note = "一家人一起包包子，爸爸去菜市场买了最新鲜的猪肉大葱。",
                    mealNotes = "大葱猪肉水煎包、绿豆百合粥"
                )
            )
        }

        val existingCalls = dao.getCallsSnapshot()
        if (existingCalls.isEmpty()) {
            dao.insertCall(
                CallRecord(
                    syncId = "call_init_1",
                    updatedAt = 1000L,
                    dateString = "9月8日 20:30",
                    isoDate = "2026-09-08",
                    person = "妈妈",
                    durationMinutes = 28,
                    topics = "问了出租房降温情况，叮嘱早晚加衣服，妈妈说最近血压都很平稳。"
                )
            )
            dao.insertCall(
                CallRecord(
                    syncId = "call_init_2",
                    updatedAt = 1000L,
                    dateString = "9月4日 19:40",
                    isoDate = "2026-09-04",
                    person = "爸爸",
                    durationMinutes = 15,
                    topics = "爸爸分享了阳台金桔开花的视频，叮嘱工作注意劳逸结合别熬夜。"
                )
            )
        }

        val existingMemories = dao.getMemoriesSnapshot()
        if (existingMemories.isEmpty()) {
            dao.insertMemory(
                FamilyMemory(
                    syncId = "memory_init_1",
                    updatedAt = 1000L,
                    title = "夏末老家小院的温馨聚餐",
                    dateString = "2026年8月23日",
                    isoDate = "2026-08-23",
                    location = "老家小院露台",
                    content = "夕阳西下，风吹过院子里的葡萄架，满桌都是爸妈亲手洗切炖煮的热菜。这一刻所有工作的疲惫都消散了，有家人的地方才是真正的港湾。",
                    photoUri = null,
                    tag = "聚餐合影"
                )
            )
        }

        val existingNotes = dao.getCalendarNotesSnapshot()
        if (existingNotes.isEmpty()) {
            dao.insertCalendarNote(
                CalendarNote(
                    dateString = "2026-09-15",
                    title = "陪妈妈去社区门诊体检复查",
                    content = "早上空腹，记得带上医保卡与既往报告",
                    tag = "健康约定"
                )
            )
        }
    }
}
