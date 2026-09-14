package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FamilyEvent
import com.example.ui.theme.WarmAmberPrimary
import com.example.util.LunarCalendarUtil
import java.util.Calendar

@Composable
fun CountdownSection(
    events: List<FamilyEvent>,
    onAddLunarEvent: (title: String, lunarMonth: Int, lunarDay: Int, person: String, note: String) -> Unit,
    onAddSolarEvent: (title: String, targetDate: String, category: String, person: String, note: String) -> Unit,
    onDeleteEvent: (Long) -> Unit,
    calcDaysAndNextDate: (FamilyEvent) -> Pair<Int, String>,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(WarmAmberPrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = WarmAmberPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "生日与佳节倒计时",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = "农历与公历智能换算",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            fontSize = 11.5.sp
                        )
                    )
                }
            }

            OutlinedButton(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("add_event_btn")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("记生日", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (events.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Text(
                    text = "暂无生日记录，点击右上角“记生日”添加爸妈农历生日 🎂",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                )
            }
        } else {
            // Three cards per row layout (三个一列并排，整齐直观不遮挡)
            val rows = events.chunked(3)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rows.forEach { rowEvents ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (i in 0 until 3) {
                            if (i < rowEvents.size) {
                                val event = rowEvents[i]
                                val (daysLeft, nextDateStr) = calcDaysAndNextDate(event)
                                CompactThreeColumnCard(
                                    event = event,
                                    daysRemaining = daysLeft,
                                    nextSolarDate = nextDateStr,
                                    onDelete = { onDeleteEvent(event.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                // Placeholder for empty slot to maintain equal 3-column width
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddBirthdayDialog(
            onDismiss = { showAddDialog = false },
            onConfirmLunar = { title, month, day, person, note ->
                onAddLunarEvent(title, month, day, person, note)
                showAddDialog = false
            },
            onConfirmSolar = { title, dateStr, cat, person, note ->
                onAddSolarEvent(title, dateStr, cat, person, note)
                showAddDialog = false
            }
        )
    }
}

/**
 * Three-column compact card:
 * Optimized for displaying 3 items side-by-side with no occlusion, generous touch target,
 * warm pastel family palette, and clean typography.
 */
@Composable
fun CompactThreeColumnCard(
    event: FamilyEvent,
    daysRemaining: Int,
    nextSolarDate: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMom = event.person.contains("妈妈")
    val isDad = event.person.contains("爸爸")
    val isSelf = event.person.contains("我") || event.person.contains("自己") || event.person.contains("孩子") || event.title.contains("我的")

    // Warm, cozy pastel card background and harmonious accents
    val (cardBg, accentColor, tagBg) = when {
        isMom -> Triple(Color(0xFFFFF0F2), Color(0xFFD81B60), Color(0xFFFFDDE4))
        isDad -> Triple(Color(0xFFF1F5FA), Color(0xFF285E8E), Color(0xFFDCE8F5))
        isSelf -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Color(0xFFC8E6C9))
        else -> Triple(Color(0xFFFFF7EA), Color(0xFFC26D00), Color(0xFFFFE8C2))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .height(152.dp)
            .testTag("countdown_card_${event.id}")
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Role badge and subtle delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = tagBg
                ) {
                    val roleLabel = when {
                        isMom -> "👩 妈妈"
                        isDad -> "👴 爸爸"
                        isSelf -> "🧑 我"
                        event.person == "全家" -> "🏮 全家"
                        else -> "🎂 ${event.person.take(4)}"
                    }
                    Text(
                        text = roleLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete",
                        tint = Color.Gray.copy(alpha = 0.45f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Event Title
            Text(
                text = event.title.replace("(.*)".toRegex(), "").trim(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Lunar or Solar Subtitle
            Text(
                text = if (event.isLunar) {
                    "农历${LunarCalendarUtil.getLunarMonthName(event.lunarMonth)}${LunarCalendarUtil.getLunarDayName(event.lunarDay)}"
                } else {
                    nextSolarDate
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 10.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.weight(1f))

            // Days remaining emphasis
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "还剩",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        fontSize = 10.sp
                    ),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "$daysRemaining",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        fontSize = 22.sp
                    )
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "天",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        fontSize = 10.sp
                    ),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            // Next Gregorian date note
            Text(
                text = "公历 $nextSolarDate",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 9.5.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
fun AddBirthdayDialog(
    onDismiss: () -> Unit,
    onConfirmLunar: (title: String, lunarMonth: Int, lunarDay: Int, person: String, note: String) -> Unit,
    onConfirmSolar: (title: String, targetDate: String, category: String, person: String, note: String) -> Unit
) {
    var isLunar by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("") }
    var person by remember { mutableStateOf("我") }
    var customPersonName by remember { mutableStateOf("") }
    var lunarMonth by remember { mutableIntStateOf(8) }
    var lunarDay by remember { mutableIntStateOf(16) }

    var solarYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR).toString()) }
    var solarMonth by remember { mutableStateOf("10") }
    var solarDay by remember { mutableStateOf("18") }

    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录重要生日 / 佳节", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Mode selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isLunar) WarmAmberPrimary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isLunar = true }
                    ) {
                        Text(
                            text = "农历生日 (传统习惯)",
                            color = if (isLunar) Color.White else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (!isLunar) WarmAmberPrimary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isLunar = false }
                    ) {
                        Text(
                            text = "公历日期",
                            color = if (!isLunar) Color.White else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Person selector (Including myself "我", "妈妈", "爸爸", "全家", "其他亲友")
                Column {
                    Text(
                        text = "为谁记录生日：",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("我", "妈妈", "爸爸", "全家", "其他").forEach { p ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (person == p) WarmAmberPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    person = p
                                    if (title.isBlank() || title.endsWith("生日") || title == "阖家团圆") {
                                        title = when (p) {
                                            "我" -> "我的生日"
                                            "妈妈" -> "妈妈生日"
                                            "爸爸" -> "爸爸生日"
                                            "全家" -> "阖家团圆"
                                            else -> ""
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    text = when (p) {
                                        "我" -> "🧑 我"
                                        "妈妈" -> "👩 妈妈"
                                        "爸爸" -> "👴 爸爸"
                                        "全家" -> "🏮 全家"
                                        else -> "➕ 其他"
                                    },
                                    color = if (person == p) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }

                if (person == "其他") {
                    OutlinedTextField(
                        value = customPersonName,
                        onValueChange = { customPersonName = it },
                        label = { Text("称呼/姓名 (如: 爷爷、外婆、好友小林)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("名称 (如: 我的生日、妈妈寿辰)") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isLunar) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = "$lunarMonth",
                            onValueChange = { lunarMonth = it.toIntOrNull()?.coerceIn(1, 12) ?: 1 },
                            label = { Text("农历月份 (1-12)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = "$lunarDay",
                            onValueChange = { lunarDay = it.toIntOrNull()?.coerceIn(1, 30) ?: 1 },
                            label = { Text("农历日期 (1-30)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = "选定为：农历${LunarCalendarUtil.getLunarMonthName(lunarMonth)}${LunarCalendarUtil.getLunarDayName(lunarDay)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = WarmAmberPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = solarYear,
                            onValueChange = { solarYear = it },
                            label = { Text("年") },
                            modifier = Modifier.weight(1.2f)
                        )
                        OutlinedTextField(
                            value = solarMonth,
                            onValueChange = { solarMonth = it },
                            label = { Text("月") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = solarDay,
                            onValueChange = { solarDay = it },
                            label = { Text("日") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("暖心小贴士 (如: 预定蛋糕、买保暖围巾)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val resolvedPerson = if (person == "其他") {
                        if (customPersonName.isNotBlank()) customPersonName else "亲友"
                    } else person

                    val finalTitle = if (title.isNotBlank()) title else "$resolvedPerson 生日"
                    if (isLunar) {
                        onConfirmLunar(finalTitle, lunarMonth, lunarDay, resolvedPerson, note)
                    } else {
                        val m = solarMonth.padStart(2, '0')
                        val d = solarDay.padStart(2, '0')
                        onConfirmSolar(finalTitle, "$solarYear-$m-$d", "BIRTHDAY", resolvedPerson, note)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WarmAmberPrimary)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
