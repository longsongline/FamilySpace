package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.FamilyMemory
import com.example.ui.theme.CozySage
import com.example.ui.theme.WarmAmberPrimary
import com.example.ui.theme.WarmAmberSecondary
import com.example.ui.theme.WarmPeachContainer
import com.example.ui.viewmodel.CalendarDayEvent
import com.example.util.LunarCalendarUtil
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

private const val CALENDAR_BASE_PAGE = 1200
private const val CALENDAR_TOTAL_PAGES = 2400

@Composable
fun FamilyCalendarView(
    onGetMappedEvents: (year: Int, month: Int, day: Int) -> List<CalendarDayEvent>,
    onAddCalendarNote: (dateString: String, title: String, content: String, tag: String) -> Unit,
    onNavigateToMemory: ((Long) -> Unit)? = null,
    onNavigateToTimeline: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentCalendar = remember { Calendar.getInstance() }
    val todayYear = currentCalendar.get(Calendar.YEAR)
    val todayMonth = currentCalendar.get(Calendar.MONTH) + 1
    val todayDay = currentCalendar.get(Calendar.DAY_OF_MONTH)

    val pagerState = rememberPagerState(
        initialPage = CALENDAR_BASE_PAGE,
        pageCount = { CALENDAR_TOTAL_PAGES }
    )
    val coroutineScope = rememberCoroutineScope()

    // Helper to calculate Year and Month for a given page index
    fun getYearMonthForPage(page: Int): Pair<Int, Int> {
        val delta = page - CALENDAR_BASE_PAGE
        val c = Calendar.getInstance().apply {
            set(todayYear, todayMonth - 1, 1)
            add(Calendar.MONTH, delta)
        }
        return Pair(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    val (displayYear, displayMonth) = getYearMonthForPage(pagerState.currentPage)
    var selectedDay by remember { mutableIntStateOf(todayDay) }

    // Clamp selectedDay when switching between months with different days count
    val maxDaysInCurrentMonth = remember(displayYear, displayMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, displayYear)
            set(Calendar.MONTH, displayMonth - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    LaunchedEffect(maxDaysInCurrentMonth) {
        if (selectedDay > maxDaysInCurrentMonth) {
            selectedDay = maxDaysInCurrentMonth
        }
    }

    var showAddNoteDialog by remember { mutableStateOf(false) }
    var previewMemory by remember { mutableStateOf<FamilyMemory?>(null) }

    // Events for currently selected day
    val selectedDayEvents = onGetMappedEvents(displayYear, displayMonth, selectedDay)
    val selectedLunar = LunarCalendarUtil.solarToLunar(displayYear, displayMonth, selectedDay)
    val selectedLunarFestival = LunarCalendarUtil.getLunarFestival(selectedLunar.month, selectedLunar.day)

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("family_calendar_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 1. Header: Title & Subtitle + Jump to Today Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(WarmAmberPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Calendar",
                            tint = WarmAmberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "家庭亲情万年历",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "农历公历双显 · 生日与回家动态映射",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }

                // Quick jump to today button
                Surface(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(CALENDAR_BASE_PAGE)
                        }
                        selectedDay = todayDay
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = WarmAmberPrimary.copy(alpha = 0.12f),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = "回到今天",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = WarmAmberPrimary
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Dedicated Month Switcher Row (Full-width, clickable & swipe indicator)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                if (pagerState.currentPage > 0) {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous Month",
                            tint = WarmAmberPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = "${displayYear}年 ${displayMonth}月",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )

                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                if (pagerState.currentPage < CALENDAR_TOTAL_PAGES - 1) {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next Month",
                            tint = WarmAmberPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Weekday labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf("日", "一", "二", "三", "四", "五", "六").forEachIndexed { idx, day ->
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (idx == 0 || idx == 6) WarmAmberPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // HorizontalPager allowing smooth swipe left/right across all months
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val (pageYear, pageMonth) = getYearMonthForPage(page)
                MonthCalendarGrid(
                    year = pageYear,
                    month = pageMonth,
                    selectedDay = if (page == pagerState.currentPage) selectedDay else -1,
                    todayYear = todayYear,
                    todayMonth = todayMonth,
                    todayDay = todayDay,
                    onSelectDay = { day ->
                        selectedDay = day
                    },
                    onGetMappedEvents = onGetMappedEvents
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Selected Day Detail Box (Shows automatically mapped events for this day!)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${displayMonth}月${selectedDay}日",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = WarmAmberPrimary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = selectedLunar.toChineseString() + if (selectedLunarFestival != null) " · $selectedLunarFestival" else "",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = WarmAmberPrimary,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Button to add event/note on this date
                        OutlinedButton(
                            onClick = { showAddNoteDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("add_calendar_note_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("记一笔", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // List mapped events
                    if (selectedDayEvents.isEmpty()) {
                        Text(
                            text = "该日暂无生日或事件记录。点击“记一笔”可在此日期备忘、约定或记录回家～",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            selectedDayEvents.forEach { event ->
                                DayEventRow(
                                    event = event,
                                    onNavigateToMemory = onNavigateToMemory,
                                    onNavigateToTimeline = onNavigateToTimeline,
                                    onShowMemoryPreview = { previewMemory = it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog to preview specific memory
    previewMemory?.let { memory ->
        MemoryDetailPreviewDialog(
            memory = memory,
            onDismiss = { previewMemory = null },
            onNavigateToTimeline = {
                val memId = memory.id
                previewMemory = null
                if (onNavigateToMemory != null) {
                    onNavigateToMemory(memId)
                } else {
                    onNavigateToTimeline()
                }
            }
        )
    }

    // Dialog to add custom note on this date
    if (showAddNoteDialog) {
        val isoDateStr = String.format(Locale.US, "%04d-%02d-%02d", displayYear, displayMonth, selectedDay)
        AddCalendarNoteDialog(
            dateDisplay = "${displayYear}年${displayMonth}月${selectedDay}日 (${selectedLunar.toChineseString()})",
            onDismiss = { showAddNoteDialog = false },
            onConfirm = { title, content, tag ->
                onAddCalendarNote(isoDateStr, title, content, tag)
                showAddNoteDialog = false
            }
        )
    }
}

@Composable
private fun MonthCalendarGrid(
    year: Int,
    month: Int,
    selectedDay: Int,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    onSelectDay: (Int) -> Unit,
    onGetMappedEvents: (year: Int, month: Int, day: Int) -> List<CalendarDayEvent>
) {
    val cal = remember(year, month) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 for Sunday, 2 for Monday
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val offset = firstDayOfWeek - 1 // 0-based offset

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until 6) {
            if (row * 7 - offset >= daysInMonth) break
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                for (col in 0 until 7) {
                    val slotIndex = row * 7 + col
                    val dayNumber = slotIndex - offset + 1

                    if (dayNumber in 1..daysInMonth) {
                        val isToday = (year == todayYear && month == todayMonth && dayNumber == todayDay)
                        val isSelected = (dayNumber == selectedDay)
                        val lunar = LunarCalendarUtil.solarToLunar(year, month, dayNumber)
                        val festival = LunarCalendarUtil.getLunarFestival(lunar.month, lunar.day)
                        val mappedEvents = onGetMappedEvents(year, month, dayNumber)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        isSelected -> WarmAmberPrimary.copy(alpha = 0.18f)
                                        isToday -> WarmPeachContainer.copy(alpha = 0.6f)
                                        else -> Color.Transparent
                                    }
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else if (isToday) 1.dp else 0.dp,
                                    color = if (isSelected) WarmAmberPrimary else if (isToday) WarmAmberSecondary else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectDay(dayNumber) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "$dayNumber",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isToday) WarmAmberPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp
                                    )
                                )

                                // Lunar text or festival text
                                Text(
                                    text = festival ?: lunar.toShortChineseString(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        color = if (festival != null) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                        fontWeight = if (festival != null) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    maxLines = 1
                                )

                                // Mapped Event Dots (Birthdays, Home visits, Memories, Calls)
                                if (mappedEvents.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        mappedEvents.take(3).forEach { evt ->
                                            val dotColor = when (evt.type) {
                                                "BIRTHDAY" -> Color(0xFFE91E63)
                                                "VISIT" -> WarmAmberPrimary
                                                "MEMORY" -> CozySage
                                                "CALL" -> Color(0xFF2196F3)
                                                else -> Color(0xFF9C27B0)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(4.5.dp)
                                                    .clip(CircleShape)
                                                    .background(dotColor)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DayEventRow(
    event: CalendarDayEvent,
    onNavigateToMemory: ((Long) -> Unit)? = null,
    onNavigateToTimeline: () -> Unit = {},
    onShowMemoryPreview: ((FamilyMemory) -> Unit)? = null
) {
    val isMemory = (event.type == "MEMORY" && event.memory != null)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isMemory && event.memory != null) {
                    onShowMemoryPreview?.invoke(event.memory) ?: run {
                        if (onNavigateToMemory != null) onNavigateToMemory(event.memory.id)
                        else onNavigateToTimeline()
                    }
                } else {
                    onNavigateToTimeline()
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                if (event.detail.isNotBlank()) {
                    Text(
                        text = event.detail,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            fontSize = 11.5.sp
                        )
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = event.timeOrTag,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isMemory) WarmAmberPrimary.copy(alpha = 0.2f) else WarmAmberPrimary.copy(alpha = 0.14f),
                    modifier = Modifier.clickable {
                        if (isMemory && event.memory != null) {
                            if (onNavigateToMemory != null) {
                                onNavigateToMemory(event.memory.id)
                            } else {
                                onNavigateToTimeline()
                            }
                        } else {
                            onNavigateToTimeline()
                        }
                    }
                ) {
                    val actionLabel = when (event.type) {
                        "MEMORY" -> "📸 直达图文 >"
                        "VISIT" -> "🏠 回家记录 >"
                        "CALL" -> "📞 通话记录 >"
                        "BIRTHDAY" -> "🎂 倒计时 >"
                        else -> "查看 >"
                    }
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarmAmberPrimary
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MemoryDetailPreviewDialog(
    memory: FamilyMemory,
    onDismiss: () -> Unit,
    onNavigateToTimeline: () -> Unit
) {
    val isVideo = memory.mediaType == "VIDEO" || !memory.videoUri.isNullOrBlank()

    Dialog(onDismissRequest = onDismiss) {
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Media preview banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(Color(0xFF1E1E1E)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!memory.photoUri.isNullOrBlank()) {
                        AsyncImage(
                            model = memory.photoUri,
                            contentDescription = memory.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.img_family_warm_home),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (isVideo) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB74D),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "视频回忆",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = WarmAmberPrimary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = memory.tag,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = WarmAmberPrimary,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            text = memory.dateString,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = memory.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    if (memory.location.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = CozySage,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = memory.location,
                                style = MaterialTheme.typography.labelSmall.copy(color = CozySage)
                            )
                        }
                    }

                    if (memory.content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = memory.content,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                lineHeight = 20.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("关闭")
                        }

                        Button(
                            onClick = {
                                onDismiss()
                                onNavigateToTimeline()
                            },
                            modifier = Modifier.weight(1.6f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WarmAmberPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("直达时光轴定位", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddCalendarNoteDialog(
    dateDisplay: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String, tag: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("约定") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("在 $dateDisplay 记一笔") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("事件主题 (如: 陪妈妈体检、回家买特产)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("约定", "提醒", "回家计划", "健康").forEach { t ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (tag == t) WarmAmberPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { tag = t }
                        ) {
                            Text(
                                text = t,
                                color = if (tag == t) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("详细备注与注意事项") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title, content, tag)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WarmAmberPrimary)
            ) {
                Text("保存到万年历")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
